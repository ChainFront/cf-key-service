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

package pcrypto.cf.ethereum.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import pcrypto.cf.ethereum.domain.entity.EthereumTransactionResponseDomain;

import java.util.Optional;
import java.util.UUID;


public interface EthereumTransactionResponseRepository
      extends CrudRepository<EthereumTransactionResponseDomain, Long>
{
    @Query( "SELECT s FROM EthereumTransactionResponseDomain s WHERE s.ethereumTransactionRequest.uuid = ?1" )
    Optional<EthereumTransactionResponseDomain> findByTransactionRequestUUID( UUID transactionRequestUUID );
}
