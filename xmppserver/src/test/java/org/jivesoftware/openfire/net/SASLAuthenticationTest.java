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
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.security.sasl.SaslServer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.jivesoftware.openfire.net.SASLAuthentication.SASL_NAMESPACE;
import static org.jivesoftware.openfire.net.SASLAuthentication.SASL2_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
     * For SASL1, the success element has no authorization-identifier.
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
        // SASL1 <success/> carries no authorization-identifier.
        assertFalse(response.getValue().contains("authorization-identifier"), "Expected no authorization-identifier in SASL1 success element.");
    }

    /**
     * Verifies that authenticationSuccessful generates an anonymous auth token for a client with no username, using SASL2 (no Bind2).
     * The authorization-identifier must be a bare JID (node@domain) where the node is the anonymous UUID.
     */
    @Test
    public void shouldGenerateAnonymousAuthTokenForClientWhenUsernameIsNullWithSasl2()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        // No bind2-request set in session data, so the non-bind2 SASL2 path is taken.
        // Capture the anonymous username (stream-ID resource) before authentication changes session state.
        final String anonymousUsername = session.getAnonymousUsername();

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, null, "ANONYMOUS", new byte[0], true);

        // Verify result.
        final AuthToken authToken = session.getAuthToken();
        assertTrue(authToken.isAnonymous(), "Expected an anonymous auth token when username is null.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<success"), "Expected SASL2 success element to be sent.");
        // For SASL2 without Bind2, authorization-identifier must be a bare JID: uuid@domain (no resource).
        final String expectedBareJid = anonymousUsername + "@" + Fixtures.XMPP_DOMAIN;
        assertTrue(response.getValue().contains(expectedBareJid),
            "Expected authorization-identifier to be bare JID '" + expectedBareJid + "' but got: " + response.getValue());
        assertFalse(response.getValue().contains(expectedBareJid + "/"),
            "Expected no resource in authorization-identifier for non-Bind2 SASL2 case.");
    }

    /**
     * Verifies that authenticationSuccessful generates an anonymous auth token for a client with no username, using SASL2+Bind2,
     * and that the SASL2 success element contains a full JID authorization-identifier where node and resource are the same UUID.
     */
    @Test
    public void shouldGenerateAnonymousAuthTokenForClientWhenUsernameIsNullWithSasl2AndBind2()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        // Capture the anonymous username (stream-ID resource) before authentication changes session state.
        final String anonymousUsername = session.getAnonymousUsername();

        // Set a Bind2Request in session data so the bind2 path is taken.
        // For anonymous sessions, the resource must equal the anonymous username (same UUID for node and resource).
        final Bind2Request bind2Request = mock(Bind2Request.class);
        when(bind2Request.generateResourceString(any())).thenReturn(anonymousUsername);
        session.setSessionData("bind2-request", bind2Request);

        // Stub SessionManager.bindResource to complete successfully (synchronously).
        final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        when(sessionManager.bindResource(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(SessionManager.BindResult.BOUND));

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, null, "ANONYMOUS", new byte[0], true);

        // Verify result.
        final AuthToken authToken = session.getAuthToken();
        assertTrue(authToken.isAnonymous(), "Expected an anonymous auth token when username is null.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        final String responseValue = response.getValue();
        assertTrue(responseValue.contains("<success"), "Expected SASL2 success element to be sent.");
        // For SASL2+Bind2 anonymous, authorization-identifier must be a full JID: uuid@domain/uuid
        // where the node (local part) and resource are the same UUID.
        final String expectedFullJid = anonymousUsername + "@" + Fixtures.XMPP_DOMAIN + "/" + anonymousUsername;
        assertTrue(responseValue.contains(expectedFullJid),
            "Expected authorization-identifier to be full JID '" + expectedFullJid + "' (node==resource for anonymous) but got: " + response.getValue());
        verify(bind2Request).processFeatureRequests(any(), any());
    }

    /**
     * Verifies that authenticationSuccessful generates a user auth token for a client with a username.
     * For SASL1, the success element has no authorization-identifier.
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
        // SASL1 <success/> carries no authorization-identifier.
        assertFalse(response.getValue().contains("authorization-identifier"), "Expected no authorization-identifier in SASL1 success element.");
    }

    /**
     * Verifies that authenticationSuccessful generates a user auth token for a client with a username, using SASL2 (no Bind2).
     * The authorization-identifier must be a bare JID (username@domain) with no resource.
     */
    @Test
    public void shouldGenerateUserAuthTokenForClientWhenUsernameIsProvidedWithSasl2()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        // No bind2-request set in session data, so the non-bind2 SASL2 path is taken.

        final String username = "testuser";

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, username, "PLAIN", new byte[0], true);

        // Verify result.
        final AuthToken authToken = session.getAuthToken();
        assertFalse(authToken.isAnonymous(), "Expected a user auth token when username is provided.");
        assertEquals(username, authToken.getUsername(), "Expected auth token to contain the provided username.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<success"), "Expected SASL2 success element to be sent.");
        // For SASL2 without Bind2, authorization-identifier must be a bare JID: username@domain (no resource).
        final String expectedBareJid = username + "@" + Fixtures.XMPP_DOMAIN;
        assertTrue(response.getValue().contains(expectedBareJid),
            "Expected authorization-identifier to be bare JID '" + expectedBareJid + "' but got: " + response.getValue());
        assertFalse(response.getValue().contains(expectedBareJid + "/"),
            "Expected no resource in authorization-identifier for non-Bind2 SASL2 case.");
    }

    /**
     * Verifies that authenticationSuccessful generates a user auth token for a client with a username, using SASL2+Bind2,
     * and that the SASL2 success element contains a full JID authorization-identifier (username@domain/resource).
     */
    @Test
    public void shouldGenerateUserAuthTokenForClientWhenUsernameIsProvidedWithSasl2AndBind2()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        final String username = "testuser";
        final String resource = "testresource";

        // Set a Bind2Request in session data so the bind2 path is taken.
        final Bind2Request bind2Request = mock(Bind2Request.class);
        when(bind2Request.generateResourceString(any())).thenReturn(resource);
        session.setSessionData("bind2-request", bind2Request);

        // Stub SessionManager.bindResource to complete successfully (synchronously).
        final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        when(sessionManager.bindResource(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(SessionManager.BindResult.BOUND));

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, username, "PLAIN", new byte[0], true);

        // Verify result.
        final AuthToken authToken = session.getAuthToken();
        assertFalse(authToken.isAnonymous(), "Expected a user auth token when username is provided.");
        assertEquals(username, authToken.getUsername(), "Expected auth token to contain the provided username.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<success"), "Expected SASL2 success element to be sent.");
        // For SASL2+Bind2, authorization-identifier must be a full JID: username@domain/resource.
        final String expectedFullJid = username + "@" + Fixtures.XMPP_DOMAIN + "/" + resource;
        assertTrue(response.getValue().contains(expectedFullJid),
            "Expected authorization-identifier to be full JID '" + expectedFullJid + "' but got: " + response.getValue());
        verify(bind2Request).processFeatureRequests(any(), any());
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

    /**
     * Verifies that a SASL2 authentication request is rejected when SASL2 is disabled on the server, and that the
     * rejection happens before any mechanism-specific processing.
     */
    @Test
    public void shouldRejectSasl2WhenNotEnabled()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(false);
        // PLAIN-only: with EXTERNAL enabled, a regression that bypassed the gate would reach mechanism eligibility and
        // NPE on the bare mock's null config — surfacing as a not-authorized <failure> that mimics the gate rejection.
        // Restricting to PLAIN ensures the only thing that can fail this test is the gate itself.
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2AuthenticateElement("PLAIN"), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL2 negotiation to fail when SASL2 is disabled.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        // Condition-specific: distinguishes the gate rejection from any other failure reason.
        assertTrue(response.getValue().contains("<not-authorized"), "Expected a not-authorized condition specifically from the SASL2-disabled gate.");
        assertFalse(response.getValue().contains("<challenge"), "Did not expect negotiation to proceed past the gate.");
    }

    /**
     * Verifies that a SASL2 authentication request is rejected when TLS is required for SASL2 but the session is not
     * encrypted. The gate must fire before mechanism eligibility is evaluated (hence no invalid-mechanism failure for
     * an otherwise-eligible mechanism).
     */
    @Test
    public void shouldRejectSasl2WhenTlsRequiredButSessionIsNotEncrypted()
    {
        // Setup test fixture: SASL2 enabled, TLS required (the default), session not encrypted.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2AuthenticateElement("PLAIN"), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL2 negotiation to fail when TLS is required but the session is unencrypted.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<failure"), "Expected a SASL failure when TLS is required but the session is unencrypted.");
        assertFalse(response.getValue().contains("<challenge"), "Did not expect negotiation to proceed to a challenge.");
        // PLAIN would be an eligible mechanism here, so an invalid-mechanism failure would prove the gate ran too late.
        assertFalse(response.getValue().contains("<invalid-mechanism"), "Expected the TLS gate to reject before mechanism eligibility is evaluated.");
        // Condition assumption: adjust if handle() uses a Failure other than ENCRYPTION_REQUIRED for this rejection.
        assertTrue(response.getValue().contains("<encryption-required"), "Expected an encryption-required condition when TLS is required but absent.");
    }

    /**
     * Verifies that a SASL2 authentication request is accepted (negotiation proceeds) when SASL2 is enabled and the
     * session is encrypted.
     */
    @Test
    public void shouldAcceptSasl2WhenEnabledAndSessionIsEncrypted()
    {
        // Setup test fixture: SASL2 enabled, session encrypted (TLS requirement satisfied).
        // Enable only PLAIN: computing the available-mechanism set evaluates every supported mechanism, and the
        // EXTERNAL branch would dereference connection.getConfiguration() (null on this bare mock) for an encrypted
        // session. This test is about the SASL2 gate, not EXTERNAL, so PLAIN alone keeps it focused.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2AuthenticateElement("PLAIN"), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.needResponse, status, "Expected SASL2 PLAIN to pass the gate and continue negotiation by issuing a challenge.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertFalse(response.getValue().contains("<failure"), "Did not expect a failure when SASL2 is enabled on an encrypted session.");
        assertTrue(response.getValue().contains("<challenge"), "Expected a challenge stanza as proof that SASL2 negotiation continued past the gate.");
    }

    /**
     * Verifies that a SASL2 authentication request is accepted on an unencrypted session when TLS is not required for
     * SASL2. This proves the TLS requirement is genuinely governed by the property.
     */
    @Test
    public void shouldAcceptSasl2WhenTlsNotRequiredAndSessionIsNotEncrypted()
    {
        // Setup test fixture: SASL2 enabled, TLS requirement explicitly disabled, session not encrypted.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2AuthenticateElement("PLAIN"), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.needResponse, status, "Expected SASL2 to be permitted on an unencrypted session when TLS is not required.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertFalse(response.getValue().contains("<failure"), "Did not expect a failure when the TLS requirement for SASL2 is disabled.");
        assertTrue(response.getValue().contains("<challenge"), "Expected a challenge stanza as proof that SASL2 negotiation continued past the gate.");
    }

    /**
     * Verifies that the SASL2 gate does not affect SASL1 negotiation: a SASL1 request must still be processed even
     * when SASL2 is disabled.
     */
    @Test
    public void shouldNotApplySasl2GateToSasl1Requests()
    {
        // Setup test fixture: SASL2 disabled; a SASL1 request should be unaffected by the SASL2 gate.
        SASLAuthentication.ENABLE_SASL2.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.needResponse, status, "Expected SASL1 PLAIN to proceed regardless of the SASL2 gate.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertFalse(response.getValue().contains("<failure"), "Did not expect the SASL2 gate to fail a SASL1 request.");
        assertTrue(response.getValue().contains("<challenge"), "Expected a challenge stanza for the SASL1 request.");
    }

    /**
     * Verifies that the SASL2 TLS requirement does not leak into SASL1 negotiation: with SASL2 enabled and TLS required
     * for SASL2, an unencrypted SASL1 request must still be processed. This guards against a regression that hoisted the
     * TLS check above the SASL2-only guard, which would break SASL1-over-plaintext while leaving the SASL2 tests green.
     */
    @Test
    public void shouldNotApplySasl2TlsRequirementToSasl1Requests()
    {
        // Setup test fixture: SASL2 enabled and TLS-required for SASL2, session unencrypted, SASL1 request.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.needResponse, status, "Expected SASL1 PLAIN to proceed even though the SASL2 TLS requirement is active.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertFalse(response.getValue().contains("<failure"), "Did not expect the SASL2 TLS gate to fail a SASL1 request.");
        assertTrue(response.getValue().contains("<challenge"), "Expected a challenge stanza for the SASL1 request.");
    }

    /**
     * Verifies that the SASL2 gate applies to inbound server sessions as well, rejecting before mechanism eligibility
     * when TLS is required but the session is unencrypted.
     */
    @Test
    public void shouldRejectSasl2ForIncomingServerSessionWhenTlsRequiredButNotEncrypted()
    {
        // Setup test fixture: SASL2 enabled, TLS required, unencrypted inbound server session.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2AuthenticateElement("EXTERNAL"), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected the SASL2 gate to reject an unencrypted inbound server session when TLS is required.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        // EXTERNAL is ineligible here; an invalid-mechanism failure would prove the gate ran too late.
        assertFalse(response.getValue().contains("<invalid-mechanism"), "Expected the TLS gate to reject before mechanism eligibility is evaluated.");
        // Condition-specific: ties the pass to the TLS gate rather than to any incidental failure.
        assertTrue(response.getValue().contains("<encryption-required"), "Expected an encryption-required condition specifically from the SASL2 TLS gate.");
    }

    /**
     * Verifies that checkSASL2Permitted reports SASL2 as not permitted (NOT_AUTHORIZED) when SASL2 is disabled,
     * regardless of encryption state. The disabled check must take precedence over the TLS check.
     */
    @Test
    public void checkSASL2Permitted_disabled_encrypted_returnsNotAuthorized()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isPresent(), "Expected SASL2 to be reported as not permitted when disabled.");
        assertEquals(Failure.NOT_AUTHORIZED, result.get(), "Expected NOT_AUTHORIZED when SASL2 is disabled.");
    }

    /**
     * Verifies that checkSASL2Permitted reports NOT_AUTHORIZED when SASL2 is disabled and the session is unencrypted.
     * Even though TLS is also absent, the disabled condition is evaluated first and determines the reason.
     */
    @Test
    public void checkSASL2Permitted_disabled_unencrypted_returnsNotAuthorized()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(false);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isPresent(), "Expected SASL2 to be reported as not permitted when disabled.");
        assertEquals(Failure.NOT_AUTHORIZED, result.get(), "Expected the disabled check to take precedence over the TLS check, yielding NOT_AUTHORIZED.");
    }

    /**
     * Verifies that checkSASL2Permitted reports ENCRYPTION_REQUIRED when SASL2 is enabled and requires TLS, but the
     * session is not encrypted.
     */
    @Test
    public void checkSASL2Permitted_enabled_tlsRequired_unencrypted_returnsEncryptionRequired()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isPresent(), "Expected SASL2 to be reported as not permitted when TLS is required but absent.");
        assertEquals(Failure.ENCRYPTION_REQUIRED, result.get(), "Expected ENCRYPTION_REQUIRED when TLS is required but the session is unencrypted.");
    }

    /**
     * Verifies that checkSASL2Permitted permits SASL2 when it is enabled, requires TLS, and the session is encrypted.
     */
    @Test
    public void checkSASL2Permitted_enabled_tlsRequired_encrypted_returnsEmpty()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isEmpty(), "Expected SASL2 to be permitted when enabled, TLS is required, and the session is encrypted.");
    }

    /**
     * Verifies that checkSASL2Permitted permits SASL2 on an unencrypted session when TLS is not required, proving the
     * TLS condition is genuinely governed by the property rather than always enforced.
     */
    @Test
    public void checkSASL2Permitted_enabled_tlsNotRequired_unencrypted_returnsEmpty()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isEmpty(), "Expected SASL2 to be permitted on an unencrypted session when TLS is not required.");
    }

    /**
     * Verifies that checkSASL2Permitted permits SASL2 when enabled and TLS is not required, and the session happens to
     * be encrypted. Encryption should not be penalised when it is not required.
     */
    @Test
    public void checkSASL2Permitted_enabled_tlsNotRequired_encrypted_returnsEmpty()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isEmpty(), "Expected SASL2 to be permitted when enabled and TLS is not required, regardless of encryption.");
    }

    /**
     * Verifies SASL1 channel-binding advertisement.
     */
    @Test
    public void shouldAppendChannelBindingCapabilityForSasl1()
    {
        // Setup test fixture.
        final Element mechanisms = DocumentHelper.createElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final List<Element> features = new ArrayList<>(List.of(mechanisms));
        final Element capability = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);

            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(mechanisms)).thenReturn(Optional.of(capability));

            // Execute system under test.
            SASLAuthentication.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            assertTrue(features.contains(capability), "A SASL1 mechanisms feature with channel-binding support should result in a sasl-channel-binding feature being added.");
        }
    }

    /**
     * Verifies SASL2 channel-binding advertisement.
     */
    @Test
    public void shouldAppendChannelBindingCapabilityForSasl2()
    {
        // Setup test fixture.
        final Element authentication = DocumentHelper.createElement(QName.get("authentication", "urn:xmpp:sasl:2"));
        final List<Element> features = new ArrayList<>(List.of(authentication));
        final Element capability = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);

            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(authentication)).thenReturn(Optional.of(capability));

            // Execute system under test.
            SASLAuthentication.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            assertTrue(features.contains(capability), "A SASL2 authentication feature with channel-binding support should result in a sasl-channel-binding feature being added.");
        }
    }

    /**
     * Verifies no advertisement without a SASL feature.
     */
    @Test
    public void shouldNotAppendChannelBindingCapabilityWhenNoSaslFeatureExists()
    {
        // Setup test fixture.
        final Element compression = DocumentHelper.createElement(QName.get("compression", "http://jabber.org/features/compress"));
        final List<Element> features = new ArrayList<>(List.of(compression));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            // Execute system under test.
            SASLAuthentication.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            mocked.verifyNoInteractions();

            assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())),"No sasl-channel-binding feature should be added when no SASL feature is present.");
        }
    }

    /**
     * Verifies no advertisement when unavailable.
     */
    @Test
    public void shouldNotAppendChannelBindingCapabilityWhenProviderReturnsEmpty()
    {
        // Setup test fixture.
        final Element mechanisms = DocumentHelper.createElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final List<Element> features = new ArrayList<>(List.of(mechanisms));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);

            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(mechanisms)).thenReturn(Optional.empty());

            // Execute system under test.
            SASLAuthentication.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())), "No sasl-channel-binding feature should be added when no channel-binding types are available.");
        }
    }

    /**
     * Verifies SASL1 and SASL2 channel-binding advertisement (simultaneously).
     */
    @Test
    public void shouldAppendChannelBindingCapabilityForBothSasl1AndSasl2()
    {
        // Setup test fixture.
        final Element mechanisms = DocumentHelper.createElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final Element authentication = DocumentHelper.createElement(QName.get("authentication", "urn:xmpp:sasl:2"));
        final List<Element> features = new ArrayList<>(List.of(mechanisms, authentication));

        // Distinct, identifiable return values so the assertions can attribute each to its source feature.
        final Element cbForSasl1 = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));
        cbForSasl1.addAttribute("test-source", "sasl1");
        final Element cbForSasl2 = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));
        cbForSasl2.addAttribute("test-source", "sasl2");

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);
            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(mechanisms)).thenReturn(Optional.of(cbForSasl1));
            when(manager.getSASLChannelBindingTypeCapabilityElement(authentication)).thenReturn(Optional.of(cbForSasl2));

            // Execute system under test
            SASLAuthentication.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            verify(manager).getSASLChannelBindingTypeCapabilityElement(authentication);
            verify(manager).getSASLChannelBindingTypeCapabilityElement(mechanisms);
            assertTrue(features.contains(cbForSasl2), "SASL2 must receive channel-binding caps even when SASL1 is also advertised.");
            assertTrue(features.contains(cbForSasl1), "SASL1 caps should also be present in the dual-stack case.");
        }
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

    private static Element sasl2AuthenticateElement(final String mechanism)
    {
        final Element authenticate = DocumentHelper.createElement(new QName("authenticate", Namespace.get("", SASL2_NAMESPACE)));
        authenticate.addAttribute("mechanism", mechanism);
        return authenticate;
    }
}
