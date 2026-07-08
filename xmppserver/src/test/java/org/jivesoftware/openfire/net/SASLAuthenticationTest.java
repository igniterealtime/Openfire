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
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.security.sasl.SaslServer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.jivesoftware.openfire.net.SASLAuthentication.SASL_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("EXTERNAL"), false);

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
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

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
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

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
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(""), false);

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
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(""), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "Expected authentication to complete for a completed non-EXTERNAL SASL server.");
        assertEquals(ServerSession.AuthenticationMethod.OTHER, session.getAuthenticationMethod(), "Expected incoming server sessions using non-EXTERNAL SASL to be marked as OTHER.");
    }

    /**
     * Verifies that getAvailableMechanismsForSession returns mechanisms for a ClientSession.
     */
    @Test
    public void shouldReturnAvailableMechanismsForClientSession()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> mechanisms = SASLAuthentication.getAvailableMechanismsForSession(session);

        // Verify result.
        assertTrue(mechanisms.contains("PLAIN"), "Expected PLAIN to be available for an unencrypted client session.");
        assertFalse(mechanisms.contains("EXTERNAL"), "Expected EXTERNAL not to be available for an unencrypted client session without a trusted cert.");
    }

    /**
     * Verifies that getAvailableMechanismsForSession returns mechanisms for an incoming server session.
     */
    @Test
    public void shouldReturnAvailableMechanismsForIncomingServerSession()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> mechanisms = SASLAuthentication.getAvailableMechanismsForSession(session);

        // Verify result.
        assertTrue(mechanisms.isEmpty(), "Expected no mechanisms to be available for an unencrypted server session without a trusted cert.");
    }

    /**
     * Verifies that getAvailableMechanismsForSession does not advertise EXTERNAL for an incoming server session
     * when EXTERNAL is disabled in the global SASL mechanisms configuration.
     *
     * Regression test for: Stream features advertise EXTERNAL even when disabled in sasl.mechs
     */
    @Test
    public void shouldNotAdvertiseExternalForIncomingServerSessionWhenDisabledGlobally()
    {
        // Save the original enabled mechanisms to restore after the test.
        final Set<String> originalMechanisms = new HashSet<>(SASLAuthentication.getEnabledMechanisms());

        try {
            // Setup test fixture: Disable EXTERNAL in the global mechanisms configuration
            SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN")); // Only PLAIN, no EXTERNAL

            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(true);

            final StreamID streamID = new BasicStreamIDFactory().createStreamID();
            final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

            // Execute system under test.
            final Set<String> mechanisms = SASLAuthentication.getAvailableMechanismsForSession(session);

            // Verify result.
            assertFalse(mechanisms.contains("EXTERNAL"), "Expected EXTERNAL not to be advertised when disabled in global mechanisms configuration, even for encrypted sessions.");
        } finally {
            // Restore state to prevent affecting other unit tests.
            SASLAuthentication.setEnabledMechanisms(new ArrayList<>(originalMechanisms));
        }
    }

    /**
     * Verifies that getAvailableMechanismsForSession handles unknown session types gracefully.
     */
    @Test
    public void shouldReturnEmptySetForUnknownSessionType()
    {
        // Setup test fixture.
        final LocalSession unknownSession = mock(LocalSession.class);

        // Execute system under test.
        final Set<String> mechanisms = SASLAuthentication.getAvailableMechanismsForSession(unknownSession);

        // Verify result.
        assertTrue(mechanisms.isEmpty(), "Expected empty set for an unknown session type.");
    }

    /**
     * Verifies that authenticationSuccessful generates an anonymous auth token for a client with no username.
     */
    @Test
    public void shouldGenerateAnonymousAuthTokenForClientWhenUsernameIsNull()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, null, "ANONYMOUS", new byte[0], false);

        // Verify result.
        final AuthToken authToken = session.getAuthToken();
        assertTrue(authToken.isAnonymous(), "Expected an anonymous auth token when username is null.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<success"), "Expected success element to be sent.");
    }

    /**
     * Verifies that authenticationSuccessful generates a user auth token for a client with a username.
     */
    @Test
    public void shouldGenerateUserAuthTokenForClientWhenUsernameIsProvided()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        final String username = "testuser";

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, username, "PLAIN", new byte[0], false);

        // Verify result.
        final AuthToken authToken = session.getAuthToken();
        assertFalse(authToken.isAnonymous(), "Expected a user auth token when username is provided.");
        assertEquals(username, authToken.getUsername(), "Expected auth token to contain the provided username.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<success"), "Expected success element to be sent.");
    }

    /**
     * Verifies that authenticationSuccessful marks the domain as validated for an inbound server session.
     */
    @Test
    public void shouldMarkDomainAsValidatedForIncomingServerSession()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");
        final String remoteDomain = "remote.example.org";

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, remoteDomain, "EXTERNAL", new byte[0], false);

        // Verify result.
        assertTrue(session.isValidDomain(remoteDomain), "Expected remote domain to be marked as validated.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<success"), "Expected success element to be sent.");
    }

    /**
     * Verifies that decodeData returns an empty byte array when doc is null and emptyNull is true.
     */
    @Test
    public void decodeData_nullDoc_emptyNullTrue_returnsEmptyArray() throws SaslFailureException
    {
        // Execute system under test.
        final byte[] result = SASLAuthentication.decodeData(null, true);

        // Verify result.
        assertArrayEquals(new byte[0], result, "Expected empty byte array when doc is null and emptyNull is true.");
    }

    /**
     * Verifies that decodeData returns null when doc is null and emptyNull is false.
     */
    @Test
    public void decodeData_nullDoc_emptyNullFalse_returnsNull() throws SaslFailureException
    {
        // Execute system under test.
        final byte[] result = SASLAuthentication.decodeData(null, false);

        // Verify result.
        assertNull(result, "Expected null when doc is null and emptyNull is false.");
    }

    /**
     * Verifies that decodeData returns null when the element text is empty and emptyNull is true.
     */
    @Test
    public void decodeData_emptyText_emptyNullTrue_returnsNull() throws SaslFailureException
    {
        // Setup test fixture.
        final Element doc = DocumentHelper.createElement("response");
        doc.setText("");

        // Execute system under test.
        final byte[] result = SASLAuthentication.decodeData(doc, true);

        // Verify result.
        assertNull(result, "Expected null when element text is empty and emptyNull is true.");
    }

    /**
     * Verifies that decodeData returns an empty byte array when the element text is empty and emptyNull is false.
     */
    @Test
    public void decodeData_emptyText_emptyNullFalse_returnsEmptyArray() throws SaslFailureException
    {
        // Setup test fixture.
        final Element doc = DocumentHelper.createElement("response");
        doc.setText("");

        // Execute system under test.
        final byte[] result = SASLAuthentication.decodeData(doc, false);

        // Verify result.
        assertArrayEquals(new byte[0], result, "Expected empty byte array when element text is empty and emptyNull is false.");
    }

    /**
     * Verifies that decodeData returns an empty byte array when the element text is '=' and emptyNull is true.
     * Per RFC 6120 section 6.4.2, '=' represents an empty initial response.
     */
    @Test
    public void decodeData_equalsSign_emptyNullTrue_returnsEmptyArray() throws SaslFailureException
    {
        // Setup test fixture.
        final Element doc = DocumentHelper.createElement("auth");
        doc.setText("=");

        // Execute system under test.
        final byte[] result = SASLAuthentication.decodeData(doc, true);

        // Verify result.
        assertArrayEquals(new byte[0], result, "Expected empty byte array when element text is '=' and emptyNull is true.");
    }

    /**
     * Verifies that decodeData throws SaslFailureException when the element text is '=' and emptyNull is false.
     * The '=' encoding is only valid in SASL1 auth elements.
     */
    @Test
    public void decodeData_equalsSign_emptyNullFalse_throwsException()
    {
        // Setup test fixture.
        final Element doc = DocumentHelper.createElement("response");
        doc.setText("=");

        // Execute system under test & verify result.
        assertThrows(SaslFailureException.class, () -> SASLAuthentication.decodeData(doc, false),
            "Expected SaslFailureException when element text is '=' and emptyNull is false.");
    }

    /**
     * Verifies that decodeData correctly decodes a valid base64-encoded string.
     */
    @Test
    public void decodeData_validBase64_returnsDecodedBytes() throws SaslFailureException
    {
        // Setup test fixture.
        final byte[] expected = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final String encoded = java.util.Base64.getEncoder().encodeToString(expected);
        final Element doc = DocumentHelper.createElement("response");
        doc.setText(encoded);

        // Execute system under test.
        final byte[] result = SASLAuthentication.decodeData(doc, false);

        // Verify result.
        assertArrayEquals(expected, result, "Expected decoded bytes to match original input.");
    }

    /**
     * Verifies that decodeData throws SaslFailureException when the element text is not valid base64.
     */
    @Test
    public void decodeData_invalidBase64_throwsException()
    {
        // Setup test fixture.
        final Element doc = DocumentHelper.createElement("response");
        doc.setText("not-valid-base64!!!");

        // Execute system under test & verify result.
        assertThrows(SaslFailureException.class, () -> SASLAuthentication.decodeData(doc, false),
            "Expected SaslFailureException when element text is not valid base64.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession returns a non-null (but empty) element
     * when there are no available mechanisms, SASL1 is used, and sasl.client.suppressEmpty is false.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl1_suppressEmptyFalse_noMechanisms_returnsEmptyElement()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, false);

        // Verify result.
        assertTrue(result != null && result.elements().isEmpty(),
            "Expected a non-null empty <mechanisms> element when suppressEmpty is false and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession returns null
     * when there are no available mechanisms, SASL1 is used, and sasl.client.suppressEmpty is true.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl1_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, false);

        // Verify result.
        assertNull(result, "Expected null when suppressEmpty is true and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.client.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl2_suppressEmptyFalse_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is false.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.client.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl2_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is true.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession returns a non-null (but empty) element
     * when there are no available mechanisms, SASL1 is used, and sasl.server.suppressEmpty is false.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl1_suppressEmptyFalse_noMechanisms_returnsEmptyElement()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, false);

        // Verify result.
        assertTrue(result != null && result.elements().isEmpty(),
            "Expected a non-null empty <mechanisms> element when suppressEmpty is false and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession returns null
     * when there are no available mechanisms, SASL1 is used, and sasl.server.suppressEmpty is true.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl1_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, false);

        // Verify result.
        assertNull(result, "Expected null when suppressEmpty is true and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.server.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl2_suppressEmptyFalse_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is false.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.server.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl2_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Element result = SASLAuthentication.getSASLMechanismsElement(session, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is true.");
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
