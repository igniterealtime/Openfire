/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 */

package org.jivesoftware.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.lang.reflect.Method;
import junit.framework.TestCase;
import org.jivesoftware.admin.AdminConsole;
import org.dom4j.Element;

public class AdminConsoleTest extends TestCase {

    public AdminConsoleTest() {

    }

    /**
     * Resets the admin console internal data structures.
     */
    public void tearDown() throws Exception {
        Class c = AdminConsole.class;
        Method init = c.getDeclaredMethod("init", (Class[])null);
        init.setAccessible(true);
        init.invoke((Object)null, (Object[])null);
    }

    public void testGetGlobalProps() throws Exception {
        String name = AdminConsole.getAppName();
        String image = AdminConsole.getLogoImage();
        assertEquals("Openfire", name);
        assertEquals("images/header-title.gif", image);
    }

    public void testModifyGlobalProps() throws Exception {
        // Add a new stream to the AdminConsole:
        String filename = TestUtils.prepareFilename(
                "./resources/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-01.xml");
        InputStream in = new FileInputStream(filename);
        AdminConsole.addModel("test1", in);
        in.close();
        String name = AdminConsole.getAppName();
        assertEquals("Foo Bar", name);
        String img = AdminConsole.getLogoImage();
        assertEquals("foo.gif", img);
    }

    public void testNewTabs() throws Exception {
        // Add a new stream to the AdminConsole:
        String filename = TestUtils.prepareFilename(
                "./resources/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-02.xml");
        InputStream in = new FileInputStream(filename);
        AdminConsole.addModel("test2", in);
        in.close();
        Collection tabs = AdminConsole.getModel().selectNodes("//tab");
        assertNotNull(tabs);
        assertTrue(tabs.size() > 0);
        boolean found = false;
        for (Iterator iter=tabs.iterator(); iter.hasNext(); ) {
            Element tab = (Element)iter.next();
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

    public void testTabOverwrite() throws Exception {
        // Add a new stream to the AdminConsole:
        String filename = TestUtils.prepareFilename(
                "./resources/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-03.xml");
        InputStream in = new FileInputStream(filename);
        AdminConsole.addModel("test3", in);
        in.close();
        boolean found = false;
        for (Iterator tabs=AdminConsole.getModel().selectNodes("//tab").iterator(); tabs.hasNext(); ) {
            Element tab = (Element)tabs.next();
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
