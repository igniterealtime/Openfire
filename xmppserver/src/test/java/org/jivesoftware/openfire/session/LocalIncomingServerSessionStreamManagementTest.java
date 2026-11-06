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
package org.jivesoftware.openfire.session;

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
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
import org.xmpp.packet.StreamError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that Stream Management (XEP-0198) is offered to, and can be enabled by, servers that connect to Openfire,
 * depending on {@link StreamManager#SERVER_ACTIVE}.
 *
 * These tests use a session with a mocked connection, and pass Stream Management elements to the stream manager of
 * the session directly, as the stanza handler does.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class LocalIncomingServerSessionStreamManagementTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final QName SM_V2 = QName.get("sm", StreamManager.NAMESPACE_V2);
    private static final QName SM_V3 = QName.get("sm", StreamManager.NAMESPACE_V3);

    private Connection connection;

    private LocalIncomingServerSession session;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        doReturn(Connection.CompressionPolicy.disabled).when(configuration).getCompressionPolicy();
        connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();

        session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), REMOTE_DOMAIN);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that Stream Management is not offered to servers when it is not enabled for them, which is the default.
     */
    @Test
    public void featureIsNotAdvertisedByDefault()
    {
        // Setup test fixture.
        authenticate(ServerSession.AuthenticationMethod.DIALBACK);

        // Execute system under test.
        final List<Element> features = session.getAvailableStreamFeatures();

        // Verify result.
        assertTrue(features.stream().noneMatch(feature -> feature.getQName().equals(SM_V2) || feature.getQName().equals(SM_V3)), "Stream Management must not be advertised to servers by default, but the features were: " + features);
    }

    /**
     * Verifies that Stream Management is offered before the server is authenticated. Dialback authenticates a domain
     * without restarting the stream, so the features from before authentication are the only ones the server gets.
     */
    @Test
    public void featureIsAdvertisedBeforeAuthentication()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);

        // Execute system under test.
        final List<Element> features = session.getAvailableStreamFeatures();

        // Verify result.
        assertTrue(features.stream().anyMatch(feature -> feature.getQName().equals(SM_V2)), "Version 2 of Stream Management must be advertised, but the features were: " + features);
        assertTrue(features.stream().anyMatch(feature -> feature.getQName().equals(SM_V3)), "Version 3 of Stream Management must be advertised, but the features were: " + features);
    }

    /**
     * Verifies that Stream Management is offered after the server is authenticated, which is when the features are sent
     * again on the stream that is restarted after SASL authentication.
     */
    @Test
    public void featureIsAdvertisedAfterAuthentication()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        final List<Element> features = session.getAvailableStreamFeatures();

        // Verify result.
        assertTrue(features.stream().anyMatch(feature -> feature.getQName().equals(SM_V3)), "Stream Management must be advertised after authentication, but the features were: " + features);
    }

    /**
     * Verifies that a server can enable Stream Management after one of its domains is authenticated, with either method
     * of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void enableSucceedsAfterAuthentication(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(method);

        // Execute system under test.
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V3), lastDelivered().getQName(), "The server must be told that Stream Management is enabled.");
        assertTrue(session.getStreamManager().isEnabled(), "Stream Management must be enabled after a successful request.");
    }

    /**
     * Verifies that a server cannot enable Stream Management before any of its domains is authenticated.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    public void enableBeforeAuthenticationFails(final String namespace) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);

        // Execute system under test.
        session.getStreamManager().process(smElement("enable", namespace));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(), namespace);
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must not be enabled before a domain is authenticated.");
    }

    /**
     * Verifies that a server cannot enable Stream Management after all of its authenticated domains were removed from
     * the session, which leaves the session in the authenticated state.
     */
    @Test
    public void enableAfterRemovalOfAllDomainsFails() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK);
        session.removeValidatedDomain(REMOTE_DOMAIN);

        // Execute system under test.
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(), StreamManager.NAMESPACE_V3);
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must not be enabled when no domain is authenticated.");
    }

    /**
     * Verifies that a server that is authenticated cannot enable Stream Management, when Stream Management is not
     * enabled for servers.
     */
    @Test
    public void enableFailsWhenNotActiveForServers() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(false);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK);

        // Execute system under test.
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(), StreamManager.NAMESPACE_V3);
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must not be enabled when it is not enabled for servers.");
    }

    /**
     * Verifies that a server that requests resumption gets Stream Management without resumption.
     */
    @Test
    public void resumptionIsNotOffered() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        session.getStreamManager().process(enable);

        // Verify result.
        final Element enabled = lastDelivered();
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V3), enabled.getQName(), "The server must be told that Stream Management is enabled.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected for a server session, but got: " + enabled.asXML());
        assertFalse(session.getStreamManager().getResume(), "The stream of a server session must not be resumable.");
    }

    /**
     * Verifies that a request from a server to resume a stream is refused.
     */
    @Test
    public void resumeIsRefused() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        final Element resume = smElement("resume", StreamManager.NAMESPACE_V3).addAttribute("previd", "some-id").addAttribute("h", "0");

        // Execute system under test.
        session.getStreamManager().process(resume);

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(), StreamManager.NAMESPACE_V3);
    }

    /**
     * Verifies that a request for acknowledgement from a server is answered with the number of stanzas that the
     * session handled after Stream Management was enabled.
     */
    @Test
    public void requestIsAnsweredWithCountOfHandledStanzas() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK);
        session.incrementClientPacketCount(); // Before enabling: not counted.
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        session.incrementClientPacketCount();
        session.incrementClientPacketCount();
        session.incrementClientPacketCount();

        // Execute system under test.
        session.getStreamManager().process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        final Element ack = lastDelivered();
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), ack.getQName(), "An 'a' element must be sent, in the namespace of the stream.");
        assertEquals("3", ack.attributeValue("h"), "The count must equal the number of stanzas handled after Stream Management was enabled.");
    }

    /**
     * Verifies that an acknowledgement from a server is accepted. Openfire does not send stanzas on a connection that a
     * server opened, so the server can only acknowledge zero stanzas.
     */
    @Test
    public void acknowledgementOfNoStanzasIsAccepted()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));

        // Execute system under test.
        session.getStreamManager().process(smElement("a", StreamManager.NAMESPACE_V3).addAttribute("h", "0"));

        // Verify result.
        verify(connection, never().description("The connection must not be closed for an acknowledgement of zero stanzas.")).close(any(StreamError.class));
        assertTrue(session.getStreamManager().isEnabled(), "Stream Management must remain enabled.");
    }

    /**
     * Authenticates the domain of the remote server on the session, as dialback and SASL authentication do.
     */
    private void authenticate(final ServerSession.AuthenticationMethod method)
    {
        session.addValidatedDomain(REMOTE_DOMAIN);
        session.setAuthenticationMethod(method);
    }

    private static Element smElement(final String name, final String namespace)
    {
        return DocumentHelper.createElement(QName.get(name, namespace));
    }

    /**
     * Returns the last element that was delivered on the connection.
     */
    private Element lastDelivered() throws DocumentException
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Expected something to be delivered on the connection.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
    }

    private static void assertFailedWithUnexpectedRequest(final Element failed, final String namespace)
    {
        assertEquals(QName.get("failed", namespace), failed.getQName(), "The response must be a 'failed' element in the namespace of the request.");
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
    }
}
