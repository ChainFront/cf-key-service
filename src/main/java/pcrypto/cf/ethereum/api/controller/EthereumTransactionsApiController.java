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

package pcrypto.cf.ethereum.api.controller;

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
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import pcrypto.cf.account.api.model.AccountIdentifier;
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.account.domain.service.AccountService;
import pcrypto.cf.common.api.controller.ApiController;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.docs.SwaggerTags;
import pcrypto.cf.ethereum.api.model.EthereumPaymentRequest;
import pcrypto.cf.ethereum.api.model.EthereumTokenPaymentRequest;
import pcrypto.cf.ethereum.api.model.EthereumTransaction;
import pcrypto.cf.ethereum.api.model.EthereumTransactionStatus;
import pcrypto.cf.ethereum.client.EthereumTransactionClient;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionRequestApproverDomain;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionRequestDomain;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionResponseDomain;
import pcrypto.cf.ethereum.service.EthereumTransactionService;
import pcrypto.cf.exception.ApiError;
import pcrypto.cf.exception.ConflictException;
import pcrypto.cf.mfa.api.model.Approval;
import pcrypto.cf.mfa.api.model.ApprovalStatus;
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.security.service.IdempotencyService;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Api( tags = { SwaggerTags.ETH_TRANSACTIONS } )
@ApiController
public class EthereumTransactionsApiController
{

    private static final Logger log = LoggerFactory.getLogger( EthereumTransactionsApiController.class );

    private final VaultOperations vaultOperations;
    private final AccountService accountService;
    private final EthereumTransactionService ethereumTransactionService;
    private final EthereumTransactionClient ethereumTransactionClient;
    private final IdempotencyService idempotencyService;


    @Autowired
    public EthereumTransactionsApiController( final VaultOperations vaultOperations,
                                              final AccountService accountService,
                                              final EthereumTransactionService ethereumTransactionService,
                                              final EthereumTransactionClient ethereumTransactionClient,
                                              final IdempotencyService idempotencyService )
    {
        this.vaultOperations = vaultOperations;
        this.accountService = accountService;
        this.ethereumTransactionService = ethereumTransactionService;
        this.ethereumTransactionClient = ethereumTransactionClient;
        this.idempotencyService = idempotencyService;
    }


    @ApiOperation( value = "Create a signed payment transaction to transfer ETH between 2 accounts",
                   nickname = "createPayment",
                   notes = "Transfers Ether between 2 accounts. The source account must have sufficient balance to pay the gas fee.",
                   response = EthereumTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ethereum_txs",
                                                                    description = "Ability to create payment transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 202,
                        message = "Successful operation",
                        response = EthereumTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    @RequestMapping( value = "/ethereum/transactions/payments",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<EthereumTransaction> createPayment( final Authentication authentication,
                                                              @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                              @RequestHeader( value = "X-Idempotency-Key",
                                                                              required = true ) final String idempotencyKey,
                                                              @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." ) @RequestHeader( value = "X-TOTP-Code",
                                                                                                                                                                      required = false,
                                                                                                                                                                      defaultValue = "" ) final String totpCode,
                                                              @ApiParam( value = "Payment request object",
                                                                         required = true ) @Valid @RequestBody final EthereumPaymentRequest paymentRequest )
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

        // Get the additional signer accounts
        final List<AccountDomain> additionalSignerAccounts = new ArrayList<>();
        for ( final AccountIdentifier additionalSigner : paymentRequest.getAdditionalSigners() )
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
        final EthereumTransactionRequestDomain ethereumTransactionRequestDomain =
              ethereumTransactionService.processPaymentRequest( authentication,
                                                                paymentRequest,
                                                                sourceAccountDomain,
                                                                destAccountDomain,
                                                                additionalSignerAccounts );

        final EthereumTransaction transaction = new EthereumTransaction();
        transaction.setId( ethereumTransactionRequestDomain.getUuid().toString() );
        transaction.setStatus( EthereumTransactionStatus.PENDING );

        final List<EthereumTransactionRequestApproverDomain> approverDomains = ethereumTransactionRequestDomain.getApproverDomains();
        for ( final EthereumTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final Approval approval = new Approval();
            approval.setUserName( approverDomain.getAccountDomain().getUserName() );
            approval.setEmail( approverDomain.getAccountDomain().getEmail() );
            approval.setApprovalStatus( ApprovalStatus.PENDING );

            transaction.addApproval( approval );
        }

        return new ResponseEntity<>( transaction, HttpStatus.ACCEPTED );
    }


    @ApiOperation( value = "Create a signed payment transaction to transfer ERC-20 tokens between 2 accounts",
                   nickname = "createTokenPayment",
                   notes = "Transfers ERC-20 tokens between 2 accounts. The source account must have sufficient balance to pay the gas fee.",
                   response = EthereumTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ethereum_txs",
                                                                    description = "Ability to create payment transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 202,
                        message = "Accepted",
                        response = EthereumTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    @RequestMapping( value = "/ethereum/transactions/tokenpayments",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<EthereumTransaction> createTokenPayment( final Authentication authentication,
                                                                   @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                                   @RequestHeader( value = "X-Idempotency-Key",
                                                                                   required = true ) final String idempotencyKey,
                                                                   @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." ) @RequestHeader( value = "X-TOTP-Code",
                                                                                                                                                                           required = false,
                                                                                                                                                                           defaultValue = "" ) final String totpCode,
                                                                   @ApiParam( value = "Token payment request object",
                                                                              required = true ) @Valid @RequestBody final EthereumTokenPaymentRequest body )
    {
        final VaultResponse vaultResponse = vaultOperations.write( "ethereum/transactions/tokenpayments", body );

        final EthereumTransaction ethereumTransaction = vaultResponseToTransaction( vaultResponse );

        return new ResponseEntity<EthereumTransaction>( ethereumTransaction, HttpStatus.ACCEPTED );
    }


    @ApiOperation( value = "Check the status of an Ethereum transaction.",
                   nickname = "getEthereumTransaction",
                   notes = "Queries the status of an Ethereum transaction based on the ChainFront transaction id.",
                   response = EthereumTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ethereum_txs",
                                                                    description = "Ability to read Ethereum transactions" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_TRANSACTIONS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Ethereum transaction",
                        response = EthereumTransaction.class ) } )
    @ResponseStatus( HttpStatus.OK )
    @RequestMapping( value = "/ethereum/transactions/{transactionId}/status",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<EthereumTransaction> getEthereumTransactionStatus( final Authentication authentication,
                                                                             @ApiParam( value = "Transaction identifier",
                                                                                        required = true ) @PathVariable final String transactionId )
    {
        // Get the transaction response (may be a placeholder record that only links to the request)
        final EthereumTransactionResponseDomain ethereumTransactionResponseDomain = ethereumTransactionService.getEthereumTransactionResponse( transactionId );

        // Get the associated transaction request
        final EthereumTransactionRequestDomain ethereumTransactionRequestDomain = ethereumTransactionResponseDomain.getEthereumTransactionRequest();

        // Populate our model with the request values
        final EthereumTransaction transaction = new EthereumTransaction();
        transaction.setId( ethereumTransactionRequestDomain.getUuid().toString() );

        final List<EthereumTransactionRequestApproverDomain> approverDomains = ethereumTransactionRequestDomain.getApproverDomains();
        for ( final EthereumTransactionRequestApproverDomain approverDomain : approverDomains )
        {
            final Approval approval = new Approval();
            approval.setUserName( approverDomain.getAccountDomain().getUserName() );
            approval.setEmail( approverDomain.getAccountDomain().getEmail() );
            approval.setApprovalStatus( ApprovalStatus.Companion.fromId( approverDomain.getStatus() ) );

            transaction.addApproval( approval );
        }

        // Add in the response values if available
        transaction.setSignedTransaction( ethereumTransactionResponseDomain.getSignedTransaction() );

        // Set the tx status to pending until we check for a valid hash and blocknumber below
        transaction.setStatus( EthereumTransactionStatus.PENDING );

        final String transactionHash = ethereumTransactionResponseDomain.getTransactionHash();
        transaction.setTransactionHash( transactionHash );

        // If we have a transaction hash we know that the tx was submitted to the Ethereum network. So we fetch the details here.
        if ( null != transactionHash )
        {
            final Optional<TransactionReceipt> transactionReceiptOptional = ethereumTransactionClient.getTransaction( transactionHash );
            if ( transactionReceiptOptional.isPresent() )
            {
                transaction.setNonce( new BigDecimal( transactionReceiptOptional.get().getTransactionIndex() ) );
                transaction.setGasPrice( new BigDecimal( transactionReceiptOptional.get().getGasUsed() ) );
                transaction.setSourceAddress( transactionReceiptOptional.get().getFrom() );
                transaction.setDestinationAddress( transactionReceiptOptional.get().getTo() );
                final BigInteger blockNumber = transactionReceiptOptional.get().getBlockNumber();
                if ( blockNumber != null && blockNumber.intValue() > 0 )
                {
                    transaction.setStatus( EthereumTransactionStatus.COMPLETE );
                }

                // Get the logs and convert them to strings
                final List<Log> logs = transactionReceiptOptional.get().getLogs();
                final List<String> logStrings = logs.stream()
                                                    .map( Log::toString )
                                                    .collect( Collectors.toList() );
                transaction.setLogs( logStrings );
            }
        }

        return new ResponseEntity<>( transaction, HttpStatus.OK );
    }


    private EthereumTransaction vaultResponseToTransaction( final VaultResponse vaultResponse )
    {
        final Map<String, Object> data = vaultResponse.getData();

        final String fromAddress = (String) data.get( "from_address" );
        final String toAddress = (String) data.get( "to_address" );
        final Integer gasPrice = (Integer) data.get( "gas_price" );
        final String transactionHash = (String) data.get( "transaction_hash" );
        final String signedTx = (String) data.get( "signed_tx" );

        final EthereumTransaction tx = new EthereumTransaction();
        tx.setDestinationAddress( toAddress );
        tx.setSourceAddress( fromAddress );
        tx.setGasPrice( new BigDecimal( gasPrice ) );
        tx.setTransactionHash( transactionHash );
        tx.setSignedTransaction( signedTx );

        return tx;
    }
}
