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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import pcrypto.cf.common.api.controller.ApiController;
import pcrypto.cf.docs.SwaggerTags;
import pcrypto.cf.ethereum.api.model.EthereumContract;
import pcrypto.cf.ethereum.api.model.EthereumContractRequest;
import pcrypto.cf.ethereum.api.model.EthereumContracts;
import pcrypto.cf.ethereum.api.model.EthereumTransaction;
import pcrypto.cf.exception.ApiError;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;


@Api( tags = { SwaggerTags.ETH_CONTRACTS } )
@ApiController
public class EthereumContractsApiController
{

    private static final Logger log = LoggerFactory.getLogger( EthereumContractsApiController.class );

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;


    @org.springframework.beans.factory.annotation.Autowired
    public EthereumContractsApiController( ObjectMapper objectMapper,
                                           HttpServletRequest request )
    {
        this.objectMapper = objectMapper;
        this.request = request;
    }


    @ApiOperation( value = "List contracts",
                   nickname = "ethereumContractsGet",
                   notes = "Returns a paginated list of all contracts associated with this account.",
                   response = EthereumContracts.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "read:ethereum_contracts",
                                                                    description = "Ability to read contracts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_CONTRACTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "A list of contract accounts",
                        response = EthereumContracts.class ) } )
    @RequestMapping( value = "/accounts/{cfAccountId}/ethereum/contracts",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )
    public ResponseEntity<EthereumContracts> getContracts( final Authentication authentication,
                                                           @ApiParam( value = "Account identifier",
                                                                      required = true ) @PathVariable final Long cfAccountId )
    {
        String accept = request.getHeader( "Accept" );
        if ( accept != null && accept.contains( "application/json" ) )
        {
            try
            {
                return new ResponseEntity<EthereumContracts>( objectMapper.readValue( "\"\"", EthereumContracts.class ), HttpStatus.NOT_IMPLEMENTED );
            }
            catch ( IOException e )
            {
                log.error( "Couldn't serialize response for content type application/json", e );
                return new ResponseEntity<EthereumContracts>( HttpStatus.INTERNAL_SERVER_ERROR );
            }
        }

        return new ResponseEntity<EthereumContracts>( HttpStatus.NOT_IMPLEMENTED );
    }


    @ApiOperation( value = "Get contract",
                   nickname = "getEthereumContract",
                   notes = "Returns contract details for the given contract name",
                   response = EthereumContract.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "read:ethereum_contracts",
                                                                    description = "Ability to read contracts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_CONTRACTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Contract details",
                        response = EthereumContract.class ) } )
    @RequestMapping( value = "/accounts/{cfAccountId}/ethereum/contracts/{contractName}",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.GET )

    public ResponseEntity<EthereumContract> getContract( final Authentication authentication,
                                                         @ApiParam( value = "Account identifier",
                                                                    required = true ) @PathVariable final Long cfAccountId,
                                                         @ApiParam( value = "Name of contract",
                                                                    required = true ) @PathVariable( "contractName" ) String contractName )
    {
        String accept = request.getHeader( "Accept" );
        if ( accept != null && accept.contains( "application/json" ) )
        {
            try
            {
                return new ResponseEntity<EthereumContract>( objectMapper.readValue( "{  \"address\" : \"address\",  \"name\" : \"name\",  \"last_modified_date\" : \"2000-01-23T04:56:07.000+00:00\",  \"created_date\" : \"2000-01-23T04:56:07.000+00:00\",  \"transactionHash\" : \"transactionHash\",  \"status\" : \"ACTIVE\"}", EthereumContract.class ), HttpStatus.NOT_IMPLEMENTED );
            }
            catch ( IOException e )
            {
                log.error( "Couldn't serialize response for content type application/json", e );
                return new ResponseEntity<EthereumContract>( HttpStatus.INTERNAL_SERVER_ERROR );
            }
        }

        return new ResponseEntity<EthereumContract>( HttpStatus.NOT_IMPLEMENTED );
    }


    @ApiOperation( value = "Invoke a contract",
                   nickname = "invokeEthereumContract",
                   notes = "Invoke a contract",
                   response = EthereumTransaction.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "read:ethereum_contracts",
                                                                    description = "Ability to read contracts" ),
                                               @AuthorizationScope( scope = "execute:ethereum_contracts",
                                                                    description = "Ability to execute contracts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_CONTRACTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 200,
                        message = "Transaction details",
                        response = EthereumTransaction.class ),
          @ApiResponse( code = 409,
                        message = "Transaction already submitted (duplicate idempotency key)",
                        response = ApiError.class ) } )
    @RequestMapping( value = "/accounts/{cfAccountId}/ethereum/contracts/{contractName}/invoke",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<EthereumTransaction> invokeContract( final Authentication authentication,
                                                               @ApiParam( value = "Client generated unique key to guarantee this transaction is only applied once." )
                                                               @RequestHeader( value = "X-Idempotency-Key",
                                                                               required = true ) final String idempotencyKey,
                                                               @ApiParam( value = "When using APP_TOTP approval, pass the TOTP code in this header." )
                                                               @RequestHeader( value = "X-TOTP-Code",
                                                                               required = false,
                                                                               defaultValue = "" ) final String totpCode,
                                                               @ApiParam( value = "Account identifier",
                                                                          required = true ) @PathVariable final Long cfAccountId,
                                                               @ApiParam( value = "Name of contract",
                                                                          required = true ) @PathVariable( "contractName" ) final String contractName,
                                                               @ApiParam( value = "ABI payload indicating the function and data to call on the contract." ) @Valid @RequestBody EthereumContractRequest contractRequest )
    {
        String accept = request.getHeader( "Accept" );
        if ( accept != null && accept.contains( "application/json" ) )
        {
            try
            {
                return new ResponseEntity<EthereumTransaction>( objectMapper.readValue( "{  \"gasLimit\" : 6.02745618307040320615897144307382404804229736328125,  \"amount\" : 100.0,  \"sourceAddress\" : \"sourceAddress\",  \"destinationAddress\" : \"destinationAddress\",  \"signedTransaction\" : \"signedTransaction\",  \"nonce\" : 1.46581298050294517310021547018550336360931396484375,  \"transactionHash\" : \"transactionHash\",  \"gasPrice\" : 0.80082819046101150206595775671303272247314453125}", EthereumTransaction.class ), HttpStatus.NOT_IMPLEMENTED );
            }
            catch ( IOException e )
            {
                log.error( "Couldn't serialize response for content type application/json", e );
                return new ResponseEntity<EthereumTransaction>( HttpStatus.INTERNAL_SERVER_ERROR );
            }
        }

        return new ResponseEntity<EthereumTransaction>( HttpStatus.NOT_IMPLEMENTED );
    }


    @ApiOperation( value = "Create contract",
                   nickname = "createEthereumContract",
                   notes = "Create a new contract",
                   response = EthereumContract.class,
                   authorizations = {
                         @Authorization( value = "OAuth2",
                                         scopes = {
                                               @AuthorizationScope( scope = "write:ethereum_contracts",
                                                                    description = "Ability to create contracts" )
                                         } ),
                         @Authorization( value = "ApiKey" )
                   },
                   tags = { SwaggerTags.ETH_CONTRACTS } )
    @ApiResponses( value = {
          @ApiResponse( code = 201,
                        message = "Contract created successfully",
                        response = EthereumContract.class ) } )
    @ResponseStatus( HttpStatus.CREATED )
    @RequestMapping( value = "/accounts/{cfAccountId}/ethereum/contracts",
                     produces = { "application/json" },
                     consumes = { "application/json" },
                     method = RequestMethod.POST )
    public ResponseEntity<EthereumContract> createContract( final Authentication authentication,
                                                            @ApiParam( value = "Account identifier",
                                                                       required = true ) @PathVariable final Long cfAccountId,
                                                            @ApiParam( value = "The contract to create." ) @Valid @RequestBody EthereumContract ethereumContract )
    {
        String accept = request.getHeader( "Accept" );
        if ( accept != null && accept.contains( "application/json" ) )
        {
            try
            {
                return new ResponseEntity<EthereumContract>( objectMapper.readValue( "{  \"address\" : \"address\",  \"name\" : \"name\",  \"last_modified_date\" : \"2000-01-23T04:56:07.000+00:00\",  \"created_date\" : \"2000-01-23T04:56:07.000+00:00\",  \"transactionHash\" : \"transactionHash\",  \"status\" : \"ACTIVE\"}", EthereumContract.class ), HttpStatus.NOT_IMPLEMENTED );
            }
            catch ( IOException e )
            {
                log.error( "Couldn't serialize response for content type application/json", e );
                return new ResponseEntity<EthereumContract>( HttpStatus.INTERNAL_SERVER_ERROR );
            }
        }

        return new ResponseEntity<EthereumContract>( HttpStatus.NOT_IMPLEMENTED );
    }
}
