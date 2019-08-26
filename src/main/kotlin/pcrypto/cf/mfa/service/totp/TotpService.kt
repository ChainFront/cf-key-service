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

package pcrypto.cf.mfa.service.totp

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.vault.VaultException
import org.springframework.vault.core.VaultOperations
import pcrypto.cf.common.domain.TenantDomain
import pcrypto.cf.mfa.dto.TotpKey
import pcrypto.cf.security.domain.CustomUserDetails
import java.util.*


@Service
class TotpService(private val vaultOperations: VaultOperations) {

    fun createTotpKey(
        authentication: Authentication,
        cfAccountId: Long?,
        userEmail: String
    ): TotpKey {

        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Get the tenant-specific TOTP mount
        val path = getTotpVaultPluginPath(tenantDomain) + "/keys/" + cfAccountId

        val body = HashMap<String, String>()
        body["generate"] = "true"
        body["algorithm"] =
            "SHA1" // Google Authenticator only supports SHA1, so we use it here instead of a stronger hashing algorithm
        body["issuer"] = "ChainFront"
        body["account_name"] = userEmail

        val vaultResponse = this.vaultOperations.write(path, body)
            ?: throw VaultException("No response from vault server when generating TOTP code for " + cfAccountId!!)

        val data = vaultResponse.data
            ?: throw VaultException("Vault response when generating TOTP code contained a null data map : " + cfAccountId!!)
        val barcode = data["barcode"] as String
        val url = data["url"] as String

        return TotpKey(barcode, url)
    }


    fun verifyCode(
        authentication: Authentication,
        userId: Long?,
        code: String
    ): Boolean {
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Get the tenant-specific TOTP mount
        val path = getTotpVaultPluginPath(tenantDomain) + "/code/" + userId

        val body = HashMap<String, String>()
        body["code"] = code

        val vaultResponse = this.vaultOperations.write(path, body)
            ?: throw VaultException("No response from vault server when validating TOTP code for " + userId!!)

        val data = vaultResponse.data
            ?: throw VaultException("Vault response when validating TOTP code contained a null data map : " + userId!!)

        return data["valid"] as Boolean
    }


    private fun getTotpVaultPluginPath(tenantDomain: TenantDomain): String {
        return "/totp/" + tenantDomain.id!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(TotpService::class.java)
    }

}
