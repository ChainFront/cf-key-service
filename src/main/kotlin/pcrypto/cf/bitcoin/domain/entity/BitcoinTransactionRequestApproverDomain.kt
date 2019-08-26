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
import javax.persistence.*


@Entity
@Table(name = "bitcoin_transaction_request_approver")
class BitcoinTransactionRequestApproverDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bitcoin_transaction_request_approver_id_gen")
    @SequenceGenerator(
        name = "bitcoin_transaction_request_approver_id_gen",
        sequenceName = "bitcoin_transaction_request_approver_seq",
        allocationSize = 1
    )
    var id: Long = 0

    @ManyToOne
    @JoinColumn(name = "account_id")
    lateinit var accountDomain: AccountDomain

    @Column(name = "authy_approval_request_uuid")
    var authyApprovalRequestUUID: String? = null

    var status: Int = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bitcoin_transaction_request_uuid")
    var bitcoinTransactionRequest: BitcoinTransactionRequestDomain? = null

}
