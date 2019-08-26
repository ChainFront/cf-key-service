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
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import pcrypto.cf.exception.BlockchainServiceException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


/**
 * Service which handles all non-permissioned communications with the Ethereum network.
 */
@Slf4j
@Service
public class EthereumTransactionClient
{
    @Value( "${ethereum.geth-url}" )
    private String ethereumUrl;


    public EthSendTransaction submitTransaction( final String signedTx )
    {
        final Web3j client = Web3j.build( new HttpService( ethereumUrl ) );

        final Request<?, EthSendTransaction> request = client.ethSendRawTransaction( signedTx );
        final CompletableFuture<EthSendTransaction> future = request.sendAsync();

        try
        {
            final EthSendTransaction ethSendTransaction = future.get();
            return ethSendTransaction;
        }
        catch ( final InterruptedException | ExecutionException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }

    public Optional<TransactionReceipt> getTransaction( final String transactionHash )
    {
        final Web3j client = Web3j.build( new HttpService( ethereumUrl ) );

        final Request<?, EthGetTransactionReceipt> request = client.ethGetTransactionReceipt( transactionHash );
        final CompletableFuture<EthGetTransactionReceipt> future = request.sendAsync();

        try
        {
            final EthGetTransactionReceipt ethGetTransactionReceipt = future.get();
            return ethGetTransactionReceipt.getTransactionReceipt();
        }
        catch ( final InterruptedException | ExecutionException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }
}
