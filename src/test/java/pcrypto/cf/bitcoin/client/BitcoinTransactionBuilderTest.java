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

package pcrypto.cf.bitcoin.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;


@Slf4j
class BitcoinTransactionBuilderTest
{

    private static NetworkParameters networkParams;
    private static BitcoindClient bitcoindClient;

    @BeforeAll
    static void setup()
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        final RestTemplate restTemplate = new RestTemplateBuilder().build();
        bitcoindClient = new BitcoindClient( objectMapper, restTemplate );
        ReflectionTestUtils.setField( bitcoindClient, "bitcoreUrl", "http://localhost:3001" );

        networkParams = NetworkParameters.fromID( NetworkParameters.ID_TESTNET );
    }

    @Test
    void testBuildTransaction()
    {
        final String sourceAddress = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";
        final String destAddress = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";

        final BigDecimal amount = new BigDecimal( 120 );


        final Transaction transaction =
              new BitcoinTransactionBuilder( bitcoindClient, networkParams, sourceAddress, destAddress, amount, sourceAddress )
                    .build();

        final String transactionString = transaction.toString();
        log.info( "UNSIGNED TRANSACTION: " + transactionString );

        // Sign the tx
        final byte[] transactionBytes = transaction.bitcoinSerialize();
    }
}