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

import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.responses.TransactionResponse;
import pcrypto.cf.stellar.client.response.DecoratedSubmitTransactionResponse;
import pcrypto.cf.stellar.client.response.DecoratedTransactionResponse;

import java.io.IOException;


public interface StellarResponseHandler
{
    DecoratedSubmitTransactionResponse handleResponse( SubmitTransactionResponse response )
          throws IOException;

    DecoratedTransactionResponse handleResponse( TransactionResponse response )
          throws IOException;
}
