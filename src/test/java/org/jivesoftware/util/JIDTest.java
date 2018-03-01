/*
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.junit.Test;
import org.xmpp.packet.JID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertTrue("A domain with spaces was accepted", failed);

        failed = false;
        try {
            new JID("wfink_adm");
        } catch (Exception e) {
            failed = true;
        }
        assertTrue("A domain with _ was accepted", failed);
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
        assertTrue("A username with spaces was accepted", failed);
    }

    @Test
    public void testCompare() {
        JID jid1 = new JID("john@mycomapny.com");
        JID jid2 = new JID("john@mycomapny.com");
        assertEquals("Failed to compare 2 similar JIDs", 0 , jid1.compareTo(jid2));
        assertEquals("Failed to recognize equal JIDs", jid1 , jid2);

        jid1 = new JID("john@mycomapny.com");
        jid2 = new JID("mycomapny.com");
        assertTrue("Failed to recognized bigger JID", jid1.compareTo(jid2) > 0);
        assertFalse("Failed to recognize different JIDs", jid1.equals(jid2));

        jid1 = new JID("john@mycomapny.com");
        jid2 = new JID("mycomapny.com/resource");
        assertTrue("Failed to recognized bigger JID", jid1.compareTo(jid2) > 0);
        assertFalse("Failed to recognize different JIDs", jid1.equals(jid2));

        jid1 = new JID("john@mycomapny.com");
        jid2 = new JID("john@mycomapny.com/resource");
        assertTrue("Failed to recognized bigger JID", jid1.compareTo(jid2) < 0);
        assertFalse("Failed to recognize different JIDs", jid1.equals(jid2));

    }
}
