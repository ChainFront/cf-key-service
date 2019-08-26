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

package pcrypto.cf.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pcrypto.cf.common.domain.TenantDomain;
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.security.domain.IdempotencyKeyDomain;
import pcrypto.cf.security.domain.IdempotencyKeyDomainId;
import pcrypto.cf.security.repository.IdempotencyKeyRepository;


@Service
public class IdempotencyService
{
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    public IdempotencyService( final IdempotencyKeyRepository idempotencyKeyRepository )
    {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }


    public boolean processIdempotencyKey( final Authentication authentication,
                                          final String idempotencyKey,
                                          final Long cfAccountId )
    {
        // Obtain the current tenant
        final CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        final TenantDomain tenantDomain = userDetails.getTenantDomain();

        final IdempotencyKeyDomainId idempotencyKeyDomainId = new IdempotencyKeyDomainId();
        idempotencyKeyDomainId.setKey( idempotencyKey );
        idempotencyKeyDomainId.setAccountId( cfAccountId );

        final boolean exists = idempotencyKeyRepository.existsById( idempotencyKeyDomainId );
        if ( exists )
        {
            return false;
        }
        else
        {
            final IdempotencyKeyDomain idempotencyKeyDomain = new IdempotencyKeyDomain();
            idempotencyKeyDomain.setKey( idempotencyKey );
            idempotencyKeyDomain.setAccountId( cfAccountId );
            idempotencyKeyDomain.setTenantDomain( tenantDomain );

            idempotencyKeyRepository.save( idempotencyKeyDomain );
            return true;
        }
    }
}
