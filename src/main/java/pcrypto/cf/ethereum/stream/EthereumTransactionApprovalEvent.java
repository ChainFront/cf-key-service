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

package pcrypto.cf.ethereum.stream;

import lombok.NonNull;


public class EthereumTransactionApprovalEvent
{
    @NonNull
    private String tenantId;

    @NonNull
    private String transactionId;

    public EthereumTransactionApprovalEvent()
    {
    }

    @NonNull
    public String getTenantId()
    {
        return this.tenantId;
    }

    public void setTenantId( @NonNull final String tenantId )
    {
        this.tenantId = tenantId;
    }

    @NonNull
    public String getTransactionId()
    {
        return this.transactionId;
    }

    public void setTransactionId( @NonNull final String transactionId )
    {
        this.transactionId = transactionId;
    }

    public String toString()
    {
        return "EthereumTransactionApprovalEvent(tenantId=" + this.getTenantId() + ", transactionId=" + this.getTransactionId() + ")";
    }
}
