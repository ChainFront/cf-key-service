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

package pcrypto.cf.ripple.api.controller;

import com.ripple.core.coretypes.Amount;
import com.ripple.core.coretypes.Currency;
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
import pcrypto.cf.common.mail.service.EmailService;
import pcrypto.cf.docs.SwaggerTags;
import pcrypto.cf.exception.BadRequestException;
import pcrypto.cf.exception.NotFoundException;
import pcrypto.cf.mfa.service.totp.TotpService;
import pcrypto.cf.ripple.api.model.RippleAccount;
import pcrypto.cf.ripple.api.model.RippleAccountSet;
import pcrypto.cf.ripple.api.model.RippleAccountTrustline;
import pcrypto.cf.ripple.api.model.RippleTransaction;
import pcrypto.cf.ripple.client.RippleAccountClient;
import pcrypto.cf.ripple.client.RippleTransactionClient;
import pcrypto.cf.ripple.client.dto.RippleAccountInfoDto;
import pcrypto.cf.ripple.client.dto.RippleAccountLineDto;
import pcrypto.cf.ripple.client.dto.RippleSubmitResponseDto;
import pcrypto.cf.ripple.domain.entity.RippleAccountDomain;
import pcrypto.cf.ripple.domain.repository.RippleAccountRepository;
import pcrypto.cf.ripple.vault.dto.VaultRippleAccountDomain;
import pcrypto.cf.ripple.vault.dto.VaultRippleAccountSetDomain;
import pcrypto.cf.ripple.vault.dto.VaultRippleAccountTrustlineDomain;
import pcrypto.cf.security.domain.CustomUserDetails;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * API for the /ripple/accounts endpoint.
 */
@Api( tags = { SwaggerTags.RIPPLE_ACCOUNTS } )
@ApiController
public class RippleAccountsApiController
{
    private static final Logger LOGGER = LoggerFactory.getLogger( RippleAccountsApiController.class );

    private final VaultOperations vaultOperations;

    private final AccountRepository accountRepository;

    private final RippleAccountRepository rippleAccountRepository;

    private final RippleAccountClient rippleAccountClient;
    private final RippleTransactionClient rippleTransactionClient;

    private final TotpService totpService;

    private final EmailService emailService;


    @Autowired
    public RippleAccountsApiController( final VaultOperations vaultOperations,
                                        final AccountRepository accountRepository,
                                        final RippleAccountRepository rippleAccountRepository,
                                        final RippleAccountClient rippleAccountClient,
                                        final RippleTransactionClient rippleTransactionClient,
                                        final TotpService totpService,
                                        final EmailService emailService )
    {
        this.vaultOperations = vaultOperations;
        this.accountRepository = accountRepository;
        this.rippleAccountRepository = rippleAccountRepository;
        this.rippleAccountClient = rippleAccountClient;
        this.rippleTransactionClient = rippleTransactionClient;
        this.totpService = totpService;
        this.emailService = emailService;
    }


    @ApiOperation( value = "Get Ripple account",
                   nickname = "getRippleAccount",
                   notes = "Returns the Ripple account details for the given account and Ripple address",
                   response = RippleAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "read:ripple_accounts",
                                                                    description = "Ability to read Ripple accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Ripple account details",
                        response = RippleAccount.class ) } )
    @RequestMapping( value = "/accounts/{cfAccountId}/ripple",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<RippleAccount> getRippleAccount( final Authentication authentication,
                                                           @NotNull @PathVariable final Long cfAccountId )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the ripple account db record
        final Optional<RippleAccountDomain> rippleAccountDomain = rippleAccountRepository.findByCfAccountId( cfAccountId );

        if ( !rippleAccountDomain.isPresent() || !rippleAccountDomain.get().getTenantDomain().equals( tenantDomain ) )
        {
            throw new NotFoundException( "Ripple account for cfAccountId " + cfAccountId + " not found." );
        }

        // Get the vault path
        final String vaultPath = getRippleVaultPluginPath( cfAccountId, tenantDomain );

        // Read the account from Vault
        final VaultResponse vaultResponse = this.vaultOperations.read( vaultPath );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while reading the Ripple account." );
        }
        final VaultRippleAccountDomain vaultRippleAccountDomain = convertVaultResponseToDomain( cfAccountId, vaultResponse );

        // Create the BitcoinAccount object
        final RippleAccount rippleAccount = new RippleAccount();
        rippleAccount.setCfAccountId( cfAccountId );
        rippleAccount.setAddress( vaultRippleAccountDomain.getAddress() );
        rippleAccount.setWhitelistAddresses( vaultRippleAccountDomain.getWhitelist() );
        rippleAccount.setBlacklistAddresses( vaultRippleAccountDomain.getBlacklist() );
        rippleAccount.setCreatedDate( rippleAccountDomain.get().getCreatedDate() );
        rippleAccount.setLastModifiedDate( rippleAccountDomain.get().getLastModifiedDate() );

        // Add balances to BitcoinAccount object
        final List<RippleAccount.Balance> balances = getBalances( rippleAccount );
        rippleAccount.setBalances( balances );

        return new ResponseEntity<>( rippleAccount, HttpStatus.OK );
    }


    @ApiOperation( value = "Create an account on the Ripple network",
                   nickname = "createRippleAccount",
                   notes = "Create a new Ripple account. The seed, private and public key are created from a high entropy " +
                           "random number; all performed within the secure environment. The private key is created within the secure " +
                           "environment and is never viewable nor transmitted between servers.",
                   response = RippleAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ripple_accounts",
                                                                    description = "Ability to create Ripple accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Account created successfully",
                        response = RippleAccount.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/ripple",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    //    @PreAuthorize( "hasAuthority('SUPER_ADMIN') OR hasAuthority('ADMIN')")
    public ResponseEntity<RippleAccount> createRippleAccount( final Authentication authentication,
                                                              @ApiParam( value = "Account identifier",
                                                                         required = true ) @PathVariable final Long cfAccountId,
                                                              @Valid @RequestBody final RippleAccount rippleAccount )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the chainfront account
        final Optional<AccountDomain> accountDomain = accountRepository.findById( cfAccountId );
        accountDomain.orElseThrow( () -> new NotFoundException( "Account " + cfAccountId + " not found." ) );

        // Check if we already have a Ripple account for this chainfront account
        final Optional<RippleAccountDomain> existingRippleAccountDomain = rippleAccountRepository.findByCfAccountId( cfAccountId );
        if ( existingRippleAccountDomain.isPresent() )
        {
            throw new BadRequestException( "A Ripple account already exists for this ChainFront account. Address: '" + existingRippleAccountDomain.get().getRippleAddress() + "'" );
        }

        // Get the path where this account will be stored
        final String path = getRippleVaultPluginPath( cfAccountId, tenantDomain );

        // Write ripple account to vault (plugin will generate private key behind the vault barrier)
        final VaultRippleAccountDomain vaultRippleAccountDomain = new VaultRippleAccountDomain();
        vaultRippleAccountDomain.setWhitelist( rippleAccount.getWhitelistAddresses() );
        vaultRippleAccountDomain.setBlacklist( rippleAccount.getBlacklistAddresses() );
        final VaultResponse vaultResponse = this.vaultOperations.write( path, vaultRippleAccountDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while generating Ripple account." );
        }
        final VaultRippleAccountDomain persistedVaultRippleDomain = convertVaultResponseToDomain( cfAccountId, vaultResponse );

        // Persist the new Ripple account to our database
        final RippleAccountDomain rippleAccountDomain = new RippleAccountDomain();
        rippleAccountDomain.setTenantDomain( tenantDomain );
        rippleAccountDomain.setAccountDomain( accountDomain.get() );
        rippleAccountDomain.setRippleAddress( persistedVaultRippleDomain.getAddress() );

        final RippleAccountDomain persistedRippleAccountDomain;
        try
        {
            persistedRippleAccountDomain = rippleAccountRepository.save( rippleAccountDomain );
        }
        catch ( final Exception e )
        {
            // At this point we have a funded Ripple account, but weren't able to save the address to our db.
            // The only thing we can do is record the error so that we can manually recover the account.
            LOGGER.error( "[ROLLBACK ERROR] Ripple account '" + persistedVaultRippleDomain.getAddress() + "' created, but db record creation failed.", e );

            throw e;
        }

        // Populate the model for return to the client
        final RippleAccount createdAccount = new RippleAccount();
        createdAccount.setCfAccountId( cfAccountId );
        createdAccount.setAddress( persistedVaultRippleDomain.getAddress() );
        createdAccount.setWhitelistAddresses( persistedVaultRippleDomain.getWhitelist() );
        createdAccount.setBlacklistAddresses( persistedVaultRippleDomain.getBlacklist() );
        createdAccount.setCreatedDate( persistedRippleAccountDomain.getCreatedDate() );

        // Add balances to BitcoinAccount object
        final List<RippleAccount.Balance> balances = getBalances( createdAccount );
        createdAccount.setBalances( balances );

        return new ResponseEntity<>( createdAccount, HttpStatus.CREATED );
    }


    @ApiOperation( value = "Set account details (flags, domain name) on a Ripple account.",
                   nickname = "updateRippleAccountSet",
                   notes = "Set account details on a Ripple account",
                   response = RippleAccount.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ripple_accounts",
                                                                    description = "Ability to create Ripple accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Account details set successfully",
                        response = RippleTransaction.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/ripple/{rippleAddress}/accountset",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<RippleTransaction> updateRippleAccountSet( final Authentication authentication,
                                                                     @ApiParam( value = "Account identifier",
                                                                                required = true ) @PathVariable final Long cfAccountId,
                                                                     @NotNull @PathVariable final String rippleAddress,
                                                                     @Valid @RequestBody final RippleAccountSet rippleAccountSet )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the chainfront account
        final Optional<AccountDomain> accountDomain = accountRepository.findById( cfAccountId );
        accountDomain.orElseThrow( () -> new NotFoundException( "Account " + cfAccountId + " not found." ) );

        // Check if we already have a Ripple account for this chainfront account
        final Optional<RippleAccountDomain> existingRippleAccountDomain = rippleAccountRepository.findByCfAccountId( cfAccountId );
        if ( !existingRippleAccountDomain.isPresent() )
        {
            throw new BadRequestException( "A Ripple account does not exist for this ChainFront account." );
        }

        // Get the path where this account will be stored
        final String path = getRippleVaultPluginPath( cfAccountId, tenantDomain ) + "/accountset";

        final VaultRippleAccountSetDomain vaultRippleAccountSetDomain = new VaultRippleAccountSetDomain();
        vaultRippleAccountSetDomain.setSetFlag( null == rippleAccountSet.getFlag() ? "" : rippleAccountSet.getFlag().toString() );
        vaultRippleAccountSetDomain.setClearFlag( null == rippleAccountSet.getClearFlag() ? "" : rippleAccountSet.getClearFlag().toString() );
        vaultRippleAccountSetDomain.setDomain( rippleAccountSet.getDomain() );

        final VaultResponse vaultResponse = this.vaultOperations.write( path, vaultRippleAccountSetDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while setting details on the Ripple account." );
        }
        final Map<String, Object> data = vaultResponse.getData();
        if ( null == data )
        {
            throw new VaultException( "Vault response when signing transaction contained a null data map." );
        }
        final String signedTx = (String) data.get( "signed_transaction" );

        // Submit the signed tx to Ripple
        final RippleSubmitResponseDto txResponse = rippleTransactionClient.submitTransaction( signedTx );

        final RippleTransaction rippleTransaction = new RippleTransaction();
        rippleTransaction.setSourceAddress( existingRippleAccountDomain.get().getRippleAddress() );
        rippleTransaction.setFee( txResponse.getFee() );
        rippleTransaction.setAccountSequence( new BigDecimal( txResponse.getSequence() ) );
        rippleTransaction.setTransactionHash( txResponse.getHash() );
        rippleTransaction.setSignedTransaction( signedTx );

        return new ResponseEntity<>( rippleTransaction, HttpStatus.CREATED );
    }


    @ApiOperation( value = "Add a trustline to an account on the Ripple network",
                   nickname = "createRippleTrustlineForAccount",
                   notes = "Create a new trustline for a Ripple account",
                   response = RippleTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ripple_accounts",
                                                                    description = "Ability to create Ripple accounts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.RIPPLE_ACCOUNTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Trustline created successfully",
                        response = RippleTransaction.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/ripple/{rippleAddress}/trustline",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<RippleTransaction> createRippleTrustline( final Authentication authentication,
                                                                    @ApiParam( value = "Account identifier",
                                                                               required = true ) @PathVariable final Long cfAccountId,
                                                                    @NotNull @PathVariable final String rippleAddress,
                                                                    @Valid @RequestBody final RippleAccountTrustline rippleAccountTrustline )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        // Obtain the chainfront account
        final Optional<AccountDomain> accountDomain = accountRepository.findById( cfAccountId );
        accountDomain.orElseThrow( () -> new NotFoundException( "Account " + cfAccountId + " not found." ) );

        // Check if we already have a Ripple account for this chainfront account
        final Optional<RippleAccountDomain> existingRippleAccountDomain = rippleAccountRepository.findByCfAccountId( cfAccountId );
        if ( !existingRippleAccountDomain.isPresent() )
        {
            throw new BadRequestException( "A Ripple account does not exist for this ChainFront account." );
        }

        // Get the path for the plugin API
        final String path = getRippleVaultPluginPath( cfAccountId, tenantDomain ) + "/trustline";

        final VaultRippleAccountTrustlineDomain vaultRippleAccountTrustlineDomain = new VaultRippleAccountTrustlineDomain();
        vaultRippleAccountTrustlineDomain.setCurrencyCode( rippleAccountTrustline.getCurrencyCode() );
        vaultRippleAccountTrustlineDomain.setIssuer( rippleAccountTrustline.getCurrencyIssuer() );
        vaultRippleAccountTrustlineDomain.setLimit( rippleAccountTrustline.getLimit().toPlainString() );

        final VaultResponse vaultResponse = this.vaultOperations.write( path, vaultRippleAccountTrustlineDomain );
        if ( null == vaultResponse )
        {
            throw new VaultException( "An error occurred while setting details on the Ripple account." );
        }
        final Map<String, Object> data = vaultResponse.getData();
        if ( null == data )
        {
            throw new VaultException( "Vault response when signing transaction contained a null data map." );
        }
        final String signedTx = (String) data.get( "signed_transaction" );

        // Submit the signed tx to Ripple
        final RippleSubmitResponseDto txResponse = rippleTransactionClient.submitTransaction( signedTx );

        final RippleTransaction rippleTransaction = new RippleTransaction();
        rippleTransaction.setSourceAddress( existingRippleAccountDomain.get().getRippleAddress() );
        rippleTransaction.setFee( txResponse.getFee() );
        rippleTransaction.setAccountSequence( new BigDecimal( txResponse.getSequence() ) );
        rippleTransaction.setTransactionHash( txResponse.getHash() );
        rippleTransaction.setSignedTransaction( signedTx );

        return new ResponseEntity<>( rippleTransaction, HttpStatus.CREATED );
    }


    private List<RippleAccount.Balance> getBalances( final RippleAccount rippleAccount )
    {
        final List<RippleAccount.Balance> balances = new ArrayList<>();

        // Get the XRP balance
        final RippleAccountInfoDto rippleAccountInfoDto = rippleAccountClient.getAccountInfo( rippleAccount.getAddress() );
        final Amount xrpAmount = rippleAccountInfoDto.getBalance();
        final RippleAccount.Balance xrpBalance = new RippleAccount.Balance( "native",
                                                                            "XRP",
                                                                            null,
                                                                            null,
                                                                            xrpAmount.valueText() );
        balances.add( xrpBalance );

        // Get balances for other issued currencies
        final List<RippleAccountLineDto> rippleAccountLineDtos = rippleAccountClient.getAccountLines( rippleAccount.getAddress() );
        for ( final RippleAccountLineDto rippleAccountLineDto : rippleAccountLineDtos )
        {
            final Amount amount = rippleAccountLineDto.getBalance();
            final Currency currency = rippleAccountLineDto.getCurrency();
            final Amount limit = rippleAccountLineDto.getLimit();

            final RippleAccount.Balance currencyBalance = new RippleAccount.Balance( currency.toString(),
                                                                                     currency.humanCode(),
                                                                                     amount.issuerString(),
                                                                                     limit.valueText(),
                                                                                     amount.valueText() );
            balances.add( currencyBalance );
        }

        return balances;
    }


    private String getRippleVaultPluginPath( final Long cfAccountId,
                                             final TenantDomain tenantDomain )
    {
        return "/ripple/" + tenantDomain.getId() + "/accounts/" + cfAccountId;
    }


    @SuppressWarnings( "unchecked" )
    private VaultRippleAccountDomain convertVaultResponseToDomain( final Long cfAccountId,
                                                                   final VaultResponse vaultResponse )
    {
        final Map<String, Object> data = vaultResponse.getData();
        if ( null == data )
        {
            throw new VaultException( "No vault data found for account " + cfAccountId );
        }

        final String address = (String) data.get( "accountId" );
        final List<String> whitelists = (List<String>) data.getOrDefault( "whitelist", Collections.emptyList() );
        final List<String> blacklists = (List<String>) data.getOrDefault( "blacklist", Collections.emptyList() );

        final VaultRippleAccountDomain domain = new VaultRippleAccountDomain();
        domain.setAddress( address );
        domain.setWhitelist( whitelists );
        domain.setBlacklist( blacklists );
        return domain;
    }
}
