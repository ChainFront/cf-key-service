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

package pcrypto.cf.ethereum.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;
import pcrypto.cf.exception.BlockchainServiceException;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


@Slf4j
@Service
public class EthereumAccountClient
{
    @Value( "${ethereum.geth-url}" )
    private String ethereumUrl;

    public BigInteger getAccountBalance( final String ethAddress )
    {
        final Web3j client = Web3j.build( new HttpService( ethereumUrl ) );

        final Request<?, EthGetBalance> balanceRequest = client.ethGetBalance( ethAddress, DefaultBlockParameter.valueOf( "latest" ) );
        final CompletableFuture<EthGetBalance> future = balanceRequest.sendAsync();
        final EthGetBalance ethGetBalance;
        try
        {
            ethGetBalance = future.get();
        }
        catch ( final InterruptedException | ExecutionException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }

        return ethGetBalance.getBalance();
    }
}
