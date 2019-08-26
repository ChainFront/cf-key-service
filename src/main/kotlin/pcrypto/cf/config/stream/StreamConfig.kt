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

package pcrypto.cf.config.stream

import org.springframework.cloud.stream.annotation.EnableBinding
import pcrypto.cf.bitcoin.stream.BitcoinTransactionApprovalStream
import pcrypto.cf.ethereum.stream.EthereumTransactionApprovalStream
import pcrypto.cf.ripple.stream.RippleTransactionApprovalStream
import pcrypto.cf.stellar.stream.StellarTransactionApprovalStream


@EnableBinding(
    StellarTransactionApprovalStream::class,
    RippleTransactionApprovalStream::class,
    EthereumTransactionApprovalStream::class,
    BitcoinTransactionApprovalStream::class
)
class StreamConfig
