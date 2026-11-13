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

package org.jivesoftware.openfire.auth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.lockout.LockOutEventDispatcher;
import org.jivesoftware.openfire.lockout.LockOutEventListener;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Limits password attempts for XMPP user logins that do not succeed, as a defence against password guessing.
 *
 * Attempts are counted in three tiers, each over a fixed window that starts at the first attempt for its key:
 *
 * Tier 1 counts attempts for one username from one address. Reaching its limit refuses that username from that
 * address for the tier's block duration, from the attempt that reached it, whether or not the window has ended by then.
 *
 * Tier 2 counts attempts from one address, for any username. Reaching its limit refuses that address for the tier's
 * block duration in the same way.
 *
 * When a tier 1 or tier 2 block ends, counting for its key starts again from zero.
 *
 * Tier 3 counts attempts for one username, from any address. The attempt after its limit locks the account through
 * {@link LockOutManager} for a set duration, and the username is refused for that duration whether or not the lock
 * could be set (the account does not exist, is already locked, or the lockout provider cannot set a lock that ends),
 * so the answer does not reveal which case applies. An administrator's account is never locked, so that guessing at
 * its username cannot keep the administrator out of the admin console: its username is refused in the same way, for
 * XMPP and JMX logins only.
 *
 * A limit of N admits N attempts in a window; the attempt after those is refused. A limit of 0 turns its tier off.
 * An attempt is counted when it is admitted ({@link #tryAcquireAttempt(String, String)}) and given back when it
 * succeeds ({@link #recordSuccessfulLogin(Reservation)}), so checking and counting are one step and concurrent
 * attempts cannot together exceed a limit. The counts are kept in clustered caches, so all cluster nodes share them.
 * The feature is on by default with only tier 1 active, as a tier 1 block refuses no one but the pair that reached it;
 * tiers 2 and 3 can affect other users (everyone behind a shared address, or a user whose username others guess at),
 * so they are off (a limit of 0) until an administrator turns them on. While the feature is off, nothing is counted
 * and only account locks refuse an attempt.
 *
 * @author Matthew Vivian
 */
public class UserLoginLimitManager implements LockOutEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(UserLoginLimitManager.class);

    public static final String PAIR_FAILURES_CACHE_NAME = "User Login Failures per Username and IP Address";
    public static final String ADDRESS_FAILURES_CACHE_NAME = "User Login Failures per IP Address";
    public static final String USERNAME_FAILURES_CACHE_NAME = "User Login Failures per Username";
    public static final String USERNAME_BLOCKS_CACHE_NAME = "User Login Blocks per Username";

    private static final String AUDIT_SUMMARY_LIMIT_REACHED = "User login limit reached";
    private static final String AUDIT_SUMMARY_ACCOUNT_LOCKED = "User account locked after failed logins";

    /**
     * Separates the address from the username's digest (see {@link #usernameKey(String)}) in a tier 1 key. A NUL
     * character cannot occur in either.
     */
    private static final char PAIR_KEY_SEPARATOR = '\u0000';

    /**
     * The longest username, in bytes of UTF-8, that can name an account: the maximum length of a JID localpart
     * (RFC 7622). Longer usernames are counted for their address only and never used as a cache key.
     */
    static final int MAX_USERNAME_BYTES = 1023;

    /**
     * The width of the username column of the security audit log; longer audit subjects are truncated to it.
     */
    static final int MAX_AUDIT_SUBJECT_LENGTH = 64;

    /**
     * The longest any window or lock may last. Caches are registered with this lifetime, so that entries are cleaned
     * up eventually; correctness never depends on it, as windows and blocks end by their own timestamps.
     */
    public static final Duration MAX_DURATION = Duration.ofDays(1);

    /**
     * {@link #MAX_DURATION} in milliseconds, as a compile-time constant: CacheFactory registers the caches with this
     * lifetime, and reading a constant does not initialise this class while the caches are being set up.
     */
    public static final long MAX_DURATION_MILLIS = 24L * 60 * 60 * 1000;

    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.loginlimit.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    public static final SystemProperty<Long> MAX_ATTEMPTS_PER_PAIR = SystemProperty.Builder.ofType(Long.class)
        .setKey("xmpp.auth.loginlimit.maxAttemptsPerUsernameAddressPair")
        .setDynamic(true)
        .setDefaultValue(10L)
        .setMinValue(0L)
        .build();

    public static final SystemProperty<Duration> PER_PAIR_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.auth.loginlimit.perUsernameAddressPairInterval")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(15))
        .setMinValue(Duration.ofMillis(1))
        .setMaxValue(MAX_DURATION)
        .build();

    public static final SystemProperty<Duration> PER_PAIR_BLOCK_DURATION = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.auth.loginlimit.perUsernameAddressPairBlockDuration")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(15))
        .setMinValue(Duration.ofSeconds(1))
        .setMaxValue(MAX_DURATION)
        .build();

    public static final SystemProperty<Long> MAX_ATTEMPTS_PER_ADDRESS = SystemProperty.Builder.ofType(Long.class)
        .setKey("xmpp.auth.loginlimit.maxAttemptsPerAddress")
        .setDynamic(true)
        .setDefaultValue(0L)
        .setMinValue(0L)
        .build();

    public static final SystemProperty<Duration> PER_ADDRESS_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.auth.loginlimit.perAddressInterval")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(15))
        .setMinValue(Duration.ofMillis(1))
        .setMaxValue(MAX_DURATION)
        .build();

    public static final SystemProperty<Duration> PER_ADDRESS_BLOCK_DURATION = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.auth.loginlimit.perAddressBlockDuration")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(15))
        .setMinValue(Duration.ofSeconds(1))
        .setMaxValue(MAX_DURATION)
        .build();

    public static final SystemProperty<Long> MAX_ATTEMPTS_PER_USERNAME = SystemProperty.Builder.ofType(Long.class)
        .setKey("xmpp.auth.loginlimit.maxAttemptsPerUsername")
        .setDynamic(true)
        .setDefaultValue(0L)
        .setMinValue(0L)
        .build();

    public static final SystemProperty<Duration> PER_USERNAME_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.auth.loginlimit.perUsernameInterval")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofHours(1))
        .setMinValue(Duration.ofMillis(1))
        .setMaxValue(MAX_DURATION)
        .build();

    public static final SystemProperty<Duration> LOCKOUT_DURATION = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.auth.loginlimit.lockoutDuration")
        .setDynamic(true)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(30))
        .setMinValue(Duration.ofMillis(1))
        .setMaxValue(MAX_DURATION)
        .build();

    private static class UserLoginLimitManagerContainer {
        private static final UserLoginLimitManager instance = new UserLoginLimitManager();
    }

    /**
     * Returns the singleton instance, creating it (and registering it for account unlock events) on first use.
     *
     * @return the UserLoginLimitManager instance.
     */
    public static UserLoginLimitManager getInstance() {
        return UserLoginLimitManagerContainer.instance;
    }

    private final Cache<String, FailureWindow> pairFailures;
    private final Cache<String, FailureWindow> addressFailures;
    private final Cache<String, FailureWindow> usernameFailures;

    /**
     * Tier 3 blocks that stand in for a LockOutManager lock, keyed by normalised username.
     */
    private final Cache<String, UsernameBlock> usernameBlocks;

    private final Supplier<LockOutManager> lockOutManager;
    private final Supplier<LockOutProvider> lockOutProvider;
    private final Predicate<String> userExists;
    private final Predicate<String> isAdministrator;
    private final Supplier<SecurityAuditManager> securityAuditManager;
    private final Supplier<String> xmppDomain;
    private final Clock clock;

    private UserLoginLimitManager() {
        this(CacheFactory.createCache(PAIR_FAILURES_CACHE_NAME),
            CacheFactory.createCache(ADDRESS_FAILURES_CACHE_NAME),
            CacheFactory.createCache(USERNAME_FAILURES_CACHE_NAME),
            CacheFactory.createCache(USERNAME_BLOCKS_CACHE_NAME),
            LockOutManager::getInstance,
            LockOutManager::getLockOutProvider,
            UserLoginLimitManager::isExistingUser,
            UserLoginLimitManager::isAdministratorAccount,
            SecurityAuditManager::getInstance,
            UserLoginLimitManager::serverXmppDomain,
            Clock.systemUTC());
        LockOutEventDispatcher.addListener(this);
    }

    /**
     * Constructs a manager with its collaborators supplied. Exposed for test use only.
     */
    @VisibleForTesting
    UserLoginLimitManager(@Nonnull final Cache<String, FailureWindow> pairFailures,
                          @Nonnull final Cache<String, FailureWindow> addressFailures,
                          @Nonnull final Cache<String, FailureWindow> usernameFailures,
                          @Nonnull final Cache<String, UsernameBlock> usernameBlocks,
                          @Nonnull final Supplier<LockOutManager> lockOutManager,
                          @Nonnull final Supplier<LockOutProvider> lockOutProvider,
                          @Nonnull final Predicate<String> userExists,
                          @Nonnull final Predicate<String> isAdministrator,
                          @Nonnull final Supplier<SecurityAuditManager> securityAuditManager,
                          @Nonnull final Supplier<String> xmppDomain,
                          @Nonnull final Clock clock)
    {
        this.pairFailures = pairFailures;
        this.addressFailures = addressFailures;
        this.usernameFailures = usernameFailures;
        this.usernameBlocks = usernameBlocks;
        this.lockOutManager = lockOutManager;
        this.lockOutProvider = lockOutProvider;
        this.userExists = userExists;
        this.isAdministrator = isAdministrator;
        this.securityAuditManager = securityAuditManager;
        this.xmppDomain = xmppDomain;
        this.clock = clock;
    }

    /**
     * Reserves a password attempt for a username from an address, or refuses it. Called as soon as the username is
     * known, before the credentials are evaluated, by callers that can give the attempt back when it succeeds (see
     * {@link #isAttemptAllowed(String, String)} for those that cannot). In one step, under the locks of the keys
     * involved, it refuses the attempt if any tier is at its limit and otherwise counts it in every tier, so concurrent
     * attempts cannot together exceed a limit.
     *
     * The attempt is refused when:
     *
     * the account is locked through {@link LockOutManager} (checked whether or not the limits are enabled; such a
     * refusal is reported to LockOutManager's listeners, as a refused login for a locked account always has been);
     *
     * tier 1 or tier 2 has blocked the key, or is at its limit for the window (when the limit was lowered after the
     * window's last attempt; this attempt then starts the block);
     *
     * the username is blocked by tier 3; or
     *
     * tier 3 is at its limit for the window, in which case this attempt locks the account (or blocks the username)
     * for the lockout duration. The lock is set on the attempt after the Nth rather than during the Nth, so that the
     * lock check after a successful exchange cannot reveal whether the Nth attempt's password was right.
     *
     * An admitted attempt stays counted unless {@link #recordSuccessfulLogin(Reservation)} is called with the returned
     * reservation, so an attempt that ends without its credentials being evaluated (the client aborts, or a protocol
     * error) counts as a failure. A refused attempt is not counted, so a block does not extend itself.
     *
     * If the account lock cannot be checked (for example because the username is not a valid node), that alone does
     * not refuse the attempt: the lock is checked again after a successful exchange, so a locked account still cannot
     * log in, and refusing here would turn a lookup problem into a login outage.
     *
     * @param username the username the attempt is for (as provided by the client).
     * @param address the address the attempt comes from, or null if it is not known (then only tier 3 applies).
     * @return the reservation of the admitted attempt, to pass to {@link #recordSuccessfulLogin(Reservation)} if it
     *         succeeds; or null if the attempt must be refused.
     */
    @Nullable
    public Reservation tryAcquireAttempt(@Nonnull final String username, @Nullable final String address)
    {
        final String user = normalise(username);
        final boolean accountName = isAccountName(user);
        final Boolean locked = accountName ? checkLocked(user) : null;
        if (Boolean.TRUE.equals(locked)) {
            return null;
        }
        // Recorded so that the lock check after a successful exchange need not look the account up again.
        final String notLockedUser = Boolean.FALSE.equals(locked) ? user : null;
        final String addressKey = address == null ? null : addressKey(address);
        final String userKey = accountName ? usernameKey(user) : null;
        if (!ENABLED.getValue()) {
            // Nothing was counted, so a success has nothing to give back or clear.
            return new Reservation(null, addressKey, NOT_COUNTED, userKey, NOT_COUNTED, notLockedUser);
        }
        final String pairKey = userKey != null && addressKey != null ? pairKey(userKey, addressKey) : null;

        final long pairLimit = MAX_ATTEMPTS_PER_PAIR.getValue();
        final long addressLimit = MAX_ATTEMPTS_PER_ADDRESS.getValue();
        final long usernameLimit = MAX_ATTEMPTS_PER_USERNAME.getValue();
        final Duration pairInterval = PER_PAIR_INTERVAL.getValue();
        final Duration addressInterval = PER_ADDRESS_INTERVAL.getValue();
        final Duration usernameInterval = PER_USERNAME_INTERVAL.getValue();
        final Duration pairBlockDuration = PER_PAIR_BLOCK_DURATION.getValue();
        final Duration addressBlockDuration = PER_ADDRESS_BLOCK_DURATION.getValue();
        final Duration lockoutDuration = LOCKOUT_DURATION.getValue();

        final boolean pairTier = pairKey != null && pairLimit > 0;
        final boolean addressTier = addressKey != null && addressLimit > 0;
        final boolean usernameTier = userKey != null && usernameLimit > 0;

        Reservation reservation = null;
        long pairFailuresAtLimit = -1; // Set when this attempt starts a tier 1 block.
        long addressFailuresAtLimit = -1; // Set when this attempt starts a tier 2 block.
        long usernameFailuresAtLimit = -1; // Set when this attempt reaches tier 3, which then locks the account.

        // Locks are always taken in the same order (pair, address, username) so that attempts cannot deadlock.
        final List<Lock> held = new ArrayList<>(3);
        final long now;
        try {
            if (pairTier) {
                lock(held, pairFailures.getLock(pairKey));
            }
            if (addressTier) {
                lock(held, addressFailures.getLock(addressKey));
            }
            if (userKey != null) {
                // Guards both the username's count and its block.
                lock(held, usernameFailures.getLock(userKey));
            }
            // Read once the locks are held, so that time does not pass between reading it and acting on the counts.
            now = clock.millis();

            final FailureWindow pairWindow = pairTier ? current(pairFailures.get(pairKey), pairInterval, pairBlockDuration, now) : null;
            final FailureWindow addressWindow = addressTier ? current(addressFailures.get(addressKey), addressInterval, addressBlockDuration, now) : null;
            final FailureWindow usernameWindow = usernameTier ? current(usernameFailures.get(userKey), usernameInterval, Duration.ZERO, now) : null;
            final UsernameBlock block = userKey != null ? usernameBlocks.get(userKey) : null;

            final boolean blocked = usernameLimit > 0 && block != null && !block.hasEnded(now);
            boolean refuse = blocked;
            if (pairTier && isOverLimit(pairWindow, pairLimit)) {
                refuse = true;
                if (!pairWindow.isBlocked()) {
                    // At or over a limit lowered since the window's last attempt: the block starts now.
                    startBlock(pairFailures, pairWindow, now);
                    pairFailuresAtLimit = pairWindow.count;
                }
            }
            if (addressTier && isOverLimit(addressWindow, addressLimit)) {
                refuse = true;
                if (!addressWindow.isBlocked()) {
                    startBlock(addressFailures, addressWindow, now);
                    addressFailuresAtLimit = addressWindow.count;
                }
            }
            if (!blocked && usernameTier && count(usernameWindow) >= usernameLimit) {
                // Tier 3 reached: block the username at once (so that no concurrent attempt is admitted while the
                // account is being locked), and start a new count for when the block ends.
                usernameFailuresAtLimit = count(usernameWindow);
                usernameFailures.remove(userKey);
                usernameBlocks.put(userKey, new UsernameBlock(userKey, displayName(user), isDisplayTruncated(user), now + lockoutDuration.toMillis(), usernameFailuresAtLimit));
                refuse = true;
            }

            if (!refuse) {
                long addressStart = NOT_COUNTED;
                long usernameStart = NOT_COUNTED;
                if (pairTier) {
                    final FailureWindow counted = countAttempt(pairFailures, pairKey, pairWindow, now, pairLimit, displayName(user), isDisplayTruncated(user));
                    if (counted.isBlocked()) {
                        pairFailuresAtLimit = counted.count;
                    }
                }
                if (addressTier) {
                    final FailureWindow counted = countAttempt(addressFailures, addressKey, addressWindow, now, addressLimit, null, false);
                    if (counted.isBlocked()) {
                        addressFailuresAtLimit = counted.count;
                    }
                    addressStart = counted.start;
                }
                if (usernameTier) {
                    // Tier 3 blocks through its own cache and lock, so its count never records a block.
                    usernameStart = countAttempt(usernameFailures, userKey, usernameWindow, now, 0, null, false).start;
                }
                // A success clears the pair's count only where tier 1 counted it.
                reservation = new Reservation(pairTier ? pairKey : null, addressKey, addressStart, userKey, usernameStart, notLockedUser);
            }
        } finally {
            // Each lock is released on its own, so that a failure to release one leaves none of the others held.
            for (int i = held.size() - 1; i >= 0; i--) {
                try {
                    held.get(i).unlock();
                } catch (final RuntimeException e) {
                    Log.warn("Unable to release a user login limits cache lock", e);
                }
            }
        }

        // Audit entries and the account lock (which writes to the lockout provider) happen outside the cache locks.
        if (pairFailuresAtLimit >= 0) {
            Log.info("Login limit reached for username {} from IP address '{}' ({} attempts); further attempts are refused for {} unless the last of them succeeds",
                logName(user), forLog(addressKey), pairFailuresAtLimit, pairBlockDuration);
            audit(user, AUDIT_SUMMARY_LIMIT_REACHED,
                "Username " + logName(user) + " from IP address " + forLog(addressKey) + " reached the login limit (" + pairFailuresAtLimit
                    + " attempts); further attempts are refused for " + pairBlockDuration + " unless the last of them succeeds.");
        }
        if (addressFailuresAtLimit >= 0) {
            Log.info("Login limit reached for IP address '{}' ({} attempts); further attempts are refused for {} unless the last of them succeeds",
                forLog(addressKey), addressFailuresAtLimit, addressBlockDuration);
            // Filed under the address: the username of the last attempt need not belong to whoever reached the limit.
            audit(addressKey, AUDIT_SUMMARY_LIMIT_REACHED,
                "IP address " + forLog(addressKey) + " reached the login limit for any username (" + addressFailuresAtLimit
                    + " attempts, the last for username " + logName(user) + "); further attempts are refused for " + addressBlockDuration
                    + " unless the last of them succeeds.");
        }
        if (usernameFailuresAtLimit >= 0) {
            lockAccount(user, usernameFailuresAtLimit, now, lockoutDuration);
        }
        if (reservation == null) {
            Log.debug("Refusing login for username {} from IP address '{}': a login limit applies", logName(user), addressKey == null ? null : forLog(addressKey));
        }
        return reservation;
    }

    /**
     * Returns whether a login attempt may proceed, without counting it: false if the account is locked through
     * {@link LockOutManager} (checked whether or not the limits are enabled) or the login limits would refuse it (see
     * {@link #isLoginAllowed(String, String)}). For callers that cannot give an attempt back when it succeeds, such as a
     * mechanism used without a session; counting their attempts would let successful logins add up to a block.
     *
     * @param username the username the attempt is for (as provided by the client).
     * @param address the address the attempt comes from, or null if it is not known.
     * @return true if the attempt may proceed.
     */
    public boolean isAttemptAllowed(@Nonnull final String username, @Nullable final String address)
    {
        final String user = normalise(username);
        if (isAccountName(user) && Boolean.TRUE.equals(checkLocked(user))) {
            return false;
        }
        return isLoginAllowed(username, address);
    }

    /**
     * Returns whether a login attempt for a username from an address would be admitted by the login limits, without
     * counting it or locking anything. Returns false when tier 1 or tier 2 has blocked the key or is at its limit, when
     * tier 3 is at its limit or has blocked the username. Always returns true while the feature is disabled.
     *
     * This does not check whether the account is locked through {@link LockOutManager}. It is meant for mechanisms
     * whose failures are not counted (FAST tokens) and for {@link #isAttemptAllowed(String, String)}; other password
     * attempts use {@link #tryAcquireAttempt(String, String)}.
     *
     * @param username the username the attempt is for (as provided by the client).
     * @param address the address the attempt comes from, or null if it is not known.
     * @return true if the attempt may proceed.
     */
    public boolean isLoginAllowed(@Nonnull final String username, @Nullable final String address)
    {
        if (!ENABLED.getValue()) {
            return true;
        }
        final String user = normalise(username);
        final boolean accountName = isAccountName(user);
        final long now = clock.millis();

        if (address != null) {
            final String addressKey = addressKey(address);
            if (accountName && pairBlockEnd(pairFailures.get(pairKey(usernameKey(user), addressKey)), now) != NOT_BLOCKED) {
                return false;
            }
            if (addressBlockEnd(addressFailures.get(addressKey), now) != NOT_BLOCKED) {
                return false;
            }
        }
        if (!accountName) {
            return true;
        }
        final String userKey = usernameKey(user);
        if (isLimitReached(usernameFailures.get(userKey), MAX_ATTEMPTS_PER_USERNAME.getValue(), PER_USERNAME_INTERVAL.getValue(), now)) {
            return false;
        }
        final UsernameBlock block = usernameBlocks.get(userKey);
        return MAX_ATTEMPTS_PER_USERNAME.getValue() <= 0 || block == null || block.hasEnded(now);
    }

    /**
     * Returns whether attempts for a username are refused from every IP address: its account is locked through
     * {@link LockOutManager} (whether or not the limits are enabled), or tier 3 has blocked the username or is at its
     * limit. A refused attempt for such a username is answered as for a locked account; one refused by a tier 1 or
     * tier 2 block is answered as a wrong password would be. This does not report a refusal to LockOutManager's
     * listeners, and a failure to check the lock counts as not locked.
     *
     * @param username the username the attempt is for (as provided by the client).
     * @return true if the username is locked out from every IP address.
     */
    public boolean isLockedOut(@Nonnull final String username)
    {
        final String user = normalise(username);
        if (!isAccountName(user)) {
            return false;
        }
        try {
            if (lockOutManager.get().isAccountDisabled(user)) {
                return true;
            }
        } catch (final RuntimeException e) {
            Log.debug("Unable to check whether account {} is locked", logName(user), e);
        }
        if (!ENABLED.getValue()) {
            return false;
        }
        final String userKey = usernameKey(user);
        return isLimitReached(usernameFailures.get(userKey), MAX_ATTEMPTS_PER_USERNAME.getValue(), PER_USERNAME_INTERVAL.getValue(), clock.millis())
            || hasUsernameBlock(username);
    }

    /**
     * Returns whether tier 3 holds a block for a username that is in force: one set when the username reached the
     * limit per username from any IP address. Such a block goes with a LockOutManager lock set at the same time, except
     * where none could be set (see {@link #tryAcquireAttempt(String, String)}), so an administrator can tell a lock set
     * by the login limits from one set by hand. False while the limits are disabled or tier 3 is off, as a block then
     * refuses nothing.
     *
     * @param username the username, as given by the client or as the account's name.
     * @return true if the username is blocked by tier 3.
     */
    public boolean hasUsernameBlock(@Nonnull final String username)
    {
        return getUsernameBlock(username).isPresent();
    }

    /**
     * Returns the tier 3 block in force for a username, if any (see {@link #hasUsernameBlock(String)}).
     *
     * @param username the username, as given by the client or as the account's name.
     * @return the block, or empty if tier 3 holds none in force for the username.
     */
    @Nonnull
    public Optional<ActiveBlock> getUsernameBlock(@Nonnull final String username)
    {
        if (!ENABLED.getValue()) {
            return Optional.empty();
        }
        final String user = normalise(username);
        if (!isAccountName(user)) {
            return Optional.empty();
        }
        final String userKey = usernameKey(user);
        return Optional.ofNullable(usernameBlock(userKey, usernameBlocks.get(userKey), clock.millis()));
    }

    /**
     * Returns whether a lock was set by the per-username limit (tier 3): its username has a tier 3 block in force, and
     * the lock ends when the block does, as a lock set by the limit is given the block's end. A lock set by hand while
     * the username is blocked (which tier 3 leaves in place), a lock without an end, and every lock once the block has
     * ended or while the limits are disabled, are not.
     *
     * Ends within a second of each other count as the same, for lockout providers that store times to the second.
     *
     * @param flag the account's lock, as {@link LockOutManager#getDisabledStatus(String)} returns it.
     * @return true if the login limits set the lock.
     */
    public boolean isLockSetByLoginLimits(@Nonnull final LockOutFlag flag)
    {
        if (flag.getUsername() == null || flag.getEndTime() == null) {
            return false;
        }
        final long lockEnd = flag.getEndTime().getTime();
        return getUsernameBlock(flag.getUsername())
            .filter(block -> Math.abs(block.endsAt().toEpochMilli() - lockEnd) < 1000)
            .isPresent();
    }

    /**
     * Records that an attempt admitted by {@link #tryAcquireAttempt(String, String)} succeeded: the tier 1 count for
     * its username and address is cleared, and the attempt is given back to the tier 2 and tier 3 counts it was counted
     * in, if those windows are still open (a refund never reduces the count of a later window). Earlier failures in
     * those counts, tier 3 blocks and LockOutManager locks are left as they are.
     *
     * @param reservation the reservation returned when the attempt was admitted.
     */
    public void recordSuccessfulLogin(@Nonnull final Reservation reservation)
    {
        if (reservation.pairKey != null) {
            removeUnderLock(pairFailures, reservation.pairKey);
        }
        final long now = clock.millis();
        if (reservation.addressKey != null && reservation.addressStart != NOT_COUNTED) {
            giveBack(addressFailures, reservation.addressKey, reservation.addressStart, MAX_ATTEMPTS_PER_ADDRESS.getValue(),
                PER_ADDRESS_INTERVAL.getValue(), PER_ADDRESS_BLOCK_DURATION.getValue(), now);
        }
        if (reservation.usernameKey != null && reservation.usernameStart != NOT_COUNTED) {
            giveBack(usernameFailures, reservation.usernameKey, reservation.usernameStart, 0,
                PER_USERNAME_INTERVAL.getValue(), Duration.ZERO, now);
        }
    }

    @Override
    public void accountLocked(final LockOutFlag flag) {
        // Nothing to do.
    }

    /**
     * Clears the tier 3 count and any tier 3 block for the username, so that the next failure does not lock the
     * account again at once.
     *
     * @param username the username of the account that an administrator unlocked.
     */
    @Override
    public void accountUnlocked(final String username) {
        if (username == null) {
            return;
        }
        final String user = normalise(username);
        if (isAccountName(user)) {
            clearUsername(usernameKey(user));
        }
    }

    @Override
    public void lockedAccountDenied(final String username) {
        // Nothing to do.
    }

    /**
     * Returns, for each tier in order, how many blocks are in force and at most the given number of them, the latest
     * to end first. During an attack there can be many thousands of blocks; this bounds what an administrator's page
     * has to show and look up, while the newest blocks (those a genuine user is most likely to have just met) stay
     * visible. Counters below their limit, blocks and windows that have ended and tiers turned off (limit 0) are not
     * listed, and nothing is listed while the feature is disabled.
     *
     * Every tier 3 block is listed, whether or not the account was also locked through LockOutManager. Locks set by an
     * administrator are not listed; they are shown with the account's lockout status.
     *
     * Each call reads every entry of the counter caches, which in a cluster means reading from other nodes; it is
     * meant for an administrator's page, not for use on each login.
     *
     * @param maxPerTier the most blocks to return for each tier.
     * @return one entry per tier, in tier order; the lists are immutable.
     */
    @Nonnull
    public List<TierBlocks> getActiveBlocks(final int maxPerTier)
    {
        final List<TierBlocks> result = new ArrayList<>(Tier.values().length);
        for (final Tier tier : Tier.values()) {
            final List<ActiveBlock> blocks = activeBlocks(tier);
            final List<ActiveBlock> newest = blocks.stream()
                .sorted(Comparator.comparing(ActiveBlock::endsAt).reversed())
                .limit(maxPerTier)
                .toList();
            result.add(new TierBlocks(tier, blocks.size(), newest));
        }
        return List.copyOf(result);
    }

    /**
     * Returns the blocks in force for one tier, in no particular order; none while the feature is disabled.
     */
    @Nonnull
    private List<ActiveBlock> activeBlocks(@Nonnull final Tier tier)
    {
        if (!ENABLED.getValue()) {
            return List.of();
        }
        final long now = clock.millis();
        final List<ActiveBlock> result = new ArrayList<>();
        switch (tier) {
            case USERNAME_FROM_ADDRESS -> {
                for (final Map.Entry<String, FailureWindow> entry : pairFailures.entrySet()) {
                    addIfPresent(result, pairBlock(entry.getKey(), entry.getValue(), now));
                }
            }
            case ADDRESS -> {
                for (final Map.Entry<String, FailureWindow> entry : addressFailures.entrySet()) {
                    addIfPresent(result, addressBlock(entry.getKey(), entry.getValue(), now));
                }
            }
            case USERNAME -> {
                for (final Map.Entry<String, UsernameBlock> entry : usernameBlocks.entrySet()) {
                    addIfPresent(result, usernameBlock(entry.getKey(), entry.getValue(), now));
                }
            }
        }
        return result;
    }

    private static void addIfPresent(@Nonnull final List<ActiveBlock> list, @Nullable final ActiveBlock block)
    {
        if (block != null) {
            list.add(block);
        }
    }

    /**
     * Returns the tier 1 block that a counter represents, or null if it is not at its limit, its window has ended, or
     * the key is not a tier 1 key.
     */
    @Nullable
    private static ActiveBlock pairBlock(@Nonnull final String key, @Nullable final FailureWindow window, final long now)
    {
        final int separator = key.indexOf(PAIR_KEY_SEPARATOR);
        final long end = pairBlockEnd(window, now);
        if (separator < 0 || end == NOT_BLOCKED) {
            return null;
        }
        // The key holds the username's digest; the counter holds the username to show.
        return new ActiveBlock(Tier.USERNAME_FROM_ADDRESS, window.display, window.truncated, key.substring(0, separator),
            window.count, Instant.ofEpochMilli(end), key);
    }

    /**
     * Returns the tier 2 block that a counter represents, or null if it is not blocked (see {@link #blockEnd}).
     */
    @Nullable
    private static ActiveBlock addressBlock(@Nonnull final String key, @Nullable final FailureWindow window, final long now)
    {
        final long end = addressBlockEnd(window, now);
        if (end == NOT_BLOCKED) {
            return null;
        }
        return new ActiveBlock(Tier.ADDRESS, null, false, key, window.count, Instant.ofEpochMilli(end), key);
    }

    /**
     * Returns when the tier 1 block that a counter represents ends, or {@link #NOT_BLOCKED}.
     */
    private static long pairBlockEnd(@Nullable final FailureWindow window, final long now)
    {
        return blockEnd(window, MAX_ATTEMPTS_PER_PAIR.getValue(), PER_PAIR_INTERVAL.getValue(), PER_PAIR_BLOCK_DURATION.getValue(), now);
    }

    /**
     * Returns when the tier 2 block that a counter represents ends, or {@link #NOT_BLOCKED}.
     */
    private static long addressBlockEnd(@Nullable final FailureWindow window, final long now)
    {
        return blockEnd(window, MAX_ATTEMPTS_PER_ADDRESS.getValue(), PER_ADDRESS_INTERVAL.getValue(), PER_ADDRESS_BLOCK_DURATION.getValue(), now);
    }

    /**
     * Returns the tier 3 block that a cached block represents, or null if there is none, it has ended, or tier 3 is
     * off (limit 0).
     */
    @Nullable
    private static ActiveBlock usernameBlock(@Nonnull final String key, @Nullable final UsernameBlock block, final long now)
    {
        if (MAX_ATTEMPTS_PER_USERNAME.getValue() <= 0 || block == null || block.hasEnded(now)) {
            return null;
        }
        return new ActiveBlock(Tier.USERNAME, block.display, block.truncated, null, block.failures, Instant.ofEpochMilli(block.end), key);
    }

    /**
     * Clears the counter of one key, and with it any block it holds, so that counting for that key starts again from
     * zero, whether or not the counter has reached its limit. Does nothing if there is no such counter. This does not
     * write an audit entry. Used by tests that share this class's singleton, to leave no counts behind; the admin
     * console clears blocks through {@link #clearActiveBlock(Tier, String)}.
     *
     * Clearing a tier 3 block does not lift a LockOutManager lock; an administrator unlocks the account for that.
     *
     * @param tier the tier of the block.
     * @param username the username of the block: required for {@link Tier#USERNAME_FROM_ADDRESS} and
     *                 {@link Tier#USERNAME}, ignored for {@link Tier#ADDRESS}.
     * @param address the address of the block: required for {@link Tier#USERNAME_FROM_ADDRESS} and
     *                {@link Tier#ADDRESS}, ignored for {@link Tier#USERNAME}.
     * @throws IllegalArgumentException if a value the tier requires is missing.
     */
    @VisibleForTesting
    public void clearBlock(@Nonnull final Tier tier, @Nullable final String username, @Nullable final String address)
    {
        switch (tier) {
            case USERNAME_FROM_ADDRESS -> removeUnderLock(pairFailures, pairKey(usernameKey(normalise(require(username, "username"))), addressKey(require(address, "address"))));
            case ADDRESS -> removeUnderLock(addressFailures, addressKey(require(address, "address")));
            case USERNAME -> clearUsername(usernameKey(normalise(require(username, "username"))));
        }
    }

    /**
     * Lifts the block in force with the given tier and key (as listed by {@link #getActiveBlocks(int)}) and clears its
     * counter. Only that key's entry is read, under its lock. Nothing is cleared when no such block is in force, for
     * example because it has ended since it was listed, or the key's counter is below its limit. This does not
     * write an audit entry; the caller records who cleared it.
     *
     * @param tier the tier of the block.
     * @param key the key of the block, from {@link ActiveBlock#key()}.
     * @return the block that was lifted, or empty if no block with that tier and key was in force.
     */
    @Nonnull
    public Optional<ActiveBlock> clearActiveBlock(@Nonnull final Tier tier, @Nonnull final String key)
    {
        if (!ENABLED.getValue()) {
            return Optional.empty();
        }
        final Lock lock = switch (tier) {
            case USERNAME_FROM_ADDRESS -> pairFailures.getLock(key);
            case ADDRESS -> addressFailures.getLock(key);
            case USERNAME -> usernameFailures.getLock(key); // Guards both the username's count and its block.
        };
        lock.lock();
        try {
            final long now = clock.millis();
            final ActiveBlock block = switch (tier) {
                case USERNAME_FROM_ADDRESS -> pairBlock(key, pairFailures.get(key), now);
                case ADDRESS -> addressBlock(key, addressFailures.get(key), now);
                case USERNAME -> usernameBlock(key, usernameBlocks.get(key), now);
            };
            if (block == null) {
                return Optional.empty();
            }
            switch (tier) {
                case USERNAME_FROM_ADDRESS -> pairFailures.remove(key);
                case ADDRESS -> addressFailures.remove(key);
                case USERNAME -> {
                    usernameFailures.remove(key);
                    usernameBlocks.remove(key);
                }
            }
            return Optional.of(block);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether the attempt of a reservation found, when it was admitted, that the account of a username was not
     * locked through LockOutManager. A caller about to check the lock again once the same attempt has succeeded can
     * rely on this instead, saving a lookup for every login; the lookup is not cached by LockOutManager for accounts
     * that are not locked. Returns false when the check was not made or could not be completed, or was for another
     * name.
     *
     * The username must be exactly the name whose lock was checked: the normalised username (see
     * {@link #normalise(String)}). Any other form, even one that normalises to the same name (such as 'Alice' for
     * 'alice'), returns false, so that a lock filed under that exact name is still looked up.
     *
     * A lock set while the attempt's exchange was in progress is then not seen until the next login, as for a lock set
     * just after a login.
     *
     * @param reservation the reservation of the attempt.
     * @param username the name to ask about, such as the authorization identity of the successful exchange.
     * @return true if the attempt found the account of exactly that name not locked.
     */
    public boolean isKnownNotLocked(@Nonnull final Reservation reservation, @Nonnull final String username)
    {
        return reservation.notLockedUser != null && reservation.notLockedUser.equals(username);
    }

    /**
     * Clears the tier 3 count and block for a username key, under the key's lock.
     */
    private void clearUsername(@Nonnull final String userKey)
    {
        final Lock lock = usernameFailures.getLock(userKey);
        lock.lock();
        try {
            usernameFailures.remove(userKey);
            usernameBlocks.remove(userKey);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes an entry under its key's lock, so that a concurrent attempt cannot write back a count that is being
     * cleared.
     */
    private static void removeUnderLock(@Nonnull final Cache<String, FailureWindow> cache, @Nonnull final String key)
    {
        final Lock lock = cache.getLock(key);
        lock.lock();
        try {
            cache.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Nonnull
    private static String require(@Nullable final String value, @Nonnull final String name)
    {
        if (value == null) {
            throw new IllegalArgumentException("A " + name + " is required to clear a block of this tier.");
        }
        return value;
    }

    /**
     * The tiers in which failed logins are counted.
     */
    public enum Tier
    {
        /**
         * Tier 1: one username from one address.
         */
        USERNAME_FROM_ADDRESS,

        /**
         * Tier 2: one address, for any username.
         */
        ADDRESS,

        /**
         * Tier 3: one username, from any address.
         */
        USERNAME
    }

    /**
     * A block that currently refuses logins.
     *
     * @param tier the tier that applied the block.
     * @param username the normalised username the block applies to, shortened to 64 characters for display, or null
     *                 for a {@link Tier#ADDRESS} block.
     * @param usernameTruncated whether the username was shortened for display.
     * @param address the address the block applies to (an IPv6 address as its /64), or null for a {@link Tier#USERNAME}
     *                block.
     * @param failureCount the number of attempts that led to the block.
     * @param endsAt when the block ends.
     * @param key identifies the block for {@link #clearActiveBlock(Tier, String)}; not meant for display.
     */
    public record ActiveBlock(@Nonnull Tier tier, @Nullable String username, boolean usernameTruncated, @Nullable String address, long failureCount, @Nonnull Instant endsAt, @Nonnull String key) {}

    /**
     * The blocks in force for one tier, as returned by {@link #getActiveBlocks(int)}.
     *
     * @param tier the tier.
     * @param total how many blocks of this tier are in force.
     * @param blocks at most the requested number of them, the latest to end first.
     */
    public record TierBlocks(@Nonnull Tier tier, int total, @Nonnull List<ActiveBlock> blocks) {}

    /**
     * Marks a tier in a {@link Reservation} that did not count the attempt.
     */
    private static final long NOT_COUNTED = -1;

    /**
     * Returned by {@link #blockEnd} for a counter that does not refuse attempts.
     */
    private static final long NOT_BLOCKED = -1;

    /**
     * An admitted attempt, as returned by {@link #tryAcquireAttempt(String, String)}: the keys it was counted under
     * and the start of each window it was counted in, so that a success gives it back to those windows only.
     */
    public static final class Reservation implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private final String pairKey;
        private final String addressKey;
        private final long addressStart;
        private final String usernameKey;
        private final long usernameStart;

        /**
         * The normalised username whose account the attempt found not locked when it was admitted, or null if the
         * check was not made or could not be completed (see {@link #isKnownNotLocked(Reservation, String)}).
         */
        private final String notLockedUser;

        Reservation(@Nullable final String pairKey, @Nullable final String addressKey, final long addressStart,
                    @Nullable final String usernameKey, final long usernameStart, @Nullable final String notLockedUser)
        {
            this.pairKey = pairKey;
            this.addressKey = addressKey;
            this.addressStart = addressStart;
            this.usernameKey = usernameKey;
            this.usernameStart = usernameStart;
            this.notLockedUser = notLockedUser;
        }
    }

    /**
     * Returns whether the account is locked through LockOutManager, reporting a refusal for a locked account to its
     * listeners; or null if that could not be checked. A failure to check is logged, and does not refuse the attempt
     * on its own (see {@link #tryAcquireAttempt(String, String)}).
     */
    @Nullable
    private Boolean checkLocked(@Nonnull final String user)
    {
        try {
            if (lockOutManager.get().isAccountDisabled(user)) {
                lockOutManager.get().recordFailedLogin(user);
                Log.debug("Refusing login for locked account {} before evaluating its credentials", logName(user));
                return true;
            }
            return false;
        } catch (final RuntimeException e) {
            Log.debug("Unable to check whether account {} is locked; leaving that to the check after authentication", logName(user), e);
            return null;
        }
    }

    /**
     * Locks the account through LockOutManager, for an account that reached tier 3 and whose username is already
     * blocked in the cache for the lockout duration. Where a lock is not possible (no such account, an existing lock,
     * a read-only provider or one that cannot end a lock, or an error), the block in the cache stands in for it, so
     * the answer is the same.
     *
     * An administrator's account is never locked: the block in the cache refuses its XMPP and JMX logins in the same
     * way, while the admin console, which honours only LockOutManager locks, stays open to it. Otherwise anyone who
     * knows an administrator's username could keep them out of the admin console by guessing at it.
     */
    private void lockAccount(@Nonnull final String user, final long failures, final long now, @Nonnull final Duration lockoutDuration)
    {
        final long end = now + lockoutDuration.toMillis();
        String reason;
        try {
            if (!userExists.test(user)) {
                reason = "the account does not exist";
            } else if (isAdministratorOrUnknown(user)) {
                reason = "the account is an administrator's, which must not be locked out of the admin console";
            } else if (lockOutManager.get().getDisabledStatus(user) != null) {
                reason = "the account already has a lock, which is left in place";
            } else {
                final LockOutProvider provider = lockOutProvider.get();
                if (provider.isReadOnly()) {
                    reason = "the lockout provider is read-only";
                } else if (!provider.isTimeoutSupported()) {
                    reason = "the lockout provider cannot set a lock that ends";
                } else {
                    lockOutManager.get().disableAccount(user, new Date(now), new Date(end));
                    Log.info("Locked account {} for {} after {} attempts that did not succeed", logName(user), lockoutDuration, failures);
                    audit(user, AUDIT_SUMMARY_ACCOUNT_LOCKED,
                        "The account is locked for " + lockoutDuration + " after " + failures + " attempts that did not succeed.");
                    return;
                }
            }
        } catch (final IllegalArgumentException e) {
            // A username that cannot name an account (it is not a valid node): expected for junk usernames, so no trace.
            // Fail closed: the username stays blocked in the cache.
            Log.debug("Not locking {} after {} attempts that did not succeed, as it is not a valid account name: {}", logName(user), failures, e.getMessage());
            reason = "the username is not a valid account name";
        } catch (final RuntimeException e) {
            // Fail closed: the username stays blocked in the cache.
            Log.warn("Unable to lock account {} after {} attempts that did not succeed; refusing its logins instead", logName(user), failures, e);
            reason = "locking the account failed";
        }
        Log.info("Login limit reached for username {}; refusing its logins for {} ({})", logName(user), lockoutDuration, reason);
        audit(user, AUDIT_SUMMARY_LIMIT_REACHED,
            "Login attempts for this username from any IP address are refused for " + lockoutDuration + " after "
                + failures + " attempts that did not succeed, without locking the account because " + reason + ".");
    }

    /**
     * Returns whether an account belongs to an administrator, or true if that cannot be determined: a lock that might
     * keep an administrator out of the admin console is not set, and the block in the cache refuses the username's
     * other logins either way.
     */
    private boolean isAdministratorOrUnknown(@Nonnull final String user)
    {
        try {
            return isAdministrator.test(user);
        } catch (final RuntimeException e) {
            Log.debug("Unable to determine whether {} is an administrator; blocking it without locking the account", logName(user), e);
            return true;
        }
    }

    private static void lock(@Nonnull final List<Lock> held, @Nonnull final Lock lock)
    {
        lock.lock();
        held.add(lock);
    }

    /**
     * Returns a counter if it still applies, or null if there is none or it has run out, after which its key starts
     * afresh. A counter that has started a block (tiers 1 and 2 only) applies until the block duration has passed since
     * then, whether or not its window has ended; any other counter applies until its window ends. The durations are
     * the current settings, so a change applies to counters and blocks already in force.
     *
     * @param blockDuration the tier's block duration; unused for tier 3, whose counters never start a block.
     */
    @Nullable
    private static FailureWindow current(@Nullable final FailureWindow window, @Nonnull final Duration interval, @Nonnull final Duration blockDuration, final long now)
    {
        if (window == null) {
            return null;
        }
        if (window.isBlocked()) {
            return now < window.blockStart + blockDuration.toMillis() ? window : null;
        }
        return window.hasEnded(interval, now) ? null : window;
    }

    /**
     * Returns whether a tier 1 or tier 2 counter that applies refuses attempts: it has started a block, or its count is
     * at or over the limit (which happens without a block when the limit was lowered after the counter's last
     * attempt; the next attempt then starts the block).
     */
    private static boolean isOverLimit(@Nullable final FailureWindow window, final long limit)
    {
        return window != null && (window.isBlocked() || window.count >= limit);
    }

    /**
     * Returns when the block that a tier 1 or tier 2 counter represents ends, or {@link #NOT_BLOCKED} if it does not
     * refuse attempts or its tier is off. A counter at or over a lowered limit, without a block, refuses attempts until
     * its window ends unless an attempt starts its block first.
     */
    private static long blockEnd(@Nullable final FailureWindow entry, final long limit, @Nonnull final Duration interval, @Nonnull final Duration blockDuration, final long now)
    {
        final FailureWindow window = limit > 0 ? current(entry, interval, blockDuration, now) : null;
        if (!isOverLimit(window, limit)) {
            return NOT_BLOCKED;
        }
        return window.isBlocked() ? window.blockStart + blockDuration.toMillis() : window.start + interval.toMillis();
    }

    /**
     * Records that a tier 1 or tier 2 block starts now for a counter. The caller holds the key's lock.
     */
    private static void startBlock(@Nonnull final Cache<String, FailureWindow> cache, @Nonnull final FailureWindow window, final long now)
    {
        cache.put(window.key, new FailureWindow(window.key, window.start, window.count, now, window.display, window.truncated));
    }

    private static long count(@Nullable final FailureWindow window)
    {
        return window == null ? 0 : window.count;
    }

    /**
     * Counts an attempt in an open window, or starts a new window with it, and starts a block if the count reaches the
     * limit. The caller holds the key's lock.
     *
     * @param blockLimit the count at which a block starts, or 0 for a counter that never starts one (tier 3).
     * @param display the username to show for the counter, shortened for display (tier 1), or null.
     * @param truncated whether the username to show was shortened.
     * @return the window, including this attempt.
     */
    private static FailureWindow countAttempt(@Nonnull final Cache<String, FailureWindow> cache, @Nonnull final String key, @Nullable final FailureWindow open,
                                              final long now, final long blockLimit, @Nullable final String display, final boolean truncated)
    {
        final long start = open == null ? now : open.start;
        final long count = open == null ? 1 : open.count + 1;
        final FailureWindow updated = new FailureWindow(key, start, count, blockLimit > 0 && count >= blockLimit ? now : FailureWindow.NO_BLOCK, display, truncated);
        cache.put(key, updated); // An explicit put is needed for the change to reach other cluster nodes.
        return updated;
    }

    /**
     * Gives back one attempt to a count, under the key's lock, if the window it was counted in still applies (see
     * {@link #current}). A refund that brings the count below the limit lifts the block.
     *
     * @param limit the tier's limit, or 0 for tier 3, whose counters never start a block.
     */
    private static void giveBack(@Nonnull final Cache<String, FailureWindow> cache, @Nonnull final String key, final long windowStart,
                                 final long limit, @Nonnull final Duration interval, @Nonnull final Duration blockDuration, final long now)
    {
        final Lock lock = cache.getLock(key);
        lock.lock();
        try {
            final FailureWindow window = current(cache.get(key), interval, blockDuration, now);
            if (window == null || window.start != windowStart) {
                return;
            }
            final long count = window.count - 1;
            if (count <= 0) {
                cache.remove(key);
            } else {
                final long blockStart = window.isBlocked() && count >= limit ? window.blockStart : FailureWindow.NO_BLOCK;
                cache.put(key, new FailureWindow(key, window.start, count, blockStart, window.display, window.truncated));
            }
        } finally {
            lock.unlock();
        }
    }

    private static boolean isLimitReached(@Nullable final FailureWindow window, final long limit, @Nonnull final Duration interval, final long now)
    {
        return limit > 0 && window != null && !window.hasEnded(interval, now) && window.count >= limit;
    }

    /**
     * Normalises a username so that variants a client may send for the same account share counts: surrounding
     * whitespace and a suffix of '@' and this server's XMPP domain (in any case) are removed, as the default
     * authentication provider accepts 'user@domain' for its own domain, and then whitespace before that suffix; then
     * the result is prepared with nodeprep, or lower-cased if nodeprep rejects it.
     *
     * A value longer than {@link #MAX_USERNAME_BYTES} characters cannot name an account (see
     * {@link #isAccountName(String)}), so it is only lower-cased: nodeprep's cost grows with the square of the length
     * for some input (combining marks in reverse canonical order), and the value comes from the client.
     */
    @Nonnull
    private String normalise(@Nonnull final String username)
    {
        String user = username.trim();
        final String domain = xmppDomain.get();
        if (domain != null && !domain.isEmpty()) {
            final String suffix = "@" + domain;
            if (user.length() > suffix.length() && user.regionMatches(true, user.length() - suffix.length(), suffix, 0, suffix.length())) {
                user = user.substring(0, user.length() - suffix.length()).trim();
            }
        }
        if (user.length() > MAX_USERNAME_BYTES) {
            return user.toLowerCase(Locale.ROOT);
        }
        try {
            return Stringprep.nodeprep(user);
        } catch (final StringprepException e) {
            return user.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Returns whether a normalised username is short enough to name an account (see {@link #MAX_USERNAME_BYTES}).
     * Only such usernames are used as cache keys or looked up as accounts.
     */
    private static boolean isAccountName(@Nonnull final String user)
    {
        return user.length() <= MAX_USERNAME_BYTES && user.getBytes(StandardCharsets.UTF_8).length <= MAX_USERNAME_BYTES;
    }

    /**
     * Writes a security audit entry. The subject is escaped and truncated to the width of the audit log's username
     * column, as a longer value makes the insert fail.
     */
    private void audit(@Nonnull final String subject, @Nonnull final String summary, @Nonnull final String details)
    {
        String safeSubject = forLog(subject);
        if (safeSubject.codePointCount(0, safeSubject.length()) > MAX_AUDIT_SUBJECT_LENGTH) {
            safeSubject = safeSubject.substring(0, safeSubject.offsetByCodePoints(0, MAX_AUDIT_SUBJECT_LENGTH));
        }
        securityAuditManager.get().logEvent(safeSubject, summary, details);
    }

    /**
     * Escapes control characters (including line and paragraph separators) and bidirectional text controls in a value
     * provided by a peer, so that it cannot forge lines in a log or audit entry, nor make one read differently from
     * what it contains.
     *
     * @param value the value to escape.
     * @return the value, with each such character replaced by a \\uXXXX escape.
     */
    @Nonnull
    public static String forLog(@Nonnull final String value)
    {
        StringBuilder result = null;
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029' || isBidiControl(c)) {
                if (result == null) {
                    result = new StringBuilder(value.length() + 16).append(value, 0, i);
                }
                result.append(String.format("\\u%04x", (int) c));
            } else if (result != null) {
                result.append(c);
            }
        }
        return result == null ? value : result.toString();
    }

    /**
     * Returns whether a character is a bidirectional text control: the Arabic letter mark, the left-to-right and
     * right-to-left marks, the embedding and override controls, and the isolates.
     */
    private static boolean isBidiControl(final char c)
    {
        return c == '\u061C' || c == '\u200E' || c == '\u200F' || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069');
    }

    @Nullable
    private static String serverXmppDomain()
    {
        final XMPPServer server = XMPPServer.getInstance();
        return server == null || server.getServerInfo() == null ? null : server.getServerInfo().getXMPPDomain();
    }

    /**
     * Returns the key of a normalised username in the per-username caches: a fixed-size digest, so that the number of
     * usernames the caches can hold does not depend on how long they are, and junk usernames cannot push a real
     * user's count out of the cache sooner for being long.
     */
    @Nonnull
    static String usernameKey(@Nonnull final String user)
    {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(user.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Returns the key of an address for the per-address limits: an IPv6 address is reduced to its /64 prefix, as one
     * network or host is typically given a whole /64; IPv4 addresses (including IPv4-mapped IPv6 addresses) and values
     * that are not an IP address are used as they are.
     */
    @Nonnull
    static String addressKey(@Nonnull final String address)
    {
        final String literal = address.indexOf('%') >= 0 ? address.substring(0, address.indexOf('%')) : address;
        if (literal.indexOf(':') < 0 || !InetAddresses.isInetAddress(literal)) {
            return address;
        }
        final InetAddress parsed = InetAddresses.forString(literal);
        if (!(parsed instanceof Inet6Address)) {
            return address;
        }
        final byte[] bytes = parsed.getAddress();
        Arrays.fill(bytes, 8, 16, (byte) 0);
        try {
            return InetAddresses.toAddrString(InetAddress.getByAddress(bytes)) + "/64";
        } catch (final UnknownHostException e) {
            return address;
        }
    }

    /**
     * Returns the form of a normalised username shown to administrators: truncated to 64 characters.
     */
    @Nonnull
    private static String displayName(@Nonnull final String user)
    {
        return isDisplayTruncated(user) ? user.substring(0, user.offsetByCodePoints(0, MAX_AUDIT_SUBJECT_LENGTH)) : user;
    }

    /**
     * Returns the form of a normalised username written to the log and to audit details: the form shown to
     * administrators (see {@link #displayName(String)}), escaped (see {@link #forLog(String)}) and quoted, followed by
     * '(shortened)' when it was shortened. A client can send a username of any length, so its full form is never
     * logged.
     */
    @Nonnull
    private static String logName(@Nonnull final String user)
    {
        return "'" + forLog(displayName(user)) + "'" + (isDisplayTruncated(user) ? " (shortened)" : "");
    }

    /**
     * Returns whether {@link #displayName(String)} shortens a normalised username.
     */
    private static boolean isDisplayTruncated(@Nonnull final String user)
    {
        return user.codePointCount(0, user.length()) > MAX_AUDIT_SUBJECT_LENGTH;
    }

    @Nonnull
    private static String pairKey(@Nonnull final String userKey, @Nonnull final String address)
    {
        return address + PAIR_KEY_SEPARATOR + userKey;
    }

    /**
     * Returns whether a username is an administrator's, as the admin console decides it when an administrator signs in.
     */
    private static boolean isAdministratorAccount(@Nonnull final String username)
    {
        return AdminManager.getInstance().isUserAdmin(username, true);
    }

    private static boolean isExistingUser(@Nonnull final String username)
    {
        try {
            UserManager.getInstance().getUser(username);
            return true;
        } catch (final UserNotFoundException e) {
            return false;
        }
    }

    /**
     * A tier 3 block, stored under the digest of the username (see {@link #usernameKey(String)}).
     */
    @VisibleForTesting
    static final class UsernameBlock implements Cacheable
    {
        private static final long serialVersionUID = 1L;

        /**
         * The key the block is stored under, held here so that the cached size accounts for it.
         */
        private final String key;

        /**
         * The username, truncated for display.
         */
        private final String display;

        /**
         * Whether the username was shortened for display.
         */
        private final boolean truncated;

        /**
         * When the block ends, in epoch milliseconds.
         */
        private final long end;

        /**
         * The number of attempts that led to the block.
         */
        private final long failures;

        UsernameBlock(@Nonnull final String key, @Nonnull final String display, final boolean truncated, final long end, final long failures)
        {
            this.key = key;
            this.display = display;
            this.truncated = truncated;
            this.end = end;
            this.failures = failures;
        }

        boolean hasEnded(final long now)
        {
            return now >= end;
        }

        String key() { return key; }

        String display() { return display; }

        boolean truncated() { return truncated; }

        long end() { return end; }

        long failures() { return failures; }

        @Override
        public int getCachedSize()
        {
            // The key is stored by the cache alongside this value, but caches size only the value: account for both.
            return CacheSizes.sizeOfObject() + 2 * CacheSizes.sizeOfLong() + CacheSizes.sizeOfBoolean() + 2 * CacheSizes.sizeOfString(key) + CacheSizes.sizeOfString(display);
        }
    }

    /**
     * The failures counted for one key in one fixed window, and for tiers 1 and 2, when the count reached the limit and
     * started a block.
     */
    @VisibleForTesting
    static final class FailureWindow implements Cacheable
    {
        private static final long serialVersionUID = 2L;

        /**
         * The value of {@link #blockStart} for a window that has not started a block.
         */
        static final long NO_BLOCK = -1;

        /**
         * The key the window is stored under, held here so that the cached size accounts for it.
         */
        private final String key;

        /**
         * When the window started, in epoch milliseconds.
         */
        private final long start;

        /**
         * The number of failures in the window.
         */
        private final long count;

        /**
         * When the count reached the limit and started a block, in epoch milliseconds, or {@link #NO_BLOCK}.
         */
        private final long blockStart;

        /**
         * For a tier 1 counter, whose key holds the username's digest: the username, shortened for display; otherwise
         * null.
         */
        private final String display;

        /**
         * Whether {@link #display} was shortened.
         */
        private final boolean truncated;

        FailureWindow(@Nonnull final String key, final long start, final long count, final long blockStart,
                      @Nullable final String display, final boolean truncated)
        {
            this.key = key;
            this.start = start;
            this.count = count;
            this.blockStart = blockStart;
            this.display = display;
            this.truncated = truncated;
        }

        String key() { return key; }

        long start() { return start; }

        long count() { return count; }

        long blockStart() { return blockStart; }

        String display() { return display; }

        boolean truncated() { return truncated; }

        boolean isBlocked()
        {
            return blockStart != NO_BLOCK;
        }

        boolean hasEnded(@Nonnull final Duration interval, final long now)
        {
            return now >= start + interval.toMillis();
        }

        @Override
        public int getCachedSize()
        {
            // The key is stored by the cache alongside this value, but caches size only the value: account for both.
            return CacheSizes.sizeOfObject() + 3 * CacheSizes.sizeOfLong() + CacheSizes.sizeOfBoolean() + 2 * CacheSizes.sizeOfString(key)
                + (display == null ? 0 : CacheSizes.sizeOfString(display));
        }
    }
}
