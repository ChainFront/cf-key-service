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

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;


@Slf4j
public class BlockingWebSocketClient
      extends WebSocketClient
{
    private static long timeout = 20000;
    private final List<String> results = new ArrayList<>();
    private String message;

    public BlockingWebSocketClient( final URI serverUri )
    {
        super( serverUri );
    }

    public String sendBlocking( final String msg )
          throws InterruptedException
    {
        this.message = msg;
        this.connect();
        synchronized ( this.results )
        {
            this.results.wait( timeout );
        }
        this.close();
        return this.results.get( 0 );
    }

    @Override
    public void onOpen( final ServerHandshake handshakedata )
    {
        log.debug( "connected. sending message" );
        this.send( this.message );
    }

    @Override
    public void onMessage( final String message )
    {
        log.debug( "response received" );
        synchronized ( this.results )
        {
            this.results.add( message );
            this.results.notify();
        }
    }

    @Override
    public void onClose( final int code,
                         final String reason,
                         final boolean remote )
    {
        log.debug( "connection closed" );
    }

    @Override
    public void onError( final Exception ex )
    {
        log.error( ex.getMessage(), ex );
        this.close();
    }
}
