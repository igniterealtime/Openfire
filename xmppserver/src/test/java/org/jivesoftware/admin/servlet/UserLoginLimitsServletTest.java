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
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ActiveBlock;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.Tier;
import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserLoginLimitsServlet}, the admin console page for the user login limits.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UserLoginLimitsServletTest
{
    private static final String CSRF = "token1234";
    private static final String ADDRESS = "192.0.2.1";
    private static final Instant ENDS_AT = Instant.parse("2026-11-10T10:00:00Z");

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private RequestDispatcher dispatcher;

    @Mock
    private UserLoginLimitManager manager;

    private final Map<String, String> params = new HashMap<>();
    private final List<String[]> events = new ArrayList<>();
    private UserLoginLimitsServlet servlet;

    private boolean originalEnabled;
    private long originalMaxPair;
    private long originalMaxAddress;
    private long originalMaxUsername;
    private Duration originalPairInterval;
    private Duration originalAddressInterval;
    private Duration originalUsernameInterval;
    private Duration originalLockoutDuration;
    private Duration originalPairBlockDuration;
    private Duration originalAddressBlockDuration;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        originalEnabled = UserLoginLimitManager.ENABLED.getValue();
        originalMaxPair = UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue();
        originalMaxAddress = UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getValue();
        originalMaxUsername = UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue();
        originalPairInterval = UserLoginLimitManager.PER_PAIR_INTERVAL.getValue();
        originalAddressInterval = UserLoginLimitManager.PER_ADDRESS_INTERVAL.getValue();
        originalUsernameInterval = UserLoginLimitManager.PER_USERNAME_INTERVAL.getValue();
        originalLockoutDuration = UserLoginLimitManager.LOCKOUT_DURATION.getValue();
        originalPairBlockDuration = UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.getValue();
        originalAddressBlockDuration = UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.getValue();

        UserLoginLimitManager.ENABLED.setValue(false);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(10L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(40L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(50L);
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(Duration.ofMinutes(15));
        UserLoginLimitManager.PER_ADDRESS_INTERVAL.setValue(Duration.ofMinutes(15));
        UserLoginLimitManager.PER_USERNAME_INTERVAL.setValue(Duration.ofHours(1));
        UserLoginLimitManager.LOCKOUT_DURATION.setValue(Duration.ofMinutes(30));
        UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.setValue(Duration.ofMinutes(15));
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(Duration.ofMinutes(15));

        when(request.getSession()).thenReturn(session);
        when(request.getServerName()).thenReturn("localhost");
        when(request.getParameter(anyString())).thenAnswer(invocation -> params.get(invocation.<String>getArgument(0)));
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        when(manager.getActiveBlocks(100)).thenReturn(List.of());

        servlet = new UserLoginLimitsServlet(() -> manager, username -> false) {
            @Override
            protected void logEvent(final HttpServletRequest request, final HttpServletResponse response, final String summary, final String details)
            {
                events.add(new String[] {summary, details});
            }
        };
    }

    @AfterEach
    public void tearDown()
    {
        UserLoginLimitManager.ENABLED.setValue(originalEnabled);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(originalMaxPair);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(originalMaxAddress);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(originalMaxUsername);
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(originalPairInterval);
        UserLoginLimitManager.PER_ADDRESS_INTERVAL.setValue(originalAddressInterval);
        UserLoginLimitManager.PER_USERNAME_INTERVAL.setValue(originalUsernameInterval);
        UserLoginLimitManager.LOCKOUT_DURATION.setValue(originalLockoutDuration);
        UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.setValue(originalPairBlockDuration);
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(originalAddressBlockDuration);
    }

    /**
     * Fills the request with the settings form as the page shows it for the defaults set up above.
     */
    private void defaultSettingsForm()
    {
        params.put("enabled", "false");
        params.put("pairLimit", "10");
        params.put("pairInterval", "15");
        params.put("pairBlockDuration", "15");
        params.put("addressLimit", "40");
        params.put("addressInterval", "15");
        params.put("addressBlockDuration", "15");
        params.put("usernameLimit", "50");
        params.put("usernameInterval", "60");
        params.put("lockoutDuration", "30");
    }

    private void validCsrf()
    {
        params.put("csrf", CSRF);
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("csrf", CSRF)});
    }

    // ========== Settings form ==========

    /**
     * Verifies that valid settings are applied, and that the changes are described for the admin event.
     */
    @Test
    public void testSettingsFormAppliesValidValues()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("enabled", "true");
        params.put("pairLimit", "3");
        params.put("lockoutDuration", "1440");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);
        final List<String> changes = form.apply();

        // Verify result.
        assertTrue(form.getErrors().isEmpty(), "valid values must not be reported as errors");
        assertTrue(UserLoginLimitManager.ENABLED.getValue(), "the limits must be turned on");
        assertEquals(3L, UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue(), "the tier 1 limit must be saved");
        assertEquals(Duration.ofDays(1), UserLoginLimitManager.LOCKOUT_DURATION.getValue(), "a lockout duration of one day must be accepted");
        assertEquals(List.of("xmpp.auth.loginlimit.enabled = true", "xmpp.auth.loginlimit.maxAttemptsPerUsernameAddressPair = 3",
            "xmpp.auth.loginlimit.lockoutDuration = 1440 minutes"), changes, "exactly the changed settings must be described");
    }

    /**
     * Verifies that the tier 1 and tier 2 block durations are saved in minutes and described for the admin event.
     */
    @Test
    public void testSettingsFormAppliesBlockDurations()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("pairBlockDuration", "5");
        params.put("addressBlockDuration", "60");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);
        final List<String> changes = form.apply();

        // Verify result.
        assertTrue(form.getErrors().isEmpty(), "valid block durations must not be reported as errors");
        assertEquals(Duration.ofMinutes(5), UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.getValue(), "the tier 1 block duration must be saved");
        assertEquals(Duration.ofHours(1), UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.getValue(), "the tier 2 block duration must be saved");
        assertEquals(List.of("xmpp.auth.loginlimit.perUsernameAddressPairBlockDuration = 5 minutes", "xmpp.auth.loginlimit.perAddressBlockDuration = 60 minutes"),
            changes, "exactly the changed block durations must be described");
    }

    /**
     * Verifies that a limit of 0, which turns a tier off, is accepted.
     */
    @Test
    public void testSettingsFormAcceptsZeroLimit()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("addressLimit", "0");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);
        form.apply();

        // Verify result.
        assertTrue(form.getErrors().isEmpty(), "a limit of 0 must be accepted");
        assertEquals(0L, UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getValue(), "a limit of 0 must be saved");
    }

    /**
     * Verifies that negative or non-numeric limits are rejected, field by field.
     */
    @Test
    public void testSettingsFormRejectsInvalidLimits()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("pairLimit", "-1");
        params.put("addressLimit", "lots");
        params.put("usernameLimit", "");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);

        // Verify result.
        assertEquals(3, form.getErrors().size(), "each invalid limit must be reported");
        assertTrue(form.getErrors().containsKey("pairLimit"), "a negative limit must be rejected");
        assertTrue(form.getErrors().containsKey("addressLimit"), "a limit that is not a number must be rejected");
        assertTrue(form.getErrors().containsKey("usernameLimit"), "a missing limit must be rejected");
        assertEquals("-1", form.getPairLimit(), "the submitted value must be kept for display");
    }

    /**
     * Verifies that a form without the on/off choice is rejected rather than read as off, and that a value other than
     * true or false is rejected too.
     */
    @Test
    public void testSettingsFormRequiresEnabledChoice()
    {
        // Setup test fixture.
        UserLoginLimitManager.ENABLED.setValue(true);
        defaultSettingsForm();
        params.remove("enabled");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm missing = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);
        final List<String> changes = missing.apply();
        params.put("enabled", "maybe");
        final UserLoginLimitsServlet.SettingsForm invalid = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);

        // Verify result.
        assertEquals(Map.of("enabled", "user.login.limits.error.enabled"), missing.getErrors(), "a missing on/off choice must be reported");
        assertTrue(changes.isEmpty(), "a form without the on/off choice must change nothing");
        assertTrue(UserLoginLimitManager.ENABLED.getValue(), "a form without the on/off choice must not turn the limits off");
        assertTrue(invalid.getErrors().containsKey("enabled"), "an on/off value other than true or false must be reported");
    }

    /**
     * Verifies that durations of zero, below zero, over one day or not a whole number of minutes are rejected.
     */
    @Test
    public void testSettingsFormRejectsInvalidDurations()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("pairInterval", "0");
        params.put("addressInterval", "-5");
        params.put("usernameInterval", "1441");
        params.put("lockoutDuration", "2.5");
        params.put("pairBlockDuration", "0");
        params.put("addressBlockDuration", "1441");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);

        // Verify result.
        assertEquals(6, form.getErrors().size(), "each invalid duration must be reported");
        assertTrue(form.getErrors().containsKey("pairBlockDuration"), "a tier 1 block duration of 0 must be rejected");
        assertTrue(form.getErrors().containsKey("addressBlockDuration"), "a tier 2 block duration over one day must be rejected");
        assertTrue(form.getErrors().containsKey("pairInterval"), "a duration of 0 must be rejected");
        assertTrue(form.getErrors().containsKey("addressInterval"), "a negative duration must be rejected");
        assertTrue(form.getErrors().containsKey("usernameInterval"), "a duration over one day must be rejected");
        assertTrue(form.getErrors().containsKey("lockoutDuration"), "a duration that is not a whole number of minutes must be rejected");
    }

    /**
     * Verifies that a form with errors applies nothing, not even its valid values.
     */
    @Test
    public void testSettingsFormWithErrorsAppliesNothing()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("enabled", "true");
        params.put("pairLimit", "-1");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);
        final List<String> changes = form.apply();

        // Verify result.
        assertTrue(changes.isEmpty(), "no change must be reported for a form with errors");
        assertFalse(UserLoginLimitManager.ENABLED.getValue(), "a valid value must not be saved while another is invalid");
    }

    /**
     * Verifies that the form shows the current settings, with durations in minutes.
     */
    @Test
    public void testSettingsFormShowsCurrentSettings()
    {
        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromCurrentSettings();

        // Verify result.
        assertFalse(form.isEnabled(), "the form must show that the limits are off");
        assertEquals("10", form.getPairLimit(), "the form must show the tier 1 limit");
        assertEquals("60", form.getUsernameInterval(), "the form must show the tier 3 interval in minutes");
        assertEquals("30", form.getLockoutDuration(), "the form must show the lockout duration in minutes");
        assertEquals("15", form.getPairBlockDuration(), "the form must show the tier 1 block duration in minutes");
        assertEquals("15", form.getAddressBlockDuration(), "the form must show the tier 2 block duration in minutes");
    }

    /**
     * Verifies that a duration set elsewhere to a value that is not a whole number of minutes is also given in full,
     * for the page to show beside its field, which shows it rounded up to whole minutes.
     */
    @Test
    public void testSettingsFormShowsDurationsThatAreNotWholeMinutesExactly()
    {
        // Setup test fixture.
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(Duration.ofSeconds(90));
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(Duration.ofMillis(20_000));

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromCurrentSettings();

        // Verify result.
        assertEquals(Map.of("pairInterval", StringUtils.getFullElapsedTime(Duration.ofSeconds(90)),
                "addressBlockDuration", StringUtils.getFullElapsedTime(Duration.ofSeconds(20))),
            form.getExactDurations(), "exactly the durations that are not whole minutes must be given in full, beside their rounded-up fields");
    }

    /**
     * Verifies that a duration set elsewhere to a value that is not a whole number of minutes is left as it is when
     * the form is saved without changing it.
     */
    @Test
    public void testSettingsFormLeavesUnchangedDurationAlone()
    {
        // Setup test fixture.
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(Duration.ofSeconds(90));
        final UserLoginLimitsServlet.SettingsForm shown = UserLoginLimitsServlet.SettingsForm.fromCurrentSettings();
        defaultSettingsForm();
        params.put("pairInterval", shown.getPairInterval());

        // Execute system under test.
        final List<String> changes = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get).apply();

        // Verify result.
        assertEquals("2", shown.getPairInterval(), "a duration that is not whole minutes must be shown rounded up");
        assertEquals(Duration.ofSeconds(90), UserLoginLimitManager.PER_PAIR_INTERVAL.getValue(), "an unchanged field must leave its setting alone");
        assertTrue(changes.isEmpty(), "an unchanged form must report no changes");
    }

    // ========== Block tokens and rows ==========

    /**
     * Verifies that a block's key survives the round trip through the page, including the NUL character that
     * separates a tier 1 key's parts, and that the token is safe to place in a form.
     */
    @Test
    public void testBlockTokenRoundTrip()
    {
        // Setup test fixture.
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, "alice", false, ADDRESS, 3, ENDS_AT, ADDRESS + '\u0000' + "alice");

        // Execute system under test.
        final String token = UserLoginLimitsServlet.blockToken(block);
        final Optional<UserLoginLimitsServlet.BlockReference> result = UserLoginLimitsServlet.parseBlockToken(token);

        // Verify result.
        assertTrue(token.matches("[A-Za-z0-9_.-]+"), "the token must contain only characters that are safe in a form value");
        assertEquals(Optional.of(new UserLoginLimitsServlet.BlockReference(Tier.USERNAME_FROM_ADDRESS, block.key())), result, "the token must identify the block's tier and key");
    }

    /**
     * Verifies that tokens that do not name a tier and key are rejected.
     */
    @Test
    public void testBlockTokenRejectsInvalidTokens()
    {
        assertTrue(UserLoginLimitsServlet.parseBlockToken(null).isEmpty(), "a missing token must be rejected");
        assertTrue(UserLoginLimitsServlet.parseBlockToken("ADDRESS").isEmpty(), "a token without a key must be rejected");
        assertTrue(UserLoginLimitsServlet.parseBlockToken("NO_SUCH_TIER.YWJj").isEmpty(), "a token with an unknown tier must be rejected");
        assertTrue(UserLoginLimitsServlet.parseBlockToken("ADDRESS.!!!").isEmpty(), "a token whose key is not base64 must be rejected");
    }

    /**
     * Verifies that a tier 3 row is marked when its account is locked, so that the page can link to the lockout page.
     */
    @Test
    public void testRowsMarkLockedAccounts()
    {
        // Setup test fixture.
        final List<ActiveBlock> blocks = List.of(
            new ActiveBlock(Tier.USERNAME, "alice", false, null, 50, ENDS_AT, "a"),
            new ActiveBlock(Tier.USERNAME, "bob", false, null, 50, ENDS_AT, "b"),
            new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, "alice", false, ADDRESS, 10, ENDS_AT, "c"));

        // Execute system under test.
        final List<UserLoginLimitsServlet.BlockRow> rows = UserLoginLimitsServlet.toRows(blocks, "alice"::equals);

        // Verify result.
        assertEquals(3, rows.size(), "every block must have a row");
        assertTrue(rows.get(0).isAccountLocked(), "a tier 3 row for a locked account must be marked");
        assertEquals("alice", rows.get(0).getLockoutUsername(), "the row must give the username to link to");
        assertFalse(rows.get(1).isAccountLocked(), "a tier 3 row for an account that is not locked must not be marked");
        assertFalse(rows.get(2).isAccountLocked(), "only tier 3 rows may be marked as locked");
    }

    /**
     * Verifies that a shortened username is not looked up, as the lookup could find another account, and that a
     * username of 64 characters that was not shortened is looked up as any other.
     */
    @Test
    public void testRowsLookUpOnlyUsernamesThatWereNotShortened()
    {
        // Setup test fixture.
        final List<ActiveBlock> blocks = List.of(
            new ActiveBlock(Tier.USERNAME, "z".repeat(64), true, null, 50, ENDS_AT, "a"),
            new ActiveBlock(Tier.USERNAME, "y".repeat(64), false, null, 50, ENDS_AT, "b"));

        // Execute system under test.
        final List<UserLoginLimitsServlet.BlockRow> rows = UserLoginLimitsServlet.toRows(blocks, username -> true);

        // Verify result.
        assertFalse(rows.get(0).isAccountLocked(), "a shortened username must not be looked up");
        assertTrue(rows.get(0).isUsernameTruncated(), "the row must say the username was shortened");
        assertTrue(rows.get(1).isAccountLocked(), "a 64-character username that was not shortened must be looked up");
        assertFalse(rows.get(1).isUsernameTruncated(), "a username that was not shortened must not be marked as shortened");
    }

    /**
     * Verifies that each tier reports its total, and says when it shows fewer blocks than are in force.
     */
    @Test
    public void testTierSectionsReportTotalsAndTruncation()
    {
        // Setup test fixture.
        final ActiveBlock block = new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 40, ENDS_AT, ADDRESS);
        final List<UserLoginLimitManager.TierBlocks> tiers = List.of(
            new UserLoginLimitManager.TierBlocks(Tier.USERNAME_FROM_ADDRESS, 0, List.of()),
            new UserLoginLimitManager.TierBlocks(Tier.ADDRESS, 250, List.of(block)),
            new UserLoginLimitManager.TierBlocks(Tier.USERNAME, 1, List.of(new ActiveBlock(Tier.USERNAME, "alice", false, null, 50, ENDS_AT, "a"))));

        // Execute system under test.
        final List<UserLoginLimitsServlet.TierSection> sections = UserLoginLimitsServlet.toSections(tiers, username -> false);

        // Verify result.
        assertEquals(3, sections.size(), "every tier must have a section");
        assertEquals(250, sections.get(1).getTotal(), "a section must give the tier's total");
        assertEquals(1, sections.get(1).getRows().size(), "a section must list only the blocks returned");
        assertTrue(sections.get(1).isTruncated(), "a section showing fewer blocks than are in force must say so");
        assertFalse(sections.get(2).isTruncated(), "a section showing every block must not say it is truncated");
        assertEquals(250, UserLoginLimitsServlet.section(sections, Tier.ADDRESS).getTotal(), "a tier's own section must be found for its table");
    }

    /**
     * Verifies that a tier the manager reports nothing for still gets an empty section, so that its table shows the
     * empty-list row.
     */
    @Test
    public void testMissingTierGetsEmptySection()
    {
        // Execute system under test.
        final UserLoginLimitsServlet.TierSection result = UserLoginLimitsServlet.section(List.of(), Tier.USERNAME);

        // Verify result.
        assertEquals(0, result.getTotal(), "a missing tier must have no blocks in force");
        assertTrue(result.getRows().isEmpty(), "a missing tier must list no rows");
        assertFalse(result.isTruncated(), "an empty section must not say it is truncated");
    }

    /**
     * Verifies that a failure to check an account's lock leaves the row unmarked rather than breaking the page.
     */
    @Test
    public void testRowsSurviveFailedLockCheck()
    {
        // Setup test fixture.
        final List<ActiveBlock> blocks = List.of(new ActiveBlock(Tier.USERNAME, "alice", false, null, 50, ENDS_AT, "a"));

        // Execute system under test.
        final List<UserLoginLimitsServlet.BlockRow> rows = UserLoginLimitsServlet.toRows(blocks, username -> { throw new IllegalStateException("lookup failed"); });

        // Verify result.
        assertFalse(rows.get(0).isAccountLocked(), "a failed lock check must leave the row unmarked");
    }

    /**
     * Verifies that control characters in a username or address are shown escaped.
     */
    @Test
    public void testRowsEscapeControlCharacters()
    {
        // Setup test fixture.
        final List<ActiveBlock> blocks = List.of(new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, "eve\nforged", false, "x\ty", 10, ENDS_AT, "a"));

        // Execute system under test.
        final List<UserLoginLimitsServlet.BlockRow> rows = UserLoginLimitsServlet.toRows(blocks, username -> false);

        // Verify result.
        assertEquals("eve\\u000aforged", rows.get(0).getUsername(), "control characters in a username must be shown escaped");
        assertEquals("x\\u0009y", rows.get(0).getAddress(), "control characters in an address must be shown escaped");
    }

    // ========== Requests ==========

    /**
     * Verifies that the page is rendered with a CSRF token, the current settings and the blocks.
     */
    @Test
    public void testGetRendersPage() throws Exception
    {
        // Execute system under test.
        servlet.doGet(request, response);

        // Verify result.
        verify(response, times(1).description("a CSRF cookie must be set")).addCookie(any(Cookie.class));
        verify(request, times(1).description("the CSRF token must be given to the page")).setAttribute(eq("csrf"), anyString());
        verify(request, times(1).description("the settings must be given to the page")).setAttribute(eq("settings"), any(UserLoginLimitsServlet.SettingsForm.class));
        verify(request, times(1).description("the page must be given a table of blocks per username from one IP address")).setAttribute(eq("pairBlocks"), any(UserLoginLimitsServlet.TierSection.class));
        verify(request, times(1).description("the page must be given a table of blocks per IP address")).setAttribute(eq("addressBlocks"), any(UserLoginLimitsServlet.TierSection.class));
        verify(request, times(1).description("the page must be given a table of blocks per username from any IP address")).setAttribute(eq("usernameBlocks"), any(UserLoginLimitsServlet.TierSection.class));
        verify(request, times(1).description("the longest duration the form accepts must be given to the page, in minutes")).setAttribute("maxDurationMinutes", UserLoginLimitManager.MAX_DURATION.toMinutes());
        verify(manager, times(1).description("at most 100 blocks per tier must be listed")).getActiveBlocks(100);
        verify(dispatcher, times(1).description("the page must be rendered")).forward(request, response);
    }

    /**
     * Verifies that a request without a valid CSRF token changes nothing and logs nothing.
     */
    @Test
    public void testPostWithoutCsrfTokenIsRejected() throws Exception
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("action", "save");
        params.put("enabled", "true");
        params.put("csrf", "forged");
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("csrf", CSRF)});

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertFalse(UserLoginLimitManager.ENABLED.getValue(), "a request without a valid CSRF token must change nothing");
        assertTrue(events.isEmpty(), "a rejected request must not log an admin event");
        verify(request, times(1).description("the CSRF failure must be reported")).setAttribute(eq("errors"), eq(Map.of("csrf", "global.csrf.failed")));
        verify(response, never().description("a rejected request must not redirect as if it succeeded")).sendRedirect(anyString());
    }

    /**
     * Verifies that a clear request without a valid CSRF token clears nothing.
     */
    @Test
    public void testClearWithoutCsrfTokenIsRejected() throws Exception
    {
        // Setup test fixture.
        params.put("action", "clear");
        params.put("block", UserLoginLimitsServlet.blockToken(new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 40, ENDS_AT, ADDRESS)));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(manager, never().description("a request without a CSRF token must clear nothing")).clearActiveBlock(any(), anyString());
        assertTrue(events.isEmpty(), "a rejected request must not log an admin event");
    }

    /**
     * Verifies that saving valid settings applies them, logs one admin event naming the changes, and redirects.
     */
    @Test
    public void testPostSaveAppliesSettingsAndLogsEvent() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        defaultSettingsForm();
        params.put("action", "save");
        params.put("enabled", "true");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertTrue(UserLoginLimitManager.ENABLED.getValue(), "valid settings must be applied");
        assertEquals(1, events.size(), "one admin event must be logged for the change");
        assertTrue(events.get(0)[1].contains("xmpp.auth.loginlimit.enabled = true"), "the admin event must name the change");
        verify(response, times(1).description("a successful save must redirect")).sendRedirect("user-login-limits.jsp?saved=true");
    }

    /**
     * Verifies that saving without any change logs no admin event.
     */
    @Test
    public void testPostSaveWithoutChangesLogsNothing() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        defaultSettingsForm();
        params.put("action", "save");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertTrue(events.isEmpty(), "a save that changes nothing must not log an admin event");
        verify(response, times(1).description("a save must redirect")).sendRedirect("user-login-limits.jsp?saved=true");
    }

    /**
     * Verifies that invalid settings re-render the page with the errors and change nothing.
     */
    @Test
    public void testPostSaveWithErrorsRendersErrors() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        defaultSettingsForm();
        params.put("action", "save");
        params.put("lockoutDuration", "0");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(Duration.ofMinutes(30), UserLoginLimitManager.LOCKOUT_DURATION.getValue(), "an invalid setting must not be applied");
        assertTrue(events.isEmpty(), "an invalid save must not log an admin event");
        verify(request, times(1).description("the errors must be given to the page")).setAttribute(eq("errors"), any(Map.class));
        verify(dispatcher, times(1).description("the page must be shown again with the errors")).forward(request, response);
    }

    /**
     * Verifies that clearing a block lifts it through the manager, logs an admin event describing it, and redirects.
     */
    @Test
    public void testPostClearLiftsBlockAndLogsEvent() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, "alice", false, ADDRESS, 10, ENDS_AT, ADDRESS + '\u0000' + "alice");
        when(manager.clearActiveBlock(Tier.USERNAME_FROM_ADDRESS, block.key())).thenReturn(Optional.of(block));
        params.put("action", "clear");
        params.put("block", UserLoginLimitsServlet.blockToken(block));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(manager, times(1).description("the block must be cleared by its tier and key")).clearActiveBlock(Tier.USERNAME_FROM_ADDRESS, block.key());
        assertEquals(1, events.size(), "one admin event must be logged for the cleared block");
        assertTrue(events.get(0)[1].contains("'alice'") && events.get(0)[1].contains(ADDRESS), "the admin event must name the username and address of the block");
        verify(response, times(1).description("clearing must redirect")).sendRedirect("user-login-limits.jsp?cleared=true");
    }

    /**
     * Verifies that clearing a block that is no longer in force logs nothing and says so.
     */
    @Test
    public void testPostClearOfEndedBlockLogsNothing() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        final ActiveBlock block = new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 40, ENDS_AT, ADDRESS);
        when(manager.clearActiveBlock(Tier.ADDRESS, ADDRESS)).thenReturn(Optional.empty());
        params.put("action", "clear");
        params.put("block", UserLoginLimitsServlet.blockToken(block));

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertTrue(events.isEmpty(), "nothing cleared must mean no admin event");
        verify(response, times(1).description("the page must say the block was no longer in force")).sendRedirect("user-login-limits.jsp?gone=true");
    }

    /**
     * Verifies that a clear request with a token that does not name a block is reported as an error.
     */
    @Test
    public void testPostClearWithInvalidTokenIsReported() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        params.put("block", "garbage");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(manager, never().description("an invalid token must clear nothing")).clearActiveBlock(any(), anyString());
        verify(request, times(1).description("the invalid request must be reported")).setAttribute(eq("errors"), eq(Map.of("block", "user.login.limits.error.block")));
    }
}
