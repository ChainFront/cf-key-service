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

package pcrypto.cf.config.i18n;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;


class MessageSourceConfigTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger( MessageSourceConfigTest.class );


    @Test
    void apiDocumentationMessageSource()
    {
        final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames( "i18n.otherStuff", "i18n.testMessages" );
        messageSource.setDefaultEncoding( "UTF-8" );
        messageSource.setUseCodeAsDefaultMessage( true );

        String message = messageSource.getMessage( "tag.ethereum.accounts.description", null, Locale.getDefault() );
        LOGGER.info( "tag.ethereum.accounts.description = " + message );
    }
}