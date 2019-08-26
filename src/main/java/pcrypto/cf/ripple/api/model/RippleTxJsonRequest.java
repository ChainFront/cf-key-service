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
import lombok.Getter;
import lombok.Setter;
import pcrypto.cf.account.api.model.AccountIdentifier;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;


/**
 * PaymentRequest
 */
@Getter
@Setter
@ApiModel
public class RippleTxJsonRequest
{

    @NotNull
    @ApiModelProperty( required = true,
                       value = "Identifier of the ChainFront source account.",
                       position = 10 )
    private AccountIdentifier sourceCfAccountIdentifier = null;

    @NotNull
    @ApiModelProperty( required = true,
                       value = "Ripple JSON transaction string.",
                       position = 20 )
    private String txJson;

    @ApiModelProperty( value = "The optional ChainFront username of the account to be used as the payment channel. This " +
                               "account will be used to pay transaction fees. If not set, the source account will be used " +
                               "to pay all fees.",
                       position = 60 )
    private Long paymentChannelCfAccountId = null;

    @ApiModelProperty( value = "An optional list of ChainFront accounts required to sign this transaction.",
                       position = 70 )
    private List<AccountIdentifier> additionalSigners = new ArrayList<>();
}

