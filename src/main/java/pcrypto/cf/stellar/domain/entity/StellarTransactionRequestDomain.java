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

package pcrypto.cf.stellar.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import pcrypto.cf.account.domain.entity.AccountDomain;
import pcrypto.cf.common.domain.AbstractAuditableDomain;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


@Data
@EqualsAndHashCode( callSuper = true )
@Entity
@Table( name = "stellar_transaction_request" )
public class StellarTransactionRequestDomain
      extends AbstractAuditableDomain
{

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    private UUID uuid;

    @ManyToOne
    @JoinColumn( name = "account_id" )
    private AccountDomain accountDomain;

    @ManyToOne
    @JoinColumn( name = "dest_account_id" )
    private AccountDomain destAccountDomain;

    @ManyToOne
    @JoinColumn( name = "payment_channel_account_id" )
    private AccountDomain paymentChannelAccountDomain;

    private String stellarPaymentRequest;

    private BigDecimal amount;
    private String assetCode;
    private String assetIssuer;

    private String memo;

    @OneToMany( mappedBy = "stellarTransactionRequest",
                cascade = CascadeType.ALL )
    private List<StellarTransactionRequestApproverDomain> approverDomains;
}

