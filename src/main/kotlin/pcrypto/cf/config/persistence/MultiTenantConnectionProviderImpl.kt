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

import org.hibernate.HibernateException
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import pcrypto.cf.security.web.TenantContext
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource


/**
 * Based on the tenant set in the context, return a db connection set to use the tenant-specific schema.
 */
@Component
class MultiTenantConnectionProviderImpl : MultiTenantConnectionProvider {

    @Autowired
    private val dataSource: DataSource? = null

    @Throws(SQLException::class)
    override fun getAnyConnection(): Connection {
        return dataSource!!.connection
    }

    @Throws(SQLException::class)
    override fun releaseAnyConnection(connection: Connection) {
        connection.close()
    }

    @Throws(SQLException::class)
    override fun getConnection(tenantIdentifie: String): Connection {
        val tenantIdentifier = TenantContext.getCurrentTenant()
        val connection = anyConnection
        try {
            if (tenantIdentifier != null) {
                log.debug("SET SCHEMA '{}'", tenantIdentifier)
                val sql = "SET SCHEMA '$tenantIdentifier'"
                connection.createStatement().execute(sql)
            } else {
                throw HibernateException("Missing tenant identifier")
            }
        } catch (e: SQLException) {
            throw HibernateException("Problem setting schema to " + tenantIdentifier!!, e)
        }

        return connection
    }

    @Throws(SQLException::class)
    override fun releaseConnection(
        tenantIdentifier: String,
        connection: Connection
    ) {
        try {
            log.debug("SET SCHEMA '{}'", MASTER_SCHEMA_NAME)
            val sql = "SET SCHEMA '$MASTER_SCHEMA_NAME'"
            connection.createStatement().execute(sql)
        } catch (e: SQLException) {
            throw HibernateException("Problem setting schema to $tenantIdentifier", e)
        }

        connection.close()
    }

    override fun isUnwrappableAs(unwrapType: Class<*>): Boolean {
        return false
    }

    override fun <T> unwrap(unwrapType: Class<T>): T? {
        return null
    }

    override fun supportsAggressiveRelease(): Boolean {
        return true
    }

    companion object {
        private val MASTER_SCHEMA_NAME = "master"

        private val log = LoggerFactory.getLogger(MultiTenantConnectionProviderImpl::class.java)
    }
}