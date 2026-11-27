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
package org.jivesoftware.admin.servlet;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ActiveBlock;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.BlocksForUsername;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ClearedBlocks;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.Tier;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserLockoutServlet}, the admin console page that locks a user's account and lists the login
 * limits' blocks for the user.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UserLockoutServletTest
{
    private static final String CSRF = "token1234";
    private static final String USER = "john";
    private static final String ADMIN = "admin";
    private static final String ADDRESS = "127.0.0.5";
    private static final String OTHER_ADDRESS = "127.0.0.9";
    private static final Instant ENDS_AT = Instant.parse("2026-11-20T18:04:02Z");

    /**
     * The page's clock: 1 hour and 26 minutes before the blocks end, so that each end is shown as that long from now.
     */
    private static final Instant NOW = ENDS_AT.minus(Duration.ofMinutes(86));
    private static final String FROM_NOW = "1 hour, 26 minutes from now";
    private static final String USER_KEY = "am9obg==";

    private static final ActiveBlock ANY_ADDRESS_BLOCK = new ActiveBlock(Tier.USERNAME, USER, false, null, 5, ENDS_AT, USER_KEY);
    private static final ActiveBlock ADDRESS_BLOCK = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, USER, false, ADDRESS, 3, ENDS_AT, ADDRESS + '\u0000' + USER_KEY);
    private static final ActiveBlock REFUSING_BLOCK = new ActiveBlock(Tier.ADDRESS, null, false, OTHER_ADDRESS, 40, ENDS_AT, OTHER_ADDRESS);

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private RequestDispatcher dispatcher;

    @Mock
    private UserLoginLimitManager limits;

    @Mock
    private LockOutManager lockOutManager;

    @Mock
    private LockOutProvider lockOutProvider;

    private final Map<String, String> params = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<String[]> events = new ArrayList<>();
    private final List<String> closedSessions = new ArrayList<>();
    private final Set<String> administrators = new HashSet<>();
    private boolean userEventsBlocked;
    private UserLockoutServlet servlet;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("jive.admin.authToken")).thenReturn(AuthToken.generateUserToken(ADMIN));
        when(request.getServerName()).thenReturn("localhost");
        when(request.getParameter(anyString())).thenAnswer(invocation -> params.get(invocation.<String>getArgument(0)));
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
            .when(request).setAttribute(anyString(), any());

        when(lockOutProvider.isReadOnly()).thenReturn(false);
        when(lockOutProvider.isDelayedStartSupported()).thenReturn(true);
        when(lockOutProvider.isTimeoutSupported()).thenReturn(true);
        when(limits.getBlocksForUsername(anyString(), eq(UserLockoutServlet.MAX_ROWS))).thenReturn(new BlocksForUsername(null, 0, List.of(), 0, List.of(), false));
        params.put("username", USER);

        servlet = new UserLockoutServlet(() -> limits, () -> lockOutManager, () -> lockOutProvider, administrators::contains,
            closedSessions::add, () -> userEventsBlocked, Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            protected void logEvent(final HttpServletRequest request, final HttpServletResponse response, final String summary, final String details)
            {
                events.add(new String[] {summary, details});
            }
        };
    }

    private void validCsrf()
    {
        params.put("csrf", CSRF);
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("csrf", CSRF)});
    }

    private UserLockoutServlet.LoginLimitsSection section()
    {
        return (UserLockoutServlet.LoginLimitsSection) attributes.get("loginLimits");
    }

    private UserLockoutServlet.Answer answer()
    {
        return (UserLockoutServlet.Answer) attributes.get("answer");
    }

    private UserLockoutServlet.Time lockEnd()
    {
        return (UserLockoutServlet.Time) attributes.get("lockEnd");
    }

    // ========== The page for each case ==========

    /**
     * Verifies the page for a user whom the login limits locked: the lock is shown as set by the login limits, and the
     * Login limits section lists the block from any IP address and the block from one IP address, with the Clear login
     * limits button.
     */
    @Test
    public void testGetShowsLockSetByLoginLimitsWithBlocks() throws Exception
    {
        // Setup test fixture.
        final LockOutFlag flag = new LockOutFlag(USER, null, Date.from(ENDS_AT));
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(flag);
        when(limits.isLockSetByLoginLimits(flag)).thenReturn(true);
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(ANY_ADDRESS_BLOCK, 1, List.of(ADDRESS_BLOCK), 0, List.of(), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(true, attributes.get("locked"), "the account must be shown as locked");
        assertEquals(true, attributes.get("lockSetByLoginLimits"), "the lock must be shown as set by the login limits");
        assertEquals(JiveGlobals.formatDateTime(Date.from(ENDS_AT)), lockEnd().getAt(), "the lock's end must be shown in the console's date format");
        assertEquals(FROM_NOW, lockEnd().getFromNow(), "the lock's end must say how long that is from now");
        assertEquals(true, attributes.get("lockInForce"), "a lock without a start must be in force");
        assertEquals(5L, section().getFromAnyAddress().getFailureCount(), "the block from any IP address must be listed");
        assertTrue(section().isClearable(), "the Clear login limits button must be shown");
        assertEquals(1, section().getFromAddresses().size(), "the block from one IP address must be listed");
        assertEquals(ADDRESS, section().getFromAddresses().get(0).getAddress(), "the row must give the IP address");
        assertTrue(section().isShown(), "the Login limits section must be shown");
        assertEquals(false, attributes.get("administrator"), "a user who is not an administrator must not get the administrator's note");
        verify(dispatcher, times(1).description("the page must be rendered")).forward(request, response);
    }

    /**
     * Verifies the page for an administrator whom the login limits refuse from any IP address without a lock: the
     * account is not locked, the block is listed, and the page is told the user is an administrator.
     */
    @Test
    public void testGetShowsAdministratorRefusedWithoutLock() throws Exception
    {
        // Setup test fixture.
        params.put("username", ADMIN);
        administrators.add(ADMIN);
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME, ADMIN, false, null, 5, ENDS_AT, "YWRtaW4=");
        when(limits.getBlocksForUsername(ADMIN, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(block, 0, List.of(), 0, List.of(), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(false, attributes.get("locked"), "the account must be shown as not locked");
        assertEquals(true, attributes.get("administrator"), "the page must say the login limits never lock an administrator's account");
        assertEquals(true, attributes.get("ownAccount"), "the warning about locking your own account must be shown");
        assertNotNull(section().getFromAnyAddress(), "the block from any IP address must be listed");
        assertTrue(section().getFromAddresses().isEmpty(), "no block from one IP address must be listed");
    }

    /**
     * Verifies the page for a user blocked from one IP address only.
     */
    @Test
    public void testGetShowsBlockFromOneAddress() throws Exception
    {
        // Setup test fixture.
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 1, List.of(ADDRESS_BLOCK), 0, List.of(), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(false, attributes.get("locked"), "the account must be shown as not locked");
        assertNull(section().getFromAnyAddress(), "no block from any IP address must be listed");
        assertEquals(List.of(ADDRESS), section().getFromAddresses().stream().map(UserLockoutServlet.BlockRow::getAddress).toList(),
            "the block from the IP address must be listed");
        assertTrue(section().isBlocked(), "the section must list the user's blocks");
        assertTrue(section().isClearable(), "the Clear login limits button must be shown");
        assertEquals(false, attributes.get("ownAccount"), "another user's page must not warn about locking your own account");
    }

    /**
     * Verifies that an IP address block that refused the user is shown, apart from the user's own blocks, so that the
     * page can say it still refuses the user and link to the Login Limits page, and that it alone does not offer the
     * Clear login limits button, which would not lift it.
     */
    @Test
    public void testGetShowsAddressBlockThatRefusedUser() throws Exception
    {
        // Setup test fixture.
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 0, List.of(), 1, List.of(REFUSING_BLOCK), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(1, section().getRefusingAddresses().size(), "the IP address block that refused the user must be shown");
        assertEquals(OTHER_ADDRESS, section().getRefusingAddresses().get(0).getAddress(), "the IP address of the block must be shown");
        assertFalse(section().isBlocked(), "an IP address block must not be listed as one of the user's blocks to clear");
        assertFalse(section().isClearable(), "an IP address block alone must not offer the Clear login limits button");
        assertTrue(section().isShown(), "the Login limits section must be shown");
    }

    /**
     * Verifies that the page says how many blocks it does not show when the bounded listing is truncated.
     */
    @Test
    public void testGetSaysHowManyBlocksAreNotShown() throws Exception
    {
        // Setup test fixture.
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 25, List.of(ADDRESS_BLOCK), 3, List.of(REFUSING_BLOCK), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(24, section().getHiddenFromAddresses(), "the blocks from one IP address not shown must be counted");
        assertEquals(2, section().getHiddenRefusingAddresses(), "the IP address blocks not shown must be counted");
    }

    /**
     * Verifies that for a user the login limits do not refuse, the page gets no Login limits section to show, so that
     * it says in one line that the limits refuse no logins for the user, naming the user.
     */
    @Test
    public void testGetWithoutBlocksSaysLimitsRefuseNothing() throws Exception
    {
        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertFalse(section().isShown(), "a user without blocks must get the line saying the limits refuse nothing, not the Login limits box");
        assertFalse(section().isBlocked(), "a user without blocks must have no block to clear");
        assertFalse(section().isClearable(), "a user with nothing to clear must not get the Clear login limits button");
        assertEquals(USER, attributes.get("displayName"), "the line must be able to name the user");
        assertEquals(false, attributes.get("lockSetByLoginLimits"), "an account without a lock must not be shown as locked by the login limits");
    }

    /**
     * Verifies that a user with failure counts but no block gets the Login limits section with the Clear login limits
     * button, so that the counts can be reset before they lead to a block.
     */
    @Test
    public void testGetWithCountsOnlyOffersClearLoginLimits() throws Exception
    {
        // Setup test fixture.
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 0, List.of(), 0, List.of(), true));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertFalse(section().isBlocked(), "a user with only failure counts must have no block listed");
        assertTrue(section().isCounted(), "the page must be told failures are counted for the user");
        assertTrue(section().isClearable(), "the Clear login limits button must be shown to reset the counts");
        assertTrue(section().isShown(), "the Login limits section must be shown");
    }

    /**
     * Verifies that after the login limits were cleared, an IP address block that still refuses the user is shown, so
     * that the success message can say so, with no button to clear it.
     */
    @Test
    public void testGetAfterClearShowsAddressBlockStillRefusing() throws Exception
    {
        // Setup test fixture.
        params.put("cleared", "true");
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 0, List.of(), 1, List.of(REFUSING_BLOCK), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertTrue(section().isStillRefused(), "the page must be told an IP address block still refuses the user");
        assertFalse(section().isClearable(), "nothing is left that Clear login limits would clear");
    }

    /**
     * Verifies that after the login limits were cleared with no IP address block refusing the user, the page is not
     * told the user is still refused.
     */
    @Test
    public void testGetAfterClearWithoutAddressBlockIsNotStillRefused() throws Exception
    {
        // Setup test fixture.
        params.put("cleared", "true");

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertFalse(section().isStillRefused(), "without an IP address block the page must not say the user is still refused");
    }

    /**
     * Verifies that a read-only lockout provider is reported to the page, which disables the lock form only.
     */
    @Test
    public void testGetWithReadOnlyProvider() throws Exception
    {
        // Setup test fixture.
        when(lockOutProvider.isReadOnly()).thenReturn(true);
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 1, List.of(ADDRESS_BLOCK), 0, List.of(), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(true, attributes.get("readOnly"), "the page must be told the lockout provider is read-only");
        assertEquals(1, section().getFromAddresses().size(), "the blocks must still be listed");
        assertTrue(section().isClearable(), "the Clear login limits button must still be offered");
    }

    /**
     * Verifies that a request without a username goes to the user list.
     */
    @Test
    public void testGetWithoutUsernameRedirectsToUserList() throws Exception
    {
        // Setup test fixture.
        params.remove("username");

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        verify(response, times(1).description("a request without a username must go to the user list")).sendRedirect("user-summary.jsp");
        verify(dispatcher, never().description("no page must be rendered without a username")).forward(any(), any());
    }

    // ========== Clearing the login limits ==========

    /**
     * Verifies that Clear login limits clears the user's blocks and failure counts with the lock the login limits set,
     * logs one admin event saying so, and redirects with the outcome; IP address blocks are never cleared here.
     */
    @Test
    public void testPostClearLoginLimitsClearsBlocksAndLiftsLockSetByLoginLimits() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(ANY_ADDRESS_BLOCK, ADDRESS_BLOCK), true, true, false));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(limits, times(1).description("the user's login limits must be cleared")).clearBlocksForUsername(USER);
        verify(limits, never().description("an IP address block must not be cleared from a user's page")).clearBlocksForAddress(anyString());
        assertEquals(1, events.size(), "one admin event must be logged");
        assertEquals("Cleared the login limits for a user", events.get(0)[0], "the admin event must say the login limits were cleared");
        assertTrue(events.get(0)[1].contains("'john'"), "the admin event must name the user");
        assertTrue(events.get(0)[1].contains("with the account's lock"), "the admin event must say the lock was lifted");
        assertTrue(events.get(0)[1].contains("from IP address " + ADDRESS), "the admin event must list the block from one IP address");
        assertTrue(events.get(0)[1].contains("it would have ended at " + JiveGlobals.formatDateTime(Date.from(ENDS_AT)) + "."), "the admin event must give each block's end in the console's date format");
        verify(response, times(1).description("the page must say the login limits were cleared and the account unlocked"))
            .sendRedirect("user-lockout.jsp?username=john&cleared=true&unlocked=true");
    }

    /**
     * Verifies that clearing the login limits of a user locked by hand reports them cleared without the account
     * unlocked, and the admin event says the lock is left in place.
     */
    @Test
    public void testPostClearLoginLimitsLeavesLockSetByHand() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(ANY_ADDRESS_BLOCK), false, false, false));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(lockOutManager, never().description("the page must not unlock the account itself")).enableAccount(anyString());
        assertTrue(events.get(0)[1].contains("left in place"), "the admin event must say a lock set by hand is left in place");
        verify(response, times(1).description("the page must say only that the login limits were cleared")).sendRedirect("user-lockout.jsp?username=john&cleared=true");
    }

    /**
     * Verifies that clearing the login limits of a user with failure counts but no block resets the counts, logs an
     * admin event and says the login limits were cleared.
     */
    @Test
    public void testPostClearLoginLimitsResetsCountsWithoutBlocks() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(), true, false, false));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "resetting the counts must be logged");
        assertTrue(events.get(0)[1].contains("failure counts"), "the admin event must say the failure counts were reset");
        verify(response, times(1).description("the page must say the login limits were cleared")).sendRedirect("user-lockout.jsp?username=john&cleared=true");
    }

    /**
     * Verifies that the admin event names the username and the address of each cleared block, with control characters
     * escaped.
     */
    @Test
    public void testPostClearLoginLimitsLogsEscapedEvent() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, USER, false, "x\ny", 3, ENDS_AT, "x\ny" + '\u0000' + USER_KEY);
        params.put("username", "jo\nhn");
        params.put("action", "clear");
        when(limits.clearBlocksForUsername("jo\nhn")).thenReturn(new ClearedBlocks(List.of(block), true, false, false));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "one admin event must be logged");
        assertTrue(events.get(0)[1].contains("'jo\\u000ahn'"), "the admin event must name the username escaped");
        assertTrue(events.get(0)[1].contains("x\\u000ay"), "the admin event must name the escaped address");
        assertFalse(events.get(0)[1].contains("\ny"), "no control character from a client must reach the admin event");
    }

    /**
     * Verifies that clearing the login limits when there is nothing to clear logs nothing and says so.
     */
    @Test
    public void testPostClearLoginLimitsWithNothingToClearLogsNothing() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(), false, false, false));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertTrue(events.isEmpty(), "nothing cleared must mean no admin event");
        verify(response, times(1).description("the page must say there was nothing to clear")).sendRedirect("user-lockout.jsp?username=john&gone=true");
    }

    /**
     * Verifies that a lock that could not be lifted is reported, while what was cleared is still logged.
     */
    @Test
    public void testPostClearLoginLimitsReportsLockThatCouldNotBeLifted() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(ADDRESS_BLOCK), true, false, true));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "the blocks and counts that were cleared must still be logged");
        verify(response, times(1).description("the page must say the lock could not be lifted")).sendRedirect("user-lockout.jsp?username=john&lockfailed=true");
    }

    /**
     * Verifies that a lock that could not be lifted, with nothing else cleared, logs nothing.
     */
    @Test
    public void testPostClearLoginLimitsWithOnlyFailedLockLogsNothing() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(), false, false, true));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertTrue(events.isEmpty(), "nothing cleared must mean no admin event");
        verify(response, times(1).description("the page must say the lock could not be lifted")).sendRedirect("user-lockout.jsp?username=john&lockfailed=true");
    }

    /**
     * Verifies that with a read-only lockout provider, the login limits can still be cleared.
     */
    @Test
    public void testPostClearLoginLimitsWorksWithReadOnlyProvider() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        when(lockOutProvider.isReadOnly()).thenReturn(true);
        params.put("action", "clear");
        when(limits.clearBlocksForUsername(USER)).thenReturn(new ClearedBlocks(List.of(ADDRESS_BLOCK), true, false, false));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(limits, times(1).description("a read-only lockout provider must not stop the login limits being cleared")).clearBlocksForUsername(USER);
        assertEquals(1, events.size(), "the clear must be logged");
    }

    /**
     * Verifies that a clear request without a valid CSRF token clears nothing and logs nothing.
     */
    @Test
    public void testPostClearLoginLimitsWithoutCsrfTokenIsRejected() throws Exception
    {
        // Setup test fixture.
        params.put("csrf", "forged");
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("csrf", CSRF)});
        params.put("action", "clear");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(limits, never().description("a request without a valid CSRF token must clear nothing")).clearBlocksForUsername(anyString());
        assertTrue(events.isEmpty(), "a rejected request must not log an admin event");
        assertEquals(Map.of("csrf", "global.csrf.failed"), attributes.get("errors"), "the CSRF failure must be reported");
    }

    // ========== Locking and unlocking ==========

    /**
     * Verifies that locking at once and for ever locks the account without start or end, logs the event, closes the
     * user's sessions and returns to the user's page.
     */
    @Test
    public void testPostLockLocksAccountAndClosesSessions() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("lock", "Lock account");
        params.put("startdelay", "-1");
        params.put("duration", "-1");
        when(lockOutManager.isAccountDisabled(USER)).thenReturn(true);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(lockOutManager, times(1).description("the account must be locked without a start or end")).disableAccount(eq(USER), isNull(), isNull());
        assertEquals(List.of(USER), closedSessions, "the user's sessions must be closed when the lock applies at once");
        assertEquals("locked out user john", events.get(0)[0], "the lock must be logged as before");
        verify(response, times(1).description("locking must return to the user's page")).sendRedirect("user-properties.jsp?username=john&locksuccess=1");
    }

    /**
     * Verifies that a lock set to start later, for a custom number of minutes, is given its start and an end that many
     * minutes after the start, and leaves the user's sessions open.
     */
    @Test
    public void testPostLockWithDelayAndCustomDuration() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("lock", "Lock account");
        params.put("startdelay", "60");
        params.put("duration", "-2");
        params.put("duration_custom", "30");
        final long before = System.currentTimeMillis();

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        final ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager).disableAccount(eq(USER), start.capture(), end.capture());
        assertTrue(start.getValue().getTime() >= before + Duration.ofHours(1).toMillis(), "the lock must start an hour from now");
        assertEquals(Duration.ofMinutes(30).toMillis(), end.getValue().getTime() - start.getValue().getTime(), "the lock must last the minutes entered, from its start");
        assertEquals("start time = " + JiveGlobals.formatDateTime(start.getValue()) + ", end time = " + JiveGlobals.formatDateTime(end.getValue()), events.get(0)[1],
            "the admin event must give the lock's start and end in the console's date format");
        assertTrue(closedSessions.isEmpty(), "a lock that starts later must not close the user's sessions");
    }

    /**
     * Verifies that an administrator who locks their own account is signed out.
     */
    @Test
    public void testPostLockOfOwnAccountSignsOut() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("username", ADMIN);
        params.put("lock", "Lock account");
        when(lockOutManager.isAccountDisabled(ADMIN)).thenReturn(true);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(session, times(1).description("the administrator's sign-in must be removed")).removeAttribute("jive.admin.authToken");
        verify(response, times(1).description("the administrator must be sent to the sign-in page")).sendRedirect("login.jsp");
    }

    /**
     * Verifies that lock and unlock events are not logged when the security audit provider blocks user events.
     */
    @Test
    public void testPostLockLogsNothingWhenUserEventsAreBlocked() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        userEventsBlocked = true;
        params.put("lock", "Lock account");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(lockOutManager, times(1).description("the account must still be locked")).disableAccount(eq(USER), any(), any());
        assertTrue(events.isEmpty(), "no admin event must be logged while user events are blocked");
    }

    /**
     * Verifies that unlocking an account locked by hand unlocks it through the login limits, which leave their counts
     * and blocks alone for such a lock, logs the event as before and returns to the user's page.
     */
    @Test
    public void testPostUnlockOfLockSetByHandUnlocksOnly() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("unlock", "Unlock account");
        when(limits.unlockAccount(USER)).thenReturn(Optional.empty());

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(limits, times(1).description("the account must be unlocked")).unlockAccount(USER);
        verify(limits, never().description("unlocking a lock set by hand must not clear the login limits")).clearBlocksForUsername(anyString());
        assertEquals(1, events.size(), "one admin event must be logged");
        assertEquals("unlocked user john", events.get(0)[0], "the unlock must be logged as before");
        assertNull(events.get(0)[1], "an unlock that cleared no login limits must log no details");
        verify(response, times(1).description("unlocking must return to the user's page")).sendRedirect("user-properties.jsp?username=john&unlocksuccess=1");
    }

    /**
     * Verifies that unlocking an account the login limits locked logs what the login limits cleared with the unlock,
     * and returns to the user's page saying the login limits were cleared too.
     */
    @Test
    public void testPostUnlockOfLockSetByLoginLimitsSaysLoginLimitsWereCleared() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("unlock", "Unlock account");
        when(limits.unlockAccount(USER)).thenReturn(Optional.of(new ClearedBlocks(List.of(ANY_ADDRESS_BLOCK, ADDRESS_BLOCK), true, true, false)));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "one admin event must be logged");
        assertEquals("unlocked user john", events.get(0)[0], "the unlock must be logged as before");
        assertTrue(events.get(0)[1].contains("Cleared the login limits' blocks and failure counts"), "the admin event must say the login limits were cleared");
        assertTrue(events.get(0)[1].contains("from IP address " + ADDRESS), "the admin event must list the block from one IP address");
        verify(response, times(1).description("the user's page must say the login limits were cleared with the unlock"))
            .sendRedirect("user-properties.jsp?username=john&unlocksuccess=1&limitscleared=1");
    }

    /**
     * Verifies that an unlock of a lock the login limits set is logged with what was cleared even when the security
     * audit provider blocks user events, as clearing the login limits always is.
     */
    @Test
    public void testPostUnlockOfLockSetByLoginLimitsIsLoggedWhenUserEventsAreBlocked() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        userEventsBlocked = true;
        params.put("unlock", "Unlock account");
        when(limits.unlockAccount(USER)).thenReturn(Optional.of(new ClearedBlocks(List.of(ANY_ADDRESS_BLOCK), true, true, false)));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "clearing the login limits must be logged");
    }

    /**
     * Verifies that an unlock whose lock could not be lifted stays on the Lock Out page and says so.
     */
    @Test
    public void testPostUnlockReportsLockThatCouldNotBeLifted() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("unlock", "Unlock account");
        when(limits.unlockAccount(USER)).thenReturn(Optional.of(new ClearedBlocks(List.of(ADDRESS_BLOCK), true, false, true)));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "the blocks and counts that were cleared must still be logged");
        verify(response, times(1).description("the page must say the account could not be unlocked")).sendRedirect("user-lockout.jsp?username=john&unlockfailed=true");
    }

    /**
     * Verifies that a read-only lockout provider is not asked to lock or unlock.
     */
    @Test
    public void testPostLockWithReadOnlyProviderChangesNothing() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        when(lockOutProvider.isReadOnly()).thenReturn(true);
        params.put("lock", "Lock account");

        // Execute system under test.
        servlet.doPost(request, response);
        params.remove("lock");
        params.put("unlock", "Unlock account");
        servlet.doPost(request, response);

        // Verify result.
        verify(lockOutManager, never().description("a read-only provider must not be asked to lock")).disableAccount(anyString(), any(), any());
        verify(lockOutManager, never().description("a read-only provider must not be asked to unlock")).enableAccount(anyString());
        verify(limits, never().description("a read-only provider must not be asked to unlock through the login limits")).unlockAccount(anyString());
        assertTrue(events.isEmpty(), "nothing changed must mean no admin event");
    }

    /**
     * Verifies that a lock request without a valid CSRF token locks nothing.
     */
    @Test
    public void testPostLockWithoutCsrfTokenIsRejected() throws Exception
    {
        // Setup test fixture.
        params.put("lock", "Lock account");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(lockOutManager, never().description("a request without a CSRF token must lock nothing")).disableAccount(anyString(), any(), any());
        assertEquals(Map.of("csrf", "global.csrf.failed"), attributes.get("errors"), "the CSRF failure must be reported");
    }

    // ========== Block rows ==========

    /**
     * Verifies that control characters in a block's address are shown escaped.
     */
    @Test
    public void testBlockRowEscapesControlCharacters()
    {
        // Setup test fixture.
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, USER, false, "x\ty", 3, ENDS_AT, "k");

        // Execute system under test.
        final UserLockoutServlet.BlockRow row = new UserLockoutServlet.BlockRow(block, NOW);

        // Verify result.
        assertEquals("x\\u0009y", row.getAddress(), "control characters in an address must be shown escaped");
    }

    /**
     * Verifies that a block's end is shown in the console's date format with how long that is from now.
     */
    @Test
    public void testBlockRowSaysHowLongUntilItEnds()
    {
        // Execute system under test.
        final UserLockoutServlet.BlockRow row = new UserLockoutServlet.BlockRow(ADDRESS_BLOCK, NOW);

        // Verify result.
        assertEquals(JiveGlobals.formatDateTime(Date.from(ENDS_AT)), row.getEnds().getAt(), "the end must be shown in the console's date format");
        assertEquals(FROM_NOW, row.getEnds().getFromNow(), "the end must say how long that is from now");
    }

    /**
     * Verifies that a time that has passed is shown without a time from now.
     */
    @Test
    public void testTimeInThePastHasNoTimeFromNow()
    {
        // Execute system under test.
        final UserLockoutServlet.Time time = UserLockoutServlet.Time.of(NOW.minusSeconds(60), NOW);

        // Verify result.
        assertNull(time.getFromNow(), "a time that has passed must not say how long it is from now");
    }

    // ========== The answer at the top of the page ==========

    /**
     * Verifies that for a lock the login limits set, the page answers that the user cannot log in until the lock ends,
     * because the login limits locked the account after the failed attempts counted.
     */
    @Test
    public void testAnswerForLockSetByLoginLimits() throws Exception
    {
        // Setup test fixture.
        final LockOutFlag flag = new LockOutFlag(USER, Date.from(NOW.minus(Duration.ofHours(2))), Date.from(ENDS_AT));
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(flag);
        when(limits.isLockSetByLoginLimits(flag)).thenReturn(true);
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(ANY_ADDRESS_BLOCK, 0, List.of(), 0, List.of(), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.LOCKED_BY_LOGIN_LIMITS.name(), answer().getKind(), "the answer must be that the login limits locked the account");
        assertTrue(answer().isRefused(), "the answer must be that the user cannot log in");
        assertEquals(FROM_NOW, answer().getUntil().getFromNow(), "the answer must say how long until the lock ends");
        assertEquals(5L, answer().getFailures(), "the answer must give the failed attempts that set the lock");
        assertNull(((UserLockoutServlet.Time) attributes.get("lockStart")).getFromNow(), "a lock's start that has passed must not say how long it is from now");
    }

    /**
     * Verifies that for a lock set by hand without an end, the page answers that the user cannot log in until the
     * account is unlocked.
     */
    @Test
    public void testAnswerForLockSetByHandWithoutEnd() throws Exception
    {
        // Setup test fixture.
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, null));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.LOCKED.name(), answer().getKind(), "the answer must be that the account is locked until it is unlocked");
        assertNull(answer().getUntil(), "a lock without an end must give no end");
        assertNull(lockEnd(), "a lock without an end must give the page no end");
    }

    /**
     * Verifies that for a lock set by hand with an end, the page answers that the user cannot log in until it ends.
     */
    @Test
    public void testAnswerForLockSetByHandWithEnd() throws Exception
    {
        // Setup test fixture.
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, Date.from(ENDS_AT)));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.LOCKED_UNTIL.name(), answer().getKind(), "the answer must be that the account is locked until its end");
        assertEquals(FROM_NOW, answer().getUntil().getFromNow(), "the answer must say how long until the lock ends");
    }

    /**
     * Verifies that for a lock that starts later, and nothing else that refuses the user, the page answers that the
     * user can log in until the lock starts.
     */
    @Test
    public void testAnswerForLockThatStartsLater() throws Exception
    {
        // Setup test fixture.
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, Date.from(ENDS_AT), null));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.CAN_LOG_IN_UNTIL_LOCK.name(), answer().getKind(), "the answer must be that the user can log in until the lock starts");
        assertFalse(answer().isRefused(), "a lock that has not started must not refuse the user");
        assertEquals(FROM_NOW, answer().getUntil().getFromNow(), "the answer must say how long until the lock starts");
        assertEquals(false, attributes.get("lockInForce"), "a lock that starts later must not be in force");
        assertEquals(true, attributes.get("locked"), "a lock that starts later must still be shown, with Unlock account");
    }

    /**
     * Verifies that for a user the login limits refuse from any IP address without a lock, the page answers that the
     * user cannot log in until the block ends, and that the account is not locked.
     */
    @Test
    public void testAnswerForBlockFromAnyAddressWithoutLock() throws Exception
    {
        // Setup test fixture.
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(ANY_ADDRESS_BLOCK, 1, List.of(ADDRESS_BLOCK), 0, List.of(), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.REFUSED_FROM_ANY_ADDRESS.name(), answer().getKind(), "the answer must be that the login limits refuse the user from any IP address");
        assertEquals(5L, answer().getFailures(), "the answer must give the failed attempts that set the block");
        assertFalse(answer().isAccountLocked(), "the answer must say the account is not locked");
    }

    /**
     * Verifies that for a user the login limits refuse from some IP addresses, the page answers that the user cannot
     * log in from those addresses (the user's own blocks and the IP address blocks that refused the user), giving each
     * address its own end, as the blocks can end at different times.
     */
    @Test
    public void testAnswerForBlocksFromAddresses() throws Exception
    {
        // Setup test fixture.
        final ActiveBlock later = new ActiveBlock(Tier.ADDRESS, null, false, OTHER_ADDRESS, 40, ENDS_AT.plus(Duration.ofHours(1)), OTHER_ADDRESS);
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 1, List.of(ADDRESS_BLOCK), 1, List.of(later), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.REFUSED_FROM_ADDRESSES.name(), answer().getKind(), "the answer must be that the user is refused from some IP addresses");
        assertEquals(List.of(ADDRESS, OTHER_ADDRESS), answer().getAddresses().stream().map(UserLockoutServlet.RefusedAddress::getAddress).toList(), "the answer must name the user's blocked address and the address block that refused the user");
        assertEquals(FROM_NOW, answer().getAddresses().get(0).getUntil().getFromNow(), "the answer must give the user's blocked address its own end");
        assertEquals("2 hours, 26 minutes from now", answer().getAddresses().get(1).getUntil().getFromNow(), "the answer must give the address block that refused the user its own end");
        assertEquals(2, answer().getAddressCount(), "the answer must count the addresses");
        assertEquals("2 hours, 26 minutes from now", answer().getUntil().getFromNow(), "the answer must give the end of the last block to end");
        assertFalse(answer().isAccountLocked(), "the answer must say the account is not locked");
    }

    /**
     * Verifies that the answer counts the IP addresses the page does not list, and names an address once when the user
     * is refused from it both by a block of the user and by a block of the address, ending when the later block ends.
     */
    @Test
    public void testAnswerCountsAddressesNotShown() throws Exception
    {
        // Setup test fixture.
        final ActiveBlock sameAddress = new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 40, ENDS_AT.plus(Duration.ofHours(1)), ADDRESS);
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 4, List.of(ADDRESS_BLOCK), 1, List.of(sameAddress), false));

        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        assertEquals(List.of(ADDRESS), answer().getAddresses().stream().map(UserLockoutServlet.RefusedAddress::getAddress).toList(), "an address refused by both kinds of block must be named once");
        assertEquals("2 hours, 26 minutes from now", answer().getAddresses().get(0).getUntil().getFromNow(), "an address refused by both kinds of block must end when the later block ends");
        assertEquals(4, answer().getAddressCount(), "the addresses not shown must be counted");
    }

    /**
     * Verifies that for a user nothing refuses, the page answers that the user can log in, and says so differently when
     * failed attempts are counted for the user.
     */
    @Test
    public void testAnswerForUserWhoCanLogIn() throws Exception
    {
        // Execute system under test.
        servlet.doGet(request, response);
        final UserLockoutServlet.Answer nothing = answer();
        when(limits.getBlocksForUsername(USER, UserLockoutServlet.MAX_ROWS)).thenReturn(new BlocksForUsername(null, 0, List.of(), 0, List.of(), true));
        servlet.doGet(request, response);
        final UserLockoutServlet.Answer counted = answer();

        // Verify result.
        assertEquals(UserLockoutServlet.Answer.Kind.CAN_LOG_IN.name(), nothing.getKind(), "a user nothing refuses must be able to log in");
        assertFalse(nothing.isRefused(), "a user nothing refuses must not be refused");
        assertEquals(UserLockoutServlet.Answer.Kind.CAN_LOG_IN_COUNTED.name(), counted.getKind(), "the answer must say when failed attempts are counted for the user");
    }
}
