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
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import pcrypto.cf.bitcoin.client.dto.BitcoinAccountDto;
import pcrypto.cf.bitcoin.client.dto.BitcoinTransactionDto;
import pcrypto.cf.bitcoin.client.dto.BitcoinUtxoDto;

import java.io.IOException;
import java.util.List;


@Slf4j
class BitcoindClientTest
{

    private static BitcoindClient bitcoindClient;

    @BeforeAll
    static void setup()
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        final RestTemplate restTemplate = new RestTemplateBuilder().build();
        bitcoindClient = new BitcoindClient( objectMapper, restTemplate );
        ReflectionTestUtils.setField( bitcoindClient, "bitcoreUrl", "https://test-insight.bitpay.com/api/" );
    }


    @Test
    void testGetAccount()
    {
        final String address = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";
        final BitcoinAccountDto account = bitcoindClient.getAccount( address );

        log.info( "Account : " + account.toString() );
    }

    @Test
    void testGetUtxos()
    {
        final String address = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";
        final List<BitcoinUtxoDto> utxos = bitcoindClient.getUtxos( address );

        for ( final BitcoinUtxoDto utxo : utxos )
        {
            log.info( "UTXO : " + utxo.toString() );
        }
    }

    @Test
    void testGetTransaction()
    {
        final String hash = "debfe83706465f9a77b8edc1b113e0302d2de8c7197fdfa4b15015f37edde82c";
        final BitcoinTransactionDto transaction = bitcoindClient.getTransaction( hash );

        log.info( "Transaction : " + transaction.toString() );
    }

    @Test
    void testGetUtxosFull()
    {
        final String address = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";
        final List<BitcoinUtxoDto> utxos = bitcoindClient.getUtxos( address );

        final long start = System.currentTimeMillis();
        for ( final BitcoinUtxoDto utxo : utxos )
        {
            final String txid = utxo.getTxid();
            final BitcoinTransactionDto transaction = bitcoindClient.getTransaction( txid );
            log.info( "Transaction : " + transaction.toString() );
        }
        final long end = System.currentTimeMillis();

        log.info( "time = " + ( end - start ) );
    }

    @Test
    void testGetUtxosScript()
    {
        final String address = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";
        final List<BitcoinUtxoDto> utxos = bitcoindClient.getUtxos( address );

        for ( BitcoinUtxoDto utxo : utxos )
        {
            final String scriptPubKey = utxo.getScriptPubKey();
            final byte[] decodedScript = Utils.HEX.decode( scriptPubKey );
            final Script script = new Script( decodedScript );
            log.info( "UTXO: " + utxo );
            log.info( "  -- SCRIPT: " + script );
        }
    }

    @Test
    void testGetBlockChainHeight()
          throws IOException
    {
        final Long blockChainHeight = bitcoindClient.getBlockChainHeight();
        log.info( "block chain height = " + blockChainHeight );
    }
}