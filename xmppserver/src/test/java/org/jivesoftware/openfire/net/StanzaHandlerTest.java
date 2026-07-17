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

import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.sasl.AnonymousSaslServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StanzaHandler}, focusing on the {@code startedSASL} flag lifecycle.
 */
public class StanzaHandlerTest
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
        AnonymousSaslServer.ENABLED.setValue(true);
        SASLAuthentication.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL", "ANONYMOUS"));
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);
    }

    /**
     * Verifies that the {@code startedSASL} flag is reset to {@code false} after a successful SASL1 authentication.
     * In SASL1, the flag is reset in {@link StanzaHandler#initiateSession} when the client reopens the stream.
     * This test directly exercises the flag-reset logic in {@code initiateSession} without going through real SASL.
     */
    @Test
    public void startedSASL_shouldBeResetAfterSasl1Authentication() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        when(connection.getAdditionalNamespaces()).thenReturn(java.util.Collections.emptySet());

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        final ClientStanzaHandler handler = new ClientStanzaHandler(mock(PacketRouter.class), connection) {
            @Override
            protected void saslSuccessful() {
                // No-op: skip stream header/features delivery in unit test.
            }
        };
        handler.setSession(session);

        // Manually prime the handler as if a SASL1 <auth> was already processed and authentication succeeded.
        handler.sessionCreated = true;
        handler.startedSASL = true;
        handler.usingSASL2 = false;
        handler.saslStatus = SASLAuthentication.Status.authenticated;

        // Execute system under test: simulate the stream restart that follows SASL1 success.
        handler.initiateSession("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>", new XMPPPacketReader());

        // Verify result: startedSASL must be false after SASL1 stream restart.
        assertFalse(handler.isStartedSASL(),
            "Expected startedSASL to be reset to false after SASL1 stream restart, but it was still true.");
    }

    /**
     * Verifies that the {@code startedSASL} flag is reset to {@code false} after a successful SASL2 authentication
     * without Bind2 (synchronous path), via the multi-step {@code <response>} path in {@code processStanza}.
     * This exercises the existing correct reset at the {@code authenticated} branch.
     */
    @Test
    public void startedSASL_shouldBeResetAfterSasl2AuthenticationWithoutBind2() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        when(connection.getAdditionalNamespaces()).thenReturn(java.util.Collections.emptySet());

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        final ClientStanzaHandler handler = new ClientStanzaHandler(mock(PacketRouter.class), connection) {
            @Override
            protected void sasl2Successful() {
                // No-op: skip features delivery in unit test.
            }
        };
        handler.setSession(session);

        // Manually prime the handler as if a SASL2 <authenticate> was already processed (multi-step, awaiting response),
        // and the SaslServer is ready to complete on the next response.
        handler.startedSASL = true;
        handler.usingSASL2 = true;
        handler.saslStatus = SASLAuthentication.Status.needResponse;

        // Stub the SaslServer to complete immediately with no Bind2 request in session data.
        final javax.security.sasl.SaslServer saslServer = mock(javax.security.sasl.SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenReturn(new byte[0]);
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getAuthorizationID()).thenReturn(null);
        when(saslServer.getMechanismName()).thenReturn("ANONYMOUS");
        session.setSessionData("SaslServer", saslServer);
        // No bind2-request in session data: the non-Bind2 synchronous path is taken.

        // Execute system under test: process a <response/> that completes SASL2 without Bind2.
        final String responseStanza = "<response xmlns='" + SASLAuthentication.SASL_NAMESPACE + "'/>";
        handler.processStanza(responseStanza, new XMPPPacketReader());

        // Verify result: startedSASL must be false after synchronous SASL2 (no Bind2) completion.
        assertFalse(handler.isStartedSASL(),
            "Expected startedSASL to be reset to false after synchronous SASL2 (no Bind2) completion, but it was still true.");
    }

    /**
     * Verifies that the {@code startedSASL} flag is reset to {@code false} after a successful SASL2+Bind2
     * authentication where resource binding completes synchronously (immediately-completed future).
     *
     * <p>This is a regression test for the bug where {@code startedSASL} was never reset when
     * {@link SASLAuthentication#handle} returns {@link SASLAuthentication.Status#authenticatedAwaitingFeatures}
     * (the Bind2 async path), leaving the flag {@code true} indefinitely.</p>
     */
    @Test
    public void startedSASL_shouldBeResetAfterSasl2Bind2Authentication() throws Exception
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        when(connection.getAdditionalNamespaces()).thenReturn(java.util.Collections.emptySet());

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        final String anonymousUsername = session.getAnonymousUsername();

        // Set up a Bind2Request so the SASL2+Bind2 async path is taken.
        final Bind2Request bind2Request = mock(Bind2Request.class);
        when(bind2Request.generateResourceString(any())).thenReturn(anonymousUsername);
        when(bind2Request.processFeatureRequests(any(), any())).thenReturn(null);
        session.setSessionData("bind2-request", bind2Request);

        // Stub SessionManager.bindResource to complete successfully (synchronously).
        final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        when(sessionManager.bindResource(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(SessionManager.BindResult.BOUND));

        // Stub the SaslServer to complete immediately; bind2-request is already in session data.
        final javax.security.sasl.SaslServer saslServer = mock(javax.security.sasl.SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenReturn(new byte[0]);
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getAuthorizationID()).thenReturn(null);
        when(saslServer.getMechanismName()).thenReturn("ANONYMOUS");
        session.setSessionData("SaslServer", saslServer);

        final ClientStanzaHandler handler = new ClientStanzaHandler(mock(PacketRouter.class), connection);
        handler.setSession(session);

        // Manually prime the handler as if a SASL2 <authenticate> was already processed (multi-step, awaiting response).
        handler.startedSASL = true;
        handler.usingSASL2 = true;
        handler.saslStatus = SASLAuthentication.Status.needResponse;

        // Execute system under test: process a <response/> that completes SASL2 with Bind2 present.
        // handle() will return authenticatedAwaitingFeatures; startedSASL must be reset.
        final String responseStanza = "<response xmlns='" + SASLAuthentication.SASL_NAMESPACE + "'/>";
        handler.processStanza(responseStanza, new XMPPPacketReader());

        // Verify result: startedSASL must be false after SASL2+Bind2 async completion.
        assertFalse(handler.isStartedSASL(),
            "Expected startedSASL to be reset to false after SASL2+Bind2 async completion, but it was still true.");
    }
}
