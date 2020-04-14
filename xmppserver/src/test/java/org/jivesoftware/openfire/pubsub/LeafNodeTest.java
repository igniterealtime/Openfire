/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
import org.junit.Test;
import org.xmpp.packet.JID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;

/**
 * Verifies the implementation of {@link LeafNode}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class LeafNodeTest
{
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
}
