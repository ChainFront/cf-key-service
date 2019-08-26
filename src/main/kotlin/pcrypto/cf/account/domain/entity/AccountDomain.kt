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

import pcrypto.cf.common.domain.AbstractAuditableDomain
import java.time.LocalDateTime
import javax.persistence.*
import javax.validation.constraints.Null


@Entity
@Table(name = "account")
class AccountDomain : AbstractAuditableDomain() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_id_gen")
    @SequenceGenerator(name = "account_id_gen", sequenceName = "account_seq", allocationSize = 1)
    var id: Long = 0

    var userName: String = ""
    var email: String = ""
    var phone: String = ""

    var firstName: String? = null
    var lastName: String? = null

    var txApprovalMethod: Int = 0

    var status: Int = 0
    var isLocked: Boolean = false
    var isExpired: Boolean = false

    @Null
    var deletedDate: LocalDateTime? = null

    @Transient
    var qrCode: String? = null

    @Transient
    var otpauthUrl: String? = null

}
