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

package pcrypto.cf.bitcoin.stream

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import pcrypto.cf.bitcoin.service.BitcoinTransactionService
import pcrypto.cf.security.web.TenantContext


@Component
class BitcoinTransactionApprovalListener
@Autowired constructor(private val bitcoinTransactionService: BitcoinTransactionService) {

    @StreamListener(BitcoinTransactionApprovalStream.APPROVAL_INBOUND)
    fun handleApprovalEvent(@Payload event: BitcoinTransactionApprovalEvent) {
        log.info("Received event: {}", event)

        // Since we're outside of the scope of a multitenant call, we set the tenant id here for downstream db calls
        val tenantId = event.tenantId
        TenantContext.setCurrentTenant(tenantId!!.toLowerCase())

        bitcoinTransactionService.processApprovalEvent(event)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BitcoinTransactionApprovalListener::class.java)
    }
}
