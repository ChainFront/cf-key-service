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

package pcrypto.cf.ethereum.api.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import pcrypto.cf.mfa.api.model.Approval;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * Transaction
 */
@Getter
@Setter
@ApiModel
public class EthereumTransaction
{
    @ApiModelProperty( value = "A unique ChainFront id for this transaction. Use this id to query for the transaction status.",
                       position = 10,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String id = null;

    @ApiModelProperty( value = "The transaction status.",
                       position = 20,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private EthereumTransactionStatus status = null;


    @ApiModelProperty( value = "A list of required approvals and their status for this transaction.",
                       position = 30,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private List<Approval> approvals = new ArrayList<>();


    @ApiModelProperty( value = "Hash of the transaction.",
                       position = 100,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String transactionHash = null;

    @ApiModelProperty( value = "The Ethereum address of the sender.",
                       position = 110,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String sourceAddress = null;

    @ApiModelProperty( value = "The Ethereum address of the destination. Not set for contract creation transactions.",
                       position = 120,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String destinationAddress = null;

    @ApiModelProperty( value = "Amount of wei transferred.",
                       position = 130,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private BigDecimal amount = null;

    @ApiModelProperty( value = "The sequence number of this transaction (scoped to the source account).",
                       position = 140,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private BigDecimal nonce = null;

    @ApiModelProperty( value = "The gas price for the transaction.",
                       position = 150,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    @Valid
    private BigDecimal gasPrice = null;

    @ApiModelProperty( value = "A list of log messages from the Ethereum network for the operations executed as part of this transaction.",
                       position = 180,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private List<String> logs = new ArrayList<>();

    @ApiModelProperty( value = "The signed encoded transaction.",
                       position = 200,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String signedTransaction = null;


    public void addApproval( final Approval approval )
    {
        approvals.add( approval );
    }
}

