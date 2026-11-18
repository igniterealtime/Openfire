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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.cert.Certificate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that {@link LocalIncomingServerSession#validateSubsequentDomain(Element)} reports success to the peer from
 * the normalised form of the validated domain (OF-2643), and that it refuses a dialback request for a further domain on
 * a session that is already authenticated, without closing the connection (OF-3470).
 *
 * Server Dialback validates the domain through {@link org.jivesoftware.openfire.server.ServerDialback}, which returns
 * the domain as the local XMPP service knows it. This test checks that the {@code <db:result type='valid'>} reply uses
 * that returned value, rather than the form the peer wrote. The remote-server permission check and the certificate
 * check are replaced with stubs, as they read the database and the peer's certificates, which are not what this test
 * is about.
 *
 * @author Matthew Vivian
 */
public class LocalIncomingServerSessionSubsequentDomainTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final String OTHER_REMOTE_DOMAIN = "other.example.org";

    private static final String BLOCKED_REMOTE_DOMAIN = "blocked.example.org";

    private Connection connection;

    private LocalIncomingServerSession session;

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
    public void setUp()
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        doReturn(Fixtures.mockRoutingTable()).when(xmppServer).getRoutingTable();
        final SessionManager sessionManager = xmppServer.getSessionManager();
        doReturn(List.of()).when(sessionManager).getIncomingServerSessions(REMOTE_DOMAIN);
        XMPPServer.setInstance(xmppServer);

        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();
        doReturn(new Certificate[0]).when(connection).getPeerCertificates();

        session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), REMOTE_DOMAIN);
    }

    /**
     * Asserts that, when a peer addresses a subsequent domain in a different case from the XMPP domain, the reply that
     * reports success carries the normalised domain in its 'from' attribute, rather than the form the peer wrote.
     */
    @Test
    public void testSubsequentDomainReplyReportsNormalisedDomain()
    {
        // Setup test fixture.
        final Element dbResult = DocumentHelper.createElement("result");
        dbResult.addAttribute("from", REMOTE_DOMAIN);
        dbResult.addAttribute("to", Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT));
        dbResult.setText("key");

        // Execute system under test.
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedStatic<SASLAuthentication> sasl = mockStatic(SASLAuthentication.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            sasl.when(() -> SASLAuthentication.verifyCertificates(any(), anyString(), anyBoolean())).thenReturn(true);

            final boolean result = session.validateSubsequentDomain(dbResult);
            assertTrue(result, "The subsequent domain must be validated successfully.");
        }

        // Verify result.
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("A reply must have been delivered to the peer.")).deliverRawText(captor.capture());
        final String delivered = String.join("", captor.getAllValues());
        assertTrue(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The reply must report success from the normalised domain.");
        assertFalse(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT) + "\""), "The reply must not report success from the domain in the case the peer wrote.");
    }

    /**
     * Asserts that a dialback request for a further domain, on a session that is already authenticated for a domain, is
     * refused with a 'resource-constraint' dialback error, without authenticating that domain and without closing the
     * connection, so that the remote server can use a new connection for it (XEP-0220 section 2.5).
     */
    @Test
    public void testAdditionalDomainIsRefusedWithResourceConstraint() throws Exception
    {
        // Setup test fixture.
        final Element additional = dialbackRequest(OTHER_REMOTE_DOMAIN, Fixtures.XMPP_DOMAIN);

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(additional);

        // Verify result.
        assertTrue(result, "Refusing a further domain must not be reported as a failure, as that ends the stream.");
        assertDialbackError(deliveredReply(), Fixtures.XMPP_DOMAIN, OTHER_REMOTE_DOMAIN, "resource-constraint");
        assertFalse(session.getValidatedDomains().contains(OTHER_REMOTE_DOMAIN), "The further domain must not be authenticated on the session.");
        assertTrue(session.getValidatedDomains().contains(REMOTE_DOMAIN), "The first domain must stay authenticated on the session.");
        verifyConnectionNotClosed();
    }

    /**
     * Asserts that a dialback request for a further domain, to a target domain that is not hosted here, is refused with
     * 'item-not-found' rather than with 'resource-constraint', as a new connection would not help, and that the
     * connection is not closed.
     */
    @Test
    public void testAdditionalDomainForUnhostedTargetIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final Element additional = dialbackRequest(OTHER_REMOTE_DOMAIN, "unhosted.example.com");

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(additional);

        // Verify result.
        assertTrue(result, "Refusing a further domain must not be reported as a failure, as that ends the stream.");
        assertDialbackError(deliveredReply(), "unhosted.example.com", OTHER_REMOTE_DOMAIN, "item-not-found");
        assertFalse(session.getValidatedDomains().contains(OTHER_REMOTE_DOMAIN), "The further domain must not be authenticated on the session.");
        verifyConnectionNotClosed();
    }

    /**
     * Asserts that a dialback request for a further domain that the configuration of this server does not allow is
     * refused with a 'policy-violation' dialback error rather than a stream error, so that the connection stays open for
     * the domain that is already authenticated (XEP-0220 section 2.5).
     */
    @Test
    public void testAdditionalDomainRefusedByPolicyIsRefusedWithDialbackError() throws Exception
    {
        // Setup test fixture.
        final Element additional = dialbackRequest(BLOCKED_REMOTE_DOMAIN, Fixtures.XMPP_DOMAIN);

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(additional);

        // Verify result.
        assertTrue(result, "Refusing a further domain must not be reported as a failure, as that ends the stream.");
        assertDialbackError(deliveredReply(), Fixtures.XMPP_DOMAIN, BLOCKED_REMOTE_DOMAIN, "policy-violation");
        assertFalse(session.getValidatedDomains().contains(BLOCKED_REMOTE_DOMAIN), "The further domain must not be authenticated on the session.");
        verifyConnectionNotClosed();
    }

    /**
     * Asserts that the dialback error that refuses a further domain declares the 'db' prefix itself. The stream header
     * declares that prefix only when dialback is enabled, while a peer that authenticated in another way can still send
     * a dialback request.
     */
    @Test
    public void testRefusalIsWellFormedWhenDialbackIsDisabled() throws Exception
    {
        // Setup test fixture.
        session.addValidatedDomain(REMOTE_DOMAIN);
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        final Element additional = dialbackRequest(OTHER_REMOTE_DOMAIN, Fixtures.XMPP_DOMAIN);
        final boolean result;

        // Execute system under test.
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            result = session.validateSubsequentDomain(additional);
        }

        // Verify result.
        assertTrue(result, "Refusing a further domain must not be reported as a failure, as that ends the stream.");
        final String delivered = deliveredText();
        final Element reply = assertDoesNotThrow(() -> DocumentHelper.parseText(delivered).getRootElement(), "The reply must be well-formed on its own, as the stream header does not declare the 'db' prefix.");
        assertDialbackError(reply, Fixtures.XMPP_DOMAIN, OTHER_REMOTE_DOMAIN, "policy-violation");
        verifyConnectionNotClosed();
    }

    static Stream<Arguments> requestsWithoutAddress()
    {
        return Stream.of(
            Arguments.of(null, Fixtures.XMPP_DOMAIN),
            Arguments.of("", Fixtures.XMPP_DOMAIN),
            Arguments.of(OTHER_REMOTE_DOMAIN, null),
            Arguments.of(OTHER_REMOTE_DOMAIN, ""),
            Arguments.of(REMOTE_DOMAIN, null)
        );
    }

    /**
     * Asserts that a dialback request that does not name its sending domain or its target domain is refused with an
     * 'improper-addressing' stream error, also when it is for a further domain, as a dialback error could not be
     * addressed.
     */
    @ParameterizedTest
    @MethodSource("requestsWithoutAddress")
    public void testRequestWithoutAddressIsRefusedWithStreamError(final String from, final String to)
    {
        // Setup test fixture.
        final Element request = dialbackRequest(from, to);

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(request);

        // Verify result.
        assertFalse(result, "A request without a sending or a target domain must be reported as a failure.");
        final String delivered = deliveredText();
        assertTrue(delivered.contains("<stream:error"), "The refusal must be a stream error.");
        assertTrue(delivered.contains("improper-addressing"), "The stream error must carry the 'improper-addressing' condition.");
        verify(connection, atLeastOnce().description("The connection must be closed after the stream error.")).close();
    }

    /**
     * Asserts that a dialback request from the domain that the session is already authenticated for is validated, and
     * not refused: it adds no domain to the session.
     */
    @Test
    public void testRequestFromAuthenticatedDomainIsNotRefused()
    {
        // Setup test fixture.
        final Element again = dialbackRequest(REMOTE_DOMAIN, Fixtures.XMPP_DOMAIN);

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(again);

        // Verify result.
        assertTrue(result, "A request from the authenticated domain must be validated successfully.");
        final String delivered = deliveredText();
        assertTrue(delivered.contains("type=\"valid\""), "A request from the authenticated domain must be answered as valid.");
        assertFalse(delivered.contains("<resource-constraint"), "A request from the authenticated domain must not be refused.");
    }

    /**
     * Asserts that a dialback request from the domain that the session is already authenticated for, written in another
     * case, is treated as a request from that domain, and not refused.
     */
    @Test
    public void testRequestFromAuthenticatedDomainInOtherCaseIsNotRefused()
    {
        // Setup test fixture.
        final Element again = dialbackRequest(REMOTE_DOMAIN.toUpperCase(Locale.ROOT), Fixtures.XMPP_DOMAIN);

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(again);

        // Verify result.
        assertTrue(result, "A request from the authenticated domain in another case must be validated successfully.");
        final String delivered = deliveredText();
        assertTrue(delivered.contains("type=\"valid\""), "A request from the authenticated domain in another case must be answered as valid.");
        assertFalse(delivered.contains("<resource-constraint"), "A request from the authenticated domain in another case must not be refused.");
    }

    /**
     * Asserts that a dialback request from the domain that the session is already authenticated for, written in a form
     * that prepares to that domain (here in full-width letters), is treated as a request from that domain, and not
     * refused.
     */
    @Test
    public void testRequestFromAuthenticatedDomainInOtherFormIsNotRefused()
    {
        // Setup test fixture.
        final String fullWidth = "\uFF52\uFF45\uFF4D\uFF4F\uFF54\uFF45.example.org";
        final Element again = dialbackRequest(fullWidth, Fixtures.XMPP_DOMAIN);

        // Execute system under test.
        final boolean result = validateAfterFirstDomain(again);

        // Verify result.
        assertTrue(result, "A request from the authenticated domain in another form must be validated successfully.");
        final String delivered = deliveredText();
        assertTrue(delivered.contains("type=\"valid\""), "A request from the authenticated domain in another form must be answered as valid.");
        assertFalse(delivered.contains("<resource-constraint"), "A request from the authenticated domain in another form must not be refused.");
    }

    /**
     * Authenticates {@link #REMOTE_DOMAIN} on the session, then processes the provided dialback request. Invocations on
     * the connection that are made while the first domain is authenticated are cleared.
     */
    private boolean validateAfterFirstDomain(final Element dbResult)
    {
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedStatic<SASLAuthentication> sasl = mockStatic(SASLAuthentication.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            remoteServers.when(() -> RemoteServerManager.canAccess(BLOCKED_REMOTE_DOMAIN)).thenReturn(false);
            sasl.when(() -> SASLAuthentication.verifyCertificates(any(), anyString(), anyBoolean())).thenReturn(true);

            assertTrue(session.validateSubsequentDomain(dialbackRequest(REMOTE_DOMAIN, Fixtures.XMPP_DOMAIN)), "The first domain on the session must be validated successfully.");
            clearInvocations(connection);
            return session.validateSubsequentDomain(dbResult);
        }
    }

    private String deliveredText()
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("A reply must have been delivered to the peer.")).deliverRawText(captor.capture());
        return String.join("", captor.getAllValues());
    }

    /**
     * Returns the one dialback result that was delivered to the peer. The reply leaves out the declaration of the 'db'
     * prefix, which the stream header holds, so it is parsed within an element that declares it.
     */
    private Element deliveredReply() throws Exception
    {
        final Element stream = DocumentHelper.parseText("<stream xmlns:db=\"jabber:server:dialback\">" + deliveredText() + "</stream>").getRootElement();
        assertEquals(1, stream.elements().size(), "Exactly one reply must have been delivered to the peer.");
        return stream.elements().get(0);
    }

    private static void assertDialbackError(final Element reply, final String from, final String to, final String condition)
    {
        assertEquals(QName.get("result", "db", "jabber:server:dialback"), reply.getQName(), "The reply must be a dialback result.");
        assertEquals("error", reply.attributeValue("type"), "The reply must be a dialback error.");
        assertEquals(from, reply.attributeValue("from"), "The dialback error must be sent from the target domain of the request.");
        assertEquals(to, reply.attributeValue("to"), "The dialback error must be addressed to the sending domain of the request.");
        final Element error = reply.element("error");
        assertNotNull(error, "The dialback error must hold an error element.");
        assertNotNull(error.element(QName.get(condition, "urn:ietf:params:xml:ns:xmpp-stanzas")), "The dialback error must carry the '" + condition + "' condition of the stanza error namespace.");
    }

    private void verifyConnectionNotClosed()
    {
        verify(connection, never().description("The connection must not be closed when a further domain is refused.")).close();
        verify(connection, never().description("The connection must not be closed when a further domain is refused.")).close(any());
    }

    private static Element dialbackRequest(final String from, final String to)
    {
        final Element dbResult = DocumentHelper.createElement("result");
        dbResult.addAttribute("from", from);
        dbResult.addAttribute("to", to);
        dbResult.setText("key");
        return dbResult;
    }
}
