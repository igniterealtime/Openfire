package org.jivesoftware.admin;

import junit.framework.TestCase;

/**
 * 
 */
public class AuthCheckFilterTest extends TestCase {

    // login.jsp,index.jsp?logout=true,setup/index.jsp,setup/setup-,.gif,.png,error-serverdown.jsp

    public void testExcludeRules() {
        assertFalse(AuthCheckFilter.testURLPassesExclude("blahblah/login.jsp", "login.jsp"));
        assertTrue(AuthCheckFilter.testURLPassesExclude("login.jsp", "login.jsp"));
        assertTrue(AuthCheckFilter.testURLPassesExclude("login.jsp?yousuck&blah", "login.jsp"));
        assertTrue(AuthCheckFilter.testURLPassesExclude("login.jsp?another=true&login.jsp?true", "login.jsp"));
        assertFalse(AuthCheckFilter.testURLPassesExclude("blahblah/login.jsp", "login.jsp?logout=false"));
        assertTrue(AuthCheckFilter.testURLPassesExclude("login.jsp?logout=false", "login.jsp?logout=false"));
        assertFalse(AuthCheckFilter.testURLPassesExclude("login.jsp?logout=false&another=true", "login.jsp?logout=false"));
        assertFalse(AuthCheckFilter.testURLPassesExclude("login.jsp?logout=false&another=true", "login.jsp?logout=false"));

        assertFalse(AuthCheckFilter.testURLPassesExclude("setup/setup-/../../log.jsp?log=info&mode=asc&lines=All","setup/setup-*"));

        assertTrue(AuthCheckFilter.testURLPassesExclude("setup/setup-new.jsp","setup/setup-*"));

        // Let's get crafty by using an "@" symbol
        assertFalse(AuthCheckFilter.testURLPassesExclude("login.jsp?@another.jsp", "login.jsp"));
        assertFalse(AuthCheckFilter.testURLPassesExclude("another.jsp?login.jsp", "login.jsp"));
    }
}
