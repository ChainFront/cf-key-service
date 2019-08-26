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

package pcrypto.cf.bitcoin.domain.entity


import pcrypto.cf.account.domain.entity.AccountDomain
import pcrypto.cf.common.domain.AbstractAuditableDomain
import java.time.LocalDateTime
import javax.persistence.*
import javax.validation.constraints.Null


@Entity
@Table(name = "bitcoin_account")
class BitcoinAccountDomain : AbstractAuditableDomain() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bitcoin_account_id_gen")
    @SequenceGenerator(name = "bitcoin_account_id_gen", sequenceName = "bitcoin_account_seq", allocationSize = 1)
    var id: Long = 0

    @ManyToOne
    @JoinColumn(name = "account_id")
    lateinit var accountDomain: AccountDomain

    var bitcoinAddress: String = ""

    @Null
    var deletedDate: LocalDateTime? = null

}
