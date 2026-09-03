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

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmpp.packet.StreamError;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies how {@link StanzaHandler} adopts the pre-existing session that a SASL2 authentication resumed inline
 * (XEP-0198 § 9.2).
 */
public class StanzaHandlerSasl2ResumeTest
{
    /**
     * Verifies that the session referenced by the session data that {@link SASLAuthentication} provides is adopted,
     * replacing the temporary session that negotiated the SASL2 authentication.
     */
    @Test
    public void testAdoptsResumedSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession resumedSession = mock(LocalClientSession.class);
        final LocalSession temporarySession = mock(LocalSession.class);
        when(temporarySession.removeSessionData(SASLAuthentication.SASL2_RESUMED_SESSION)).thenReturn(resumedSession);
        final Connection connection = mock(Connection.class);
        final TestStanzaHandler handler = new TestStanzaHandler(mock(PacketRouter.class), connection);
        handler.setSession(temporarySession);

        // Execute system under test.
        handler.adoptSasl2ResumedSession(temporarySession);

        // Verify result.
        assertEquals(resumedSession, handler.session, "Expected the handler to have adopted the resumed session.");
        assertTrue(handler.sasl2SessionResumed, "Expected the handler to have recorded that the session was resumed.");
        verify(temporarySession).removeSessionData(SASLAuthentication.SASL2_RESUMED_SESSION);
        verify(connection, never()).close(any(StreamError.class));
    }

    /**
     * Verifies that no stream features are delivered after a session was resumed inline, as XEP-0198 § 9.2 requires.
     */
    @Test
    public void testDoesNotDeliverFeaturesAfterResume() throws Exception
    {
        // Setup test fixture.
        final LocalSession temporarySession = mock(LocalSession.class);
        when(temporarySession.removeSessionData(SASLAuthentication.SASL2_RESUMED_SESSION)).thenReturn(mock(LocalClientSession.class));
        final TestStanzaHandler handler = new TestStanzaHandler(mock(PacketRouter.class), mock(Connection.class));
        handler.setSession(temporarySession);
        handler.adoptSasl2ResumedSession(temporarySession);

        // Execute system under test.
        handler.sasl2Successful();

        // Verify result.
        assertFalse(handler.deliveredFeatures, "Expected no post-authentication stream features to be delivered after a session was resumed inline.");
    }

    /**
     * Verifies that the connection is closed, rather than served by a session that can no longer be used, when the
     * expected resumed session is absent from the session data.
     */
    @Test
    public void testClosesConnectionWhenResumedSessionIsAbsent() throws Exception
    {
        // Setup test fixture.
        final LocalSession temporarySession = mock(LocalSession.class);
        when(temporarySession.removeSessionData(SASLAuthentication.SASL2_RESUMED_SESSION)).thenReturn("not-a-session");
        final Connection connection = mock(Connection.class);
        final TestStanzaHandler handler = new TestStanzaHandler(mock(PacketRouter.class), connection);
        handler.setSession(temporarySession);

        // Execute system under test.
        handler.adoptSasl2ResumedSession(temporarySession);

        // Verify result.
        verify(connection).close(any(StreamError.class));
        assertEquals(temporarySession, handler.session, "Expected the handler to not have adopted a value that is not a session.");
    }

    /**
     * Verifies that the resumed session is adopted even when the connection transfer that preceded this already
     * replaced this handler's session, which is what {@link org.jivesoftware.openfire.Connection#reinit} does.
     */
    @Test
    public void testAdoptsResumedSessionAfterConnectionReinit() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession resumedSession = mock(LocalClientSession.class);
        final LocalSession temporarySession = mock(LocalSession.class);
        when(temporarySession.removeSessionData(SASLAuthentication.SASL2_RESUMED_SESSION)).thenReturn(resumedSession);
        final Connection connection = mock(Connection.class);
        final TestStanzaHandler handler = new TestStanzaHandler(mock(PacketRouter.class), connection);
        handler.setSession(resumedSession); // As Connection#reinit will have done.

        // Execute system under test.
        handler.adoptSasl2ResumedSession(temporarySession);

        // Verify result.
        assertEquals(resumedSession, handler.session, "Expected the handler to have adopted the resumed session.");
        verify(connection, never()).close(any(StreamError.class));
    }

    /**
     * A minimal concrete {@link StanzaHandler}, which records whether stream features were delivered.
     */
    private static class TestStanzaHandler extends StanzaHandler
    {
        private boolean deliveredFeatures = false;

        TestStanzaHandler(final PacketRouter router, final Connection connection)
        {
            super(router, connection);
        }

        @Override
        protected void deliverSasl2Features()
        {
            deliveredFeatures = true;
        }

        @Override
        boolean processUnknowPacket(final Element doc)
        {
            return false;
        }

        @Override
        void startTLS()
        {
        }

        @Override
        Namespace getNamespace()
        {
            return Namespace.get("jabber:client");
        }

        @Override
        boolean validateHost()
        {
            return false;
        }

        @Override
        boolean validateJIDs()
        {
            return false;
        }

        @Override
        void createSession(final String serverName, final XmlPullParser xpp, final Connection connection)
        {
        }
    }
}
