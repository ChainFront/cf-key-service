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

package pcrypto.cf.stellar.api.controller;

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
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.security.service.IdempotencyService;
import pcrypto.cf.stellar.api.model.StellarPaymentRequest;
import pcrypto.cf.stellar.api.model.StellarTransaction;
import pcrypto.cf.stellar.api.model.StellarTransactionStatus;
import pcrypto.cf.stellar.api.model.StellarXdrRequest;
import pcrypto.cf.stellar.client.StellarNetworkService;
import pcrypto.cf.stellar.client.response.DecoratedTransactionResponse;
import pcrypto.cf.stellar.domain.entity.StellarTransactionRequestApproverDomain;
import pcrypto.cf.stellar.domain.entity.StellarTransactionRequestDomain;
import pcrypto.cf.stellar.domain.entity.StellarTransactionResponseDomain;
import pcrypto.cf.stellar.service.StellarTransactionService;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Api( tags = { SwaggerTags.STELLAR_TRANSACTIONS } )
@ApiController
public class StellarTransactionsApiController
{

    private static final Logger log = LoggerFactory.getLogger( StellarTransactionsApiController.class );


    private final VaultOperations vaultOperations;
    private final AccountService accountService;
    private final AccountConfigurationRepository accountConfigurationRepository;
    private final StellarTransactionService stellarTransactionService;
    private final StellarNetworkService stellarNetworkService;
    private final TotpService totpService;
    private final AuthyPushApprovalService authyPushApprovalService;
    private final IdempotencyService idempotencyService;


    @Autowired
    public StellarTransactionsApiController( final VaultOperations vaultOperations,
                                             final AccountService accountService,
                                             final AccountConfigurationRepository accountConfigurationRepository,
                                             final StellarTransactionService stellarTransactionService,
                                             final StellarNetworkService stellarNetworkService,
                                             final TotpService totpService,
                                             final AuthyPushApprovalService authyPushApprovalService,
                                             final IdempotencyService idempotencyService )
    {
        this.vaultOperations = vaultOperations;
        this.accountService = accountService;
        this.accountConfigurationRepository = accountConfigurationRepository;
        this.stellarTransactionService = stellarTransactionService;
        this.stellarNetworkService = stellarNetworkService;
        this.totpService = totpService;
        this.authyPushApprovalService = authyPushApprovalService;
        this.idempotencyService = idempotencyService;
    }


    @ApiOperation( value = "Create a signed payment transaction.",
                   nickname = "createStellarPayment",
                   notes = "Transfers an asset between 2 accounts. The source account (or optional payment channel account) " +
                           "must have sufficient XLM balance to pay the transaction fee.",
                   response = StellarTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:stellar_txs",
                                                                    description = "Ability to create payment transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.STELLAR_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 202,
                        message = "Payment request accepted",
                        response = StellarTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    @RequestMapping( value = "/stellar/transactions/payments",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<StellarTransaction> createStellarPayment( final Authentication authentication,
                                                                    @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                                    @RequestHeader( value = "X-Idempotency-Key",
                                                                                    required = true ) final String idempotencyKey,
                                                                    @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." )
                                                                    @RequestHeader( value = "X-TOTP-Code",
                                                                                    required = false,
                                                                                    defaultValue = "" ) final String totpCode,
                                                                    @ApiParam( value = "Payment request object",
                                                                               required = true ) @Valid @RequestBody final StellarPaymentRequest paymentRequest )
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
        final StellarTransactionRequestDomain stellarTransactionRequestDomain =
              stellarTransactionService.processPaymentRequest( authentication,
                                                               paymentRequest,
                                                               sourceAccountDomain,
                                                               destAccountDomain,
                                                               additionalSignerAccounts,
                                                               paymentChannelAccountDomain );

        final StellarTransaction transaction = new StellarTransaction();
        transaction.setId( stellarTransactionRequestDomain.getUuid().toString() );
        transaction.setStatus( StellarTransactionStatus.PENDING );

        final List<StellarTransactionRequestApproverDomain> approverDomains = stellarTransactionRequestDomain.getApproverDomains();
        for ( final StellarTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final Approval approval = new Approval();
            approval.setUserName( approverDomain.getAccountDomain().getUserName() );
            approval.setEmail( approverDomain.getAccountDomain().getEmail() );
            approval.setApprovalStatus( ApprovalStatus.PENDING );

            transaction.addApproval( approval );
        }

        return new ResponseEntity<>( transaction, HttpStatus.ACCEPTED );
    }



    @ApiOperation( value = "Sign an XDR-encoded transaction object.",
                   notes = "Signs a pre-built transaction encoded as XDR. Use this if you need to submit a transaction to the Stellar " +
                           "network that is not directly supported by the ChainFront APIs.",
                   nickname = "createStellarTransaction",
                   response = StellarTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = { @AuthorizationScope( scope = "write:stellar_txs",
                                                                         description = "Ability to create Stellar transactions" ) } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.STELLAR_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 202,
                        message = "Transaction accepted",
                        response = StellarTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    @RequestMapping( value = "/stellar/transactions/xdr",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<StellarTransaction> signTransaction( final Authentication authentication,
                                                               @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                               @RequestHeader( value = "X-Idempotency-Key",
                                                                               required = true ) final String idempotencyKey,
                                                               @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." ) @RequestHeader( value = "X-TOTP-Code",
                                                                                                                                                                       required = false,
                                                                                                                                                                       defaultValue = "" ) final String totpCode,
                                                               @ApiParam( value = "XDR encoded transaction object.",
                                                                          required = true ) @Valid @RequestBody final StellarXdrRequest stellarXdrRequest )
    {
        // TODO: implement

        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        final VaultResponse vaultResponse = vaultOperations.write( getStellarVaultPluginPath( tenantDomain ) + "/xdr", stellarXdrRequest );

        final StellarTransaction transaction = vaultResponseToTransaction( vaultResponse );

        return new ResponseEntity<>( transaction, HttpStatus.ACCEPTED );
    }


    @ApiOperation( value = "Check the status of a Stellar transaction.",
                   nickname = "getStellarTransactionStatus",
                   notes = "Queries the status of a Stellar transaction based on the ChainFront transaction id.",
                   response = StellarTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:stellar_txs",
                                                                    description = "Ability to create payment transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.STELLAR_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Stellar transaction",
                        response = StellarTransaction.class ) } )
    @ResponseStatus( HttpStatus.OK )
    @RequestMapping( value = "/stellar/transactions/{transactionId}/status",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<StellarTransaction> getStellarTransactionStatus( final Authentication authentication,
                                                                           @ApiParam( value = "Transaction identifier",
                                                                                      required = true ) @PathVariable final String transactionId )
    {
        // Get the transaction response (may be a placeholder record that only links to the request)
        final StellarTransactionResponseDomain stellarTransactionResponseDomain = stellarTransactionService.getStellarTransactionResponse( transactionId );

        // Get the associated transaction request
        final StellarTransactionRequestDomain stellarTransactionRequestDomain = stellarTransactionResponseDomain.getStellarTransactionRequest();

        // Populate our model with the request values
        final StellarTransaction transaction = new StellarTransaction();
        transaction.setId( stellarTransactionRequestDomain.getUuid().toString() );

        final List<StellarTransactionRequestApproverDomain> approverDomains = stellarTransactionRequestDomain.getApproverDomains();
        for ( final StellarTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final Approval approval = new Approval();
            approval.setUserName( approverDomain.getAccountDomain().getUserName() );
            approval.setEmail( approverDomain.getAccountDomain().getEmail() );
            approval.setApprovalStatus( ApprovalStatus.Companion.fromId( approverDomain.getStatus() ) );

            transaction.addApproval( approval );
        }

        // Add in the response values if available
        transaction.setSignedTransaction( stellarTransactionResponseDomain.getSignedTransaction() );

        // TODO: Get status from the db
        //transaction.setStatus( stellarTransactionResponseDomain.getSuccess()  );

        final String transactionHash = stellarTransactionResponseDomain.getTransactionHash();
        // If we have a transaction hash we know that the tx was submitted to the Stellar network. So we fetch the details from Horizon here.
        if ( null != transactionHash )
        {
            final DecoratedTransactionResponse decoratedTransactionResponse = stellarNetworkService.getTransaction( transactionHash );
            transaction.setAccountSequence( new BigDecimal( decoratedTransactionResponse.getSourceAccountSequence() ) );
            transaction.setFee( new BigDecimal( decoratedTransactionResponse.getFeePaid() ) );
            transaction.setLedger( decoratedTransactionResponse.getLedger() );
            transaction.setSourceAddress( decoratedTransactionResponse.getSourceAccount().getAccountId() );
            transaction.setResultCodeMap( decoratedTransactionResponse.getResultCodeMap() );
            transaction.setTransactionId( decoratedTransactionResponse.getHash() );
        }

        return new ResponseEntity<>( transaction, HttpStatus.OK );
    }


    private String getStellarVaultPluginPath( final TenantDomain tenantDomain )
    {
        return "/stellar/" + tenantDomain.getId();
    }


    private StellarTransaction vaultResponseToTransaction( final VaultResponse vaultResponse )
    {
        final Map<String, Object> data = vaultResponse.getData();

        final String sourceAddress = (String) data.get( "source_address" );
        final Long accountSequence = (Long) data.get( "account_sequence" );
        final Integer fee = (Integer) data.get( "fee" );
        final String transactionHash = (String) data.get( "transaction_hash" );
        final String signedTx = (String) data.get( "signed_transaction" );

        final StellarTransaction tx = new StellarTransaction();
        tx.setSourceAddress( sourceAddress );
        tx.setFee( new BigDecimal( fee ) );
        tx.setAccountSequence( new BigDecimal( accountSequence ) );
        tx.setTransactionHash( transactionHash );
        tx.setSignedTransaction( signedTx );

        return tx;
    }
}
