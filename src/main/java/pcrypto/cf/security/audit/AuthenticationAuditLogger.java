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

package pcrypto.cf.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;


/**
 * Listener for all security audit evens.
 */
@Component
public class AuthenticationAuditLogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger( "AUDIT" );


    @EventListener
    public void onApplicationEvent( final AuditApplicationEvent event )
    {
        final AuditEvent auditEvent = event.getAuditEvent();

        final String type = auditEvent.getType();
        final Instant timestamp = auditEvent.getTimestamp();
        final String principal = auditEvent.getPrincipal();
        final String message = (String) auditEvent.getData().get( "message" );
        final String remoteAddress = getClientIP();

        LOGGER.info( "type:'{}', timestamp:'{}', principal:'{}', remoteAddress:'{}', message:'{}'", type, timestamp, principal, remoteAddress, message );
    }

    /**
     * Handle the AWS elastic load balancer proxy by grabbing the original source IP from a header set by the ELB
     */
    private String getClientIP()
    {
        String clientIP = "";

        final RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
        if ( RequestContextHolder.getRequestAttributes() != null )
        {
            final HttpServletRequest request = ( (ServletRequestAttributes) attribs ).getRequest();
            final String xfHeader = request.getHeader( "X-Forwarded-For" );
            if ( xfHeader == null )
            {
                clientIP = request.getRemoteAddr();
            }
            else
            {
                clientIP = xfHeader.split( "," )[0];
            }
        }
        return clientIP;
    }
}
