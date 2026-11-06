/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.streammanagement;

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The fixture and helpers that the unit tests of {@link StreamManager} and its subclasses share.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
abstract class AbstractStreamManagerTest
{
    static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);
    static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);
    static final String STREAM_ID = "test-stream-id";
    static final int DETACH_TIME_MILLIS = 120_000;

    // OF-3433: Names of private fields, used to reach the roll-over at 2^32 without sending that many stanzas.
    static final String HANDLED_COUNTER_FIELD = "handledInboundStanzas";
    static final String ACKNOWLEDGED_COUNTER_FIELD = "unacknowledgedOutboundStanzas.acknowledged";

    SessionManager sessionManager;

    /**
     * The routing table of the mocked server, in which tests can stub the route of a session.
     */
    RoutingTable routingTable;

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
        doReturn(DETACH_TIME_MILLIS).when(sessionManager).getSessionDetachTime();
        routingTable = mock(RoutingTable.class);
        doReturn(routingTable).when(server).getRoutingTable();

        //noinspection deprecation
        XMPPServer.setInstance(server);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * A mock of a connection that reports itself as closed once it is closed, through either close() or
     * close(StreamError), and that has a fallback for stanzas that it cannot deliver.
     */
    static Connection closableConnection()
    {
        final Connection connection = mock(Connection.class);
        doReturn(mock(PacketDeliverer.class)).when(connection).getPacketDeliverer();
        final AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer(invocation -> { closed.set(true); return null; }).when(connection).close();
        doAnswer(invocation -> { closed.set(true); return null; }).when(connection).close(nullable(StreamError.class));
        doAnswer(invocation -> closed.get()).when(connection).isClosed();
        return connection;
    }

    /**
     * A mock of an authenticated, non-anonymous client session that has a connection, a full JID and a stream ID.
     *
     * The session will set its closed flag when it is closed through invocation of either close() or close(StreamError)
     */
    static LocalClientSession authenticatedClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(false).when(authToken).isAnonymous();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(closableConnection()).when(session).getConnection();
        doReturn(true).when(session).isAuthenticated();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(BasicStreamIDFactory.createStreamID(STREAM_ID)).when(session).getStreamID();
        mockCloseTracking(session);
        return session;
    }

    /**
     * Creates a stream manager with Stream Management enabled, and clears the interaction with the session that resulted
     * from enabling it.
     */
    static AnsweringStreamManager enabledStreamManager(final LocalSession session, final String namespace)
    {
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        sm.process(smElement("enable", namespace));
        assertTrue(sm.isEnabled(), "Test fixture expects Stream Management to be enabled.");
        clearInvocations(session);
        return sm;
    }

    /**
     * A mock of a detached client session that can be resumed: it has a non-anonymous authentication token and, as a
     * detached session, no connection. It is the route for its full JID in the routing table of the mocked server.
     */
    LocalClientSession resumableClientSession()
    {
        final LocalClientSession session = authenticatedClientSession();
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(FULL_JID.getNode()).when(authToken).getUsername();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        doReturn(session).when(routingTable).getClientRoute(FULL_JID);
        return session;
    }

    /**
     * A mock of a new client session from which a resumption is requested: it authenticated as the same user, but did
     * not bind a resource.
     */
    static LocalClientSession resumingClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(FULL_JID.getNode()).when(authToken).getUsername();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(false).when(session).isAuthenticated();
        doReturn(Fixtures.XMPP_DOMAIN).when(session).getServerName();
        doReturn(BasicStreamIDFactory.createStreamID("resuming-stream-id")).when(session).getStreamID();
        mockCloseTracking(session);
        return session;
    }

    /**
     * Creates the stream manager of a session, with Stream Management enabled with resumption. The {@code <enabled/>}
     * element remains the last element that was delivered to the session.
     */
    static StreamManager streamManagerWithResumption(final LocalClientSession session)
    {
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        doReturn(sm).when(session).getStreamManager();
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");
        sm.process(enable);
        assertTrue(sm.getResume(), "Test fixture expects a resumable stream.");
        return sm;
    }

    static Element resume(final String previd, final long h)
    {
        return smElement("resume", StreamManager.NAMESPACE_V3).addAttribute("previd", previd).addAttribute("h", String.valueOf(h));
    }

    static Element smElement(final String name, final String namespace)
    {
        return DocumentHelper.createElement(QName.get(name, namespace));
    }

    static Element ack(final String namespace, final String h)
    {
        return smElement("a", namespace).addAttribute("h", h);
    }

    static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        return message;
    }

    /**
     * Returns the last element that was delivered to the session.
     */
    static Element lastDelivered(final LocalSession session) throws DocumentException
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(session, atLeastOnce().description("Expected something to be delivered to the session.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
    }

    /**
     * Returns the IDs of the messages that are unacknowledged, in order. This closes the stream manager, as it uses the
     * routing of unacknowledged messages on close to observe them.
     */
    static List<String> unacknowledgedMessageIds(final StreamManager sm)
    {
        final PacketRouter router = mock(PacketRouter.class);
        sm.onClose(router, SERVER_ADDRESS);

        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(router, atLeast(0)).route(captor.capture());
        return captor.getAllValues().stream().map(Packet::getID).toList();
    }

    static void assertFailedWithUnexpectedRequest(final Element failed, final String namespace)
    {
        assertEquals(QName.get("failed", namespace), failed.getQName(), "The response must be a 'failed' element in the namespace of the request.");
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
    }

    /**
     * Verifies that the session was closed with one stream error of the specified condition. Returns that stream
     * error's XML representation.
     *
     * @param expectedCondition the condition of the stream error
     * @return the XML representation of the stream error
     */
    static String assertSessionClosedWithStreamError(final LocalSession session, final StreamError.Condition expectedCondition)
    {
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(session, times(1).description("The session must be closed with exactly one stream error.")).close(captor.capture());
        assertTrue(session.isClosed(), "The session must report itself as closed.");
        assertEquals(expectedCondition, captor.getValue().getCondition(), "The stream error must use the '" + expectedCondition + "' condition, but was: " + captor.getValue());
        return captor.getValue().toXML();
    }

    /**
     * Verifies that the session was closed with one stream error of the specified condition, containing the specified
     * text.
     *
     * @param expectedCondition the condition of the stream error
     * @param expectedText      the text that must be contained in the stream error
     */
    static void assertSessionClosedWithStreamError(final LocalSession session, final StreamError.Condition expectedCondition, final String expectedText)
    {
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(session, times(1).description("The session must be closed with exactly one stream error.")).close(captor.capture());
        assertTrue(session.isClosed(), "The session must report itself as closed.");
        assertEquals(expectedCondition, captor.getValue().getCondition(), "The stream error must use the '" + expectedCondition + "' condition, but was: " + captor.getValue());
        final String streamError = captor.getValue().toXML();
        assertTrue(streamError.contains(expectedText), "The stream error must contain the text '" + expectedText + "', but was: " + streamError);
    }

    static void assertHandledCountTooHigh(final LocalSession session, final String expectedH, final String expectedSendCount) throws DocumentException
    {
        final String streamError = assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition);
        final Element element = DocumentHelper.parseText(streamError).getRootElement().element(QName.get("handled-count-too-high", StreamManager.NAMESPACE_V3));
        assertNotNull(element, "The stream error must include a 'handled-count-too-high' element, but was: " + streamError);
        assertEquals(expectedH, element.attributeValue("h"), "The element must report the value of 'h' that was received.");
        assertEquals(expectedSendCount, element.attributeValue("send-count"), "The element must report the number of stanzas that were sent.");
    }

    /**
     * Mocks the session to track whether it is closed.
     *
     * @param session the session to mock.
     */
    static void mockCloseTracking(final LocalClientSession session)
    {
        final AtomicBoolean closed = new AtomicBoolean(false);

        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(session).close();

        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(session).close(nullable(StreamError.class));

        doAnswer(invocation -> closed.get()).when(session).isClosed();
    }

    /**
     * Sets a private counter of a stream manager, as sending 2^32 stanzas is not feasible in a unit test. The name of the
     * field can be a path of field names separated by dots, to reach a counter in an object that the stream manager holds.
     */
    static void setCounter(final StreamManager sm, final String fieldName, final long value) throws Exception
    {
        Object holder = sm;
        final String[] names = fieldName.split("\\.");
        for (int i = 0; i < names.length - 1; i++) {
            holder = field(holder, names[i]).get(holder);
        }
        final Field counter = field(holder, names[names.length - 1]);
        if (counter.getType() == long.class) {
            counter.setLong(holder, value);
        } else {
            ((AtomicLong) counter.get(holder)).set(value);
        }
    }

    static Field field(final Object holder, final String name) throws NoSuchFieldException
    {
        for (Class<?> type = holder.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // Declared on a superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
