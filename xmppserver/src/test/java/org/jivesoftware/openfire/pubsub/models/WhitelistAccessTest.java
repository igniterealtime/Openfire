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
package org.jivesoftware.openfire.pubsub.models;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhitelistAccessTest {

    private final WhitelistAccess access = new WhitelistAccess();
    private final Node node = mock(Node.class);
    private final NodeAffiliate affiliate = mock(NodeAffiliate.class);
    private final JID user = new JID("user@example.org");

    @Test
    void memberCanSubscribe() {
        when(node.getAffiliate(user)).thenReturn(affiliate);
        when(affiliate.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.member);

        assertTrue(access.canSubscribe(node, user, user));
    }

    @Test
    void noneAffiliationIsNotWhitelisted() {
        when(node.getAffiliate(user)).thenReturn(affiliate);
        when(affiliate.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.none);

        assertFalse(access.canSubscribe(node, user, user));
    }
}
