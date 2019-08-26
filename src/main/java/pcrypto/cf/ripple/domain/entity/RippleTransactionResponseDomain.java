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

package pcrypto.cf.ripple.domain.entity;

import lombok.Data;
import pcrypto.cf.account.domain.entity.AccountDomain;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.OffsetDateTime;


@Data
@Entity
@Table( name = "ripple_transaction_response" )
public class RippleTransactionResponseDomain
{
    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE,
                     generator = "ripple_transaction_response_id_gen" )
    @SequenceGenerator( name = "ripple_transaction_response_id_gen",
                        sequenceName = "ripple_transaction_response_seq",
                        allocationSize = 1 )
    private Long id;

    @ManyToOne
    @JoinColumn( name = "account_id" )
    private AccountDomain accountDomain;

    @OneToOne( fetch = FetchType.LAZY )
    @JoinColumn( name = "ripple_transaction_request_uuid" )
    private RippleTransactionRequestDomain rippleTransactionRequest;

    private Boolean success;
    private String transactionResult;
    private String transactionHash;
    private Long ledger;
    private String signedTransaction;
    private OffsetDateTime createdDate;
}
