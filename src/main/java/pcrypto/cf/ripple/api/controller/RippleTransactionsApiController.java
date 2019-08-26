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

package pcrypto.cf.ripple.api.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import pcrypto.cf.account.api.model.AccountIdentifier;
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.account.domain.repository.AccountConfigurationRepository;
import pcrypto.cf.account.domain.service.AccountService;
import pcrypto.cf.common.api.controller.ApiController;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.docs.SwaggerTags;
import pcrypto.cf.exception.ApiError;
import pcrypto.cf.exception.ConflictException;
import pcrypto.cf.mfa.api.model.Approval;
import pcrypto.cf.mfa.api.model.ApprovalStatus;
import pcrypto.cf.mfa.service.authy.AuthyPushApprovalService;
import pcrypto.cf.mfa.service.totp.TotpService;
import pcrypto.cf.ripple.api.model.RipplePaymentRequest;
import pcrypto.cf.ripple.api.model.RippleTransaction;
import pcrypto.cf.ripple.api.model.RippleTransactionStatus;
import pcrypto.cf.ripple.api.model.RippleTxJsonRequest;
import pcrypto.cf.ripple.client.RippleTransactionClient;
import pcrypto.cf.ripple.client.dto.RippleTransactionResponseDto;
import pcrypto.cf.ripple.domain.entity.RippleTransactionRequestApproverDomain;
import pcrypto.cf.ripple.domain.entity.RippleTransactionRequestDomain;
import pcrypto.cf.ripple.domain.entity.RippleTransactionResponseDomain;
import pcrypto.cf.ripple.service.RippleTransactionService;
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.security.service.IdempotencyService;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Api( tags = { SwaggerTags.RIPPLE_TRANSACTIONS } )
@ApiController
public class RippleTransactionsApiController
{

    private static final Logger log = LoggerFactory.getLogger( RippleTransactionsApiController.class );


    private final VaultOperations vaultOperations;
    private final AccountService accountService;
    private final AccountConfigurationRepository accountConfigurationRepository;
    private final RippleTransactionService rippleTransactionService;
    private final RippleTransactionClient rippleTransactionClient;
    private final TotpService totpService;
    private final AuthyPushApprovalService authyPushApprovalService;
    private final IdempotencyService idempotencyService;


    @Autowired
    public RippleTransactionsApiController( final VaultOperations vaultOperations,
                                            final AccountService accountService,
                                            final AccountConfigurationRepository accountConfigurationRepository,
                                            final RippleTransactionService rippleTransactionService,
                                            final RippleTransactionClient rippleTransactionClient,
                                            final TotpService totpService,
                                            final AuthyPushApprovalService authyPushApprovalService,
                                            final IdempotencyService idempotencyService )
    {
        this.vaultOperations = vaultOperations;
        this.accountService = accountService;
        this.accountConfigurationRepository = accountConfigurationRepository;
        this.rippleTransactionService = rippleTransactionService;
        this.rippleTransactionClient = rippleTransactionClient;
        this.totpService = totpService;
        this.authyPushApprovalService = authyPushApprovalService;
        this.idempotencyService = idempotencyService;
    }


    @ApiOperation( value = "Create a signed payment transaction.",
                   nickname = "createRipplePayment",
                   notes = "Transfers an asset between 2 accounts. The source account (or optional payment channel account) " +
                           "must have sufficient XRP balance to pay the transaction fee.",
                   response = RippleTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ripple_txs",
                                                                    description = "Ability to create payment transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 202,
                        message = "Payment request accepted",
                        response = RippleTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    @RequestMapping( value = "/ripple/transactions/payments",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<RippleTransaction> createRipplePayment( final Authentication authentication,
                                                                  @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                                  @RequestHeader( value = "X-Idempotency-Key",
                                                                                  required = true ) final String idempotencyKey,
                                                                  @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." ) @RequestHeader( value = "X-TOTP-Code",
                                                                                                                                                                          required = false,
                                                                                                                                                                          defaultValue = "" ) final String totpCode,
                                                                  @ApiParam( value = "Payment request object",
                                                                             required = true ) @Valid @RequestBody final RipplePaymentRequest paymentRequest )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Get the source account
        final AccountIdentifier sourceCfAccountIdentifier = paymentRequest.getSourceCfAccountIdentifier();
        final AccountDomain sourceAccountDomain = accountService.getCfAccountDomainByIdentifier( tenantDomain, sourceCfAccountIdentifier );

        // Get the destination account
        final AccountIdentifier destCfAccountIdentifier = paymentRequest.getDestinationCfAccountIdentifier();
        final AccountDomain destAccountDomain = accountService.getCfAccountDomainByIdentifier( tenantDomain, destCfAccountIdentifier );

        // If set, get the payment channel account
        AccountDomain paymentChannelAccountDomain = null;
        final AccountIdentifier paymentChannelCfAccountIdentifier = paymentRequest.getPaymentChannelCfAccountIdentifier();
        if ( null != paymentChannelCfAccountIdentifier )
        {
            paymentChannelAccountDomain = accountService.getCfAccountDomainByIdentifier( tenantDomain, paymentChannelCfAccountIdentifier );
        }

        // Get the additional signer accounts
        final List<AccountDomain> additionalSignerAccounts = new ArrayList<>();
        for ( final AccountIdentifier additionalSigner : paymentRequest.getAdditionalApprovers() )
        {
            final AccountDomain additionalSignerAccountDomain = accountService.getCfAccountDomainByIdentifier( tenantDomain, additionalSigner );
            additionalSignerAccounts.add( additionalSignerAccountDomain );
        }

        // Validate the idempotency key
        if ( !idempotencyService.processIdempotencyKey( authentication, idempotencyKey, sourceAccountDomain.getId() ) )
        {
            throw new ConflictException( "A transaction has already been created with this idempotency key" );
        }

        // Validate and process all the signing approvals
        final RippleTransactionRequestDomain rippleTransactionRequestDomain =
              rippleTransactionService.processPaymentRequest( authentication,
                                                              paymentRequest,
                                                              sourceAccountDomain,
                                                              destAccountDomain,
                                                              additionalSignerAccounts,
                                                              paymentChannelAccountDomain );

        final RippleTransaction transaction = new RippleTransaction();
        transaction.setId( rippleTransactionRequestDomain.getUuid().toString() );
        transaction.setStatus( RippleTransactionStatus.PENDING );

        final List<RippleTransactionRequestApproverDomain> approverDomains = rippleTransactionRequestDomain.getApproverDomains();
        for ( final RippleTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final Approval approval = new Approval();
            approval.setUserName( approverDomain.getAccountDomain().getUserName() );
            approval.setEmail( approverDomain.getAccountDomain().getEmail() );
            approval.setApprovalStatus( ApprovalStatus.PENDING );

            transaction.addApproval( approval );
        }

        return new ResponseEntity<>( transaction, HttpStatus.ACCEPTED );
    }



    @ApiOperation( value = "Sign and submit a pre-built JSON transaction object.",
                   notes = "Signs a pre-built transaction. Use this if you need to submit a transaction to the Ripple " +
                           "network that is not directly supported by the ChainFront APIs.",
                   nickname = "createRippleTransaction",
                   response = RippleTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = { @AuthorizationScope( scope = "write:ripple_txs",
                                                                         description = "Ability to create Ripple transactions" ) } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 202,
                        message = "Transaction accepted",
                        response = RippleTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    @RequestMapping( value = "/ripple/transactions/json",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<RippleTransaction> signTransaction( final Authentication authentication,
                                                              @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                              @RequestHeader( value = "X-Idempotency-Key",
                                                                              required = true ) final String idempotencyKey,
                                                              @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." ) @RequestHeader( value = "X-TOTP-Code",
                                                                                                                                                                      required = false,
                                                                                                                                                                      defaultValue = "" ) final String totpCode,
                                                              @ApiParam( value = "XDR encoded transaction object.",
                                                                         required = true ) @Valid @RequestBody final RippleTxJsonRequest rippleXdrRequest )
    {
        // TODO: implement

        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        final VaultResponse vaultResponse = vaultOperations.write( getRippleVaultPluginPath( tenantDomain ) + "/json", rippleXdrRequest );

        final RippleTransaction transaction = vaultResponseToTransaction( vaultResponse );

        return new ResponseEntity<>( transaction, HttpStatus.ACCEPTED );
    }


    @ApiOperation( value = "Check the status of a Ripple transaction.",
                   nickname = "getRippleTransactionStatus",
                   notes = "Queries the status of a Ripple transaction based on the ChainFront transaction id.",
                   response = RippleTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ripple_txs",
                                                                    description = "Ability to create payment transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Ripple transaction",
                        response = RippleTransaction.class ) } )
    @ResponseStatus( HttpStatus.OK )
    @RequestMapping( value = "/ripple/transactions/{transactionId}/status",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<RippleTransaction> getRippleTransactionStatus( final Authentication authentication,
                                                                         @ApiParam( value = "Transaction identifier",
                                                                                    required = true ) @PathVariable final String transactionId )
    {
        // Get the transaction response (may be a placeholder record that only links to the request)
        final RippleTransactionResponseDomain rippleTransactionResponseDomain = rippleTransactionService.getRippleTransactionResponse( transactionId );

        // Get the associated transaction request
        final RippleTransactionRequestDomain rippleTransactionRequestDomain = rippleTransactionResponseDomain.getRippleTransactionRequest();

        // Populate our model with the request values
        final RippleTransaction transaction = new RippleTransaction();
        transaction.setId( rippleTransactionRequestDomain.getUuid().toString() );

        final List<RippleTransactionRequestApproverDomain> approverDomains = rippleTransactionRequestDomain.getApproverDomains();
        for ( final RippleTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final Approval approval = new Approval();
            approval.setUserName( approverDomain.getAccountDomain().getUserName() );
            approval.setEmail( approverDomain.getAccountDomain().getEmail() );
            approval.setApprovalStatus( ApprovalStatus.Companion.fromId( approverDomain.getStatus() ) );

            transaction.addApproval( approval );
        }

        // Add in the response values if available
        transaction.setSignedTransaction( rippleTransactionResponseDomain.getSignedTransaction() );

        // TODO: Get status from the db
        //transaction.setStatus( rippleTransactionResponseDomain.getSuccess()  );

        final String transactionHash = rippleTransactionResponseDomain.getTransactionHash();
        // If we have a transaction hash we know that the tx was submitted to the Ripple network. So we fetch the details from rippled here.
        if ( null != transactionHash )
        {
            final RippleTransactionResponseDto rippleTransactionResponseDto = rippleTransactionClient.getTransaction( transactionHash );
            transaction.setAccountSequence( new BigDecimal( rippleTransactionResponseDto.getSequence() ) );
            transaction.setFee( rippleTransactionResponseDto.getFee() );
            transaction.setLedger( Long.valueOf( rippleTransactionResponseDto.getLedger() ) );
            transaction.setSourceAddress( rippleTransactionResponseDto.getSourceAddress() );
            // transaction.setResultCodeMap( rippleTransactionResponseDto.getStatus());
            transaction.setTransactionId( transactionHash );

            final HashMap<String, String> resultCodeMap = new HashMap<>();
            resultCodeMap.put( "status", rippleTransactionResponseDto.getStatus() );
            resultCodeMap.put( "error", rippleTransactionResponseDto.getError() );
            transaction.setResultCodeMap( resultCodeMap );
        }

        return new ResponseEntity<>( transaction, HttpStatus.OK );
    }


    private String getRippleVaultPluginPath( final TenantDomain tenantDomain )
    {
        return "/ripple/" + tenantDomain.getId();
    }


    private RippleTransaction vaultResponseToTransaction( final VaultResponse vaultResponse )
    {
        final Map<String, Object> data = vaultResponse.getData();

        final String sourceAddress = (String) data.get( "source_address" );
        final Long accountSequence = (Long) data.get( "account_sequence" );
        final Integer fee = (Integer) data.get( "fee" );
        final String transactionHash = (String) data.get( "transaction_hash" );
        final String signedTx = (String) data.get( "signed_transaction" );

        final RippleTransaction tx = new RippleTransaction();
        tx.setSourceAddress( sourceAddress );
        tx.setFee( new BigDecimal( fee ) );
        tx.setAccountSequence( new BigDecimal( accountSequence ) );
        tx.setTransactionHash( transactionHash );
        tx.setSignedTransaction( signedTx );

        return tx;
    }
}
