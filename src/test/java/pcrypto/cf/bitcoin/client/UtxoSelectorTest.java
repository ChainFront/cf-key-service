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
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import pcrypto.cf.bitcoin.client.dto.BitcoinUtxoDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Slf4j
class UtxoSelectorTest
{

    private static BitcoindClient bitcoindClient;

    @BeforeAll
    static void setup()
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        final RestTemplate restTemplate = new RestTemplateBuilder().build();
        bitcoindClient = new BitcoindClient( objectMapper, restTemplate );
        ReflectionTestUtils.setField( bitcoindClient, "bitcoreUrl", "http://localhost:3001" );
    }


    @Test
    void testSelectUtxos()
          throws IOException
    {
        final UtxoSelector utxoSelector = new UtxoSelector();

        final BigDecimal amount = new BigDecimal( 120 );

        final String address = "2NBMEXpDHGwecPwk9qzKMgAPdChhH5zvaE3";
        final List<BitcoinUtxoDto> utxos = bitcoindClient.getUtxos( address );
        final List<UTXO> candidates = new ArrayList<>();
        for ( final BitcoinUtxoDto utxo : utxos )
        {
            final UTXO bitcoinjUtxo = new UTXO( Sha256Hash.of( Utils.HEX.decode( utxo.getTxid() ) ),
                                                utxo.getVout(),
                                                Coin.valueOf( utxo.getSatoshis().longValue() ),
                                                utxo.getHeight().intValue(),
                                                false,
                                                new Script( Utils.HEX.decode( utxo.getScriptPubKey() ) ) );
            candidates.add( bitcoinjUtxo );
        }

        final Long blockChainHeight = bitcoindClient.getBlockChainHeight();

        final CoinSelection coinSelection = utxoSelector.selectUtxos( amount, candidates, blockChainHeight );
        final Collection<TransactionOutput> gathered = coinSelection.gathered;
        for ( final TransactionOutput transactionOutput : gathered )
        {
            log.info( "tx : " + transactionOutput );
        }
    }
}