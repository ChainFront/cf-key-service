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

import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root


class AccountDomainSpecification(private val criteria: SearchCriteria) : Specification<AccountDomain> {

    override fun toPredicate(
        root: Root<AccountDomain>,
        query: CriteriaQuery<*>,
        builder: CriteriaBuilder
    ): Predicate? {

        when {
            criteria.operation.equals(">", ignoreCase = true) ->
                return builder.greaterThanOrEqualTo(
                    root.get(criteria.key), criteria.value.toString()
                )
            criteria.operation.equals("<", ignoreCase = true) ->
                return builder.lessThanOrEqualTo(
                    root.get(criteria.key), criteria.value.toString()
                )
            criteria.operation.equals(":", ignoreCase = true) ->
                return if (root.get<Any>(criteria.key).javaType == String::class.java) {
                    builder.like(
                        root.get(criteria.key), "%" + criteria.value + "%"
                    )
                } else {
                    builder.equal(root.get<Any>(criteria.key), criteria.value)
                }
            else -> return null
        }
    }
}
