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

package pcrypto.cf.stellar.api.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.stellar.sdk.Asset;
import org.stellar.sdk.responses.AccountResponse;
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.account.domain.repository.AccountRepository;
import pcrypto.cf.common.api.controller.ApiController;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.common.mail.service.EmailService;
import pcrypto.cf.docs.SwaggerTags;
import pcrypto.cf.exception.BadRequestException;
import pcrypto.cf.exception.NotFoundException;
import pcrypto.cf.mfa.service.totp.TotpService;
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.stellar.api.model.StellarAccount;
import pcrypto.cf.stellar.api.model.StellarAccountTrustline;
import pcrypto.cf.stellar.client.StellarNetworkService;
import pcrypto.cf.stellar.domain.entity.StellarAccountDomain;
import pcrypto.cf.stellar.domain.repository.StellarAccountRepository;
import pcrypto.cf.stellar.vault.dto.VaultStellarAccountDomain;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * API for the /stellar/accounts endpoint.
 */
@Api( tags = { SwaggerTags.STELLAR_ACCOUNTS } )
@ApiController
public class StellarAccountsApiController
{
    private static final Logger LOGGER = LoggerFactory.getLogger( StellarAccountsApiController.class );

    private final VaultOperations vaultOperations;

    private final AccountRepository accountRepository;

    private final StellarAccountRepository stellarAccountRepository;

    private final StellarNetworkService stellarNetworkService;

    private final TotpService totpService;

    private final EmailService emailService;


    @Autowired
    public StellarAccountsApiController( final VaultOperations vaultOperations,
                                         final AccountRepository accountRepository,
                                         final StellarAccountRepository stellarAccountRepository,
                                         final StellarNetworkService stellarNetworkService,
                                         final TotpService totpService,
                                         final EmailService emailService )
    {
        this.vaultOperations = vaultOperations;
        this.accountRepository = accountRepository;
        this.stellarAccountRepository = stellarAccountRepository;
        this.stellarNetworkService = stellarNetworkService;
        this.totpService = totpService;
        this.emailService = emailService;
    }


    @ApiOperation( value = "Get Stellar account",
                   nickname = "getStellarAccount",
                   notes = "Returns the Stellar account details for the given account and Stellar address",
                   response = StellarAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "read:stellar_accounts",
                                                                    description = "Ability to read Stellar accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.STELLAR_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Stellar account details",
                        response = StellarAccount.class ) } )
    @RequestMapping( value = "/accounts/{cfAccountId}/stellar",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<StellarAccount> getStellarAccount( final Authentication authentication,
                                                             @NotNull @PathVariable final Long cfAccountId )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the stellar account db record
        final Optional<StellarAccountDomain> stellarAccountDomain = stellarAccountRepository.findByCfAccountId( cfAccountId );
        if ( !stellarAccountDomain.isPresent() || !stellarAccountDomain.get().getTenantDomain().equals( tenantDomain ) )
        {
            throw new NotFoundException( "Stellar account for cfAccountId " + cfAccountId + " not found." );
        }

        // Get the vault path
        final String vaultPath = getStellarVaultPluginPath( cfAccountId, tenantDomain );

        // Read the account from Vault
        final VaultResponse vaultResponse = this.vaultOperations.read( vaultPath );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while reading the Stellar account." );
        }
        final VaultStellarAccountDomain vaultStellarAccountDomain = convertVaultResponseToDomain( cfAccountId, vaultResponse );

        // Create the StellarAccount object
        final StellarAccount stellarAccount = new StellarAccount();
        stellarAccount.setCfAccountId( cfAccountId );
        stellarAccount.setAddress( vaultStellarAccountDomain.getAddress() );
        stellarAccount.setWhitelistAddresses( vaultStellarAccountDomain.getWhitelist() );
        stellarAccount.setBlacklistAddresses( vaultStellarAccountDomain.getBlacklist() );
        stellarAccount.setCreatedDate( stellarAccountDomain.get().getCreatedDate() );
        stellarAccount.setLastModifiedDate( stellarAccountDomain.get().getLastModifiedDate() );

        // Add balances to StellarAccount object
        final List<StellarAccount.Balance> balances = getBalances( stellarAccount );
        stellarAccount.setBalances( balances );

        return new ResponseEntity<>( stellarAccount, HttpStatus.OK );
    }


    @ApiOperation( value = "Create an account on the Stellar network",
                   nickname = "createStellarAccount",
                   notes = "Create a new Stellar account. The seed, private and public key are created from a high entropy " +
                           "random number; all performed within the secure environment. The private key is created within the secure " +
                           "environment and is never viewable nor transmitted between servers.",
                   response = StellarAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:stellar_accounts",
                                                                    description = "Ability to create Stellar accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.STELLAR_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Account created successfully",
                        response = StellarAccount.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/stellar",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    //    @PreAuthorize( "hasAuthority('SUPER_ADMIN') OR hasAuthority('ADMIN')")
    public ResponseEntity<StellarAccount> createStellarAccount( final Authentication authentication,
                                                                @ApiParam( value = "Account identifier",
                                                                           required = true ) @PathVariable final Long cfAccountId,
                                                                @Valid @RequestBody final StellarAccount stellarAccount )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the chainfront account
        final Optional<AccountDomain> accountDomain = accountRepository.findById( cfAccountId );
        accountDomain.orElseThrow( () -> new NotFoundException( "Account " + cfAccountId + " not found." ) );

        // Check if we already have a Stellar account for this chainfront account
        final Optional<StellarAccountDomain> existingStellarAccountDomain = stellarAccountRepository.findByCfAccountId( cfAccountId );
        if ( existingStellarAccountDomain.isPresent() )
        {
            throw new BadRequestException( "A Stellar account already exists for this ChainFront account. Address: '" + existingStellarAccountDomain.get().getStellarAddress() + "'" );
        }

        // Get the path where this account will be stored
        final String path = getStellarVaultPluginPath( cfAccountId, tenantDomain );

        // Write stellar account to vault (plugin will generate private key behind the vault barrier)
        final VaultStellarAccountDomain vaultStellarAccountDomain = new VaultStellarAccountDomain();
        vaultStellarAccountDomain.setWhitelist( stellarAccount.getWhitelistAddresses() );
        vaultStellarAccountDomain.setBlacklist( stellarAccount.getBlacklistAddresses() );
        final VaultResponse vaultResponse = this.vaultOperations.write( path, vaultStellarAccountDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while generating Stellar account." );
        }
        final VaultStellarAccountDomain persistedVaultStellarDomain = convertVaultResponseToDomain( cfAccountId, vaultResponse );

        // Persist the new Stellar account to our database
        final StellarAccountDomain stellarAccountDomain = new StellarAccountDomain();
        stellarAccountDomain.setTenantDomain( tenantDomain );
        stellarAccountDomain.setAccountDomain( accountDomain.get() );
        stellarAccountDomain.setStellarAddress( persistedVaultStellarDomain.getAddress() );

        final StellarAccountDomain persistedStellarAccountDomain;
        try
        {
            persistedStellarAccountDomain = stellarAccountRepository.save( stellarAccountDomain );
        }
        catch ( final Exception e )
        {
            // At this point we have a funded Stellar account, but weren't able to save the address to our db.
            // The only thing we can do is record the error so that we can manually recover the account.
            LOGGER.error( "[ROLLBACK ERROR] Stellar account '" + persistedVaultStellarDomain.getAddress() + "' created, but db record creation failed.", e );

            throw e;
        }

        // Populate the model for return to the client
        final StellarAccount createdAccount = new StellarAccount();
        createdAccount.setCfAccountId( cfAccountId );
        createdAccount.setAddress( persistedVaultStellarDomain.getAddress() );
        createdAccount.setWhitelistAddresses( persistedVaultStellarDomain.getWhitelist() );
        createdAccount.setBlacklistAddresses( persistedVaultStellarDomain.getBlacklist() );
        createdAccount.setCreatedDate( persistedStellarAccountDomain.getCreatedDate() );

        // Add balances to StellarAccount object
        final List<StellarAccount.Balance> balances = getBalances( createdAccount );
        createdAccount.setBalances( balances );

        return new ResponseEntity<>( createdAccount, HttpStatus.CREATED );
    }


    @ApiOperation( value = "Add a trustline to an account on the Stellar network",
                   nickname = "createStellarTrustlineForAccount",
                   notes = "Create a new trustline for a Stellar account",
                   response = StellarAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:stellar_accounts",
                                                                    description = "Ability to create Stellar accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.STELLAR_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Trustline created successfully",
                        response = StellarAccount.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/stellar/{stellarAddress}/trustline",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<StellarAccount> createStellarTrustline( final Authentication authentication,
                                                                  @ApiParam( value = "Account identifier",
                                                                             required = true ) @PathVariable final Long cfAccountId,
                                                                  @NotNull @PathVariable final String stellarAddress,
                                                                  @Valid @RequestBody final StellarAccountTrustline stellarAccountTrustline )
    {
        return null;
    }


    private List<StellarAccount.Balance> getBalances( final StellarAccount stellarAccount )
    {
        final List<StellarAccount.Balance> balances = new ArrayList<>();
        final AccountResponse.Balance[] stellarAccountBalances = stellarNetworkService.getAccountBalances( stellarAccount.getAddress() );
        for ( final AccountResponse.Balance stellarAccountBalance : stellarAccountBalances )
        {
            final Asset asset = stellarAccountBalance.getAsset();
            String assetCode = "XLM";
            String assetIssuer = null;
            if ( !"native".equals( asset.getType() ) )
            {
                assetCode = stellarAccountBalance.getAssetCode();
                assetIssuer = stellarAccountBalance.getAssetIssuer().getAccountId();
            }

            final StellarAccount.Balance balance = new StellarAccount.Balance( stellarAccountBalance.getAssetType(),
                                                                               assetCode,
                                                                               assetIssuer,
                                                                               stellarAccountBalance.getLimit(),
                                                                               stellarAccountBalance.getBalance() );
            balances.add( balance );
        }
        return balances;
    }


    private String getStellarVaultPluginPath( final Long cfAccountId,
                                              final TenantDomain tenantDomain )
    {
        return "/stellar/" + tenantDomain.getId() + "/accounts/" + cfAccountId;
    }


    @SuppressWarnings( "unchecked" )
    private VaultStellarAccountDomain convertVaultResponseToDomain( final Long cfAccountId,
                                                                    final VaultResponse vaultResponse )
    {
        final Map<String, Object> data = vaultResponse.getData();
        if ( null == data )
        {
            throw new VaultException( "No vault data found for account " + cfAccountId );
        }

        final String address = (String) data.get( "address" );
        final List<String> whitelists = (List<String>) data.getOrDefault( "whitelist", Collections.emptyList() );
        final List<String> blacklists = (List<String>) data.getOrDefault( "blacklist", Collections.emptyList() );

        final VaultStellarAccountDomain domain = new VaultStellarAccountDomain();
        domain.setAddress( address );
        domain.setWhitelist( whitelists );
        domain.setBlacklist( blacklists );
        return domain;
    }
}
