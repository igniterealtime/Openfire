/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 */

package org.jivesoftware.util;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;

import org.dom4j.Element;
import org.jivesoftware.admin.AdminConsole;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class AdminConsoleTest {

    /**
     * Resets the admin console internal data structures.
     */
    @After
    public void tearDown() throws Exception {
        Class c = AdminConsole.class;
        Method init = c.getDeclaredMethod("load", (Class[])null);
        init.setAccessible(true);
        init.invoke(null, (Object[])null);
    }

    @Test
    @Ignore
    public void testGetGlobalProps() throws Exception {
        String name = AdminConsole.getAppName();
        String image = AdminConsole.getLogoImage();
        assertEquals("Openfire", name);
        assertEquals("images/header-title.gif", image);
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
        Collection tabs = AdminConsole.getModel().selectNodes("//tab");
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
