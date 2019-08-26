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

package pcrypto.cf.stellar.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import pcrypto.cf.stellar.domain.entity.StellarTransactionResponseDomain;

import java.util.Optional;
import java.util.UUID;


public interface StellarTransactionResponseRepository
      extends CrudRepository<StellarTransactionResponseDomain, Long>
{
    @Query( "SELECT s FROM StellarTransactionResponseDomain s WHERE s.stellarTransactionRequest.uuid = ?1" )
    Optional<StellarTransactionResponseDomain> findByTransactionRequestUUID( UUID transactionRequestUUID );
}