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

package pcrypto.cf.account.domain.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pcrypto.cf.account.api.model.AccountIdentifier
import pcrypto.cf.account.domain.entity.AccountConfigurationDomain
import pcrypto.cf.account.domain.entity.AccountDomain
import pcrypto.cf.account.domain.repository.AccountConfigurationRepository
import pcrypto.cf.account.domain.repository.AccountRepository
import pcrypto.cf.account.value.AccountStatusEnum
import pcrypto.cf.account.value.TxApprovalMethodEnum
import pcrypto.cf.common.domain.TenantDomain
import pcrypto.cf.exception.NotFoundException
import pcrypto.cf.mfa.service.authy.AuthyRegistrationService
import pcrypto.cf.mfa.service.authy.SmsVerificationService
import pcrypto.cf.mfa.service.totp.TotpService
import pcrypto.cf.security.domain.CustomUserDetails


@Service
open class AccountService
@Autowired constructor(
    private val accountRepository: AccountRepository,
    private val accountConfigurationRepository: AccountConfigurationRepository,
    private val totpService: TotpService,
    private val smsVerificationService: SmsVerificationService,
    private val authyRegistrationService: AuthyRegistrationService
) {


    @Transactional
    open fun createAccount(
        authentication: Authentication,
        accountDomain: AccountDomain
    ): AccountDomain {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // If we're using implicit approvals, we immediately activate the account.
        // If we're using MFA, we initially set the account status to PENDING.
        accountDomain.status = when (accountDomain.txApprovalMethod) {
            TxApprovalMethodEnum.IMPLICIT.id -> AccountStatusEnum.ACTIVE.id
            else -> AccountStatusEnum.PENDING.id
        }

        // Write to the database
        val persistedAccountDomain = accountRepository.save(accountDomain)

        // Process the MFA registration
        when (persistedAccountDomain.txApprovalMethod) {
            TxApprovalMethodEnum.APP_TOTP.id -> {
                val totpKey = totpService.createTotpKey(
                    authentication,
                    persistedAccountDomain.id,
                    persistedAccountDomain.email
                )
                persistedAccountDomain.qrCode = totpKey.barcode
                persistedAccountDomain.otpauthUrl = totpKey.url

                // Send an SMS verification to the user's phone
                smsVerificationService.sendVerificationMessage(persistedAccountDomain.phone, "sms")
            }

            TxApprovalMethodEnum.AUTHY_PUSH.id -> {
                val authyId = authyRegistrationService.register(
                    persistedAccountDomain.phone,
                    persistedAccountDomain.email
                )

                val accountConfigurationDomain = AccountConfigurationDomain()
                accountConfigurationDomain.tenantDomain = tenantDomain
                accountConfigurationDomain.accountDomain = persistedAccountDomain
                accountConfigurationDomain.authyId = authyId
                accountConfigurationRepository.save(accountConfigurationDomain)
            }

            TxApprovalMethodEnum.CHAINFRONT_TOTP.id -> {
                throw UnsupportedOperationException("The transaction approval method 'CHAINFRONT_TOTP' is still in development. Please use either 'AUTHY_PUSH' or 'TOTP' in the meantime.")
            }
        }

        return persistedAccountDomain
    }


    open fun getCfAccountDomainByIdentifier(
        tenantDomain: TenantDomain,
        accountIdentifer: AccountIdentifier
    ): AccountDomain {
        val accountIdStr = accountIdentifer.identifier
        // TODO: support by email and by username
        val accountDomainOptional = accountRepository.findById(java.lang.Long.valueOf(accountIdStr))
        if (!accountDomainOptional.isPresent || accountDomainOptional.get().tenantDomain != tenantDomain) {
            // TODO: better error message
            throw NotFoundException("ChainFront account $accountIdStr not found.")
        }
        return accountDomainOptional.get()
    }
}
