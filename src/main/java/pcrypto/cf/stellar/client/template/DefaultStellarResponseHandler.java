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
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.ChangeTrustResultCode;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.PaymentResultCode;
import org.stellar.sdk.xdr.TransactionResult;
import org.stellar.sdk.xdr.XdrDataInputStream;
import pcrypto.cf.stellar.client.response.DecoratedSubmitTransactionResponse;
import pcrypto.cf.stellar.client.response.DecoratedTransactionResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class DefaultStellarResponseHandler
      implements StellarResponseHandler
{
    private static final Logger logger = LoggerFactory.getLogger( DefaultStellarResponseHandler.class );


    @Override
    public DecoratedSubmitTransactionResponse handleResponse( final SubmitTransactionResponse response )
          throws IOException
    {
        final DecoratedSubmitTransactionResponse decoratedSubmitTransactionResponse = new DecoratedSubmitTransactionResponse( response );

        final String resultXdr = response.getResultXdr();
        final Map<String, String> resultCodeMap = parseResultCodeMap( resultXdr );

        decoratedSubmitTransactionResponse.setResultCodeMap( resultCodeMap );
        return decoratedSubmitTransactionResponse;
    }



    @Override
    public DecoratedTransactionResponse handleResponse( final TransactionResponse response )
          throws IOException
    {
        final DecoratedTransactionResponse decoratedTransactionResponse = new DecoratedTransactionResponse( response );

        final String resultXdr = response.getResultXdr();
        final Map<String, String> resultCodeMap = parseResultCodeMap( resultXdr );

        decoratedTransactionResponse.setResultCodeMap( resultCodeMap );
        return decoratedTransactionResponse;
    }


    private Map<String, String> parseResultCodeMap( final String resultXdr )
          throws IOException
    {
        final Map<String, String> resultCodeMap = new HashMap<>();

        final byte[] decodedResultXdr = Base64.getDecoder().decode( resultXdr );

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( decodedResultXdr );
        final TransactionResult transactionResult = TransactionResult.decode( new XdrDataInputStream( byteArrayInputStream ) );
        final OperationResult[] results = transactionResult.getResult().getResults();
        for ( final OperationResult result : results )
        {
            final OperationResult.OperationResultTr tr = result.getTr();
            switch ( tr.getDiscriminant() )
            {
                case CHANGE_TRUST:
                    final ChangeTrustResultCode changeTrustResultCode = tr.getChangeTrustResult().getDiscriminant();
                    resultCodeMap.put( tr.getDiscriminant().toString(), changeTrustResultCode.toString() );
                    break;
                case PAYMENT:
                    final PaymentResultCode paymentResultCode = tr.getPaymentResult().getDiscriminant();
                    resultCodeMap.put( tr.getDiscriminant().toString(), paymentResultCode.toString() );
                    break;
                default:
                    logger.error( "uh-oh" );
                    // TODO: handle this
                    break;
            }
        }
        return resultCodeMap;
    }
}
