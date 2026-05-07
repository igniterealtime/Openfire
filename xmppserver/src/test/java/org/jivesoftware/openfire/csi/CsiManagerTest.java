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
package org.jivesoftware.openfire.csi;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.jivesoftware.openfire.session.LocalClientSession;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Verifies the implementation of {@link CsiManager}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CsiManagerTest
{
    @Mock
    private LocalClientSession mockSession;

    private CsiManager csiManager;

    @BeforeEach
    public void setUp() throws Exception
    {
        csiManager = new CsiManager(mockSession);
        // Wire the mock so that deliver() forwards the stanza back through queueOrPush(),
        // mirroring what LocalClientSession.deliver() does in production.
        doAnswer(invocation -> {
            csiManager.queueOrPush(invocation.getArgument(0));
            return Collections.emptyList(); // For some invocations (when 'mustpush==true), this is expected to return the queue, otherwise it returns an empty list. We're picking one of the two here. This is a simplification for testing purposes.
        }).when(mockSession).deliver(any(Packet.class));
    }

    /**
     * Verifies initial state is active.
     */
    @Test
    public void testInitialStateIsActive()
    {
        assertTrue(csiManager.isActive(), "CsiManager should initialize with active state");
    }

    /**
     * Verifies activation updates CSI state.
     */
    @Test
    public void testActivate()
    {
        // Setup test fixture
        csiManager.deactivate();
        assertFalse(csiManager.isActive(), "CsiManager should be inactive");

        // Execute system under test
        csiManager.activate();

        // Verify result
        assertTrue(csiManager.isActive(), "CsiManager should be active after calling activate()");
    }

    /**
     * Verifies deactivation updates CSI state.
     */
    @Test
    public void testDeactivate()
    {
        // Setup test fixture
        assertTrue(csiManager.isActive(), "CsiManager should be active");

        // Execute system under test
        csiManager.deactivate();

        // Verify result
        assertFalse(csiManager.isActive(), "CsiManager should be inactive after calling deactivate()");
    }

    /**
     * Verifies processing of active nonza.
     */
    @Test
    public void testProcessActiveNonza() throws Exception
    {
        // Setup test fixture
        csiManager.deactivate();
        final Element activeElement = parseElement("<active xmlns='urn:xmpp:csi:0'/>");

        // Execute system under test
        csiManager.process(activeElement);

        // Verify result
        assertTrue(csiManager.isActive(), "CsiManager should be active after processing active nonza");
    }

    /**
     * Verifies processing of inactive nonza.
     */
    @Test
    public void testProcessInactiveNonza() throws Exception
    {
        // Setup test fixture
        final Element inactiveElement = parseElement("<inactive xmlns='urn:xmpp:csi:0'/>");

        // Execute system under test
        csiManager.process(inactiveElement);

        // Verify result
        assertFalse(csiManager.isActive(), "CsiManager should be inactive after processing inactive nonza");
    }

    /**
     * Verifies initial queue size is zero.
     */
    @Test
    public void testInitialQueueSizeIsZero()
    {
        assertEquals(0, csiManager.getDelayQueueSize(), "Initial queue size should be zero");
    }

    /**
     * Verifies delay queue size after queueing.
     */
    @Test
    public void testQueueSizeAfterQueueing() throws Exception
    {
        // Setup test fixture
        csiManager.deactivate();
        final Packet delayableMessage = createDelayableMessage();

        // Execute system under test
        csiManager.queueOrPush(delayableMessage);

        // Verify result
        assertEquals(1, csiManager.getDelayQueueSize(), "Queue size should be 1 after queueing one message");
    }

    /**
     * Verifies queueOrPush behavior when active flushes immediately.
     */
    @Test
    public void testQueueOrPushWhenActiveFlushesImmediately() throws Exception
    {
        // Setup test fixture
        assertTrue(csiManager.isActive(), "CsiManager should be active");
        final Packet delayableMessage = createDelayableMessage();

        // Execute system under test
        final List<Packet> result = csiManager.queueOrPush(delayableMessage);

        // Verify result
        assertEquals(1, result.size(), "Should return the message immediately when active");
        assertEquals(0, csiManager.getDelayQueueSize(), "Queue should be empty when active");
    }

    /**
     * Verifies queueOrPush behavior when inactive queues.
     */
    @Test
    public void testQueueOrPushWhenInactiveQueues() throws Exception
    {
        // Setup test fixture
        csiManager.deactivate();
        final Packet delayableMessage = createDelayableMessage();

        // Execute system under test
        final List<Packet> result = csiManager.queueOrPush(delayableMessage);

        // Verify result
        assertTrue(result.isEmpty(), "Should queue the message when inactive");
        assertEquals(1, csiManager.getDelayQueueSize(), "Queue should contain the message");
    }

    /**
     * Verifies queueOrPush behavior when non delayable packet always flushed.
     */
    @Test
    public void testQueueOrPushNonDelayablePacketAlwaysFlushed() throws Exception
    {
        // Setup test fixture
        csiManager.deactivate();
        final Packet nonDelayableIQ = createIQ();

        // Execute system under test
        final List<Packet> result = csiManager.queueOrPush(nonDelayableIQ);

        // Verify result
        assertEquals(1, result.size(), "Should return non-delayable packet immediately");
        assertEquals(0, csiManager.getDelayQueueSize(), "Queue should remain empty");
    }

    /**
     * Verifies queueOrPush behavior when returns both queued and new packet when flushing.
     */
    @Test
    public void testQueueOrPushReturnsBothQueuedAndNewPacketWhenFlushing() throws Exception
    {
        // Setup test fixture: queue one delayable packet while inactive.
        csiManager.deactivate();
        final Packet queuedMessage = createDelayableMessage();
        final List<Packet> initiallyQueued = csiManager.queueOrPush(queuedMessage);

        assertTrue(initiallyQueued.isEmpty(), "First delayable message should be queued while client is inactive");
        assertEquals(1, csiManager.getDelayQueueSize(), "First delayable message should be queued");

        // Execute system under test: a non-delayable packet should force an immediate flush that returns both the
        // previously queued packet and the new packet.
        final Packet nonDelayableIQ = createIQ();
        final List<Packet> result = csiManager.queueOrPush(nonDelayableIQ);

        // Verify result
        assertEquals(2, result.size(), "Flushing should return both the queued packet and the new packet");
        assertTrue(result.contains(queuedMessage), "Flushed result should contain the previously queued packet");
        assertTrue(result.contains(nonDelayableIQ), "Flushed result should contain the new non-delayable packet");
        assertEquals(0, csiManager.getDelayQueueSize(), "Queue should be empty after flushing");
    }

    /**
     * Verifies activation updates CSI state and queued stanza behavior.
     */
    @Test
    public void testActivateWithQueuedMessagesFlushesAll() throws Exception
    {
        // Setup test fixture
        csiManager.deactivate();
        final Packet message1 = createDelayableMessage();
        final Packet message2 = createDelayableMessage();

        csiManager.queueOrPush(message1);
        csiManager.queueOrPush(message2);

        assertEquals(2, csiManager.getDelayQueueSize(), "Should have 2 messages queued");

        // Execute system under test
        csiManager.activate();

        // Verify result - after activation, the queue should be flushed
        // Note: activate() only delivers the tail, but that should trigger a flush as the delivery immediately invokes
        // the next operation. The queue should be empty after session.deliver() is called with the tail.
        assertEquals(0, csiManager.getDelayQueueSize(), "After activate, the delay queue should be flushed.");
    }

    /**
     * Verifies that an IQ stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayIQReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet iq = createIQ();

        // Execute system under test
        final boolean result = CsiManager.canDelay(iq);

        // Verify result
        assertFalse(result, "IQ stanzas should not be delayable");
    }

    /**
     * Verifies that an available presence stanza <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayAvailablePresenceReturnsTrue() throws Exception
    {
        // Setup test fixture - Note: Presence with type other than null/unavailable is not delayable
        final Packet presenceAvailable = parse("""
            <presence to="user@example.com">
            </presence>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(presenceAvailable);

        // Verify result
        assertTrue(result, "Available presence (type=null) should be delayable");
    }

    /**
     * Verifies that an unavailable presence stanza <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayUnavailablePresenceReturnsTrue() throws Exception
    {
        // Setup test fixture
        final Packet presenceUnavailable = parse("""
            <presence type="unavailable" to="user@example.com"/>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(presenceUnavailable);

        // Verify result
        assertTrue(result, "Unavailable presence should be delayable");
    }

    /**
     * Verifies that a subscription request presence stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayPresenceWithOtherTypeReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet presenceSubscribed = parse("""
            <presence type="subscribe" to="user@example.com"/>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(presenceSubscribed);

        // Verify result
        assertFalse(result, "Presence with type other than null/unavailable should not be delayable");
    }

    /**
     * Verifies that a MUC self-presence stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelaySelfPresenceFromMUCReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet selfPresence = parse("""
            <presence type="unavailable" to="user@example.com">
               <x xmlns="http://jabber.org/protocol/muc#user">
                  <status code="110"/>
               </x>
            </presence>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(selfPresence);

        // Verify result
        assertFalse(result, "MUC self-presence should not be delayable");
    }

    /**
     * Verifies that a message stanza with a body <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayMessageWithBodyReturnsFalse() throws Exception
    {
        // Setup test fixture - Messages WITH body are important and not delayable
        final Packet messageWithBody = parse("""
            <message type="chat" to="user@example.com">
               <body>Hello, World!</body>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(messageWithBody);

        // Verify result
        assertFalse(result, "Messages with body should not be delayable");
    }

    /**
     * Verifies that a chat state notification stanza <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayChatStateNotificationReturnsTrue() throws Exception
    {
        // Setup test fixture - Messages WITHOUT body (like chat state notifications) are delayable
        final Packet chatStateNotification = parse("""
            <message type="chat" to="user@example.com">
               <composing xmlns="http://jabber.org/protocol/chatstates"/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(chatStateNotification);

        // Verify result
        assertTrue(result, "Chat state notifications (messages without body) should be delayable");
    }

    /**
     * Verifies that a typing notification stanza <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayTypingNotificationReturnsTrue() throws Exception
    {
        // Setup test fixture - Typing notifications (paused) should be delayable
        final Packet typingPaused = parse("""
            <message type="chat" to="user@example.com">
               <paused xmlns="http://jabber.org/protocol/chatstates"/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(typingPaused);

        // Verify result
        assertTrue(result, "Typing paused notifications should be delayable");
    }

    /**
     * Verifies that a message delivery receipt stanza <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayReceiptNotificationReturnsTrue() throws Exception
    {
        // Setup test fixture - Message delivery receipts should be delayable
        final Packet receipt = parse("""
            <message type="normal" to="user@example.com">
               <received xmlns="urn:xmpp:receipts" id="message-id"/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(receipt);

        // Verify result
        assertTrue(result, "Message delivery receipts should be delayable");
    }

    /**
     * Verifies that a (non-empty) MUC room subject change stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayMUCRoomSubjectReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet roomSubject = parse("""
            <message type="groupchat" to="user@example.com">
               <subject>Room Subject</subject>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(roomSubject);

        // Verify result
        assertFalse(result, "MUC room subject should not be delayable");
    }

    /**
     * Verifies that an empty MUC room subject change stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayEmptyMUCRoomSubjectReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet emptyRoomSubject = parse("""
            <message type="groupchat" to="user@example.com">
               <subject/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(emptyRoomSubject);

        // Verify result
        assertFalse(result, "Empty MUC room subject should not be delayable");
    }

    /**
     * Verifies that a MUC room invitation stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayMUCInvitationReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet mucInvitation = parse("""
            <message type="normal" to="user@example.com">
               <x xmlns="http://jabber.org/protocol/muc#user">
                  <invite from="alice@example.com">
                     <reason>Come join our groupchat!</reason>
                  </invite>
               </x>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(mucInvitation);

        // Verify result
        assertFalse(result, "MUC invitations should not be delayable");
    }

    /**
     * Verifies that an OMEMO-encrypted message stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayOMEMOMessageReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet omemoMessage = parse("""
            <message type="chat" to="user@example.com">
               <encrypted xmlns="urn:xmpp:omemo:0">
                  <header sid="123">
                     <key rid="1">base64key</key>
                  </header>
                  <payload>base64payload</payload>
               </encrypted>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(omemoMessage);

        // Verify result
        assertFalse(result, "OMEMO encrypted messages should not be delayable");
    }

    /**
     * Verifies that a stanza Indicating Intent to Start a Session (Jingle) is <em>not</em> identified as a stanza that can be delayed/queued in context of CSI.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2750">OF-2750: CSI-enabled client does not receive Jingle invitations</a>
     */
    @Test
    public void testCanDelayJingleProposeReturnsFalse() throws Exception
    {
        // Setup test fixture.
        final Packet input = parse("""
            <message type="chat" id="jm-propose-LE3clSJQobTiFcrAoSD52" to="user@example.com">
               <propose xmlns="urn:xmpp:jingle-message:0" id="LE3clSJQobTiFcrAoNLR2A">
                  <description xmlns="urn:xmpp:jingle:apps:rtp:1" media="audio" />
                  <description xmlns="urn:xmpp:jingle:apps:rtp:1" media="video" />
               </propose>
               <request xmlns="urn:xmpp:receipts" />
               <store xmlns="urn:xmpp:hints" />
            </message>""");

        // Execute system under test.
        final boolean result = CsiManager.canDelay(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Verifies that a Jingle 'accept' stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayJingleAcceptReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet jingleAccept = parse("""
            <message type="chat" to="user@example.com">
               <accept xmlns="urn:xmpp:jingle-message:0" id="LE3clSJQobTiFcrAoNLR2A"/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(jingleAccept);

        // Verify result
        assertFalse(result, "Jingle accept messages should not be delayable");
    }

    /**
     * Verifies that a Jingle 'reject' stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayJingleRejectReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet jingleReject = parse("""
            <message type="chat" to="user@example.com">
               <reject xmlns="urn:xmpp:jingle-message:0" id="LE3clSJQobTiFcrAoNLR2A"/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(jingleReject);

        // Verify result
        assertFalse(result, "Jingle reject messages should not be delayable");
    }

    /**
     * Verifies that a group chat message stanza with a body <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayProceedingToGroupChatReturnsFalse() throws Exception
    {
        // Setup test fixture - Groupchat messages with body are not delayable
        final Packet groupchatMessage = parse("""
            <message type="groupchat" to="muc@example.com/nick">
               <body>Group message</body>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(groupchatMessage);

        // Verify result
        assertFalse(result, "Groupchat messages with body are not delayable");
    }

    /**
     * Verifies that a headline message stanza <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayHeadlineMessageReturnsTrue() throws Exception
    {
        // Setup test fixture - Headline messages should be delayable
        final Packet headlineMessage = parse("""
            <message type="headline" to="user@example.com">
               <subject>News Headline</subject>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(headlineMessage);

        // Verify result
        assertTrue(result, "Headline messages without body should be delayable");
    }

    /**
     * Verifies that an HTTP File Upload slot response (an IQ stanza) <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayHTTPUploadSlotResponseReturnsFalse() throws Exception
    {
        // Setup test fixture - HTTP Upload slot responses contain important data
        final Packet uploadSlot = parse("""
            <iq type="result" to="user@example.com">
               <slot xmlns="urn:xmpp:http:upload:0">
                  <get url="https://upload.example.com/file"/>
                  <put url="https://upload.example.com/file"/>
               </slot>
            </iq>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(uploadSlot);

        // Verify result
        assertFalse(result, "IQ stanzas should not be delayable");
    }

    /**
     * Verifies that a 'subscribed' presence stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelaySubscribedPresenceReturnsFalse() throws Exception
    {
        // Setup test fixture - Subscribed presence (type='subscribed') is important
        final Packet subscribedPresence = parse("""
            <presence type="subscribed" to="user@example.com"/>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(subscribedPresence);

        // Verify result
        assertFalse(result, "Subscribed presence should not be delayable");
    }

    /**
     * Verifies that a 'subscribe' presence stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelaySubscriptionPresenceReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet subscribePresence = parse("""
            <presence type="subscribe" to="user@example.com"/>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(subscribePresence);

        // Verify result
        assertFalse(result, "Subscription presence should not be delayable");
    }

    /**
     * Verifies that an 'unsubscribe' presence stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayUnsubscribePresenceReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet unsubscribePresence = parse("""
            <presence type="unsubscribe" to="user@example.com"/>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(unsubscribePresence);

        // Verify result
        assertFalse(result, "Unsubscribe presence should not be delayable");
    }

    /**
     * Verifies that a 'probe' presence stanza <em>is not</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayProbePresenceReturnsFalse() throws Exception
    {
        // Setup test fixture
        final Packet probePresence = parse("""
            <presence type="probe" to="user@example.com"/>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(probePresence);

        // Verify result
        assertFalse(result, "Probe presence should not be delayable");
    }

    /**
     * Verifies that a message stanza without a body <em>is</em> identified as a stanza that can be delayed/queued in context of CSI.
     */
    @Test
    public void testCanDelayNormalMessageWithoutBodyReturnsTrue() throws Exception
    {
        // Setup test fixture - Normal (type not specified or type="normal") messages without body
        final Packet normalMessage = parse("""
            <message type="normal" to="user@example.com">
               <x xmlns="some:namespace"/>
            </message>""");

        // Execute system under test
        final boolean result = CsiManager.canDelay(normalMessage);

        // Verify result
        assertTrue(result, "Normal messages without body should be delayable");
    }

    /**
     * Verifies CSI nonza recognition for an 'active' element.
     */
    @Test
    public void testIsCsiNonzaWithActiveElement() throws Exception
    {
        // Setup test fixture
        final Element activeElement = parseElement("<active xmlns='urn:xmpp:csi:0'/>");

        // Execute system under test
        final boolean result = CsiManager.isCsiNonza(activeElement);

        // Verify result
        assertTrue(result, "Active element should be recognized as CSI nonza");
    }

    /**
     * Verifies CSI nonza recognition for an 'inactive' element.
     */
    @Test
    public void testIsCsiNonzaWithInactiveElement() throws Exception
    {
        // Setup test fixture
        final Element inactiveElement = parseElement("<inactive xmlns='urn:xmpp:csi:0'/>");

        // Execute system under test
        final boolean result = CsiManager.isCsiNonza(inactiveElement);

        // Verify result
        assertTrue(result, "Inactive element should be recognized as CSI nonza");
    }

    /**
     * Verifies CSI nonza recognition with an invalid namespace.
     */
    @Test
    public void testIsCsiNonzaWithInvalidNamespace() throws Exception
    {
        // Setup test fixture
        final Element invalidElement = parseElement("<active xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>");

        // Execute system under test
        final boolean result = CsiManager.isCsiNonza(invalidElement);

        // Verify result
        assertFalse(result, "Element with invalid namespace should not be recognized as CSI nonza");
    }

    /**
     * Verifies CSI nonza recognition with an invalid name.
     */
    @Test
    public void testIsCsiNonzaWithInvalidName() throws Exception
    {
        // Setup test fixture
        final Element invalidElement = parseElement("<invalid xmlns='urn:xmpp:csi:0'/>");

        // Execute system under test
        final boolean result = CsiManager.isCsiNonza(invalidElement);

        // Verify result
        assertFalse(result, "Element with invalid name should not be recognized as CSI nonza");
    }

    /**
     * Verifies CSI nonza recognition with a null element.
     */
    @Test
    public void testIsCsiNonzaWithNullElement()
    {
        // Execute system under test
        final boolean result = CsiManager.isCsiNonza(null);

        // Verify result
        assertFalse(result, "Null element should not be recognized as CSI nonza");
    }

    /**
     * Verifies CSI nonza recognition with a message element.
     */
    @Test
    public void testIsCsiNonzaWithMessageElement() throws Exception
    {
        // Setup test fixture
        final Packet message = createDelayableMessage();
        final Element messageElement = message.getElement();

        // Execute system under test
        final boolean result = CsiManager.isCsiNonza(messageElement);

        // Verify result
        assertFalse(result, "Message element should not be recognized as CSI nonza");
    }

    /**
     * Creates a delayable message for testing purposes.
     *
     * This is a chat state notification (message without body), which is delayable.
     */
    private Packet createDelayableMessage() throws Exception
    {
        return parse("""
            <message type="chat" to="user@example.com">
               <composing xmlns="http://jabber.org/protocol/chatstates"/>
            </message>""");
    }

    /**
     * Creates an IQ stanza for testing purposes.
     */
    private Packet createIQ() throws Exception
    {
        return parse("""
            <iq type="get" to="user@example.com">
               <query xmlns="jabber:iq:roster"/>
            </iq>""");
    }

    /**
     * Parses an element from a string representation.
     */
    private Element parseElement(final String input) throws DocumentException, XmlPullParserException, IOException
    {
        final XMPPPacketReader reader = new XMPPPacketReader();
        return reader.read(new StringReader(input)).getRootElement();
    }

    /**
     * Tries to parse a stanza from an input text. This method throws an exception when the input cannot be parsed.
     *
     * @param input The text to be parsed
     * @return the stanza that resulted from parsing
     */
    private static Packet parse(final String input) throws DocumentException, XmlPullParserException, IOException
    {
        final XMPPPacketReader reader = new XMPPPacketReader();
        final Element element = reader.read(new StringReader(input)).getRootElement();
        return switch (element.getName()) {
            case "presence" -> new Presence(element, true);
            case "iq"       -> new IQ(element, true);
            case "message"  -> new Message(element, true);
            default -> throw new IllegalStateException("Unexpected element name: " + element.asXML());
        };
    }
}
