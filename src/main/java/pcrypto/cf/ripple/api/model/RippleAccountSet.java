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


/**
 * Model to set account details on the Ripple network.
 */
@Getter
@Setter
@ApiModel
public class RippleAccountSet
{

    @ApiModelProperty( value = "Ripple account flag to set.",
                       position = 10 )
    private Integer flag = null;

    @ApiModelProperty( value = "Ripple account flag to clear",
                       position = 20 )
    private Integer clearFlag = null;

    @ApiModelProperty( value = "The domain name which owns this account.",
                       position = 30 )
    private String domain = null;
}

