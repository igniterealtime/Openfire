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
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies Stream Management (XEP-0198) on connections that other servers open to Openfire, by passing the data that
 * such a server sends to a {@link ServerStanzaHandler}, and inspecting what is delivered on the connection in return.
 *
 * The connection is a mock. Authentication is not negotiated: the tests authenticate the domain of the remote server on
 * the session in the same way as dialback and SASL EXTERNAL (certificate) authentication do.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ServerStanzaHandlerStreamManagementTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final String STREAM_HEADER = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback' from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>";

    private static final QName SM_V2 = QName.get("sm", StreamManager.NAMESPACE_V2);
    private static final QName SM_V3 = QName.get("sm", StreamManager.NAMESPACE_V3);

    private Connection connection;

    private PacketRouter router;

    private LocalIncomingServerSession session;

    private ServerStanzaHandler handler;

    private XMPPPacketReader reader;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        Fixtures.clearExistingProperties();

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        final IdentityStore identityStore = mock(IdentityStore.class, withSettings().lenient());
        doReturn(Collections.emptyMap()).when(identityStore).getAllCertificates();
        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        doReturn(Connection.CompressionPolicy.disabled).when(configuration).getCompressionPolicy();
        doReturn(identityStore).when(configuration).getIdentityStore();
        connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();

        // The session that the session manager creates when the handler receives the first stream header.
        session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), REMOTE_DOMAIN);
        final SessionManager sessionManager = xmppServer.getSessionManager();
        doReturn(session).when(sessionManager).createIncomingServerSession(any(), any(), any());

        router = mock(PacketRouter.class);
        handler = new ServerStanzaHandler(router, connection, false);
        reader = newPacketReader();
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that Stream Management is advertised in the features that follow the first stream header of a server
     * if, and only if, it is enabled for servers. Servers that authenticate with dialback get no other features.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void featureIsAdvertisedOnFirstStreamDependingOnSetting(final boolean active) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(active);

        // Execute system under test.
        handler.process(STREAM_HEADER, reader);

        // Verify result.
        final List<Element> features = lastAdvertisedFeatures();
        assertEquals(active, features.stream().anyMatch(feature -> feature.getQName().equals(SM_V2)), "Version 2 of Stream Management must be advertised if, and only if, it is enabled for servers (enabled: " + active + "), but the features were: " + features);
        assertEquals(active, features.stream().anyMatch(feature -> feature.getQName().equals(SM_V3)), "Version 3 of Stream Management must be advertised if, and only if, it is enabled for servers (enabled: " + active + "), but the features were: " + features);
    }

    /**
     * Verifies that Stream Management is advertised in the features that follow the stream that a server restarts after
     * authenticating with a certificate if, and only if, it is enabled for servers.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void featureIsAdvertisedAfterCertificateAuthenticationDependingOnSetting(final boolean active) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(active);
        handler.process(STREAM_HEADER, reader);
        authenticate(ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        handler.process(STREAM_HEADER, reader);

        // Verify result.
        final List<Element> features = lastAdvertisedFeatures();
        assertTrue(features.stream().noneMatch(feature -> feature.getName().equals("mechanisms")), "The features after authentication were expected; without them this test proves nothing. The features were: " + features);
        assertEquals(active, features.stream().anyMatch(feature -> feature.getQName().equals(SM_V3)), "Stream Management must be advertised after authentication if, and only if, it is enabled for servers (enabled: " + active + "), but the features were: " + features);
    }

    /**
     * Verifies that a server cannot enable Stream Management before any of its domains is authenticated, even though
     * the feature is advertised at that time.
     */
    @Test
    public void enableBeforeAuthenticationIsRefused() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        handler.process(STREAM_HEADER, reader);

        // Execute system under test.
        handler.process("<enable xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        final Element failed = lastDelivered();
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), failed.getQName(), "The request must be refused with a 'failed' element in the namespace of the request, but the response was: " + failed.asXML());
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must not be enabled before a domain is authenticated.");
    }

    /**
     * Verifies that requests for acknowledgement are answered with the number of stanzas that the server sent after it
     * enabled Stream Management, counting all kinds of stanza, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void requestIsAnsweredWithCountOfHandledStanzas(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        establishAuthenticatedStream(method);
        handler.process(message("before-enable"), reader); // Sent before Stream Management is enabled: not counted.
        handler.process("<enable xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V3), lastDelivered().getQName(), "Stream Management was expected to be enabled; without it this test proves nothing.");
        handler.process(message("1"), reader);
        handler.process("<presence from='user@" + REMOTE_DOMAIN + "/resource' to='user@" + Fixtures.XMPP_DOMAIN + "' id='2'/>", reader);
        handler.process("<iq type='get' from='user@" + REMOTE_DOMAIN + "/resource' to='" + Fixtures.XMPP_DOMAIN + "' id='3'><ping xmlns='urn:xmpp:ping'/></iq>", reader);

        // Execute system under test.
        handler.process("<r xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        final Element firstAck = lastDelivered();
        handler.process(message("4"), reader);
        handler.process(message("5"), reader);
        handler.process("<r xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        final Element secondAck = lastDelivered();

        // Verify result.
        verify(router, times(4).description("Every message was expected to be routed; without that this test proves nothing.")).route(any(Message.class));
        verify(router, times(1).description("The presence was expected to be routed; without that this test proves nothing.")).route(any(Presence.class));
        verify(router, times(1).description("The IQ was expected to be routed; without that this test proves nothing.")).route(any(IQ.class));
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), firstAck.getQName(), "A request for acknowledgement must be answered with an 'a' element, but the response was: " + firstAck.asXML());
        assertEquals("3", firstAck.attributeValue("h"), "The count must equal the number of stanzas handled after Stream Management was enabled.");
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), secondAck.getQName(), "A second request for acknowledgement must be answered with an 'a' element, but the response was: " + secondAck.asXML());
        assertEquals("5", secondAck.attributeValue("h"), "The count must include the stanzas handled before the previous acknowledgement.");
    }

    /**
     * Verifies that a server that requests a resumable stream gets Stream Management without resumption, for either
     * method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void resumptionIsNotOffered(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        establishAuthenticatedStream(method);

        // Execute system under test.
        handler.process("<enable xmlns='" + StreamManager.NAMESPACE_V3 + "' resume='true'/>", reader);

        // Verify result.
        final Element enabled = lastDelivered();
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V3), enabled.getQName(), "The server must be told that Stream Management is enabled, but the response was: " + enabled.asXML());
        assertNull(enabled.attributeValue("resume"), "Resumption must not be offered to a server, but the response was: " + enabled.asXML());
        assertNull(enabled.attributeValue("id"), "No stream to resume must be identified to a server, but the response was: " + enabled.asXML());
        assertFalse(session.getStreamManager().getResume(), "The stream of a server session must not be resumable.");
    }

    /**
     * Verifies that a stream header that restarts the stream on a connection on which another stream was resumed
     * carries the stream ID of the stream header that was sent on that connection before, with which the remote server
     * generates dialback keys, rather than the stream ID of the resumed session.
     */
    @Test
    public void streamHeaderAfterResumptionCarriesStreamIdOfConnection() throws Exception
    {
        // Setup test fixture.
        handler.process(STREAM_HEADER, reader);
        final StreamID connectionStreamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession resumed = mock(LocalIncomingServerSession.class, withSettings().lenient());
        doReturn(new BasicStreamIDFactory().createStreamID()).when(resumed).getStreamID();
        doReturn(connectionStreamID).when(resumed).getConnectionStreamID();
        doReturn(Locale.ENGLISH).when(resumed).getLanguage();
        handler.setSession(resumed);

        // Execute system under test.
        final Element header = handler.getStreamHeader().getRootElement();

        // Verify result.
        assertEquals(connectionStreamID.getID(), header.attributeValue("id"), "The stream header must carry the stream ID of the stream header on the connection, not that of the resumed session.");
    }

    /**
     * Opens a stream on the handler and authenticates the domain of the remote server on it. With SASL EXTERNAL, the
     * remote server restarts the stream after authentication, while with dialback, it continues on the same stream.
     */
    private void establishAuthenticatedStream(final ServerSession.AuthenticationMethod method) throws Exception
    {
        handler.process(STREAM_HEADER, reader);
        authenticate(method);
        if (method == ServerSession.AuthenticationMethod.SASL_EXTERNAL) {
            handler.process(STREAM_HEADER, reader);
        }
    }

    /**
     * Authenticates the domain of the remote server on the session, as dialback and SASL authentication do. For SASL,
     * the handler is also put in the state that a successful SASL negotiation leaves it in, so that it treats the next
     * stream header as the restart that follows authentication.
     */
    private void authenticate(final ServerSession.AuthenticationMethod method)
    {
        session.addValidatedDomain(REMOTE_DOMAIN);
        session.setAuthenticationMethod(method);
        if (method == ServerSession.AuthenticationMethod.SASL_EXTERNAL) {
            handler.startedSASL = true;
            handler.saslStatus = SASLAuthentication.Status.authenticated;
        }
    }

    private static String message(final String id)
    {
        return "<message type='chat' from='user@" + REMOTE_DOMAIN + "/resource' to='user@" + Fixtures.XMPP_DOMAIN + "' id='" + id + "'><body>test</body></message>";
    }

    /**
     * Returns everything that was delivered on the connection, in order.
     */
    private List<String> allDelivered()
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Expected something to be delivered on the connection.")).deliverRawText(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Returns the last element that was delivered on the connection.
     */
    private Element lastDelivered() throws Exception
    {
        final List<String> delivered = allDelivered();
        return DocumentHelper.parseText(delivered.get(delivered.size() - 1)).getRootElement();
    }

    /**
     * Returns the stream features in the last stream header that was delivered on the connection.
     */
    private List<Element> lastAdvertisedFeatures() throws Exception
    {
        final List<String> headers = allDelivered().stream().filter(text -> text.contains("<stream:stream")).toList();
        assertFalse(headers.isEmpty(), "Expected a stream header to be delivered on the connection.");
        final Element stream = DocumentHelper.parseText(headers.get(headers.size() - 1) + "</stream:stream>").getRootElement();
        final Element features = stream.element(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        assertNotNull(features, "Expected the stream header to contain stream features, but it was: " + stream.asXML());
        return features.elements();
    }

    /**
     * Returns a namespace-aware packet reader, configured as the production code configures its own.
     */
    private static XMPPPacketReader newPacketReader() throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
        return reader;
    }
}
