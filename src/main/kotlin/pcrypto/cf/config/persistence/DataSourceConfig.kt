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

import org.apache.tomcat.jdbc.pool.DataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Primary
import org.springframework.data.jdbc.repository.config.JdbcConfiguration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.SQLException


/**
 * Auto configuration for the jdbc connection pools and related templates.
 *
 * The connection pool is configured by properties in 'application.yml', using the relevant configuration property
 * prefix. In production environments, standard spring configuration rules apply, so there will be an application-prod.yml
 * file created by the deployment process which defines the db connection properties.
 *
 * Due to a limitation in spring, we have to force register each datasource mbean, which is why there are MBeans defined here.
 */
@Configuration
class DataSourceConfig : JdbcConfiguration() {


    @Bean(name = ["cfDataSource"])
    @Primary
    @ConfigurationProperties(prefix = "cf.datasource")
    fun cfDataSource(): DataSource {
        return DataSourceBuilder.create().type(DataSource::class.java).build() as DataSource
    }


    @Bean(name = ["cfDataSourceMBean"])
    @DependsOn("mbeanExporter")
    fun cfDataSourceMBean(@Qualifier("cfDataSource") dataSource: DataSource?): Any? {
        if (dataSource != null) {
            try {
                return dataSource.createPool().jmxPool
            } catch (ex: SQLException) {
                log.warn("Cannot expose chainfront dataSource to JMX (could not connect)")
            }

        }
        return null
    }


    @Bean(name = ["jdbcTemplate"])
    fun jdbcTemplate(@Qualifier("cfDataSource") dsPostgres: DataSource): JdbcTemplate {
        return JdbcTemplate(dsPostgres)
    }


    @Bean(name = ["namedParameterJdbcTemplate"])
    fun namedParameterJdbcTemplate(@Qualifier("cfDataSource") dsPostgres: DataSource): NamedParameterJdbcTemplate {
        return NamedParameterJdbcTemplate(dsPostgres)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DataSourceConfig::class.java)
    }
}
