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

package pcrypto.cf.webhook.api.controller

import com.authy.AuthyUtil
import com.authy.OneTouchException
import org.apache.commons.io.IOUtils
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.util.LinkedCaseInsensitiveMap
import org.springframework.util.MimeTypeUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import pcrypto.cf.bitcoin.service.BitcoinTransactionService
import pcrypto.cf.bitcoin.stream.BitcoinTransactionApprovalEvent
import pcrypto.cf.bitcoin.stream.BitcoinTransactionApprovalStream
import pcrypto.cf.ethereum.service.EthereumTransactionService
import pcrypto.cf.ethereum.stream.EthereumTransactionApprovalEvent
import pcrypto.cf.ethereum.stream.EthereumTransactionApprovalStream
import pcrypto.cf.exception.MfaException
import pcrypto.cf.mfa.api.model.ApprovalStatus
import pcrypto.cf.ripple.service.RippleTransactionService
import pcrypto.cf.ripple.stream.RippleTransactionApprovalEvent
import pcrypto.cf.ripple.stream.RippleTransactionApprovalStream
import pcrypto.cf.security.web.TenantContext
import pcrypto.cf.stellar.service.StellarTransactionService
import pcrypto.cf.stellar.stream.StellarTransactionApprovalEvent
import pcrypto.cf.stellar.stream.StellarTransactionApprovalStream
import java.util.*
import javax.servlet.http.HttpServletRequest


@RestController
@RequestMapping("/webhooks/v1/authy")
class AuthyWebhookApiController
@Autowired constructor(
    private val stellarTransactionService: StellarTransactionService,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val stellarTransactionApprovalStream: StellarTransactionApprovalStream,
    private val rippleTransactionService: RippleTransactionService,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val rippleTransactionApprovalStream: RippleTransactionApprovalStream,
    private val ethereumTransactionService: EthereumTransactionService,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val ethereumTransactionApprovalStream: EthereumTransactionApprovalStream,
    private val bitcoinTransactionService: BitcoinTransactionService,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val bitcoinTransactionApprovalStream: BitcoinTransactionApprovalStream
) {

    @Value("\${authy.api.key}")
    private val authyApiKey: String? = null

    @RequestMapping(
        value = ["/callbacks"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    @Throws(Exception::class)
    fun authyWebhookPost(request: HttpServletRequest) {

        // Get the headers as a map (for some reason the @RequestHeader Spring method isn't picking up all the request headers in AWS)
        val headers = LinkedCaseInsensitiveMap<String>()
        val headerNames = request.headerNames
        while (headerNames.hasMoreElements()) {
            val key = headerNames.nextElement()
            val value = request.getHeader(key)
            headers[key] = value
        }

        // Determine the webhook request url
        val forwardedProtocol = headers["X-Forwarded-Proto"]
        val protocol = forwardedProtocol ?: request.scheme
        val url = String.format(
            "%s://%s%s",
            protocol,
            request.serverName,
            request.servletPath
        )

        // Fetch body
        val body = IOUtils.toString(request.reader)

        log.info("Authy callback body: {}", body)

        // Validate the incoming webhook signature
        val isValidSignature: Boolean
        try {
            isValidSignature = AuthyUtil.validateSignatureForPost(
                body,
                headers,
                url,
                authyApiKey!!
            )
        } catch (e: OneTouchException) {
            log.error("Unable to process incoming authy webhook callback", e)
            throw e
        }

        if (!isValidSignature) {
            val msg = "Invalid signature on incoming authy webhook callback"
            log.error(msg)
            throw MfaException(msg)
        }

        // Process the valid incoming event
        val params = HashMap<String, String>()
        AuthyUtil.extract("", JSONObject(body), params)

        // Get the values we need from the parsed map
        val authyUUID = params["uuid"]
            ?: throw MfaException("Missing authy uuid in authy approval callback : params = $params")

        val statusStr = params["status"]
            ?: throw MfaException("Missing authy status in authy approval callback : params = $params")

        val status: Int
        status = when {
            statusStr.equals("approved", ignoreCase = true) -> ApprovalStatus.SIGNED.id
            statusStr.equals("denied", ignoreCase = true) -> ApprovalStatus.DENIED.id
            else -> {
                log.error("Unknown status from Authy service: {}, authyUuid: {}.", statusStr, authyUUID)
                ApprovalStatus.PENDING.id
            }
        }

        val tenantId = params["approval_request[transaction][hidden_details][tenant_id]"]
            ?: throw MfaException("Missing tenant_id param in authy approval callback : params = $params")

        val chainType = params["approval_request[transaction][hidden_details][chain_type]"]
            ?: throw MfaException("Missing chain_type param in authy approval callback : params = $params")

        val transactionId = params["approval_request[transaction][hidden_details][transaction_id]"]
            ?: throw MfaException("Missing transaction_id param in authy approval callback : params = $params")

        log.info(
            "Processing incoming authy callback for tenantId: {}, authyUuid: {}, transactionId: {}, and chainType {}",
            tenantId,
            authyUUID,
            transactionId,
            chainType
        )

        // Since we're outside of the scope of a multitenant call, we set the tenant id here for downstream db calls
        TenantContext.setCurrentTenant(tenantId.toLowerCase())

        when (chainType) {
            "STELLAR" -> {
                stellarTransactionService.updateStellarTransactionApproval(transactionId, authyUUID, status)

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                val approvalEvent = StellarTransactionApprovalEvent()
                approvalEvent.tenantId = tenantId
                approvalEvent.transactionId = transactionId

                val messageChannel = stellarTransactionApprovalStream.outboundApproval()
                messageChannel.send(
                    MessageBuilder.withPayload(approvalEvent)
                        .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                        .build()
                )
            }
            "RIPPLE" -> {
                rippleTransactionService.updateRippleTransactionApproval(transactionId, authyUUID, status)

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                val approvalEvent = RippleTransactionApprovalEvent()
                approvalEvent.tenantId = tenantId
                approvalEvent.transactionId = transactionId

                val messageChannel = rippleTransactionApprovalStream.outboundApproval()
                messageChannel.send(
                    MessageBuilder.withPayload(approvalEvent)
                        .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                        .build()
                )
            }
            "ETHEREUM" -> {
                ethereumTransactionService.updateEthereumTransactionApproval(transactionId, authyUUID, status)

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                val approvalEvent = EthereumTransactionApprovalEvent()
                approvalEvent.tenantId = tenantId
                approvalEvent.transactionId = transactionId

                val messageChannel = ethereumTransactionApprovalStream.outboundApproval()
                messageChannel.send(
                    MessageBuilder.withPayload(approvalEvent)
                        .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                        .build()
                )
            }
            "BITCOIN" -> {
                bitcoinTransactionService.updateBitcoinTransactionApproval(transactionId, authyUUID, status)

                // Notify the downstream listener of an approval event. The listener is responsible for determining what actions
                // need to be taken (ex. if all approvals granted, sign and submit tx)
                val approvalEvent = BitcoinTransactionApprovalEvent()
                approvalEvent.tenantId = tenantId
                approvalEvent.transactionId = transactionId

                val messageChannel = bitcoinTransactionApprovalStream.outboundApproval()
                messageChannel.send(
                    MessageBuilder.withPayload(approvalEvent)
                        .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                        .build()
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthyWebhookApiController::class.java)
    }
}
