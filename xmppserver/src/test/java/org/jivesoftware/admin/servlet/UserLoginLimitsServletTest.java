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
import org.jivesoftware.openfire.auth.UserLoginLimitManager.BlocksByAddress;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.BlocksForAddress;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.RefusedUsername;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.Tier;
import org.jivesoftware.util.JiveGlobals;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private static final String OTHER_ADDRESS = "198.51.100.7";
    private static final Instant ENDS_AT = Instant.parse("2026-11-10T10:00:00Z");

    /**
     * The page's clock: 1 hour and 26 minutes before the blocks end, so that each end is shown as that long from now.
     */
    private static final Instant NOW = ENDS_AT.minus(Duration.ofMinutes(86));

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
        when(manager.getBlocksByAddress(anyInt(), anyInt())).thenReturn(new BlocksByAddress(0, List.of()));

        servlet = new UserLoginLimitsServlet(() -> manager, username -> true, Clock.fixed(NOW, ZoneOffset.UTC)) {
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
     * for the page to show beside its field, which is then empty.
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
            form.getExactDurations(), "exactly the durations that are not whole minutes must be given in full, beside their empty fields");
    }

    /**
     * Verifies that a duration set elsewhere to a value that is not a whole number of minutes is shown with an empty
     * field, and is left as it is when the form is saved with that field left empty.
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
        final UserLoginLimitsServlet.SettingsForm submitted = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);
        final List<String> changes = submitted.apply();

        // Verify result.
        assertEquals("", shown.getPairInterval(), "a duration that is not whole minutes must be shown with an empty field");
        assertTrue(submitted.getErrors().isEmpty(), "an empty field for a duration that is not whole minutes must be accepted");
        assertEquals(Duration.ofSeconds(90), UserLoginLimitManager.PER_PAIR_INTERVAL.getValue(), "an empty field must leave its setting alone");
        assertTrue(changes.isEmpty(), "an unchanged form must report no changes");
    }

    /**
     * Verifies that entering a whole number of minutes for a duration that is not a whole number of minutes saves it,
     * including the number of minutes the duration rounds up to.
     */
    @Test
    public void testSettingsFormSavesWholeMinutesOverDurationThatIsNot()
    {
        // Setup test fixture.
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(Duration.ofSeconds(90));
        defaultSettingsForm();
        params.put("pairInterval", "2");

        // Execute system under test.
        final List<String> changes = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get).apply();

        // Verify result.
        assertEquals(Duration.ofMinutes(2), UserLoginLimitManager.PER_PAIR_INTERVAL.getValue(), "the minutes entered must be saved");
        assertEquals(List.of(UserLoginLimitManager.PER_PAIR_INTERVAL.getKey() + " = 2 minutes"), changes, "the change must be reported");
    }

    /**
     * Verifies that an empty field for a duration that is a whole number of minutes is rejected, as the field showed a
     * value that was then removed.
     */
    @Test
    public void testSettingsFormRejectsEmptyWholeMinuteDuration()
    {
        // Setup test fixture.
        defaultSettingsForm();
        params.put("pairInterval", "");

        // Execute system under test.
        final UserLoginLimitsServlet.SettingsForm form = UserLoginLimitsServlet.SettingsForm.fromRequest(params::get);

        // Verify result.
        assertEquals(Set.of("pairInterval"), form.getErrors().keySet(), "an empty field for a whole-minute duration must be rejected");
    }

    // ========== The table of IP addresses ==========

    /**
     * Verifies that an address blocked for all usernames is shown as such, with its failure count, and that the
     * usernames its block refused are not listed (they are shown on each user's page).
     */
    @Test
    public void testAddressTableShowsAddressBlockForAllUsernames()
    {
        // Setup test fixture.
        final ActiveBlock addressBlock = new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 15, ENDS_AT, ADDRESS);
        final BlocksByAddress blocks = new BlocksByAddress(1, List.of(new BlocksForAddress(ADDRESS, addressBlock,
            List.of(new RefusedUsername("k", "alice", false)), 0, List.of(), ENDS_AT)));

        // Execute system under test.
        final UserLoginLimitsServlet.AddressTable table = UserLoginLimitsServlet.AddressTable.of(blocks, username -> true, NOW);

        // Verify result.
        assertEquals(1, table.getRows().size(), "the address must have a row");
        final UserLoginLimitsServlet.AddressRow row = table.getRows().get(0);
        assertEquals(ADDRESS, row.getAddress(), "the row must show the address");
        assertEquals(ADDRESS, row.getFormValue(), "the Clear form must submit the address as listed");
        assertTrue(row.isAllUsernames(), "an address block must be shown as refusing all usernames");
        assertEquals(15, row.getAddressFailureCount(), "the address block's failed attempts must be shown");
        assertTrue(row.getUsernames().isEmpty(), "the usernames an address block refused must not be listed on this page");
        assertEquals(0, row.getUsernameTotal(), "an address without username blocks must report none");
        assertEquals(JiveGlobals.formatDateTime(Date.from(ENDS_AT)), row.getEnds().getAt(), "the row must show when its last block ends");
        assertEquals("1 hour, 26 minutes from now", row.getEnds().getFromNow(), "the row must say how long until its last block ends");
    }

    /**
     * Verifies that the usernames blocked from an address link to the Lock Out page of their account, URL-encoded,
     * and that a username without an account, or shortened for display, is shown as plain text.
     */
    @Test
    public void testAddressTableLinksOnlyUsernamesWithAccounts()
    {
        // Setup test fixture.
        final List<ActiveBlock> usernames = List.of(
            pairBlock("alice", false),
            pairBlock("a&b c", false),
            pairBlock("nobody", false),
            pairBlock("z".repeat(64), true));
        final BlocksByAddress blocks = new BlocksByAddress(1, List.of(new BlocksForAddress(ADDRESS, null, List.of(), 4, usernames, ENDS_AT)));
        final List<String> looked = new ArrayList<>();

        // Execute system under test.
        final UserLoginLimitsServlet.AddressTable table = UserLoginLimitsServlet.AddressTable.of(blocks, username -> {
            looked.add(username);
            return !"nobody".equals(username);
        }, NOW);

        // Verify result.
        final UserLoginLimitsServlet.AddressRow row = table.getRows().get(0);
        assertFalse(row.isAllUsernames(), "an address without an address block must not be shown as refusing all usernames");
        assertEquals(4, row.getUsernameTotal(), "the row must count the usernames blocked from the address");
        final List<UserLoginLimitsServlet.UsernameEntry> entries = row.getUsernames();
        assertEquals("user-lockout.jsp?username=alice", entries.get(0).getLockoutUrl(), "a username with an account must link to its Lock Out page");
        assertEquals("user-lockout.jsp?username=a%26b+c", entries.get(1).getLockoutUrl(), "the username in the link must be URL-encoded");
        assertNull(entries.get(2).getLockoutUrl(), "a username without an account must not be linked");
        assertNull(entries.get(3).getLockoutUrl(), "a shortened username must not be linked, as it could name another account");
        assertTrue(entries.get(3).isTruncated(), "a shortened username must be marked so");
        assertFalse(looked.contains("z".repeat(64)), "a shortened username must not be looked up");
    }

    /**
     * Verifies that an address with both an address block and username blocks shows both in one row, ending when the
     * last of them ends.
     */
    @Test
    public void testAddressTableShowsBothKindsOfBlockInOneRow()
    {
        // Setup test fixture.
        final Instant later = ENDS_AT.plus(Duration.ofMinutes(5));
        final ActiveBlock addressBlock = new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 40, ENDS_AT, ADDRESS);
        final BlocksByAddress blocks = new BlocksByAddress(1, List.of(new BlocksForAddress(ADDRESS, addressBlock, List.of(), 1, List.of(pairBlock("alice", false)), later)));

        // Execute system under test.
        final UserLoginLimitsServlet.AddressTable table = UserLoginLimitsServlet.AddressTable.of(blocks, username -> true, NOW);

        // Verify result.
        assertEquals(1, table.getRows().size(), "an address with both kinds of block must have one row");
        final UserLoginLimitsServlet.AddressRow row = table.getRows().get(0);
        assertTrue(row.isAllUsernames(), "the row must show the address block");
        assertEquals(List.of("alice"), row.getUsernames().stream().map(UserLoginLimitsServlet.UsernameEntry::getUsername).toList(), "the row must also list the username blocks");
        assertEquals(JiveGlobals.formatDateTime(Date.from(later)), row.getEnds().getAt(), "the row must end when the last of its blocks ends");
    }

    /**
     * Verifies that the table says how many addresses, and each row how many usernames, are not shown.
     */
    @Test
    public void testAddressTableCountsWhatIsNotShown()
    {
        // Setup test fixture.
        final List<ActiveBlock> usernames = new ArrayList<>();
        for (int i = 0; i < UserLoginLimitsServlet.MAX_USERNAMES_PER_ADDRESS; i++) {
            usernames.add(pairBlock("user" + i, false));
        }
        final BlocksByAddress blocks = new BlocksByAddress(250, List.of(new BlocksForAddress(ADDRESS, null, List.of(), 37, usernames, ENDS_AT)));

        // Execute system under test.
        final UserLoginLimitsServlet.AddressTable table = UserLoginLimitsServlet.AddressTable.of(blocks, username -> false, NOW);

        // Verify result.
        assertEquals(250, table.getTotal(), "the table must give how many addresses have blocks");
        assertEquals(249, table.getHidden(), "the table must say how many addresses are not shown");
        assertEquals(37 - UserLoginLimitsServlet.MAX_USERNAMES_PER_ADDRESS, table.getRows().get(0).getHiddenUsernames(), "the row must say how many usernames are not shown");
    }

    /**
     * Verifies that a username blocked from several addresses is looked up once, and that a failed lookup shows the
     * username as plain text rather than breaking the page.
     */
    @Test
    public void testAddressTableLooksUpEachUsernameOnce()
    {
        // Setup test fixture.
        final BlocksByAddress blocks = new BlocksByAddress(2, List.of(
            new BlocksForAddress(ADDRESS, null, List.of(), 1, List.of(pairBlock("alice", false)), ENDS_AT),
            new BlocksForAddress(OTHER_ADDRESS, null, List.of(), 1, List.of(pairBlock("alice", false)), ENDS_AT)));
        final List<String> looked = new ArrayList<>();

        // Execute system under test.
        final UserLoginLimitsServlet.AddressTable table = UserLoginLimitsServlet.AddressTable.of(blocks, username -> {
            looked.add(username);
            throw new IllegalStateException("lookup failed");
        }, NOW);

        // Verify result.
        assertEquals(List.of("alice"), looked, "a username must be looked up once per page");
        assertNull(table.getRows().get(0).getUsernames().get(0).getLockoutUrl(), "a failed lookup must leave the username unlinked");
    }

    /**
     * Verifies that control characters in an address or username are shown escaped.
     */
    @Test
    public void testAddressTableEscapesControlCharacters()
    {
        // Setup test fixture.
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, "eve\nforged", false, "x\ty", 10, ENDS_AT, "k");
        final BlocksByAddress blocks = new BlocksByAddress(1, List.of(new BlocksForAddress("x\ty", null, List.of(), 1, List.of(block), ENDS_AT)));

        // Execute system under test.
        final UserLoginLimitsServlet.AddressTable table = UserLoginLimitsServlet.AddressTable.of(blocks, username -> false, NOW);

        // Verify result.
        final UserLoginLimitsServlet.AddressRow row = table.getRows().get(0);
        assertEquals("x\\u0009y", row.getAddress(), "control characters in an address must be shown escaped");
        assertEquals("eve\\u000aforged", row.getUsernames().get(0).getUsername(), "control characters in a username must be shown escaped");
    }

    // ========== Requests ==========

    /**
     * Verifies that the page is rendered with a CSRF token, the current settings and the bounded table of addresses.
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
        verify(request, times(1).description("the page must be given the table of IP addresses")).setAttribute(eq("addresses"), any(UserLoginLimitsServlet.AddressTable.class));
        verify(request, times(1).description("the longest duration the form accepts must be given to the page, in minutes")).setAttribute("maxDurationMinutes", UserLoginLimitManager.MAX_DURATION.toMinutes());
        verify(manager, times(1).description("the addresses and their usernames must be listed within the page's bounds"))
            .getBlocksByAddress(UserLoginLimitsServlet.MAX_ADDRESSES, UserLoginLimitsServlet.MAX_USERNAMES_PER_ADDRESS);
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
        params.put("address", ADDRESS);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(manager, never().description("a request without a CSRF token must clear nothing")).clearBlocksForAddress(anyString());
        assertTrue(events.isEmpty(), "a rejected request must not log an admin event");
        verify(request, times(1).description("the CSRF failure must be reported")).setAttribute(eq("errors"), eq(Map.of("csrf", "global.csrf.failed")));
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
     * Verifies that clearing an address lifts every block for it through the manager, logs one admin event describing
     * each block lifted, and redirects.
     */
    @Test
    public void testPostClearLiftsEveryBlockForAddressAndLogsEvent() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        final ActiveBlock addressBlock = new ActiveBlock(Tier.ADDRESS, null, false, ADDRESS, 40, ENDS_AT, ADDRESS);
        final ActiveBlock pairBlock = pairBlock("alice", false);
        when(manager.clearBlocksForAddress(ADDRESS)).thenReturn(List.of(addressBlock, pairBlock));
        params.put("action", "clear");
        params.put("address", ADDRESS);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(manager, times(1).description("every block for the address must be cleared")).clearBlocksForAddress(ADDRESS);
        assertEquals(1, events.size(), "one admin event must be logged for the address");
        assertTrue(events.get(0)[0].contains(ADDRESS), "the admin event's summary must name the address");
        assertTrue(events.get(0)[1].contains("any username from IP address " + ADDRESS), "the admin event must describe the address block");
        assertTrue(events.get(0)[1].contains("'alice'"), "the admin event must describe each username block");
        assertTrue(events.get(0)[1].contains("it would have ended at " + JiveGlobals.formatDateTime(Date.from(ENDS_AT)) + "."), "the admin event must give each block's end in the console's date format");
        verify(response, times(1).description("clearing must redirect")).sendRedirect("user-login-limits.jsp?cleared=true");
    }

    /**
     * Verifies that the admin event for a clear escapes control characters in the address and usernames, which come
     * from clients, so that they cannot forge audit lines.
     */
    @Test
    public void testPostClearLogsEscapedEvent() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        final String address = "192.0.2.1\nforged";
        final ActiveBlock block = new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, "eve\nforged", false, address, 10, ENDS_AT, "k");
        when(manager.clearBlocksForAddress(address)).thenReturn(List.of(block));
        params.put("action", "clear");
        params.put("address", address);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertEquals(1, events.size(), "one admin event must be logged");
        assertFalse(events.get(0)[0].contains("\n"), "the summary must not contain a raw line break");
        assertTrue(events.get(0)[0].contains("192.0.2.1\\u000aforged"), "the address must be escaped in the summary");
        assertFalse(events.get(0)[1].contains("\n"), "the details of a single block must not contain a raw line break");
        assertTrue(events.get(0)[1].contains("'eve\\u000aforged'"), "the username must be escaped in the details");
    }

    /**
     * Verifies that clearing an address that no longer has blocks in force logs nothing and says so.
     */
    @Test
    public void testPostClearOfAddressWithoutBlocksLogsNothing() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        when(manager.clearBlocksForAddress(ADDRESS)).thenReturn(List.of());
        params.put("action", "clear");
        params.put("address", ADDRESS);

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        assertTrue(events.isEmpty(), "nothing cleared must mean no admin event");
        verify(response, times(1).description("the page must say there was nothing to clear")).sendRedirect("user-login-limits.jsp?gone=true");
    }

    /**
     * Verifies that a clear request that names no address is reported as an error.
     */
    @Test
    public void testPostClearWithoutAddressIsReported() throws Exception
    {
        // Setup test fixture.
        validCsrf();
        params.put("action", "clear");
        params.put("address", " ");

        // Execute system under test.
        servlet.doPost(request, response);

        // Verify result.
        verify(manager, never().description("a request naming no address must clear nothing")).clearBlocksForAddress(anyString());
        verify(request, times(1).description("the invalid request must be reported")).setAttribute(eq("errors"), eq(Map.of("address", "user.login.limits.error.address")));
    }

    private static ActiveBlock pairBlock(final String username, final boolean truncated)
    {
        return new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, username, truncated, ADDRESS, 10, ENDS_AT, ADDRESS + '\u0000' + username);
    }
}
