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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that {@link StanzaHandler} delivers a stream error to the peer when it refuses a stream header that does
 * not declare a content namespace.
 *
 * A peer can open a stream with a header that names a stream namespace but no default (content) namespace, such as
 * {@code <stream:stream xmlns:stream='http://etherx.jabber.org/streams' to='example.org' version='1.0'>}. When such a
 * header is refused, building the stream that carries the error used to copy the content namespace of the refused
 * header, which is absent here, so the peer never received the stream error.
 *
 * These tests trigger the refusal with a stream namespace that is not the one RFC 6120 section 4.8.1 requires. That is
 * a reason to refuse which is independent of the missing content namespace itself, so the tests exercise the
 * error-building defect without asserting that a header which omits the content namespace is refused in its own right.
 *
 * @author Matthew Vivian
 */
public class StanzaHandlerRefuseStreamTest
{
    /**
     * A stream namespace that is not the one that RFC 6120 section 4.8.1 requires, used to have a stream header
     * refused for a reason that does not depend on the content namespace that the header declares.
     */
    private static final String WRONG_STREAM_NAMESPACE = "http://example.org/not-the-stream-namespace";

    private Connection connection;

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

        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
    }

    /**
     * Asserts that a client stream header that declares no content namespace, and that is refused, is answered with the
     * stream error, opened in the namespace of the handler, and that the connection is then closed.
     */
    @Test
    public void testClientStreamWithoutNamespaceIsRefusedWithError() throws Exception
    {
        // Setup test fixture.
        final ClientStanzaHandler handler = new ClientStanzaHandler(mock(PacketRouter.class), connection);

        // Execute system under test.
        handler.initiateSession("<stream:stream xmlns:stream='" + WRONG_STREAM_NAMESPACE + "' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>", reader);

        // Verify result.
        final String delivered = deliveredText(connection);
        assertTrue(delivered.contains("<stream:stream"), "The refusal must be sent on a stream that is opened for that purpose.");
        assertTrue(delivered.contains("xmlns=\"jabber:client\""), "The refusal stream must be opened in the namespace of the handler, as the refused header declared none.");
        assertTrue(delivered.contains("<invalid-namespace xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\""), "The stream must carry the invalid-namespace stream error.");
        verify(connection, times(1).description("The connection must be closed after the refusal.")).close();
        assertNull(handler.session, "No session must be created for a refused stream.");
    }

    /**
     * Asserts that a server stream header that declares no content namespace, and that is refused, is answered with the
     * stream error, opened in the namespace of the handler, and that the connection is then closed. This is the same
     * defect as for client streams: the error is built in {@link StanzaHandler}, which every connection type shares.
     */
    @Test
    public void testServerStreamWithoutNamespaceIsRefusedWithError() throws Exception
    {
        // Setup test fixture.
        final ServerStanzaHandler handler = new ServerStanzaHandler(mock(PacketRouter.class), connection, false);

        // Execute system under test.
        handler.initiateSession("<stream:stream xmlns:stream='" + WRONG_STREAM_NAMESPACE + "' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>", reader);

        // Verify result.
        final String delivered = deliveredText(connection);
        assertTrue(delivered.contains("<stream:stream"), "The refusal must be sent on a stream that is opened for that purpose.");
        assertTrue(delivered.contains("xmlns=\"jabber:server\""), "The refusal stream must be opened in the namespace of the handler, as the refused header declared none.");
        assertTrue(delivered.contains("<invalid-namespace xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\""), "The stream must carry the invalid-namespace stream error.");
        verify(connection, times(1).description("The connection must be closed after the refusal.")).close();
        assertNull(handler.session, "No session must be created for a refused stream.");
    }

    /**
     * Returns all text that was delivered to the peer, concatenated.
     *
     * @param connection the connection that text was delivered to.
     * @return delivered text.
     */
    private static String deliveredText(final Connection connection)
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Text must have been delivered to the peer.")).deliverRawText(captor.capture());
        return String.join("", captor.getAllValues());
    }
}
