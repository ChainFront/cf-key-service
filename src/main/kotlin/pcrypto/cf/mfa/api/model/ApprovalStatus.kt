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

package pcrypto.cf.mfa.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import org.apache.commons.lang3.StringUtils
import pcrypto.cf.exception.BadRequestException
import java.util.*


enum class ApprovalStatus(val id: Int) {

    PENDING(1),
    SIGNED(2),
    DENIED(3),
    TIMEOUT(4);

    companion object {

        fun valueOfIgnoreCase(typeString: String): ApprovalStatus? {
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
        fun fromString(string: String): ApprovalStatus {
            return valueOfIgnoreCase(string)
                ?: throw IllegalArgumentException(string + " must be one of " + Arrays.toString(values()))
        }

        fun fromId(id: Int): ApprovalStatus {
            return when (id) {
                1 -> PENDING

                2 -> SIGNED

                3 -> DENIED

                4 -> TIMEOUT

                else -> throw IllegalArgumentException("ApprovalStatus id [$id] not supported.")
            }
        }
    }
}
