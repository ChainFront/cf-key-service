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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import pcrypto.cf.exception.ApiError;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;


/**
 * Authentication entry point to enable us to return JSON error messages during invalid HTTP BASIC authentication flows.
 */
@Component
public class HttpBasicRestAuthenticationEntryPoint
      extends BasicAuthenticationEntryPoint
{
    private static final Logger logger = LoggerFactory.getLogger( HttpBasicRestAuthenticationEntryPoint.class );


    @Override
    public void commence( final HttpServletRequest request,
                          final HttpServletResponse response,
                          final AuthenticationException authEx )
          throws IOException, ServletException
    {
        response.addHeader( "WWW-Authenticate", "Basic realm=\"" + getRealmName() + "\"" );
        response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );

        writeErrorRepresentation( request, response );
    }


    @Override
    public void afterPropertiesSet()
          throws Exception
    {
        setRealmName( "ChainFront Security Realm" );
        super.afterPropertiesSet();
    }


    private void writeErrorRepresentation( final HttpServletRequest request,
                                           final HttpServletResponse response )
          throws IOException
    {
        // Build up the basic error message
        final ApiError apiError = new ApiError();

        apiError.setHttpStatusCode( HttpStatus.UNAUTHORIZED.value() );
        apiError.setHttpMessage( HttpStatus.UNAUTHORIZED.getReasonPhrase() );

        final String responseMessage = getErrorRepresentationInJson( apiError );
        response.setContentType( MediaType.APPLICATION_JSON.toString() );

        // Write the response message to the response
        final PrintWriter writer = response.getWriter();
        writer.println( responseMessage );
    }


    private String getErrorRepresentationInJson( final Object errorRepresentation )
    {
        if ( errorRepresentation != null )
        {
            final ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setSerializationInclusion( JsonInclude.Include.NON_NULL );
            final ObjectWriter ow = objectMapper.writer().withDefaultPrettyPrinter();
            try
            {
                return ow.writeValueAsString( errorRepresentation );
            }
            catch ( final IOException e )
            {
                logger.error( "Exception while transforming errorRepresentation into json.", e );
            }
        }
        return null;
    }
}
