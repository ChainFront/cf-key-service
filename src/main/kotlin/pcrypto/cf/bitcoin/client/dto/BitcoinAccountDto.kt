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

package pcrypto.cf.bitcoin.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal


@JsonIgnoreProperties(ignoreUnknown = true)
data class BitcoinAccountDto(

    var addrStr: String? = null,

    var balance: BigDecimal? = null,

    var balanceSat: BigDecimal? = null,

    var totalReceived: BigDecimal? = null,

    var totalReceivedSat: BigDecimal? = null,

    var totalSent: BigDecimal? = null,

    var totalSentSat: BigDecimal? = null,

    var unconfirmedBalance: BigDecimal? = null,

    var unconfirmedBalanceSat: BigDecimal? = null

)
