/**
 * $RCSfile$
 * $Revision
 * $Date$
 *
 * Copyright (C) 1999-2004 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.util;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;

public class XMLPropertiesTest extends TestCase {

    public void testAttributes() throws Exception {
        String xml = "<root><foo></foo></root>";
        XMLProperties props = new XMLProperties(new ByteArrayInputStream(xml.getBytes()));
        assertNull(props.getAttribute("foo","bar"));
        xml = "<root><foo bar=\"test123\"></foo></root>";
        props = new XMLProperties(new ByteArrayInputStream(xml.getBytes()));
        assertEquals(props.getAttribute("foo","bar"), "test123");
    }
}
