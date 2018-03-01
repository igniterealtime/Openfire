/*
 * Copyright (C) 2008 Daniel Henninger. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.util;

import static org.junit.Assert.assertTrue;

import javax.naming.ldap.Rdn;

import org.jivesoftware.openfire.ldap.LdapManager;
import org.junit.Test;

/**
 * @author Daniel Henninger
 */
public class LDAPTest {

    @Test
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

    @Test
    public void testRdnEscapeValue() {
        String before = "Jive Software, Inc";
        String after = "Jive Software\\, Inc";
        String converted = Rdn.escapeValue(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
        
        before = "Test.User; (+1)";
        after = "Test.User\\; (\\+1)";
        converted = Rdn.escapeValue(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
        
        before = "Wildcard *";
        after = "Wildcard *";
        converted = Rdn.escapeValue(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
        
        before = "Group/Section";
        after = "Group/Section";
        converted = Rdn.escapeValue(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
    }

    @Test
    public void testSanitizeSearchFilter() {
        String before = "Test.User; (+1)";
        String after = "Test.User; \\28+1\\29";
        String converted = LdapManager.sanitizeSearchFilter(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));

        before = "Wildcard *";
        after = "Wildcard \\2a";
        converted = LdapManager.sanitizeSearchFilter(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));

        before = "Wildcard *";
        after = "Wildcard *";
        converted = LdapManager.sanitizeSearchFilter(before, true);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
        
        before = "Wild*card *";
        after = "Wild\\2acard \\2a";
        converted = LdapManager.sanitizeSearchFilter(before, false);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
        
        before = "Wild*card *";
        after = "Wild*card *";
        converted = LdapManager.sanitizeSearchFilter(before, true);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
        
        before = "~ Group|Section & Teams!";
        after = "\\7e Group\\7cSection \\26 Teams\\21";
        converted = LdapManager.sanitizeSearchFilter(before);
        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
    }
}
