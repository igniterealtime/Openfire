/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.group;

import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import static org.junit.jupiter.api.Assertions.*;

public class GroupJIDTest {

    @Test
    public void testBase32Encoding() {
        
        String testGroupName = "Test Group (1)";
        String testDomainName = "localhost";

        String testBase32GroupName = StringUtils.encodeBase32(testGroupName);
        
        // no need for JID escaping
        JID testJid = new JID(testBase32GroupName, testDomainName, null);
        assertEquals(testBase32GroupName, testJid.getNode());
        
        String testDecodedGroupName = new String(StringUtils.decodeBase32(testJid.getNode()));
        assertEquals(testGroupName, testDecodedGroupName);
    }

    @Test
    public void testBase32Alphabet() {
        String testABC = "ABC";
        assertTrue(StringUtils.isBase32(testABC));

        String test123 = "123";
        assertTrue(StringUtils.isBase32(test123));

        // should be case insensitve
        String testabc = "abc";
        assertTrue(StringUtils.isBase32(testabc));

        String testXYZ = "XYZ";
        assertFalse(StringUtils.isBase32(testXYZ));
    }

    @Test
    public void testParseGroupJIDFromString() {
        String testGroupName = "Test Group (2);  - now with *special* =characters= too!";
        JID testJid = new JID(StringUtils.encodeBase32(testGroupName), "localhost", StringUtils.hash(testGroupName));
        
        assertTrue(GroupJID.isGroup(testJid));
        assertEquals(testGroupName, ((GroupJID)GroupJID.fromString(testJid.toString())).getGroupName());
    }

}
