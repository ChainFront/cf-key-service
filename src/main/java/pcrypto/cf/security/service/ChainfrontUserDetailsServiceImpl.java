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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pcrypto.cf.common.domain.ApiUserDomain;
import pcrypto.cf.security.domain.CustomUserDetails;
import pcrypto.cf.security.repository.ApiUserRepository;
import pcrypto.cf.security.web.TenantContext;

import java.util.Collections;


/**
 * Custom Spring Security implementation of UserDetailsService to load chainfront users.
 */
@Service
public class ChainfrontUserDetailsServiceImpl
      implements UserDetailsService
{
    private static final Logger logger = LoggerFactory.getLogger( ChainfrontUserDetailsServiceImpl.class );

    @Autowired
    private ApiUserRepository apiUserRepository;


    @Transactional
    @Override
    public UserDetails loadUserByUsername( final String username )
          throws UsernameNotFoundException
    {
        final ApiUserDomain apiUserDomain;
        try
        {
            apiUserDomain = apiUserRepository.findByUserName( username );
        }
        catch ( final DataAccessException e )
        {
            logger.error( e.getMessage(), e );
            throw new UsernameNotFoundException( "User name not found" );
        }
        catch ( final Exception e )
        {
            logger.error( e.getMessage(), e );
            throw new DataRetrievalFailureException( "An error occurred while loading user by user name." );
        }

        if ( apiUserDomain == null )
        {
            logger.error( "[SECURITY] Invalid login attempt for username {}", username );
            throw new UsernameNotFoundException( "User name not found" );
        }

        // Prevent logins to locked accounts or tenants
        if ( apiUserDomain.getLocked() || apiUserDomain.getTenantDomain().getLocked() )
        {
            logger.error( "[SECURITY] Invalid login attempt to locked account for username {}", username );
            throw new UsernameNotFoundException( "User account is locked" );
        }

        // prevent logins to deleted organizations.
        //        final UserOrganization userOrganization = userOrganizationService.findUserOrganization( userInfo.getRootOrgId() );
        //        if ( null != userOrganization && userOrganization.isDeleted() )
        //        {
        //            logger.error( "User {} attempted login to deleted organization {}", userInfo.getId(), userOrganization.getId() );
        //            throw new UsernameNotFoundException( "User name not found" );
        //        }
        //
        //        // prevent logins with deleted user accounts
        //        if ( null != profile && profile.isDeleted() )
        //        {
        //            logger.error( "User {} attempted login to deleted account", userInfo.getId() );
        //            throw new UsernameNotFoundException( "User name not found" );
        //        }

        // Extra security check to ensure our user account is using the same tenant passed in on the request header
        final String currentTenant = TenantContext.getCurrentTenant();
        if ( !currentTenant.equalsIgnoreCase( apiUserDomain.getTenantDomain().getCode() ) )
        {
            logger.error( "[SECURITY] Invalid tenant code for user. Tenant: {}, Username: {}", currentTenant, username );
            throw new UsernameNotFoundException( "User name not found" );
        }

        // Log the successful authentication
        logger.info( "[SECURITY] Successful login for username {}", username );

        return new CustomUserDetails( apiUserDomain.getUserName(),
                                      apiUserDomain.getPassword(),
                                      Collections.emptyList(),
                                      apiUserDomain.getTenantDomain() );
    }
}
