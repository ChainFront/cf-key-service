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

package pcrypto.cf.ripple.api.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import pcrypto.cf.mfa.api.model.Approval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Transaction
 */
@ApiModel
public class RippleTransaction
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
    private RippleTransactionStatus status = null;


    @ApiModelProperty( value = "A list of required approvals and their status for this transaction.",
                       position = 30,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private List<Approval> approvals = new ArrayList<>();


    @ApiModelProperty( value = "The Ripple transaction id.",
                       position = 100,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String transactionId = null;

    @ApiModelProperty( value = "A hex-encoded SHA-256 hash of the transaction.",
                       position = 110,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String transactionHash = null;

    @ApiModelProperty( value = "The Ripple address of the source account for this transaction.",
                       position = 120,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String sourceAddress = null;

    @ApiModelProperty( value = "The fees paid for this transaction.",
                       position = 150,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private BigDecimal fee = null;

    @ApiModelProperty( value = "The sequence number of the source account used for this transaction.",
                       position = 160,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private BigDecimal accountSequence = null;

    @ApiModelProperty( value = "The sequence number of the ledger on which this transaction was applied.",
                       position = 170,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private Long ledger = null;

    @ApiModelProperty( value = "A map of result codes for the operations executed as part of this transaction.",
                       position = 180,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private Map<String, String> resultCodeMap = new HashMap<>();

    @ApiModelProperty( value = "The signed encoded transaction.",
                       position = 200,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String signedTransaction = null;


    public String getId()
    {
        return id;
    }

    public void setId( final String id )
    {
        this.id = id;
    }

    public RippleTransactionStatus getStatus()
    {
        return status;
    }

    public void setStatus( final RippleTransactionStatus status )
    {
        this.status = status;
    }

    public List<Approval> getApprovals()
    {
        return approvals;
    }

    public void setApprovals( final List<Approval> approvals )
    {
        this.approvals = approvals;
    }

    public void addApproval( final Approval approval )
    {
        this.approvals.add( approval );
    }

    public String getTransactionId()
    {
        return transactionId;
    }

    public void setTransactionId( final String transactionId )
    {
        this.transactionId = transactionId;
    }

    public String getTransactionHash()
    {
        return transactionHash;
    }

    public void setTransactionHash( final String transactionHash )
    {
        this.transactionHash = transactionHash;
    }

    public String getSourceAddress()
    {
        return sourceAddress;
    }

    public void setSourceAddress( final String sourceAddress )
    {
        this.sourceAddress = sourceAddress;
    }

    public BigDecimal getFee()
    {
        return fee;
    }

    public void setFee( final BigDecimal fee )
    {
        this.fee = fee;
    }

    public BigDecimal getAccountSequence()
    {
        return accountSequence;
    }

    public void setAccountSequence( final BigDecimal accountSequence )
    {
        this.accountSequence = accountSequence;
    }

    public Long getLedger()
    {
        return ledger;
    }

    public void setLedger( final Long ledger )
    {
        this.ledger = ledger;
    }

    public Map<String, String> getResultCodeMap()
    {
        return resultCodeMap;
    }

    public void setResultCodeMap( final Map<String, String> resultCodeMap )
    {
        this.resultCodeMap = resultCodeMap;
    }

    public String getSignedTransaction()
    {
        return signedTransaction;
    }

    public void setSignedTransaction( final String signedTransaction )
    {
        this.signedTransaction = signedTransaction;
    }
}

