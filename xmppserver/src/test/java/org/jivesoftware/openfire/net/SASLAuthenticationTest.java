/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.net;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.security.sasl.SaslServer;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SASLAuthentication}.
 */
public class SASLAuthenticationTest
{
    private static final String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";

    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setup()
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        SASLAuthentication.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL"));
    }

    /**
     * Verifies that an unencrypted client session cannot use EXTERNAL when that mechanism is not available for the session.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3273">OF-3273: SASLAuthentication accepts mechanisms not advertised for the current connection/session</a>
     */
    @Test
    public void shouldRejectExternalForUnencryptedClientSessionAsInvalidMechanism()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("EXTERNAL"));

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL negotiation to fail when EXTERNAL is requested on an unencrypted client session.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<invalid-mechanism"), "Expected server to return an invalid-mechanism failure for a non-advertised mechanism.");
    }

    /**
     * Verifies that an inbound server session rejects PLAIN when only session-eligible mechanisms are allowed.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3273">OF-3273: SASLAuthentication accepts mechanisms not advertised for the current connection/session</a>
     */
    @Test
    public void shouldRejectPlainForUnencryptedIncomingServerSessionAsInvalidMechanism()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"));

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL negotiation to fail when PLAIN is requested for an inbound server session that does not advertise it.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<invalid-mechanism"), "Expected server to return an invalid-mechanism failure for an inbound server mechanism that is not available.");
    }

    /**
     * Verifies that an unencrypted client session accepts PLAIN as an eligible mechanism and does not reject it as invalid.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3273">OF-3273: SASLAuthentication accepts mechanisms not advertised for the current connection/session</a>
     */
    @Test
    public void shouldAcceptPlainForUnencryptedClientSessionAsEligibleMechanism()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"));

        // Verify result.
        assertEquals(SASLAuthentication.Status.needResponse, status, "Expected PLAIN to be accepted and continue negotiation by issuing a challenge.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertFalse(response.getValue().contains("<invalid-mechanism"), "Did not expect invalid-mechanism, because PLAIN is advertised and should be accepted for processing.");
        assertTrue(response.getValue().contains("<challenge"), "Expected a challenge stanza as proof that PLAIN negotiation continued after mechanism validation.");
    }

    /**
     * Verifies that an incoming server session authenticated with EXTERNAL is marked as SASL_EXTERNAL.
     */
    @Test
    public void shouldMarkIncomingServerSessionAsSaslExternalForExternalMechanism() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        final SaslServer saslServer = mock(SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenReturn(new byte[0]);
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getAuthorizationID()).thenReturn("remote.example.org");
        when(saslServer.getMechanismName()).thenReturn("EXTERNAL");
        session.setSessionData("SaslServer", saslServer);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(""));

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "Expected authentication to complete for a completed EXTERNAL SASL server.");
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, session.getAuthenticationMethod(), "Expected incoming server sessions using EXTERNAL to be marked as SASL_EXTERNAL.");
    }

    /**
     * Verifies that an incoming server session authenticated with a non-EXTERNAL mechanism is marked as OTHER.
     */
    @Test
    public void shouldMarkIncomingServerSessionAsOtherForNonExternalMechanism() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        final SaslServer saslServer = mock(SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenReturn(new byte[0]);
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getAuthorizationID()).thenReturn("remote.example.org");
        when(saslServer.getMechanismName()).thenReturn("PLAIN");
        session.setSessionData("SaslServer", saslServer);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(""));

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "Expected authentication to complete for a completed non-EXTERNAL SASL server.");
        assertEquals(ServerSession.AuthenticationMethod.OTHER, session.getAuthenticationMethod(), "Expected incoming server sessions using non-EXTERNAL SASL to be marked as OTHER.");
    }

    private static Element authElement(final String mechanism)
    {
        final Element auth = DocumentHelper.createElement(new QName("auth", Namespace.get("", SASL_NAMESPACE)));
        auth.addAttribute("mechanism", mechanism);
        return auth;
    }

    private static Element responseElement(final String value)
    {
        final Element response = DocumentHelper.createElement(new QName("response", Namespace.get("", SASL_NAMESPACE)));
        response.setText(value);
        return response;
    }
}




