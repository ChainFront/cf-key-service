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

package pcrypto.cf.bitcoin.value

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.StringUtils
import pcrypto.cf.exception.BadRequestException
import java.util.*


/**
 *
 */
enum class TransactionApprovalStatusEnum constructor(val id: Int) {

    @JsonProperty("PENDING")
    PENDING(1),

    @JsonProperty("APPROVED")
    APPROVED(2),

    @JsonProperty("DENIED")
    DENIED(3),

    @JsonProperty("TIMED_OUT")
    TIMED_OUT(4);


    companion object {

        fun valueOfIgnoreCase(typeString: String): TransactionApprovalStatusEnum? {
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
        fun fromString(string: String): TransactionApprovalStatusEnum {
            return valueOfIgnoreCase(string)
                ?: throw IllegalArgumentException(string + " must be one of " + Arrays.toString(values()))
        }

        fun fromId(id: Int): TransactionApprovalStatusEnum {
            return when (id) {
                1 -> PENDING

                2 -> APPROVED

                3 -> DENIED

                4 -> TIMED_OUT

                else -> throw IllegalArgumentException("TransactionApprovalStatusEnum id [$id] not supported.")
            }
        }
    }
}

