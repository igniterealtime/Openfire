/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 */

package org.jivesoftware.util;

import junit.framework.TestCase;
import org.jivesoftware.admin.AdminConsole;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.lang.reflect.Method;

public class AdminConsoleTest extends TestCase {

    public AdminConsoleTest() {

    }

    /**
     * Resets the admin console internal data structures.
     */
    public void tearDown() throws Exception {
        Class c = AdminConsole.class;
        Method clear = c.getDeclaredMethod("clear", (Class[])null);
        clear.setAccessible(true);
        clear.invoke((Object)null, (Object[])null);
    }

    public void testModifyGlobalProps() throws Exception {
        // Add a new stream to the AdminConsole:
        String filename = TestUtils.prepareFilename(
                "./resources/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-01.xml");
        InputStream in = new FileInputStream(filename);
        AdminConsole.addXMLSource(in);
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
        AdminConsole.addXMLSource(in);
        in.close();
        Collection items = AdminConsole.getItems();
        assertNotNull(items);
        assertTrue(items.size() > 0);
        boolean found = false;
        for (Iterator iter=items.iterator(); iter.hasNext(); ) {
            AdminConsole.Item item = (AdminConsole.Item)iter.next();
            if ("foobar".equals(item.getId())) {
                found = true;
                assertEquals("Foo Bar", item.getName());
                assertEquals("Click to see foo bar", item.getDescription());
            }
        }
        if (!found) {
            fail("Expected new item 'foobar' was not found.");
        }
    }

    public void testTabOverwrite() throws Exception {
        // Add a new stream to the AdminConsole:
        String filename = TestUtils.prepareFilename(
                "./resources/org/jivesoftware/admin/AdminConsoleTest.admin-sidebar-02.xml");
        InputStream in = new FileInputStream(filename);
        AdminConsole.addXMLSource(in);
        in.close();
        Collection items = AdminConsole.getItems();
        boolean found = false;
        for (Iterator iter=items.iterator(); iter.hasNext(); ) {
            AdminConsole.Item item = (AdminConsole.Item)iter.next();
            if ("server".equals(item.getId())) {
                found = true;
                assertEquals("New Server Title", item.getName());
                assertEquals("Testing 1 2 3", item.getDescription());
            }
        }
        if (!found) {
            fail("Failed to overwrite 'server' tab with new properties.");
        }
    }
}
