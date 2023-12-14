/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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
package  org.jivesoftware.openfire.ldap;

import org.junit.jupiter.api.Test;

import javax.naming.ldap.LdapName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the functionality of {@link LdapManager}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class LdapManagerTest
{
    /**
     * Test if {@link LdapManager#getProviderURL(LdapName)} generates a URL using basic attributes (happy-flow test).
     */
    @Test
    public void testGetProviderURL() throws Exception
    {
        // Setup fixture.
        final Map<String, String> properties = new HashMap<>();
        properties.put("ldap.host", "localhost");
        properties.put("ldap.port", "389");
        final LdapManager manager = new LdapManager( properties );
        final LdapName name = new LdapName("ou=people,dc=example,dc=org");

        // Execute system under test.
        final String result = manager.getProviderURL( name );

        // Verify result.
        assertEquals("ldaps://localhost:389/ou=people,dc=example,dc=org", result);
    }

    /**
     * Test if {@link LdapManager#getProviderURL(LdapName)} generates a whitespace-separated value of URLs when
     * more than one connect-host is being provided.
     */
    @Test
    public void testGetProviderURLTwoHosts() throws Exception
    {
        // Setup fixture.
        final Map<String, String> properties = new HashMap<>();
        properties.put("ldap.host", "localhost example.org");
        properties.put("ldap.port", "389");
        final LdapManager manager = new LdapManager( properties );
        final LdapName name = new LdapName("ou=people,dc=example,dc=org");

        // Execute system under test.
        final String result = manager.getProviderURL( name );

        // Verify result.
        assertEquals("ldaps://localhost:389/ou=people,dc=example,dc=org ldaps://example.org:389/ou=people,dc=example,dc=org", result);
    }

    /**
     * Test if {@link LdapManager#getProviderURL(LdapName)} escapes whitespace characters in the baseDN value.
     */
    @Test
    public void testGetProviderURLWithSpaces() throws Exception
    {
        // Setup fixture.
        final Map<String, String> properties = new HashMap<>();
        properties.put("ldap.host", "localhost");
        properties.put("ldap.port", "389");
        properties.put("ldap.sslEnabled", "false");
        final LdapManager manager = new LdapManager( properties );
        final LdapName name = new LdapName("ou=people,dc=example with spaces,dc=org");

        // Execute system under test.
        final String result = manager.getProviderURL( name );

        // Verify result.
        assertEquals("ldap://localhost:389/ou=people,dc=example%20with%20spaces,dc=org", result);
    }

    /**
     * Tests if {@link LdapManager#splitFilter(String)} returns a single-valued result if the provided input cannot
     * be parsed as a ldap-filter string.
     */
    @Test
    public void testSplitFilterSimpleSingleValue() throws Exception
    {
        // Setup fixture.
        final String input = "test";

        // Execute system under test.
        final List<String> result = LdapManager.splitFilter( input );

        // Verify result.
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertTrue( result.contains( input ) );
    }

    /**
     * Tests if {@link LdapManager#splitFilter(String)} returns a single-valued result if the provided input is in LDAP
     * Filter string format that contains one part.
     */
    @Test
    public void testSplitFilterSingleValueInFilterFormat() throws Exception
    {
        // Setup fixture.
        final String input = "(|(test))";

        // Execute system under test.
        final List<String> result = LdapManager.splitFilter( input );

        // Verify result.
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertTrue( result.contains( "test" ) );
    }

    /**
     * Tests if {@link LdapManager#splitFilter(String)} returns a multi-valued result if the provided input is in LDAP
     * Filter string format that contains multiple parts.
     */
    @Test
    public void testSplitFilterDualValueInFilterFormat() throws Exception
    {
        // Setup fixture.
        final String input = "(|(foo)(bar))";

        // Execute system under test.
        final List<String> result = LdapManager.splitFilter( input );

        // Verify result.
        assertNotNull( result );
        assertEquals( 2, result.size() );
        assertEquals( "foo", result.get(0) );
        assertEquals( "bar", result.get(1) );
    }

    /**
     * Tests if {@link LdapManager#joinFilter(char, List)} joins a collection of parts into one string.
     */
    @Test
    public void testJoinFilter() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();
        input.add( "foo" );
        input.add( "bar" );

        // Execute system under test.
        final String result = LdapManager.joinFilter( '|', input );

        // Verify result.
        assertNotNull( result );
        assertEquals( "(|(foo)(bar))", result );
    }
}
