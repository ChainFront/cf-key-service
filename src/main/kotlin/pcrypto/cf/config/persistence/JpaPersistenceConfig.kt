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

package pcrypto.cf.config.persistence

import org.hibernate.MultiTenancyStrategy
import org.hibernate.cfg.Environment
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaVendorAdapter
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.OffsetDateTime
import java.util.*
import javax.sql.DataSource


@Configuration
@EnableTransactionManagement
@EnableJpaRepositories("pcrypto.cf")
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaPersistenceConfig {

    @Autowired
    private val jpaProperties: JpaProperties? = null

    @Autowired
    private val jpaVendorAdapter: JpaVendorAdapter? = null

    @Bean
    fun auditorProvider(): AuditorAware<String> {
        return AuditorAwareImpl()
    }

    /**
     * Spring audit doesn't pick up the normal JPA Jsr310 converters, so we have to manually do it here.
     *
     * @return a DateTimeProvider
     */
    @Bean(name = ["auditingDateTimeProvider"])
    fun dateTimeProvider(): DateTimeProvider {
        return DateTimeProvider { Optional.of(OffsetDateTime.now()) }
    }


    @Bean
    fun jpaVendorAdapter(): JpaVendorAdapter {
        return HibernateJpaVendorAdapter()
    }


    /**
     * Configure the entity manager to handle our schema-per-tenant connection model.
     *
     * @param dataSource
     * @param multiTenantConnectionProviderImpl
     * @param currentTenantIdentifierResolverImpl
     * @return
     */
    @Bean
    fun entityManagerFactory(
        dataSource: DataSource,
        multiTenantConnectionProviderImpl: MultiTenantConnectionProvider,
        currentTenantIdentifierResolverImpl: CurrentTenantIdentifierResolver
    ): LocalContainerEntityManagerFactoryBean {

        // Damn, I hate JPA. Supposed to be simple, starts that way, then just gets in your way.
        val hibernateSettings = HibernateSettings()

        val properties = HashMap<String, Any>()
        properties.putAll(jpaProperties!!.getHibernateProperties(hibernateSettings))
        properties[Environment.MULTI_TENANT] = MultiTenancyStrategy.SCHEMA
        properties[Environment.MULTI_TENANT_CONNECTION_PROVIDER] = multiTenantConnectionProviderImpl
        properties[Environment.MULTI_TENANT_IDENTIFIER_RESOLVER] = currentTenantIdentifierResolverImpl


        val em = LocalContainerEntityManagerFactoryBean()
        em.dataSource = dataSource
        em.setPackagesToScan("pcrypto.cf")
        em.jpaVendorAdapter = jpaVendorAdapter
        em.jpaPropertyMap = properties

        return em
    }
}
