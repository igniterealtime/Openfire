/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import junit.framework.TestCase;
import org.jivesoftware.openfire.stun.StunServerAddress;

import java.util.List;
import java.util.ArrayList;

public class STUNServerTest extends TestCase {

    public void testEqualsStunServerAddress() {

        StunServerAddress addr0 = new StunServerAddress("apollo", "10000");
        StunServerAddress addr1 = new StunServerAddress("apollo", "10000");
        StunServerAddress addr2 = new StunServerAddress("63.246.20.124", "10002");
        StunServerAddress addr3 = new StunServerAddress("jivesoftware.com", "10002");
        StunServerAddress addr4 = new StunServerAddress("jivesoftware.com", "10003");

        assertTrue(addr0.equals(addr1));
        assertFalse(addr0.equals(addr2));
        assertTrue(addr2.equals(addr3));
        assertFalse(addr3.equals(addr4));

        List<StunServerAddress> list = new ArrayList<StunServerAddress>();
        list.add(addr0);
        list.add(addr1);
        list.add(addr2);
        list.add(addr4);
        assertTrue(list.contains(addr3));

    }


}
