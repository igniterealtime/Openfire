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

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that {@link StanzaHandler#initiateSession(String, XMPPPacketReader)} records the identity claimed on every
 * stream header that reopens a stream on an existing session, and that it does so <em>before</em> dispatching to the
 * branch that regenerates stream features.
 *
 * The ordering is what makes the value usable: the set of advertised SASL mechanisms is derived from the claim, and
 * the header that decides that set is the one that follows TLS negotiation.
 *
 * The clearing behaviour is what makes it safe. A stream header sent before TLS travels in the clear and can be
 * modified by an active attacker. If a claim made there survived into the protected stream, an attacker could narrow
 * the mechanisms offered on a stream that is otherwise protected against exactly that kind of downgrade.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class StanzaHandlerClaimedIdentityTest
{
    private static final String HEADER_WITH_FROM = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' from='juliet@example.org' to='example.org' version='1.0'>";

    private static final String HEADER_WITHOUT_FROM = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' to='example.org' version='1.0'>";

    private RecordingStanzaHandler handler;

    private LocalClientSession session;

    private XMPPPacketReader reader;

    @BeforeEach
    public void setUp() throws Exception
    {
        reader = newPacketReader();
        session = sessionStub();

        handler = new RecordingStanzaHandler(mock(PacketRouter.class), mock(Connection.class));
        handler.setSession(session);

        // Simulate a session that has already been created by an earlier stream header, so that the headers processed
        // by these tests are treated as reopening an existing stream.
        handler.sessionCreated = true;
    }

    /**
     * Asserts that a claim made on the stream header that follows TLS negotiation is recorded on the session before
     * the branch that generates stream features is entered.
     */
    @Test
    public void testClaimIsRecordedBeforeFeaturesAreGenerated() throws Exception
    {
        // Setup test fixture.
        handler.startedTLS = true;

        // Execute system under test.
        handler.initiateSession(HEADER_WITH_FROM, reader);

        // Verify result.
        assertTrue(handler.branchInvoked, "The TLS branch was expected to run; without it this test proves nothing about ordering.");
        assertEquals(new JID("juliet@example.org"), handler.claimVisibleToBranch, "The claim must already be recorded on the session by the time the branch that generates stream features runs.");
    }

    /**
     * Asserts that a claim made on the unprotected stream that preceded TLS negotiation is not visible to the feature
     * generation that follows it, when the new stream header omits the attribute.
     */
    @Test
    public void testClaimFromUnprotectedStreamIsCleared() throws Exception
    {
        // Setup test fixture.
        session.setClaimedIdentity(new JID("romeo@example.org")); // A claim made on the stream that preceded TLS negotiation. That stream was not protected.
        handler.startedTLS = true;

        // Execute system under test.
        handler.initiateSession(HEADER_WITHOUT_FROM, reader);

        // Verify result.
        assertTrue(handler.branchInvoked, "The TLS branch was expected to run; without it this test proves nothing about ordering.");
        assertNull(handler.claimVisibleToBranch, "A claim made on the unprotected stream must not be visible to the feature generation that follows TLS negotiation.");
        assertTrue(session.getClaimedIdentity().isEmpty(), "A stream header that omits 'from' must leave the session with no claimed identity.");
    }

    /**
     * Asserts that the branch receives a parser that is still positioned on the stream element. Recording the claim
     * requires advancing the parser, as does the branch that runs afterwards, so that advance must be idempotent.
     */
    @Test
    public void testParserRemainsPositionedForBranch() throws Exception
    {
        // Setup test fixture.
        handler.startedTLS = true;

        // Execute system under test.
        handler.initiateSession(HEADER_WITH_FROM, reader);

        // Verify result.
        assertEquals(XmlPullParser.START_TAG, handler.parserEventTypeAtBranch, "The branch must receive a parser that is still positioned on the stream element.");
    }

    /**
     * Asserts that recording is not specific to the TLS branch. Compression can be negotiated before authentication,
     * so the header that follows it regenerates the mechanism list just as the post-TLS header does.
     */
    @Test
    public void testClaimIsRecordedAfterCompression() throws Exception
    {
        // Setup test fixture.
        handler.waitingCompressionACK = true;

        // Execute system under test.
        handler.initiateSession(HEADER_WITH_FROM, reader);

        // Verify result.
        assertTrue(handler.branchInvoked, "The compression branch was expected to run; without it this test proves nothing about ordering.");
        assertEquals(new JID("juliet@example.org"), handler.claimVisibleToBranch, "The claim must be recorded before the branch that follows compression negotiation runs.");
    }

    /**
     * Asserts that a stream header updates the claim even when no negotiation is in flight and no branch runs. This
     * keeps the invariant simple: the session always reflects the most recent stream header, whatever followed it.
     */
    @Test
    public void testClaimIsRecordedWhenNoBranchRuns() throws Exception
    {
        // Setup test fixture.
        session.setClaimedIdentity(new JID("romeo@example.org"));

        // Execute system under test.
        handler.initiateSession(HEADER_WITHOUT_FROM, reader);

        // Verify result.
        assertFalse(handler.branchInvoked, "No branch was expected to run for this header.");
        assertTrue(session.getClaimedIdentity().isEmpty(), "A stream header must update the claimed identity even when it triggers no further processing.");
    }

    /**
     * Asserts that recording a claim on a handler that has no session is a no-op. Session creation can fail, after
     * which the connection is torn down; data already in flight must not raise a NullPointerException on its way out.
     */
    @Test
    public void testRecordingClaimWithoutSession() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = reader.getXPPParser();
        xpp.setInput(new StringReader(HEADER_WITH_FROM));
        handler.setSession(null);

        // Execute system under test & verify result.
        assertDoesNotThrow(() -> handler.recordClaimedIdentity(xpp), "Recording a claim on a handler that has no session must be a no-op rather than an error.");
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
     * Returns a {@link LocalClientSession} that executes its real method bodies without having run a constructor, so
     * that the claim-related methods can be invoked without the collaborators that a real session requires.
     *
     * @return a session on which the claim-related methods can be invoked.
     */
    private static LocalClientSession sessionStub()
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        // The real toString() dereferences fields that no constructor has populated here. Stub it so that a failing
        // assertion reports a useful message instead of a NullPointerException raised while formatting one.
        doReturn("LocalClientSession[test stub]").when(session).toString();
        return session;
    }

    /**
     * A {@link StanzaHandler} that replaces every post-negotiation branch with a recording of the session state as the
     * branch observed it. The real branches write stream features to the connection, which is not what these tests are
     * about; what matters is what the claim looked like at the moment they were entered.
     */
    private static class RecordingStanzaHandler extends StanzaHandler
    {
        private boolean branchInvoked;

        private JID claimVisibleToBranch;

        private int parserEventTypeAtBranch = -1;

        RecordingStanzaHandler(final PacketRouter router, final Connection connection)
        {
            super(router, connection);
        }

        @Override
        protected void tlsNegotiated(final XmlPullParser xpp)
        {
            parserEventTypeAtBranch = safeEventType(xpp);
            recordBranch();
        }

        @Override
        protected void saslSuccessful()
        {
            recordBranch();
        }

        @Override
        protected void compressionSuccessful()
        {
            recordBranch();
        }

        @Override
        boolean processUnknowPacket(final Element doc)
        {
            return false;
        }

        @Override
        void startTLS()
        {
        }

        @Override
        Namespace getNamespace()
        {
            return Namespace.get("jabber:client");
        }

        @Override
        boolean validateHost()
        {
            return false;
        }

        @Override
        boolean validateJIDs()
        {
            return false;
        }

        @Override
        void createSession(final String serverName, final XmlPullParser xpp, final Connection connection)
        {
        }

        /**
         * Records that a branch was entered, together with the claim that the session held at that moment.
         */
        private void recordBranch()
        {
            branchInvoked = true;
            claimVisibleToBranch = session.getClaimedIdentity().orElse(null);
        }

        /**
         * Returns the current event type of the provided parser, or -1 when it cannot be determined.
         *
         * @param xpp the parser to inspect.
         * @return an event type, or -1.
         */
        private static int safeEventType(final XmlPullParser xpp)
        {
            try {
                return xpp.getEventType();
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
