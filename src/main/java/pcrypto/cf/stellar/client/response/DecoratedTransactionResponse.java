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

import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Memo;
import org.stellar.sdk.responses.TransactionResponse;

import java.util.HashMap;
import java.util.Map;


/**
 * Decorates the Stellar SDK 'TransactionResponse' object with a parsed result code map.
 */
public class DecoratedTransactionResponse
{
    private final TransactionResponse original;
    private final Map<String, String> resultCodeMap = new HashMap<>();

    public DecoratedTransactionResponse( final TransactionResponse original )
    {
        this.original = original;
    }

    public TransactionResponse getOriginal()
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

    public String getHash()
    {
        return getOriginal().getHash();
    }

    public Long getLedger()
    {
        return getOriginal().getLedger();
    }

    public String getCreatedAt()
    {
        return original.getCreatedAt();
    }

    public KeyPair getSourceAccount()
    {
        return original.getSourceAccount();
    }

    public Long getSourceAccountSequence()
    {
        return original.getSourceAccountSequence();
    }

    public Long getFeePaid()
    {
        return original.getFeePaid();
    }

    public Integer getOperationCount()
    {
        return original.getOperationCount();
    }

    public String getEnvelopeXdr()
    {
        return original.getEnvelopeXdr();
    }

    public String getResultXdr()
    {
        return original.getResultXdr();
    }

    public String getResultMetaXdr()
    {
        return original.getResultMetaXdr();
    }

    public Memo getMemo()
    {
        return original.getMemo();
    }

    public TransactionResponse.Links getLinks()
    {
        return original.getLinks();
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
