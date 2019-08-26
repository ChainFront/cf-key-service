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

package pcrypto.cf.bitcoin.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.OffsetDateTime
import java.util.*


@ApiModel
data class BitcoinAccount(

    @ApiModelProperty(
        value = "The ChainFront id which owns this account.",
        position = 10,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var cfAccountId: Long,


    @ApiModelProperty(
        value = "The public address of this account.",
        position = 20,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var address: String,

    @ApiModelProperty(
        value = "A list of the balances held by this account.",
        position = 30,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var balances: List<Balance>? = null,

    @ApiModelProperty(
        value = "An optional list of addresses for which this account is prohibited to transact.",
        position = 100
    )
    var blacklistAddresses: List<String> = ArrayList(),

    @ApiModelProperty(
        value = "An optional list of addresses for which this account is allowed to transact. If empty, " + "there are no restrictions on who this account can transact with (subject to any optional blacklist addresses.",
        position = 110
    )
    var whitelistAddresses: List<String> = ArrayList(),

    @ApiModelProperty(
        value = "Date the account was created.",
        position = 1000,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    @JsonProperty("createdDate")
    var createdDate: OffsetDateTime? = null,

    @ApiModelProperty(
        value = "Date the account was last updated.",
        position = 1010,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    @JsonProperty("lastModifiedDate")
    var lastModifiedDate: OffsetDateTime? = null

) {
    class Balance(var assetType: String, var assetCode: String, var amount: String)
}

