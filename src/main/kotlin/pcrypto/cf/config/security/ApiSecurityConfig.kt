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

package pcrypto.cf.config.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import pcrypto.cf.security.service.ChainfrontUserDetailsServiceImpl
import pcrypto.cf.security.web.HttpBasicRestAuthenticationEntryPoint
import pcrypto.cf.security.web.MultiTenantFilter


@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class ApiSecurityConfig : WebSecurityConfigurerAdapter() {
    @Autowired
    private val userDetailsService: ChainfrontUserDetailsServiceImpl? = null

    @Autowired
    private val httpBasicRestAuthenticationEntryPoint: HttpBasicRestAuthenticationEntryPoint? = null


    @Throws(Exception::class)
    public override fun configure(auth: AuthenticationManagerBuilder?) {
        auth!!.userDetailsService<ChainfrontUserDetailsServiceImpl>(userDetailsService)
            .passwordEncoder(passwordEncoder())
    }

    @Throws(Exception::class)
    override fun configure(web: WebSecurity?) {
        web!!.ignoring().antMatchers(
            "/v2/api-docs",
            "/docs.html",
            "/redoc.html",
            "/favicon.ico",
            "/image/**",
            "/webjars/**",
            "/webhooks/**"
        )
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }


    @Bean
    fun multiTenantFilter(): FilterRegistrationBean<MultiTenantFilter> {
        val registrationBean = FilterRegistrationBean<MultiTenantFilter>()

        registrationBean.filter = MultiTenantFilter()
        registrationBean.addUrlPatterns("/api/*")
        registrationBean.order = 0

        return registrationBean
    }

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        // Http basic auth
        http.sessionManagement()
            .sessionCreationPolicy(STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/actuator/**").permitAll()
            .anyRequest().authenticated()
            .and()
            .exceptionHandling().authenticationEntryPoint(httpBasicRestAuthenticationEntryPoint)
            .and()
            .httpBasic()
            .and()
            .csrf().disable()
    }
}
