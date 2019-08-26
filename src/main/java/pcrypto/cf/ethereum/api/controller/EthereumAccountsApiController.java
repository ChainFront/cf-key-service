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

package pcrypto.cf.ethereum.api.controller;

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
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.account.domain.repository.AccountRepository;
import pcrypto.cf.common.api.controller.ApiController;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.docs.SwaggerTags;
import pcrypto.cf.ethereum.api.model.EthereumAccount;
import pcrypto.cf.ethereum.client.EthereumAccountClient;
import pcrypto.cf.ethereum.domain.entity.EthereumAccountDomain;
import pcrypto.cf.ethereum.domain.repository.EthereumAccountRepository;
import pcrypto.cf.ethereum.vault.dto.VaultEthereumAccountDomain;
import pcrypto.cf.exception.BadRequestException;
import pcrypto.cf.exception.NotFoundException;
import pcrypto.cf.security.domain.CustomUserDetails;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Api( tags = { SwaggerTags.ETH_ACCOUNTS } )
@ApiController
public class EthereumAccountsApiController
{
    private static final Logger LOGGER = LoggerFactory.getLogger( EthereumAccountsApiController.class );


    private final AccountRepository accountRepository;

    private final EthereumAccountRepository ethereumAccountRepository;

    private final EthereumAccountClient ethereumAccountClient;

    private final VaultOperations vaultOperations;


    @Autowired
    public EthereumAccountsApiController( final AccountRepository accountRepository,
                                          final VaultOperations vaultOperations,
                                          final EthereumAccountRepository ethereumAccountRepository,
                                          final EthereumAccountClient ethereumAccountClient )
    {
        this.accountRepository = accountRepository;
        this.vaultOperations = vaultOperations;
        this.ethereumAccountRepository = ethereumAccountRepository;
        this.ethereumAccountClient = ethereumAccountClient;
    }


    @ApiOperation( value = "Get Ethereum account",
                   nickname = "getEthereumAccount",
                   notes = "Returns the Ethereum account details for the given account and Ethereum address",
                   response = EthereumAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "read:ethereum_accounts",
                                                                    description = "Ability to read Ethereum accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Ethereum account details",
                        response = EthereumAccount.class ) } )
    @RequestMapping( value = "/accounts/{cfAccountId}/ethereum",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<EthereumAccount> getEthereumAccount( final Authentication authentication,
                                                               @NotNull @PathVariable final Long cfAccountId )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the ethereum account db record
        final Optional<EthereumAccountDomain> ethereumAccountDomain = ethereumAccountRepository.findByCfAccountId( cfAccountId );

        if ( !ethereumAccountDomain.isPresent() || !ethereumAccountDomain.get().getTenantDomain().equals( tenantDomain ) )
        {
            throw new NotFoundException( "Ethereum account for cfAccountId " + cfAccountId + " not found." );
        }

        // Get the vault path
        final String vaultPath = getEthereumVaultPluginPath( cfAccountId, tenantDomain );

        // Read the account from Vault
        final VaultResponse vaultResponse = this.vaultOperations.read( vaultPath );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while reading the Ethereum account." );
        }
        final VaultEthereumAccountDomain vaultEthereumAccountDomain = convertVaultResponseToDomain( cfAccountId, vaultResponse );

        // Create the EthereumAccount object
        final EthereumAccount ethereumAccount = new EthereumAccount();
        ethereumAccount.setCfAccountId( cfAccountId );
        ethereumAccount.setAddress( vaultEthereumAccountDomain.getAddress() );
        ethereumAccount.setWhitelistAddresses( vaultEthereumAccountDomain.getWhitelist() );
        ethereumAccount.setBlacklistAddresses( vaultEthereumAccountDomain.getBlacklist() );
        ethereumAccount.setCreatedDate( ethereumAccountDomain.get().getCreatedDate() );
        ethereumAccount.setLastModifiedDate( ethereumAccountDomain.get().getLastModifiedDate() );

        // Add balance to EthereumAccount object
        final BigInteger accountBalance = ethereumAccountClient.getAccountBalance( ethereumAccount.getAddress() );
        ethereumAccount.setBalance( new BigDecimal( accountBalance ) );

        return new ResponseEntity<>( ethereumAccount, HttpStatus.OK );
    }


    @ApiOperation( value = "Create an Ethereum account",
                   nickname = "createEthereumAccount",
                   notes = "Create a new Ethereum account. The private and public key are created from a high entropy " +
                           "random number; all performed within the secure environment. The private key is created within the " +
                           "secure environment and is never viewable nor transmitted between servers.",
                   response = EthereumAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ethereum_accounts",
                                                                    description = "Ability to create Ethereum accounts" )
                                         } )
                   },
                   tags = { SwaggerTags.ETH_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Account created successfully",
                        response = EthereumAccount.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/ethereum",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<EthereumAccount> createEthereumAccount( final Authentication authentication,
                                                                  @ApiParam( value = "Account identifier",
                                                                             required = true ) @PathVariable final Long cfAccountId,
                                                                  @ApiParam( value = "Account to create.",
                                                                             required = true ) @Valid @RequestBody final EthereumAccount ethereumAccount )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the chainfront account
        final Optional<AccountDomain> accountDomain = accountRepository.findById( cfAccountId );
        accountDomain.orElseThrow( () -> new NotFoundException( "Account " + cfAccountId + " not found." ) );

        // Check if we already have a Ethereum account for this chainfront account
        final Optional<EthereumAccountDomain> existingEthereumAccountDomain = ethereumAccountRepository.findByCfAccountId( cfAccountId );
        if ( existingEthereumAccountDomain.isPresent() )
        {
            throw new BadRequestException( "An Ethereum account already exists for this ChainFront account. Address: '" + existingEthereumAccountDomain.get().getEthereumAddress() + "'" );
        }

        // Get the path where this account will be stored
        final String path = getEthereumVaultPluginPath( cfAccountId, tenantDomain );

        // Write ethereum account to vault (plugin will generate private key behind the vault barrier)
        final VaultEthereumAccountDomain vaultEthereumAccountDomain = new VaultEthereumAccountDomain();
        vaultEthereumAccountDomain.setWhitelist( ethereumAccount.getWhitelistAddresses() );
        vaultEthereumAccountDomain.setBlacklist( ethereumAccount.getBlacklistAddresses() );
        final VaultResponse vaultResponse = this.vaultOperations.write( path, vaultEthereumAccountDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while generating Ethereum account." );
        }
        final VaultEthereumAccountDomain persistedVaultEthereumDomain = convertVaultResponseToDomain( cfAccountId, vaultResponse );

        // Persist the new Ethereum account to our database
        final EthereumAccountDomain ethereumAccountDomain = new EthereumAccountDomain();
        ethereumAccountDomain.setTenantDomain( tenantDomain );
        ethereumAccountDomain.setAccountDomain( accountDomain.get() );
        ethereumAccountDomain.setEthereumAddress( persistedVaultEthereumDomain.getAddress() );

        final EthereumAccountDomain persistedEthereumAccountDomain;
        try
        {
            persistedEthereumAccountDomain = ethereumAccountRepository.save( ethereumAccountDomain );
        }
        catch ( final Exception e )
        {
            // At this point we have a unfunded Ethereum account, but weren't able to save the address to our db.
            // The only thing we can do is record the error so that we can manually recover the account.
            LOGGER.error( "[ROLLBACK ERROR] Ethereum account '" + persistedVaultEthereumDomain.getAddress() + "' created, but db record creation failed.", e );

            throw e;
        }

        // Populate the model for return to the client
        final EthereumAccount createdAccount = new EthereumAccount();
        createdAccount.setCfAccountId( cfAccountId );
        createdAccount.setAddress( persistedVaultEthereumDomain.getAddress() );
        createdAccount.setWhitelistAddresses( persistedVaultEthereumDomain.getWhitelist() );
        createdAccount.setBlacklistAddresses( persistedVaultEthereumDomain.getBlacklist() );
        createdAccount.setCreatedDate( persistedEthereumAccountDomain.getCreatedDate() );

        // Add balance to EthereumAccount object
        final BigInteger accountBalance = ethereumAccountClient.getAccountBalance( createdAccount.getAddress() );
        createdAccount.setBalance( new BigDecimal( accountBalance ) );

        return new ResponseEntity<>( createdAccount, HttpStatus.CREATED );
    }

    private String getEthereumVaultPluginPath( final Long cfAccountId,
                                               final TenantDomain tenantDomain )
    {
        return "/ethereum/" + tenantDomain.getId() + "/accounts/" + cfAccountId;
    }

    @SuppressWarnings( "unchecked" )
    private VaultEthereumAccountDomain convertVaultResponseToDomain( final Long cfAccountId,
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

        final VaultEthereumAccountDomain domain = new VaultEthereumAccountDomain();
        domain.setAddress( address );
        domain.setWhitelist( whitelists );
        domain.setBlacklist( blacklists );
        return domain;
    }
}
