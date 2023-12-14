/*
 * Copyright (C) 2008 Daniel Henninger, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.ldap.LdapManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Rdn;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Daniel Henninger
 */
public class LDAPTest {

    @BeforeAll
    public static void reconfigureOpenfireHome() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

//    @Test
//    public void testEncloseDN() {
//        String before = "ou=Jive Software\\, Inc,dc=support,dc=jive,dc=com";
//        String after = "ou=\"Jive Software, Inc\",dc=\"support\",dc=\"jive\",dc=\"com\"";
//        String converted = LdapManager.getEnclosedDN(before);
//        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
//
//        before = "ou=Jive Software\\, Inc,dc=t,dc=jive,dc=com";
//        after = "ou=\"Jive Software, Inc\",dc=\"t\",dc=\"jive\",dc=\"com\"";
//        converted = LdapManager.getEnclosedDN(before);
//        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
//
//        before = "ou=jive,dc=test,dc=jive,dc=com";
//        after = "ou=\"jive\",dc=\"test\",dc=\"jive\",dc=\"com\"";
//        converted = LdapManager.getEnclosedDN(before);
//        assertTrue("Conversion result "+before+" to "+converted, converted.equals(after));
//    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    @Test
    public void testRdnEscapeValue() {
        String before = "Jive Software, Inc";
        String after = "Jive Software\\, Inc";
        String converted = Rdn.escapeValue(before);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted);
        
        before = "Test.User; (+1)";
        after = "Test.User\\; (\\+1)";
        converted = Rdn.escapeValue(before);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted);
        
        before = "Wildcard *";
        after = "Wildcard *";
        converted = Rdn.escapeValue(before);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted);
        
        before = "Group/Section";
        after = "Group/Section";
        converted = Rdn.escapeValue(before);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted);
    }

    @Test
    public void testSanitizeSearchFilter() {
        String before = "Test.User; (+1)";
        String after = "Test.User; \\28+1\\29";
        String converted = LdapManager.sanitizeSearchFilter(before, false);
        //assertTrue("Conversion result "+before+" to "+converted+ " expected " + after, converted.equals(after));

        before = "Wildcard *";
        after = "Wildcard \\2a";
        converted = LdapManager.sanitizeSearchFilter(before, false);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted + " expected " + after);

        before = "Wildcard *";
        after = "Wildcard *";
        converted = LdapManager.sanitizeSearchFilter(before, true);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted + " expected " + after);
        
        before = "Wild*card *";
        after = "Wild\\2acard \\2a";
        converted = LdapManager.sanitizeSearchFilter(before, false);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted + " expected " + after);
        
        before = "Wild*card *";
        after = "Wild*card *";
        converted = LdapManager.sanitizeSearchFilter(before, true);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted + " expected " + after);
        
        before = "~ Group|Section & Teams!";
        after = "\\7e Group\\7cSection \\26 Teams\\21";
        converted = LdapManager.sanitizeSearchFilter(before, false);
        assertEquals(converted, after, "Conversion result " + before + " to " + converted + " expected " + after);
    }

    /**
     * Verifies that org.jivesoftware.openfire.ldap.LdapManager#getRelativeDNFromResult(javax.naming.directory.SearchResult)
     * can handle a result that contains one RDN value.
     */
    @Test
    public void testGetRelativeDNFromResultSingleValue() throws Exception
    {
        // Setup test fixture.
        final SearchResult input = new SearchResult( "cn=bender", null, new BasicAttributes(), true );

        // Execute system under test.
        final Rdn[] result = LdapManager.getRelativeDNFromResult( input );

        // Verify result.
        assertEquals( 1, result.length );
        assertEquals( "cn", result[0].getType() );
        assertEquals( "bender", result[0].getValue() );
    }

    /**
     * Verifies that org.jivesoftware.openfire.ldap.LdapManager#getRelativeDNFromResult(javax.naming.directory.SearchResult)
     * can handle a result that contains multiple RDN values.
     */
    @Test
    public void testGetRelativeDNFromResultMultiValue() throws Exception
    {
        // Setup test fixture.
        final SearchResult input = new SearchResult( "cn=bender,ou=people", null, new BasicAttributes(), true );

        // Execute system under test.
        final Rdn[] result = LdapManager.getRelativeDNFromResult( input );

        // Verify result.
        assertEquals( 2, result.length );
        assertEquals( "cn", result[0].getType() );
        assertEquals( "bender", result[0].getValue() );
        assertEquals( "ou", result[1].getType() );
        assertEquals( "people", result[1].getValue() );
    }

    /**
     * Verifies that org.jivesoftware.openfire.ldap.LdapManager#getRelativeDNFromResult(javax.naming.directory.SearchResult)
     * can handle a result that contains a quoted RDN values.
     *
     * Openldap has been observed returning the type of quoted values that are tested here.
     */
    @Test
    public void testGetRelativeDNFromResultQuoted() throws Exception
    {
        // Setup test fixture.
        final SearchResult input = new SearchResult( "\"cn=ship crew/cooks\"", null, new BasicAttributes(), true );

        // Execute system under test.
        final Rdn[] result = LdapManager.getRelativeDNFromResult( input );

        // Verify result.
        assertEquals( 1, result.length );
        assertEquals( "cn", result[0].getType() );
        assertEquals( "ship crew/cooks", result[0].getValue() );
    }
}
