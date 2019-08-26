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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


@ApiModel
public class RippleAccount
{

    @ApiModelProperty( value = "The ChainFront id which owns this account.",
                       position = 10,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private Long cfAccountId;


    @ApiModelProperty( value = "The public address of this account on the Ripple network.",
                       position = 20,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private String address;

    @ApiModelProperty( value = "A list of the balances held by this account.",
                       position = 30,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    private List<Balance> balances;

    @ApiModelProperty( value = "An optional list of addresses for which this account is prohibited to transact.",
                       position = 100 )
    private List<String> blacklistAddresses = new ArrayList<>();

    @ApiModelProperty( value = "An optional list of addresses for which this account is allowed to transact. If empty, " +
                               "there are no restrictions on who this account can transact with (subject to any optional blacklist addresses.",
                       position = 110 )
    private List<String> whitelistAddresses = new ArrayList<>();

    @ApiModelProperty( value = "Date the account was created.",
                       position = 1000,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    @JsonProperty( "createdDate" )
    private OffsetDateTime createdDate = null;

    @ApiModelProperty( value = "Date the account was last updated.",
                       position = 1010,
                       accessMode = ApiModelProperty.AccessMode.READ_ONLY,
                       readOnly = true )
    @JsonProperty( "lastModifiedDate" )
    private OffsetDateTime lastModifiedDate = null;


    public Long getCfAccountId()
    {
        return cfAccountId;
    }

    public void setCfAccountId( final Long cfAccountId )
    {
        this.cfAccountId = cfAccountId;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress( final String address )
    {
        this.address = address;
    }

    public List<Balance> getBalances()
    {
        return balances;
    }

    public void setBalances( final List<Balance> balances )
    {
        this.balances = balances;
    }

    public List<String> getBlacklistAddresses()
    {
        return blacklistAddresses;
    }

    public void setBlacklistAddresses( final List<String> blacklistAddresses )
    {
        this.blacklistAddresses = blacklistAddresses;
    }

    public List<String> getWhitelistAddresses()
    {
        return whitelistAddresses;
    }

    public void setWhitelistAddresses( final List<String> whitelistAddresses )
    {
        this.whitelistAddresses = whitelistAddresses;
    }

    public OffsetDateTime getCreatedDate()
    {
        return createdDate;
    }

    public void setCreatedDate( final OffsetDateTime createdDate )
    {
        this.createdDate = createdDate;
    }

    public OffsetDateTime getLastModifiedDate()
    {
        return lastModifiedDate;
    }

    public void setLastModifiedDate( final OffsetDateTime lastModifiedDate )
    {
        this.lastModifiedDate = lastModifiedDate;
    }

    public static final class Balance
    {
        private String assetType;
        private String assetCode;
        private String assetIssuer;
        private String limit;
        private String amount;

        public Balance( final String pAssetType,
                        final String pAssetCode,
                        final String pAssetIssuer,
                        final String pLimit,
                        final String pAmount )
        {
            assetType = pAssetType;
            assetCode = pAssetCode;
            assetIssuer = pAssetIssuer;
            limit = pLimit;
            amount = pAmount;
        }

        public String getAssetType()
        {
            return assetType;
        }

        public void setAssetType( final String pAssetType )
        {
            assetType = pAssetType;
        }

        public String getAssetCode()
        {
            return assetCode;
        }

        public void setAssetCode( final String pAssetCode )
        {
            assetCode = pAssetCode;
        }

        public String getAssetIssuer()
        {
            return assetIssuer;
        }

        public void setAssetIssuer( final String pAssetIssuer )
        {
            assetIssuer = pAssetIssuer;
        }

        public String getLimit()
        {
            return limit;
        }

        public void setLimit( final String pLimit )
        {
            limit = pLimit;
        }

        public String getAmount()
        {
            return amount;
        }

        public void setAmount( final String pAmount )
        {
            amount = pAmount;
        }
    }
}
