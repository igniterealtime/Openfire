package org.jivesoftware.admin;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class AuthCheckFilterTest {

    private static final String adminUser = "test-admin-user";
    private static final String normalUser = "test-normal-user";
    private static final String remoteAddr = "198.51.100.15";

    @Mock private HttpServletRequest request;
    @Mock private HttpSession httpSession;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private AdminManager adminManager;
    @Mock private LoginLimitManager loginLimitManager;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
    }

    @Before
    public void setUp() {

        Fixtures.clearExistingProperties();

        doReturn("/uri/to/page").when(request).getRequestURI();
        doReturn(httpSession).when(request).getSession();
        doReturn(remoteAddr).when(request).getRemoteAddr();

        doReturn(true).when(adminManager).isUserAdmin(adminUser, true);
        doReturn(false).when(adminManager).isUserAdmin(normalUser, true);

    }

    // login.jsp,index.jsp?logout=true,setup/index.jsp,setup/setup-,.gif,.png,error-serverdown.jsp

    @Test
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
        assertFalse(AuthCheckFilter.testURLPassesExclude("setup/setup-/%2E/%2E/log.jsp?log=info&mode=asc&lines=All","setup/setup-*"));

        assertTrue(AuthCheckFilter.testURLPassesExclude("setup/setup-new.jsp","setup/setup-*"));

        assertFalse(AuthCheckFilter.testURLPassesExclude("another.jsp?login.jsp", "login.jsp"));
    }

    @Test
    public void willNotRedirectARequestFromAnAdminUser() throws Exception {

        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        filter.doFilter(request, response, filterChain);

        verify(response, never()).sendRedirect(anyString());
        verify(loginLimitManager).recordSuccessfulAttempt(adminUser, remoteAddr);
        final ArgumentCaptor<AuthToken> argumentCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(httpSession).setAttribute(eq("jive.admin.authToken"), argumentCaptor.capture());
        final AuthToken authToken = argumentCaptor.getValue();
        assertThat(authToken.getUsername(), is(adminUser));
    }

    @Test
    public void willRedirectARequestWithoutAServletRequestAuthenticator() throws Exception {

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willRedirectARequestWithABrokenServletRequestAuthenticator() throws Exception {

        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(BrokenUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willRedirectARequestIfTheServletRequestAuthenticatorReturnsNoUser() throws Exception {

        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(NoUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willRedirectARequestIfTheServletRequestAuthenticatorReturnsAnUnauthorisedUser() throws Exception {

        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(NormalUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willReturnTrueIfTheCorrectServletRequestAuthenticatorIsConfigured() {

        new AuthCheckFilter(adminManager, loginLimitManager);
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(NormalUserServletAuthenticatorClass.class);

        assertThat(AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(NormalUserServletAuthenticatorClass.class), is(true));
    }

    @Test
    public void willReturnFalseIfTheWrongServletRequestAuthenticatorIsConfigured() {

        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(NormalUserServletAuthenticatorClass.class);
        new AuthCheckFilter(adminManager, loginLimitManager);

        assertThat(AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(AdminUserServletAuthenticatorClass.class), is(false));
    }

    @Test
    public void willReturnFalseIfNoServletRequestAuthenticatorIsConfigured() {

        new AuthCheckFilter(adminManager, loginLimitManager);

        assertThat(AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(AdminUserServletAuthenticatorClass.class), is(false));
    }

    @Test
    public void ipOnList() throws Exception {
        // Setup test fixture.
        final String input = "203.0.113.251";
        final Set<String> list = new HashSet<>();
        list.add(input);

        // Execute system under test.
        final boolean result = AuthCheckFilter.isOnList(list, input);

        // Verify result.
        assertTrue(result);
    }

    @Test
    public void ipNotOnList() throws Exception {
        // Setup test fixture.
        final String input = "203.0.113.251";
        final Set<String> list = new HashSet<>();
        list.add("192.0.2.2");

        // Execute system under test.
        final boolean result = AuthCheckFilter.isOnList(list, input);

        // Verify result.
        assertFalse(result);
    }

    @Test
    public void ipNotOnEmptyList() throws Exception {
        // Setup test fixture.
        final String input = "203.0.113.251";
        final Set<String> list = new HashSet<>();

        // Execute system under test.
        final boolean result = AuthCheckFilter.isOnList(list, input);

        // Verify result.
        assertFalse(result);
    }

    @Test
    public void ipOnListRange() throws Exception {
        // Setup test fixture.
        final String input = "203.0.113.251";
        final Set<String> list = new HashSet<>();
        list.add("203.0.113.25-203.0.113.251");

        // Execute system under test.
        final boolean result = AuthCheckFilter.isOnList(list, input);

        // Verify result.
        assertTrue(result);
    }

    @Test
    public void ipOnListCIDR() throws Exception {
        // Setup test fixture.
        final String input = "203.0.113.251";
        final Set<String> list = new HashSet<>();
        list.add("203.0.113.0/24");

        // Execute system under test.
        final boolean result = AuthCheckFilter.isOnList(list, input);

        // Verify result.
        assertTrue(result);
    }

    @Test
    public void nonExcludedUrlWillNotErrorWhenListsEmpty() throws Exception {
        // Setup test fixture.
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        // Execute system under test.
        filter.doFilter(request, response, filterChain);

        // Verify result
        verify(response, never()).sendError(anyInt());
        verify(filterChain, atLeastOnce()).doFilter(any(), any());
    }

    @Test
    public void excludedUrlWillNotErrorWhenListsEmpty() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            // Execute system under test.
            filter.doFilter(request, response, filterChain);

            // Verify result
            verify(response, never()).sendError(anyInt());
            verify(filterChain, atLeastOnce()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    @Test
    public void nonExcludedUrlWillErrorWhenOnBlocklist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(request.getRemoteAddr()));
        filter.doFilter(request, response, filterChain);

        verify(response, atLeastOnce()).sendError(anyInt());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void nonExcludedUrlWillErrorWhenMatchingCIDROnBlocklist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        final String cidr = request.getRemoteAddr().substring(0, request.getRemoteAddr().lastIndexOf('.')) + ".0/24";
        AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(cidr));
        filter.doFilter(request, response, filterChain);

        verify(response, atLeastOnce()).sendError(anyInt());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void nonExcludedUrlWillErrorWhenMatchingRangeOnBlocklist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        final String range = request.getRemoteAddr().substring(0, request.getRemoteAddr().lastIndexOf('.')) + ".0-" + request.getRemoteAddr().substring(0, request.getRemoteAddr().lastIndexOf('.')) + ".255";
        AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(range));
        filter.doFilter(request, response, filterChain);

        verify(response, atLeastOnce()).sendError(anyInt());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void excludedUrlWillNotErrorWhenOnBlocklist() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(request.getRemoteAddr()));
            filter.doFilter(request, response, filterChain);

            verify(response, never()).sendError(anyInt());
            verify(filterChain, atLeastOnce()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    @Test
    public void excludedUrlWillErrorWhenOnBlocklistAndExcludesAreIgnored() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(request.getRemoteAddr()));
            AuthCheckFilter.IP_ACCESS_IGNORE_EXCLUDES.setValue(true);
            filter.doFilter(request, response, filterChain);

            verify(response, atLeastOnce()).sendError(anyInt());
            verify(filterChain, never()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    @Test
    public void nonExcludedUrlWillNotErrorWhenOnAllowlist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton(remoteAddr));
        filter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt());
        verify(filterChain, atLeastOnce()).doFilter(any(), any());
    }

    @Test
    public void nonExcludedUrlWillNotErrorWhenCIDROnAllowlist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        final String cidr = remoteAddr.substring(0, remoteAddr.lastIndexOf('.')) + ".0/24";
        AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton(cidr));
        filter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt());
        verify(filterChain, atLeastOnce()).doFilter(any(), any());
    }

    @Test
    public void nonExcludedUrlWillNotErrorWhenRangeOnAllowlist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        final String range = remoteAddr.substring(0, remoteAddr.lastIndexOf('.')) + ".0-" + remoteAddr.substring(0, remoteAddr.lastIndexOf('.')) + ".255";
        AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton(range));
        filter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt());
        verify(filterChain, atLeastOnce()).doFilter(any(), any());
    }

    @Test
    public void nonExcludedUrlWillErrorWhenNotOnAllowlist() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton("w.x.y.z"));
        filter.doFilter(request, response, filterChain);

        verify(response, atLeastOnce()).sendError(anyInt());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void excludedUrlWillNotErrorWhenNotOnAllowlist() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton("w.x.y.z"));
            filter.doFilter(request, response, filterChain);

            verify(response, never()).sendError(anyInt());
            verify(filterChain, atLeastOnce()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    @Test
    public void excludedUrlWillErrorWhenNotOnAllowlistAndExcludesAreIgnored() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton("w.x.y.z"));
            AuthCheckFilter.IP_ACCESS_IGNORE_EXCLUDES.setValue(true);
            filter.doFilter(request, response, filterChain);

            verify(response, atLeastOnce()).sendError(anyInt());
            verify(filterChain, never()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    @Test
    public void nonExcludedUrlWillErrorWhenOnBothLists() throws Exception {
        AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);

        AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(request.getRemoteAddr()));
        AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton(request.getRemoteAddr()));
        filter.doFilter(request, response, filterChain);

        verify(response, atLeastOnce()).sendError(anyInt());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void excludedUrlWillNotErrorWhenOnBothLists() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(request.getRemoteAddr()));
            AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton(request.getRemoteAddr()));
            filter.doFilter(request, response, filterChain);

            verify(response, never()).sendError(anyInt());
            verify(filterChain, atLeastOnce()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    @Test
    public void excludedUrlWillErrorWhenOnBothListsAndExcludesAreIgnored() throws Exception {
        // Setup test fixture.
        try {
            AuthCheckFilter.SERVLET_REQUEST_AUTHENTICATOR.setValue(AdminUserServletAuthenticatorClass.class);
            final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager);
            AuthCheckFilter.addExclude(request.getRequestURI().substring(1));

            AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(Collections.singleton(request.getRemoteAddr()));
            AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(Collections.singleton(request.getRemoteAddr()));
            AuthCheckFilter.IP_ACCESS_IGNORE_EXCLUDES.setValue(true);
            filter.doFilter(request, response, filterChain);

            verify(response, atLeastOnce()).sendError(anyInt());
            verify(filterChain, never()).doFilter(any(), any());
        } finally {
            // Tear down test fixture.
            AuthCheckFilter.removeExclude(request.getRequestURI().substring(1));
        }
    }

    public static class AdminUserServletAuthenticatorClass implements ServletRequestAuthenticator {
        @Override
        public String authenticateRequest(final HttpServletRequest request) {
            return adminUser;
        }
    }

    public static class NormalUserServletAuthenticatorClass implements ServletRequestAuthenticator {
        @Override
        public String authenticateRequest(final HttpServletRequest request) {
            return normalUser;
        }
    }

    public static class NoUserServletAuthenticatorClass implements ServletRequestAuthenticator {
        @Override
        public String authenticateRequest(final HttpServletRequest request) {
            return null;
        }
    }

    public static class BrokenUserServletAuthenticatorClass implements ServletRequestAuthenticator {

        public BrokenUserServletAuthenticatorClass() {
            throw new IllegalStateException();
        }

        @Override
        public String authenticateRequest(final HttpServletRequest request) {
            return adminUser;
        }
    }
}
