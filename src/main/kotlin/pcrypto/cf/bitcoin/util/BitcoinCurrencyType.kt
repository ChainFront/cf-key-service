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

package pcrypto.cf.bitcoin.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.StringUtils
import pcrypto.cf.exception.BadRequestException
import java.util.*


enum class BitcoinCurrencyType constructor(val id: Int) {

    @JsonProperty("BTC")
    BTC(1),
    @JsonProperty("SATOSHI")
    SATOSHI(2);


    companion object {

        fun valueOfIgnoreCase(typeString: String): BitcoinCurrencyType? {
            return try {
                valueOf(StringUtils.upperCase(typeString))
            } catch (e: IllegalArgumentException) {
                null
            }

        }

        /**
         * Control Jackson serialization to do case-insensitive serialization.
         *
         * @param string original value
         * @return the Enum
         * @throws BadRequestException if an invalid value was sent
         */
        @JsonCreator
        fun fromString(string: String): BitcoinCurrencyType {
            return valueOfIgnoreCase(string) ?: throw IllegalArgumentException(
                "$string must be one of " + Arrays.toString(
                    values()
                )
            )
        }

        fun fromId(id: Int): BitcoinCurrencyType {
            return when (id) {
                1 -> BTC

                2 -> SATOSHI

                else -> throw IllegalArgumentException("BitcoinCurrencyType id [$id] not supported.")
            }
        }
    }
}

