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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;


@ApiModel
@Validated
public class EthereumAccountRequest
{
    @JsonProperty( "blacklistAddresses" )
    private List<String> blacklistAddresses = new ArrayList<>();

    @JsonProperty( "whitelistAddresses" )
    private List<String> whitelistAddresses = new ArrayList<>();


    @ApiModelProperty( value = "An optional list of addresses for which this account is prohibited to transact." )
    public List<String> getBlacklistAddresses()
    {
        return blacklistAddresses;
    }

    public void setBlacklistAddresses( final List<String> blacklistAddresses )
    {
        this.blacklistAddresses = blacklistAddresses;
    }

    @ApiModelProperty( value = "An optional list of addresses for which this account is allowed to transact. If empty, " +
                               "there are no restrictions on who this account can transact with (subject to any optional blacklist addresses." )
    public List<String> getWhitelistAddresses()
    {
        return whitelistAddresses;
    }

    public void setWhitelistAddresses( final List<String> whitelistAddresses )
    {
        this.whitelistAddresses = whitelistAddresses;
    }
}

