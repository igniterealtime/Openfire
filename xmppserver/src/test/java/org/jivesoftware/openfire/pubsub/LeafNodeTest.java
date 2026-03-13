/*
 * Copyright (C) 2020-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link LeafNode}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class LeafNodeTest
{
    /** Creates a minimal LeafNode for use in tests. */
    private static LeafNode createTestLeafNode() {
        final DefaultNodeConfiguration config = new DefaultNodeConfiguration(true);
        return new LeafNode(
            new PubSubService.UniqueIdentifier("test-service-id"),
            null,
            "test-node-id",
            new JID("unit-test@example.org"),
            config);
    }

    @Test
    public void testSerialization() throws Exception
    {
        // Setup fixture.
        final DefaultNodeConfiguration config = new DefaultNodeConfiguration(true);
        config.setDeliverPayloads( !config.isDeliverPayloads() );  // invert all defaults to improve test coverage.
        config.setMaxPayloadSize( 98732 );
        config.setPersistPublishedItems( !config.isPersistPublishedItems() );
        config.setMaxPublishedItems( 13461 );
        config.setNotifyConfigChanges( !config.isNotifyConfigChanges());
        config.setNotifyDelete( !config.isNotifyDelete() );
        config.setNotifyRetract( !config.isNotifyRetract() );
        config.setPresenceBasedDelivery( !config.isPresenceBasedDelivery() );
        config.setSendItemSubscribe( !config.isSendItemSubscribe() );
        config.setPublisherModel(PublisherModel.subscribers );
        config.setSubscriptionEnabled( !config.isSubscriptionEnabled() );
        config.setAccessModel(AccessModel.whitelist );
        config.setLanguage( "nl_NL" );
        config.setReplyPolicy(Node.ItemReplyPolicy.publisher );
        config.setAssociationPolicy(CollectionNode.LeafNodeAssociationPolicy.whitelist );
        final LeafNode input = new LeafNode( new PubSubService.UniqueIdentifier( "test-service-id" ), null, "test-node-id", new JID( "unit-test@example.org"), config);

        // Execute system under test.
        final Object result = CollectionNodeTest.serializeAndDeserialize( input );

        // Verify result.
        assertNotNull( result );
        assertTrue( result instanceof LeafNode );
        assertEquals( input, result );
    }

    // =========================================================================
    // setLastPublishedItem / getPublishedItem — XEP-0060 §7.1.2 compliance
    //
    // When a publisher publishes an item with the same ItemID as a previously
    // published item, the server MUST overwrite the old item with the new one.
    // The in-memory 'lastPublished' cache must reflect the new item immediately,
    // even if both items share the same creation-date timestamp.
    // =========================================================================

    /**
     * Regression test for XEP-0060 §7.1.2: publishing a second item with the same
     * ItemID must update the in-memory {@code lastPublished} reference, even when
     * the two items share the same creation-date (published within the same
     * millisecond, as is common in fast sequential integration tests).
     *
     * <p>Before the fix, {@code setLastPublishedItem} only updated {@code lastPublished}
     * when {@code item.getCreationDate().after(lastPublished.getCreationDate())} returned
     * {@code true}. If both items had the same timestamp the old item remained cached,
     * causing {@code getPublishedItem()} to serve the stale first-published payload.
     */
    @Test
    public void testSetLastPublishedItem_SameIdSameTimestamp_UpdatesCache() {
        final LeafNode node = createTestLeafNode();
        final JID publisher = new JID("user@example.org");
        final Date sharedTimestamp = new Date(1_000_000L);

        // First publish: item1 with itemID="shared-id", payload conceptually "payload-1".
        final PublishedItem item1 = new PublishedItem(node, publisher, "shared-id", sharedTimestamp);
        node.setLastPublishedItem(item1);

        // Sanity: lastPublished should now be item1.
        assertSame(item1, node.getLastPublishedItem(),
            "After first publish, lastPublished must be item1");

        // Second publish: item2 with SAME itemID and SAME timestamp (typical in fast tests).
        final PublishedItem item2 = new PublishedItem(node, publisher, "shared-id", sharedTimestamp);
        node.setLastPublishedItem(item2);

        // Assert: lastPublished must be updated to item2 (the overwriting item).
        // Before the fix this assertion would fail because the creation-date guard
        // prevented the update when both items shared the same timestamp.
        assertSame(item2, node.getLastPublishedItem(),
            "XEP-0060 §7.1.2: re-publishing with the same ItemID must update lastPublished " +
            "even when both items have the same creation-date");
    }

    /**
     * Complementary test: publishing an item with a different ItemID but the same
     * timestamp must NOT overwrite {@code lastPublished}. Only items with newer
     * timestamps (or the same ID) should replace it.
     */
    @Test
    public void testSetLastPublishedItem_DifferentIdSameTimestamp_DoesNotOverwrite() {
        final LeafNode node = createTestLeafNode();
        final JID publisher = new JID("user@example.org");
        final Date sharedTimestamp = new Date(1_000_000L);

        final PublishedItem item1 = new PublishedItem(node, publisher, "id-alpha", sharedTimestamp);
        node.setLastPublishedItem(item1);

        // A different item with a different ID but the same timestamp.
        final PublishedItem item2 = new PublishedItem(node, publisher, "id-beta", sharedTimestamp);
        node.setLastPublishedItem(item2);

        // item1 was the last published (by time) and has a different ID,
        // so it should remain as lastPublished (item2 is not newer).
        assertSame(item1, node.getLastPublishedItem(),
            "A different item published at the same timestamp must not displace lastPublished");
    }

    /**
     * Sanity test: publishing a newer item (strictly later timestamp) with a
     * different ID must update {@code lastPublished} as before.
     */
    @Test
    public void testSetLastPublishedItem_DifferentIdNewerTimestamp_UpdatesCache() {
        final LeafNode node = createTestLeafNode();
        final JID publisher = new JID("user@example.org");

        final PublishedItem item1 = new PublishedItem(node, publisher, "id-alpha", new Date(1_000L));
        node.setLastPublishedItem(item1);

        final PublishedItem item2 = new PublishedItem(node, publisher, "id-beta", new Date(2_000L));
        node.setLastPublishedItem(item2);

        assertSame(item2, node.getLastPublishedItem(),
            "A newer item (later timestamp, different ID) must update lastPublished");
    }
}
