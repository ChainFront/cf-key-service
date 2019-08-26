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

package pcrypto.cf.security.repository;

import org.springframework.jdbc.core.RowMapper;
import pcrypto.cf.common.domain.ApiUserDomain;

import java.sql.ResultSet;
import java.sql.SQLException;


public class UserRowMapper
      implements RowMapper<ApiUserDomain>
{
    @Override
    public ApiUserDomain mapRow( final ResultSet rs,
                                 final int rowNum )
          throws SQLException
    {
        final ApiUserDomain apiUserDomain = new ApiUserDomain();
        //        apiUserDomain.setId( rs.getLong( "id" ) );
        //        apiUserDomain.setUserName( rs.getString( "user_name" ) );
        //        apiUserDomain.setPassword( rs.getString( "password" ) );
        //        apiUserDomain.setFirstName( rs.getString( "first_name" ) );
        //        apiUserDomain.setLastName( rs.getString( "last_name" ) );
        //        apiUserDomain.setLocked( rs.getBoolean( "locked" ) );
        //        apiUserDomain.setExpired( rs.getBoolean( "expired" ) );
        //        apiUserDomain.setCreatedById( rs.getLong( "created_by_id" ) );
        //        apiUserDomain.setCreatedDate( DateTimeUtils.toLocalDateTime( rs.getTimestamp( "created_date" ) ) );
        //        apiUserDomain.setUpdatedById( rs.getLong( "updated_by_id" ) );
        //        apiUserDomain.setUpdatedDate( DateTimeUtils.toLocalDateTime( rs.getTimestamp( "updated_date" ) ) );
        //
        //        final TenantDomain tenantDomain = new TenantDomain();
        //        tenantDomain.setId( rs.getLong( "tenant_id" ) );
        //        tenantDomain.setCode( rs.getString( "tenant_code" ) );
        //        tenantDomain.setName( rs.getString( "tenant_name" ) );
        //
        //        apiUserDomain.setTenantDomain( tenantDomain );

        return apiUserDomain;
    }
}
