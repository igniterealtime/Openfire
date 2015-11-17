package org.jivesoftware.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class StringUtilsTest {
	
    @Test
    public void testValidDomainNames() {
    	
        assertValidDomainName("www.mycompany.com");
        assertValidDomainName("www.my-company.com");
        assertValidDomainName("abc.de");
        assertValidDomainName("tronçon.be", "xn--tronon-zua.be");
        assertValidDomainName("öbb.at", "xn--bb-eka.at");

    }
    
    @Test
    public void testInvalidDomainNames() {
    	
        assertInvalidDomainName("www.my_company.com", "Contains non-LDH characters");
        assertInvalidDomainName("www.-dash.com", "Has leading or trailing hyphen");
        assertInvalidDomainName("www.dash-.com", "Has leading or trailing hyphen");
        assertInvalidDomainName("abc.<test>.de", "Contains non-LDH characters");

    }

    @Test
    public void testStringReplace() {
        assertEquals(StringUtils.replace("Hello Foo Foo", "Foo", "World"), "Hello World World");
        assertEquals(StringUtils.replace("Hello Foo foo", "Foo", "World"), "Hello World foo");
        assertEquals(StringUtils.replaceIgnoreCase("Hello Foo foo", "Foo", "World"), "Hello World World");
        int[] count = new int[1];
        assertEquals(StringUtils.replaceIgnoreCase("Hello Foo foo", "Foo", "World", count), "Hello World World");
        assertEquals(count[0], 2);
    }

	private void assertValidDomainName(String domain) {
		assertValidDomainName(domain, domain);
    }

	private void assertValidDomainName(String domain, String expected) {
        assertEquals("Domain should be valid: " + domain, expected, StringUtils.validateDomainName(domain));
    }

	private void assertInvalidDomainName(String domain, String expectedCause) {
		try {
        	StringUtils.validateDomainName(domain);
        	fail("Domain should not be valid: " + domain);
        } catch (IllegalArgumentException iae) {
        	// this is not part of the official API, so leave off for now
        	//assertEquals("Unexpected cause: " + iae.getMessage(), expectedCause, iae.getMessage());
        }
	}
}
