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
package org.jivesoftware.openfire.streammanagement;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.JID;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link StreamManager} makes a stream non-resumable when it closes it with a stream error, as a stream
 * that ended because of an error must not be resumable (OF-2751).
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3443">OF-3443: Prevent resumption of a Stream Management session that was closed with a stream error</a>
 */
public class StreamManagerCloseWithErrorTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);

    private Connection connection;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        //noinspection deprecation
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        connection = mock(Connection.class);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a resumable stream is no longer resumable after it is closed for an acknowledgement with a value of
     * 'h' that is not valid, so that the session is ended rather than kept detached.
     *
     * @param h a value of 'h' that is not a number, negative, higher than the number of stanzas that were sent, or above 2^32-1.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "-1", "1", "4294967296"})
    public void streamClosedForInvalidAcknowledgementIsNotResumable(final String h) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableSession();
        final StreamManager sm = session.getStreamManager();
        final Element ack = DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3));
        ack.addAttribute("h", h);

        // Execute system under test.
        sm.process(ack);

        // Verify result.
        assertStreamClosedWithError();
        assertFalse(sm.getResume(), "A stream that was closed with a stream error must not be resumable.");
    }

    /**
     * Verifies that a resumable stream is no longer resumable after it is closed for a malformed resume request, so that
     * the session is ended rather than kept detached.
     */
    @Test
    public void streamClosedForMalformedResumeRequestIsNotResumable() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableSession();
        final Element resume = DocumentHelper.createElement(QName.get("resume", StreamManager.NAMESPACE_V3));
        resume.addAttribute("h", "0"); // No 'previd', which makes the request malformed.

        // Execute system under test.
        session.getStreamManager().process(resume);

        // Verify result.
        assertStreamClosedWithError();
        assertFalse(session.getStreamManager().getResume(), "A stream that was closed with a stream error must not be resumable.");
    }

    /**
     * A client session with a mocked connection, on which Stream Management is enabled with resumption.
     */
    private LocalClientSession resumableSession() throws Exception
    {
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID("test-stream-id"), Locale.ENGLISH);
        session.setAddress(FULL_JID);
        session.setAuthToken(AuthToken.generateUserToken(FULL_JID.getNode()));
        session.setStatus(Session.Status.AUTHENTICATED);

        final Element enable = DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3));
        enable.addAttribute("resume", "true");
        session.getStreamManager().process(enable);
        assertTrue(session.getStreamManager().getResume(), "Test fixture expects a resumable stream.");
        clearInvocations(connection);
        return session;
    }

    private void assertStreamClosedWithError()
    {
        final ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("A stream error must be sent.")).deliverRawText(delivered.capture());
        assertTrue(delivered.getAllValues().stream().anyMatch(text -> text.contains("undefined-condition")), "A stream error with the 'undefined-condition' condition must be sent, but was: " + delivered.getAllValues());
        verify(connection, atLeastOnce().description("The connection must be closed.")).close();
    }
}
