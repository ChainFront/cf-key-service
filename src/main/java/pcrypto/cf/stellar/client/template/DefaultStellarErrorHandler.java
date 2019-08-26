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
import org.stellar.sdk.responses.SubmitTransactionResponse;
import pcrypto.cf.exception.BlockchainServiceException;

import java.util.ArrayList;


public class DefaultStellarErrorHandler
      implements StellarErrorHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultStellarErrorHandler.class );


    @Override
    public void handleError( final SubmitTransactionResponse response )
          throws BlockchainServiceException
    {
        final StringBuilder sb = new StringBuilder();
        final String transactionResultCode = response.getExtras().getResultCodes().getTransactionResultCode();
        sb.append( " - Transaction Result Code : " ).append( transactionResultCode );
        final ArrayList<String> operationsResultCodes = response.getExtras().getResultCodes().getOperationsResultCodes();
        if ( null != operationsResultCodes )
        {
            for ( final String operationsResultCode : operationsResultCodes )
            {
                sb.append( " - " ).append( "Operation Result Code: " ).append( operationsResultCode );
            }
        }

        throw new BlockchainServiceException( sb.toString() );
    }
}
