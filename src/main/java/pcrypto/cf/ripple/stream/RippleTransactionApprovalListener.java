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

package pcrypto.cf.ripple.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import pcrypto.cf.ripple.service.RippleTransactionService;
import pcrypto.cf.security.web.TenantContext;


@Slf4j
@Component
public class RippleTransactionApprovalListener
{
    private RippleTransactionService rippleTransactionService;

    @Autowired
    public RippleTransactionApprovalListener( final RippleTransactionService rippleTransactionService )
    {
        this.rippleTransactionService = rippleTransactionService;
    }


    @StreamListener( RippleTransactionApprovalStream.APPROVAL_INBOUND )
    public void handleApprovalEvent( @Payload final RippleTransactionApprovalEvent event )
    {
        log.info( "Received event: {}", event );

        // Since we're outside of the scope of a multitenant call, we set the tenant id here for downstream db calls
        final String tenantId = event.getTenantId();
        TenantContext.setCurrentTenant( tenantId.toLowerCase() );

        rippleTransactionService.processApprovalEvent( event );
    }
}
