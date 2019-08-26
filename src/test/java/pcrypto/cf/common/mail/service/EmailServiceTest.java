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

package pcrypto.cf.common.mail.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Ignore
@ExtendWith( SpringExtension.class )
@RunWith( SpringRunner.class )
@EnableMBeanExport( registration = RegistrationPolicy.IGNORE_EXISTING )
@ActiveProfiles( profiles = { "dev",
                              "test" } )
@SpringBootTest
class EmailServiceTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger( EmailServiceTest.class );

    private GreenMail smtpServer;

    @Autowired
    private EmailService emailService;


    @BeforeEach
    void setUp()
          throws Exception
    {
        smtpServer = new GreenMail( new ServerSetup( 3025, null, "smtp" ) );
        smtpServer.start();
    }

    @AfterEach
    void tearDown()
          throws Exception
    {
        smtpServer.stop();
    }

    @Test
    void shouldSendMail()
          throws Exception
    {
        //given
        final String recipient = "test-account";
        final String expectedMessage = "Welcome " + recipient;
        //when
        emailService.sendRegistrationEmail( recipient, "http://example.com" );
        //then
        assertReceivedMessageContains( expectedMessage );
    }

    private void assertReceivedMessageContains( final String expected )
          throws IOException, MessagingException
    {
        final MimeMessage[] receivedMessages = smtpServer.getReceivedMessages();
        assertEquals( 1, receivedMessages.length );
        final String content = (String) receivedMessages[0].getContent();
        LOGGER.info( content );
        assertTrue( content.contains( expected ) );
    }
}
