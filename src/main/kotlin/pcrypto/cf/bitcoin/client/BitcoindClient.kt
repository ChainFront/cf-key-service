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

package pcrypto.cf.bitcoin.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import pcrypto.cf.bitcoin.client.dto.*
import pcrypto.cf.bitcoin.util.BitcoinConvertUtil
import pcrypto.cf.bitcoin.util.BitcoinCurrencyType
import java.io.IOException
import java.math.BigDecimal


/**
 * Service which handles all non-permissioned communications with a Bitcoind node.
 */
@Service
class BitcoindClient @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate
) {

    @Value("\${bitcore.url}")
    private val bitcoreUrl: String? = null


    // Parse the block height from the response
    val blockChainHeight: Long?
        @Throws(IOException::class)
        get() {
            val responseEntity = restTemplate.exchange(
                bitcoreUrl!! + "/sync",
                HttpMethod.GET,
                null,
                String::class.java
            )
            val body = responseEntity.body
            val jsonNode = objectMapper.readTree(body)
            return jsonNode.get("blockChainHeight").asLong()
        }


    // Parse the block height from the response
    val feePerByte: BigDecimal
        @Throws(IOException::class)
        get() {
            val responseEntity = restTemplate.exchange(
                bitcoreUrl!! + "/utils/estimatefee",
                HttpMethod.GET, null,
                String::class.java
            )
            val body = responseEntity.body
            val jsonNode = objectMapper.readTree(body)
            val feeInBtc = jsonNode.get("2").asDouble()
            val feeInSatoshi = BitcoinConvertUtil.asSatoshis(BigDecimal(feeInBtc), BitcoinCurrencyType.BTC)
            return feeInSatoshi.divide(BigDecimal(1024))
        }


    fun getAccount(address: String?): BitcoinAccountDto? {
        address ?: return null
        val responseEntity = restTemplate.exchange(
            "$bitcoreUrl/addr/$address",
            HttpMethod.GET, null,
            BitcoinAccountDto::class.java
        )

        return responseEntity.body
    }

    fun getUtxos(address: String): List<BitcoinUtxoDto>? {
        val responseEntity = restTemplate.exchange("$bitcoreUrl/addr/$address/utxo",
            HttpMethod.GET, null,
            object : ParameterizedTypeReference<List<BitcoinUtxoDto>>() {})

        return responseEntity.body
    }

    fun getTransaction(hash: String): BitcoinTransactionDto? {
        val responseEntity = restTemplate.exchange(
            "$bitcoreUrl/tx/$hash",
            HttpMethod.GET, null,
            BitcoinTransactionDto::class.java
        )

        return responseEntity.body
    }

    fun postTransaction(encodedTx: String): BitcoinSubmitTransactionResponseDto? {
        val requestEntity = HttpEntity(BitcoinSubmitTransactionDto(encodedTx))

        val responseEntity = restTemplate.exchange(
            bitcoreUrl!! + "/tx/send",
            HttpMethod.POST,
            requestEntity,
            BitcoinSubmitTransactionResponseDto::class.java
        )

        return responseEntity.body
    }
}
