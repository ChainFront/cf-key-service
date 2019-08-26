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

package pcrypto.cf.bitcoin.api.controller

import io.swagger.annotations.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pcrypto.cf.account.domain.entity.AccountDomain
import pcrypto.cf.account.domain.service.AccountService
import pcrypto.cf.bitcoin.api.model.BitcoinPaymentRequest
import pcrypto.cf.bitcoin.api.model.BitcoinTransaction
import pcrypto.cf.bitcoin.api.model.BitcoinTransactionStatus
import pcrypto.cf.bitcoin.service.BitcoinTransactionService
import pcrypto.cf.common.api.controller.ApiController
import pcrypto.cf.docs.SwaggerTags
import pcrypto.cf.exception.ApiError
import pcrypto.cf.exception.ConflictException
import pcrypto.cf.mfa.api.model.Approval
import pcrypto.cf.mfa.api.model.ApprovalStatus
import pcrypto.cf.security.domain.CustomUserDetails
import pcrypto.cf.security.service.IdempotencyService
import java.util.*
import javax.validation.Valid


@Api(tags = [SwaggerTags.BITCOIN_TRANSACTIONS])
@ApiController
class BitcoinTransactionsApiController
@Autowired constructor(
    private val accountService: AccountService,
    private val bitcoinTransactionService: BitcoinTransactionService,
    private val idempotencyService: IdempotencyService
) {

    @ApiOperation(
        value = "Create a signed payment transaction.",
        nickname = "createBitcoinPayment",
        notes = "Transfer bitcoin between 2 accounts. The source account " + "must have sufficient unspent transaction outputs to pay the transaction fee.",
        response = BitcoinTransaction::class,
        authorizations = [Authorization(
            value = "OAuth2",
            scopes = [AuthorizationScope(
                scope = "write:bitcoin_txs",
                description = "Ability to create payment transactions"
            )]
        ), Authorization(value = "ApiKey")],
        tags = [SwaggerTags.BITCOIN_TRANSACTIONS]
    )
    @ApiResponses(
        value = [ApiResponse(
            code = 202,
            message = "Payment request accepted",
            response = BitcoinTransaction::class
        ), ApiResponse(
            code = 409,
            message = "Transaction already submitted (duplicate idempotency key)",
            response = ApiError::class
        ), ApiResponse(
            code = 422,
            message = "Unable to process payment request (insufficient funds, etc)",
            response = ApiError::class
        )]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequestMapping(
        value = ["/bitcoin/transactions/payments"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    fun createBitcoinPayment(
        authentication: Authentication,

        @ApiParam(value = "Client generated unique key to guarantee this transaction is only applied once.")
        @RequestHeader(value = "X-Idempotency-Key", required = true)
        idempotencyKey: String,

        @ApiParam(value = "When using APP_TOTP approval, pass the TOTP code in this header.")
        @RequestHeader(value = "X-TOTP-Code", required = false, defaultValue = "")
        totpCode: String,

        @ApiParam(value = "Payment request object", required = true)
        @Valid
        @RequestBody
        paymentRequest: BitcoinPaymentRequest

    ): ResponseEntity<BitcoinTransaction> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Get the source account
        val sourceCfAccountIdentifier = paymentRequest.sourceCfAccountIdentifier
        val sourceAccountDomain = accountService.getCfAccountDomainByIdentifier(tenantDomain, sourceCfAccountIdentifier)

        // Get the destination account
        val destCfAccountIdentifier = paymentRequest.destinationCfAccountIdentifier
        val destAccountDomain = accountService.getCfAccountDomainByIdentifier(tenantDomain, destCfAccountIdentifier)

        // Get the additional signer accounts
        val additionalSignerAccounts = ArrayList<AccountDomain>()
        for (additionalSigner in (paymentRequest.additionalApprovers ?: emptyList())) {
            val additionalSignerAccountDomain =
                accountService.getCfAccountDomainByIdentifier(tenantDomain, additionalSigner)
            additionalSignerAccounts.add(additionalSignerAccountDomain)
        }

        // Validate the idempotency key
        if (!idempotencyService.processIdempotencyKey(authentication, idempotencyKey, sourceAccountDomain.id)) {
            throw ConflictException("A transaction has already been created with this idempotency key")
        }

        // Validate and process all the signing approvals
        val bitcoinTransactionRequestDomain = bitcoinTransactionService.processPaymentRequest(
            authentication,
            paymentRequest,
            sourceAccountDomain,
            destAccountDomain,
            additionalSignerAccounts
        )

        val transaction = BitcoinTransaction()
        transaction.id = bitcoinTransactionRequestDomain.uuid.toString()
        transaction.status = BitcoinTransactionStatus.PENDING

        val approverDomains = bitcoinTransactionRequestDomain.approverDomains
        val txApprovals: ArrayList<Approval> = arrayListOf()
        for (approverDomain in approverDomains) {
            val approval = Approval().apply {
                userName = approverDomain.accountDomain.userName
                email = approverDomain.accountDomain.email
                approvalStatus = ApprovalStatus.PENDING
            }

            txApprovals.add(approval)
        }
        transaction.approvals = txApprovals

        return ResponseEntity(transaction, HttpStatus.ACCEPTED)
    }


    @ApiOperation(
        value = "Check the status of a Bitcoin transaction.",
        nickname = "getBitcoinTransactionStatus",
        notes = "Queries the status of a Bitcoin transaction based on the ChainFront transaction id.",
        response = BitcoinTransaction::class,
        authorizations = [Authorization(
            value = "OAuth2",
            scopes = [AuthorizationScope(
                scope = "write:bitcoin_txs",
                description = "Ability to create payment transactions"
            )]
        ), Authorization(value = "ApiKey")],
        tags = [SwaggerTags.BITCOIN_TRANSACTIONS]
    )
    @ApiResponses(
        value = [ApiResponse(
            code = 200,
            message = "Bitcoin transaction",
            response = BitcoinTransaction::class
        )]
    )
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(
        value = ["/bitcoin/transactions/{transactionId}/status"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.GET]
    )
    fun getBitcoinTransactionStatus(
        authentication: Authentication,
        @ApiParam(value = "Transaction identifier", required = true) @PathVariable transactionId: String
    ): ResponseEntity<BitcoinTransaction> {

        // Get the transaction response (may be a placeholder record that only links to the request)
        val bitcoinTransactionResponseDomain = bitcoinTransactionService.getBitcoinTransactionResponse(transactionId)

        // Get the associated transaction request
        val bitcoinTransactionRequestDomain = bitcoinTransactionResponseDomain.bitcoinTransactionRequest

        // Populate our model with the request values
        val transaction = BitcoinTransaction()
        transaction.id = bitcoinTransactionRequestDomain.uuid.toString()

        val txApprovals: ArrayList<Approval> = arrayListOf()
        val approverDomains = bitcoinTransactionRequestDomain.approverDomains
        for (approverDomain in approverDomains) {
            val approval = Approval().apply {
                userName = approverDomain.accountDomain.userName
                email = approverDomain.accountDomain.email
                approvalStatus = ApprovalStatus.fromId(approverDomain.status)
            }

            txApprovals.add(approval)
        }
        transaction.approvals = txApprovals

        // Add in the response values if available
        transaction.signedTransaction = bitcoinTransactionResponseDomain.signedTransaction

        transaction.succeeded = bitcoinTransactionResponseDomain.success
        transaction.transactionResult = bitcoinTransactionResponseDomain.transactionResult

        val transactionHash = bitcoinTransactionResponseDomain.transactionHash
        // If we have a transaction hash we know that the tx was submitted to the Bitcoin network. So we fetch the details from bitcoind here.
        if (null != transactionHash) {
            val bitcoinTransactionDto = bitcoinTransactionService.getTransaction(transactionHash)

            transaction.confirmations = bitcoinTransactionDto?.confirmations
            transaction.fee = bitcoinTransactionDto?.fees
            transaction.blockHash = bitcoinTransactionDto?.blockhash
            transaction.blockHeight = bitcoinTransactionDto?.blockheight
        }

        return ResponseEntity(transaction, HttpStatus.OK)
    }


    companion object {
        private val log = LoggerFactory.getLogger(BitcoinTransactionsApiController::class.java)
    }
}
