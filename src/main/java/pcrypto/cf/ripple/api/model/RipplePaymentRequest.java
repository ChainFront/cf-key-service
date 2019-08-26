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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * PaymentRequest
 */
@Getter
@Setter
@ApiModel
public class RipplePaymentRequest
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
                       example = "100.0",
                       value = "The amount to pay.",
                       position = 30 )
    private BigDecimal amount = null;

    @NotNull
    @ApiModelProperty( example = "native",
                       required = true,
                       value = "The code of the asset to use. For XRP, use 'native'. For custom assets, the assetIssuer field " +
                               "is also required.",
                       position = 40 )
    private String assetCode = null;

    @ApiModelProperty( value = "The Ripple address of the issuer of the custom asset. This is only required when using non-native " +
                               "assets.",
                       position = 50 )
    private String assetIssuer = null;

    @ApiModelProperty( value = "The optional ChainFront account to be used as the payment channel. This " +
                               "account will be used to pay transaction fees. If not set, the source account will be used " +
                               "to pay all fees.",
                       position = 60 )
    private AccountIdentifier paymentChannelCfAccountIdentifier = null;

    @ApiModelProperty( value = "An optional list of ChainFront accounts required to approve this transaction.",
                       position = 70 )
    private List<AccountIdentifier> additionalApprovers = new ArrayList<>();

    @ApiModelProperty( value = "An optional memo to include with this transaction.",
                       position = 80 )
    private String memo;
}

