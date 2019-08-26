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

package pcrypto.cf.stellar.client.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.responses.TransactionResponse;
import pcrypto.cf.exception.BlockchainServiceException;
import pcrypto.cf.stellar.client.response.DecoratedSubmitTransactionResponse;
import pcrypto.cf.stellar.client.response.DecoratedTransactionResponse;

import java.io.IOException;


@Component
public class StellarTemplate
{
    /**
     * Logger available to subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger( getClass() );


    private Server server;

    private StellarErrorHandler errorHandler = new DefaultStellarErrorHandler();

    private StellarResponseHandler responseHandler = new DefaultStellarResponseHandler();


    /**
     * Default constructor which points to Stellar testnet.
     */
    public StellarTemplate()
    {
        Network.useTestNetwork();
        server = new Server( "https://horizon-testnet.stellar.org" );
    }

    /**
     * Constructor which takes a custom server object.
     *
     * @param pServer
     */
    public StellarTemplate( final Server pServer )
    {
        server = pServer;
    }


    public DecoratedSubmitTransactionResponse submitTransaction( final Transaction transaction )
          throws IOException
    {
        final SubmitTransactionResponse submitTransactionResponse = server.submitTransaction( transaction );
        if ( !submitTransactionResponse.isSuccess() )
        {
            getErrorHandler().handleError( submitTransactionResponse );
        }

        return getResponseHandler().handleResponse( submitTransactionResponse );
    }


    public DecoratedTransactionResponse getTransaction( final String transactionId )
          throws IOException
    {
        final TransactionResponse transactionResponse;
        try
        {
            transactionResponse = server.transactions().transaction( transactionId );
        }
        catch ( final ErrorResponse err )
        {
            throw new BlockchainServiceException( err.getBody(), err );
        }
        return getResponseHandler().handleResponse( transactionResponse );
    }


    public AccountResponse account( final KeyPair keyPair )
          throws IOException
    {
        final AccountResponse accountResponse = server.accounts().account( keyPair );
        return accountResponse;
    }

    public StellarResponseHandler getResponseHandler()
    {
        return responseHandler;
    }

    public void setResponseHandler( final StellarResponseHandler pResponseHandler )
    {
        responseHandler = pResponseHandler;
    }

    public StellarErrorHandler getErrorHandler()
    {
        return errorHandler;
    }

    public void setErrorHandler( final StellarErrorHandler pErrorHandler )
    {
        errorHandler = pErrorHandler;
    }
}
