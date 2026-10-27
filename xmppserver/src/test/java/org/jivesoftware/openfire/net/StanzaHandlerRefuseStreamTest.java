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
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParserFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies how {@link StanzaHandler} refuses a stream header that it cannot accept: it opens a stream that carries
 * the stream error, and closes the connection without creating a session.
 */
public class StanzaHandlerRefuseStreamTest
{
    private Connection connection;

    private ClientStanzaHandler handler;

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
        handler = new ClientStanzaHandler(mock(PacketRouter.class), connection);

        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
    }

    /**
     * Asserts that a stream header with a content namespace that does not match the handler is refused with an
     * invalid-namespace stream error.
     */
    @Test
    public void testStreamWithInvalidNamespaceIsRefused() throws Exception
    {
        // Execute system under test.
        handler.initiateSession("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>", reader);

        // Verify result.
        final String delivered = deliveredText();
        assertTrue(delivered.contains("<stream:stream"), "The refusal must be sent on a stream that is opened for that purpose.");
        assertTrue(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The stream must be opened from the domain that the peer addressed.");
        assertTrue(delivered.contains("<invalid-namespace xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\""), "The stream must carry the invalid-namespace stream error.");
        verify(connection).close();
        assertNull(handler.session, "No session must be created for a refused stream.");
    }

    /**
     * Asserts that, with host validation enabled for clients, a stream header that addresses another domain is refused
     * with a host-unknown stream error.
     */
    @Test
    public void testClientStreamToUnknownHostIsRefused() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.client.validate.host", "true");

        // Execute system under test.
        handler.initiateSession("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' to='unserviced.example.com' version='1.0'>", reader);

        // Verify result.
        final String delivered = deliveredText();
        assertTrue(delivered.contains("<host-unknown xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\""), "The stream must carry the host-unknown stream error.");
        assertEquals(1, countOccurrences(delivered, "<stream:stream"), "Exactly one stream must be opened, to carry the refusal.");
        verify(connection).close();
        assertNull(handler.session, "No session must be created for a refused stream.");
    }

    /**
     * Asserts that a stream that is refused because it addresses an unknown host is opened from the XMPP domain of
     * this server, as RFC 6120 section 4.9.1.3 requires an authoritative name for the receiving entity, rather than
     * from the unknown host.
     */
    @Test
    public void testStreamToUnknownHostIsRefusedFromOwnDomain() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.client.validate.host", "true");

        // Execute system under test.
        handler.initiateSession("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' to='unserviced.example.com' version='1.0'>", reader);

        // Verify result.
        final String delivered = deliveredText();
        assertTrue(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The stream must be opened from the XMPP domain of this server.");
        assertFalse(delivered.contains("from=\"unserviced.example.com\""), "The stream must not be opened from the unknown host.");
    }

    /**
     * Asserts that a stream header that does not address a host, and that is refused, is answered with a stream that
     * is opened from the XMPP domain of this server, as RFC 6120 section 4.9.1.3 requires.
     */
    @Test
    public void testStreamWithoutToIsRefusedFromOwnDomain() throws Exception
    {
        // Execute system under test.
        handler.initiateSession("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' version='1.0'>", reader);

        // Verify result.
        final String delivered = deliveredText();
        assertTrue(delivered.contains("<invalid-namespace xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\""), "The stream must be refused.");
        assertTrue(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The stream must be opened from the XMPP domain of this server.");
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
     * Counts the occurrences of a fragment in a text.
     *
     * @param text the text to search.
     * @param fragment the fragment to count.
     * @return the number of occurrences.
     */
    private static int countOccurrences(final String text, final String fragment)
    {
        int count = 0;
        for (int index = text.indexOf(fragment); index >= 0; index = text.indexOf(fragment, index + 1)) {
            count++;
        }
        return count;
    }
}
