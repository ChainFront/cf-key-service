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

package pcrypto.cf.bitcoin.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.Utils
import org.bitcoinj.params.TestNet3Params
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.MimeTypeUtils
import org.springframework.vault.VaultException
import org.springframework.vault.core.VaultOperations
import pcrypto.cf.account.domain.entity.AccountDomain
import pcrypto.cf.account.domain.repository.AccountConfigurationRepository
import pcrypto.cf.account.value.TxApprovalMethodEnum
import pcrypto.cf.bitcoin.api.model.BitcoinPaymentRequest
import pcrypto.cf.bitcoin.client.BitcoinTransactionBuilder
import pcrypto.cf.bitcoin.client.BitcoindClient
import pcrypto.cf.bitcoin.client.dto.BitcoinTransactionDto
import pcrypto.cf.bitcoin.domain.entity.BitcoinTransactionRequestApproverDomain
import pcrypto.cf.bitcoin.domain.entity.BitcoinTransactionRequestDomain
import pcrypto.cf.bitcoin.domain.entity.BitcoinTransactionResponseDomain
import pcrypto.cf.bitcoin.domain.repository.BitcoinAccountRepository
import pcrypto.cf.bitcoin.domain.repository.BitcoinTransactionRequestApproverRepository
import pcrypto.cf.bitcoin.domain.repository.BitcoinTransactionRequestRepository
import pcrypto.cf.bitcoin.domain.repository.BitcoinTransactionResponseRepository
import pcrypto.cf.bitcoin.stream.BitcoinTransactionApprovalEvent
import pcrypto.cf.bitcoin.stream.BitcoinTransactionApprovalStream
import pcrypto.cf.bitcoin.util.BitcoinConvertUtil
import pcrypto.cf.bitcoin.util.BitcoinCurrencyType
import pcrypto.cf.bitcoin.value.TransactionApprovalStatusEnum
import pcrypto.cf.bitcoin.vault.dto.VaultBitcoinPaymentDomain
import pcrypto.cf.common.domain.TenantDomain
import pcrypto.cf.exception.BadRequestException
import pcrypto.cf.exception.ErrorMessage
import pcrypto.cf.exception.InsufficentBalanceException
import pcrypto.cf.exception.NotFoundException
import pcrypto.cf.mfa.service.authy.AuthyPushApprovalService
import pcrypto.cf.security.domain.CustomUserDetails
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*


@Service
class BitcoinTransactionService
@Autowired constructor(
    private val bitcoinAccountRepository: BitcoinAccountRepository,
    private val bitcoinTransactionRequestRepository: BitcoinTransactionRequestRepository,
    private val bitcoinTransactionRequestApproverRepository: BitcoinTransactionRequestApproverRepository,
    private val bitcoinTransactionResponseRepository: BitcoinTransactionResponseRepository,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val bitcoinTransactionApprovalStream: BitcoinTransactionApprovalStream,
    private val bitcoindClient: BitcoindClient,
    private val accountConfigurationRepository: AccountConfigurationRepository,
    private val authyPushApprovalService: AuthyPushApprovalService,
    private val vaultOperations: VaultOperations,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun processPaymentRequest(
        authentication: Authentication,
        paymentRequest: BitcoinPaymentRequest,
        sourceAccountDomain: AccountDomain,
        destAccountDomain: AccountDomain,
        additionalSignerAccounts: List<AccountDomain>
    ): BitcoinTransactionRequestDomain {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain ?: throw BadRequestException("Invalid authorization")

        // Create our transaction request object
        val transactionRequest = BitcoinTransactionRequestDomain()
        try {
            transactionRequest.bitcoinPaymentRequest = objectMapper.writeValueAsString(paymentRequest)
        } catch (e: JsonProcessingException) {
            throw BadRequestException("Unable to process payment request json. Please check for errors.", e)
        }

        transactionRequest.accountDomain = sourceAccountDomain
        transactionRequest.destAccountDomain = destAccountDomain
        transactionRequest.tenantDomain = tenantDomain
        transactionRequest.amount = paymentRequest.amount
        transactionRequest.assetCode = paymentRequest.currencyType.name
        transactionRequest.memo = paymentRequest.memo

        // Validate that the source account is likely to have sufficient funds for this payment
        val sourceBitcoinAccountDomain = bitcoinAccountRepository.findByCfAccountId(sourceAccountDomain.id)
        sourceBitcoinAccountDomain.orElseThrow { NotFoundException("ChainFront source account " + sourceAccountDomain.id + " does not have a bitcoin account.") }
        val bitcoinAccountDto = bitcoindClient.getAccount(sourceBitcoinAccountDomain.get().bitcoinAddress)
        val availableSatoshis = Coin.valueOf(bitcoinAccountDto!!.balanceSat!!.toLong())
        val paymentAmountInSatoshis = BitcoinConvertUtil.asSatoshis(
            paymentRequest.amount,
            paymentRequest.currencyType
        )
        if (availableSatoshis.longValue() < paymentAmountInSatoshis.toLong()) {
            throw InsufficentBalanceException(
                "The source account has a balance of " + availableSatoshis + " satoshis, which is " +
                        "insufficient for a payment of " + paymentAmountInSatoshis.toLong() + " satoshis."
            )
        }

        // Add all of the approver objects to our transaction request (source account, payment channel, and additional signers)
        val approvers = ArrayList<AccountDomain>()
        approvers.add(sourceAccountDomain)
        approvers.addAll(additionalSignerAccounts)

        // Validate that all the MFA approvers are registered and/or set up properly
        validateMfaApprovers(approvers)

        // Save the transaction request which will generate our internal transaction id
        val persistedTransactionRequest = bitcoinTransactionRequestRepository.save(transactionRequest)

        // Send out MFA approval requests
        val approverDomains = sendMfaApprovalRequests(
            tenantDomain,
            persistedTransactionRequest,
            paymentRequest,
            approvers
        )

        // Record the approval requests in our local db
        persistedTransactionRequest.approverDomains = approverDomains as MutableList
        for (approverDomain in approverDomains) {
            // JPA requires this so the foreign key value is set correctly
            approverDomain.bitcoinTransactionRequest = persistedTransactionRequest

            // Save the approver request record
            bitcoinTransactionRequestApproverRepository.save(approverDomain)
        }

        // Create a placeholder linked db record to hold the transaction response
        val bitcoinTransactionResponseDomain = BitcoinTransactionResponseDomain()
        bitcoinTransactionResponseDomain.accountDomain = sourceAccountDomain
        bitcoinTransactionResponseDomain.bitcoinTransactionRequest = persistedTransactionRequest
        bitcoinTransactionResponseRepository.save(bitcoinTransactionResponseDomain)

        // Return transaction request details
        return persistedTransactionRequest
    }


    fun getBitcoinTransactionRequest(transactionId: String): BitcoinTransactionRequestDomain {
        val optionalBitcoinTransactionRequestDomain =
            bitcoinTransactionRequestRepository.findById(UUID.fromString(transactionId))
        optionalBitcoinTransactionRequestDomain.orElseThrow { NotFoundException("Bitcoin transaction $transactionId not found.") }

        return optionalBitcoinTransactionRequestDomain.get()
    }

    fun getBitcoinTransactionResponse(transactionId: String): BitcoinTransactionResponseDomain {
        val optionalBitcoinTransactionResponseDomain =
            bitcoinTransactionResponseRepository.findByTransactionRequestUUID(UUID.fromString(transactionId))
        optionalBitcoinTransactionResponseDomain.orElseThrow { NotFoundException("Bitcoin transaction $transactionId not found.") }

        return optionalBitcoinTransactionResponseDomain.get()
    }


    fun updateBitcoinTransactionApproval(
        transactionId: String,
        authyUUID: String,
        status: Int
    ): BitcoinTransactionResponseDomain {
        val optionalBitcoinTransactionResponseDomain =
            bitcoinTransactionResponseRepository.findByTransactionRequestUUID(UUID.fromString(transactionId))
        optionalBitcoinTransactionResponseDomain.orElseThrow { NotFoundException("Bitcoin transaction $transactionId not found.") }

        val approverDomains = optionalBitcoinTransactionResponseDomain.get().bitcoinTransactionRequest.approverDomains
        for (approverDomain in approverDomains) {
            if (authyUUID == approverDomain.authyApprovalRequestUUID) {
                // TODO: more validation (username, tenant, etc)
                approverDomain.status = status
                // TODO: save
                bitcoinTransactionRequestApproverRepository.save(approverDomain)
                break
            }
        }

        return optionalBitcoinTransactionResponseDomain.get()
    }


    /**
     * When an approval event fires, this service method is eventually called by the listener. It will check the
     * transaction request for all approvals, and if the requirements have been met, sign and submit the transaction.
     *
     * @param event
     */
    @Transactional
    fun processApprovalEvent(event: BitcoinTransactionApprovalEvent) {
        // Fetch the transaction from the db
        val transactionId = event.transactionId
            ?: throw IllegalArgumentException("Incoming transaction approval event missing transactionId: $event")

        val bitcoinTransactionRequestDomain = getBitcoinTransactionRequest(transactionId)
        val bitcoinTransactionResponseDomain = getBitcoinTransactionResponse(transactionId)

        val approverDomains = bitcoinTransactionRequestDomain.approverDomains
        for (approverDomain in approverDomains) {
            val status = approverDomain.status
            if (status != TransactionApprovalStatusEnum.APPROVED.id) {
                // All approvals are not yet granted, so no need to process further.
                return
            }
        }

        // All approvals granted, send the transaction to Vault for signing, and then submit to Bitcoin
        submitTransaction(bitcoinTransactionRequestDomain, bitcoinTransactionResponseDomain)
    }


    fun getTransaction(txId: String): BitcoinTransactionDto? {
        return bitcoindClient.getTransaction(txId)
    }


    fun submitTransaction(
        bitcoinTransactionRequestDomain: BitcoinTransactionRequestDomain,
        bitcoinTransactionResponseDomain: BitcoinTransactionResponseDomain
    ): BitcoinTransactionResponseDomain {

        try {
            val params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET) ?: TestNet3Params.get()

            val tenantDomain =
                bitcoinTransactionRequestDomain.tenantDomain ?: throw BadRequestException("Invalid authorization")

            val sourceBitcoinAccountDomain =
                bitcoinAccountRepository.findByCfAccountId(bitcoinTransactionRequestDomain.accountDomain.id)
            sourceBitcoinAccountDomain.orElseThrow { NotFoundException("ChainFront source account " + bitcoinTransactionRequestDomain.accountDomain.id + " does not have a bitcoin account.") }

            val destBitcoinAccountDomain =
                bitcoinAccountRepository.findByCfAccountId(bitcoinTransactionRequestDomain.destAccountDomain.id)
            destBitcoinAccountDomain.orElseThrow { NotFoundException("ChainFront destination account " + bitcoinTransactionRequestDomain.destAccountDomain.id + " does not have a bitcoin account.") }

            val optionalBitcoinAccountDomain =
                bitcoinAccountRepository.findByCfAccountId(bitcoinTransactionRequestDomain.accountDomain.id)
            optionalBitcoinAccountDomain.orElseThrow { NotFoundException("ChainFront account " + bitcoinTransactionRequestDomain.accountDomain.id + " does not have a bitcoin account.") }

            val sourceAddress = optionalBitcoinAccountDomain.get().bitcoinAddress
            val destinationAddress = destBitcoinAccountDomain.get().bitcoinAddress
            val amount = bitcoinTransactionRequestDomain.amount!!
            val assetCode = bitcoinTransactionRequestDomain.assetCode!!
            val bitcoinCurrencyType = BitcoinCurrencyType.fromString(assetCode)
            val satoshis = BitcoinConvertUtil.asSatoshis(amount, bitcoinCurrencyType)

            // Build the unsigned transaction
            val unsignedTransaction = createUnsignedTransaction(params, sourceAddress, destinationAddress, satoshis)

            // Validate that we have a sane transaction at this point (will throw an exception if invalid)
            unsignedTransaction.verify()

            // Encode the tx so we can send to Vault for signing
            val txBytes = unsignedTransaction.bitcoinSerialize()
            val encodedTx = Utils.HEX.encode(txBytes)

            // Obtain a signed tx from Vault
            val paymentDomain = VaultBitcoinPaymentDomain()
            paymentDomain.source = bitcoinTransactionRequestDomain.accountDomain.id.toString()
            paymentDomain.destination = bitcoinTransactionRequestDomain.destAccountDomain.id.toString()

            paymentDomain.unsignedTx = encodedTx
            paymentDomain.amount = satoshis.toLong().toString()

            val vaultResponse =
                vaultOperations.write(getBitcoinVaultPluginPath(tenantDomain) + "/payments", paymentDomain)
                    ?: throw VaultException(
                        "An error occurred while creating the payment transaction."
                    )

            val data = vaultResponse.data
                ?: throw VaultException("Vault response when signing transaction contained a null data map.")
            val signedTx = data["signed_transaction"] as String

            // Validate the signed transaction
            val signedTransaction = Transaction(params!!, Utils.HEX.decode(signedTx))
            signedTransaction.verify()
            val signedTransactionBytes = signedTransaction.unsafeBitcoinSerialize()
            val encodedSignedTransaction = Utils.HEX.encode(signedTransactionBytes)
            bitcoinTransactionResponseDomain.transactionHash = signedTransaction.txId.toString()
            bitcoinTransactionResponseDomain.signedTransaction = encodedSignedTransaction


            // Submit the signed tx to Bitcoin
            val txResponse = bitcoindClient.postTransaction(signedTx)

            // Save the transaction response
            //bitcoinTransactionResponseDomain.setLedger( txResponse.getLedger() );
            //bitcoinTransactionResponseDomain.setSuccess( txResponse.isSucceeded() );
            bitcoinTransactionResponseDomain.transactionHash = txResponse!!.txid
            //bitcoinTransactionResponseDomain.setTransactionResult( txResponse.toString() );

            bitcoinTransactionResponseDomain.createdDate = OffsetDateTime.now()

            return bitcoinTransactionResponseRepository.save(bitcoinTransactionResponseDomain)
        } catch (e: Throwable) {
            // If an exception occurs, we try to log it to the transaction record so the user can see what happened.
            // We don't rethrow the exception, otherwise it will rollback the update op.
            log.error("Error occurred while signing bitcoin transaction :", e)
            bitcoinTransactionResponseDomain.success = java.lang.Boolean.FALSE
            bitcoinTransactionResponseDomain.transactionResult = e.message
            bitcoinTransactionResponseDomain.createdDate = OffsetDateTime.now()
            return bitcoinTransactionResponseRepository.save(bitcoinTransactionResponseDomain)
        }

    }

    private fun createUnsignedTransaction(
        params: NetworkParameters,
        sourceAddress: String,
        destinationAddress: String,
        satoshis: BigDecimal
    ): Transaction {

        return BitcoinTransactionBuilder(
            bitcoindClient = bitcoindClient,
            params = params,
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            amount = satoshis,
            memo = ""
        ).build()
    }


    private fun validateMfaApprovers(approvers: List<AccountDomain>) {
        val invalidApprovers = ArrayList<ErrorMessage>()
        for (approver in approvers) {
            // If using Authy, generate a push notification
            if (TxApprovalMethodEnum.AUTHY_PUSH.id == approver.txApprovalMethod) {
                val accountId = approver.id
                val sourceAccountConfigurationDomain = accountConfigurationRepository.findByCfAccountId(accountId)
                if (!sourceAccountConfigurationDomain.isPresent) {
                    throw NotFoundException("Authy push notification configuration for user '" + approver.email + "' not found")
                }
                val hasAuthyApp = authyPushApprovalService.hasAuthyApp(sourceAccountConfigurationDomain.get().authyId)
                if (!hasAuthyApp) {
                    invalidApprovers.add(
                        ErrorMessage(
                            "Required approver '" + approver.email + "' has not yet installed the Authy application.",
                            ""
                        )
                    )
                }
            }
        }
        if (invalidApprovers.isNotEmpty()) {
            throw BadRequestException("One or more approvers are invalid.", invalidApprovers)
        }
    }

    private fun sendMfaApprovalRequests(
        tenantDomain: TenantDomain,
        bitcoinTransactionRequestDomain: BitcoinTransactionRequestDomain,
        paymentRequest: BitcoinPaymentRequest,
        approvers: List<AccountDomain>
    ): List<BitcoinTransactionRequestApproverDomain> {
        val transactionUUID = bitcoinTransactionRequestDomain.uuid

        val approverDomains = ArrayList<BitcoinTransactionRequestApproverDomain>()
        for (approver in approvers) {
            val txRequestApproverDomain = BitcoinTransactionRequestApproverDomain()
            txRequestApproverDomain.accountDomain = approver
            txRequestApproverDomain.bitcoinTransactionRequest = bitcoinTransactionRequestDomain

            // If using Authy, generate a push notification
            if (TxApprovalMethodEnum.AUTHY_PUSH.id == approver.txApprovalMethod) {
                val accountId = approver.id
                val accountConfigurationDomain = accountConfigurationRepository.findByCfAccountId(accountId)
                if (!accountConfigurationDomain.isPresent) {
                    throw NotFoundException("Authy push notification configuration for user '" + approver.email + "' not found")
                }

                val userName = approver.userName
                val authyId = accountConfigurationDomain.get().authyId

                val reason =
                    "Transaction: payment of " + paymentRequest.amount + " tokens to account " + paymentRequest.destinationCfAccountIdentifier + "."

                // Send the approval request
                val approvalRequestUUID = authyPushApprovalService.sendOneTouchToken(
                    tenantDomain,
                    userName,
                    authyId,
                    transactionUUID,
                    "BITCOIN",
                    reason
                )

                txRequestApproverDomain.authyApprovalRequestUUID = approvalRequestUUID
                txRequestApproverDomain.status = TransactionApprovalStatusEnum.PENDING.id

                // Save the approval request record
                bitcoinTransactionRequestApproverRepository.save(txRequestApproverDomain)

                approverDomains.add(txRequestApproverDomain)
            } else if (TxApprovalMethodEnum.IMPLICIT.id == approver.txApprovalMethod) {
                txRequestApproverDomain.status = TransactionApprovalStatusEnum.APPROVED.id

                // Save the approver request record. We need to save this record BEFORE sending the approval event so that
                // the downstream Kafka listener can properly detect that the approval has been granted for this approver.
                bitcoinTransactionRequestApproverRepository.save(txRequestApproverDomain)

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                val approvalEvent = BitcoinTransactionApprovalEvent()
                approvalEvent.tenantId = tenantDomain.code
                approvalEvent.transactionId = transactionUUID.toString()

                val messageChannel = bitcoinTransactionApprovalStream.outboundApproval()
                messageChannel.send(
                    MessageBuilder.withPayload(approvalEvent)
                        .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                        .build()
                )

                approverDomains.add(txRequestApproverDomain)
            }
        }

        return approverDomains
    }


    private fun getBitcoinVaultPluginPath(tenantDomain: TenantDomain): String {
        return "/bitcoin/" + tenantDomain.id!!
    }


    companion object {
        private val log = LoggerFactory.getLogger(BitcoinTransactionService::class.java)
    }
}
