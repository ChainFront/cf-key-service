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

package pcrypto.cf.stellar.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.AccountResponse;
import pcrypto.cf.exception.BlockchainServiceException;
import pcrypto.cf.stellar.client.response.DecoratedSubmitTransactionResponse;
import pcrypto.cf.stellar.client.response.DecoratedTransactionResponse;
import pcrypto.cf.stellar.client.template.StellarTemplate;

import java.io.IOException;


/**
 * Service which handles all non-permissioned communications with the Stellar network.
 */
@Service
public class StellarNetworkService
{
    private static final Logger LOGGER = LoggerFactory.getLogger( StellarNetworkService.class );

    private final StellarTemplate stellarTemplate;


    @Autowired
    public StellarNetworkService( final StellarTemplate stellarTemplate )
    {
        this.stellarTemplate = stellarTemplate;
    }


    public DecoratedSubmitTransactionResponse submitTransaction( final String signedTx )
    {
        try
        {
            final Transaction transaction = Transaction.fromEnvelopeXdr( signedTx );
            return stellarTemplate.submitTransaction( transaction );
        }
        catch ( final IOException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }

    public DecoratedTransactionResponse getTransaction( final String transactionId )
    {
        try
        {
            final DecoratedTransactionResponse transaction = stellarTemplate.getTransaction( transactionId );
            return transaction;
        }
        catch ( final IOException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }

    public AccountResponse.Balance[] getAccountBalances( final String stellarAddress )
    {
        final AccountResponse account;
        try
        {
            final KeyPair keyPair = KeyPair.fromAccountId( stellarAddress );
            account = stellarTemplate.account( keyPair );
        }
        catch ( final IOException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
        return account.getBalances();
    }
}
