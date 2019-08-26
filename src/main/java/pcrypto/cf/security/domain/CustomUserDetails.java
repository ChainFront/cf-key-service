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

package pcrypto.cf.security.domain;

import org.springframework.security.core.GrantedAuthority;
import pcrypto.cf.common.domain.TenantDomain;

import java.util.Collection;


public class CustomUserDetails
      extends org.springframework.security.core.userdetails.User
{

    private static final long serialVersionUID = 1L;

    /**
     * The extra field in the login form is for the tenant name
     */
    private TenantDomain tenantDomain;

    /**
     * Constructor based on the spring security User class but with an extra
     * argument <code>tenant</code> to store the tenant name submitted by the
     * end user.
     *
     * @param username
     * @param password
     * @param authorities
     * @param tenantDomain
     */
    public CustomUserDetails( final String username,
                              final String password,
                              final Collection<? extends GrantedAuthority> authorities,
                              final TenantDomain tenantDomain )
    {
        super( username, password, authorities );
        this.tenantDomain = tenantDomain;
    }

    public TenantDomain getTenantDomain()
    {
        return tenantDomain;
    }

    public void setTenantDomain( final TenantDomain tenantDomain )
    {
        this.tenantDomain = tenantDomain;
    }
}