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
import org.jivesoftware.openfire.auth.UserLoginLimitManager.Tier;
import org.jivesoftware.openfire.lockout.LockOutManager;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The admin console page for the login limits of XMPP user accounts: it sets the limits and lists the blocks in force,
 * each of which can be cleared.
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

    private static final String PARAM_CSRF = "csrf";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_BLOCK = "block";
    private static final String ACTION_SAVE = "save";
    private static final String ACTION_CLEAR = "clear";

    private static final String ERROR_CSRF = "global.csrf.failed";
    private static final String ERROR_BLOCK = "user.login.limits.error.block";
    private static final String ERROR_LIMIT = "user.login.limits.error.limit";
    private static final String ERROR_DURATION = "user.login.limits.error.duration";
    private static final String ERROR_ENABLED = "user.login.limits.error.enabled";

    /**
     * The most blocks the page lists for each tier. During an attack there can be many thousands; each listed tier 3
     * block costs a lockout lookup, and each row a form.
     */
    static final int MAX_ROWS_PER_TIER = 100;

    /**
     * Separates the tier from the encoded key in a block token. Neither part can contain it.
     */
    private static final char TOKEN_SEPARATOR = '.';

    private static final long MAX_DURATION_MINUTES = UserLoginLimitManager.MAX_DURATION.toMinutes();

    private final Supplier<UserLoginLimitManager> manager;
    private final Predicate<String> accountLocked;

    public UserLoginLimitsServlet()
    {
        this(UserLoginLimitManager::getInstance, username -> LockOutManager.getInstance().isAccountDisabled(username));
    }

    /**
     * Constructs a servlet with its collaborators supplied. Exposed for test use only.
     */
    @VisibleForTesting
    UserLoginLimitsServlet(@Nonnull final Supplier<UserLoginLimitManager> manager, @Nonnull final Predicate<String> accountLocked)
    {
        this.manager = manager;
        this.accountLocked = accountLocked;
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
            final Optional<BlockReference> reference = parseBlockToken(request.getParameter(PARAM_BLOCK));
            if (reference.isEmpty()) {
                render(request, response, SettingsForm.fromCurrentSettings(), Map.of(PARAM_BLOCK, ERROR_BLOCK));
                return;
            }
            final Optional<ActiveBlock> cleared = manager.get().clearActiveBlock(reference.get().tier(), reference.get().key());
            if (cleared.isEmpty()) {
                response.sendRedirect(PAGE + "?gone=true");
                return;
            }
            logEvent(request, response, "Cleared a user login limit block", describe(cleared.get()));
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
        // One table per kind of block, each with only the columns that apply to it.
        final List<TierSection> sections = toSections(manager.get().getActiveBlocks(MAX_ROWS_PER_TIER), accountLocked);
        request.setAttribute("pairBlocks", section(sections, Tier.USERNAME_FROM_ADDRESS));
        request.setAttribute("addressBlocks", section(sections, Tier.ADDRESS));
        request.setAttribute("usernameBlocks", section(sections, Tier.USERNAME));
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
            case USERNAME -> "Block for username '" + escape(block.username()) + "' from any IP address (an account lock, if any, is left in place)";
        };
        return what + ", after " + block.failureCount() + " attempts; it would have ended at " + block.endsAt() + ".";
    }

    @Nonnull
    private static String escape(@Nullable final String value)
    {
        return value == null ? "" : UserLoginLimitManager.forLog(value);
    }

    /**
     * Returns the value that identifies a block in the page's clear form: its tier and its key, the key encoded so
     * that it can be placed in a form (a tier 1 key contains a NUL character, which HTML forms cannot carry).
     *
     * @param block the block.
     * @return the token.
     */
    @Nonnull
    static String blockToken(@Nonnull final ActiveBlock block)
    {
        final String key = block.key();
        return block.tier().name() + TOKEN_SEPARATOR + Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads a token made by {@link #blockToken(ActiveBlock)}.
     *
     * @param token the token, as submitted.
     * @return the tier and key it names, or empty if it is not a valid token.
     */
    @Nonnull
    static Optional<BlockReference> parseBlockToken(@Nullable final String token)
    {
        if (token == null) {
            return Optional.empty();
        }
        final int separator = token.indexOf(TOKEN_SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        try {
            final Tier tier = Tier.valueOf(token.substring(0, separator));
            final String key = new String(Base64.getUrlDecoder().decode(token.substring(separator + 1)), StandardCharsets.UTF_8);
            return key.isEmpty() ? Optional.empty() : Optional.of(new BlockReference(tier, key));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Builds the sections of the blocks table, one per tier, from the bounded listing.
     *
     * @param tiers the blocks in force, by tier.
     * @param accountLocked tells whether an account is locked.
     * @return the sections, in tier order.
     */
    @Nonnull
    static List<TierSection> toSections(@Nonnull final List<UserLoginLimitManager.TierBlocks> tiers, @Nonnull final Predicate<String> accountLocked)
    {
        final List<TierSection> sections = new ArrayList<>(tiers.size());
        for (final UserLoginLimitManager.TierBlocks tier : tiers) {
            sections.add(new TierSection(tier.tier(), tier.total(), toRows(tier.blocks(), accountLocked)));
        }
        return sections;
    }

    /**
     * Returns the section for a tier, or an empty one if there is none.
     */
    @Nonnull
    static TierSection section(@Nonnull final List<TierSection> sections, @Nonnull final Tier tier)
    {
        return sections.stream().filter(section -> section.tier == tier).findFirst()
            .orElseGet(() -> new TierSection(tier, 0, List.of()));
    }

    /**
     * Builds the rows of the blocks table. A tier 3 row is marked when its account is locked through LockOutManager,
     * so that the page can say that clearing the row does not lift the lock. A shortened username is not looked up,
     * as the lookup could find another account.
     *
     * @param blocks the blocks to show.
     * @param accountLocked tells whether an account is locked.
     * @return the rows, in the order of the blocks.
     */
    @Nonnull
    static List<BlockRow> toRows(@Nonnull final List<ActiveBlock> blocks, @Nonnull final Predicate<String> accountLocked)
    {
        final List<BlockRow> rows = new ArrayList<>(blocks.size());
        for (final ActiveBlock block : blocks) {
            final String username = block.username();
            boolean locked = false;
            if (block.tier() == Tier.USERNAME && username != null && !block.usernameTruncated()) {
                try {
                    locked = accountLocked.test(username);
                } catch (final RuntimeException e) {
                    Log.debug("Unable to check whether account '{}' is locked", escape(username), e);
                }
            }
            rows.add(new BlockRow(block, blockToken(block), locked));
        }
        return rows;
    }

    /**
     * The blocks of one tier in the blocks table, with JavaBean getters for the page.
     */
    public static final class TierSection
    {
        private final Tier tier;
        private final int total;
        private final List<BlockRow> rows;

        TierSection(@Nonnull final Tier tier, final int total, @Nonnull final List<BlockRow> rows)
        {
            this.tier = tier;
            this.total = total;
            this.rows = rows;
        }

        /**
         * @return how many blocks of this tier are in force.
         */
        public int getTotal() { return total; }

        /**
         * @return the rows shown, the latest to end first.
         */
        @Nonnull
        public List<BlockRow> getRows() { return rows; }

        /**
         * @return whether fewer blocks are shown than are in force.
         */
        public boolean isTruncated() { return rows.size() < total; }
    }

    /**
     * The tier and key of a block, as named by a token from the page.
     *
     * @param tier the tier of the block.
     * @param key the key of the block.
     */
    record BlockReference(@Nonnull Tier tier, @Nonnull String key) {}

    /**
     * A row of the blocks table, with JavaBean getters for the page.
     */
    public static final class BlockRow
    {
        private final ActiveBlock block;
        private final String token;
        private final boolean accountLocked;

        BlockRow(@Nonnull final ActiveBlock block, @Nonnull final String token, final boolean accountLocked)
        {
            this.block = block;
            this.token = token;
            this.accountLocked = accountLocked;
        }

        /**
         * @return the username with control characters escaped, or null for a tier 2 block.
         */
        @Nullable
        public String getUsername() { return block.username() == null ? null : escape(block.username()); }

        /**
         * @return the address with control characters escaped, or null for a tier 3 block.
         */
        @Nullable
        public String getAddress() { return block.address() == null ? null : escape(block.address()); }

        public long getFailureCount() { return block.failureCount(); }

        /**
         * @return when the block ends, formatted for display.
         */
        @Nonnull
        public String getEndsAt() { return JiveGlobals.formatDateTime(Date.from(block.endsAt())); }

        /**
         * @return the value that identifies the block in the clear form.
         */
        @Nonnull
        public String getToken() { return token; }

        /**
         * @return whether the listed username was shortened for display.
         */
        public boolean isUsernameTruncated() { return block.usernameTruncated(); }

        /**
         * @return whether this is a tier 3 block for an account that is also locked through LockOutManager.
         */
        public boolean isAccountLocked() { return accountLocked; }

        /**
         * @return the username to link to the account's lockout page with: the username as listed, unescaped.
         */
        @Nullable
        public String getLockoutUsername() { return accountLocked ? block.username() : null; }
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
            form.values.put(FIELD_PAIR_INTERVAL, String.valueOf(minutes(UserLoginLimitManager.PER_PAIR_INTERVAL.getValue())));
            form.values.put(FIELD_PAIR_BLOCK_DURATION, String.valueOf(minutes(UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.getValue())));
            form.values.put(FIELD_ADDRESS_LIMIT, String.valueOf(UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getValue()));
            form.values.put(FIELD_ADDRESS_INTERVAL, String.valueOf(minutes(UserLoginLimitManager.PER_ADDRESS_INTERVAL.getValue())));
            form.values.put(FIELD_ADDRESS_BLOCK_DURATION, String.valueOf(minutes(UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.getValue())));
            form.values.put(FIELD_USERNAME_LIMIT, String.valueOf(UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue()));
            form.values.put(FIELD_USERNAME_INTERVAL, String.valueOf(minutes(UserLoginLimitManager.PER_USERNAME_INTERVAL.getValue())));
            form.values.put(FIELD_LOCKOUT_DURATION, String.valueOf(minutes(UserLoginLimitManager.LOCKOUT_DURATION.getValue())));
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
         * Saves the settings that differ from the current ones. A duration whose field still shows the current value
         * (rounded up to whole minutes) is left as it is, so that a value set elsewhere in finer units is not changed
         * by saving the form. Nothing is saved if the form has errors.
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
            final long value = Long.parseLong(values.get(field));
            if (value != minutes(property.getValue())) {
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
         * Returns a duration in whole minutes, rounded up, so that a positive duration never shows as 0.
         */
        private static long minutes(@Nonnull final Duration duration)
        {
            final long minutes = duration.toMinutes();
            return duration.equals(Duration.ofMinutes(minutes)) ? minutes : minutes + 1;
        }

        /**
         * Returns, for each duration field whose setting is not a whole number of minutes (set elsewhere, for example
         * on the System Properties page), that setting written out in full, as the field shows it rounded up to whole
         * minutes.
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
