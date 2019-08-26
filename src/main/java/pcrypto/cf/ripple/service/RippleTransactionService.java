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

package pcrypto.cf.ripple.service;

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
import pcrypto.cf.exception.ErrorMessage;
import pcrypto.cf.exception.NotFoundException;
import pcrypto.cf.mfa.service.authy.AuthyPushApprovalService;
import pcrypto.cf.ripple.api.model.RipplePaymentRequest;
import pcrypto.cf.ripple.client.RippleTransactionClient;
import pcrypto.cf.ripple.client.dto.RippleSubmitResponseDto;
import pcrypto.cf.ripple.domain.entity.RippleTransactionRequestApproverDomain;
import pcrypto.cf.ripple.domain.entity.RippleTransactionRequestDomain;
import pcrypto.cf.ripple.domain.entity.RippleTransactionResponseDomain;
import pcrypto.cf.ripple.domain.repository.RippleTransactionRequestApproverRepository;
import pcrypto.cf.ripple.domain.repository.RippleTransactionRequestRepository;
import pcrypto.cf.ripple.domain.repository.RippleTransactionResponseRepository;
import pcrypto.cf.ripple.stream.RippleTransactionApprovalEvent;
import pcrypto.cf.ripple.stream.RippleTransactionApprovalStream;
import pcrypto.cf.ripple.value.TransactionApprovalStatusEnum;
import pcrypto.cf.ripple.vault.dto.VaultRipplePaymentDomain;
import pcrypto.cf.security.domain.CustomUserDetails;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
public class RippleTransactionService
{

    private final RippleTransactionRequestRepository rippleTransactionRequestRepository;
    private final RippleTransactionRequestApproverRepository rippleTransactionRequestApproverRepository;
    private final RippleTransactionResponseRepository rippleTransactionResponseRepository;
    private final RippleTransactionApprovalStream rippleTransactionApprovalStream;
    private final RippleTransactionClient rippleTransactionClient;
    private final AccountConfigurationRepository accountConfigurationRepository;
    private final AuthyPushApprovalService authyPushApprovalService;
    private final VaultOperations vaultOperations;
    private final ObjectMapper objectMapper;


    @Autowired
    public RippleTransactionService( final RippleTransactionRequestRepository rippleTransactionRequestRepository,
                                     final RippleTransactionRequestApproverRepository rippleTransactionRequestApproverRepository,
                                     final RippleTransactionResponseRepository rippleTransactionResponseRepository,
                                     @SuppressWarnings( "SpringJavaInjectionPointsAutowiringInspection" ) final RippleTransactionApprovalStream rippleTransactionApprovalStream,
                                     final RippleTransactionClient rippleTransactionClient,
                                     final AccountConfigurationRepository accountConfigurationRepository,
                                     final AuthyPushApprovalService authyPushApprovalService,
                                     final VaultOperations vaultOperations,
                                     final ObjectMapper objectMapper )
    {
        this.rippleTransactionRequestRepository = rippleTransactionRequestRepository;
        this.rippleTransactionRequestApproverRepository = rippleTransactionRequestApproverRepository;
        this.rippleTransactionResponseRepository = rippleTransactionResponseRepository;
        this.rippleTransactionApprovalStream = rippleTransactionApprovalStream;
        this.rippleTransactionClient = rippleTransactionClient;
        this.accountConfigurationRepository = accountConfigurationRepository;
        this.authyPushApprovalService = authyPushApprovalService;
        this.vaultOperations = vaultOperations;
        this.objectMapper = objectMapper;
    }


    @Transactional
    public RippleTransactionRequestDomain processPaymentRequest( final Authentication authentication,
                                                                 final RipplePaymentRequest paymentRequest,
                                                                 final AccountDomain sourceAccountDomain,
                                                                 final AccountDomain destAccountDomain,
                                                                 final List<AccountDomain> additionalSignerAccounts,
                                                                 final AccountDomain paymentChannelAccountDomain )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Create our transaction request object
        final RippleTransactionRequestDomain transactionRequest = new RippleTransactionRequestDomain();
        try
        {
            transactionRequest.setRipplePaymentRequest( objectMapper.writeValueAsString( paymentRequest ) );
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
        final RippleTransactionRequestDomain persistedTransactionRequest = rippleTransactionRequestRepository.save( transactionRequest );

        // Send out MFA approval requests
        final List<RippleTransactionRequestApproverDomain> approverDomains = sendMfaApprovalRequests( tenantDomain,
                                                                                                      persistedTransactionRequest,
                                                                                                      paymentRequest,
                                                                                                      approvers );

        // Record the approval requests in our local db
        persistedTransactionRequest.setApproverDomains( approverDomains );
        for ( final RippleTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            // JPA requires this so the foreign key value is set correctly
            approverDomain.setRippleTransactionRequest( persistedTransactionRequest );

            // Save the approver request record
            rippleTransactionRequestApproverRepository.save( approverDomain );
        }

        // Create a placeholder linked db record to hold the transaction response
        final RippleTransactionResponseDomain rippleTransactionResponseDomain = new RippleTransactionResponseDomain();
        rippleTransactionResponseDomain.setAccountDomain( sourceAccountDomain );
        rippleTransactionResponseDomain.setRippleTransactionRequest( persistedTransactionRequest );
        rippleTransactionResponseRepository.save( rippleTransactionResponseDomain );

        // Return transaction request details
        return persistedTransactionRequest;
    }


    public RippleTransactionRequestDomain getRippleTransactionRequest( final String transactionId )
    {
        final Optional<RippleTransactionRequestDomain> optionalRippleTransactionRequestDomain = rippleTransactionRequestRepository.findById( UUID.fromString( transactionId ) );
        optionalRippleTransactionRequestDomain.orElseThrow( () -> new NotFoundException( "Ripple transaction " + transactionId + " not found." ) );

        return optionalRippleTransactionRequestDomain.get();
    }

    public RippleTransactionResponseDomain getRippleTransactionResponse( final String transactionId )
    {
        final Optional<RippleTransactionResponseDomain> optionalRippleTransactionResponseDomain = rippleTransactionResponseRepository.findByTransactionRequestUUID( UUID.fromString( transactionId ) );
        optionalRippleTransactionResponseDomain.orElseThrow( () -> new NotFoundException( "Ripple transaction " + transactionId + " not found." ) );

        return optionalRippleTransactionResponseDomain.get();
    }


    public RippleTransactionResponseDomain updateRippleTransactionApproval( final String transactionId,
                                                                            final String authyUUID,
                                                                            final int status )
    {
        final Optional<RippleTransactionResponseDomain> optionalRippleTransactionResponseDomain = rippleTransactionResponseRepository.findByTransactionRequestUUID( UUID.fromString( transactionId ) );
        optionalRippleTransactionResponseDomain.orElseThrow( () -> new NotFoundException( "Ripple transaction " + transactionId + " not found." ) );

        final List<RippleTransactionRequestApproverDomain> approverDomains = optionalRippleTransactionResponseDomain.get().getRippleTransactionRequest().getApproverDomains();
        for ( final RippleTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            if ( authyUUID.equals( approverDomain.getAuthyApprovalRequestUUID() ) )
            {
                // TODO: more validation (username, tenant, etc)
                approverDomain.setStatus( status );
                // TODO: save
                rippleTransactionRequestApproverRepository.save( approverDomain );
                break;
            }
        }

        return optionalRippleTransactionResponseDomain.get();
    }


    /**
     * When an approval event fires, this service method is eventually called by the listener. It will check the
     * transaction request for all approvals, and if the requirements have been met, sign and submit the transaction.
     *
     * @param event
     */
    @Transactional
    public void processApprovalEvent( final RippleTransactionApprovalEvent event )
    {
        // Fetch the transaction from the db
        final String transactionId = event.getTransactionId();
        final RippleTransactionRequestDomain rippleTransactionRequestDomain = getRippleTransactionRequest( transactionId );
        final RippleTransactionResponseDomain rippleTransactionResponseDomain = getRippleTransactionResponse( transactionId );

        final List<RippleTransactionRequestApproverDomain> approverDomains = rippleTransactionRequestDomain.getApproverDomains();
        for ( final RippleTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final int status = approverDomain.getStatus();
            if ( status != TransactionApprovalStatusEnum.APPROVED.getId() )
            {
                // All approvals are not yet granted, so no need to process further.
                return;
            }
        }

        // All approvals granted, send the transaction to Vault for signing, and then submit to Ripple
        submitTransaction( rippleTransactionRequestDomain, rippleTransactionResponseDomain );
    }

    public RippleTransactionResponseDomain submitTransaction( final RippleTransactionRequestDomain rippleTransactionRequestDomain,
                                                              final RippleTransactionResponseDomain rippleTransactionResponseDomain )
    {
        final TenantDomain tenantDomain = rippleTransactionRequestDomain.getTenantDomain();

        // Obtain a signed tx from Vault
        final VaultRipplePaymentDomain paymentDomain = new VaultRipplePaymentDomain();
        paymentDomain.setSource( String.valueOf( rippleTransactionRequestDomain.getAccountDomain().getId() ) );
        paymentDomain.setDestination( String.valueOf( rippleTransactionRequestDomain.getDestAccountDomain().getId() ) );

        if ( null != rippleTransactionRequestDomain.getPaymentChannelAccountDomain() )
        {
            paymentDomain.setPaymentChannel( String.valueOf( rippleTransactionRequestDomain.getPaymentChannelAccountDomain().getId() ) );
        }

        paymentDomain.setAmount( rippleTransactionRequestDomain.getAmount().toString() );
        paymentDomain.setAssetCode( rippleTransactionRequestDomain.getAssetCode() );
        paymentDomain.setAssetIssuer( rippleTransactionRequestDomain.getAssetIssuer() );
        paymentDomain.setMemo( rippleTransactionRequestDomain.getMemo() );

        final VaultResponse vaultResponse = vaultOperations.write( getRippleVaultPluginPath( tenantDomain ) + "/payments", paymentDomain );
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
        final String transactionHash = (String) data.get( "transaction_hash" );

        // Submit the signed tx to Ripple
        final RippleSubmitResponseDto txResponse = rippleTransactionClient.submitTransaction( signedTx );

        // Save the transaction response
        //rippleTransactionResponseDomain.setLedger( txResponse.getLedger() );
        rippleTransactionResponseDomain.setSuccess( txResponse.isSucceeded() );
        rippleTransactionResponseDomain.setTransactionHash( transactionHash );
        rippleTransactionResponseDomain.setSignedTransaction( signedTx );
        rippleTransactionResponseDomain.setTransactionResult( txResponse.getEngineResult().toString() );

        rippleTransactionResponseDomain.setCreatedDate( OffsetDateTime.now() );

        final RippleTransactionResponseDomain savedResponse = rippleTransactionResponseRepository.save( rippleTransactionResponseDomain );

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

    private List<RippleTransactionRequestApproverDomain> sendMfaApprovalRequests( final TenantDomain tenantDomain,
                                                                                  final RippleTransactionRequestDomain rippleTransactionRequestDomain,
                                                                                  final RipplePaymentRequest paymentRequest,
                                                                                  final List<AccountDomain> approvers )
    {
        final UUID transactionUUID = rippleTransactionRequestDomain.getUuid();

        final List<RippleTransactionRequestApproverDomain> approverDomains = new ArrayList<>();
        for ( final AccountDomain approver : approvers )
        {
            final RippleTransactionRequestApproverDomain txRequestApproverDomain = new RippleTransactionRequestApproverDomain();
            txRequestApproverDomain.setAccountDomain( approver );
            txRequestApproverDomain.setRippleTransactionRequest( rippleTransactionRequestDomain );

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
                                                                                               "RIPPLE",
                                                                                               reason );

                txRequestApproverDomain.setAuthyApprovalRequestUUID( approvalRequestUUID );
                txRequestApproverDomain.setStatus( TransactionApprovalStatusEnum.PENDING.getId() );

                // Save the approval request record
                rippleTransactionRequestApproverRepository.save( txRequestApproverDomain );

                approverDomains.add( txRequestApproverDomain );
            }
            else if ( TxApprovalMethodEnum.IMPLICIT.getId() == approver.getTxApprovalMethod() )
            {
                txRequestApproverDomain.setStatus( TransactionApprovalStatusEnum.APPROVED.getId() );

                // Save the approver request record. We need to save this record BEFORE sending the approval event so that
                // the downstream Kafka listener can properly detect that the approval has been granted for this approver.
                rippleTransactionRequestApproverRepository.save( txRequestApproverDomain );

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                final RippleTransactionApprovalEvent approvalEvent = new RippleTransactionApprovalEvent();
                approvalEvent.setTenantId( tenantDomain.getCode() );
                approvalEvent.setTransactionId( transactionUUID.toString() );

                final MessageChannel messageChannel = rippleTransactionApprovalStream.outboundApproval();
                messageChannel.send( MessageBuilder.withPayload( approvalEvent )
                                                   .setHeader( MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON )
                                                   .build() );

                approverDomains.add( txRequestApproverDomain );
            }
        }

        return approverDomains;
    }


    //    RippleTransactionRequestApproverDomain t = new RippleTransactionRequestApproverDomain();
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

    private String getRippleVaultPluginPath( final TenantDomain tenantDomain )
    {
        return "/ripple/" + tenantDomain.getId();
    }
}
