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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pcrypto.cf.ripple.client.dto.RippleAccountInfoDto;
import pcrypto.cf.ripple.client.dto.RippleAccountLineDto;

import java.util.List;


@Slf4j
class RippleAccountClientTest
{
    private static RippleAccountClient rippleAccountClient;

    @BeforeAll
    static void setup()
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        rippleAccountClient = new RippleAccountClient( objectMapper );
        ReflectionTestUtils.setField( rippleAccountClient, "rippleUrl", "wss://s.altnet.rippletest.net:51233" );
    }

    @Test
    void testGetAccountBalances()
    {
        final String address = "r4oSY6JSHL67GzU9G3zSB6e9eX4Vy6CRAA";

        final RippleAccountInfoDto accountInfo = rippleAccountClient.getAccountInfo( address );

        log.info( accountInfo.getBalance().nativeText() + " - " + accountInfo.getBalance().currencyString() + " - " + accountInfo.getBalance().issuerString() + " - " + accountInfo.getBalance().valueText() );
    }


    @Test
    void testGetAccountLines()
    {
        final String address = "r4oSY6JSHL67GzU9G3zSB6e9eX4Vy6CRAA";

        final List<RippleAccountLineDto> rippleAccountLineDtos = rippleAccountClient.getAccountLines( address );

        for ( final RippleAccountLineDto rippleAccountLineDto : rippleAccountLineDtos )
        {
            log.info( rippleAccountLineDto.currency.toString() );
        }
    }
}