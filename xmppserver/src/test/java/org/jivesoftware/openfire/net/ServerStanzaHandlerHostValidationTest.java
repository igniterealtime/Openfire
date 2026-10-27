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
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies the validation of the 'to' attribute of stream headers that are sent by remote servers (OF-2643).
 *
 * When validation is enabled, a stream that names a domain that is not serviced by this server is refused with a
 * host-unknown stream error. Serviced domains are the XMPP domain of this server, subdomains of it that have a
 * component route, and domains for which trunking is enabled. A stream header that has no 'to' attribute is accepted,
 * as servers that use legacy Server Dialback omit it.
 *
 * Stream headers are validated both when the stream is first opened, and when it is restarted after TLS has been
 * negotiated: RFC 6120 section 5.4.3.3 requires values obtained before TLS took effect to be discarded, which makes the
 * header that follows TLS negotiation the one that counts.
 */
public class ServerStanzaHandlerHostValidationTest
{
    private static final String COMPONENT_DOMAIN = "conference." + Fixtures.XMPP_DOMAIN;

    private static final String TRUNKED_DOMAIN = "trunked.example.net";

    private static final String UNSERVICED_DOMAIN = "unserviced.example.com";

    private Connection connection;

    private RoutingTable routingTable;

    private RecordingServerStanzaHandler handler;

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

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        routingTable = Fixtures.mockRoutingTable();
        doReturn(true).when(routingTable).hasComponentRoute(new JID(COMPONENT_DOMAIN));
        doReturn(routingTable).when(xmppServer).getRoutingTable();
        XMPPServer.setInstance(xmppServer);

        connection = mock(Connection.class, withSettings().lenient());
        handler = new RecordingServerStanzaHandler(mock(PacketRouter.class), connection);
        reader = newPacketReader();
    }

    /**
     * Asserts that validation is disabled by default, so that existing installations keep accepting the connections
     * that they accepted before validation was introduced.
     */
    @Test
    public void testValidationIsDisabledByDefault()
    {
        assertFalse(handler.validateHost());
    }

    /**
     * Asserts that, with validation enabled, a stream that names a domain that is not serviced is refused with a
     * host-unknown stream error before a session is created.
     */
    @Test
    public void testInitialStreamToUnservicedDomainIsRefused() throws Exception
    {
        // Setup test fixture.
        ServerStanzaHandler.VALIDATE_HOST.setValue(true);

        // Execute system under test.
        handler.initiateSession(streamHeader(UNSERVICED_DOMAIN), reader);

        // Verify result.
        assertEquals(0, handler.sessionsCreated, "No session must be created for a stream that names a domain that is not serviced.");
        assertTrue(deliveredText().contains("host-unknown"), "The peer must be told that the host is unknown.");
        assertTrue(deliveredText().contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The refusal must be sent from the XMPP domain of this server.");
        verify(connection).close();
    }

    /**
     * Asserts that, with validation disabled, a stream that names a domain that is not serviced is accepted, as it was
     * before validation was introduced.
     */
    @Test
    public void testInitialStreamToUnservicedDomainIsAcceptedWhenValidationIsDisabled() throws Exception
    {
        // Execute system under test.
        handler.initiateSession(streamHeader(UNSERVICED_DOMAIN), reader);

        // Verify result.
        assertEquals(1, handler.sessionsCreated, "A session must be created when validation is disabled.");
    }

    /**
     * Asserts that, with validation enabled, streams that name a serviced domain are accepted.
     */
    @ParameterizedTest
    @ValueSource(strings = {Fixtures.XMPP_DOMAIN, COMPONENT_DOMAIN, TRUNKED_DOMAIN})
    public void testInitialStreamToServicedDomainIsAccepted(final String domain) throws Exception
    {
        // Setup test fixture.
        ServerStanzaHandler.VALIDATE_HOST.setValue(true);
        Trunking.ENABLED.setValue(true);
        Trunking.ALLOWABLE_DOMAINS.setValue(List.of(TRUNKED_DOMAIN));

        // Execute system under test.
        handler.initiateSession(streamHeader(domain), reader);

        // Verify result.
        assertEquals(1, handler.sessionsCreated, "A session must be created for a stream that names '" + domain + "'.");
    }

    /**
     * Asserts that, with validation enabled, a stream that does not name a domain at all is accepted, as servers that
     * use legacy Server Dialback omit the 'to' attribute.
     */
    @Test
    public void testInitialStreamWithoutToIsAccepted() throws Exception
    {
        // Setup test fixture.
        ServerStanzaHandler.VALIDATE_HOST.setValue(true);

        // Execute system under test.
        handler.initiateSession(streamHeader(null), reader);

        // Verify result.
        assertEquals(1, handler.sessionsCreated, "A session must be created for a stream that has no 'to' attribute.");
    }

    /**
     * Asserts that, with validation disabled, a stream that names a domain that is not serviced, and that is refused
     * for another reason, is answered from the XMPP domain.
     */
    @Test
    public void testStreamToUnservicedDomainRefusedForOtherReasonIsRefusedFromOwnDomain() throws Exception
    {
        // Execute system under test.
        handler.initiateSession(streamHeaderWithNamespace(UNSERVICED_DOMAIN, "jabber:client"), reader);

        // Verify result.
        final String delivered = deliveredText();
        assertTrue(delivered.contains("invalid-namespace"), "The stream must be refused for its namespace.");
        assertTrue(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The refusal must be sent from the XMPP domain of this server.");
        assertFalse(delivered.contains("from=\"" + UNSERVICED_DOMAIN + "\""), "The refusal must not be sent from the domain that is not serviced.");
    }

    /**
     * Asserts that a stream that is refused for a reason other than its host is answered from the domain that it names,
     * when that domain is serviced.
     */
    @Test
    public void testStreamToServicedDomainRefusedForOtherReasonIsRefusedFromThatDomain() throws Exception
    {
        // Execute system under test.
        handler.initiateSession(streamHeaderWithNamespace(COMPONENT_DOMAIN, "jabber:client"), reader);

        // Verify result.
        final String delivered = deliveredText();
        assertTrue(delivered.contains("invalid-namespace"), "The stream must be refused for its namespace.");
        assertTrue(delivered.contains("from=\"" + COMPONENT_DOMAIN + "\""), "The refusal must be sent from the serviced domain that the peer addressed.");
    }

    /**
     * Asserts that, with validation enabled, a stream that names a serviced domain before TLS is negotiated, but a
     * domain that is not serviced after, is refused with a host-unknown stream error.
     */
    @Test
    public void testStreamAfterTlsToUnservicedDomainIsRefused() throws Exception
    {
        // Setup test fixture.
        ServerStanzaHandler.VALIDATE_HOST.setValue(true);
        handler.initiateSession(streamHeader(Fixtures.XMPP_DOMAIN), reader);
        handler.startedTLS = true;

        // Execute system under test.
        handler.initiateSession(streamHeader(UNSERVICED_DOMAIN), reader);

        // Verify result.
        assertEquals(1, handler.sessionsCreated, "The session must not be re-created for a stream that names a domain that is not serviced.");
        assertTrue(deliveredText().contains("host-unknown"), "The peer must be told that the host is unknown.");
        assertFalse(deliveredText().contains("from=\"" + UNSERVICED_DOMAIN + "\""), "The refusal must not be sent from the domain that is not serviced.");
        verify(connection).close();
    }

    /**
     * Asserts that, with validation enabled, a stream that names a serviced domain after TLS is negotiated is
     * accepted. Peers that use trunking typically negotiate TLS before authenticating with Server Dialback, which
     * makes this the path by which trunked domains are normally validated.
     */
    @ParameterizedTest
    @ValueSource(strings = {Fixtures.XMPP_DOMAIN, COMPONENT_DOMAIN, TRUNKED_DOMAIN})
    public void testStreamAfterTlsToServicedDomainIsAccepted(final String domain) throws Exception
    {
        // Setup test fixture.
        ServerStanzaHandler.VALIDATE_HOST.setValue(true);
        Trunking.ENABLED.setValue(true);
        Trunking.ALLOWABLE_DOMAINS.setValue(List.of(TRUNKED_DOMAIN));
        handler.initiateSession(streamHeader(domain), reader);
        handler.startedTLS = true;

        // Execute system under test.
        handler.initiateSession(streamHeader(domain), reader);

        // Verify result.
        assertEquals(2, handler.sessionsCreated, "The session must be re-created for the stream that follows TLS negotiation, when it names '" + domain + "'.");
        assertFalse(deliveredText().contains("host-unknown"), "The stream must not be refused.");
    }

    /**
     * Asserts that, with validation disabled, a stream that names a domain that is not serviced after TLS is
     * negotiated is accepted, as it was before validation was introduced.
     */
    @Test
    public void testStreamAfterTlsToUnservicedDomainIsAcceptedWhenValidationIsDisabled() throws Exception
    {
        // Setup test fixture.
        handler.initiateSession(streamHeader(Fixtures.XMPP_DOMAIN), reader);
        handler.startedTLS = true;

        // Execute system under test.
        handler.initiateSession(streamHeader(UNSERVICED_DOMAIN), reader);

        // Verify result.
        assertEquals(2, handler.sessionsCreated, "The session must be re-created when validation is disabled.");
    }

    /**
     * Returns an opening stream header as sent by a remote server.
     *
     * @param to the value of the 'to' attribute, or null to omit the attribute.
     * @return a stream header.
     */
    private static String streamHeader(final String to)
    {
        return streamHeaderWithNamespace(to, "jabber:server");
    }

    /**
     * Returns an opening stream header as sent by a remote server, with the provided content namespace.
     *
     * @param to the value of the 'to' attribute, or null to omit the attribute.
     * @param namespace the content namespace.
     * @return a stream header.
     */
    private static String streamHeaderWithNamespace(final String to, final String namespace)
    {
        return "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='" + namespace + "' xmlns:db='jabber:server:dialback' from='remote.example.org'"
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
     * Returns a namespace-aware packet reader, configured as the production code configures its own.
     *
     * @return a packet reader.
     */
    private static XMPPPacketReader newPacketReader() throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
        return reader;
    }

    /**
     * A {@link ServerStanzaHandler} that replaces the creation of a session with a stub, and counts how often a session
     * is created. A real session requires collaborators (an identity store, a session manager) that are not what these
     * tests are about.
     */
    private static class RecordingServerStanzaHandler extends ServerStanzaHandler
    {
        private int sessionsCreated;

        RecordingServerStanzaHandler(final PacketRouter router, final Connection connection)
        {
            super(router, connection, false);
        }

        @Override
        void createSession(final String serverName, final XmlPullParser xpp, final Connection connection)
        {
            sessionsCreated++;
            final LocalIncomingServerSession stub = mock(LocalIncomingServerSession.class, withSettings().lenient());
            doReturn(new BasicStreamIDFactory().createStreamID()).when(stub).getStreamID();
            doReturn(Locale.ENGLISH).when(stub).getLanguage();
            doReturn(List.of()).when(stub).getAvailableStreamFeatures();
            session = stub;
        }
    }
}
