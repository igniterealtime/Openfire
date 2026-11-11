/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.commands.admin.user;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ClearedBlocks;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the XEP-0133 Re-Enable a User command ({@link ReEnableUser}): it unlocks each account through the login
 * limits (OF-2279), so that a lock the login limits set is lifted with the rest of the user's login limits, as the
 * admin console's Unlock account does, while a lock set by hand is lifted alone; and its notes keep their shape.
 *
 * @author Matthew Vivian
 */
public class ReEnableUserTest
{
    private static final String USER = "john";
    private static final String UNKNOWN_USER = "nobody";
    private static final JID ADMIN = new JID("admin", Fixtures.XMPP_DOMAIN, null);

    private MockedStatic<XMPPServer> xmppServerStatic;
    private MockedStatic<UserManager> userManagerStatic;
    private MockedStatic<LockOutManager> lockOutManagerStatic;
    private MockedStatic<UserLoginLimitManager> loginLimitsStatic;
    private LockOutManager lockOutManager;
    private UserLoginLimitManager loginLimits;
    private ReEnableUser command;

    @BeforeEach
    public void setUp() throws Exception
    {
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        xmppServerStatic = mockStatic(XMPPServer.class);
        xmppServerStatic.when(XMPPServer::getInstance).thenReturn(xmppServer);

        final UserManager userManager = mock(UserManager.class);
        final User user = mock(User.class);
        when(user.getUsername()).thenReturn(USER);
        when(userManager.getUser(USER)).thenReturn(user);
        when(userManager.getUser(UNKNOWN_USER)).thenThrow(new UserNotFoundException());
        userManagerStatic = mockStatic(UserManager.class);
        userManagerStatic.when(UserManager::getInstance).thenReturn(userManager);

        lockOutManager = mock(LockOutManager.class);
        final LockOutProvider lockOutProvider = mock(LockOutProvider.class);
        when(lockOutProvider.isReadOnly()).thenReturn(false);
        lockOutManagerStatic = mockStatic(LockOutManager.class);
        lockOutManagerStatic.when(LockOutManager::getInstance).thenReturn(lockOutManager);
        lockOutManagerStatic.when(LockOutManager::getLockOutProvider).thenReturn(lockOutProvider);

        loginLimits = mock(UserLoginLimitManager.class);
        loginLimitsStatic = mockStatic(UserLoginLimitManager.class);
        loginLimitsStatic.when(UserLoginLimitManager::getInstance).thenReturn(loginLimits);

        command = new ReEnableUser();
    }

    @AfterEach
    public void tearDown()
    {
        loginLimitsStatic.close();
        lockOutManagerStatic.close();
        userManagerStatic.close();
        xmppServerStatic.close();
    }

    /**
     * Verifies that re-enabling an account the login limits locked unlocks it through the login limits, which lift
     * the lock with the user's blocks and reset the user's failure counts, and reports success as before.
     */
    @Test
    public void testReEnableOfLockSetByLoginLimitsClearsLoginLimits()
    {
        // Setup test fixture.
        when(loginLimits.unlockAccount(USER)).thenReturn(Optional.of(new ClearedBlocks(List.of(), true, true, false)));

        // Execute system under test.
        final Element note = execute(USER);

        // Verify result.
        verify(loginLimits, times(1).description("the account must be unlocked through the login limits, so that a lock they set is cleared with the user's counts")).unlockAccount(USER);
        verify(lockOutManager, never().description("the login limits lift the lock; the command must not lift it again")).enableAccount(anyString());
        assertEquals("info", note.attributeValue("type"), "a successful re-enable must be reported as information");
        assertEquals("Operation finished successfully.", note.getText(), "a successful re-enable must be reported as before");
    }

    /**
     * Verifies that re-enabling an account locked by hand unlocks it through the login limits, which lift only the
     * lock for such a lock and leave the user's failure counts alone, and reports success as before.
     */
    @Test
    public void testReEnableOfLockSetByHandLeavesLoginLimits()
    {
        // Setup test fixture.
        when(loginLimits.unlockAccount(USER)).thenReturn(Optional.empty());

        // Execute system under test.
        final Element note = execute(USER);

        // Verify result.
        verify(loginLimits, times(1).description("the account must be unlocked through the login limits")).unlockAccount(USER);
        verify(loginLimits, never().description("re-enabling a lock set by hand must not clear the login limits")).clearBlocksForUsername(anyString());
        assertEquals("info", note.attributeValue("type"), "a successful re-enable must be reported as information");
        assertEquals("Operation finished successfully.", note.getText(), "a successful re-enable must be reported as before");
    }

    /**
     * Verifies that when the lock the login limits set could not be lifted, the command reports an error rather than
     * success.
     */
    @Test
    public void testReEnableOfLockThatCouldNotBeLiftedReportsError()
    {
        // Setup test fixture.
        when(loginLimits.unlockAccount(USER)).thenReturn(Optional.of(new ClearedBlocks(List.of(), true, false, true)));

        // Execute system under test.
        final Element note = execute(USER);

        // Verify result.
        assertEquals("error", note.attributeValue("type"), "a lock that could not be lifted must be reported as an error");
        assertEquals("Operation finished with errors", note.getText(), "a lock that could not be lifted must be reported as finished with errors");
    }

    /**
     * Verifies that an unknown user is reported as before, and that nothing is unlocked.
     */
    @Test
    public void testReEnableOfUnknownUserReportsUserDoesNotExist()
    {
        // Execute system under test.
        final Element note = execute(UNKNOWN_USER);

        // Verify result.
        assertEquals("error", note.attributeValue("type"), "an unknown user must be reported as an error");
        assertEquals("User does not exist: " + UNKNOWN_USER + "@" + Fixtures.XMPP_DOMAIN, note.getText(), "an unknown user must be named as before");
        verify(loginLimits, never().description("nothing must be unlocked through the login limits for a request with errors")).unlockAccount(anyString());
        verify(lockOutManager, never().description("nothing must be unlocked for a request with errors")).enableAccount(anyString());
    }

    /**
     * Runs the command for one account and returns the note it added.
     */
    private Element execute(final String username)
    {
        final SessionData data = new SessionData("session", ADMIN) {
            {
                addStageForm(Map.of("accountjids", List.of(username + "@" + Fixtures.XMPP_DOMAIN)));
            }
        };
        final Element element = DocumentHelper.createElement("command");

        command.execute(data, element);

        final Element note = element.element("note");
        assertNotNull(note, "the command must add a note");
        return note;
    }
}
