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
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link StanzaHandler} does not let Stream Management data that arrives over a connection affect a
 * session that no longer uses that connection, as happens when the session is resumed on another connection while the
 * former one is still delivering data (XEP-0198 § 5).
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3443">OF-3443: Prevent resumption of a Stream Management session that was closed with a stream error</a>
 */
public class StanzaHandlerSupersededConnectionTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);

    /**
     * An acknowledgement with a negative 'h', which Stream Management answers with a stream error.
     */
    private static final String INVALID_ACK = "<a xmlns='" + StreamManager.NAMESPACE_V3 + "' h='-1'/>";

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        //noinspection deprecation
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that an invalid acknowledgement that arrives over a connection that the session no longer uses neither
     * disables resumption of the session, nor ends the stream that the session now uses.
     */
    @Test
    public void streamManagementDataOverSupersededConnectionDoesNotAffectSession() throws Exception
    {
        // Setup test fixture.
        final Connection current = connection();
        final Connection superseded = connection();
        final LocalClientSession session = resumableSession(current);
        final ClientStanzaHandler handler = handler(superseded, session);

        // Execute system under test.
        handler.process(INVALID_ACK, new XMPPPacketReader());

        // Verify result.
        assertTrue(session.getStreamManager().getResume(), "Data received over a superseded connection must not disable resumption of the session.");
        verify(current, never().description("Data received over a superseded connection must not be answered over the connection of the session.")).deliverRawText(any());
        verify(current, never().description("Data received over a superseded connection must not close the connection of the session.")).close();
    }

    /**
     * Verifies that Stream Management data that arrives over a connection of a session that is detached, and therefore
     * has no connection, is not processed by the Stream Management of that session.
     */
    @Test
    public void streamManagementDataForDetachedSessionIsNotProcessed() throws Exception
    {
        // Setup test fixture.
        final StreamManager streamManager = mock(StreamManager.class);
        final LocalClientSession session = mock(LocalClientSession.class);
        when(session.getConnection()).thenReturn(null);
        when(session.getStreamManager()).thenReturn(streamManager);
        final ClientStanzaHandler handler = handler(connection(), session);

        // Execute system under test.
        handler.process(INVALID_ACK, new XMPPPacketReader());

        // Verify result.
        verify(streamManager, never().description("Data received for a detached session must not reach its Stream Management.")).process(any());
    }

    /**
     * Verifies that Stream Management data that arrives over the connection of the session is still processed: an
     * invalid acknowledgement ends the stream with an error, and disables resumption.
     */
    @Test
    public void streamManagementDataOverCurrentConnectionIsProcessed() throws Exception
    {
        // Setup test fixture.
        final Connection current = connection();
        final LocalClientSession session = resumableSession(current);
        final ClientStanzaHandler handler = handler(current, session);

        // Execute system under test.
        handler.process(INVALID_ACK, new XMPPPacketReader());

        // Verify result.
        assertFalse(session.getStreamManager().getResume(), "An invalid acknowledgement over the connection of the session must disable resumption.");
        verify(current, atLeastOnce().description("An invalid acknowledgement over the connection of the session must close that connection.")).close();
    }

    private static Connection connection()
    {
        final Connection connection = mock(Connection.class);
        when(connection.getAdditionalNamespaces()).thenReturn(Collections.emptySet());
        return connection;
    }

    /**
     * A handler that reads from the provided connection, for the provided session, past the opening of the stream.
     */
    private static ClientStanzaHandler handler(final Connection connection, final LocalClientSession session)
    {
        final ClientStanzaHandler handler = new ClientStanzaHandler(mock(PacketRouter.class), connection);
        handler.setSession(session);
        handler.sessionCreated = true;
        return handler;
    }

    /**
     * A client session that uses the provided connection, on which Stream Management is enabled with resumption.
     */
    private static LocalClientSession resumableSession(final Connection connection) throws Exception
    {
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID("test-stream-id"), Locale.ENGLISH);
        session.setAddress(FULL_JID);
        session.setAuthToken(AuthToken.generateUserToken(FULL_JID.getNode()));
        session.setStatus(Session.Status.AUTHENTICATED);

        final Element enable = DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3));
        enable.addAttribute("resume", "true");
        session.getStreamManager().process(enable);
        assertTrue(session.getStreamManager().getResume(), "Test fixture expects a resumable stream.");
        clearInvocations(connection);
        return session;
    }
}
