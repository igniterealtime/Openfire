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
package org.jivesoftware.openfire.handler;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link IQBindHandler} hands the unacknowledged stanzas of a detached session that a bind replaced to
 * the session manager only after it delivered the result of that bind, so that the client receives that result first.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3442">OF-3442: Handle all unacknowledged stanzas when a Stream Management session closes without being resumed</a>
 */
public class IQBindHandlerReplacedSessionTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);

    private SessionManager sessionManager;
    private IQBindHandler handler;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        final XMPPServer server = Fixtures.mockXMPPServer();
        sessionManager = server.getSessionManager();
        //noinspection deprecation
        XMPPServer.setInstance(server);

        handler = new IQBindHandler();
        handler.initialize(server);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a successful bind delivers its result before the stanzas of a detached session that it replaced are
     * handed over to be handled.
     */
    @Test
    public void stanzasOfReplacedSessionAreHandledAfterBindResult() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(AuthToken.generateUserToken(FULL_JID.getNode())).when(session).getAuthToken();
        doReturn(session).when(sessionManager).getSession(FULL_JID);
        doReturn(CompletableFuture.completedFuture(SessionManager.BindResult.BOUND)).when(sessionManager).bindResource(any(), any(), any());
        final List<String> events = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { if (invocation.getArgument(0) instanceof IQ iq && iq.getType() == IQ.Type.result && "bind-1".equals(iq.getID())) { events.add("bind result"); } return null; }).when(session).process(any());
        doAnswer(invocation -> { events.add("hand over"); return null; }).when(sessionManager).handleStanzasOfReplacedDetachedSession(session);
        final IQ request = new IQ(IQ.Type.set, "bind-1");
        request.setFrom(FULL_JID);
        request.setChildElement("bind", "urn:ietf:params:xml:ns:xmpp-bind").addElement("resource").setText(FULL_JID.getResource());

        // Execute system under test.
        handler.handleIQ(request);

        // Verify result.
        assertEquals(List.of("bind result", "hand over"), events, "The stanzas of a replaced session must be handed over once, after the result of the bind was delivered.");
    }

    /**
     * Verifies that the stanzas of a replaced session are still handed over when delivering the result of the bind
     * fails, as they would otherwise never be handled.
     */
    @Test
    public void stanzasOfReplacedSessionAreHandedOverWhenDeliveringBindResultFails() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(AuthToken.generateUserToken(FULL_JID.getNode())).when(session).getAuthToken();
        doReturn(session).when(sessionManager).getSession(FULL_JID);
        doReturn(CompletableFuture.completedFuture(SessionManager.BindResult.BOUND)).when(sessionManager).bindResource(any(), any(), any());
        doThrow(new IllegalStateException("Simulated failure to deliver the result of the bind.")).when(session).process(any());
        final IQ request = new IQ(IQ.Type.set, "bind-3");
        request.setFrom(FULL_JID);
        request.setChildElement("bind", "urn:ietf:params:xml:ns:xmpp-bind").addElement("resource").setText(FULL_JID.getResource());

        // Execute system under test.
        handler.handleIQ(request);

        // Verify result.
        verify(sessionManager, times(1).description("The stanzas of a replaced session must be handed over, also when delivering the result of the bind fails.")).handleStanzasOfReplacedDetachedSession(session);
    }

    /**
     * Verifies that a bind that is refused does not hand over the stanzas of a replaced session, as the session did not
     * get the resource.
     */
    @Test
    public void stanzasOfReplacedSessionAreNotHandledForRefusedBind() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(AuthToken.generateUserToken(FULL_JID.getNode())).when(session).getAuthToken();
        doReturn(session).when(sessionManager).getSession(FULL_JID);
        doReturn(CompletableFuture.completedFuture(SessionManager.BindResult.CONFLICT)).when(sessionManager).bindResource(any(), any(), any());
        final IQ request = new IQ(IQ.Type.set, "bind-2");
        request.setFrom(FULL_JID);
        request.setChildElement("bind", "urn:ietf:params:xml:ns:xmpp-bind").addElement("resource").setText(FULL_JID.getResource());

        // Execute system under test.
        handler.handleIQ(request);

        // Verify result.
        verify(sessionManager, never().description("A refused bind must not hand over the stanzas of a replaced session.")).handleStanzasOfReplacedDetachedSession(any());
    }
}
