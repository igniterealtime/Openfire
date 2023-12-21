/*
 * Copyright (C) 2004-2007 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.util;

import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for the JID class.
 *
 * @author Gaston Dombiak
 */
public class JIDTest {

    @Test
    public void testDomain() {
        new JID("mycomapny.com");
        new JID("wfink-adm");

        boolean failed = false;
        try {
            new JID("wfink adm");
        } catch (Exception e) {
            failed = true;
        }
        assertTrue(failed, "A domain with spaces was accepted");

        failed = false;
        try {
            new JID("wfink_adm");
        } catch (Exception e) {
            failed = true;
        }
        assertTrue(failed, "A domain with _ was accepted");
    }

    @Test
    public void testUsernames() {
        new JID("john@mycomapny.com");
        new JID("john_paul@mycomapny.com");
        new JID("john-paul@mycomapny.com");
        boolean failed = false;
        try {
            new JID("john paul@mycomapny.com");
        } catch (Exception e) {
            failed = true;
        }
        assertTrue(failed, "A username with spaces was accepted");
    }

    @Test
    public void testCompare() {
        JID jid1 = new JID("john@mycomapny.com");
        JID jid2 = new JID("john@mycomapny.com");
        assertEquals(0 , jid1.compareTo(jid2), "Failed to compare 2 similar JIDs");
        assertEquals(jid1, jid2, "Failed to recognize equal JIDs");

        jid1 = new JID("john@mycomapny.com");
        jid2 = new JID("mycomapny.com");
        assertTrue(jid1.compareTo(jid2) > 0, "Failed to recognized bigger JID");
        assertNotEquals(jid1, jid2, "Failed to recognize different JIDs");

        jid1 = new JID("john@mycomapny.com");
        jid2 = new JID("mycomapny.com/resource");
        assertTrue(jid1.compareTo(jid2) > 0, "Failed to recognized bigger JID");
        assertNotEquals(jid1, jid2, "Failed to recognize different JIDs");

        jid1 = new JID("john@mycomapny.com");
        jid2 = new JID("john@mycomapny.com/resource");
        assertTrue(jid1.compareTo(jid2) < 0, "Failed to recognized bigger JID");
        assertNotEquals(jid1, jid2, "Failed to recognize different JIDs");
    }
}
