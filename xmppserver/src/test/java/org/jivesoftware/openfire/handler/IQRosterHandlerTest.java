/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.handler;

import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Roster;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that verify the functionality as implemented in {@link IQRosterHandler}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class IQRosterHandlerTest
{
    /**
     * Asserts that IQRosterHandler#testIgnoreNonRemoveSubscriptionValues removes a subscription attribute of which the
     * value is not 'remove'.
     */
    @Test
    public void testSubscriptionTo() throws Exception
    {
        // Setup test fixture.
        final Roster input = new Roster();
        input.setType(IQ.Type.set);
        final JID address = new JID("foo", "example.org", null);
        final Roster.Subscription subscription = Roster.Subscription.to;
        input.addItem(address, subscription);

        // Execute system under test.
        final Roster result = IQRosterHandler.ignoreNonRemoveSubscriptionValues(input);

        // Verify results.
        assertTrue(result.getItems().stream().anyMatch(item -> item.getJID().equals(address)));
        assertFalse(result.getItems().stream().anyMatch(item -> item.getJID().equals(address) && subscription.equals(item.getSubscription())));
    }

    /**
     * Asserts that IQRosterHandler#testIgnoreNonRemoveSubscriptionValues does _not_ remove a subscription attribute of
     * which the value is 'remove'.
     */
    @Test
    public void testSubscriptionRemove() throws Exception
    {
        // Setup test fixture.
        final Roster input = new Roster();
        input.setType(IQ.Type.set);
        final JID address = new JID("foo", "example.org", null);
        final Roster.Subscription subscription = Roster.Subscription.remove;
        input.addItem(address, subscription);

        // Execute system under test.
        final Roster result = IQRosterHandler.ignoreNonRemoveSubscriptionValues(input);

        // Verify results.
        assertTrue(result.getItems().stream().anyMatch(item -> item.getJID().equals(address) && subscription.equals(item.getSubscription())));
    }
}
