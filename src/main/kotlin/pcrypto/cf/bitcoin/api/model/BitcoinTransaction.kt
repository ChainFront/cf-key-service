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
import pcrypto.cf.mfa.api.model.Approval
import java.math.BigDecimal


/**
 * Transaction
 */
@ApiModel
data class BitcoinTransaction(

    @ApiModelProperty(
        value = "A unique ChainFront id for this transaction. Use this id to query for the transaction status.",
        position = 10,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var id: String? = null,

    @ApiModelProperty(
        value = "The transaction status.",
        position = 20,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var status: BitcoinTransactionStatus? = null,

    @ApiModelProperty(
        value = "Boolean indicating if the transaction succeeded or not.",
        position = 25,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var succeeded: Boolean? = null,

    @ApiModelProperty(
        value = "Optional message describing the transaction result.",
        position = 26,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var transactionResult: String? = null,

    @ApiModelProperty(
        value = "A list of required approvals and their status for this transaction.",
        position = 30,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var approvals: List<Approval>? = arrayListOf(),

    @ApiModelProperty(
        value = "A hex-encoded SHA-256 hash of the transaction.",
        position = 110,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var transactionHash: String? = null,

    @ApiModelProperty(
        value = "The Bitcoin address of the source account for this transaction.",
        position = 120,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var sourceAddress: String? = null,

    @ApiModelProperty(
        value = "The Bitcoin address of the destination account for this transaction.",
        position = 120,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var destinationAddress: String? = null,

    @ApiModelProperty(
        value = "The amount of BTC transferred in this transaction.",
        position = 140,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var amount: BigDecimal? = null,

    @ApiModelProperty(
        value = "The fees paid for this transaction.",
        position = 150,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var fee: BigDecimal? = null,

    @ApiModelProperty(
        value = "The hash of the block containing the mined transaction. Will only be returned for confirmed transactions.",
        position = 170,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var blockHash: String? = null,

    @ApiModelProperty(
        value = "The block containing the mined transaction. Will only be returned for confirmed transactions.",
        position = 180,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var blockHeight: Long? = null,

    @ApiModelProperty(
        value = "The number of times this transaction has been confirmed by miners (i.e. the depth of blocks above the initial block containing the transaction).",
        position = 190,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var confirmations: Int? = null,

    @ApiModelProperty(
        value = "The signed encoded transaction.",
        position = 200,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var signedTransaction: String? = null

)

