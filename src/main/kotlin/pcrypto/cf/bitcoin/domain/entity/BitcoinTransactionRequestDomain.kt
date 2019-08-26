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
import java.math.BigDecimal
import java.util.*
import javax.persistence.*


@Entity
@Table(name = "bitcoin_transaction_request")
class BitcoinTransactionRequestDomain : AbstractAuditableDomain() {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    lateinit var uuid: UUID

    @ManyToOne
    @JoinColumn(name = "account_id")
    lateinit var accountDomain: AccountDomain

    @ManyToOne
    @JoinColumn(name = "dest_account_id")
    lateinit var destAccountDomain: AccountDomain

    var bitcoinPaymentRequest: String = ""

    var amount: BigDecimal? = null

    var assetCode: String? = null

    var memo: String? = null

    @OneToMany(mappedBy = "bitcoinTransactionRequest", cascade = [CascadeType.ALL])
    var approverDomains: MutableList<BitcoinTransactionRequestApproverDomain> = mutableListOf()

}

