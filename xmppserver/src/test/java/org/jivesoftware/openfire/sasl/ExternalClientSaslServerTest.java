/*
 * Copyright (C) 2023-2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.sasl.SaslException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link ExternalClientSaslServer}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ExternalClientSaslServerTest
{
    @Mock
    private LocalClientSession session;

    @Mock
    private Connection connection;

    private MockedStatic<SASLAuthentication> saslAuthentication;

    @Before
    public void setupStaticMock() {
        saslAuthentication = Mockito.mockStatic(SASLAuthentication.class);
    }

    @After
    public void teardownStaticMock() {
        if (saslAuthentication != null) {
            saslAuthentication.close();
        }
    }

    /**
     * Verify that when <em>no</em> initial response is given, a challenge (for authzid) is returned.
     *
     * The absence of an initial response is represented by a null value being provided as the response to be evaluated,
     * while the provided session <em>does not</em> have this session attribute: {@link SASLAuthentication#SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY}.
     */
    @Test
    public void testNoInitialResponse() throws Exception
    {
        // Setup test fixture.
        when(session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY)).thenReturn(null);

        final ExternalClientSaslServer server = new ExternalClientSaslServer(session);
        final byte[] input = new byte[]{};

        // Execute system under test.
        final byte[] response;
        try {
             response = server.evaluateResponse(input);
        } catch (SaslException e) {
            fail("System under test progressed beyond point that was expected to return a response.");
            return;
        }

        // Verify results.
        assertNotNull(response);
        assertArrayEquals(new byte[0], response);
    }
}
