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

package pcrypto.cf.stellar.client.response;

import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.util.HashMap;
import java.util.Map;


/**
 * Decorates the Stellar SDK 'SubmitTransactionResponse' object with a parsed result code map.
 */
public class DecoratedSubmitTransactionResponse
{
    private final SubmitTransactionResponse original;
    private final Map<String, String> resultCodeMap = new HashMap<>();

    public DecoratedSubmitTransactionResponse( final SubmitTransactionResponse original )
    {
        this.original = original;
    }

    public SubmitTransactionResponse getOriginal()
    {
        return this.original;
    }

    public Map<String, String> getResultCodeMap()
    {
        return this.resultCodeMap;
    }

    public void setResultCodeMap( final Map<String, String> resultCodeMap )
    {
        this.resultCodeMap.clear();
        this.resultCodeMap.putAll( resultCodeMap );
    }

    public boolean isSuccess()
    {
        return getOriginal().isSuccess();
    }

    public String getHash()
    {
        return getOriginal().getHash();
    }

    public Long getLedger()
    {
        return getOriginal().getLedger();
    }

    public int getRateLimitLimit()
    {
        return getOriginal().getRateLimitLimit();
    }

    public int getRateLimitRemaining()
    {
        return getOriginal().getRateLimitRemaining();
    }
}
