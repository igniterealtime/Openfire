package org.jivesoftware.admin;

import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Random;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LoginLimitManagerTest {

    private LoginLimitManager loginLimitManager;

    @Mock private SecurityAuditManager securityAuditManager;

    @Before
    public void setUp() {
        loginLimitManager = new LoginLimitManager(securityAuditManager, TaskEngine.getInstance());
    }

    @Test
    public void aSuccessfulLoginWillBeAudited() {

        final String username = "test-user-a-" + StringUtils.randomString(10);
        loginLimitManager.recordSuccessfulAttempt(username, "a.b.c.d");

        verify(securityAuditManager).logEvent(username, "Successful admin console login attempt", "The user logged in successfully to the admin console from address a.b.c.d. ");
    }

    @Test
    public void aFailedLoginWillBeAudited() {

        final String username = "test-user-b-" + StringUtils.randomString(10);
        loginLimitManager.recordFailedAttempt(username, "a.b.c.e");

        verify(securityAuditManager).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.e. ");
    }

    @Test
    public void lockoutsWillBeAudited() {

        final String username = "test-user-c-" + StringUtils.randomString(10);
        for(int i = 0; i < 11; i ++) {
            loginLimitManager.recordFailedAttempt(username, "a.b.c.f");
        }

        verify(securityAuditManager, times(10)).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.f. ");
        verify(securityAuditManager, times(1)).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.f. Future login attempts from this address will be temporarily locked out. Future login attempts for this user will be temporarily locked out. ");
    }
}
