package org.jivesoftware.admin;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.util.TaskEngine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LoginLimitManagerTest {

    private LoginLimitManager loginLimitManager;

    @Mock private SecurityAuditManager securityAuditManager;
    @Mock private TaskEngine taskEngine;

    @Before
    public void setUp() {
        loginLimitManager = new LoginLimitManager(securityAuditManager, taskEngine);
    }

    @Test
    public void aSuccessfulLoginWillBeAudited() {

        loginLimitManager.recordSuccessfulAttempt("test-user", "a.b.c.d");

        verify(securityAuditManager).logEvent("test-user", "Successful admin console login attempt", "The user logged in successfully to the admin console from address a.b.c.d. ");
    }

    @Test
    public void aFailedLoginWillBeAudited() {

        loginLimitManager.recordFailedAttempt("test-user", "a.b.c.d");

        verify(securityAuditManager).logEvent("test-user", "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.d. ");
    }

    @Test
    public void lockoutsWillBeAudited() {

        for(int i = 0; i < 11; i ++) {
            loginLimitManager.recordFailedAttempt("test-user", "a.b.c.d");
        }

        verify(securityAuditManager, times(10)).logEvent("test-user", "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.d. ");
        verify(securityAuditManager, times(1)).logEvent("test-user", "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.d. Future login attempts from this address will be temporarily locked out. Future login attempts for this user will be temporarily locked out. ");
    }
}
