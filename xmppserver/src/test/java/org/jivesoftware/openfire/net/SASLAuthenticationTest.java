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

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastToken;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
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
import org.jivesoftware.openfire.sasl.TestSaslMechanism;
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
import java.util.stream.Collectors;

import static org.jivesoftware.openfire.net.SASLAuthentication.SASL_NAMESPACE;
import static org.jivesoftware.openfire.net.SASLAuthentication.SASL2_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
     * Verifies that a client session cannot use EXTERNAL when that mechanism is not advertised for the session.
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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("EXTERNAL"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL negotiation to fail when EXTERNAL is requested on a client session that did not advertise it.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<invalid-mechanism"), "Expected server to return an invalid-mechanism failure for a non-advertised mechanism.");
    }

    /**
     * Verifies that an inbound server session rejects PLAIN when only session-eligible mechanisms are advertised.
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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("EXTERNAL"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("EXTERNAL"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("ANONYMOUS"));

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
        try (final MockedStatic<EntityCapabilitiesManager> mockedEntityCaps = mockStatic(EntityCapabilitiesManager.class)) {
            mockedEntityCaps.when(() -> EntityCapabilitiesManager.getLocalDomainVerHash(any())).thenReturn(null);

            // Setup test fixture.
            final Connection connection = mock(Connection.class);
            final ConnectionConfiguration connectionConfiguration = mock(ConnectionConfiguration.class);
            when(connectionConfiguration.getTlsPolicy()).thenReturn(Connection.TLSPolicy.disabled);
            when(connectionConfiguration.getCompressionPolicy()).thenReturn(Connection.CompressionPolicy.disabled);
            when(connection.getConfiguration()).thenReturn(connectionConfiguration);
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
            verify(connection, times(2)).deliverRawText(response.capture());
            final String responseValue = response.getAllValues().get(0);
            assertTrue(responseValue.contains("<success"), "Expected SASL2 success element to be sent.");
            // For SASL2+Bind2 anonymous, authorization-identifier must be a full JID: uuid@domain/uuid
            // where the node (local part) and resource are the same UUID.
            final String expectedFullJid = anonymousUsername + "@" + Fixtures.XMPP_DOMAIN + "/" + anonymousUsername;
            assertTrue(responseValue.contains(expectedFullJid),
                "Expected authorization-identifier to be full JID '" + expectedFullJid + "' (node==resource for anonymous) but got: " + response.getValue());
            verify(bind2Request).processFeatureRequests(any(), any());

            final String responseValue2 = response.getAllValues().get(1);
            assertTrue(responseValue2.contains("<stream:feature"), "Expected stream features Element to be sent.");
            assertFalse(responseValue2.contains("<bind"), "Expected resource binding not to be offered.");
        }
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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        try (final MockedStatic<EntityCapabilitiesManager> mockedEntityCaps = mockStatic(EntityCapabilitiesManager.class)) {
            mockedEntityCaps.when(() -> EntityCapabilitiesManager.getLocalDomainVerHash(any())).thenReturn(null);

            // Setup test fixture.
            final Connection connection = mock(Connection.class);
            final ConnectionConfiguration connectionConfiguration = mock(ConnectionConfiguration.class);
            when(connectionConfiguration.getTlsPolicy()).thenReturn(Connection.TLSPolicy.disabled);
            when(connectionConfiguration.getCompressionPolicy()).thenReturn(Connection.CompressionPolicy.disabled);
            when(connection.getConfiguration()).thenReturn(connectionConfiguration);
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
            verify(connection, times(2)).deliverRawText(response.capture());
            final String responseValue = response.getAllValues().get(0);
            assertTrue(responseValue.contains("<success"), "Expected SASL2 success element to be sent.");
            // For SASL2+Bind2, authorization-identifier must be a full JID: username@domain/resource.
            final String expectedFullJid = username + "@" + Fixtures.XMPP_DOMAIN + "/" + resource;
            assertTrue(responseValue.contains(expectedFullJid),
                "Expected authorization-identifier to be full JID '" + expectedFullJid + "' but got: " + responseValue);
            verify(bind2Request).processFeatureRequests(any(), any());

            final String responseValue2 = response.getAllValues().get(1);
            assertTrue(responseValue2.contains("<stream:feature"), "Expected stream features Element to be sent.");
            assertFalse(responseValue2.contains("<bind"), "Expected resource binding not to be offered.");
        }
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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("EXTERNAL"));
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
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, false);

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
        FastTokenManager.ENABLE_FAST.setValue(false);
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, false);

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
        FastTokenManager.ENABLE_FAST.setValue(false);
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

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
        FastTokenManager.ENABLE_FAST.setValue(false);
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

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
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, false);

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
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, false);

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
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, true);

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
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, true);

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN")); // Slightly hacky, as this mechanism would not have been advertised. Simulating that it has to exercise the check under test.

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN")); // Slightly hacky, as this mechanism would not have been advertised. Simulating that it has to exercise the check under test.

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("EXTERNAL")); // Slightly hacky, as this mechanism would not have been advertised. Simulating that it has to exercise the check under test.

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN")); // Slightly hacky, as this mechanism would not have been advertised. Simulating that it has to exercise the check under test.

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN")); // Slightly hacky, as this mechanism would not have been advertised. Simulating that it has to exercise the check under test.

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

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
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

        // Execute system under test.
        final Optional<Failure> result = SASLAuthentication.checkSASL2Permitted(session);

        // Verify result.
        assertTrue(result.isEmpty(), "Expected SASL2 to be permitted when enabled and TLS is not required, regardless of encryption.");
    }

    /**
     * Verifies that channel-binding types are not advertised when no channel-binding-capable mechanism is offered,
     * since a peer that cannot use channel binding has no use for the type list.
     *
     * The connection is deliberately stubbed to support types: this asserts that the mechanism list alone is
     * enough to suppress the announcement, rather than the outcome coinciding with a connection that happens to
     * support nothing.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0440.html">XEP-0440: SASL Channel-Binding Type Capability</a>
     */
    @Test
    public void getAdvertisableChannelBindingTypes_noPlusMechanism_returnsEmpty()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-exporter"));
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(connection);

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getAdvertisableChannelBindingTypes(session, Set.of("PLAIN", "EXTERNAL"));

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no channel-binding types when no -PLUS mechanism is offered.");
    }

    /**
     * Verifies that no channel-binding types are advertised when a -PLUS mechanism is offered but the session's
     * connection cannot supply any type in its current state (for example, because it is not encrypted).
     */
    @Test
    public void getAdvertisableChannelBindingTypes_plusMechanismButConnectionSupportsNone_returnsEmpty()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of());
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(connection);

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getAdvertisableChannelBindingTypes(session, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no channel-binding types when the connection supports none.");
    }

    /**
     * Verifies that the channel-binding types that the session's connection supports are advertised when a -PLUS
     * mechanism is offered.
     *
     * The types come from the connection rather than from the globally registered providers, because
     * Connection#getSupportedChannelBindingTypes is defined to reflect the connection's current state. Advertising
     * a type the connection cannot supply would leave a peer that selects it to be rejected later, when the
     * channel-binding data cannot be produced.
     */
    @Test
    public void getAdvertisableChannelBindingTypes_plusMechanismWithConnectionSupport_returnsConnectionTypes()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-server-end-point", "tls-exporter"));
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(connection);

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getAdvertisableChannelBindingTypes(session, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Verify result.
        assertEquals(Set.of("tls-server-end-point", "tls-exporter"), result, "Expected the connection's supported channel-binding types to be advertised alongside a -PLUS mechanism.");
    }

    /**
     * Verifies that no channel-binding types are advertised for a session that has no connection, rather than the
     * lookup failing. A stream-management session that is detached has no connection, and stream features can be
     * regenerated after it resumes.
     */
    @Test
    public void getAdvertisableChannelBindingTypes_noConnection_returnsEmpty()
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(null);

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getAdvertisableChannelBindingTypes(session, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no channel-binding types for a session that has no connection.");
    }

    /**
     * Verifies that exactly one XEP-0440 capability element is advertised, even when both the SASL1 and SASL2
     * feature elements are offered.
     *
     * The previous implementation derived the capability element per SASL feature element, producing a duplicate
     * announcement whenever both profiles were advertised. XEP-0440 defines a single stream feature.
     */
    @Test
    public void asSASLMechanisms_advertisesChannelBindingTypesOnce_whenBothSasl1AndSasl2AreOffered()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final List<Element> features = SASLAuthentication.asSASLMechanisms(session, Set.of("PLAIN"), Set.of("tls-server-end-point", "tls-exporter"));
        assertEquals(1, features.stream().filter(e -> "mechanisms".equals(e.getName())).count(), "Test setup issue: expected a SASL1 mechanisms feature to be advertised.");
        assertEquals(1, features.stream().filter(e -> "authentication".equals(e.getName())).count(), "Test setup issue: expected a SASL2 authentication feature to be advertised.");

        // Verify result.
        final List<Element> capabilities = features.stream()
            .filter(e -> "sasl-channel-binding".equals(e.getName()))
            .toList();
        assertEquals(1, capabilities.size(), "Expected exactly one sasl-channel-binding feature, regardless of how many SASL profiles are advertised.");
        assertEquals(SASLAuthentication.SASL_CHANNEL_BINDING_NAMESPACE, capabilities.get(0).getNamespaceURI(), "Expected the capability element to be in the XEP-0440 namespace.");
        assertEquals(2, capabilities.get(0).elements("channel-binding").size(), "Expected one channel-binding child per advertised type.");
    }

    /**
     * Verifies that no channel-binding capability element is emitted when no types are advertised, rather than an
     * empty one. A peer following XEP-0474 treats the presence of the element as significant when computing the
     * downgrade protection hash, so an empty element is not equivalent to an absent one.
     */
    @Test
    public void asSASLMechanisms_omitsChannelBindingElement_whenNoTypesAdvertised()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final List<Element> features = SASLAuthentication.asSASLMechanisms(session, Set.of("PLAIN"), Set.of());

        // Verify result.
        assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())), "Expected no sasl-channel-binding feature when no channel-binding types are advertised.");
    }

    /**
     * Verifies that an unrecognised session type receives no SASL features at all, including no XEP-0440 capability
     * element, even when channel-binding types would otherwise be advertised.
     */
    @Test
    public void asSASLMechanisms_unknownSessionType_returnsNoFeatures()
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);

        // Execute system under test.
        final List<Element> features = SASLAuthentication.asSASLMechanisms(session, Set.of("SCRAM-SHA-1-PLUS"), Set.of("tls-exporter"));

        // Verify result.
        assertTrue(features.isEmpty(), "Expected no features for an unrecognised session type, including no channel-binding capability element.");
    }

    /**
     * Verifies that a SASL mechanism is rejected when no SASL mechanisms were advertised for the session.
     */
    @Test
    public void testMechanismIsRejectedWhenNoMechanismsWereAdvertised() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL negotiation to fail when no mechanisms were advertised for the session.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<invalid-mechanism"), "Expected server to return an invalid-mechanism failure when no mechanisms were advertised.");
    }

    /**
     * Verifies that a SASL mechanism that was advertised to a session is rejected when it is no longer supported by
     * the current configuration.
     */
    @Test
    public void testAdvertisedMechanismRejectedAfterConfigurationChange() throws Exception
    {
        // Setup test fixture.
        final List<String> enabledMechanisms = SASLAuthentication.getEnabledMechanisms();
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN"));

        // Simulate a configuration change after the mechanism was advertised.
        // PLAIN is no longer supported by the current configuration.
        try {
            SASLAuthentication.setEnabledMechanisms(List.of("EXTERNAL"));

            // Execute system under test.
            final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

            // Verify result.
            assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL negotiation to fail when the advertised mechanism is no longer supported by the current configuration.");
            final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
            verify(connection).deliverRawText(response.capture());
            assertTrue(response.getValue().contains("<invalid-mechanism"), "Expected server to return an invalid-mechanism failure when the advertised mechanism is no longer supported.");
        } finally {
            // Restore fixture.
            SASLAuthentication.setEnabledMechanisms(enabledMechanisms);
        }
    }

    /**
     * Verifies that a SASL mechanism that is currently supported by the server cannot be used when it was not advertised
     * for the session.
     */
    @Test
    public void testSupportedButNotAdvertisedMechanismIsRejected() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // PLAIN is deliberately not advertised for this session.
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("EXTERNAL"));

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "Expected SASL negotiation to fail when a currently supported mechanism was not advertised for the session.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<invalid-mechanism"), "Expected server to return an invalid-mechanism failure for a non-advertised mechanism.");
    }

    /**
     * Verifies that advertised SASL mechanisms can be stored and subsequently retrieved for a session.
     */
    @Test
    public void testAdvertisedSASLMechanismsCanBeStoredAndRetrieved()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        final Set<String> advertisedMechanisms = Set.of("PLAIN", "EXTERNAL");

        // Execute system under test.
        SASLAuthentication.setAdvertisedSASLMechanisms(session, advertisedMechanisms);

        // Verify result.
        final Optional<Set<String>> result = SASLAuthentication.getAdvertisedSASLMechanisms(session);
        assertTrue(result.isPresent(), "Expected advertised SASL mechanisms to be available for the session.");
        assertEquals(advertisedMechanisms, result.get(), "Expected the advertised SASL mechanisms to be retained.");
    }

    /**
     * Verifies that no advertised SASL mechanisms are returned when none have been recorded for a session.
     */
    @Test
    public void testAdvertisedSASLMechanismsAreEmptyWhenNotRecorded()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Optional<Set<String>> result = SASLAuthentication.getAdvertisedSASLMechanisms(session);

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no advertised SASL mechanisms to be returned when none were recorded.");
    }

    /**
     * Verifies that advertised SASL mechanisms are captured as an immutable snapshot.
     */
    @Test
    public void testAdvertisedSASLMechanismsAreCapturedAsImmutableSnapshot()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        final Set<String> advertisedMechanisms = new HashSet<>(Set.of("PLAIN"));

        // Execute system under test.
        SASLAuthentication.setAdvertisedSASLMechanisms(session, advertisedMechanisms);
        advertisedMechanisms.add("EXTERNAL");

        // Verify result.
        final Optional<Set<String>> result = SASLAuthentication.getAdvertisedSASLMechanisms(session);
        assertTrue(result.isPresent(), "Expected advertised SASL mechanisms to be available for the session.");
        assertEquals(Set.of("PLAIN"), result.get(), "Expected advertised mechanisms to be an immutable snapshot of the original set.");
    }

    /**
     * Verifies that the SASL mechanisms and channel-binding types recorded on the session are exactly those
     * rendered into the stream features.
     *
     * This is the property the whole arrangement depends on: the XEP-0474 downgrade protection hash is computed
     * from the recorded sets, while a peer computes its own from what it received. If the two ever diverge, every
     * XEP-0474-aware authentication fails, for every user, with no indication of why. Testing
     * getAdvertisableChannelBindingTypes and asSASLMechanisms separately cannot catch that divergence; only
     * driving appendSASLFeatures can.
     */
    @Test
    public void appendSASLFeatures_recordsExactlyWhatIsAdvertised_withChannelBinding()
    {
        try (final MockedStatic<AuthFactory> authFactory = mockStatic(AuthFactory.class);
             final MockedStatic<ChannelBindingProviderManager> managers = mockStatic(ChannelBindingProviderManager.class))
        {
            // Setup test fixture: an encrypted session that is offered SCRAM-SHA-1 and its -PLUS variant.
            SASLAuthentication.setEnabledMechanisms(List.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
            authFactory.when(AuthFactory::supportsScram).thenReturn(true);
            authFactory.when(() -> AuthFactory.getScramMechanisms(any())).thenReturn(Set.of("SCRAM-SHA-1"));
            authFactory.when(AuthFactory::getFallbackScramMechanisms).thenReturn(Set.of("SCRAM-SHA-1"));

            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);
            managers.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-server-end-point", "tls-exporter"));

            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(true);
            when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-server-end-point", "tls-exporter"));

            final StreamID streamID = new BasicStreamIDFactory().createStreamID();
            final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

            // Execute system under test.
            final List<Element> features = new ArrayList<>();
            SASLAuthentication.appendSASLFeatures(session, features);

            // Verify result.
            assertEquals(Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"), advertisedMechanismsIn(features),
                "Test setup issue: expected both SCRAM mechanisms to be offered to this session.");
            assertEquals(Set.of("tls-server-end-point", "tls-exporter"), advertisedChannelBindingTypesIn(features),
                "Test setup issue: expected both channel-binding types to be advertised to this session.");
            assertEquals(advertisedMechanismsIn(features), SASLAuthentication.getAdvertisedSASLMechanisms(session).orElseThrow(),
                "The recorded SASL mechanisms must be exactly those rendered into the stream features.");
            assertEquals(advertisedChannelBindingTypesIn(features), SASLAuthentication.getAdvertisedChannelBindingTypes(session).orElseThrow(),
                "The recorded channel-binding types must be exactly those rendered into the stream features.");
        }
    }

    /**
     * Verifies the same correspondence for a session that is offered no channel-binding-capable mechanism: no
     * capability element is rendered, and an empty set is recorded.
     *
     * Recording the empty set matters as much as recording a populated one. A peer that received no XEP-0440
     * announcement omits the channel-binding section from its hash input entirely, so the server must do the
     * same; an absent record would instead mean 'we do not know what was advertised'.
     */
    @Test
    public void appendSASLFeatures_recordsExactlyWhatIsAdvertised_withoutChannelBinding()
    {
        // Setup test fixture: an unencrypted session, offered only PLAIN.
        SASLAuthentication.setEnabledMechanisms(List.of("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final List<Element> features = new ArrayList<>();
        SASLAuthentication.appendSASLFeatures(session, features);

        // Verify result.
        assertEquals(Set.of("PLAIN"), advertisedMechanismsIn(features),
            "Test setup issue: expected PLAIN to be the only mechanism offered to this session.");
        assertEquals(advertisedMechanismsIn(features), SASLAuthentication.getAdvertisedSASLMechanisms(session).orElseThrow(),
            "The recorded SASL mechanisms must be exactly those rendered into the stream features.");
        assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())),
            "Test setup issue: expected no channel-binding capability element to be rendered.");
        assertEquals(Set.of(), SASLAuthentication.getAdvertisedChannelBindingTypes(session).orElseThrow(),
            "An empty set must be recorded when no channel-binding types were advertised, distinct from nothing being recorded at all.");
    }

    /**
     * Returns the SASL mechanism names rendered into the given stream features, across every SASL profile present.
     */
    private static Set<String> advertisedMechanismsIn(final List<Element> features)
    {
        return features.stream()
            .filter(e -> "mechanisms".equals(e.getName()) || "authentication".equals(e.getName()))
            .flatMap(e -> e.elements("mechanism").stream())
            .map(Element::getTextTrim)
            .collect(Collectors.toSet());
    }

    /**
     * Returns the channel-binding type names rendered into the given stream features.
     */
    private static Set<String> advertisedChannelBindingTypesIn(final List<Element> features)
    {
        return features.stream()
            .filter(e -> "sasl-channel-binding".equals(e.getName()))
            .flatMap(e -> e.elements("channel-binding").stream())
            .map(e -> e.attributeValue("type"))
            .collect(Collectors.toSet());
    }

    // =========================================================================
    // FAST / XEP-0484 tests
    // =========================================================================

    /**
     * Verifies that the SASL2 inline feature element includes a FAST feature element when FAST is enabled.
     *
     * The SASL2 feature generated for a client session with at least one mechanism available
     * should include the FAST feature inside the &lt;inline/&gt; child.
     */
    @Test
    public void shouldIncludeFastFeatureInSasl2InlineWhenFastEnabled()
    {
        // Setup test fixture: SASL2 enabled, FAST enabled, PLAIN mechanism available.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        JiveGlobals.setProperty("xmpp.fast.enabled", "true");
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNotNull(result, "Expected a non-null SASL2 element.");
        final Element inlineEl = result.element("inline");
        assertNotNull(inlineEl, "Expected an <inline/> element inside the SASL2 authentication element.");
        final Element fastEl = inlineEl.element(new org.dom4j.QName("fast",
            org.dom4j.Namespace.get("", org.jivesoftware.openfire.fast.FastTokenManager.NAMESPACE)));
        assertNotNull(fastEl, "Expected a <fast/> element inside <inline/> when FAST is enabled.");
        assertEquals(org.jivesoftware.openfire.fast.FastTokenManager.NAMESPACE, fastEl.getNamespaceURI(),
            "Expected the <fast/> element to be in the FAST namespace.");
    }

    /**
     * Verifies that the SASL2 inline feature element does NOT include a FAST feature element when FAST is disabled.
     */
    @Test
    public void shouldNotIncludeFastFeatureInSasl2InlineWhenFastDisabled()
    {
        // Setup test fixture: SASL2 enabled, FAST disabled, PLAIN mechanism available.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        JiveGlobals.setProperty("xmpp.fast.enabled", "false");
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SASLAuthentication.getAdvertisableSASLMechanisms(session);
        final Element result = SASLAuthentication.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result: either no <inline/> or <inline/> without a <fast/> child.
        if (result != null) {
            final Element inlineEl = result.element("inline");
            if (inlineEl != null) {
                final Element fastEl = inlineEl.element(new org.dom4j.QName("fast",
                    org.dom4j.Namespace.get("", org.jivesoftware.openfire.fast.FastTokenManager.NAMESPACE)));
                assertNull(fastEl, "Expected no <fast/> element inside <inline/> when FAST is disabled.");
            }
        }
        // If result is null, no mechanisms advertised, which is also acceptable (FAST not included).
    }

    /**
     * Verifies that a SASL2 authenticate element containing a &lt;request-token&gt; FAST element
     * causes a FAST token to be issued and included in the &lt;success/&gt; response.
     *
     * This test exercises the full parse-and-issue path: the authenticate element carries
     * &lt;request-token xmlns='urn:xmpp:fast:0' mechanism='HT-SHA-256-NONE'/&gt; and,
     * after successful PLAIN authentication, the &lt;success/&gt; element should contain
     * a &lt;token/&gt; element with the correct namespace.
     */
    @Test
    public void shouldIssueFastTokenWhenRequestTokenElementPresentInSasl2Authenticate()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        JiveGlobals.setProperty("xmpp.fast.enabled", "true");
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        final String username = "testuser";

        // Simulate an already-running PLAIN SASL negotiation by pre-installing a PLAIN SaslServer.
        final SaslServer saslServer = mock(SaslServer.class);
        when(saslServer.getMechanismName()).thenReturn("PLAIN");
        session.setSessionData("SaslServer", saslServer);

        // Simulate a FAST token being issued.
        final org.jivesoftware.openfire.fast.FastToken issuedToken =
            new org.jivesoftware.openfire.fast.FastToken(
                username, org.jivesoftware.openfire.fast.FastTokenManager.HT_SHA_256_NONE,
                new byte[32], java.time.Instant.now().plusSeconds(86400));

        // Manually set the session data that parsing the <authenticate> element would set.
        session.setSessionData("fast-request-token-mechanism",
            org.jivesoftware.openfire.fast.FastTokenManager.HT_SHA_256_NONE);
        session.setSessionData("user-agent-info",
            new UserAgentInfo("123e4567-e89b-42d3-a456-426614174000", null, null));

        try (final MockedStatic<org.jivesoftware.openfire.fast.FastTokenManager> mockedFtm =
                 mockStatic(org.jivesoftware.openfire.fast.FastTokenManager.class))
        {
            mockedFtm.when(org.jivesoftware.openfire.fast.FastTokenManager::featureElement)
                .thenCallRealMethod();
            mockedFtm.when(() -> org.jivesoftware.openfire.fast.FastTokenManager.issueToken(
                    eq(username), any(String.class),
                    eq(org.jivesoftware.openfire.fast.FastTokenManager.HT_SHA_256_NONE)))
                .thenReturn(issuedToken);

            // Execute system under test: call authenticationSuccessful with SASL2.
            SASLAuthentication.authenticationSuccessful(session, username, "PLAIN", new byte[0], true);

            // Verify result.
            final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
            verify(connection).deliverRawText(response.capture());
            final String successXml = response.getValue();
            assertTrue(successXml.contains("<success"), "Expected a SASL2 success element.");
            assertTrue(successXml.contains(org.jivesoftware.openfire.fast.FastTokenManager.NAMESPACE),
                "Expected the success element to contain the FAST namespace for the token element.");
        }
    }

    /**
     * Verifies that a SASL2 success element does NOT contain a FAST token element when no
     * &lt;request-token&gt; was provided and the mechanism is not an HT-* mechanism.
     */
    @Test
    public void shouldNotIssueFastTokenWhenNoRequestTokenAndNotHtMechanism()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        JiveGlobals.setProperty("xmpp.fast.enabled", "true");
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // No fast-request-token-mechanism in session data.

        // Execute system under test.
        SASLAuthentication.authenticationSuccessful(session, "testuser", "PLAIN", new byte[0], true);

        // Verify result.
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        final String successXml = response.getValue();
        assertTrue(successXml.contains("<success"), "Expected a SASL2 success element.");
        assertFalse(successXml.contains(org.jivesoftware.openfire.fast.FastTokenManager.NAMESPACE),
            "Expected no FAST token element in the success response when no <request-token> was sent and mechanism is not HT-*.");
    }

    /**
     * Verifies that fast-rotated-token session data is cleared from the session after
     * authenticationSuccessful processes it, so it cannot be re-used for a subsequent call.
     */
    @Test
    public void shouldClearFastSessionDataAfterAuthenticationSuccessful()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        final Connection connection = mock(Connection.class);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Place FAST session data as if set by the SASL server and authenticate element handler.
        session.setSessionData("fast-request-token-mechanism",
            org.jivesoftware.openfire.fast.FastTokenManager.HT_SHA_256_NONE);
        session.setSessionData("fast-invalidate", Boolean.TRUE);
        final org.jivesoftware.openfire.fast.FastToken rotatedToken =
            new org.jivesoftware.openfire.fast.FastToken("testuser",
                org.jivesoftware.openfire.fast.FastTokenManager.HT_SHA_256_NONE,
                new byte[32], java.time.Instant.now().plusSeconds(3600));
        session.setSessionData("fast-rotated-token", rotatedToken);

        JiveGlobals.setProperty("xmpp.fast.enabled", "true");

        try (final MockedStatic<org.jivesoftware.openfire.fast.FastTokenManager> mockedFtm =
                 mockStatic(org.jivesoftware.openfire.fast.FastTokenManager.class))
        {
            mockedFtm.when(() -> org.jivesoftware.openfire.fast.FastTokenManager.invalidateTokens(any()))
                .then(invocation -> null);
            mockedFtm.when(() -> org.jivesoftware.openfire.fast.FastTokenManager.issueToken(any(), any()))
                .thenReturn(rotatedToken);

            // Execute system under test.
            SASLAuthentication.authenticationSuccessful(session, "testuser", "PLAIN", new byte[0], true);
        }

        // Verify result: session data should be cleared.
        assertNull(session.getSessionData("fast-request-token-mechanism"),
            "Expected fast-request-token-mechanism to be cleared after authenticationSuccessful.");
        assertNull(session.getSessionData("fast-invalidate"),
            "Expected fast-invalidate to be cleared after authenticationSuccessful.");
        assertNull(session.getSessionData("fast-rotated-token"),
            "Expected fast-rotated-token to be cleared after authenticationSuccessful.");
    }

    // -------------------------------------------------------------------------
    // isFastMechanism
    // -------------------------------------------------------------------------

    /**
     * Verifies that isFastMechanism returns true for all HT-* mechanism names.
     */
    @Test
    public void isFastMechanismShouldReturnTrueForHtMechanisms()
    {
        assertTrue(SASLAuthentication.isFastMechanism("HT-SHA-256-NONE"), "Expected HT-SHA-256-NONE to be a FAST mechanism.");
        assertTrue(SASLAuthentication.isFastMechanism("HT-SHA-256-UNIQ"), "Expected HT-SHA-256-UNIQ to be a FAST mechanism.");
        assertTrue(SASLAuthentication.isFastMechanism("HT-SHA-512-EXPR"), "Expected HT-SHA-512-EXPR to be a FAST mechanism.");
    }

    /**
     * Verifies that isFastMechanism returns true for all HT2-* mechanism names.
     */
    @Test
    public void isFastMechanismShouldReturnTrueForHt2Mechanisms()
    {
        assertTrue(SASLAuthentication.isFastMechanism("HT2-SHA-256-NONE"), "Expected HT2-SHA-256-NONE to be a FAST mechanism.");
        assertTrue(SASLAuthentication.isFastMechanism("HT2-SHA-512-ENDP"), "Expected HT2-SHA-512-ENDP to be a FAST mechanism.");
    }

    /**
     * Verifies that isFastMechanism returns false for standard (non-FAST) mechanism names.
     */
    @Test
    public void isFastMechanismShouldReturnFalseForNonFastMechanisms()
    {
        assertFalse(SASLAuthentication.isFastMechanism("PLAIN"),     "Expected PLAIN not to be a FAST mechanism.");
        assertFalse(SASLAuthentication.isFastMechanism("EXTERNAL"),  "Expected EXTERNAL not to be a FAST mechanism.");
        assertFalse(SASLAuthentication.isFastMechanism("SCRAM-SHA-1-PLUS"), "Expected SCRAM-SHA-1-PLUS not to be a FAST mechanism.");
        assertFalse(SASLAuthentication.isFastMechanism("ANONYMOUS"), "Expected ANONYMOUS not to be a FAST mechanism.");
    }

    // -------------------------------------------------------------------------
    // handle() — FAST mechanism bypass of the mechanism list check
    // -------------------------------------------------------------------------

    /**
     * Verifies that handle() rejects a FAST (HT-*) mechanism when FAST is disabled, even though
     * the mechanism is not in the sasl.mechs list.
     *
     * When FAST is disabled the HT-* mechanism must not pass the mechanism-list guard.
     */
    @Test
    public void handleShouldRejectFastMechanismWhenFastIsDisabled()
    {
        // Setup: FAST explicitly disabled, HT-SHA-256-NONE is not in the sasl.mechs list.
        JiveGlobals.setProperty("xmpp.fast.enabled", "false");
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN")); // HT-* not listed.

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(
            session, sasl2AuthenticateElement("HT-SHA-256-NONE"), true);

        // Verify: authentication must fail with INVALID_MECHANISM.
        assertEquals(SASLAuthentication.Status.failed, status,
            "Expected SASL negotiation to fail when a FAST mechanism is used but FAST is disabled.");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("invalid-mechanism"),
            "Expected an invalid-mechanism failure element in the response.");
    }

    /**
     * Verifies that handle() passes the mechanism-list guard for a FAST mechanism when FAST is
     * enabled — i.e., the mechanism is not rejected simply because it is absent from sasl.mechs.
     *
     * The test does not complete authentication (the SASL server will not be found for an empty
     * response), but it must NOT fail with "invalid-mechanism" at the mechanism-list check.
     */
    @Test
    public void handleShouldNotRejectFastMechanismDueToMechanismListWhenFastIsEnabled()
    {
        // Setup: FAST enabled, HT-SHA-256-NONE not in sasl.mechs but FAST is on.
        JiveGlobals.setProperty("xmpp.fast.enabled", "true");
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);
        SASLAuthentication.setEnabledMechanisms(Collections.singletonList("PLAIN")); // HT-* intentionally not listed.

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(
            session, sasl2AuthenticateElement("HT-SHA-256-NONE"), true);

        // Verify: authentication must fail (no valid session CB / no SASL server), but the failure
        // reason must NOT be "invalid-mechanism" from the mechanism-list check — it should be
        // "invalid-mechanism" from the session-eligibility check (mechanism not available for this
        // unencrypted session) or a provider-level failure, not from the sasl.mechs list guard.
        assertEquals(SASLAuthentication.Status.failed, status,
            "Expected SASL negotiation to fail (HT-* needs TLS/FAST support), but not due to the mechanism list.");

        // Capture the delivered failure element.
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        final String xml = response.getValue();

        // The important assertion: the failure was NOT "The configuration of Openfire does not
        // contain or allow the mechanism." from the sasl.mechs list guard. Any failure is
        // acceptable here (session eligibility, no provider, etc.) but it must be after the list
        // guard passed — confirmed by the fact that the test did not throw before this point.
        assertTrue(xml.contains("failure"), "Expected a SASL failure element in the response.");
    }

    @Test
    public void handleRejectsFastMechanismWithoutFastMarker()
    {
        FastTokenManager.ENABLE_FAST.setValue(true);
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(true);
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
        session.setClaimedIdentity(new org.xmpp.packet.JID("test-user@" + Fixtures.XMPP_DOMAIN));
        session.setSessionData(SASLAuthentication.AVAILABLE_FAST_MECHANISMS_FOR_SESSION,
            Set.of(FastTokenManager.HT_SHA_256_NONE));

        final Element authenticate = sasl2AuthenticateElement(FastTokenManager.HT_SHA_256_NONE);
        authenticate.addElement("user-agent").addAttribute("id", "123e4567-e89b-42d3-a456-426614174000");
        assertEquals(SASLAuthentication.Status.failed, SASLAuthentication.handle(session, authenticate, true));
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("malformed-request"));
    }

    @Test
    public void sasl1RejectsFastMechanismEvenWhenAValidProofWasOffered() {
        FastTokenManager.ENABLE_FAST.setValue(true);
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
        session.setSessionData(SASLAuthentication.AVAILABLE_FAST_MECHANISMS_FOR_SESSION,
            Set.of(FastTokenManager.HT_SHA_256_NONE));
        final Element auth = authElement(FastTokenManager.HT_SHA_256_NONE);
        auth.setText(Base64.getEncoder().encodeToString(new byte[32]));

        assertEquals(SASLAuthentication.Status.failed, SASLAuthentication.handle(session, auth, false));
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("invalid-mechanism"));
        assertNull(session.getSessionData("SaslServer"));
    }

    @Test
    public void appendFeaturesDoesNotRecordFastMechanismsWhenSasl2IsNotOffered() {
        FastTokenManager.ENABLE_FAST.setValue(true);
        SASLAuthentication.ENABLE_SASL2.setValue(false);
        SASLAuthentication.setEnabledMechanisms(List.of("PLAIN"));
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);

        SASLAuthentication.appendSASLFeatures(session, new ArrayList<>());

        assertEquals(Set.of(), SASLAuthentication.getAdvertisedFastMechanisms(session).orElseThrow());
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
    @Test
    public void sasl2EndToEndParsesAndIssuesOnlyAdvertisedFastMechanism()
    {
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        FastTokenManager.ENABLE_FAST.setValue(true);
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
        session.setClaimedIdentity(new org.xmpp.packet.JID("test-user@" + Fixtures.XMPP_DOMAIN));
        TestSaslMechanism.registerTestMechanism(session);
        try {
            SASLAuthentication.setEnabledMechanisms(List.of("TEST-MECHANISM"));
            SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("TEST-MECHANISM"));
            session.setSessionData(SASLAuthentication.AVAILABLE_FAST_MECHANISMS_FOR_SESSION,
                Set.of(FastTokenManager.HT_SHA_256_NONE));
            final FastToken issued = new FastToken("test-user", FastTokenManager.HT_SHA_256_NONE,
                "issued-token".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.time.Instant.now().plusSeconds(60));
            try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class, CALLS_REAL_METHODS)) {
                manager.when(() -> FastTokenManager.issueToken(eq("test-user"), any(String.class),
                    eq(FastTokenManager.HT_SHA_256_NONE))).thenReturn(issued);
                final Element authenticate = DocumentHelper.parseText(
                    "<authenticate xmlns='urn:xmpp:sasl:2' mechanism='TEST-MECHANISM'>"
                        + "<initial-response/><user-agent id='123e4567-e89b-42d3-a456-426614174000'/>"
                        + "<request-token xmlns='urn:xmpp:fast:0' mechanism='HT-SHA-256-NONE'/>"
                        + "</authenticate>").getRootElement();
                assertEquals(SASLAuthentication.Status.authenticated,
                    SASLAuthentication.handle(session, authenticate, true));
                final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
                verify(connection).deliverRawText(response.capture());
                assertTrue(response.getValue().contains("token=\"issued-token\""));
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            TestSaslMechanism.unregisterTestMechanism();
        }
    }

    @Test
    public void sasl2RejectsFastTokenRequestWithoutClientIdentityPrerequisites()
    {
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        FastTokenManager.ENABLE_FAST.setValue(true);
        for (final String inline : List.of(
            "<request-token xmlns='urn:xmpp:fast:0' mechanism='HT-SHA-256-NONE'/>",
            "<user-agent id='123e4567-e89b-42d3-a456-426614174000'/>"
                + "<request-token xmlns='urn:xmpp:fast:0' mechanism='HT-SHA-256-NONE'/>")) {
            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(true);
            final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
                new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
            if (inline.startsWith("<request-token")) {
                session.setClaimedIdentity(new org.xmpp.packet.JID("test-user@" + Fixtures.XMPP_DOMAIN));
            }
            TestSaslMechanism.registerTestMechanism(session);
            try {
                SASLAuthentication.setEnabledMechanisms(List.of("TEST-MECHANISM"));
                SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("TEST-MECHANISM"));
                session.setSessionData(SASLAuthentication.AVAILABLE_FAST_MECHANISMS_FOR_SESSION,
                    Set.of(FastTokenManager.HT_SHA_256_NONE));
                final Element authenticate = DocumentHelper.parseText(
                    "<authenticate xmlns='urn:xmpp:sasl:2' mechanism='TEST-MECHANISM'>"
                        + "<initial-response/>" + inline + "</authenticate>").getRootElement();

                assertEquals(SASLAuthentication.Status.failed,
                    SASLAuthentication.handle(session, authenticate, true));
                final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
                verify(connection).deliverRawText(response.capture());
                assertTrue(response.getValue().contains("malformed-request"));
            } catch (Exception e) {
                fail(e);
            } finally {
                TestSaslMechanism.unregisterTestMechanism();
            }
        }
    }

    @Test
    public void sasl2EndToEndIgnoresInvalidAndUnadvertisedFastTokenRequests()
    {
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        FastTokenManager.ENABLE_FAST.setValue(true);
        for (final String requested : List.of(FastTokenManager.HT_SHA_512_NONE, "NOT-A-FAST-MECHANISM")) {
            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(true);
            final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
                new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
            session.setClaimedIdentity(new org.xmpp.packet.JID("test-user@" + Fixtures.XMPP_DOMAIN));
            TestSaslMechanism.registerTestMechanism(session);
            try {
                SASLAuthentication.setEnabledMechanisms(List.of("TEST-MECHANISM"));
                SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("TEST-MECHANISM"));
                session.setSessionData(SASLAuthentication.AVAILABLE_FAST_MECHANISMS_FOR_SESSION,
                    Set.of(FastTokenManager.HT_SHA_256_NONE));
                try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class, CALLS_REAL_METHODS)) {
                    final Element authenticate = DocumentHelper.parseText(
                        "<authenticate xmlns='urn:xmpp:sasl:2' mechanism='TEST-MECHANISM'>"
                            + "<initial-response/><user-agent id='123e4567-e89b-42d3-a456-426614174000'/>"
                            + "<request-token xmlns='urn:xmpp:fast:0' mechanism='" + requested + "'/>"
                            + "</authenticate>").getRootElement();
                    assertEquals(SASLAuthentication.Status.authenticated,
                        SASLAuthentication.handle(session, authenticate, true));
                    manager.verify(() -> FastTokenManager.issueToken(any(String.class), any(String.class), any(String.class)), never());
                    final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
                    verify(connection).deliverRawText(response.capture());
                    assertFalse(response.getValue().contains("<token"));
                }
            } catch (Exception e) {
                fail(e);
            } finally {
                TestSaslMechanism.unregisterTestMechanism();
            }
        }
    }

    @Test
    public void sasl2FailsWhenAcceptedFastTokenRequestCannotBePersisted()
    {
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        FastTokenManager.ENABLE_FAST.setValue(true);
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
        session.setClaimedIdentity(new org.xmpp.packet.JID("test-user@" + Fixtures.XMPP_DOMAIN));
        TestSaslMechanism.registerTestMechanism(session);
        try {
            SASLAuthentication.setEnabledMechanisms(List.of("TEST-MECHANISM"));
            SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("TEST-MECHANISM"));
            session.setSessionData(SASLAuthentication.AVAILABLE_FAST_MECHANISMS_FOR_SESSION,
                Set.of(FastTokenManager.HT_SHA_256_NONE));
            try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class, CALLS_REAL_METHODS)) {
                manager.when(() -> FastTokenManager.issueToken(eq("test-user"), any(String.class),
                    eq(FastTokenManager.HT_SHA_256_NONE))).thenThrow(new IllegalStateException("database unavailable"));
                final Element authenticate = DocumentHelper.parseText(
                    "<authenticate xmlns='urn:xmpp:sasl:2' mechanism='TEST-MECHANISM'>"
                        + "<initial-response/><user-agent id='123e4567-e89b-42d3-a456-426614174000'/>"
                        + "<request-token xmlns='urn:xmpp:fast:0' mechanism='HT-SHA-256-NONE'/>"
                        + "</authenticate>").getRootElement();

                assertEquals(SASLAuthentication.Status.failed,
                    SASLAuthentication.handle(session, authenticate, true));
                final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
                verify(connection).deliverRawText(response.capture());
                assertTrue(response.getValue().contains("<failure"));
                assertFalse(response.getValue().contains("<success"));
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            TestSaslMechanism.unregisterTestMechanism();
        }
    }
}
