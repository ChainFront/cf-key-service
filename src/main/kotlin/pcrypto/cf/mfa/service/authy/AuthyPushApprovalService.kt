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
import com.authy.OneTouchException
import com.authy.api.ApprovalRequestParams
import com.authy.api.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pcrypto.cf.common.domain.TenantDomain
import pcrypto.cf.exception.MfaException
import java.util.*


@Service
class AuthyPushApprovalService
@Autowired constructor(private val authyClient: AuthyApiClient) {

    fun sendOneTouchToken(
        tenantDomain: TenantDomain,
        userName: String,
        authyId: Int,
        transactionId: UUID,
        chainType: String, // TODO: this should be an enum
        reason: String
    ): String {

        try {
            val params = ApprovalRequestParams.Builder(authyId, "ChainFront transaction approval requested")
                .setSecondsToExpire(300L)
                .addDetail("Authy ID", authyId.toString())
                .addDetail("Username", userName)
                .addDetail("Location", "Chicago, IL USA")
                .addDetail("Reason", reason)
                .addHiddenDetail("chain_type", chainType)
                .addHiddenDetail("tenant_id", tenantDomain.code)
                .addHiddenDetail("transaction_id", transactionId.toString())
                .addLogo(ApprovalRequestParams.Resolution.Default, "https://sandbox.chainfront.io/favicon.ico")
                .build()

            val response = authyClient.oneTouch.sendApprovalRequest(params)

            if (!response.isSuccess) {
                throw MfaException("Problem sending the token with OneTouch")
            }

            return response.approvalRequest.uuid

        } catch (e: AuthyException) {
            throw MfaException("Problem sending the token with OneTouch: " + e.message, e)
        }

    }


    fun retrieveOneTouchStatus(uuid: String): Boolean {
        try {
            return authyClient.oneTouch
                .getApprovalRequestStatus(uuid)
                .approvalRequest
                .status == "approved"
        } catch (e: OneTouchException) {
            throw MfaException(e.message ?: "", e)
        }

    }

    fun hasAuthyApp(authyId: Int): Boolean {
        val userStatus: UserStatus
        try {
            userStatus = authyClient.users
                .requestStatus(authyId)
        } catch (e: AuthyException) {
            throw MfaException(e.message ?: "", e)
        }

        return userStatus.isRegistered
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthyPushApprovalService::class.java)
    }
}
