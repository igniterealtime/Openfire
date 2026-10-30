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
package org.jivesoftware.openfire.server;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.cert.Certificate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that {@link ServerDialback#createIncomingSession(XMPPPacketReader)} records the normalised form of the
 * validated domain as the local domain of the session it creates (OF-2643).
 *
 * Server Dialback validates the domain through {@link ServerDialback#validateRemoteDomain(Element, StreamID)}, which
 * returns the domain as the local XMPP service knows it. This test checks that the created session's local domain is
 * that returned value, rather than the form the peer wrote. The reader, the session factory, the remote-server
 * permission check and the certificate check are replaced with stubs, as they parse a live stream, create sessions,
 * read the database and read the peer's certificates, which are not what this test is about.
 *
 * @author Matthew Vivian
 */
public class ServerDialbackCreateIncomingSessionTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private SessionManager sessionManager;

    private Connection connection;

    private ServerDialback dialback;

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
        sessionManager = xmppServer.getSessionManager();
        doReturn(List.of()).when(sessionManager).getIncomingServerSessions(REMOTE_DOMAIN);
        XMPPServer.setInstance(xmppServer);

        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();
        doReturn(new Certificate[0]).when(connection).getPeerCertificates();

        dialback = new ServerDialback(connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
    }

    /**
     * Asserts that, when a peer opens a dialback session addressing the XMPP domain in a different case, the session
     * that is created records the normalised domain as its local domain, rather than the form the peer wrote.
     */
    @Test
    public void testCreatedSessionRecordsNormalisedLocalDomain() throws Exception
    {
        // Setup test fixture: a reader that yields a db:result addressed to the XMPP domain in a different case.
        final Document document = DocumentHelper.createDocument();
        final Element dbResult = document.addElement(QName.get("result", Namespace.get("db", "jabber:server:dialback")));
        dbResult.addAttribute("from", REMOTE_DOMAIN);
        dbResult.addAttribute("to", Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT));
        dbResult.setText("key");

        final MXParser parser = mock(MXParser.class);
        doReturn("jabber:server:dialback").when(parser).getNamespace("db");
        final XMPPPacketReader reader = mock(XMPPPacketReader.class);
        doReturn(parser).when(reader).getXPPParser();
        doReturn(document).when(reader).parseDocument();

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        doReturn(streamID).when(sessionManager).nextStreamID();

        final LocalIncomingServerSession createdSession = mock(LocalIncomingServerSession.class);
        doReturn(createdSession).when(sessionManager).createIncomingServerSession(connection, streamID, REMOTE_DOMAIN);

        // Execute system under test.
        final LocalIncomingServerSession result;
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedStatic<SASLAuthentication> sasl = mockStatic(SASLAuthentication.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            sasl.when(() -> SASLAuthentication.verifyCertificates(any(), anyString(), anyBoolean())).thenReturn(true);

            result = dialback.createIncomingSession(reader);
        }

        // Verify result.
        assertSame(createdSession, result, "The session that was created must be returned.");
        verify(createdSession, times(1).description("The new session must record the normalised domain as its local domain.")).setLocalDomain(Fixtures.XMPP_DOMAIN);
    }
}
