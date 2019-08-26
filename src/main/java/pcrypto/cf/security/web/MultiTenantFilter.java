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

package pcrypto.cf.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.GenericFilterBean;
import pcrypto.cf.exception.ApiError;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;


/**
 * Request interceptor which reads the mandatory X-CUSTOMER_ID header value and sets it in a threadlocal
 * for downstream usage.
 */
public class MultiTenantFilter
      extends GenericFilterBean
{
    private static final Logger LOGGER = LoggerFactory.getLogger( MultiTenantFilter.class );

    private static final String TENANT_HEADER_ID = "X-CUSTOMER-ID";


    @Override
    public void doFilter( final ServletRequest request,
                          final ServletResponse response,
                          final FilterChain chain )
          throws IOException, ServletException
    {
        TenantContext.clear();

        if ( !( request instanceof HttpServletRequest ) )
        {
            chain.doFilter( request, response );
            return;
        }

        final HttpServletRequestWrapper req =
              new HttpServletRequestWrapper( (HttpServletRequest) request );

        // Process the tenant header for our /api urls
        if ( req.getServletPath().startsWith( "/api/" ) )
        {
            processTenantHeader( (HttpServletResponse) response, req );
        }

        chain.doFilter( request, response );
    }

    private void processTenantHeader( final HttpServletResponse response,
                                      final HttpServletRequestWrapper req )
          throws IOException
    {
        final String tenantId = req.getHeader( TENANT_HEADER_ID );

        if ( StringUtils.isEmpty( tenantId ) )
        {
            final ApiError apiError = ApiError.Builder.
                  Companion.apiError()
                           .withHttpMessage( HttpStatus.BAD_REQUEST.getReasonPhrase() )
                           .withHttpStatusCode( HttpStatus.BAD_REQUEST.value() )
                           .withDescription( "X-CUSTOMER-ID header is required." )
                           .withSupportReferenceId( UUID.randomUUID().toString() )
                           .build();

            final HttpServletResponse resp = response;

            final ObjectMapper mapper = new ObjectMapper();
            resp.setStatus( HttpServletResponse.SC_BAD_REQUEST );
            resp.setContentType( MediaType.APPLICATION_JSON_VALUE );
            mapper.writeValue( resp.getWriter(), apiError );
            resp.getWriter().flush();
        }
        else
        {
            TenantContext.setCurrentTenant( tenantId.toLowerCase() );
            LOGGER.debug( "Set TenantContext: {}", tenantId );
        }
    }
}
