/*
 * Copyright (c) 2019 ChainFront LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pcrypto.cf.stellar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import pcrypto.cf.account.domain.entity.AccountConfigurationDomain;
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.account.domain.repository.AccountConfigurationRepository;
import pcrypto.cf.account.value.TxApprovalMethodEnum;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.exception.BadRequestException;
import pcrypto.cf.exception.BlockchainServiceException;
import pcrypto.cf.exception.ErrorMessage;
import pcrypto.cf.exception.NotFoundException;
import pcrypto.cf.mfa.service.authy.AuthyPushApprovalService;
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.stellar.api.model.StellarPaymentRequest;
import pcrypto.cf.stellar.client.StellarNetworkService;
import pcrypto.cf.stellar.client.response.DecoratedSubmitTransactionResponse;
import pcrypto.cf.stellar.domain.entity.StellarTransactionRequestApproverDomain;
import pcrypto.cf.stellar.domain.entity.StellarTransactionRequestDomain;
import pcrypto.cf.stellar.domain.entity.StellarTransactionResponseDomain;
import pcrypto.cf.stellar.domain.repository.StellarTransactionRequestApproverRepository;
import pcrypto.cf.stellar.domain.repository.StellarTransactionRequestRepository;
import pcrypto.cf.stellar.domain.repository.StellarTransactionResponseRepository;
import pcrypto.cf.stellar.stream.StellarTransactionApprovalEvent;
import pcrypto.cf.stellar.stream.StellarTransactionApprovalStream;
import pcrypto.cf.stellar.value.TransactionApprovalStatusEnum;
import pcrypto.cf.stellar.vault.dto.VaultStellarPaymentDomain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
public class StellarTransactionService
{

    private final StellarTransactionRequestRepository stellarTransactionRequestRepository;
    private final StellarTransactionRequestApproverRepository stellarTransactionRequestApproverRepository;
    private final StellarTransactionResponseRepository stellarTransactionResponseRepository;
    private final StellarTransactionApprovalStream stellarTransactionApprovalStream;
    private final StellarNetworkService stellarNetworkService;
    private final AccountConfigurationRepository accountConfigurationRepository;
    private final AuthyPushApprovalService authyPushApprovalService;
    private final VaultOperations vaultOperations;
    private final ObjectMapper objectMapper;


    @Autowired
    public StellarTransactionService( final StellarTransactionRequestRepository stellarTransactionRequestRepository,
                                      final StellarTransactionRequestApproverRepository stellarTransactionRequestApproverRepository,
                                      final StellarTransactionResponseRepository stellarTransactionResponseRepository,
                                      @SuppressWarnings( "SpringJavaInjectionPointsAutowiringInspection" ) final StellarTransactionApprovalStream stellarTransactionApprovalStream,
                                      final StellarNetworkService stellarNetworkService,
                                      final AccountConfigurationRepository accountConfigurationRepository,
                                      final AuthyPushApprovalService authyPushApprovalService,
                                      final VaultOperations vaultOperations,
                                      final ObjectMapper objectMapper )
    {
        this.stellarTransactionRequestRepository = stellarTransactionRequestRepository;
        this.stellarTransactionRequestApproverRepository = stellarTransactionRequestApproverRepository;
        this.stellarTransactionResponseRepository = stellarTransactionResponseRepository;
        this.stellarTransactionApprovalStream = stellarTransactionApprovalStream;
        this.stellarNetworkService = stellarNetworkService;
        this.accountConfigurationRepository = accountConfigurationRepository;
        this.authyPushApprovalService = authyPushApprovalService;
        this.vaultOperations = vaultOperations;
        this.objectMapper = objectMapper;
    }


    @Transactional
    public StellarTransactionRequestDomain processPaymentRequest( final Authentication authentication,
                                                                  final StellarPaymentRequest paymentRequest,
                                                                  final AccountDomain sourceAccountDomain,
                                                                  final AccountDomain destAccountDomain,
                                                                  final List<AccountDomain> additionalSignerAccounts,
                                                                  final AccountDomain paymentChannelAccountDomain )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Create our transaction request object
        final StellarTransactionRequestDomain transactionRequest = new StellarTransactionRequestDomain();
        try
        {
            transactionRequest.setStellarPaymentRequest( objectMapper.writeValueAsString( paymentRequest ) );
        }
        catch ( final JsonProcessingException e )
        {
            throw new BadRequestException( "Unable to process payment request json. Please check for errors.", e );
        }
        transactionRequest.setAccountDomain( sourceAccountDomain );
        transactionRequest.setDestAccountDomain( destAccountDomain );
        transactionRequest.setPaymentChannelAccountDomain( paymentChannelAccountDomain );
        transactionRequest.setTenantDomain( tenantDomain );
        transactionRequest.setAmount( paymentRequest.getAmount() );
        transactionRequest.setAssetCode( paymentRequest.getAssetCode() );
        transactionRequest.setAssetIssuer( paymentRequest.getAssetIssuer() );
        transactionRequest.setMemo( paymentRequest.getMemo() );

        // Add all of the approver objects to our transaction request (source account, payment channel, and additional signers)
        final List<AccountDomain> approvers = new ArrayList<>();
        approvers.add( sourceAccountDomain );
        approvers.addAll( additionalSignerAccounts );
        if ( null != paymentChannelAccountDomain )
        {
            approvers.add( paymentChannelAccountDomain );
        }

        // Validate that all the MFA approvers are registered and/or set up properly
        validateMfaApprovers( approvers );

        // Save the transaction request which will generate our internal transaction id
        final StellarTransactionRequestDomain persistedTransactionRequest = stellarTransactionRequestRepository.save( transactionRequest );

        // Send out MFA approval requests
        final List<StellarTransactionRequestApproverDomain> approverDomains = sendMfaApprovalRequests( tenantDomain,
                                                                                                       persistedTransactionRequest,
                                                                                                       paymentRequest,
                                                                                                       approvers );

        // Record the approval requests in our local db
        persistedTransactionRequest.setApproverDomains( approverDomains );
        for ( final StellarTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            // JPA requires this so the foreign key value is set correctly
            approverDomain.setStellarTransactionRequest( persistedTransactionRequest );

            // Save the approver request record
            stellarTransactionRequestApproverRepository.save( approverDomain );
        }

        // Create a placeholder linked db record to hold the transaction response
        final StellarTransactionResponseDomain stellarTransactionResponseDomain = new StellarTransactionResponseDomain();
        stellarTransactionResponseDomain.setAccountDomain( sourceAccountDomain );
        stellarTransactionResponseDomain.setStellarTransactionRequest( persistedTransactionRequest );
        stellarTransactionResponseRepository.save( stellarTransactionResponseDomain );

        // Return transaction request details
        return persistedTransactionRequest;
    }


    public StellarTransactionRequestDomain getStellarTransactionRequest( final String transactionId )
    {
        final Optional<StellarTransactionRequestDomain> optionalStellarTransactionRequestDomain = stellarTransactionRequestRepository.findById( UUID.fromString( transactionId ) );
        optionalStellarTransactionRequestDomain.orElseThrow( () -> new NotFoundException( "Stellar transaction " + transactionId + " not found." ) );

        return optionalStellarTransactionRequestDomain.get();
    }

    public StellarTransactionResponseDomain getStellarTransactionResponse( final String transactionId )
    {
        final Optional<StellarTransactionResponseDomain> optionalStellarTransactionResponseDomain = stellarTransactionResponseRepository.findByTransactionRequestUUID( UUID.fromString( transactionId ) );
        optionalStellarTransactionResponseDomain.orElseThrow( () -> new NotFoundException( "Stellar transaction " + transactionId + " not found." ) );

        return optionalStellarTransactionResponseDomain.get();
    }


    public StellarTransactionResponseDomain updateStellarTransactionApproval( final String transactionId,
                                                                              final String authyUUID,
                                                                              final int status )
    {
        final Optional<StellarTransactionResponseDomain> optionalStellarTransactionResponseDomain = stellarTransactionResponseRepository.findByTransactionRequestUUID( UUID.fromString( transactionId ) );
        optionalStellarTransactionResponseDomain.orElseThrow( () -> new NotFoundException( "Stellar transaction " + transactionId + " not found." ) );

        final List<StellarTransactionRequestApproverDomain> approverDomains = optionalStellarTransactionResponseDomain.get().getStellarTransactionRequest().getApproverDomains();
        for ( final StellarTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            if ( authyUUID.equals( approverDomain.getAuthyApprovalRequestUUID() ) )
            {
                // TODO: more validation (username, tenant, etc)
                approverDomain.setStatus( status );
                // TODO: save
                stellarTransactionRequestApproverRepository.save( approverDomain );
                break;
            }
        }

        return optionalStellarTransactionResponseDomain.get();
    }


    /**
     * When an approval event fires, this service method is eventually called by the listener. It will check the
     * transaction request for all approvals, and if the requirements have been met, sign and submit the transaction.
     *
     * @param event
     */
    @Transactional
    public void processApprovalEvent( final StellarTransactionApprovalEvent event )
    {
        // Fetch the transaction from the db
        final String transactionId = event.getTransactionId();
        final StellarTransactionRequestDomain stellarTransactionRequestDomain = getStellarTransactionRequest( transactionId );
        final StellarTransactionResponseDomain stellarTransactionResponseDomain = getStellarTransactionResponse( transactionId );

        final List<StellarTransactionRequestApproverDomain> approverDomains = stellarTransactionRequestDomain.getApproverDomains();
        for ( final StellarTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final int status = approverDomain.getStatus();
            if ( status != TransactionApprovalStatusEnum.APPROVED.getId() )
            {
                // All approvals are not yet granted, so no need to process further.
                return;
            }
        }

        // All approvals granted, send the transaction to Vault for signing, and then submit to Stellar
        submitTransaction( stellarTransactionRequestDomain, stellarTransactionResponseDomain );
    }

    public StellarTransactionResponseDomain submitTransaction( final StellarTransactionRequestDomain stellarTransactionRequestDomain,
                                                               final StellarTransactionResponseDomain stellarTransactionResponseDomain )
    {
        final TenantDomain tenantDomain = stellarTransactionRequestDomain.getTenantDomain();

        // Obtain a signed tx from Vault
        final VaultStellarPaymentDomain paymentDomain = new VaultStellarPaymentDomain();
        paymentDomain.setSource( String.valueOf( stellarTransactionRequestDomain.getAccountDomain().getId() ) );
        paymentDomain.setDestination( String.valueOf( stellarTransactionRequestDomain.getDestAccountDomain().getId() ) );

        if ( null != stellarTransactionRequestDomain.getPaymentChannelAccountDomain() )
        {
            paymentDomain.setPaymentChannel( String.valueOf( stellarTransactionRequestDomain.getPaymentChannelAccountDomain().getId() ) );
        }

        paymentDomain.setAmount( stellarTransactionRequestDomain.getAmount().toString() );
        paymentDomain.setAssetCode( stellarTransactionRequestDomain.getAssetCode() );
        paymentDomain.setAssetIssuer( stellarTransactionRequestDomain.getAssetIssuer() );
        paymentDomain.setMemo( stellarTransactionRequestDomain.getMemo() );

        final VaultResponse vaultResponse = vaultOperations.write( getStellarVaultPluginPath( tenantDomain ) + "/payments", paymentDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while creating the payment transaction." );
        }

        final Map<String, Object> data = vaultResponse.getData();
        if ( null == data )
        {
            throw new VaultException( "Vault response when signing transaction contained a null data map." );
        }
        final String signedTx = (String) data.get( "signed_transaction" );

        // Submit the signed tx to Stellar
        final DecoratedSubmitTransactionResponse txResponse = stellarNetworkService.submitTransaction( signedTx );

        // Save the transaction response
        stellarTransactionResponseDomain.setLedger( txResponse.getLedger() );
        stellarTransactionResponseDomain.setSuccess( txResponse.isSuccess() );
        stellarTransactionResponseDomain.setTransactionHash( txResponse.getHash() );
        stellarTransactionResponseDomain.setSignedTransaction( signedTx );
        try
        {
            stellarTransactionResponseDomain.setTransactionResult( objectMapper.writeValueAsString( txResponse.getResultCodeMap() ) );
        }
        catch ( final JsonProcessingException e )
        {
            throw new BlockchainServiceException( "Unable to parse results from Stellar: " + e.getMessage(), e );
        }
        stellarTransactionResponseDomain.setCreatedDate( OffsetDateTime.now() );

        final StellarTransactionResponseDomain savedResponse = stellarTransactionResponseRepository.save( stellarTransactionResponseDomain );

        return savedResponse;
    }


    private void validateMfaApprovers( final List<AccountDomain> approvers )
    {
        final List<ErrorMessage> invalidApprovers = new ArrayList<>();
        for ( final AccountDomain approver : approvers )
        {
            // If using Authy, generate a push notification
            if ( TxApprovalMethodEnum.AUTHY_PUSH.getId() == approver.getTxApprovalMethod() )
            {
                final Long accountId = approver.getId();
                final Optional<AccountConfigurationDomain> sourceAccountConfigurationDomain = accountConfigurationRepository.findByCfAccountId( accountId );
                if ( !sourceAccountConfigurationDomain.isPresent() )
                {
                    throw new NotFoundException( "Authy push notification configuration for user '" + approver.getEmail() + "' not found" );
                }
                final boolean hasAuthyApp = authyPushApprovalService.hasAuthyApp( sourceAccountConfigurationDomain.get().getAuthyId() );
                if ( !hasAuthyApp )
                {
                    invalidApprovers.add(
                          new ErrorMessage( "Required approver '" + approver.getEmail() + "' has not yet installed the Authy application.",
                                            null ) );
                }
            }
        }
        if ( !invalidApprovers.isEmpty() )
        {
            throw new BadRequestException( "One or more approvers are invalid.", invalidApprovers );
        }
    }

    private List<StellarTransactionRequestApproverDomain> sendMfaApprovalRequests( final TenantDomain tenantDomain,
                                                                                   final StellarTransactionRequestDomain stellarTransactionRequestDomain,
                                                                                   final StellarPaymentRequest paymentRequest,
                                                                                   final List<AccountDomain> approvers )
    {
        final UUID transactionUUID = stellarTransactionRequestDomain.getUuid();

        final List<StellarTransactionRequestApproverDomain> approverDomains = new ArrayList<>();
        for ( final AccountDomain approver : approvers )
        {
            final StellarTransactionRequestApproverDomain txRequestApproverDomain = new StellarTransactionRequestApproverDomain();
            txRequestApproverDomain.setAccountDomain( approver );
            txRequestApproverDomain.setStellarTransactionRequest( stellarTransactionRequestDomain );

            // If using Authy, generate a push notification
            if ( TxApprovalMethodEnum.AUTHY_PUSH.getId() == approver.getTxApprovalMethod() )
            {
                final Long accountId = approver.getId();
                final Optional<AccountConfigurationDomain> accountConfigurationDomain = accountConfigurationRepository.findByCfAccountId( accountId );
                if ( !accountConfigurationDomain.isPresent() )
                {
                    throw new NotFoundException( "Authy push notification configuration for user '" + approver.getEmail() + "' not found" );
                }

                final String userName = approver.getUserName();
                final int authyId = accountConfigurationDomain.get().getAuthyId();

                final String reason = "Transaction: payment of " + paymentRequest.getAmount() + " tokens to account " + paymentRequest.getDestinationCfAccountIdentifier() + ".";

                // Send the approval request
                final String approvalRequestUUID = authyPushApprovalService.sendOneTouchToken( tenantDomain,
                                                                                               userName,
                                                                                               authyId,
                                                                                               transactionUUID,
                                                                                               "STELLAR",
                                                                                               reason );

                txRequestApproverDomain.setAuthyApprovalRequestUUID( approvalRequestUUID );
                txRequestApproverDomain.setStatus( TransactionApprovalStatusEnum.PENDING.getId() );

                // Save the approval request record
                stellarTransactionRequestApproverRepository.save( txRequestApproverDomain );

                approverDomains.add( txRequestApproverDomain );
            }
            else if ( TxApprovalMethodEnum.IMPLICIT.getId() == approver.getTxApprovalMethod() )
            {
                txRequestApproverDomain.setStatus( TransactionApprovalStatusEnum.APPROVED.getId() );

                // Save the approver request record. We need to save this record BEFORE sending the approval event so that
                // the downstream Kafka listener can properly detect that the approval has been granted for this approver.
                stellarTransactionRequestApproverRepository.save( txRequestApproverDomain );

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                final StellarTransactionApprovalEvent approvalEvent = new StellarTransactionApprovalEvent();
                approvalEvent.setTenantId( tenantDomain.getCode() );
                approvalEvent.setTransactionId( transactionUUID.toString() );

                final MessageChannel messageChannel = stellarTransactionApprovalStream.outboundApproval();
                messageChannel.send( MessageBuilder.withPayload( approvalEvent )
                                                   .setHeader( MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON )
                                                   .build() );

                approverDomains.add( txRequestApproverDomain );
            }
        }

        return approverDomains;
    }


    //    StellarTransactionRequestApproverDomain t = new StellarTransactionRequestApproverDomain();
    //        t.setAccountDomain( sourceAccountDomain );
    //
    //    // Validate the MFA requirements for this call
    //        if ( TxApprovalMethodEnum.APP_TOTP.getId() == sourceAccountDomain.getTxApprovalMethod() )
    //    {
    //        if ( StringUtils.isEmpty( totpCode ) )
    //        {
    //            throw new BadRequestException( "Missing 'X-TOTP-Code' header." );
    //        }
    //        final boolean validTotpCode = totpService.verifyCode( authentication, sourceCfAccountId, totpCode );
    //        if ( !validTotpCode )
    //        {
    //            throw new ForbiddenException( "Invalid TOTP code was sent in the 'X-TOTP-Code' value. Please try again with a new code from your TOTP application." );
    //        }
    //
    //        // Ensure we don't have additionalApprovers for this payment transaction (unsupported with TOTP)
    //        if ( null != paymentRequest.getAdditionalApprovers() && !paymentRequest.getAdditionalApprovers().isEmpty() )
    //        {
    //            throw new BadRequestException( "The source account is configured with APP_TOTP mfa approvals. Additional signers are unsupported." );
    //        }
    //    }
    //        else if ( TxApprovalMethodEnum.AUTHY_PUSH.getId() == sourceAccountDomain.getTxApprovalMethod() )
    //    {
    //        final Optional<AccountConfigurationDomain> sourceAccountConfigurationDomain = accountConfigurationRepository.findByCfAccountId( sourceCfAccountId );
    //        if ( !sourceAccountConfigurationDomain.isPresent() )
    //        {
    //            throw new NotFoundException( "Authy push notification configuration for user '" + sourceCfAccountId + "' not found" );
    //        }
    //
    //        final String userName = sourceAccountDomain.getUserName();
    //        final int authyId = sourceAccountConfigurationDomain.get().getAuthyId();
    //
    //        final String reason = "Transaction: payment of " + paymentRequest.getAmount() + " tokens to account " + paymentRequest.getDestinationCfAccountIdentifier() + ".";
    //        final String approvalRequestUUID = authyPushApprovalService.sendOneTouchToken( userName, authyId, reason );
    //
    //        //            transactionRequest.setAuthyId( authyId );
    //        //            transactionRequest.setAuthyApprovalRequestUUID( approvalRequestUUID );
    //        // TODO: handle async flows. For now, we'll just wait until approval happens or we timeout.
    //        //            final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    //        //            final ScheduledFuture future = executor.scheduleWithFixedDelay( new AuthyTokenPollingService( approvalRequestUUID ),
    //        //                                                                            0,
    //        //                                                                            500,
    //        //                                                                            TimeUnit.MILLISECONDS );
    //        //            Thread.sleep( 1000 );
    //        //            future.cancel( false );
    //        //            executor.shutdown();
    //    }
    //    // TODO: support other MFA flows

    private String getStellarVaultPluginPath( final TenantDomain tenantDomain )
    {
        return "/stellar/" + tenantDomain.getId();
    }
}
