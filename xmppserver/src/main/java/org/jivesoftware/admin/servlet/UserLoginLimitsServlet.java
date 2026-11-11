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

import com.google.common.annotations.VisibleForTesting;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ActiveBlock;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.BlocksByAddress;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.BlocksForAddress;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.WebManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The admin console page for the login limits of XMPP user accounts: it sets the limits and lists the IP addresses
 * from which logins are refused, each of which can be cleared of all its blocks.
 *
 * A username's block from any IP address is not listed here: it is shown, and cleared, on the user's Lock Out page
 * ({@link UserLockoutServlet}). Blocks for usernames that name no account are shown on no page; nobody can log in with
 * those names, and the blocks end by themselves.
 *
 * @author Matthew Vivian
 * @see UserLoginLimitManager
 */
@WebServlet(value = "/user-login-limits.jsp")
public class UserLoginLimitsServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger(UserLoginLimitsServlet.class);

    private static final String PAGE = "user-login-limits.jsp";
    private static final String VIEW = "user-login-limits-page.jsp";
    private static final String LOCKOUT_PAGE = "user-lockout.jsp";

    private static final String PARAM_CSRF = "csrf";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_ADDRESS = "address";
    private static final String ACTION_SAVE = "save";
    private static final String ACTION_CLEAR = "clear";

    private static final String ERROR_CSRF = "global.csrf.failed";
    private static final String ERROR_ADDRESS = "user.login.limits.error.address";
    private static final String ERROR_LIMIT = "user.login.limits.error.limit";
    private static final String ERROR_DURATION = "user.login.limits.error.duration";
    private static final String ERROR_ENABLED = "user.login.limits.error.enabled";

    /**
     * The most IP addresses the page lists. During an attack there can be many thousands; each row costs a form and
     * an account lookup for each username it shows.
     */
    static final int MAX_ADDRESSES = 100;

    /**
     * The most usernames the page shows for each IP address; the row says how many more there are.
     */
    static final int MAX_USERNAMES_PER_ADDRESS = 6;

    private static final long MAX_DURATION_MINUTES = UserLoginLimitManager.MAX_DURATION.toMinutes();

    private final Supplier<UserLoginLimitManager> manager;
    private final Predicate<String> accountExists;
    private final Clock clock;

    public UserLoginLimitsServlet()
    {
        this(UserLoginLimitManager::getInstance, UserLoginLimitsServlet::isExistingAccount, Clock.systemUTC());
    }

    /**
     * Constructs a servlet with its collaborators supplied. Exposed for test use only.
     */
    @VisibleForTesting
    UserLoginLimitsServlet(@Nonnull final Supplier<UserLoginLimitManager> manager, @Nonnull final Predicate<String> accountExists,
                           @Nonnull final Clock clock)
    {
        this.manager = manager;
        this.accountExists = accountExists;
        this.clock = clock;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        render(request, response, SettingsForm.fromCurrentSettings(), Map.of());
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final Cookie csrfCookie = CookieUtils.getCookie(request, PARAM_CSRF);
        final String csrfParam = request.getParameter(PARAM_CSRF);
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            render(request, response, SettingsForm.fromCurrentSettings(), Map.of(PARAM_CSRF, ERROR_CSRF));
            return;
        }

        final String action = request.getParameter(PARAM_ACTION);
        if (ACTION_SAVE.equals(action)) {
            final SettingsForm form = SettingsForm.fromRequest(request::getParameter);
            if (!form.getErrors().isEmpty()) {
                render(request, response, form, form.getErrors());
                return;
            }
            final List<String> changes = form.apply();
            if (!changes.isEmpty()) {
                logEvent(request, response, "Updated user login limits", String.join("\n", changes));
            }
            response.sendRedirect(PAGE + "?saved=true");
        } else if (ACTION_CLEAR.equals(action)) {
            final String address = request.getParameter(PARAM_ADDRESS);
            if (address == null || address.isBlank()) {
                render(request, response, SettingsForm.fromCurrentSettings(), Map.of(PARAM_ADDRESS, ERROR_ADDRESS));
                return;
            }
            final List<ActiveBlock> cleared = manager.get().clearBlocksForAddress(address);
            if (cleared.isEmpty()) {
                response.sendRedirect(PAGE + "?gone=true");
                return;
            }
            final List<String> descriptions = new ArrayList<>(cleared.size());
            for (final ActiveBlock block : cleared) {
                descriptions.add(describe(block));
            }
            logEvent(request, response, "Cleared the user login limit blocks for IP address " + escape(address), String.join("\n", descriptions));
            response.sendRedirect(PAGE + "?cleared=true");
        } else {
            response.sendRedirect(PAGE);
        }
    }

    /**
     * Records an admin event: a security audit entry under the name of the administrator making the request.
     */
    protected void logEvent(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String summary, @Nonnull final String details)
    {
        final WebManager webManager = new WebManager();
        webManager.init(request, response, request.getSession(), request.getServletContext());
        webManager.logEvent(summary, details);
    }

    private void render(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final SettingsForm settings, @Nonnull final Map<String, String> errors) throws ServletException, IOException
    {
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, PARAM_CSRF, csrf, -1);
        request.setAttribute("csrf", csrf);
        request.setAttribute("settings", settings);
        request.setAttribute("errors", errors);
        request.setAttribute("limitsEnabled", UserLoginLimitManager.ENABLED.getValue());
        request.setAttribute("addresses", AddressTable.of(manager.get().getBlocksByAddress(MAX_ADDRESSES, MAX_USERNAMES_PER_ADDRESS), accountExists, clock.instant()));
        request.setAttribute("maxDurationMinutes", MAX_DURATION_MINUTES);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /**
     * Describes a cleared block for the admin event. The username and address come from clients and are escaped.
     */
    @Nonnull
    private static String describe(@Nonnull final ActiveBlock block)
    {
        final String what = switch (block.tier()) {
            case USERNAME_FROM_ADDRESS -> "Block for username '" + escape(block.username()) + "' from IP address " + escape(block.address());
            case ADDRESS -> "Block for any username from IP address " + escape(block.address());
            case USERNAME -> "Block for username '" + escape(block.username()) + "' from any IP address";
        };
        return what + ", after " + block.failureCount() + " attempts; it would have ended at " + JiveGlobals.formatDateTime(Date.from(block.endsAt())) + ".";
    }

    @Nonnull
    private static String escape(@Nullable final String value)
    {
        return value == null ? "" : UserLoginLimitManager.forLog(value);
    }

    /**
     * Returns whether a username names an account of this server. A lookup that fails is taken as no account, so the
     * username is shown without a link.
     */
    private static boolean isExistingAccount(@Nonnull final String username)
    {
        try {
            UserManager.getInstance().getUser(username);
            return true;
        } catch (final UserNotFoundException e) {
            return false;
        }
    }

    /**
     * The table of IP addresses that have blocks in force, with JavaBean getters for the page.
     */
    public static final class AddressTable
    {
        private final int total;
        private final List<AddressRow> rows;

        private AddressTable(final int total, @Nonnull final List<AddressRow> rows)
        {
            this.total = total;
            this.rows = rows;
        }

        /**
         * Builds the table from the bounded listing of blocks by IP address. Each username shown is looked up at most
         * once, to link it to its account's Lock Out page; a username shortened for display is not looked up, as the
         * lookup could find another account.
         *
         * @param blocks the blocks, grouped by IP address.
         * @param accountExists tells whether a username names an account.
         * @param now the current time, from which the page says how long until each row's blocks end.
         * @return the table.
         */
        @Nonnull
        static AddressTable of(@Nonnull final BlocksByAddress blocks, @Nonnull final Predicate<String> accountExists, @Nonnull final Instant now)
        {
            final Map<String, Boolean> accounts = new HashMap<>();
            final Predicate<String> lookup = username -> accounts.computeIfAbsent(username, name -> {
                try {
                    return accountExists.test(name);
                } catch (final RuntimeException e) {
                    Log.debug("Unable to check whether username '{}' names an account", escape(name), e);
                    return false;
                }
            });
            final List<AddressRow> rows = new ArrayList<>(blocks.addresses().size());
            for (final BlocksForAddress address : blocks.addresses()) {
                rows.add(AddressRow.of(address, lookup, now));
            }
            return new AddressTable(blocks.total(), List.copyOf(rows));
        }

        /**
         * @return how many IP addresses have blocks in force.
         */
        public int getTotal() { return total; }

        /**
         * @return the rows shown, the latest to end first.
         */
        @Nonnull
        public List<AddressRow> getRows() { return rows; }

        /**
         * @return how many IP addresses with blocks are not shown.
         */
        public int getHidden() { return Math.max(0, total - rows.size()); }
    }

    /**
     * One IP address in the table: whether it is blocked for all usernames, and which usernames are blocked from it.
     * An address can have both, as a block of one username can outlast the block for all usernames; the row then
     * shows both. JavaBean getters for the page.
     */
    public static final class AddressRow
    {
        private final BlocksForAddress blocks;
        private final List<UsernameEntry> usernames;
        private final UserLockoutServlet.Time ends;

        private AddressRow(@Nonnull final BlocksForAddress blocks, @Nonnull final List<UsernameEntry> usernames, @Nonnull final UserLockoutServlet.Time ends)
        {
            this.blocks = blocks;
            this.usernames = usernames;
            this.ends = ends;
        }

        @Nonnull
        static AddressRow of(@Nonnull final BlocksForAddress blocks, @Nonnull final Predicate<String> accountExists, @Nonnull final Instant now)
        {
            final List<UsernameEntry> usernames = new ArrayList<>(blocks.usernameBlocks().size());
            for (final ActiveBlock block : blocks.usernameBlocks()) {
                final String username = block.username();
                if (username == null) {
                    continue;
                }
                final boolean linked = !block.usernameTruncated() && accountExists.test(username);
                usernames.add(new UsernameEntry(escape(username), block.usernameTruncated(),
                    linked ? LOCKOUT_PAGE + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) : null));
            }
            return new AddressRow(blocks, List.copyOf(usernames), UserLockoutServlet.Time.of(blocks.latestEnd(), now));
        }

        /**
         * @return the address with control characters escaped (an IPv6 address as its /64).
         */
        @Nonnull
        public String getAddress() { return escape(blocks.address()); }

        /**
         * @return the address as listed, for the Clear form, which lifts every block for it.
         */
        @Nonnull
        public String getFormValue() { return blocks.address(); }

        /**
         * @return whether the address is blocked for all usernames.
         */
        public boolean isAllUsernames() { return blocks.addressBlock() != null; }

        /**
         * @return the failed attempts that led to the block for all usernames, or 0 without one.
         */
        public long getAddressFailureCount() { return blocks.addressBlock() == null ? 0 : blocks.addressBlock().failureCount(); }

        /**
         * @return how many usernames are blocked from the address.
         */
        public int getUsernameTotal() { return blocks.usernameTotal(); }

        /**
         * @return the usernames shown, the latest block to end first.
         */
        @Nonnull
        public List<UsernameEntry> getUsernames() { return usernames; }

        /**
         * @return how many usernames blocked from the address are not shown.
         */
        public int getHiddenUsernames() { return Math.max(0, blocks.usernameTotal() - usernames.size()); }

        /**
         * @return when the last of the address's blocks ends, with how long that is from now.
         */
        @Nonnull
        public UserLockoutServlet.Time getEnds() { return ends; }
    }

    /**
     * A username blocked from an IP address, with JavaBean getters for the page.
     */
    public static final class UsernameEntry
    {
        private final String username;
        private final boolean truncated;
        private final String lockoutUrl;

        private UsernameEntry(@Nonnull final String username, final boolean truncated, @Nullable final String lockoutUrl)
        {
            this.username = username;
            this.truncated = truncated;
            this.lockoutUrl = lockoutUrl;
        }

        /**
         * @return the username with control characters escaped. It comes from a client, so the page escapes it too.
         */
        @Nonnull
        public String getUsername() { return username; }

        /**
         * @return whether the username was shortened for display.
         */
        public boolean isTruncated() { return truncated; }

        /**
         * @return the URL of the Lock Out page of the username's account, with the username URL-encoded, or null when
         * the username names no account or was shortened for display.
         */
        @Nullable
        public String getLockoutUrl() { return lockoutUrl; }
    }

    /**
     * The settings form: the values to show, and when submitted, their validation and application. Limits are whole
     * numbers of attempts, 0 or more; durations are whole numbers of minutes, from 1 to the 1 day that the properties
     * allow.
     */
    public static final class SettingsForm
    {
        private static final String FIELD_ENABLED = "enabled";
        private static final String FIELD_PAIR_LIMIT = "pairLimit";
        private static final String FIELD_PAIR_INTERVAL = "pairInterval";
        private static final String FIELD_PAIR_BLOCK_DURATION = "pairBlockDuration";
        private static final String FIELD_ADDRESS_LIMIT = "addressLimit";
        private static final String FIELD_ADDRESS_INTERVAL = "addressInterval";
        private static final String FIELD_ADDRESS_BLOCK_DURATION = "addressBlockDuration";
        private static final String FIELD_USERNAME_LIMIT = "usernameLimit";
        private static final String FIELD_USERNAME_INTERVAL = "usernameInterval";
        private static final String FIELD_LOCKOUT_DURATION = "lockoutDuration";

        private final boolean enabled;
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, String> errors = new LinkedHashMap<>();

        private SettingsForm(final boolean enabled)
        {
            this.enabled = enabled;
        }

        /**
         * @return a form showing the current settings.
         */
        @Nonnull
        public static SettingsForm fromCurrentSettings()
        {
            final SettingsForm form = new SettingsForm(UserLoginLimitManager.ENABLED.getValue());
            form.values.put(FIELD_PAIR_LIMIT, String.valueOf(UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue()));
            form.values.put(FIELD_ADDRESS_LIMIT, String.valueOf(UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getValue()));
            form.values.put(FIELD_USERNAME_LIMIT, String.valueOf(UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue()));
            durationProperties().forEach((field, property) -> {
                final Long minutes = wholeMinutes(property.getValue());
                form.values.put(field, minutes == null ? "" : String.valueOf(minutes));
            });
            return form;
        }

        /**
         * Reads and validates a submitted form. The submitted values are kept for display, so that a form with errors
         * can be shown again as it was entered.
         *
         * @param parameters looks up a request parameter by name.
         * @return the form, with any errors keyed by field name.
         */
        @Nonnull
        public static SettingsForm fromRequest(@Nonnull final Function<String, String> parameters)
        {
            // A missing choice is an error rather than "off": a request that left the field out would otherwise turn
            // the limits off.
            final String enabled = parameters.apply(FIELD_ENABLED);
            final boolean validEnabled = "true".equals(enabled) || "false".equals(enabled);
            final SettingsForm form = new SettingsForm("true".equals(enabled));
            if (!validEnabled) {
                form.errors.put(FIELD_ENABLED, ERROR_ENABLED);
            }
            for (final String field : List.of(FIELD_PAIR_LIMIT, FIELD_PAIR_INTERVAL, FIELD_PAIR_BLOCK_DURATION, FIELD_ADDRESS_LIMIT,
                FIELD_ADDRESS_INTERVAL, FIELD_ADDRESS_BLOCK_DURATION, FIELD_USERNAME_LIMIT, FIELD_USERNAME_INTERVAL, FIELD_LOCKOUT_DURATION)) {
                final String value = parameters.apply(field);
                form.values.put(field, value == null ? "" : value.trim());
            }
            form.validateLimit(FIELD_PAIR_LIMIT);
            form.validateDuration(FIELD_PAIR_INTERVAL);
            form.validateDuration(FIELD_PAIR_BLOCK_DURATION);
            form.validateLimit(FIELD_ADDRESS_LIMIT);
            form.validateDuration(FIELD_ADDRESS_INTERVAL);
            form.validateDuration(FIELD_ADDRESS_BLOCK_DURATION);
            form.validateLimit(FIELD_USERNAME_LIMIT);
            form.validateDuration(FIELD_USERNAME_INTERVAL);
            form.validateDuration(FIELD_LOCKOUT_DURATION);
            return form;
        }

        /**
         * Saves the settings that differ from the current ones. A duration field left empty, which the form allows
         * only for a duration that is not a whole number of minutes, leaves that duration as it is, so that a value
         * set elsewhere in finer units is not changed by saving the form. Nothing is saved if the form has errors.
         *
         * @return a description of each setting changed, for the admin event; empty if nothing changed.
         */
        @Nonnull
        public List<String> apply()
        {
            final List<String> changes = new ArrayList<>();
            if (!errors.isEmpty()) {
                return changes;
            }
            if (enabled != UserLoginLimitManager.ENABLED.getValue()) {
                UserLoginLimitManager.ENABLED.setValue(enabled);
                changes.add(UserLoginLimitManager.ENABLED.getKey() + " = " + enabled);
            }
            applyLimit(UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR, FIELD_PAIR_LIMIT, changes);
            applyDuration(UserLoginLimitManager.PER_PAIR_INTERVAL, FIELD_PAIR_INTERVAL, changes);
            applyDuration(UserLoginLimitManager.PER_PAIR_BLOCK_DURATION, FIELD_PAIR_BLOCK_DURATION, changes);
            applyLimit(UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS, FIELD_ADDRESS_LIMIT, changes);
            applyDuration(UserLoginLimitManager.PER_ADDRESS_INTERVAL, FIELD_ADDRESS_INTERVAL, changes);
            applyDuration(UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION, FIELD_ADDRESS_BLOCK_DURATION, changes);
            applyLimit(UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME, FIELD_USERNAME_LIMIT, changes);
            applyDuration(UserLoginLimitManager.PER_USERNAME_INTERVAL, FIELD_USERNAME_INTERVAL, changes);
            applyDuration(UserLoginLimitManager.LOCKOUT_DURATION, FIELD_LOCKOUT_DURATION, changes);
            return changes;
        }

        private void applyLimit(@Nonnull final SystemProperty<Long> property, @Nonnull final String field, @Nonnull final List<String> changes)
        {
            final long value = Long.parseLong(values.get(field));
            if (value != property.getValue()) {
                property.setValue(value);
                changes.add(property.getKey() + " = " + value);
            }
        }

        private void applyDuration(@Nonnull final SystemProperty<Duration> property, @Nonnull final String field, @Nonnull final List<String> changes)
        {
            final String entered = values.get(field);
            if (entered.isEmpty()) {
                return;
            }
            final long value = Long.parseLong(entered);
            if (!Duration.ofMinutes(value).equals(property.getValue())) {
                property.setValue(Duration.ofMinutes(value));
                changes.add(property.getKey() + " = " + value + " minutes");
            }
        }

        private void validateLimit(@Nonnull final String field)
        {
            final Long value = parseLong(values.get(field));
            if (value == null || value < 0) {
                errors.put(field, ERROR_LIMIT);
            }
        }

        private void validateDuration(@Nonnull final String field)
        {
            if (values.get(field).isEmpty() && wholeMinutes(durationProperties().get(field).getValue()) == null) {
                return;
            }
            final Long value = parseLong(values.get(field));
            if (value == null || value < 1 || value > MAX_DURATION_MINUTES) {
                errors.put(field, ERROR_DURATION);
            }
        }

        @Nullable
        private static Long parseLong(@Nonnull final String value)
        {
            try {
                return Long.parseLong(value);
            } catch (final NumberFormatException e) {
                return null;
            }
        }

        /**
         * Returns a duration in minutes, or null if it is not a whole number of minutes.
         */
        @Nullable
        private static Long wholeMinutes(@Nonnull final Duration duration)
        {
            final long minutes = duration.toMinutes();
            return duration.equals(Duration.ofMinutes(minutes)) ? minutes : null;
        }

        /**
         * Returns, for each duration field whose setting is not a whole number of minutes (set elsewhere, for example
         * on the System Properties page), that setting written out in full, as the field is then empty.
         *
         * @return the settings written out in full, keyed by field name; empty when every duration is whole minutes.
         */
        @Nonnull
        public Map<String, String> getExactDurations()
        {
            final Map<String, String> exact = new LinkedHashMap<>();
            durationProperties().forEach((field, property) -> {
                final Duration value = property.getValue();
                if (!value.equals(Duration.ofMinutes(value.toMinutes()))) {
                    exact.put(field, StringUtils.getFullElapsedTime(value));
                }
            });
            return exact;
        }

        @Nonnull
        private static Map<String, SystemProperty<Duration>> durationProperties()
        {
            final Map<String, SystemProperty<Duration>> properties = new LinkedHashMap<>();
            properties.put(FIELD_PAIR_INTERVAL, UserLoginLimitManager.PER_PAIR_INTERVAL);
            properties.put(FIELD_PAIR_BLOCK_DURATION, UserLoginLimitManager.PER_PAIR_BLOCK_DURATION);
            properties.put(FIELD_ADDRESS_INTERVAL, UserLoginLimitManager.PER_ADDRESS_INTERVAL);
            properties.put(FIELD_ADDRESS_BLOCK_DURATION, UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION);
            properties.put(FIELD_USERNAME_INTERVAL, UserLoginLimitManager.PER_USERNAME_INTERVAL);
            properties.put(FIELD_LOCKOUT_DURATION, UserLoginLimitManager.LOCKOUT_DURATION);
            return properties;
        }

        /**
         * @return the errors, keyed by field name, each value the key of a message to show.
         */
        @Nonnull
        public Map<String, String> getErrors() { return errors; }

        public boolean isEnabled() { return enabled; }

        @Nonnull
        public String getPairLimit() { return values.get(FIELD_PAIR_LIMIT); }

        @Nonnull
        public String getPairInterval() { return values.get(FIELD_PAIR_INTERVAL); }

        @Nonnull
        public String getPairBlockDuration() { return values.get(FIELD_PAIR_BLOCK_DURATION); }

        @Nonnull
        public String getAddressLimit() { return values.get(FIELD_ADDRESS_LIMIT); }

        @Nonnull
        public String getAddressInterval() { return values.get(FIELD_ADDRESS_INTERVAL); }

        @Nonnull
        public String getAddressBlockDuration() { return values.get(FIELD_ADDRESS_BLOCK_DURATION); }

        @Nonnull
        public String getUsernameLimit() { return values.get(FIELD_USERNAME_LIMIT); }

        @Nonnull
        public String getUsernameInterval() { return values.get(FIELD_USERNAME_INTERVAL); }

        @Nonnull
        public String getLockoutDuration() { return values.get(FIELD_LOCKOUT_DURATION); }
    }
}
