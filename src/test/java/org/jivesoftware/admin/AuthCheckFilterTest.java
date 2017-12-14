package org.jivesoftware.admin;

import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class AuthCheckFilterTest {

    private static final String adminUser = "test-admin-user";
    private static final String normalUser = "test-normal-user";
    private static final String remoteAddr = "a.b.c.d";

    @Mock private HttpServletRequest request;
    @Mock private HttpSession httpSession;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private AdminManager adminManager;
    @Mock private LoginLimitManager loginLimitManager;

    @Before
    public void setUp() throws Exception {

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

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager, AdminUserServletAuthenticatorClass.class.getName());

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

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager, "");

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willRedirectARequestWithABrokenServletRequestAuthenticator() throws Exception {

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager, "this-is-not-a-class-name");

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willRedirectARequestIfTheServletRequestAuthenticatorReturnsNoUser() throws Exception {

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager, NoUserServletAuthenticatorClass.class.getName());

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willRedirectARequestIfTheServletRequestAuthenticatorReturnsAnUnauthorisedUser() throws Exception {

        final AuthCheckFilter filter = new AuthCheckFilter(adminManager, loginLimitManager, NormalUserServletAuthenticatorClass.class.getName());

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(anyString());
    }

    @Test
    public void willReturnTrueIfTheCorrectServletRequestAuthenticatorIsConfigured() throws Exception {

        new AuthCheckFilter(adminManager, loginLimitManager, NormalUserServletAuthenticatorClass.class.getName());

        assertThat(AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(NormalUserServletAuthenticatorClass.class), is(true));
    }

    @Test
    public void willReturnFalseIfTheWrongServletRequestAuthenticatorIsConfigured() throws Exception {

        new AuthCheckFilter(adminManager, loginLimitManager, NormalUserServletAuthenticatorClass.class.getName());

        assertThat(AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(AdminUserServletAuthenticatorClass.class), is(false));
    }

    @Test
    public void willReturnFalseIfNoServletRequestAuthenticatorIsConfigured() throws Exception {

        new AuthCheckFilter(adminManager, loginLimitManager, "");

        assertThat(AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(AdminUserServletAuthenticatorClass.class), is(false));
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
}
