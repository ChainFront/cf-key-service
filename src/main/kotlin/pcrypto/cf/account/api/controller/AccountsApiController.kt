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

package pcrypto.cf.account.api.controller

import io.swagger.annotations.*
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pcrypto.cf.account.api.model.Account
import pcrypto.cf.account.api.model.Accounts
import pcrypto.cf.account.api.model.SmsVerificationCode
import pcrypto.cf.account.api.model.VerificationInfo
import pcrypto.cf.account.domain.entity.AccountDomain
import pcrypto.cf.account.domain.entity.AccountDomainSpecificationsBuilder
import pcrypto.cf.account.domain.repository.AccountRepository
import pcrypto.cf.account.domain.service.AccountService
import pcrypto.cf.account.value.AccountStatusEnum
import pcrypto.cf.account.value.TxApprovalMethodEnum
import pcrypto.cf.common.api.controller.ApiController
import pcrypto.cf.common.domain.TenantDomain
import pcrypto.cf.common.util.formatPhoneNumber
import pcrypto.cf.docs.ApiCodeSample
import pcrypto.cf.docs.ApiCodeSamples
import pcrypto.cf.docs.SwaggerTags
import pcrypto.cf.exception.BadRequestException
import pcrypto.cf.exception.NotFoundException
import pcrypto.cf.mfa.service.authy.SmsVerificationService
import pcrypto.cf.security.domain.CustomUserDetails
import java.time.OffsetDateTime
import java.util.regex.Pattern
import javax.validation.Valid
import javax.validation.constraints.NotNull


@Api(tags = [SwaggerTags.ACCOUNTS])
@ApiController
class AccountsApiController
@Autowired constructor(
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
    private val smsVerificationService: SmsVerificationService
) {

    @ApiOperation(
        value = "List accounts",
        nickname = "listAccounts",
        notes = "Returns a paginated list of all accounts.",
        response = Accounts::class,
        authorizations = [
            Authorization(
                value = "OAuth2",
                scopes = [AuthorizationScope(scope = "read:accounts", description = "Ability to read accounts")]
            ),
            Authorization(value = "ApiKey")],
        tags = [SwaggerTags.ACCOUNTS]
    )
    @ApiResponses(value = [ApiResponse(code = 200, message = "A list of accounts", response = Accounts::class)])
    @RequestMapping(
        value = ["/accounts"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.GET]
    )
    fun listAccounts(
        @ApiParam(value = "Number of accounts returned") @RequestParam(
            value = "pageSize",
            required = false
        ) @Valid pageSize: Int?,
        @ApiParam(value = "Page number") @RequestParam(value = "pageNumber", required = false) @Valid pageNumber: Int?
    ): ResponseEntity<Accounts> {

        val accountDomains = accountRepository.findAll()

        val accounts = Accounts()

        for (accountDomain in accountDomains) {
            val account = convertDomainToModel(accountDomain)
            accounts.add(account)
        }

        return ResponseEntity(accounts, HttpStatus.OK)
    }


    @ApiOperation(
        value = "Get account",
        nickname = "getAccount",
        notes = "Returns account details for the given account identifier",
        response = Account::class,
        authorizations = [Authorization(
            value = "OAuth2",
            scopes = [AuthorizationScope(scope = "read:accounts", description = "Ability to read accounts")]
        ), Authorization(value = "ApiKey")],
        tags = [SwaggerTags.ACCOUNTS]
    )
    @ApiResponses(value = [ApiResponse(code = 200, message = "Account details", response = Account::class)])
    @RequestMapping(
        value = ["/accounts/{cfAccountId}"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.GET]
    )
    fun getAccount(
        authentication: Authentication,
        @ApiParam(value = "Account identifier", required = true) @PathVariable cfAccountId: Long?
    ): ResponseEntity<Account> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        val accountDomain = accountRepository.findById(cfAccountId!!)

        if (!accountDomain.isPresent || tenantDomain.id == accountDomain.get().tenantDomain?.id) {
            throw NotFoundException("User not found")
        }

        val account = convertDomainToModel(accountDomain.get())

        return ResponseEntity(account, HttpStatus.OK)
    }


    @ApiOperation(
        value = "Search for accounts",
        nickname = "searchAccounts",
        notes = "Search for accounts matching a given set of criteria. This uses a simple query language. \n\n" +
                "To search for a user by email: /accounts/search?query=email:john.doe@company.com. \n\n" +
                "To search by user name: /accounts/search?query=userName:johndoe. \n\n" +
                "To search by phone number: /accounts/search?query=phone:555-555-5555.",
        response = Accounts::class,
        authorizations = [
            Authorization(
                value = "OAuth2",
                scopes = [AuthorizationScope(scope = "read:accounts", description = "Ability to read accounts")]
            ),
            Authorization(value = "ApiKey")],
        tags = [SwaggerTags.ACCOUNTS]
    )
    @ApiResponses(value = [ApiResponse(code = 200, message = "A list of accounts", response = Accounts::class)])
    @RequestMapping(
        value = ["/accounts/search"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.GET]
    )
    fun searchAccounts(
        authentication: Authentication,
        @ApiParam(value = "Search query string") @RequestParam(value = "query") query: String
    ): ResponseEntity<Accounts> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Build our specifications object to send to the JPA query layer
        val builder = AccountDomainSpecificationsBuilder()
        val pattern = Pattern.compile("(\\w+?)(:|<|>)(\\w+?),")
        val matcher = pattern.matcher("$query,")
        while (matcher.find()) {
            val fieldName = matcher.group(1)
            if (StringUtils.indexOfAny(fieldName, arrayOf("userName", "email", "phone")) < 0) {
                throw BadRequestException("Query string must be against one of the following fields: [userName, email, phone]")
            }
            builder.with(fieldName, matcher.group(2), matcher.group(3))
        }

        val spec = builder.build()

        // Find all matching accounts
        val accountDomains = accountRepository.findAll(spec)

        // Convert to our rest model
        val accounts = Accounts()
        for (accountDomain in accountDomains) {
            // Quick double-check on tenant id (not technically necessary as we're using schema separation)
            if (tenantDomain.id != accountDomain.tenantDomain?.id) {
                continue
            }

            val account = convertDomainToModel(accountDomain)
            accounts.add(account)
        }

        return ResponseEntity(accounts, HttpStatus.OK)
    }


    @ApiOperation(
        value = "Create account",
        nickname = "createAccount",
        notes = "Create a new account",
        response = Account::class,
        authorizations = [
            Authorization(
                value = "OAuth2",
                scopes = [AuthorizationScope(
                    scope = "write:accounts",
                    description = "Ability to create accounts"
                ), AuthorizationScope(scope = "read:accounts", description = "Ability to read accounts")]
            ),
            Authorization(value = "ApiKey")],
        tags = [SwaggerTags.ACCOUNTS]
    )
    @ApiResponses(
        value = [ApiResponse(
            code = 201,
            message = "Account created successfully",
            response = Account::class
        )]
    )
    @ApiCodeSamples(
        samples = [ApiCodeSample(
            lang = "Curl", source = "curl --verbose -X POST \\\n" +
                    "  https://sandbox.chainfront.io/api/v1/accounts \\\n" +
                    "  -H 'Accept: application/json' \\\n" +
                    "  -H 'Authorization: Basic am9objpqb2hu...' \\\n" +
                    "  -H 'Content-type: application/json' \\\n" +
                    "  -H 'X-CUSTOMER-ID: YOUR_CUSTOMER_ID' \\\n" +
                    "  -d '{\n" +
                    "\t\"userName\": \"myUser\",\n" +
                    "\t\"email\": \"myuser@mycompany.com\",\n" +
                    "\t\"phone\": \"555-555-5555\",\n" +
                    "\t\"txApprovalMethod\": \"CHAINFRONT_TOTP\",\n" +
                    "\t\"sendSmsVerification\": \"true\"\n" +
                    "}'"
        )]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(
        value = ["/accounts"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    //    @PreAuthorize( "hasAuthority('SUPER_ADMIN') OR hasAuthority('ADMIN')")
    fun createAccount(
        authentication: Authentication,
        @ApiParam(value = "Account to create.") @Valid @RequestBody account: Account
    ): ResponseEntity<Account> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Check for duplicate username
        val byUsername = accountRepository.findByUserName(account.userName)
        if (!byUsername.isEmpty()) {
            throw BadRequestException("Username '" + account.userName + "' already exists.")
        }

        // Check for duplicate phone number
        val formattedPhoneNumber = account.phone.formatPhoneNumber()
        val byPhone = accountRepository.findByPhone(formattedPhoneNumber)
        if (!byPhone.isEmpty()) {
            throw BadRequestException("Phone number '" + account.phone + "' already exists.")
        }

        // Convert the phone number to E164 format
        account.phone = formattedPhoneNumber

        // Convert input model to db entity object
        val accountDomain = convertModelToDomain(account, tenantDomain)

        // Call the transactional createAccount service to persist to the db and generate appropriate MFA registration events
        val persistedAccountDomain = accountService.createAccount(authentication, accountDomain)

        // Convert the persisted entity back into a REST resource model
        val createdAccount = convertDomainToModel(persistedAccountDomain)

        // Build and populate the registration verification info object (will have information for certain MFA types)
        val verificationInfo = VerificationInfo(
            "",
            persistedAccountDomain.otpauthUrl,
            persistedAccountDomain.qrCode
        )

        createdAccount.verification = verificationInfo

        return ResponseEntity(createdAccount, HttpStatus.CREATED)
    }


    @ApiOperation(
        value = "Verify an account",
        nickname = "verifyAccount",
        notes = "Verify a new account by checking an SMS code sent to a user.",
        response = Account::class,
        authorizations = [
            Authorization(
                value = "OAuth2",
                scopes = [AuthorizationScope(
                    scope = "write:accounts",
                    description = "Ability to create accounts"
                ), AuthorizationScope(scope = "read:accounts", description = "Ability to read accounts")]
            ),
            Authorization(value = "ApiKey")],
        tags = [SwaggerTags.ACCOUNTS]
    )
    @ApiResponses(
        value = [ApiResponse(
            code = 201,
            message = "Account verified successfully",
            response = Account::class
        )]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(
        value = ["/accounts/{cfAccountId}/verification"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    //    @PreAuthorize( "hasAuthority('SUPER_ADMIN') OR hasAuthority('ADMIN')")
    fun verifyAccount(
        authentication: Authentication,
        @ApiParam(value = "The ChainFront account id.") @NotNull @PathVariable cfAccountId: Long?,
        @ApiParam(value = "The phone verification code.") @Valid @RequestBody smsVerificationCode: SmsVerificationCode
    ): ResponseEntity<Account> {

        // Obtain the current tenant
        val userDetails = authentication.principal as CustomUserDetails
        val tenantDomain = userDetails.tenantDomain

        // Obtain the account db record
        val accountDomain = accountRepository.findById(cfAccountId!!)
        if (!accountDomain.isPresent || accountDomain.get().tenantDomain == tenantDomain) {
            throw NotFoundException("Account for cfAccountId $cfAccountId not found.")
        }

        // Verify the phone number with Authy
        val success = smsVerificationService.verify(
            accountDomain.get().phone,
            smsVerificationCode.code
        )

        if (success) {
            // Update the status of the account
            // TODO: handle pending MFA registrations as well
            accountDomain.get().status = AccountStatusEnum.ACTIVE.id
            accountRepository.save(accountDomain.get())
        } else {
            throw BadRequestException("Invalid SMS verification code.")
        }

        val account = convertDomainToModel(accountDomain.get())
        return ResponseEntity(account, HttpStatus.OK)
    }


    private fun convertDomainToModel(domain: AccountDomain): Account {
        val model = Account(
            domain.id,
            domain.userName,
            domain.email,
            domain.phone,
            TxApprovalMethodEnum.fromId(domain.txApprovalMethod),
            AccountStatusEnum.fromId(domain.status),
            null,
            domain.createdDate ?: OffsetDateTime.now(),
            domain.lastModifiedDate ?: OffsetDateTime.now()
        )

        return model
    }

    private fun convertModelToDomain(
        account: Account,
        tenantDomain: TenantDomain
    ): AccountDomain {
        val accountDomain = AccountDomain()
        accountDomain.tenantDomain = tenantDomain
        accountDomain.userName = account.userName
        accountDomain.email = account.email
        accountDomain.phone = account.phone
        accountDomain.txApprovalMethod = account.txApprovalMethod.id
        accountDomain.status = account.status.id

        return accountDomain
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccountsApiController::class.java)
    }
}
