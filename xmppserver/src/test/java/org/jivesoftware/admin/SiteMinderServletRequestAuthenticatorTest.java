package org.jivesoftware.admin;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;

import javax.servlet.http.HttpServletRequest;

import org.jivesoftware.Fixtures;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SiteMinderServletRequestAuthenticatorTest {

    private SiteMinderServletRequestAuthenticator authenticator;

    @Mock private HttpServletRequest request;

    @Before
    public void setUp() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        authenticator = new SiteMinderServletRequestAuthenticator();
    }

    @Test
    public void willAuthenticateAUser() {

        final String userId = "a-test-user";
        doReturn(userId).when(request).getHeader("SM_USER");

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(userId));
    }

    @Test
    public void willNotAuthenticateAMissingUser() {

        doReturn(null).when(request).getHeader("SM_USER");

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(nullValue()));
    }

    @Test
    public void willNotAuthenticateABlankUser() {

        doReturn("").when(request).getHeader("SM_USER");

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(nullValue()));
    }

    @Test
    public void defaultHeaderIsCorrect() {

        assertThat(SiteMinderServletRequestAuthenticator.SITE_MINDER_HEADER.getValue(), is("SM_USER"));

    }

    @Test
    public void willAuthenticateAUserWithDifferentHeader() {

        final String userId = "a-test-user";
        final String header = "a-custom-header";
        SiteMinderServletRequestAuthenticator.SITE_MINDER_HEADER.setValue(header);
        doReturn(userId).when(request).getHeader(header);

        final String authenticatedUser = authenticator.authenticateRequest(request);

        assertThat(authenticatedUser, is(userId));
    }
}
