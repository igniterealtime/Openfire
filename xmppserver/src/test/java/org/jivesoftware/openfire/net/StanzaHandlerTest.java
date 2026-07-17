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

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.dom4j.io.XMPPPacketReader;

import javax.security.sasl.SaslServer;
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
        SASLAuthentication.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL"));
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);
    }

    /**
     * Verifies that the {@code startedSASL} flag is reset to {@code false} after a successful single-step
     * SASL2+Bind2 authentication (via {@code <authenticate>}) where resource binding completes synchronously.
     *
     * <p>This is a regression test for the bug where the async {@code whenComplete} callback in
     * {@link SASLAuthentication#authenticationSuccessful} never resets {@code startedSASL}, leaving it
     * {@code true} indefinitely after SASL2+Bind2 completes.</p>
     */
    @Test
    public void startedSASL_shouldBeResetAfterSasl2Bind2AuthenticationViaSingleStepAuthenticate() throws Exception
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

        // Set up a SaslServer that immediately completes authentication (single-step, no challenge needed).
        final SaslServer saslServer = mock(SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenReturn(new byte[0]);
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getAuthorizationID()).thenReturn(null);
        when(saslServer.getMechanismName()).thenReturn("ANONYMOUS");
        session.setSessionData("SaslServer", saslServer);

        final ClientStanzaHandler handler = new ClientStanzaHandler(mock(PacketRouter.class), connection);
        handler.setSession(session);

        // Execute system under test: process an <authenticate> that completes SASL immediately and triggers Bind2.
        final String authenticateStanza = "<authenticate xmlns='" + SASLAuthentication.SASL2_NAMESPACE + "' mechanism='ANONYMOUS'/>";
        handler.processStanza(authenticateStanza, new XMPPPacketReader());

        // Verify result: startedSASL must be false after async Bind2 completion.
        assertFalse(handler.isStartedSASL(),
            "Expected startedSASL to be reset to false after SASL2+Bind2 async completion via <authenticate>, but it was still true.");
    }
}
