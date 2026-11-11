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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ActiveBlock;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.BlocksForUsername;
import org.jivesoftware.openfire.auth.UserLoginLimitManager.ClearedBlocks;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.WebManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The admin console page that answers whether a user can log in, and why not, and locks and unlocks the user's account.
 * One sentence at the top gives the answer. Below it, two boxes give the two reasons a user can be refused: Account
 * lock, with the lock's status and a button to lock or unlock the account, and Login limits, which lists the login
 * limits' blocks for the user, says when a block of an IP address refused the user, and has the Clear login limits
 * button. Each end time is shown with how long that is from now.
 *
 * One button, Clear login limits, gives the user a fresh start with the login limits: it clears the user's blocks
 * and resets the failed attempts counted for the user, from each IP address and from any, and lifts the account's
 * lock when the login limits set it, never a lock set by hand. Unlock account does the same for a lock the login
 * limits set, and lifts only the lock for one set by hand. A block of an IP address for all usernames affects
 * everyone at that address, so it is not cleared here; the page says it still refuses the user and links to the
 * Login Limits page for it.
 *
 * @author Matthew Vivian
 * @see UserLoginLimitManager
 */
@WebServlet(value = "/user-lockout.jsp")
public class UserLockoutServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger(UserLockoutServlet.class);

    private static final String PAGE = "user-lockout.jsp";
    private static final String VIEW = "user-lockout-page.jsp";
    private static final String USER_PAGE = "user-properties.jsp";
    private static final String USER_LIST_PAGE = "user-summary.jsp";
    private static final String LOGIN_PAGE = "login.jsp";

    /**
     * The session attribute holding the signed-in administrator's token, as set by the admin console's sign-in.
     */
    private static final String AUTH_TOKEN_ATTRIBUTE = "jive.admin.authToken";

    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_CSRF = "csrf";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_LOCK = "lock";
    private static final String PARAM_UNLOCK = "unlock";
    private static final String PARAM_START_DELAY = "startdelay";
    private static final String PARAM_START_DELAY_CUSTOM = "startdelay_custom";
    private static final String PARAM_DURATION = "duration";
    private static final String PARAM_DURATION_CUSTOM = "duration_custom";
    private static final String ACTION_CLEAR = "clear";

    /**
     * The lock form's value for a lock that starts at once, or that lasts until it is lifted.
     */
    private static final long IMMEDIATE_OR_FOREVER = -1;

    /**
     * The lock form's value for a number of minutes entered in the form's text field.
     */
    private static final long CUSTOM_MINUTES = -2;

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private static final String ERROR_CSRF = "global.csrf.failed";

    /**
     * The most blocks from one IP address, and the most IP address blocks that refused the user, that the page lists.
     * A user under attack from many addresses can have many.
     */
    static final int MAX_ROWS = 20;

    private final Supplier<UserLoginLimitManager> limits;
    private final Supplier<LockOutManager> lockOutManager;
    private final Supplier<LockOutProvider> lockOutProvider;
    private final Predicate<String> isAdministrator;
    private final Consumer<String> closeSessions;
    private final BooleanSupplier userEventsBlocked;
    private final Clock clock;

    public UserLockoutServlet()
    {
        this(UserLoginLimitManager::getInstance, LockOutManager::getInstance, LockOutManager::getLockOutProvider,
            username -> AdminManager.getInstance().isUserAdmin(username, true), UserLockoutServlet::closeUserSessions,
            () -> SecurityAuditManager.getSecurityAuditProvider().blockUserEvents(), Clock.systemUTC());
    }

    /**
     * Constructs a servlet with its collaborators supplied. Exposed for test use only.
     */
    @VisibleForTesting
    UserLockoutServlet(@Nonnull final Supplier<UserLoginLimitManager> limits, @Nonnull final Supplier<LockOutManager> lockOutManager,
                       @Nonnull final Supplier<LockOutProvider> lockOutProvider, @Nonnull final Predicate<String> isAdministrator,
                       @Nonnull final Consumer<String> closeSessions, @Nonnull final BooleanSupplier userEventsBlocked,
                       @Nonnull final Clock clock)
    {
        this.limits = limits;
        this.lockOutManager = lockOutManager;
        this.lockOutProvider = lockOutProvider;
        this.isAdministrator = isAdministrator;
        this.closeSessions = closeSessions;
        this.userEventsBlocked = userEventsBlocked;
        this.clock = clock;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final String username = ParamUtils.getParameter(request, PARAM_USERNAME);
        if (username == null) {
            response.sendRedirect(USER_LIST_PAGE);
            return;
        }
        render(request, response, username, Map.of());
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final String username = ParamUtils.getParameter(request, PARAM_USERNAME);
        if (username == null) {
            response.sendRedirect(USER_LIST_PAGE);
            return;
        }
        final Cookie csrfCookie = CookieUtils.getCookie(request, PARAM_CSRF);
        final String csrfParam = request.getParameter(PARAM_CSRF);
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            render(request, response, username, Map.of(PARAM_CSRF, ERROR_CSRF));
            return;
        }

        if (ACTION_CLEAR.equals(request.getParameter(PARAM_ACTION))) {
            clearLoginLimits(request, response, username);
        } else if (request.getParameter(PARAM_LOCK) != null || request.getParameter(PARAM_UNLOCK) != null) {
            // A read-only lockout provider cannot lock or unlock; the page says so above the disabled form.
            if (lockOutProvider.get().isReadOnly()) {
                render(request, response, username, Map.of());
            } else if (request.getParameter(PARAM_LOCK) != null) {
                lock(request, response, username);
            } else {
                unlock(request, response, username);
            }
        } else {
            response.sendRedirect(pageFor(username));
        }
    }

    /**
     * Locks the account, from the start and for the duration chosen in the form, and closes the user's sessions if the
     * lock applies at once. An administrator who locks their own account is signed out.
     */
    private void lock(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String username) throws IOException
    {
        long startDelay = ParamUtils.getLongParameter(request, PARAM_START_DELAY, IMMEDIATE_OR_FOREVER);
        long duration = ParamUtils.getLongParameter(request, PARAM_DURATION, IMMEDIATE_OR_FOREVER);
        if (startDelay == CUSTOM_MINUTES) {
            startDelay = ParamUtils.getLongParameter(request, PARAM_START_DELAY_CUSTOM, IMMEDIATE_OR_FOREVER);
        }
        if (duration == CUSTOM_MINUTES) {
            duration = ParamUtils.getLongParameter(request, PARAM_DURATION_CUSTOM, IMMEDIATE_OR_FOREVER);
        }
        final long now = System.currentTimeMillis();
        final Date startTime = startDelay == IMMEDIATE_OR_FOREVER ? null : new Date(now + startDelay * MILLIS_PER_MINUTE);
        final Date endTime = duration == IMMEDIATE_OR_FOREVER ? null
            : new Date((startTime == null ? now : startTime.getTime()) + duration * MILLIS_PER_MINUTE);

        lockOutManager.get().disableAccount(username, startTime, endTime);
        if (!userEventsBlocked.getAsBoolean()) {
            logEvent(request, response, "locked out user " + username, "start time = " + format(startTime) + ", end time = " + format(endTime));
        }
        if (lockOutManager.get().isAccountDisabled(username)) {
            closeSessions.accept(username);
            if (username.equals(signedInUsername(request))) {
                // An administrator who locked their own account must sign in again (and cannot, while it is locked).
                request.getSession().removeAttribute(AUTH_TOKEN_ATTRIBUTE);
                response.sendRedirect(LOGIN_PAGE);
                return;
            }
        }
        response.sendRedirect(USER_PAGE + "?username=" + urlEncode(username) + "&locksuccess=1");
    }

    /**
     * Unlocks the account through the login limits (see {@link UserLoginLimitManager#unlockAccount(String)}). A lock
     * the login limits set is lifted as Clear login limits lifts it, with the user's blocks and failure counts from
     * each IP address and from any; the admin event lists what was cleared, and the user's page says the login limits
     * were cleared too. A lock set by hand is lifted alone, as before.
     */
    private void unlock(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String username) throws IOException
    {
        final Optional<ClearedBlocks> limitsCleared = limits.get().unlockAccount(username);
        if (limitsCleared.isEmpty()) {
            if (!userEventsBlocked.getAsBoolean()) {
                logEvent(request, response, "unlocked user " + username, null);
            }
            response.sendRedirect(USER_PAGE + "?username=" + urlEncode(username) + "&unlocksuccess=1");
            return;
        }
        final ClearedBlocks cleared = limitsCleared.get();
        if (cleared.lockLiftFailed()) {
            logCleared(request, response, "Cleared the login limits for a user", username, cleared);
            response.sendRedirect(pageFor(username) + "&unlockfailed=true");
            return;
        }
        // Clearing the login limits is always logged, so this unlock is too.
        logEvent(request, response, "unlocked user " + username, details(username, cleared));
        response.sendRedirect(USER_PAGE + "?username=" + urlEncode(username) + "&unlocksuccess=1&limitscleared=1");
    }

    /**
     * Clears the login limits for the user: the user's blocks and failure counts, from each IP address and from any,
     * with the lock the login limits set (never a lock set by hand). Blocks of an IP address for all usernames are not
     * cleared here. Logs what was cleared as an admin event.
     */
    private void clearLoginLimits(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String username) throws IOException
    {
        final ClearedBlocks cleared = limits.get().clearBlocksForUsername(username);
        logCleared(request, response, "Cleared the login limits for a user", username, cleared);
        if (cleared.lockLiftFailed()) {
            response.sendRedirect(pageFor(username) + "&lockfailed=true");
        } else if (cleared.blocks().isEmpty() && !cleared.countsReset()) {
            response.sendRedirect(pageFor(username) + "&gone=true");
        } else {
            response.sendRedirect(pageFor(username) + "&cleared=true" + (cleared.lockLifted() ? "&unlocked=true" : ""));
        }
    }

    /**
     * Logs an admin event for login limits that were cleared, if any block was lifted or any count reset.
     */
    private void logCleared(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String summary,
                            @Nonnull final String username, @Nonnull final ClearedBlocks cleared)
    {
        if (!cleared.blocks().isEmpty() || cleared.countsReset()) {
            logEvent(request, response, summary, details(username, cleared));
        }
    }

    /**
     * Describes cleared login limits for an admin event: one line naming the user, and one per lifted block.
     */
    @Nonnull
    private static String details(@Nonnull final String username, @Nonnull final ClearedBlocks cleared)
    {
        final List<String> details = new ArrayList<>();
        details.add("Cleared the login limits' blocks and failure counts for username '" + escape(username) + "'.");
        for (final ActiveBlock block : cleared.blocks()) {
            details.add(describe(block, cleared.lockLifted()));
        }
        return String.join("\n", details);
    }

    /**
     * Records an admin event: a security audit entry under the name of the administrator making the request.
     */
    protected void logEvent(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String summary, @Nullable final String details)
    {
        final WebManager webManager = new WebManager();
        webManager.init(request, response, request.getSession(), request.getServletContext());
        webManager.logEvent(summary, details);
    }

    private void render(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final String username, @Nonnull final Map<String, String> errors) throws ServletException, IOException
    {
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, PARAM_CSRF, csrf, -1);
        request.setAttribute("csrf", csrf);
        request.setAttribute("errors", errors);
        request.setAttribute("username", username);
        request.setAttribute("displayName", JID.unescapeNode(username));
        request.setAttribute("usernameUrlEncoded", urlEncode(username));
        request.setAttribute("ownAccount", username.equals(signedInUsername(request)));

        final LockOutProvider provider = lockOutProvider.get();
        request.setAttribute("readOnly", provider.isReadOnly());
        request.setAttribute("delayedStartSupported", provider.isDelayedStartSupported());
        request.setAttribute("timeoutSupported", provider.isTimeoutSupported());

        final Instant now = clock.instant();
        final UserLoginLimitManager manager = limits.get();
        final LockOutFlag flag = lockOutManager.get().getDisabledStatus(username);
        final Time lockStart = flag == null || flag.getStartTime() == null ? null : Time.of(flag.getStartTime().toInstant(), now);
        final Time lockEnd = flag == null || flag.getEndTime() == null ? null : Time.of(flag.getEndTime().toInstant(), now);
        final boolean lockInForce = flag != null && (flag.getStartTime() == null || !flag.getStartTime().toInstant().isAfter(now));
        final boolean lockSetByLoginLimits = flag != null && manager.isLockSetByLoginLimits(flag);
        request.setAttribute("locked", flag != null);
        request.setAttribute("lockStart", lockStart);
        request.setAttribute("lockEnd", lockEnd);
        request.setAttribute("lockInForce", lockInForce);
        request.setAttribute("lockSetByLoginLimits", lockSetByLoginLimits);

        final LoginLimitsSection section = LoginLimitsSection.of(manager.getBlocksForUsername(username, MAX_ROWS), now);
        request.setAttribute("loginLimits", section);
        request.setAttribute("administrator", section.getFromAnyAddress() != null && isAdministratorAccount(username));
        request.setAttribute("answer", Answer.of(flag != null, lockInForce, lockSetByLoginLimits, lockStart, lockEnd, section));
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    private boolean isAdministratorAccount(@Nonnull final String username)
    {
        try {
            return isAdministrator.test(username);
        } catch (final RuntimeException e) {
            Log.debug("Unable to check whether account '{}' is an administrator's", UserLoginLimitManager.forLog(username), e);
            return false;
        }
    }

    /**
     * Describes a cleared block for the admin event. The address comes from a client and is escaped.
     */
    @Nonnull
    static String describe(@Nonnull final ActiveBlock block, final boolean lockLifted)
    {
        final String username = escape(block.username());
        final String what = switch (block.tier()) {
            case USERNAME_FROM_ADDRESS -> "Block for username '" + username + "' from IP address " + escape(block.address());
            case ADDRESS -> "Block for any username from IP address " + escape(block.address());
            case USERNAME -> "Block for username '" + username + "' from any IP address" + (lockLifted
                ? ", with the account's lock, which the login limits set"
                : " (an account lock set by hand, if any, is left in place)");
        };
        return what + ", after " + block.failureCount() + " attempts; it would have ended at " + JiveGlobals.formatDateTime(Date.from(block.endsAt())) + ".";
    }

    /**
     * Formats a date as the rest of the admin console does, in Openfire's locale and time zone.
     *
     * @return the formatted date, or null for none.
     */
    @Nullable
    private static String format(@Nullable final Date date)
    {
        return date == null ? null : JiveGlobals.formatDateTime(date);
    }

    @Nonnull
    private static String escape(@Nullable final String value)
    {
        return value == null ? "" : UserLoginLimitManager.forLog(value);
    }

    @Nullable
    private static String signedInUsername(@Nonnull final HttpServletRequest request)
    {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        final Object token = session.getAttribute(AUTH_TOKEN_ATTRIBUTE);
        return token instanceof AuthToken authToken ? authToken.getUsername() : null;
    }

    @Nonnull
    private static String pageFor(@Nonnull final String username)
    {
        return PAGE + "?username=" + urlEncode(username);
    }

    @Nonnull
    private static String urlEncode(@Nonnull final String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Closes every session of a user on this server, telling the client it is not authorised.
     */
    private static void closeUserSessions(@Nonnull final String username)
    {
        final XMPPServer server = XMPPServer.getInstance();
        final StreamError error = new StreamError(StreamError.Condition.not_authorized);
        for (final ClientSession session : server.getSessionManager().getSessions(server.createJID(username, null))) {
            session.close(error);
        }
    }

    /**
     * A point in time for the page: formatted as the rest of the admin console formats dates, with how long that is
     * from now when it is still to come. JavaBean getters for the page.
     */
    public static final class Time
    {
        private final Instant instant;
        private final String at;
        private final String fromNow;

        private Time(@Nonnull final Instant instant, @Nonnull final String at, @Nullable final String fromNow)
        {
            this.instant = instant;
            this.at = at;
            this.fromNow = fromNow;
        }

        /**
         * @param instant the point in time.
         * @param now the current time.
         * @return the time, with how long it is from now if it is after now.
         */
        @Nonnull
        static Time of(@Nonnull final Instant instant, @Nonnull final Instant now)
        {
            final long delta = instant.toEpochMilli() - now.toEpochMilli();
            final String fromNow = delta > 0
                ? LocaleUtils.getLocalizedString("user.lockout.from_now", List.of(StringUtils.getElapsedTime(delta)))
                : null;
            return new Time(instant, JiveGlobals.formatDateTime(Date.from(instant)), fromNow);
        }

        @Nonnull
        Instant instant() { return instant; }

        /**
         * @return the time, formatted for display.
         */
        @Nonnull
        public String getAt() { return at; }

        /**
         * @return how long the time is from now (for example "1 hour, 26 minutes from now"), or null if it has passed.
         */
        @Nullable
        public String getFromNow() { return fromNow; }
    }

    /**
     * The answer at the top of the page: whether the user can log in, and if not, why and until when. JavaBean getters
     * for the page, which picks the sentence for the kind of answer.
     */
    public static final class Answer
    {
        /**
         * The kinds of answer, in the order they are checked: an account lock in force decides the answer, then a block
         * from any IP address, then blocks from some IP addresses.
         */
        public enum Kind
        {
            /** The login limits locked the account; it cannot log in until the lock ends. */
            LOCKED_BY_LOGIN_LIMITS,
            /** The account is locked until a set time. */
            LOCKED_UNTIL,
            /** The account is locked until it is unlocked. */
            LOCKED,
            /** The login limits refuse the user from any IP address, without an account lock in force. */
            REFUSED_FROM_ANY_ADDRESS,
            /** The login limits refuse the user from some IP addresses. */
            REFUSED_FROM_ADDRESSES,
            /** Nothing refuses the user now, but the account's lock starts later. */
            CAN_LOG_IN_UNTIL_LOCK,
            /** Nothing refuses the user, and failed attempts are counted for the user. */
            CAN_LOG_IN_COUNTED,
            /** Nothing refuses the user, and nothing is counted. */
            CAN_LOG_IN
        }

        /**
         * The most IP addresses the answer names; beyond this it gives their number.
         */
        static final int MAX_NAMED_ADDRESSES = 2;

        private final Kind kind;
        private final Time until;
        private final long failures;
        private final List<RefusedAddress> addresses;
        private final int addressCount;
        private final boolean accountLocked;
        private final Time lockStart;

        private Answer(@Nonnull final Kind kind, @Nullable final Time until, final long failures, @Nonnull final List<RefusedAddress> addresses,
                       final int addressCount, final boolean accountLocked, @Nullable final Time lockStart)
        {
            this.kind = kind;
            this.until = until;
            this.failures = failures;
            this.addresses = addresses;
            this.addressCount = addressCount;
            this.accountLocked = accountLocked;
            this.lockStart = lockStart;
        }

        @Nonnull
        static Answer of(final boolean locked, final boolean lockInForce, final boolean lockSetByLoginLimits, @Nullable final Time lockStart,
                         @Nullable final Time lockEnd, @Nonnull final LoginLimitsSection section)
        {
            final BlockRow fromAny = section.getFromAnyAddress();
            if (lockInForce) {
                if (lockSetByLoginLimits && lockEnd != null) {
                    return new Answer(Kind.LOCKED_BY_LOGIN_LIMITS, lockEnd, fromAny == null ? 0 : fromAny.getFailureCount(), List.of(), 0, true, lockStart);
                }
                return new Answer(lockEnd == null ? Kind.LOCKED : Kind.LOCKED_UNTIL, lockEnd, 0, List.of(), 0, true, lockStart);
            }
            if (fromAny != null) {
                return new Answer(Kind.REFUSED_FROM_ANY_ADDRESS, fromAny.getEnds(), fromAny.getFailureCount(), List.of(), 0, locked, lockStart);
            }
            final List<BlockRow> rows = new ArrayList<>(section.getFromAddresses());
            rows.addAll(section.getRefusingAddresses());
            if (!rows.isEmpty()) {
                // The rows are the latest to end first, and those not shown end sooner, so the last to end is shown.
                final Time until = rows.stream().map(BlockRow::getEnds).max(Comparator.comparing(Time::instant)).orElseThrow();
                // An address can refuse the user through both kinds of block; it is named once, ending with the later.
                final Map<String, Time> ends = new LinkedHashMap<>();
                for (final BlockRow row : rows) {
                    if (row.getAddress() != null) {
                        ends.merge(row.getAddress(), row.getEnds(), (a, b) -> a.instant().isBefore(b.instant()) ? b : a);
                    }
                }
                final List<RefusedAddress> addresses = ends.entrySet().stream().map(e -> new RefusedAddress(e.getKey(), e.getValue())).toList();
                final int count = addresses.size() + section.getHiddenFromAddresses() + section.getHiddenRefusingAddresses();
                return new Answer(Kind.REFUSED_FROM_ADDRESSES, until, 0, addresses, count, locked, lockStart);
            }
            if (locked) {
                return new Answer(Kind.CAN_LOG_IN_UNTIL_LOCK, lockStart, 0, List.of(), 0, true, lockStart);
            }
            return new Answer(section.isCounted() ? Kind.CAN_LOG_IN_COUNTED : Kind.CAN_LOG_IN, null, 0, List.of(), 0, false, null);
        }

        /**
         * @return the name of the kind of answer (see {@link Kind}).
         */
        @Nonnull
        public String getKind() { return kind.name(); }

        /**
         * @return whether the user cannot log in now, from every IP address or from some.
         */
        public boolean isRefused()
        {
            return kind != Kind.CAN_LOG_IN && kind != Kind.CAN_LOG_IN_COUNTED && kind != Kind.CAN_LOG_IN_UNTIL_LOCK;
        }

        /**
         * @return when the user can log in again (or, for {@link Kind#CAN_LOG_IN_UNTIL_LOCK}, when the lock starts), or
         * null if there is no such time.
         */
        @Nullable
        public Time getUntil() { return until; }

        /**
         * @return the failed attempts after which the login limits locked the account or blocked the user from any IP
         * address.
         */
        public long getFailures() { return failures; }

        /**
         * @return the IP addresses from which the user is refused, each once with when its last block ends, in the
         * order the page lists them.
         */
        @Nonnull
        public List<RefusedAddress> getAddresses() { return addresses; }

        /**
         * @return how many IP addresses the user is refused from, including those the page does not list.
         */
        public int getAddressCount() { return addressCount; }

        /**
         * @return whether the account has a lock, in force or starting later.
         */
        public boolean isAccountLocked() { return accountLocked; }

        /**
         * @return when the account's lock starts, or null if it has no start.
         */
        @Nullable
        public Time getLockStart() { return lockStart; }
    }

    /**
     * An IP address the answer names, with when the user can log in from it again. JavaBean getters for the page.
     *
     * @param address the address with control characters escaped.
     * @param until when the last block that refuses the user from the address ends.
     */
    public record RefusedAddress(@Nonnull String address, @Nonnull Time until)
    {
        @Nonnull
        public String getAddress() { return address; }

        @Nonnull
        public Time getUntil() { return until; }
    }

    /**
     * One of the user's blocks, or an IP address block that refused the user, with JavaBean getters for the page.
     */
    public static final class BlockRow
    {
        private final ActiveBlock block;
        private final Time ends;

        BlockRow(@Nonnull final ActiveBlock block, @Nonnull final Instant now)
        {
            this.block = block;
            this.ends = Time.of(block.endsAt(), now);
        }

        /**
         * @return the address with control characters escaped, or null for the block from any IP address.
         */
        @Nullable
        public String getAddress() { return block.address() == null ? null : escape(block.address()); }

        public long getFailureCount() { return block.failureCount(); }

        /**
         * @return when the block ends.
         */
        @Nonnull
        public Time getEnds() { return ends; }
    }

    /**
     * The page's Login limits section: the user's blocks and whether failures are counted for the user, which Clear
     * login limits clears, and the IP address blocks that refused the user, which it does not. JavaBean getters for
     * the page.
     */
    public static final class LoginLimitsSection
    {
        private final BlockRow fromAnyAddress;
        private final List<BlockRow> fromAddresses;
        private final int hiddenFromAddresses;
        private final List<BlockRow> refusingAddresses;
        private final int hiddenRefusingAddresses;
        private final boolean counted;

        private LoginLimitsSection(@Nullable final BlockRow fromAnyAddress, @Nonnull final List<BlockRow> fromAddresses,
                                   final int hiddenFromAddresses, @Nonnull final List<BlockRow> refusingAddresses, final int hiddenRefusingAddresses,
                                   final boolean counted)
        {
            this.counted = counted;
            this.fromAnyAddress = fromAnyAddress;
            this.fromAddresses = fromAddresses;
            this.hiddenFromAddresses = hiddenFromAddresses;
            this.refusingAddresses = refusingAddresses;
            this.hiddenRefusingAddresses = hiddenRefusingAddresses;
        }

        /**
         * Builds the section from a user's blocks, as returned by the bounded listing.
         *
         * @param blocks the user's blocks.
         * @param now the current time, from which the page says how long until each block ends.
         * @return the section.
         */
        @Nonnull
        static LoginLimitsSection of(@Nonnull final BlocksForUsername blocks, @Nonnull final Instant now)
        {
            final List<BlockRow> fromAddresses = rows(blocks.fromAddresses(), now);
            final List<BlockRow> refusing = rows(blocks.refusingAddresses(), now);
            return new LoginLimitsSection(blocks.fromAnyAddress() == null ? null : new BlockRow(blocks.fromAnyAddress(), now),
                fromAddresses, Math.max(0, blocks.fromAddressTotal() - fromAddresses.size()),
                refusing, Math.max(0, blocks.refusingAddressTotal() - refusing.size()), blocks.counted());
        }

        @Nonnull
        private static List<BlockRow> rows(@Nonnull final List<ActiveBlock> blocks, @Nonnull final Instant now)
        {
            return blocks.stream().map(block -> new BlockRow(block, now)).toList();
        }

        /**
         * @return the user's block from any IP address, or null.
         */
        @Nullable
        public BlockRow getFromAnyAddress() { return fromAnyAddress; }

        /**
         * @return the user's blocks from one IP address that are shown, the latest to end first.
         */
        @Nonnull
        public List<BlockRow> getFromAddresses() { return fromAddresses; }

        /**
         * @return how many of the user's blocks from one IP address are not shown.
         */
        public int getHiddenFromAddresses() { return hiddenFromAddresses; }

        /**
         * @return the blocks of IP addresses for all usernames that refused the user and are shown, the latest to end
         * first. Clear login limits does not clear them.
         */
        @Nonnull
        public List<BlockRow> getRefusingAddresses() { return refusingAddresses; }

        /**
         * @return how many blocks of IP addresses that refused the user are not shown.
         */
        public int getHiddenRefusingAddresses() { return hiddenRefusingAddresses; }

        /**
         * @return whether the section lists any of the user's blocks.
         */
        public boolean isBlocked() { return fromAnyAddress != null || !fromAddresses.isEmpty(); }

        /**
         * @return whether failed attempts are counted for the user, from one IP address or from any.
         */
        public boolean isCounted() { return counted; }

        /**
         * @return whether Clear login limits has something to clear: a block of the user, or failures counted for the
         * user. The button is shown only then.
         */
        public boolean isClearable() { return isBlocked() || counted; }

        /**
         * @return whether an IP address block for all usernames refused the user; Clear login limits leaves it in force.
         */
        public boolean isStillRefused() { return !refusingAddresses.isEmpty(); }

        /**
         * @return whether the section has anything to show: something to clear, or an IP address block that refused
         * the user.
         */
        public boolean isShown() { return isClearable() || isStillRefused(); }
    }
}
