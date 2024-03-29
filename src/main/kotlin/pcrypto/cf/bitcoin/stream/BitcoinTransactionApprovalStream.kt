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

import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.Output
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.SubscribableChannel


interface BitcoinTransactionApprovalStream {

    @Input(APPROVAL_INBOUND)
    fun inboundApproval(): SubscribableChannel

    @Output(APPROVAL_OUTBOUND)
    fun outboundApproval(): MessageChannel

    companion object {
        const val APPROVAL_OUTBOUND = "bitcoin-approval-outbound"
        const val APPROVAL_INBOUND = "bitcoin-approval-inbound"
    }
}
