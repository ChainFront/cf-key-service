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

package pcrypto.cf.bitcoin.api.controller

import io.swagger.annotations.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.vault.VaultException
import org.springframework.vault.core.VaultOperations
import org.springframework.vault.support.VaultResponse
import org.springframework.web.bind.annotation.*
import pcrypto.cf.account.domain.repository.AccountRepository
import pcrypto.cf.bitcoin.api.model.BitcoinAccount
import pcrypto.cf.bitcoin.client.BitcoindClient
import pcrypto.cf.bitcoin.domain.entity.BitcoinAccountDomain
import pcrypto.cf.bitcoin.domain.repository.BitcoinAccountRepository
import pcrypto.cf.bitcoin.vault.dto.VaultBitcoinAccountDomain
import pcrypto.cf.common.api.controller.ApiController
import pcrypto.cf.common.domain.TenantDomain
import pcrypto.cf.docs.SwaggerTags
import pcrypto.cf.exception.BadRequestException
import pcrypto.cf.exception.NotFoundException
import pcrypto.cf.security.domain.CustomUserDetails
import javax.validation.Valid
import javax.validation.constraints.NotNull


/**
 * API for the /bitcoin/accounts endpoint.
 */
@Api(tags = [SwaggerTags.BITCOIN_ACCOUNTS])
@ApiController
class BitcoinAccountsApiController
@Autowired constructor(
    private val accountRepository: AccountRepository,
    private val bitcoinAccountRepository: BitcoinAccountRepository,
    private val vaultOperations: VaultOperations,
    private val bitcoindClient: BitcoindClient
) {

    @ApiOperation(
        value = "Get Bitcoin account",
        nickname = "getBitcoinAccount",
        notes = "Returns the Bitcoin account details for the given account and Bitcoin address",
        response = BitcoinAccount::class,
        authorizations = [Authorization(
            value = "OAuth2",
            scopes = [AuthorizationScope(
                scope = "read:bitcoin_accounts",
                description = "Ability to read Bitcoin accounts"
            )]
        ), Authorization(value = "ApiKey")],
        tags = [SwaggerTags.BITCOIN_ACCOUNTS]
    )
    @ApiResponses(
        value = [ApiResponse(
            code = 200,
            message = "Bitcoin account details",
            response = BitcoinAccount::class
        )]
    )
    @RequestMapping(
        value = ["/accounts/{cfAccountId}/bitcoin"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.GET]
    )
    fun getBitcoinAccount(
        authentication: Authentication,
        @NotNull @PathVariable cfAccountId: Long
    ): ResponseEntity<BitcoinAccount> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Obtain the bitcoin account db record
        val bitcoinAccountDomain = bitcoinAccountRepository.findByCfAccountId(cfAccountId)

        if (!bitcoinAccountDomain.isPresent || bitcoinAccountDomain.get().tenantDomain != tenantDomain) {
            throw NotFoundException("Bitcoin account for cfAccountId $cfAccountId not found.")
        }

        // Get the vault path
        val vaultPath = getBitcoinVaultPluginPath(cfAccountId, tenantDomain)

        // Read the account from Vault
        val vaultResponse = this.vaultOperations.read(vaultPath)
            ?: throw VaultException("An error occurred while reading the Bitcoin account.")
        val vaultBitcoinAccountDomain = convertVaultResponseToDomain(cfAccountId, vaultResponse)

        // Create the BitcoinAccount object
        val bitcoinAccount = BitcoinAccount(cfAccountId, vaultBitcoinAccountDomain.address)
        bitcoinAccount.whitelistAddresses = vaultBitcoinAccountDomain.whitelist
        bitcoinAccount.blacklistAddresses = vaultBitcoinAccountDomain.blacklist
        bitcoinAccount.createdDate = bitcoinAccountDomain.get().createdDate
        bitcoinAccount.lastModifiedDate = bitcoinAccountDomain.get().lastModifiedDate

        // Add balances to BitcoinAccount object
        val balances = getBalances(bitcoinAccount)
        bitcoinAccount.balances = balances

        return ResponseEntity(bitcoinAccount, HttpStatus.OK)
    }


    @ApiOperation(
        value = "Create a Bitcoin account",
        nickname = "createBitcoinAccount",
        notes = "Create a new Bitcoin account. The private and public key are created from a high entropy " +
                "random number; all performed within the secure environment. The private key is created within the secure " +
                "environment and is never viewable nor transmitted between servers.",
        response = BitcoinAccount::class,
        authorizations = [Authorization(
            value = "OAuth2",
            scopes = [AuthorizationScope(
                scope = "write:bitcoin_accounts",
                description = "Ability to create Bitcoin accounts"
            )]
        ), Authorization(value = "ApiKey")],
        tags = [SwaggerTags.BITCOIN_ACCOUNTS]
    )
    @ApiResponses(
        value = [ApiResponse(
            code = 201,
            message = "Account created successfully",
            response = BitcoinAccount::class
        )]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(
        value = ["/accounts/{cfAccountId}/bitcoin"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    //    @PreAuthorize( "hasAuthority('SUPER_ADMIN') OR hasAuthority('ADMIN')")
    fun createBitcoinAccount(
        authentication: Authentication,
        @ApiParam(value = "Account identifier", required = true) @PathVariable cfAccountId: Long,
        @Valid @RequestBody bitcoinAccount: BitcoinAccount
    ): ResponseEntity<BitcoinAccount> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Obtain the chainfront account
        val accountDomain = accountRepository.findById(cfAccountId)
        accountDomain.orElseThrow { NotFoundException("Account $cfAccountId not found.") }

        // Check if we already have a Bitcoin account for this chainfront account
        val existingBitcoinAccountDomain = bitcoinAccountRepository.findByCfAccountId(cfAccountId)
        if (existingBitcoinAccountDomain.isPresent) {
            throw BadRequestException("A Bitcoin account already exists for this ChainFront account. Address: '" + existingBitcoinAccountDomain.get().bitcoinAddress + "'")
        }

        // Get the path where this account will be stored
        val path = getBitcoinVaultPluginPath(cfAccountId, tenantDomain)

        // Write bitcoin account to vault (plugin will generate private key behind the vault barrier)
        val vaultBitcoinAccountDomain = VaultBitcoinAccountDomain()
        vaultBitcoinAccountDomain.whitelist = bitcoinAccount.whitelistAddresses
        vaultBitcoinAccountDomain.blacklist = bitcoinAccount.blacklistAddresses
        val vaultResponse = this.vaultOperations.write(path, vaultBitcoinAccountDomain)
            ?: throw VaultException("An error occurred while generating Bitcoin account.")
        val persistedVaultBitcoinDomain = convertVaultResponseToDomain(cfAccountId, vaultResponse)

        // Persist the new Bitcoin account to our database
        val bitcoinAccountDomain = BitcoinAccountDomain()
        bitcoinAccountDomain.tenantDomain = tenantDomain
        bitcoinAccountDomain.accountDomain = accountDomain.get()
        bitcoinAccountDomain.bitcoinAddress = persistedVaultBitcoinDomain.address

        val persistedBitcoinAccountDomain: BitcoinAccountDomain
        try {
            persistedBitcoinAccountDomain = bitcoinAccountRepository.save(bitcoinAccountDomain)
        } catch (e: Exception) {
            // At this point we have a funded Bitcoin account, but weren't able to save the address to our db.
            // The only thing we can do is record the error so that we can manually recover the account.
            log.error(
                "[ROLLBACK ERROR] Bitcoin account '" + persistedVaultBitcoinDomain.address + "' created, but db record creation failed.",
                e
            )
            throw e
        }

        // Populate the model for return to the client
        val createdAccount = BitcoinAccount(cfAccountId, persistedVaultBitcoinDomain.address)
        createdAccount.whitelistAddresses = persistedVaultBitcoinDomain.whitelist
        createdAccount.blacklistAddresses = persistedVaultBitcoinDomain.blacklist
        createdAccount.createdDate = persistedBitcoinAccountDomain.createdDate

        // Add balances to BitcoinAccount object
        val balances = getBalances(createdAccount)
        createdAccount.balances = balances

        return ResponseEntity(createdAccount, HttpStatus.CREATED)
    }


    private fun getBalances(bitcoinAccount: BitcoinAccount): List<BitcoinAccount.Balance> {

        val bitcoinAccountDto = bitcoindClient.getAccount(bitcoinAccount.address)
        val balance = bitcoinAccountDto?.balance
        val bitcoinBalance = BitcoinAccount.Balance("native", "BTC", balance.toString())
        return listOf(bitcoinBalance)
    }


    private fun convertVaultResponseToDomain(
        cfAccountId: Long,
        vaultResponse: VaultResponse
    ): VaultBitcoinAccountDomain {

        val data = vaultResponse.data ?: throw VaultException("No vault data found for account $cfAccountId")

        val address = data["address"] as String

        val whitelists = data.getOrDefault("whitelist", emptyList<String>()) as List<String>
        val blacklists = data.getOrDefault("blacklist", emptyList<String>()) as List<String>

        val domain = VaultBitcoinAccountDomain()
        domain.address = address
        domain.whitelist = whitelists
        domain.blacklist = blacklists
        return domain
    }


    private fun getBitcoinVaultPluginPath(
        cfAccountId: Long,
        tenantDomain: TenantDomain
    ): String {

        return "/bitcoin/${tenantDomain.id}/accounts/$cfAccountId"
    }

    companion object {
        private val log = LoggerFactory.getLogger(BitcoinAccountsApiController::class.java)
    }
}
