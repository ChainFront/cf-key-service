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

package pcrypto.cf.account.value

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.StringUtils
import pcrypto.cf.exception.BadRequestException
import java.util.*


/**
 * The transaction approval method.
 *
 *
 * AUTHY_PUSH:  createAcct ->
 * APP_TOTP:  createAcct -> QR Code : sync tx approval flow
 * CHAINFRONT_TOTP:  createAcct -> verificationUiUrl -> sendSMSCode -> verifySMSCode -> displayQRCode -> verifyCode
 * IMPLICIT:  createAcct : sync tx approval flow
 */
enum class TxApprovalMethodEnum(val id: Int) {
    @JsonProperty("AUTHY_PUSH")
    AUTHY_PUSH(1),
    @JsonProperty("CHAINFRONT_TOTP")
    CHAINFRONT_TOTP(2),
    @JsonProperty("APP_TOTP")
    APP_TOTP(3),
    @JsonProperty("IMPLICIT")
    IMPLICIT(4);


    companion object {

        fun valueOfIgnoreCase(typeString: String): TxApprovalMethodEnum? {
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
        fun fromString(string: String): TxApprovalMethodEnum {
            return valueOfIgnoreCase(string)
                ?: throw IllegalArgumentException(string + " must be one of " + Arrays.toString(values()))
        }

        fun fromId(id: Int): TxApprovalMethodEnum {
            return when (id) {
                1 -> TxApprovalMethodEnum.AUTHY_PUSH
                2 -> TxApprovalMethodEnum.CHAINFRONT_TOTP
                3 -> TxApprovalMethodEnum.APP_TOTP
                4 -> TxApprovalMethodEnum.IMPLICIT
                else -> throw IllegalArgumentException("TxApprovalMethod id [$id] not supported.")
            }
        }
    }
}

