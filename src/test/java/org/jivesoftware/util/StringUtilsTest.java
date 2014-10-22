package org.jivesoftware.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringUtilsTest {
	
    @Test
    public void testValidDomainNames() {
    	
        String domain = "www.mycompany.com";
        assertTrue("Domain should be valid", StringUtils.isValidDomainName(domain));
    	
        domain = "www.my-company.com";
        assertTrue("Domain should be valid", StringUtils.isValidDomainName(domain));
    	
        domain = "abc.de";
        assertTrue("Domain should be valid", StringUtils.isValidDomainName(domain));
    }
    
    @Test
    public void testInvalidDomainNames() {
    	
        String domain = "www.my_company.com";
        assertFalse("Domain should not be valid", StringUtils.isValidDomainName(domain));
    	
        domain = "www.-dash.com";
        assertFalse("Domain should not be valid", StringUtils.isValidDomainName(domain));
    	
        domain = "www.dash-.com";
        assertFalse("Domain should not be valid", StringUtils.isValidDomainName(domain));
    	
        domain = "abc.<test>.de";
        assertFalse("Domain should not be valid", StringUtils.isValidDomainName(domain));
    }

}
