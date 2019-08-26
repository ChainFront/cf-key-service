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

package pcrypto.cf.config.rest

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.springframework.cloud.commons.httpclient.OkHttpClientFactory

import java.util.concurrent.TimeUnit


class OkHttpClientFactoryImpl : OkHttpClientFactory {

    override fun createBuilder(disableSslValidation: Boolean): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        val okHttpConnectionPool = ConnectionPool(50, 30, TimeUnit.SECONDS)
        builder.connectionPool(okHttpConnectionPool)
        builder.connectTimeout(20, TimeUnit.SECONDS)
        builder.retryOnConnectionFailure(false)
        return builder
    }

}
