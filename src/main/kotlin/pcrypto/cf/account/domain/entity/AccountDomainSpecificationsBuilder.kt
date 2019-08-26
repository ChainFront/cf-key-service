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

package pcrypto.cf.account.domain.entity


import org.springframework.data.jpa.domain.Specification
import pcrypto.cf.common.domain.SearchCriteria
import java.util.*


class AccountDomainSpecificationsBuilder {
    private val params: MutableList<SearchCriteria>

    init {
        params = ArrayList()
    }

    fun with(
        key: String,
        operation: String,
        value: Any
    ): AccountDomainSpecificationsBuilder {
        params.add(SearchCriteria(key, operation, value))
        return this
    }

    fun build(): Specification<AccountDomain>? {
        if (params.isEmpty()) {
            return null
        }

        val specs = ArrayList<Specification<AccountDomain>>()
        for (param in params) {
            specs.add(AccountDomainSpecification(param))
        }

        var result = specs[0]
        for (i in 1 until specs.size) {
            result = Specification.where(result).and(specs[i])
        }
        return result
    }
}
