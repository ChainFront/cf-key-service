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

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * EthereumContractRequest
 */
@ApiModel
public class EthereumContractRequest
{

    @ApiModelProperty( value = "The contract method signature to execute.",
                       required = true,
                       position = 10 )
    private String methodSignature = null;

    @ApiModelProperty( value = "If required by the contract method, pass parameters to the method using this array.",
                       position = 20 )
    @Valid
    private List<String> params = new ArrayList<>();

    @ApiModelProperty( value = "The gas limit in wei for the transaction.",
                       position = 100 )
    @Valid
    private BigDecimal gasLimit = null;


    public String getMethodSignature()
    {
        return methodSignature;
    }

    public void setMethodSignature( final String methodSignature )
    {
        this.methodSignature = methodSignature;
    }

    public List<String> getParams()
    {
        return params;
    }

    public void setParams( final List<String> params )
    {
        this.params = params;
    }

    public BigDecimal getGasLimit()
    {
        return gasLimit;
    }

    public void setGasLimit( final BigDecimal gasLimit )
    {
        this.gasLimit = gasLimit;
    }
}

