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
import pcrypto.cf.account.api.model.AccountIdentifier;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * TokenPaymentRequest
 */
@Getter
@Setter
@ApiModel
public class EthereumTokenPaymentRequest
{

    @NotNull
    @ApiModelProperty( required = true,
                       value = "Identifier of the ChainFront source account.",
                       position = 10 )
    private AccountIdentifier sourceCfAccountIdentifier = null;

    @NotNull
    @ApiModelProperty( required = true,
                       value = "Identifier of the ChainFront destination account.",
                       position = 20 )
    private AccountIdentifier destinationCfAccountIdentifier = null;

    @NotNull
    @ApiModelProperty( required = true,
                       value = "Name of the account which has the ERC-20 token contract",
                       position = 30 )
    private String tokenAccountName = null;

    @NotNull
    @Valid
    @ApiModelProperty( example = "100.0",
                       required = true,
                       value = "Amount of ERC-20 tokens to transfer.",
                       position = 40 )
    private BigDecimal amount = null;

    @ApiModelProperty( value = "An optional list of ChainFront accounts required to sign this transaction.",
                       position = 50 )
    private List<AccountIdentifier> additionalSigners = new ArrayList<>();

    @ApiModelProperty( value = "The gas limit in wei for the transaction. If not set, the gas limit will be 21000 wei.",
                       position = 100 )
    @Valid
    private BigDecimal gasLimit = new BigDecimal( 21000 );
}

