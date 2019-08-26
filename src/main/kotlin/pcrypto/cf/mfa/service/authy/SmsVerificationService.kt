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

package pcrypto.cf.mfa.service.authy

import com.authy.AuthyApiClient
import com.authy.AuthyException
import com.authy.api.Params
import com.authy.api.Verification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pcrypto.cf.common.util.parsePhoneNumber
import pcrypto.cf.exception.MfaException


@Service
class SmsVerificationService
@Autowired constructor(private val authyApiClient: AuthyApiClient) {

    fun sendVerificationMessage(
        phoneNumber: String,
        via: String
    ) {

        // Extract the country code from the phone number
        val parsedPhoneNumber = phoneNumber.parsePhoneNumber()

        val params = Params()
        params.setAttribute("code_length", "4")
        val verification: Verification
        try {
            verification = authyApiClient.phoneVerification.start(
                parsedPhoneNumber.nationalNumber.toString(),
                parsedPhoneNumber.countryCode.toString(),
                via,
                params
            )
            if (!verification.isOk) {
                throw MfaException("Error requesting phone verification: " + verification.message)
            }
        } catch (e: AuthyException) {
            throw MfaException("Error requesting phone verification", e)
        }

    }

    fun verify(
        phoneNumber: String,
        token: String
    ): Boolean {

        // Extract the country code from the phone number
        val parsedPhoneNumber = phoneNumber.parsePhoneNumber()

        val verification: Verification
        try {
            verification = authyApiClient.phoneVerification.check(
                parsedPhoneNumber.nationalNumber.toString(),
                parsedPhoneNumber.countryCode.toString(),
                token
            )
        } catch (e: AuthyException) {
            throw MfaException("Error validating phone verification code", e)
        }

        return verification.isOk
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmsVerificationService::class.java)
    }
}
