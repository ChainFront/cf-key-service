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

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;


/**
 * Representation of a trustline on the Ripple network.
 */
@Getter
@Setter
@ApiModel
public class RippleAccountTrustline
{

    @NotNull
    @ApiModelProperty( required = true,
                       value = "The currency code.",
                       position = 10 )
    private String currencyCode;

    @NotNull
    @ApiModelProperty( required = true,
                       value = "The address of the Ripple account which issued the currency.",
                       position = 20 )
    private String currencyIssuer;

    @NotNull
    @ApiModelProperty( required = true,
                       value = "The limit for which the account trusts the issuing account.",
                       example = "10000.0",
                       position = 30 )
    private BigDecimal limit = null;
}

