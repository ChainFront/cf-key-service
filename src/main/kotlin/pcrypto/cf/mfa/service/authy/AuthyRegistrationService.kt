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
import com.authy.api.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pcrypto.cf.common.util.parsePhoneNumber
import pcrypto.cf.exception.MfaException


@Service
class AuthyRegistrationService
@Autowired constructor(private val authyClient: AuthyApiClient) {

    fun register(
        phoneNumber: String,
        email: String
    ): Int {

        // Extract the country code from the phone number
        val parsedPhoneNumber = phoneNumber.parsePhoneNumber()

        val authyUser: User
        try {
            authyUser = authyClient.users.createUser(
                email,
                parsedPhoneNumber.nationalNumber.toString(),
                parsedPhoneNumber.countryCode.toString()
            )
        } catch (e: AuthyException) {
            throw MfaException("An error occurred while creating user account in Authy.", e)
        }

        if (authyUser.error != null) {
            throw MfaException(authyUser.error.message)
        }

        return authyUser.id
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthyRegistrationService::class.java)
    }
}
