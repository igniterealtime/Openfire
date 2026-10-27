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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies the validation of the 'to' attribute of stream headers that are sent by clients, which is enabled by
 * xmpp.client.validate.host.
 *
 * When validation is enabled, a stream that names a host other than the XMPP domain of this server (including a
 * subdomain of it) is refused with a host-unknown stream error. A stream header that has no 'to' attribute is
 * accepted.
 *
 * @author Matthew Vivian
 */
public class ClientStanzaHandlerHostValidationTest
{
    private static final String SUBDOMAIN = "conference." + Fixtures.XMPP_DOMAIN;

    private Connection connection;

    private StubbedSessionClientStanzaHandler handler;

    private XMPPPacketReader reader;

    @BeforeAll
    public static void setUpClass() throws Exception
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
    public void setUp() throws Exception
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        XMPPServer.setInstance(Fixtures.mockXMPPServer());

        connection = mock(Connection.class, withSettings().lenient());
        handler = new StubbedSessionClientStanzaHandler(mock(PacketRouter.class), connection);

        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
    }

    /**
     * Asserts that, with validation enabled, a stream that names the XMPP domain is accepted.
     */
    @Test
    public void testInitialStreamToXmppDomainIsAccepted() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.client.validate.host", "true");

        // Execute system under test.
        handler.initiateSession(streamHeader(Fixtures.XMPP_DOMAIN), reader);

        // Verify result.
        assertNotNull(handler.session, "A session must be created for a stream that names the XMPP domain.");
        verify(connection, never()).close();
    }

    /**
     * Asserts that, with validation enabled, a stream that names a subdomain of the XMPP domain is refused with a
     * host-unknown stream error: only the XMPP domain itself is serviced for clients.
     */
    @Test
    public void testInitialStreamToSubdomainIsRefused() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.client.validate.host", "true");

        // Execute system under test.
        handler.initiateSession(streamHeader(SUBDOMAIN), reader);

        // Verify result.
        assertNull(handler.session, "No session must be created for a stream that names a subdomain.");
        assertTrue(deliveredText().contains("<host-unknown xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\""), "The peer must be told that the host is unknown.");
        verify(connection).close();
    }

    /**
     * Asserts that, with validation enabled, a stream that does not name a host is accepted.
     */
    @Test
    public void testInitialStreamWithoutToIsAccepted() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.client.validate.host", "true");

        // Execute system under test.
        handler.initiateSession(streamHeader(null), reader);

        // Verify result.
        assertNotNull(handler.session, "A session must be created for a stream that has no 'to' attribute.");
        verify(connection, never()).close();
    }

    /**
     * Returns an opening stream header as sent by a client.
     *
     * @param to the value of the 'to' attribute, or null to omit the attribute.
     * @return a stream header.
     */
    private static String streamHeader(final String to)
    {
        return "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client'"
            + (to == null ? "" : " to='" + to + "'")
            + " version='1.0'>";
    }

    /**
     * Returns all text that was delivered to the peer, concatenated.
     *
     * @return delivered text.
     */
    private String deliveredText()
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).deliverRawText(captor.capture());
        return String.join("", captor.getAllValues());
    }

    /**
     * A {@link ClientStanzaHandler} that replaces the creation of a session with a stub. A real session requires
     * collaborators (a session manager, authentication providers) that are not what these tests are about.
     */
    private static class StubbedSessionClientStanzaHandler extends ClientStanzaHandler
    {
        StubbedSessionClientStanzaHandler(final PacketRouter router, final Connection connection)
        {
            super(router, connection);
        }

        @Override
        protected void createSession(final String serverName, final XmlPullParser xpp, final Connection connection)
        {
            final LocalClientSession stub = mock(LocalClientSession.class, withSettings().lenient());
            doReturn(new BasicStreamIDFactory().createStreamID()).when(stub).getStreamID();
            doReturn(Locale.ENGLISH).when(stub).getLanguage();
            doReturn(List.of()).when(stub).getAvailableStreamFeatures();
            session = stub;
        }
    }
}
