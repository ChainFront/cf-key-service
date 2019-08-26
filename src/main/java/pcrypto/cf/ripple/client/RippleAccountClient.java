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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ripple.core.coretypes.AccountID;
import com.ripple.core.coretypes.Amount;
import com.ripple.core.coretypes.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pcrypto.cf.exception.BlockchainServiceException;
import pcrypto.cf.ripple.client.dto.RippleAccountInfoDto;
import pcrypto.cf.ripple.client.dto.RippleAccountLineDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * Service which handles all non-permissioned communications with the Ripple network.
 */
@Slf4j
@Service
public class RippleAccountClient
{
    private final ObjectMapper objectMapper;

    @Value( "${ripple.wss-url}" )
    private String rippleUrl;

    @Autowired
    public RippleAccountClient( final ObjectMapper objectMapper )
    {
        this.objectMapper = objectMapper;
    }


    public RippleAccountInfoDto getAccountInfo( final String rippleAddress )
    {
        // Get the XRP balance via 'account_info'
        final String command = "account_info";

        final AccountID accountID = AccountID.fromAddress( rippleAddress );

        final ObjectNode payload = this.objectMapper.createObjectNode();

        payload.put( "command", command );
        payload.put( "account", rippleAddress );
        payload.put( "strict", true );
        payload.put( "ledger_index", "current" );
        payload.put( "queue", true );

        try
        {
            final BlockingWebSocketClient client = new BlockingWebSocketClient( new URI( rippleUrl ) );
            final String response = client.sendBlocking( payload.toString() );

            final JsonNode body = this.objectMapper.readTree( response );

            final RippleAccountInfoDto rippleAccountInfoDto = new RippleAccountInfoDto();
            rippleAccountInfoDto.setAccountID( accountID );

            final JsonNode accountDataNode = body.get( "result" ).get( "account_data" );

            final Amount balance = Amount.fromDropString( accountDataNode.get( "Balance" ).textValue() );
            rippleAccountInfoDto.setBalance( balance );

            final int sequence = accountDataNode.get( "Sequence" ).asInt();
            rippleAccountInfoDto.setSequence( sequence );

            return rippleAccountInfoDto;
        }
        catch ( final InterruptedException | IOException | URISyntaxException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }


    public List<RippleAccountLineDto> getAccountLines( final String rippleAddress )
    {
        // Get the balances via 'account_lines'
        final String command = "account_lines";

        final AccountID accountID = AccountID.fromAddress( rippleAddress );

        final ObjectNode payload = this.objectMapper.createObjectNode();

        payload.put( "command", command );
        payload.put( "account", rippleAddress );
        payload.put( "ledger_index", "current" );

        String response = null;
        try
        {
            final BlockingWebSocketClient client = new BlockingWebSocketClient( new URI( rippleUrl ) );
            response = client.sendBlocking( payload.toString() );

            final JsonNode body = this.objectMapper.readTree( response );

            final List<RippleAccountLineDto> accountLines = new ArrayList<>();
            final JsonNode jsonNode = body.get( "result" ).get( "lines" );
            if ( jsonNode.isArray() )
            {
                for ( final JsonNode lineNode : jsonNode )
                {
                    final RippleAccountLineDto accountLine = buildAccountLine( accountID, lineNode );
                    accountLines.add( accountLine );
                }
            }

            return accountLines;
        }
        catch ( final InterruptedException | IOException | URISyntaxException e )
        {
            throw new BlockchainServiceException( e.getMessage(), e );
        }
    }



    private RippleAccountLineDto buildAccountLine( final AccountID orientedTo,
                                                   final JsonNode line )
    {
        final RippleAccountLineDto accountLine = new RippleAccountLineDto();

        final AccountID peer = AccountID.fromAddress( line.get( "account" ).textValue() );

        final BigDecimal balance = new BigDecimal( line.get( "balance" ).textValue() );
        final BigDecimal limit = new BigDecimal( line.get( "limit" ).textValue() );
        final BigDecimal limit_peer = new BigDecimal( line.get( "limit_peer" ).textValue() );

        accountLine.currency = Currency.fromString( line.get( "currency" ).textValue() );
        accountLine.balance = new Amount( balance, accountLine.currency, peer );

        accountLine.limit = new Amount( limit, accountLine.currency, peer );
        accountLine.limit_peer = new Amount( limit_peer, accountLine.currency, orientedTo );

        accountLine.freeze = Optional.ofNullable( line.get( "freeze" ) ).orElse( BooleanNode.FALSE ).asBoolean( false );
        accountLine.freeze_peer = Optional.ofNullable( line.get( "freeze_peer" ) ).orElse( BooleanNode.FALSE ).asBoolean( false );

        accountLine.authorized = Optional.ofNullable( line.get( "authorized" ) ).orElse( BooleanNode.FALSE ).asBoolean( false );
        accountLine.authorized_peer = Optional.ofNullable( line.get( "authorized_peer" ) ).orElse( BooleanNode.FALSE ).asBoolean( false );

        accountLine.no_ripple = Optional.ofNullable( line.get( "no_ripple" ) ).orElse( BooleanNode.FALSE ).asBoolean();
        accountLine.no_ripple_peer = Optional.ofNullable( line.get( "no_ripple_peer" ) ).orElse( BooleanNode.FALSE ).asBoolean();

        accountLine.quality_in = Optional.ofNullable( line.get( "quality_in" ) ).orElse( IntNode.valueOf( 0 ) ).asInt( 0 );
        accountLine.quality_out = Optional.ofNullable( line.get( "quality_out" ) ).orElse( IntNode.valueOf( 0 ) ).asInt( 0 );

        return accountLine;
    }
}
