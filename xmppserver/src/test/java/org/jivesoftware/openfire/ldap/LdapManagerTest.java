package  org.jivesoftware.openfire.ldap;

import org.junit.Test;

import javax.naming.ldap.LdapName;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
     * more than one connect-host is being provided..
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
}
