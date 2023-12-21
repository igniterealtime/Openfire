/*
 * Copyright (C) 2004-2008 Jive Software. 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.admin.AdminConsole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class AdminConsoleTest {

    /**
     * Resets the admin console internal data structures.
     */
    @AfterEach
    public void tearDown() throws Exception {
        Class<AdminConsole> c = AdminConsole.class;
        Method init = c.getDeclaredMethod("load", (Class<?>[])null);
        init.setAccessible(true);
        init.invoke(null, (Object[])null);
    }

    @Test
    public void testModifyGlobalProps() throws Exception {
        // Add a new stream to the AdminConsole:
        try (InputStream in = getClass().getResourceAsStream("/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-01.xml")) {
            AdminConsole.addModel("test1", in);
        }
        String name = AdminConsole.getAppName();
        assertEquals("Foo Bar", name);
        String img = AdminConsole.getLogoImage();
        assertEquals("foo.gif", img);
    }

    @Test
    public void testNewTabs() throws Exception {
        // Add a new stream to the AdminConsole:
        try (InputStream in = getClass().getResourceAsStream("/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-02.xml")) {
            AdminConsole.addModel("test2", in);
        }
        Collection<Node> tabs = AdminConsole.getModel().selectNodes("//tab");
        assertNotNull(tabs);
        assertTrue(tabs.size() > 0);
        boolean found = false;
        for (Object tab1 : tabs) {
            Element tab = (Element) tab1;
            if ("foobar".equals(tab.attributeValue("id"))) {
                found = true;
                assertEquals("Foo Bar", tab.attributeValue("name"));
                assertEquals("Click to see foo bar", tab.attributeValue("description"));
            }
        }
        if (!found) {
            fail("Expected new item 'foobar' was not found.");
        }
    }

    @Test
    public void testTabOverwrite() throws Exception {
        // Add a new stream to the AdminConsole:
        try (InputStream in = getClass().getResourceAsStream("/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-03.xml")) {
            AdminConsole.addModel("test3", in);
        }
        boolean found = false;
        for (Object o : AdminConsole.getModel().selectNodes("//tab")) {
            Element tab = (Element) o;
            if ("server".equals(tab.attributeValue("id"))) {
                found = true;
                assertEquals("New Server Title", tab.attributeValue("name"));
                assertEquals("Testing 1 2 3", tab.attributeValue("description"));
            }
        }
        if (!found) {
            fail("Failed to overwrite 'server' tab with new properties.");
        }
    }
}
