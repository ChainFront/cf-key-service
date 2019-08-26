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

package pcrypto.cf.ethereum.service;

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
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;
import pcrypto.cf.account.domain.entity.AccountConfigurationDomain;
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.account.domain.repository.AccountConfigurationRepository;
import pcrypto.cf.account.value.TxApprovalMethodEnum;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.ethereum.api.model.EthereumPaymentRequest;
import pcrypto.cf.ethereum.client.EthereumTransactionClient;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionRequestApproverDomain;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionRequestDomain;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionResponseDomain;
import pcrypto.cf.ethereum.domain.repository.EthereumTransactionRequestApproverRepository;
import pcrypto.cf.ethereum.domain.repository.EthereumTransactionRequestRepository;
import pcrypto.cf.ethereum.domain.repository.EthereumTransactionResponseRepository;
import pcrypto.cf.ethereum.stream.EthereumTransactionApprovalEvent;
import pcrypto.cf.ethereum.stream.EthereumTransactionApprovalStream;
import pcrypto.cf.ethereum.vault.dto.VaultEthereumPaymentDomain;
import pcrypto.cf.exception.BadRequestException;
import pcrypto.cf.exception.ErrorMessage;
import pcrypto.cf.exception.NotFoundException;
import pcrypto.cf.mfa.service.authy.AuthyPushApprovalService;
import pcrypto.cf.ripple.value.TransactionApprovalStatusEnum;
import pcrypto.cf.security.domain.CustomUserDetails;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
public class EthereumTransactionService
{

    private final EthereumTransactionRequestRepository ethereumTransactionRequestRepository;
    private final EthereumTransactionRequestApproverRepository ethereumTransactionRequestApproverRepository;
    private final EthereumTransactionResponseRepository ethereumTransactionResponseRepository;
    private final EthereumTransactionApprovalStream ethereumTransactionApprovalStream;
    private final EthereumTransactionClient ethereumTransactionClient;
    private final AccountConfigurationRepository accountConfigurationRepository;
    private final AuthyPushApprovalService authyPushApprovalService;
    private final VaultOperations vaultOperations;
    private final ObjectMapper objectMapper;


    @Autowired
    public EthereumTransactionService( final EthereumTransactionRequestRepository ethereumTransactionRequestRepository,
                                       final EthereumTransactionRequestApproverRepository ethereumTransactionRequestApproverRepository,
                                       final EthereumTransactionResponseRepository ethereumTransactionResponseRepository,
                                       @SuppressWarnings( "SpringJavaInjectionPointsAutowiringInspection" ) final EthereumTransactionApprovalStream ethereumTransactionApprovalStream,
                                       final EthereumTransactionClient ethereumTransactionClient,
                                       final AccountConfigurationRepository accountConfigurationRepository,
                                       final AuthyPushApprovalService authyPushApprovalService,
                                       final VaultOperations vaultOperations,
                                       final ObjectMapper objectMapper )
    {
        this.ethereumTransactionRequestRepository = ethereumTransactionRequestRepository;
        this.ethereumTransactionRequestApproverRepository = ethereumTransactionRequestApproverRepository;
        this.ethereumTransactionResponseRepository = ethereumTransactionResponseRepository;
        this.ethereumTransactionApprovalStream = ethereumTransactionApprovalStream;
        this.ethereumTransactionClient = ethereumTransactionClient;
        this.accountConfigurationRepository = accountConfigurationRepository;
        this.authyPushApprovalService = authyPushApprovalService;
        this.vaultOperations = vaultOperations;
        this.objectMapper = objectMapper;
    }


    @Transactional
    public EthereumTransactionRequestDomain processPaymentRequest( final Authentication authentication,
                                                                   final EthereumPaymentRequest paymentRequest,
                                                                   final AccountDomain sourceAccountDomain,
                                                                   final AccountDomain destAccountDomain,
                                                                   final List<AccountDomain> additionalSignerAccounts )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Create our transaction request object
        final EthereumTransactionRequestDomain transactionRequest = new EthereumTransactionRequestDomain();
        try
        {
            transactionRequest.setEthereumPaymentRequest( objectMapper.writeValueAsString( paymentRequest ) );
        }
        catch ( final JsonProcessingException e )
        {
            throw new BadRequestException( "Unable to process payment request json. Please check for errors.", e );
        }
        transactionRequest.setAccountDomain( sourceAccountDomain );
        transactionRequest.setDestAccountDomain( destAccountDomain );
        transactionRequest.setTenantDomain( tenantDomain );
        transactionRequest.setAmount( paymentRequest.getAmount() );
        transactionRequest.setGasLimit( paymentRequest.getGasLimit() );
        transactionRequest.setMemo( paymentRequest.getMemo() );

        // Add all of the approver objects to our transaction request (source account, payment channel, and additional signers)
        final List<AccountDomain> approvers = new ArrayList<>();
        approvers.add( sourceAccountDomain );
        approvers.addAll( additionalSignerAccounts );

        // Validate that all the MFA approvers are registered and/or set up properly
        validateMfaApprovers( approvers );

        // Save the transaction request which will generate our internal transaction id
        final EthereumTransactionRequestDomain persistedTransactionRequest = ethereumTransactionRequestRepository.save( transactionRequest );

        // Send out MFA approval requests
        final List<EthereumTransactionRequestApproverDomain> approverDomains = sendMfaApprovalRequests( tenantDomain,
                                                                                                        persistedTransactionRequest,
                                                                                                        paymentRequest,
                                                                                                        approvers );

        // Record the approval requests in our local db
        persistedTransactionRequest.setApproverDomains( approverDomains );
        for ( final EthereumTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            // JPA requires this so the foreign key value is set correctly
            approverDomain.setEthereumTransactionRequest( persistedTransactionRequest );

            // Save the approver request record
            ethereumTransactionRequestApproverRepository.save( approverDomain );
        }

        // Create a placeholder linked db record to hold the transaction response
        final EthereumTransactionResponseDomain ethereumTransactionResponseDomain = new EthereumTransactionResponseDomain();
        ethereumTransactionResponseDomain.setAccountDomain( sourceAccountDomain );
        ethereumTransactionResponseDomain.setEthereumTransactionRequest( persistedTransactionRequest );
        ethereumTransactionResponseRepository.save( ethereumTransactionResponseDomain );

        // Return transaction request details
        return persistedTransactionRequest;
    }


    public EthereumTransactionRequestDomain getEthereumTransactionRequest( final String transactionId )
    {
        final Optional<EthereumTransactionRequestDomain> optionalEthereumTransactionRequestDomain = ethereumTransactionRequestRepository.findById( UUID.fromString( transactionId ) );
        optionalEthereumTransactionRequestDomain.orElseThrow( () -> new NotFoundException( "Ethereum transaction " + transactionId + " not found." ) );

        return optionalEthereumTransactionRequestDomain.get();
    }

    public EthereumTransactionResponseDomain getEthereumTransactionResponse( final String transactionId )
    {
        final Optional<EthereumTransactionResponseDomain> optionalEthereumTransactionResponseDomain = ethereumTransactionResponseRepository.findByTransactionRequestUUID( UUID.fromString( transactionId ) );
        optionalEthereumTransactionResponseDomain.orElseThrow( () -> new NotFoundException( "Ethereum transaction " + transactionId + " not found." ) );

        return optionalEthereumTransactionResponseDomain.get();
    }


    public EthereumTransactionResponseDomain updateEthereumTransactionApproval( final String transactionId,
                                                                                final String authyUUID,
                                                                                final int status )
    {
        final Optional<EthereumTransactionResponseDomain> optionalEthereumTransactionResponseDomain = ethereumTransactionResponseRepository.findByTransactionRequestUUID( UUID.fromString( transactionId ) );
        optionalEthereumTransactionResponseDomain.orElseThrow( () -> new NotFoundException( "Ethereum transaction " + transactionId + " not found." ) );

        final List<EthereumTransactionRequestApproverDomain> approverDomains = optionalEthereumTransactionResponseDomain.get().getEthereumTransactionRequest().getApproverDomains();
        for ( final EthereumTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            if ( authyUUID.equals( approverDomain.getAuthyApprovalRequestUUID() ) )
            {
                // TODO: more validation (username, tenant, etc)
                approverDomain.setStatus( status );
                // TODO: save
                ethereumTransactionRequestApproverRepository.save( approverDomain );
                break;
            }
        }

        return optionalEthereumTransactionResponseDomain.get();
    }


    /**
     * When an approval event fires, this service method is eventually called by the listener. It will check the
     * transaction request for all approvals, and if the requirements have been met, sign and submit the transaction.
     *
     * @param event
     */
    @Transactional
    public void processApprovalEvent( final EthereumTransactionApprovalEvent event )
    {
        // Fetch the transaction from the db
        final String transactionId = event.getTransactionId();
        final EthereumTransactionRequestDomain ethereumTransactionRequestDomain = getEthereumTransactionRequest( transactionId );
        final EthereumTransactionResponseDomain ethereumTransactionResponseDomain = getEthereumTransactionResponse( transactionId );

        final List<EthereumTransactionRequestApproverDomain> approverDomains = ethereumTransactionRequestDomain.getApproverDomains();
        for ( final EthereumTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final int status = approverDomain.getStatus();
            if ( status != TransactionApprovalStatusEnum.APPROVED.getId() )
            {
                // All approvals are not yet granted, so no need to process further.
                return;
            }
        }

        // All approvals granted, send the transaction to Vault for signing, and then submit to Ethereum
        submitTransaction( ethereumTransactionRequestDomain, ethereumTransactionResponseDomain );
    }

    public EthereumTransactionResponseDomain submitTransaction( final EthereumTransactionRequestDomain ethereumTransactionRequestDomain,
                                                                final EthereumTransactionResponseDomain ethereumTransactionResponseDomain )
    {
        final TenantDomain tenantDomain = ethereumTransactionRequestDomain.getTenantDomain();

        // Obtain a signed tx from Vault
        final VaultEthereumPaymentDomain paymentDomain = new VaultEthereumPaymentDomain();
        paymentDomain.setSource( String.valueOf( ethereumTransactionRequestDomain.getAccountDomain().getId() ) );
        paymentDomain.setDestination( String.valueOf( ethereumTransactionRequestDomain.getDestAccountDomain().getId() ) );

        paymentDomain.setAmount( ethereumTransactionRequestDomain.getAmount().toString() );
        paymentDomain.setGasLimit( ethereumTransactionRequestDomain.getGasLimit().toString() );
        paymentDomain.setMemo( ethereumTransactionRequestDomain.getMemo() );

        final VaultResponse vaultResponse =
              vaultOperations.write( getEthereumVaultPluginPath( tenantDomain ) + "/accounts/" + ethereumTransactionRequestDomain.getAccountDomain().getId() + "/debit", paymentDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while creating the payment transaction." );
        }

        final Map<String, Object> data = vaultResponse.getData();
        if ( null == data )
        {
            throw new VaultException( "Vault response when signing transaction contained a null data map." );
        }
        final String signedTx = (String) data.get( "signed_tx" );
        final String transactionHash = (String) data.get( "transaction_hash" );

        // Submit the signed tx to Ethereum
        final String tx = Numeric.prependHexPrefix( signedTx );
        final EthSendTransaction txResponse = ethereumTransactionClient.submitTransaction( tx );

        // Save the transaction response
        ethereumTransactionResponseDomain.setSuccess( !txResponse.hasError() );
        ethereumTransactionResponseDomain.setTransactionHash( transactionHash );
        ethereumTransactionResponseDomain.setSignedTransaction( signedTx );

        if ( txResponse.hasError() )
        {
            final Response.Error error = txResponse.getError();
            ethereumTransactionResponseDomain.setTransactionResult( error.getMessage() );
        }
        else
        {
            ethereumTransactionResponseDomain.setTransactionResult( txResponse.getResult() );
        }

        ethereumTransactionResponseDomain.setCreatedDate( OffsetDateTime.now() );

        final EthereumTransactionResponseDomain savedResponse = ethereumTransactionResponseRepository.save( ethereumTransactionResponseDomain );

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

    private List<EthereumTransactionRequestApproverDomain> sendMfaApprovalRequests( final TenantDomain tenantDomain,
                                                                                    final EthereumTransactionRequestDomain ethereumTransactionRequestDomain,
                                                                                    final EthereumPaymentRequest paymentRequest,
                                                                                    final List<AccountDomain> approvers )
    {
        final UUID transactionUUID = ethereumTransactionRequestDomain.getUuid();

        final List<EthereumTransactionRequestApproverDomain> approverDomains = new ArrayList<>();
        for ( final AccountDomain approver : approvers )
        {
            final EthereumTransactionRequestApproverDomain txRequestApproverDomain = new EthereumTransactionRequestApproverDomain();
            txRequestApproverDomain.setAccountDomain( approver );
            txRequestApproverDomain.setEthereumTransactionRequest( ethereumTransactionRequestDomain );

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
                                                                                               "ETHEREUM",
                                                                                               reason );

                txRequestApproverDomain.setAuthyApprovalRequestUUID( approvalRequestUUID );
                txRequestApproverDomain.setStatus( TransactionApprovalStatusEnum.PENDING.getId() );

                // Save the approval request record
                ethereumTransactionRequestApproverRepository.save( txRequestApproverDomain );

                approverDomains.add( txRequestApproverDomain );
            }
            else if ( TxApprovalMethodEnum.IMPLICIT.getId() == approver.getTxApprovalMethod() )
            {
                txRequestApproverDomain.setStatus( TransactionApprovalStatusEnum.APPROVED.getId() );

                // Save the approver request record. We need to save this record BEFORE sending the approval event so that
                // the downstream Kafka listener can properly detect that the approval has been granted for this approver.
                ethereumTransactionRequestApproverRepository.save( txRequestApproverDomain );

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                final EthereumTransactionApprovalEvent approvalEvent = new EthereumTransactionApprovalEvent();
                approvalEvent.setTenantId( tenantDomain.getCode() );
                approvalEvent.setTransactionId( transactionUUID.toString() );

                final MessageChannel messageChannel = ethereumTransactionApprovalStream.outboundApproval();
                messageChannel.send( MessageBuilder.withPayload( approvalEvent )
                                                   .setHeader( MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON )
                                                   .build() );

                approverDomains.add( txRequestApproverDomain );
            }
        }

        return approverDomains;
    }


    //    EthereumTransactionRequestApproverDomain t = new EthereumTransactionRequestApproverDomain();
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

    private String getEthereumVaultPluginPath( final TenantDomain tenantDomain )
    {
        return "/ethereum/" + tenantDomain.getId();
    }
}
