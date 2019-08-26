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

package pcrypto.cf.account.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import pcrypto.cf.account.value.AccountStatusEnum
import pcrypto.cf.account.value.TxApprovalMethodEnum
import pcrypto.cf.common.validator.annotation.ValidEmail
import pcrypto.cf.common.validator.annotation.ValidPhoneNumber
import java.time.OffsetDateTime
import javax.validation.Valid
import javax.validation.constraints.NotNull


/**
 * Account
 */
@ApiModel
data class Account(

    @ApiModelProperty(
        value = "The ChainFront id of the account.",
        position = 10,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var cfAccountId: Long,

    @NotNull
    @ApiModelProperty(required = true, value = "The username of the account.", position = 20)
    var userName: String,

    @NotNull
    @ValidEmail
    @ApiModelProperty(value = "The email address of the account.", required = true, position = 30)
    var email: String,

    @NotNull
    @ValidPhoneNumber
    @ApiModelProperty(value = "The phone number of the account.", required = true, position = 40)
    var phone: String,

    @Valid
    @NotNull
    @ApiModelProperty(
        value = "The transaction approval method for this account. For every transaction where this account is a signer, the approval " +
                "method indicates how the user is to approve the transaction. \n" +
                "[AUTHY_PUSH]: sends a push approval request to the registered Authy id. " +
                "[CHAINFRONT_TOTP]: using ChainFront UIs, the user will have to scan a QR Code with Google Authenticator, and will " +
                "be prompted to enter their TOTP code. " +
                "[APP_TOTP]: Your app will need to collect the TOTP code from the user and pass it in the X-TOTP-Code header. " +
                "[IMPLICIT]: No approval will be required for transactions with this account. Use this flow when your application " +
                "handles all security related to account identity.", required = true, position = 50
    )
    var txApprovalMethod: TxApprovalMethodEnum,

    @ApiModelProperty(
        value = "Status of this account. Active, Pending (awaiting user verification), Locked, or Deleted.",
        position = 60,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var status: AccountStatusEnum,

    @ApiModelProperty(
        value = "If applicable, the verification steps needed for this account to become active.",
        position = 100,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    var verification: VerificationInfo?,

    @ApiModelProperty(
        value = "Date the account was created.",
        position = 1000,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    @JsonProperty("createdDate")
    var createdDate: OffsetDateTime,

    @ApiModelProperty(
        value = "Date the account was last updated.",
        position = 1010,
        accessMode = ApiModelProperty.AccessMode.READ_ONLY,
        readOnly = true
    )
    @JsonProperty("lastModifiedDate")
    var lastModifiedDate: OffsetDateTime
)

