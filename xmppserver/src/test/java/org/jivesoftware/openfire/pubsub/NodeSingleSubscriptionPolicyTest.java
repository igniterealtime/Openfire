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

package org.jivesoftware.openfire.pubsub;

import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies subscription uniqueness rules when multi-subscribe is disabled.
 *
 * XEP-0060 §6.1.6 defines multi-subscribe as allowing more than one subscription for the same literal subscription JID
 * value. This means that, even when multi-subscribe is disabled, a single owner can still have separate subscriptions
 * for distinct bare/full subscription JIDs.
 */
public class NodeSingleSubscriptionPolicyTest
{
    private static final PubSubService.UniqueIdentifier SERVICE_ID = new PubSubService.UniqueIdentifier("test-service");
    private static final JID CREATOR = new JID("creator@example.org");
    private static final JID OWNER = new JID("user@example.org");
    private static final JID DESKTOP = new JID("user@example.org/desktop");
    private static final JID MOBILE = new JID("user@example.org/mobile");

    /**
     * Verifies that, when multi-subscribe is disabled, one owner can still have separate subscriptions
     * for different subscription JID values.
     */
    @Test
    public void testSameOwnerCanHaveSubscriptionsForDistinctFullJidsWhenMultiSubscribeDisabled()
    {
        // Setup test fixture.
        final RecordingPubSubService service = new RecordingPubSubService(false);
        final TestLeafNode node = new TestLeafNode(service, "test-node");

        // Execute system under test.
        node.createSubscription(null, OWNER, DESKTOP, false, null);
        node.createSubscription(null, OWNER, MOBILE, false, null);

        // Verify results.
        final Collection<NodeSubscription> ownerSubscriptions = node.getSubscriptions(OWNER);
        assertEquals(2, ownerSubscriptions.size(), "The owner should have one subscription for each distinct subscription JID.");

        assertNotNull(node.getSubscription(DESKTOP));
        assertNotNull(node.getSubscription(MOBILE));
        assertNotSame(node.getSubscription(DESKTOP), node.getSubscription(MOBILE));

        assertEquals(DESKTOP, node.getSubscription(DESKTOP).getJID());
        assertEquals(MOBILE, node.getSubscription(MOBILE).getJID());
    }

    /**
     * Verifies that a repeated subscribe for an identical subscription JID does not create a second subscription when
     * multi-subscribe is disabled.
     */
    @Test
    public void testDuplicateSubscribeForIdenticalJidDoesNotCreateSecondSubscriptionWhenMultiSubscribeDisabled()
    {
        // Setup test fixture.
        final RecordingPubSubService service = new RecordingPubSubService(false);
        final TestLeafNode node = new TestLeafNode(service, "test-node");

        // Execute system under test.
        node.createSubscription(null, OWNER, DESKTOP, false, null);
        final NodeSubscription originalSubscription = node.getSubscription(DESKTOP);

        final IQ duplicateRequest = new IQ(IQ.Type.set);
        duplicateRequest.setFrom(DESKTOP);
        duplicateRequest.setTo(service.getAddress());
        duplicateRequest.setID("duplicate-subscribe");
        duplicateRequest.setChildElement("pubsub", "http://jabber.org/protocol/pubsub")
            .addElement("subscribe")
            .addAttribute("node", node.getUniqueIdentifier().getNodeId())
            .addAttribute("jid", DESKTOP.toString());

        node.createSubscription(duplicateRequest, OWNER, DESKTOP, false, null);

        // Verify results.
        assertEquals(1, node.getSubscriptions(OWNER).size(), "A duplicate identical JID must not create a second subscription.");
        assertSame(originalSubscription, node.getSubscription(DESKTOP), "The original subscription must remain the current subscription.");

        assertEquals(1, service.sentPackets.size(), "The duplicate request should receive the current subscription state.");
        assertInstanceOf(IQ.class, service.sentPackets.get(0));
        final IQ response = (IQ) service.sentPackets.get(0);
        assertEquals(IQ.Type.result, response.getType());
        assertEquals("duplicate-subscribe", response.getID());
        assertEquals(DESKTOP.toString(), response.getChildElement().element("subscription").attributeValue("jid"));
        assertEquals(originalSubscription.getState().name(), response.getChildElement().element("subscription").attributeValue("subscription"));
    }

    /**
     * Verifies that owner-scoped retrieval returns all of an owner's subscriptions, including those that target
     * different full JIDs.
     */
    @Test
    public void testOwnerScopedSubscriptionRetrievalReturnsAllSubscriptionJidsForOwner()
    {
        // Setup test fixture.
        final RecordingPubSubService service = new RecordingPubSubService(false);
        final TestLeafNode node = new TestLeafNode(service, "test-node");

        node.createSubscription(null, OWNER, DESKTOP, false, null);
        node.createSubscription(null, OWNER, MOBILE, false, null);

        // Execute system under test.
        final Collection<NodeSubscription> subscriptions = node.getSubscriptions(OWNER);

        // Verify results.
        assertEquals(2, subscriptions.size());
        assertTrue(subscriptions.stream().anyMatch(subscription -> DESKTOP.equals(subscription.getJID())));
        assertTrue(subscriptions.stream().anyMatch(subscription -> MOBILE.equals(subscription.getJID())));
    }

    /**
     * Verifies that notification fan-out treats distinct subscription JIDs under the same owner as distinct recipients.
     */
    @Test
    public void testNodeEventNotificationFanOutAddressesEachDistinctSubscriptionJid()
    {
        // Setup test fixture.
        final RecordingPubSubService service = new RecordingPubSubService(false);
        final TestLeafNode node = new TestLeafNode(service, "test-node");

        node.createSubscription(null, OWNER, DESKTOP, false, null);
        node.createSubscription(null, OWNER, MOBILE, false, null);

        final Message notification = new Message();
        notification.addChildElement("event", "http://jabber.org/protocol/pubsub#event")
            .addElement("items")
            .addAttribute("node", node.getUniqueIdentifier().getNodeId());

        // Execute system under test.
        node.broadcastNodeEventForTest(notification);

        // Verify results.
        assertEquals(2, service.broadcastRecipients.size(), "Each distinct subscription JID should receive a notification.");
        assertTrue(service.broadcastRecipients.contains(DESKTOP));
        assertTrue(service.broadcastRecipients.contains(MOBILE));
    }

    /**
     * Verifies that collection nodes also allow separate subscriptions for distinct subscription JIDs under the same
     * owner when multi-subscribe is disabled.
     */
    @Test
    public void testCollectionNodeAllowsDistinctFullJidsForSameOwnerWhenMultiSubscribeDisabled()
    {
        // Setup test fixture.
        final RecordingPubSubService service = new RecordingPubSubService(false);
        final TestCollectionNode node = new TestCollectionNode(service, "test-collection-node");

        // Execute system under test.
        node.createSubscription(null, OWNER, DESKTOP, false, null);
        node.createSubscription(null, OWNER, MOBILE, false, null);

        // Verify results.
        assertEquals(2, node.getSubscriptions(OWNER).size());
        assertNotNull(node.getSubscription(DESKTOP));
        assertNotNull(node.getSubscription(MOBILE));
        assertNotSame(node.getSubscription(DESKTOP), node.getSubscription(MOBILE));
    }

//    /**
//     * Verifies that looking up subscriptions by JID uses the subscription JID, not the owner JID.
//     */
//    @Test
//    public void testSubscriptionsByJidUsesExactSubscriptionJidNotOwner()
//    {
//        // Setup test fixture.
//        final RecordingPubSubService service = new RecordingPubSubService(false);
//        final TestLeafNode node = new TestLeafNode(service, "test-node");
//
//        node.createSubscription(null, OWNER, DESKTOP, false, null);
//        node.createSubscription(null, OWNER, MOBILE, false, null);
//
//        // Execute system under test and verify results.
//        assertEquals(1, node.getSubscriptionsByJID(DESKTOP).size());
//        assertEquals(1, node.getSubscriptionsByJID(MOBILE).size());
//        assertEquals(0, node.getSubscriptionsByJID(OWNER).size(), "Owner JID must not be used as the subscription-JID lookup key.");
//    }

//    /**
//     * Verifies that exact-JID lookup remains usable after cancelling one of multiple same-JID
//     * subscriptions in multi-subscribe mode.
//     */
//    @Test
//    public void testExactJidLookupAfterCancellingOneOfMultipleSameJidSubscriptions()
//    {
//        // Setup test fixture.
//        final RecordingPubSubService service = new RecordingPubSubService(true);
//        final TestLeafNode node = new TestLeafNode(service, "test-node");
//
//        node.createSubscription(null, OWNER, DESKTOP, false, null);
//        node.createSubscription(null, OWNER, DESKTOP, false, null);
//
//        final List<NodeSubscription> sameJidSubscriptions = new ArrayList<>(node.getSubscriptionsByJID(DESKTOP));
//        assertEquals(2, sameJidSubscriptions.size(), "Test fixture should have two subscriptions for the same literal subscription JID.");
//        assertThrows(IllegalStateException.class, () -> node.getSubscription(DESKTOP),
//            "Exact-JID lookup should be ambiguous before one same-JID subscription is cancelled.");
//
//        // Execute system under test.
//        node.cancelSubscription(sameJidSubscriptions.get(0), false);
//
//        // Verify results.
//        assertEquals(1, node.getSubscriptionsByJID(DESKTOP).size());
//        assertNotNull(node.getSubscription(DESKTOP), "Exact-JID lookup should return the remaining subscription.");
//        assertSame(sameJidSubscriptions.get(1), node.getSubscription(DESKTOP));
//    }

    private static final class TestLeafNode extends LeafNode
    {
        private final RecordingPubSubService service;

        private TestLeafNode(final RecordingPubSubService service, final String nodeID)
        {
            super(SERVICE_ID, null, nodeID, CREATOR, new DefaultNodeConfiguration(true));
            this.service = service;
        }

        @Override
        public PubSubService getService()
        {
            return service;
        }

        @Override
        public boolean isMultipleSubscriptionsEnabled()
        {
            return service.isMultipleSubscriptionsEnabled();
        }

        private void broadcastNodeEventForTest(final Message message)
        {
            broadcastNodeEvent(message, false);
        }
    }

    private static final class TestCollectionNode extends CollectionNode
    {
        private final RecordingPubSubService service;

        private TestCollectionNode(final RecordingPubSubService service, final String nodeID)
        {
            super(SERVICE_ID, null, nodeID, CREATOR, new DefaultNodeConfiguration(false));
            this.service = service;
        }

        @Override
        public PubSubService getService()
        {
            return service;
        }

        @Override
        public boolean isMultipleSubscriptionsEnabled()
        {
            return service.isMultipleSubscriptionsEnabled();
        }
    }

    private static final class RecordingPubSubService implements PubSubService
    {
        private final boolean multipleSubscriptionsEnabled;
        private final JID address = new JID("pubsub.example.org");
        private final List<Packet> sentPackets = new ArrayList<>();
        private final List<JID> broadcastRecipients = new ArrayList<>();
        private final Map<JID, Map<JID, String>> subscriberPresences = new LinkedHashMap<>();

        private RecordingPubSubService(final boolean multipleSubscriptionsEnabled)
        {
            this.multipleSubscriptionsEnabled = multipleSubscriptionsEnabled;
        }

        @Override
        public JID getAddress()
        {
            return address;
        }

        @Override
        public String getServiceID()
        {
            return SERVICE_ID.getServiceId();
        }

        @Override
        public Map<JID, Map<JID, String>> getSubscriberPresences()
        {
            return subscriberPresences;
        }

        @Override
        public boolean canCreateNode(final JID creator)
        {
            return true;
        }

        @Override
        public boolean isServiceAdmin(final JID user)
        {
            return false;
        }

        @Override
        public boolean isInstantNodeSupported()
        {
            return true;
        }

        @Override
        public boolean isCollectionNodesSupported()
        {
            return true;
        }

        @Override
        public CollectionNode getRootCollectionNode()
        {
            return null;
        }

        @Override
        public Node getNode(final Node.UniqueIdentifier nodeID)
        {
            return null;
        }

        @Override
        public Collection<Node> getNodes()
        {
            return Collections.emptyList();
        }

        @Override
        public void addNode(final Node node)
        {
        }

        @Override
        public void removeNode(final Node.UniqueIdentifier nodeID)
        {
        }

        @Override
        public void broadcast(final Node node, final Message message, final Collection<JID> jids)
        {
            broadcastRecipients.addAll(jids);
        }

        @Override
        public void send(final Packet packet)
        {
            sentPackets.add(packet);
        }

        @Override
        public void sendNotification(final Node node, final Message message, final JID jid)
        {
            broadcastRecipients.add(jid);
        }

        @Override
        public DefaultNodeConfiguration getDefaultNodeConfiguration(final boolean leafType)
        {
            return new DefaultNodeConfiguration(leafType);
        }

        @Override
        public Collection<String> getShowPresences(final JID subscriber)
        {
            return Collections.singleton("online");
        }

        @Override
        public void presenceSubscriptionRequired(final Node node, final JID user)
        {
        }

        @Override
        public void presenceSubscriptionNotRequired(final Node node, final JID user)
        {
        }

        @Override
        public boolean isMultipleSubscriptionsEnabled()
        {
            return multipleSubscriptionsEnabled;
        }

        @Override
        public AdHocCommandManager getManager()
        {
            return new AdHocCommandManager();
        }

        @Override
        @Nonnull
        public UniqueIdentifier getUniqueIdentifier()
        {
            return SERVICE_ID;
        }
    }
}
