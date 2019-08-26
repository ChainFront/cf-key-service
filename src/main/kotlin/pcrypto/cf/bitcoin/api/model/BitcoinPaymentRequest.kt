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

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import pcrypto.cf.account.api.model.AccountIdentifier
import pcrypto.cf.bitcoin.util.BitcoinCurrencyType
import java.math.BigDecimal
import java.util.*
import javax.validation.constraints.NotNull


/**
 * PaymentRequest
 */
@ApiModel
data class BitcoinPaymentRequest(

    @NotNull
    @ApiModelProperty(required = true, value = "Identifier of the ChainFront source account.", position = 10)
    var sourceCfAccountIdentifier: AccountIdentifier,

    @NotNull
    @ApiModelProperty(required = true, value = "Identifier of the ChainFront destination account.", position = 20)
    var destinationCfAccountIdentifier: AccountIdentifier,

    @NotNull
    @ApiModelProperty(required = true, example = "0.01", value = "The amount of currency to pay.", position = 30)
    var amount: BigDecimal,

    @NotNull
    @ApiModelProperty(
        required = true,
        example = "BTC",
        value = "The type of currency specified in the amount field.",
        position = 40
    )
    var currencyType: BitcoinCurrencyType,

    @ApiModelProperty(
        value = "An optional list of ChainFront accounts required to approve this transaction.",
        position = 70
    )
    var additionalApprovers: List<AccountIdentifier>? = ArrayList(),

    @ApiModelProperty(value = "An optional memo to include with this transaction.", position = 80)
    var memo: String? = null

)

