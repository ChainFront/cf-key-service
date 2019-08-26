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

package pcrypto.cf.ripple.vault.dto;

/**
 * Represents a Ripple payment as represented by the Ripple vault plugin.
 */
public class VaultRipplePaymentDomain
{
    private String source;
    private String destination;
    private String paymentChannel;
    private String amount;
    private String assetCode;
    private String assetIssuer;
    private String memo;


    public String getSource()
    {
        return source;
    }

    public void setSource( final String source )
    {
        this.source = source;
    }

    public String getDestination()
    {
        return destination;
    }

    public void setDestination( final String destination )
    {
        this.destination = destination;
    }

    public String getPaymentChannel()
    {
        return paymentChannel;
    }

    public void setPaymentChannel( final String paymentChannel )
    {
        this.paymentChannel = paymentChannel;
    }

    public String getAmount()
    {
        return amount;
    }

    public void setAmount( final String amount )
    {
        this.amount = amount;
    }

    public String getAssetCode()
    {
        return assetCode;
    }

    public void setAssetCode( final String assetCode )
    {
        this.assetCode = assetCode;
    }

    public String getAssetIssuer()
    {
        return assetIssuer;
    }

    public void setAssetIssuer( final String assetIssuer )
    {
        this.assetIssuer = assetIssuer;
    }

    public String getMemo()
    {
        return memo;
    }

    public void setMemo( final String memo )
    {
        this.memo = memo;
    }
}
