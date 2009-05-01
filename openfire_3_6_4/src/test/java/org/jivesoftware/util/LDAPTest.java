/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Daniel Henninger. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.util;

import junit.framework.TestCase;
import org.jivesoftware.openfire.ldap.LdapManager;

/**
 * @author Daniel Henninger
 */
public class LDAPTest extends TestCase {

    public void testEncloseDN() {
        String before = "ou=Jive Software\\, Inc,dc=support,dc=jive,dc=com";
        String after = "ou=\"Jive Software, Inc\",dc=\"support\",dc=\"jive\",dc=\"com\"";
        String converted = LdapManager.getEnclosedDN(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));

        before = "ou=Jive Software\\, Inc,dc=t,dc=jive,dc=com";
        after = "ou=\"Jive Software, Inc\",dc=\"t\",dc=\"jive\",dc=\"com\"";
        converted = LdapManager.getEnclosedDN(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));

        before = "ou=jive,dc=test,dc=jive,dc=com";
        after = "ou=\"jive\",dc=\"test\",dc=\"jive\",dc=\"com\"";
        converted = LdapManager.getEnclosedDN(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
    }
}
