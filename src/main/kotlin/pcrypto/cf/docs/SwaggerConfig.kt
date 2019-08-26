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

package pcrypto.cf.docs


import com.fasterxml.classmate.TypeResolver
import com.google.common.collect.Lists
import com.google.common.collect.Sets.newHashSet
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.RequestMethod
import pcrypto.cf.exception.ApiError
import springfox.documentation.builders.*
import springfox.documentation.schema.ModelRef
import springfox.documentation.service.*
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


/**
 * Configuration for the Swagger API documentation.
 */
@Configuration
@EnableSwagger2
@ComponentScan("pcrypto.cf")
@PropertySource("classpath:i18n/apiDocumentation.properties")
class SwaggerConfig {

    //    @Value("${uaa.clientId}")
    internal var clientId = "clientId"
    //    @Value("${uaa.clientSecret}")
    internal var clientSecret = "clientSecret"
    //    @Value( "${uaa.url}")
    internal var oAuthServerUri = "https://sandbox.chainfront.io"
    //    @Value("${info.app.name}")
    private val serviceName = "svcName"
    //    @Value("${info.app.desc}")
    private val serviceDesc = "svcDesc"

    @Autowired
    @Qualifier("apiDocumentationMessageSource")
    private val messageSource: ResourceBundleMessageSource? = null


    private val globalResponseMessages: List<ResponseMessage>
        get() = Lists.newArrayList(
            ResponseMessageBuilder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message("Invalid or missing credentials")
                .responseModel(ModelRef("ApiError"))
                .build(),
            ResponseMessageBuilder()
                .code(HttpStatus.FORBIDDEN.value())
                .message("User not authorized to perform this operation")
                .responseModel(ModelRef("ApiError"))
                .build(),
            ResponseMessageBuilder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Invalid request")
                .responseModel(ModelRef("ApiError"))
                .build()
        )

    /**
     * Configure the springfox Docket to scan for our controllers.
     *
     * @return a Docket object
     */
    @Bean
    fun buildDocket(): Docket {
        val apiVersion = "1.0.4"

        val docket = Docket(DocumentationType.SWAGGER_2)
            .groupName(apiVersion)
            .securitySchemes(Arrays.asList(oauthSecurityScheme(), apiKeySecurityScheme()))
            .protocols(newHashSet("https"))
            .select()
            .apis(RequestHandlerSelectors.basePackage("pcrypto.cf"))
            .paths(PathSelectors.regex("/api/v1/.*"))
            .build()
            .produces(setOf("application/json"))
            .genericModelSubstitutes(ResponseEntity::class.java)
            .useDefaultResponseMessages(false)
            .additionalModels(TypeResolver().resolve(ApiError::class.java))
            .globalResponseMessage(RequestMethod.GET, globalResponseMessages)
            .globalResponseMessage(RequestMethod.POST, globalResponseMessages)
            .globalResponseMessage(RequestMethod.PUT, globalResponseMessages)
            .globalResponseMessage(RequestMethod.DELETE, globalResponseMessages)
            .directModelSubstitute(LocalDate::class.java, java.sql.Date::class.java)
            .directModelSubstitute(LocalDateTime::class.java, java.util.Date::class.java)
            .directModelSubstitute(LocalTime::class.java, java.lang.String::class.java)

        docket.ignoredParameterTypes(Authentication::class.java, ModelMap::class.java)

        docket.apiInfo(apiInfo("docs/apiGuideIntroduction.md", apiVersion))

        docket.tags(
            Tag(SwaggerTags.ACCOUNTS, this.getMessage("tag.accounts.description")),
            Tag(SwaggerTags.STELLAR_ACCOUNTS, this.getMessage("tag.stellar.accounts.description")),
            Tag(SwaggerTags.STELLAR_TRANSACTIONS, this.getMessage("tag.stellar.transactions.description")),
            Tag(SwaggerTags.RIPPLE_ACCOUNTS, this.getMessage("tag.ripple.accounts.description")),
            Tag(SwaggerTags.RIPPLE_TRANSACTIONS, this.getMessage("tag.ripple.transactions.description")),
            Tag(SwaggerTags.ETH_ACCOUNTS, this.getMessage("tag.ethereum.accounts.description")),
            Tag(SwaggerTags.ETH_CONTRACTS, this.getMessage("tag.ethereum.contracts.description")),
            Tag(SwaggerTags.ETH_TRANSACTIONS, this.getMessage("tag.ethereum.transactions.description")),
            Tag(SwaggerTags.BITCOIN_ACCOUNTS, this.getMessage("tag.bitcoin.accounts.description")),
            Tag(SwaggerTags.BITCOIN_TRANSACTIONS, this.getMessage("tag.bitcoin.transactions.description"))
        )

        return docket
    }


    private fun oauthSecurityScheme(): SecurityScheme {
        return OAuthBuilder()
            .name("OAuth2")
            .scopes(oauthScopes())
            .grantTypes(oauthGrantTypes())
            .build()
    }

    private fun apiKeySecurityScheme(): SecurityScheme {
        return BasicAuth("ApiKey")
    }

    private fun oauthGrantTypes(): List<GrantType> {
        val grantTypes = ArrayList<GrantType>()
        val tokenRequestEndpoint = TokenRequestEndpoint("$oAuthServerUri/oauth/authorize", clientId, clientSecret)
        val tokenEndpoint = TokenEndpoint("$oAuthServerUri/oauth/token", "token")
        grantTypes.add(AuthorizationCodeGrant(tokenRequestEndpoint, tokenEndpoint))
        return grantTypes
    }

    private fun oauthScopes(): List<AuthorizationScope> {
        val list = ArrayList<AuthorizationScope>()
        list.add(AuthorizationScope("read:accounts", "Grants read access to accounts"))
        list.add(AuthorizationScope("write:accounts", "Grants write access to accounts"))
        list.add(AuthorizationScope("read:stellar_accounts", "Grants access to read Stellar accounts"))
        list.add(AuthorizationScope("write:stellar_accounts", "Grants access to create Stellar accounts"))
        list.add(AuthorizationScope("write:stellar_txs", "Grants access to create Stellar transactions and payments"))
        list.add(AuthorizationScope("read:ripple_accounts", "Grants access to read Ripple accounts"))
        list.add(AuthorizationScope("write:ripple_accounts", "Grants access to create Ripple accounts"))
        list.add(AuthorizationScope("write:ripple_txs", "Grants access to create Ripple transactions and payments"))
        list.add(AuthorizationScope("read:ethereum_accounts", "Grants access to read Ethereum accounts"))
        list.add(AuthorizationScope("write:ethereum_accounts", "Grants access to create Ethereum accounts"))
        list.add(AuthorizationScope("write:ethereum_txs", "Grants access to create Ethereum transactions"))
        list.add(AuthorizationScope("read:ethereum_contracts", "Grants access to read Ethereum contracts"))
        list.add(AuthorizationScope("write:ethereum_contracts", "Grants access to create Ethereum contracts"))
        list.add(AuthorizationScope("execute:ethereum_contracts", "Grants access to execute Ethereum contracts"))
        list.add(AuthorizationScope("read:bitcoin_accounts", "Grants access to read Bitcoin accounts"))
        list.add(AuthorizationScope("write:bitcoin_accounts", "Grants access to create Bitcoin accounts"))
        list.add(AuthorizationScope("write:bitcoin_txs", "Grants access to create Bitcoin transactions"))

        return list
    }


    private fun apiInfo(
        mdResourceName: String,
        version: String
    ): ApiInfo? {
        val classLoader = javaClass.classLoader
        val resourceAsStream = classLoader.getResourceAsStream(mdResourceName)
        val apiGuideOverviewString: String
        try {
            apiGuideOverviewString = IOUtils.toString(resourceAsStream, "UTF-8")
        } catch (e: IOException) {
            logger.error(String.format("Error during load of API guide introduction in %s", mdResourceName), e)
            return null
        }

        return ApiInfoBuilder()
            .title("ChainFront Cloud Service REST API")
            .description(apiGuideOverviewString)
            .version(version)
            .build()
    }


    private fun getMessage(
        messageCode: String,
        vararg args: String
    ): String {
        return this.messageSource!!.getMessage(messageCode, args, Locale.getDefault())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SwaggerConfig::class.java)
    }
}
