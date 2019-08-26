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

package pcrypto.cf.ripple.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ripple.client.enums.RPCErr;
import com.ripple.core.serialized.enums.EngineResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pcrypto.cf.exception.BlockchainServiceException;
import pcrypto.cf.ripple.client.dto.RippleSubmitResponseDto;
import pcrypto.cf.ripple.client.dto.RippleTransactionResponseDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;


/**
 * Service which handles all non-permissioned communications with the Ripple network.
 */
@Slf4j
@Service
public class RippleTransactionClient
{
    private final ObjectMapper objectMapper;

    @Value( "${ripple.wss-url}" )
    private String rippleUrl;


    @Autowired
    public RippleTransactionClient( final ObjectMapper objectMapper )
    {
        this.objectMapper = objectMapper;
    }


    public RippleSubmitResponseDto submitTransaction( final String signedTx )
    {
        final String command = "submit";

        final ObjectNode payload = this.objectMapper.createObjectNode();

        payload.put( "command", command );
        payload.put( "tx_blob", signedTx );

        try
        {
            final BlockingWebSocketClient client = new BlockingWebSocketClient( new URI( rippleUrl ) );
            final String response = client.sendBlocking( payload.toString() );

            final JsonNode body = this.objectMapper.readTree( response );

            final RippleSubmitResponseDto rippleSubmitResponseDto = new RippleSubmitResponseDto();

            final String status = body.get( "status" ).asText();
            rippleSubmitResponseDto.setStatus( status );
            final boolean succeeded = status.equals( "success" );
            rippleSubmitResponseDto.setSucceeded( succeeded );

            if ( succeeded )
            {
                final JsonNode resultNode = body.get( "result" );

                final EngineResult engineResult = EngineResult.valueOf( resultNode.get( "engine_result" ).asText() );
                rippleSubmitResponseDto.setEngineResult( engineResult );

                final JsonNode txJson = resultNode.get( "tx_json" );

                final String fee = txJson.get( "Fee" ).asText();
                rippleSubmitResponseDto.setFee( new BigDecimal( fee ) );

                final String sequence = txJson.get( "Sequence" ).asText();
                rippleSubmitResponseDto.setSequence( sequence );

                final String hash = txJson.get( "hash" ).asText();
                rippleSubmitResponseDto.setHash( hash );
            }
            else
            {
                final String error = body.get( "error" ).asText();
                rippleSubmitResponseDto.setError( error );
                try
                {
                    final RPCErr rpcErr = RPCErr.valueOf( error );
                    rippleSubmitResponseDto.setRpcerr( rpcErr );
                }
                catch ( final Exception e )
                {
                    rippleSubmitResponseDto.setRpcerr( RPCErr.unknownError );
                }
            }

            return rippleSubmitResponseDto;
        }
        catch ( final InterruptedException | IOException | URISyntaxException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }

    public RippleTransactionResponseDto getTransaction( final String transactionHash )
    {
        final String command = "tx";

        final ObjectNode payload = this.objectMapper.createObjectNode();

        payload.put( "command", command );
        payload.put( "transaction", transactionHash );

        try
        {
            final BlockingWebSocketClient client = new BlockingWebSocketClient( new URI( rippleUrl ) );
            final String response = client.sendBlocking( payload.toString() );

            final JsonNode body = this.objectMapper.readTree( response );

            final RippleTransactionResponseDto rippleTransactionResponseDto = new RippleTransactionResponseDto();

            final String status = body.get( "status" ).asText();
            rippleTransactionResponseDto.setStatus( status );
            final boolean succeeded = status.equals( "success" );
            rippleTransactionResponseDto.setSucceeded( succeeded );

            if ( succeeded )
            {
                final JsonNode resultNode = body.get( "result" );

                final String hash = resultNode.get( "hash" ).asText();
                rippleTransactionResponseDto.setHash( hash );

                final String sequence = resultNode.get( "Sequence" ).asText();
                rippleTransactionResponseDto.setSequence( sequence );

                final String ledgerIndex = Optional.ofNullable( resultNode.get( "ledger_index" ) ).orElse( TextNode.valueOf( "0" ) ).asText();
                rippleTransactionResponseDto.setLedger( ledgerIndex );

                final int fee = resultNode.get( "Fee" ).asInt();
                rippleTransactionResponseDto.setFee( new BigDecimal( fee ) );

                final String sourceAddress = resultNode.get( "Account" ).asText();
                rippleTransactionResponseDto.setSourceAddress( sourceAddress );
            }
            else
            {
                final String error = body.get( "error" ).asText();
                rippleTransactionResponseDto.setError( error );
                try
                {
                    final RPCErr rpcErr = RPCErr.valueOf( error );
                    rippleTransactionResponseDto.setRpcerr( rpcErr );
                }
                catch ( final Exception e )
                {
                    rippleTransactionResponseDto.setRpcerr( RPCErr.unknownError );
                }
            }

            return rippleTransactionResponseDto;
        }
        catch ( final InterruptedException | IOException | URISyntaxException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }
}
