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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * The caches are bounded, so a flood of attempts (for example, made-up usernames from one address) can make a counts
 * cache evict its oldest entries. Two measures keep such a flood from resetting a real user's limits. Blocks of every
 * tier are kept in caches of their own, apart from the counts: only an attempt that reaches a limit creates a block,
 * so a flood that reaches no limit cannot remove one. And the tier 1 and tier 3 counts of usernames that have no
 * account are kept apart from those of usernames that have one (see {@link #isKnownAccount(String)}), so made-up
 * usernames evict only each other's counts. Usernames without an account are counted, limited and blocked exactly as
 * others are, so the answers do not reveal whether an account exists. To evict a real user's count, an attacker would
 * need to fail at as many real usernames as the counts cache holds. Tier 2 counts have no username and are not split.
 *
 * @author Matthew Vivian
 */
public class UserLoginLimitManager implements LockOutEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(UserLoginLimitManager.class);

    public static final String PAIR_FAILURES_CACHE_NAME = "User Login Failures per Username and IP Address";
    public static final String ADDRESS_FAILURES_CACHE_NAME = "User Login Failures per IP Address";
    public static final String USERNAME_FAILURES_CACHE_NAME = "User Login Failures per Username";
    public static final String UNKNOWN_PAIR_FAILURES_CACHE_NAME = "User Login Failures per Unknown Username and IP Address";
    public static final String UNKNOWN_USERNAME_FAILURES_CACHE_NAME = "User Login Failures per Unknown Username";
    public static final String PAIR_BLOCKS_CACHE_NAME = "User Login Blocks per Username and IP Address";
    public static final String ADDRESS_BLOCKS_CACHE_NAME = "User Login Blocks per IP Address";
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
     * The most usernames an IP address block records as refused (see {@link BlocksForAddress#refusedUsernames()}), so
     * that the user's page can say an address block refused that username while the block's size stays bounded.
     */
    static final int MAX_REFUSED_USERNAMES = 10;

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
     * Tier 1 counts for usernames that have no account, under the same keys as {@link #pairFailures} would use and
     * guarded by the same per-key locks (those of {@link #pairFailures}). Kept apart so that a flood of made-up
     * usernames can only evict other made-up usernames' counts, never an account's (see {@link #isKnownAccount}).
     */
    private final Cache<String, FailureWindow> unknownPairFailures;

    /**
     * Tier 3 counts for usernames that have no account, guarded by the per-key locks of {@link #usernameFailures}
     * (see {@link #unknownPairFailures}).
     */
    private final Cache<String, FailureWindow> unknownUsernameFailures;

    /**
     * Tier 1 blocks, under the same keys as their counts in {@link #pairFailures}, and guarded by the same per-key
     * locks. Blocks are kept apart from the counts so that a flood of attempts, which can make the counts cache evict
     * entries, cannot remove a block: only reaching a limit creates a block.
     */
    private final Cache<String, AddressBlock> pairBlocks;

    /**
     * Tier 2 blocks, under the same keys as their counts in {@link #addressFailures}, and guarded by the same per-key
     * locks (see {@link #pairBlocks}).
     */
    private final Cache<String, AddressBlock> addressBlocks;

    /**
     * Tier 3 blocks that stand in for a LockOutManager lock, keyed by the digest of the normalised username, and
     * guarded by the per-key locks of {@link #usernameFailures}.
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
            CacheFactory.createCache(UNKNOWN_PAIR_FAILURES_CACHE_NAME),
            CacheFactory.createCache(UNKNOWN_USERNAME_FAILURES_CACHE_NAME),
            CacheFactory.createCache(PAIR_BLOCKS_CACHE_NAME),
            CacheFactory.createCache(ADDRESS_BLOCKS_CACHE_NAME),
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
                          @Nonnull final Cache<String, FailureWindow> unknownPairFailures,
                          @Nonnull final Cache<String, FailureWindow> unknownUsernameFailures,
                          @Nonnull final Cache<String, AddressBlock> pairBlocks,
                          @Nonnull final Cache<String, AddressBlock> addressBlocks,
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
        this.unknownPairFailures = unknownPairFailures;
        this.unknownUsernameFailures = unknownUsernameFailures;
        this.pairBlocks = pairBlocks;
        this.addressBlocks = addressBlocks;
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
        final String addressKey = address == null ? null : addressKey(address);
        final String userKey = accountName ? usernameKey(user) : null;
        if (!ENABLED.getValue()) {
            // Nothing was counted, so a success has nothing to give back or clear.
            return new Reservation(null, addressKey, NOT_COUNTED, userKey, NOT_COUNTED, true);
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

        // Looked up before any lock is taken, as it may read the user store. The answer only chooses the counts caches;
        // the limits, blocks and answers are the same either way, so they do not reveal whether the account exists.
        final boolean knownAccount = !(pairTier || usernameTier) || isKnownAccount(user);
        final Cache<String, FailureWindow> pairCounts = knownAccount ? pairFailures : unknownPairFailures;
        final Cache<String, FailureWindow> usernameCounts = knownAccount ? usernameFailures : unknownUsernameFailures;

        Reservation reservation = null;
        long pairFailuresAtLimit = -1; // Set when this attempt starts a tier 1 block.
        long addressFailuresAtLimit = -1; // Set when this attempt starts a tier 2 block.
        long usernameFailuresAtLimit = -1; // Set when this attempt reaches tier 3, which then locks the account.

        // Locks are always taken in the same order (pair, address, username) so that attempts cannot deadlock.
        final List<Lock> held = new ArrayList<>(3);
        final long now;
        try {
            if (pairTier) {
                // Guards both the pair's count (in either counts cache) and its block.
                lock(held, pairFailures.getLock(pairKey));
            }
            if (addressTier) {
                // Guards both the address's count and its block.
                lock(held, addressFailures.getLock(addressKey));
            }
            if (usernameTier) {
                // Guards both the username's count (in either counts cache) and its block. With tier 3 off neither is
                // read or written.
                lock(held, usernameFailures.getLock(userKey));
            }
            // Read once the locks are held, so that time does not pass between reading it and acting on the counts.
            now = clock.millis();

            final FailureWindow pairWindow = pairTier ? current(pairCounts.get(pairKey), pairInterval, now) : null;
            final AddressBlock pairBlock = pairTier ? inForce(pairBlocks.get(pairKey), pairBlockDuration, now) : null;
            final FailureWindow addressWindow = addressTier ? current(addressFailures.get(addressKey), addressInterval, now) : null;
            final AddressBlock addressBlock = addressTier ? inForce(addressBlocks.get(addressKey), addressBlockDuration, now) : null;
            final FailureWindow usernameWindow = usernameTier ? current(usernameCounts.get(userKey), usernameInterval, now) : null;
            final UsernameBlock block = userKey != null ? usernameBlocks.get(userKey) : null;

            final boolean blocked = usernameLimit > 0 && block != null && !block.hasEnded(now);
            boolean refuse = blocked;
            if (pairTier && (pairBlock != null || count(pairWindow) >= pairLimit)) {
                refuse = true;
                if (pairBlock == null) {
                    // At or over a limit lowered since the window's last attempt: the block starts now.
                    startBlock(pairCounts, pairBlocks, pairWindow, Tier.USERNAME_FROM_ADDRESS, addressKey, now, List.of());
                    pairFailuresAtLimit = pairWindow.count;
                }
            }
            if (addressTier && (addressBlock != null || count(addressWindow) >= addressLimit)) {
                refuse = true;
                // Recorded so that the refused user's page can say that an address block refused them.
                final RefusedUsername refused = new RefusedUsername(userKey, displayName(user), isDisplayTruncated(user));
                if (addressBlock == null) {
                    startBlock(addressFailures, addressBlocks, addressWindow, Tier.ADDRESS, addressKey, now, List.of(refused));
                    addressFailuresAtLimit = addressWindow.count;
                } else {
                    recordRefusal(addressBlocks, addressBlock, refused);
                }
            }
            if (!blocked && usernameTier && count(usernameWindow) >= usernameLimit) {
                // Tier 3 reached: block the username at once (so that no concurrent attempt is admitted while the
                // account is being locked), and start a new count for when the block ends. The block is written
                // before the count is removed, as startBlock does, so that isLoginAllowed (which reads without the
                // lock) never sees neither.
                usernameFailuresAtLimit = count(usernameWindow);
                usernameBlocks.put(userKey, new UsernameBlock(userKey, displayName(user), isDisplayTruncated(user), now + lockoutDuration.toMillis(), usernameFailuresAtLimit));
                usernameCounts.remove(userKey);
                refuse = true;
            }

            if (!refuse) {
                long addressStart = NOT_COUNTED;
                long usernameStart = NOT_COUNTED;
                if (pairTier) {
                    final FailureWindow counted = countAttempt(pairCounts, pairBlocks, Tier.USERNAME_FROM_ADDRESS, pairKey, addressKey, pairWindow, now, pairLimit, displayName(user), isDisplayTruncated(user));
                    if (counted.count >= pairLimit) {
                        pairFailuresAtLimit = counted.count;
                    }
                }
                if (addressTier) {
                    final FailureWindow counted = countAttempt(addressFailures, addressBlocks, Tier.ADDRESS, addressKey, addressKey, addressWindow, now, addressLimit, null, false);
                    if (counted.count >= addressLimit) {
                        addressFailuresAtLimit = counted.count;
                    }
                    addressStart = counted.start;
                }
                if (usernameTier) {
                    // Tier 3 blocks above, through its own cache, so its count never starts a block here.
                    usernameStart = countAttempt(usernameCounts, null, Tier.USERNAME, userKey, null, usernameWindow, now, 0, null, false).start;
                }
                // A success clears the pair's count only where tier 1 counted it, and in the counts caches it was
                // counted in.
                reservation = new Reservation(pairTier ? pairKey : null, addressKey, addressStart, userKey, usernameStart, knownAccount);
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
            if (accountName && activePairBlock(pairKey(usernameKey(user), addressKey), now) != null) {
                return false;
            }
            if (activeAddressBlock(addressKey, now) != null) {
                return false;
            }
        }
        if (!accountName) {
            return true;
        }
        final String userKey = usernameKey(user);
        if (isLimitReached(usernameCount(userKey, now), MAX_ATTEMPTS_PER_USERNAME.getValue(), PER_USERNAME_INTERVAL.getValue(), now)) {
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
        final long now = clock.millis();
        return isLimitReached(usernameCount(userKey, now), MAX_ATTEMPTS_PER_USERNAME.getValue(), PER_USERNAME_INTERVAL.getValue(), now)
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
     * ended, while tier 3 is off or while the limits are disabled, are not.
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
     * Records that an attempt admitted by {@link #tryAcquireAttempt(String, String)} succeeded: the tier 1 count and
     * any tier 1 block for its username and address are cleared, and the attempt is given back to the tier 2 and tier 3
     * counts it was counted in, if those windows still apply (a refund never reduces the count of a later window). A
     * tier 2 block started by the window the attempt was counted in is lifted if the refund brings its failures below
     * the limit. Earlier failures in those counts, tier 3 blocks and LockOutManager locks are left as they are.
     *
     * @param reservation the reservation returned when the attempt was admitted.
     */
    public void recordSuccessfulLogin(@Nonnull final Reservation reservation)
    {
        if (reservation.pairKey != null) {
            removeUnderLock(pairFailures.getLock(reservation.pairKey), reservation.pairKey,
                reservation.knownAccount ? pairFailures : unknownPairFailures, pairBlocks);
        }
        final long now = clock.millis();
        if (reservation.addressKey != null && reservation.addressStart != NOT_COUNTED) {
            giveBack(addressFailures.getLock(reservation.addressKey), addressFailures, addressBlocks, reservation.addressKey, reservation.addressStart,
                MAX_ATTEMPTS_PER_ADDRESS.getValue(), PER_ADDRESS_INTERVAL.getValue(), PER_ADDRESS_BLOCK_DURATION.getValue(), now);
        }
        if (reservation.usernameKey != null && reservation.usernameStart != NOT_COUNTED) {
            giveBack(usernameFailures.getLock(reservation.usernameKey), reservation.knownAccount ? usernameFailures : unknownUsernameFailures, null,
                reservation.usernameKey, reservation.usernameStart, 0, PER_USERNAME_INTERVAL.getValue(), Duration.ZERO, now);
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
     * Returns the blocks in force for one tier, in no particular order: every block that has not ended, and every tier
     * 1 or tier 2 count at or over a limit lowered since its last attempt. None while the feature is disabled or the
     * tier is off (limit 0). Every tier 3 block is returned, whether or not the account was also locked through
     * LockOutManager.
     *
     * Each call reads every entry of the tier's blocks cache, and for tiers 1 and 2 of its counts caches, which in a
     * cluster means reading from other nodes; it is meant for an administrator's page, not for use on each login.
     *
     * @param tier the tier.
     * @return the tier's blocks in force.
     */
    @Nonnull
    @VisibleForTesting
    List<ActiveBlock> activeBlocks(@Nonnull final Tier tier)
    {
        if (!ENABLED.getValue()) {
            return List.of();
        }
        final long now = clock.millis();
        final List<ActiveBlock> result = new ArrayList<>();
        switch (tier) {
            case USERNAME_FROM_ADDRESS -> addActiveBlocks(result, Tier.USERNAME_FROM_ADDRESS, pairFailures, unknownPairFailures, pairBlocks,
                MAX_ATTEMPTS_PER_PAIR.getValue(), PER_PAIR_INTERVAL.getValue(), PER_PAIR_BLOCK_DURATION.getValue(), now);
            case ADDRESS -> addActiveBlocks(result, Tier.ADDRESS, addressFailures, null, addressBlocks,
                MAX_ATTEMPTS_PER_ADDRESS.getValue(), PER_ADDRESS_INTERVAL.getValue(), PER_ADDRESS_BLOCK_DURATION.getValue(), now);
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
     * Adds the tier 1 or tier 2 blocks in force to a list: every block in the blocks cache that has not ended, and
     * every count at or over a limit lowered since its last attempt (which refuses attempts until its window ends,
     * unless an attempt starts its block first). Nothing is added while the tier is off (limit 0).
     *
     * @param unknownCounts for tier 1, the counts of usernames that have no account; null for tier 2.
     */
    private static void addActiveBlocks(@Nonnull final List<ActiveBlock> result, @Nonnull final Tier tier,
                                        @Nonnull final Cache<String, FailureWindow> counts, @Nullable final Cache<String, FailureWindow> unknownCounts,
                                        @Nonnull final Cache<String, AddressBlock> blocks,
                                        final long limit, @Nonnull final Duration interval, @Nonnull final Duration blockDuration, final long now)
    {
        if (limit <= 0) {
            return;
        }
        final Set<String> blocked = new HashSet<>();
        for (final Map.Entry<String, AddressBlock> entry : blocks.entrySet()) {
            final ActiveBlock block = activeBlock(entry.getKey(), inForce(entry.getValue(), blockDuration, now), blockDuration);
            if (block != null) {
                result.add(block);
                blocked.add(entry.getKey());
            }
        }
        for (final Cache<String, FailureWindow> cache : unknownCounts == null ? List.of(counts) : List.of(counts, unknownCounts)) {
            for (final Map.Entry<String, FailureWindow> entry : cache.entrySet()) {
                // A key is listed once, even in the rare case that both counts caches hold a count for it.
                final ActiveBlock block = blocked.contains(entry.getKey()) ? null : overLimit(tier, entry.getKey(), entry.getValue(), limit, interval, now);
                if (block != null) {
                    result.add(block);
                    blocked.add(entry.getKey());
                }
            }
        }
    }

    /**
     * Returns the tier 1 block in force for a pair key: its block, if that has not ended, or else its count (from
     * whichever counts cache holds it) if that is at or over a limit lowered since its last attempt; or null if
     * neither, or tier 1 is off (limit 0). Reads nothing while tier 1 is off.
     */
    @Nullable
    private ActiveBlock activePairBlock(@Nonnull final String key, final long now)
    {
        final long limit = MAX_ATTEMPTS_PER_PAIR.getValue();
        if (limit <= 0) {
            return null;
        }
        final Duration blockDuration = PER_PAIR_BLOCK_DURATION.getValue();
        final ActiveBlock block = activeBlock(key, inForce(pairBlocks.get(key), blockDuration, now), blockDuration);
        return block != null ? block : overLimit(Tier.USERNAME_FROM_ADDRESS, key, pairCount(key, now), limit, PER_PAIR_INTERVAL.getValue(), now);
    }

    /**
     * Returns the tier 2 block in force for an address key: its block, if that has not ended, or else its count if
     * that is at or over a limit lowered since its last attempt; or null if neither, or tier 2 is off (limit 0). Reads
     * nothing while tier 2 is off.
     */
    @Nullable
    private ActiveBlock activeAddressBlock(@Nonnull final String key, final long now)
    {
        final long limit = MAX_ATTEMPTS_PER_ADDRESS.getValue();
        if (limit <= 0) {
            return null;
        }
        final Duration blockDuration = PER_ADDRESS_BLOCK_DURATION.getValue();
        final ActiveBlock block = activeBlock(key, inForce(addressBlocks.get(key), blockDuration, now), blockDuration);
        return block != null ? block : overLimit(Tier.ADDRESS, key, addressFailures.get(key), limit, PER_ADDRESS_INTERVAL.getValue(), now);
    }

    /**
     * Returns the current tier 1 count for a pair key from whichever counts cache holds one: that of usernames with an
     * account, or else that of usernames without one (see {@link #unknownPairFailures}). Null if neither holds a count
     * in a window that has not ended.
     */
    @Nullable
    private FailureWindow pairCount(@Nonnull final String key, final long now)
    {
        return currentOfEither(pairFailures, unknownPairFailures, key, PER_PAIR_INTERVAL.getValue(), now);
    }

    /**
     * Returns the current tier 3 count for a username key from whichever counts cache holds one (see
     * {@link #pairCount(String, long)}).
     */
    @Nullable
    private FailureWindow usernameCount(@Nonnull final String key, final long now)
    {
        return currentOfEither(usernameFailures, unknownUsernameFailures, key, PER_USERNAME_INTERVAL.getValue(), now);
    }

    /**
     * Returns the count for a key in a window that has not ended from the first of two counts caches that holds one,
     * or null. A key's count is normally in only one of them; it can be in both only when its account was created or
     * deleted during the window (see {@link #isKnownAccount(String)}).
     */
    @Nullable
    private static FailureWindow currentOfEither(@Nonnull final Cache<String, FailureWindow> first, @Nonnull final Cache<String, FailureWindow> second,
                                                 @Nonnull final String key, @Nonnull final Duration interval, final long now)
    {
        final FailureWindow window = current(first.get(key), interval, now);
        return window != null ? window : current(second.get(key), interval, now);
    }

    /**
     * Returns a tier 1 or tier 2 block in force as it is listed, or null for null.
     */
    @Nullable
    private static ActiveBlock activeBlock(@Nonnull final String key, @Nullable final AddressBlock block, @Nonnull final Duration blockDuration)
    {
        if (block == null) {
            return null;
        }
        return new ActiveBlock(block.tier, block.display, block.truncated, block.address, block.failures,
            Instant.ofEpochMilli(block.end(blockDuration)), key);
    }

    /**
     * Returns the block that a tier 1 or tier 2 count at or over its limit represents, ending with its window, or null
     * if the count is below the limit, its window has ended, or the key is not a key of the tier. A count reaches its
     * limit without a block only when the limit was lowered after the count's last attempt; the next attempt then
     * starts the block.
     */
    @Nullable
    private static ActiveBlock overLimit(@Nonnull final Tier tier, @Nonnull final String key, @Nullable final FailureWindow entry,
                                         final long limit, @Nonnull final Duration interval, final long now)
    {
        final FailureWindow window = current(entry, interval, now);
        if (window == null || window.count < limit) {
            return null;
        }
        final String address;
        if (tier == Tier.USERNAME_FROM_ADDRESS) {
            // The key holds the address and the username's digest; the count holds the username to show.
            final int separator = key.indexOf(PAIR_KEY_SEPARATOR);
            if (separator < 0) {
                return null;
            }
            address = key.substring(0, separator);
        } else {
            address = key;
        }
        return new ActiveBlock(tier, window.display, window.truncated, address, window.count, Instant.ofEpochMilli(window.start + interval.toMillis()), key);
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
     * Clears the count and any block of one key, so that counting for that key starts again from zero, whether or not
     * the count has reached its limit. Does nothing if the key has neither. This does not write an audit entry. Used by
     * tests that share this class's singleton, to leave no counts behind; the admin console clears blocks through
     * {@link #clearBlocksForUsername(String)} and {@link #clearBlocksForAddress(String)}.
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
            case USERNAME_FROM_ADDRESS -> {
                final String key = pairKey(usernameKey(normalise(require(username, "username"))), addressKey(require(address, "address")));
                removeUnderLock(pairFailures.getLock(key), key, pairFailures, unknownPairFailures, pairBlocks);
            }
            case ADDRESS -> {
                final String key = addressKey(require(address, "address"));
                removeUnderLock(addressFailures.getLock(key), key, addressFailures, addressBlocks);
            }
            case USERNAME -> clearUsername(usernameKey(normalise(require(username, "username"))));
        }
    }

    /**
     * Lifts the block in force with the given tier and key (from {@link ActiveBlock#key()}) and clears its count. Only
     * that key's entries are read, under its lock. Nothing is cleared when no such block is in force, for example
     * because it has ended since it was listed. This does not write an audit entry; the caller records who cleared it.
     *
     * @param tier the tier of the block.
     * @param key the key of the block, from {@link ActiveBlock#key()}.
     * @return the block that was lifted, or empty if no block with that tier and key was in force.
     */
    @Nonnull
    Optional<ActiveBlock> clearActiveBlock(@Nonnull final Tier tier, @Nonnull final String key)
    {
        if (!ENABLED.getValue()) {
            return Optional.empty();
        }
        // Each tier's count lock guards both the key's count and its block.
        final Lock lock = switch (tier) {
            case USERNAME_FROM_ADDRESS -> pairFailures.getLock(key);
            case ADDRESS -> addressFailures.getLock(key);
            case USERNAME -> usernameFailures.getLock(key);
        };
        lock.lock();
        try {
            final long now = clock.millis();
            final ActiveBlock block = switch (tier) {
                case USERNAME_FROM_ADDRESS -> activePairBlock(key, now);
                case ADDRESS -> activeAddressBlock(key, now);
                case USERNAME -> usernameBlock(key, usernameBlocks.get(key), now);
            };
            if (block == null) {
                return Optional.empty();
            }
            switch (tier) {
                case USERNAME_FROM_ADDRESS -> {
                    pairFailures.remove(key);
                    unknownPairFailures.remove(key);
                    pairBlocks.remove(key);
                }
                case ADDRESS -> {
                    addressFailures.remove(key);
                    addressBlocks.remove(key);
                }
                case USERNAME -> {
                    usernameFailures.remove(key);
                    unknownUsernameFailures.remove(key);
                    usernameBlocks.remove(key);
                }
            }
            return Optional.of(block);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns every block in force for one username, for the user's lockout page: its tier 3 block (from any IP
     * address), its tier 1 blocks (one per IP address, the latest to end first, at most the given number, with the
     * total), and the tier 2 blocks (IP address blocks) that recorded refusing it (see
     * {@link BlocksForAddress#refusedUsernames()}), bounded in the same way. The username is normalised as a login
     * attempt's is, so 'Alice' and 'alice@' followed by this server's domain find the same blocks.
     *
     * Tier 1 counts at or over a limit lowered since their last attempt are included, as they refuse the username
     * from their address until their window ends. The result also says whether failures are counted for the username
     * (tier 1 or tier 3) in a window that has not ended, which costs a read of each of the username's tier 1 counts
     * and of its tier 3 count. Usernames are given in the form shown to administrators (see
     * {@link ActiveBlock}). Nothing is returned while the feature is disabled, for a tier that is off (limit 0), or for
     * a username too long to name an account.
     *
     * Each call reads every key of the tier 1 blocks and counts caches (tier 1 keys start with the address, so a
     * username's keys cannot be looked up directly) and every entry of the tier 2 blocks cache, which in a cluster
     * means reading from other nodes; it is meant for an administrator's page, not for use on each login.
     *
     * @param username the username, as given by the client or as the account's name.
     * @param max the most tier 1 blocks, and the most tier 2 blocks, to return.
     * @return the username's blocks; the lists are immutable.
     */
    @Nonnull
    public BlocksForUsername getBlocksForUsername(@Nonnull final String username, final int max)
    {
        final BlocksForUsername none = new BlocksForUsername(null, 0, List.of(), 0, List.of(), false);
        if (!ENABLED.getValue()) {
            return none;
        }
        final String user = normalise(username);
        if (!isAccountName(user)) {
            return none;
        }
        final String userKey = usernameKey(user);
        final long now = clock.millis();
        final ActiveBlock fromAnyAddress = usernameBlock(userKey, usernameBlocks.get(userKey), now);

        boolean counted = MAX_ATTEMPTS_PER_USERNAME.getValue() > 0 && usernameCount(userKey, now) != null;

        final List<ActiveBlock> fromAddresses = new ArrayList<>();
        final String suffix = PAIR_KEY_SEPARATOR + userKey;
        for (final String key : pairKeys(key -> key.endsWith(suffix))) {
            final ActiveBlock block = activePairBlock(key, now);
            if (block != null) {
                fromAddresses.add(block);
            } else if (!counted) {
                counted = pairCount(key, now) != null;
            }
        }

        final List<ActiveBlock> refusing = new ArrayList<>();
        if (MAX_ATTEMPTS_PER_ADDRESS.getValue() > 0) {
            final Duration blockDuration = PER_ADDRESS_BLOCK_DURATION.getValue();
            for (final Map.Entry<String, AddressBlock> entry : addressBlocks.entrySet()) {
                final AddressBlock block = inForce(entry.getValue(), blockDuration, now);
                if (block != null && block.refused.stream().anyMatch(refused -> userKey.equals(refused.key()))) {
                    addIfPresent(refusing, activeBlock(entry.getKey(), block, blockDuration));
                }
            }
        }
        return new BlocksForUsername(fromAnyAddress, fromAddresses.size(), latestEndingFirst(fromAddresses, max),
            refusing.size(), latestEndingFirst(refusing, max), counted);
    }

    /**
     * Returns the tier 1 and tier 2 blocks in force grouped by IP address, for the Login Limits page: for each address,
     * its tier 2 block (refusing every username) if it has one, with the usernames that block most recently refused,
     * and its tier 1 blocks (refusing one username each). Both are returned when an address has both, as a tier 1
     * block can outlast the tier 2 block. Addresses are ordered by the latest end of their blocks, latest first, and
     * bounded, as are the tier 1 blocks of each address (the latest to end first); the totals are given.
     *
     * Counts at or over a limit lowered since their last attempt are included, as they refuse attempts until their
     * window ends, and usernames are given in the form shown to administrators (shortened to 64 characters, and marked
     * when shortened). Nothing is returned while the feature is disabled, and a tier that is off (limit 0) contributes
     * nothing.
     *
     * Each call reads every entry of the tier 1 and tier 2 blocks and counts caches, which in a cluster means reading
     * from other nodes; it is meant for an administrator's page, not for use on each login.
     *
     * @param maxAddresses the most IP addresses to return.
     * @param maxUsernamesPerAddress the most tier 1 blocks to return for each IP address.
     * @return the blocks, grouped by IP address; the lists are immutable.
     */
    @Nonnull
    public BlocksByAddress getBlocksByAddress(final int maxAddresses, final int maxUsernamesPerAddress)
    {
        final Map<String, List<ActiveBlock>> pairBlocksByAddress = new HashMap<>();
        for (final ActiveBlock block : activeBlocks(Tier.USERNAME_FROM_ADDRESS)) {
            pairBlocksByAddress.computeIfAbsent(block.address(), address -> new ArrayList<>()).add(block);
        }
        final Map<String, ActiveBlock> addressBlockByAddress = new HashMap<>();
        for (final ActiveBlock block : activeBlocks(Tier.ADDRESS)) {
            addressBlockByAddress.put(block.address(), block);
        }
        final Map<String, Instant> latestEnds = new HashMap<>();
        pairBlocksByAddress.forEach((address, blocks) -> blocks.forEach(block -> latestEnds.merge(address, block.endsAt(), UserLoginLimitManager::later)));
        addressBlockByAddress.forEach((address, block) -> latestEnds.merge(address, block.endsAt(), UserLoginLimitManager::later));

        final long now = clock.millis();
        final Duration addressBlockDuration = PER_ADDRESS_BLOCK_DURATION.getValue();
        final List<BlocksForAddress> addresses = latestEnds.entrySet().stream()
            .sorted(Map.Entry.<String, Instant>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(Math.max(0, maxAddresses))
            .map(entry -> {
                final String address = entry.getKey();
                final ActiveBlock addressBlock = addressBlockByAddress.get(address);
                // A count over a lowered limit has no stored block, and so no refused usernames.
                final AddressBlock stored = addressBlock == null ? null : inForce(addressBlocks.get(addressBlock.key()), addressBlockDuration, now);
                final List<ActiveBlock> usernames = pairBlocksByAddress.getOrDefault(address, List.of());
                return new BlocksForAddress(address, addressBlock, stored == null ? List.of() : stored.refused,
                    usernames.size(), latestEndingFirst(usernames, maxUsernamesPerAddress), entry.getValue());
            })
            .toList();
        return new BlocksByAddress(latestEnds.size(), addresses);
    }

    /**
     * Clears the login limits for one username, for the user's lockout page, so that the user starts afresh: its tier 1
     * block and count from each IP address, and its tier 3 block and count, whether or not a count has reached its
     * limit. If the account has a LockOutManager lock that the login limits set (see
     * {@link #isLockSetByLoginLimits(LockOutFlag)}), the lock is lifted too; a lock set by hand never is. If that lock
     * cannot be lifted (or the account's lock cannot be read), the tier 3 block and count are kept, so that the lock is
     * still recognised as set by the login limits, and the result says so. Tier 2 blocks and counts, which concern
     * everyone at their address, are left alone (see {@link #clearBlocksForAddress(String)}).
     *
     * Each key is cleared under its lock. Finding the tier 1 keys reads every key of the tier 1 blocks and counts
     * caches. This does not write an audit entry; the caller records who cleared the login limits. Nothing is cleared
     * while the feature is disabled.
     *
     * @param username the username, as given by the client or as the account's name.
     * @return the blocks that were lifted, whether counts were reset, and whether the account's lock was lifted.
     */
    @Nonnull
    public ClearedBlocks clearBlocksForUsername(@Nonnull final String username)
    {
        final ClearedBlocks none = new ClearedBlocks(List.of(), false, false, false);
        if (!ENABLED.getValue()) {
            return none;
        }
        final String user = normalise(username);
        if (!isAccountName(user)) {
            return none;
        }
        final String userKey = usernameKey(user);
        final List<ActiveBlock> cleared = new ArrayList<>();
        boolean countsReset = false;
        final String suffix = PAIR_KEY_SEPARATOR + userKey;
        for (final String key : pairKeys(key -> key.endsWith(suffix))) {
            countsReset |= resetPair(key, cleared);
        }

        final LockLift lift = clearUsernameBlockAndLock(user, userKey, cleared);
        return new ClearedBlocks(List.copyOf(cleared), countsReset || lift.countReset(), lift.lifted(), lift.failed());
    }

    /**
     * Unlocks an account for an administrator. A lock that the login limits set (see
     * {@link #isLockSetByLoginLimits(LockOutFlag)}) is lifted by {@link #clearBlocksForUsername(String)}, so that the
     * user starts afresh as after Clear login limits: without that, the user's counts from each IP address would
     * survive the unlock, and one more wrong password from such an address could block the user again at once. Any
     * other lock is lifted through LockOutManager alone, as an unlock always has been; the login limits' counts and
     * blocks from each IP address are left as they are (LockOutManager's unlock event still clears the tier 3 count,
     * see {@link #accountUnlocked(String)}). Tier 2 blocks are never touched. The admin console's Unlock account and
     * the Re-Enable a User ad-hoc command unlock through this.
     *
     * If the lock is no longer recognised as set by the login limits by the time they are cleared (its tier 3 block
     * ended in between), the lock is lifted through LockOutManager after the clear.
     *
     * This does not write an audit entry; the caller records who unlocked the account.
     *
     * @param username the account's username.
     * @return what was cleared when the login limits set the lock, including whether lifting the lock failed; empty
     * for any other lock, which has been lifted.
     * @throws UnsupportedOperationException if the lockout provider is read-only and the lock was not set by the login
     * limits.
     */
    @Nonnull
    public Optional<ClearedBlocks> unlockAccount(@Nonnull final String username)
    {
        final LockOutFlag flag = lockOutManager.get().getDisabledStatus(username);
        if (flag != null && isLockSetByLoginLimits(flag)) {
            final ClearedBlocks cleared = clearBlocksForUsername(username);
            if (!cleared.lockLifted() && !cleared.lockLiftFailed()) {
                lockOutManager.get().enableAccount(username);
            }
            return Optional.of(cleared);
        }
        lockOutManager.get().enableAccount(username);
        return Optional.empty();
    }

    /**
     * Clears a tier 1 key's count and block under the key's lock, adding the block to the list if it was in force.
     *
     * @return whether a count in a window that had not ended was removed.
     */
    private boolean resetPair(@Nonnull final String key, @Nonnull final List<ActiveBlock> cleared)
    {
        final Lock lock = pairFailures.getLock(key);
        lock.lock();
        try {
            final long now = clock.millis();
            addIfPresent(cleared, activePairBlock(key, now));
            pairBlocks.remove(key);
            final boolean counted = current(pairFailures.remove(key), PER_PAIR_INTERVAL.getValue(), now) != null;
            return current(unknownPairFailures.remove(key), PER_PAIR_INTERVAL.getValue(), now) != null || counted;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears a username's tier 3 block and count, and lifts the account's lock if the login limits set it. If that lock
     * cannot be checked or lifted, the block and count are kept, so that the lock is still recognised as set by the
     * login limits.
     *
     * @param user the normalised username.
     * @param userKey the username's key.
     * @param cleared the list to add the lifted block to.
     * @return whether the lock was lifted, whether checking or lifting it failed, and whether the count was reset.
     */
    @Nonnull
    private LockLift clearUsernameBlockAndLock(@Nonnull final String user, @Nonnull final String userKey, @Nonnull final List<ActiveBlock> cleared)
    {
        boolean lockLifted = false;
        final ActiveBlock usernameBlock = usernameBlock(userKey, usernameBlocks.get(userKey), clock.millis());
        if (usernameBlock != null) {
            try {
                final LockOutFlag flag = lockOutManager.get().getDisabledStatus(user);
                if (flag != null && isLockSetByLoginLimits(flag)) {
                    // Lifting the lock tells this manager's unlock listener, which clears the tier 3 block and count.
                    lockOutManager.get().enableAccount(user);
                    lockLifted = true;
                }
            } catch (final RuntimeException e) {
                Log.warn("Unable to check or lift the lock on account {}; keeping its block from any IP address", logName(user), e);
                return new LockLift(false, true, false);
            }
        }
        final Lock lock = usernameFailures.getLock(userKey);
        final ActiveBlock removed;
        final boolean countReset;
        lock.lock();
        try {
            final long now = clock.millis();
            removed = usernameBlock(userKey, usernameBlocks.remove(userKey), now);
            final boolean counted = current(usernameFailures.remove(userKey), PER_USERNAME_INTERVAL.getValue(), now) != null;
            countReset = current(unknownUsernameFailures.remove(userKey), PER_USERNAME_INTERVAL.getValue(), now) != null || counted;
        } finally {
            lock.unlock();
        }
        if (removed != null) {
            cleared.add(removed);
        } else if (lockLifted) {
            // The unlock listener cleared the block first.
            cleared.add(usernameBlock);
        }
        return new LockLift(lockLifted, false, countReset);
    }

    /**
     * Whether a lock set by the login limits was lifted, whether checking or lifting it failed, and whether the tier 3
     * count was reset.
     */
    private record LockLift(boolean lifted, boolean failed, boolean countReset) {}

    /**
     * Lifts every block for one IP address, for the Login Limits page: its tier 2 block and its tier 1 blocks, for
     * every username, with their counts. The address may be given as listed (an IPv6 address as its /64) or as any
     * address, which is reduced to its key as an attempt's is. Blocks of other addresses are left alone.
     *
     * Each block is cleared under its key's lock, and only blocks in force are cleared. Finding the tier 1 blocks reads
     * every key of the tier 1 blocks and counts caches. This does not write an audit entry; the caller records who
     * cleared the blocks. Nothing is cleared while the feature is disabled.
     *
     * @param address the IP address.
     * @return the blocks that were lifted; an immutable list.
     */
    @Nonnull
    public List<ActiveBlock> clearBlocksForAddress(@Nonnull final String address)
    {
        if (!ENABLED.getValue()) {
            return List.of();
        }
        final String addressKey = addressKey(address);
        final List<ActiveBlock> cleared = new ArrayList<>();
        clearActiveBlock(Tier.ADDRESS, addressKey).ifPresent(cleared::add);
        final String prefix = addressKey + PAIR_KEY_SEPARATOR;
        for (final String key : pairKeys(key -> key.startsWith(prefix))) {
            clearActiveBlock(Tier.USERNAME_FROM_ADDRESS, key).ifPresent(cleared::add);
        }
        return List.copyOf(cleared);
    }

    /**
     * Returns the tier 1 keys, of blocks or of counts, that match a predicate; none while tier 1 is off. Reads only the
     * keys of the caches.
     */
    @Nonnull
    private Set<String> pairKeys(@Nonnull final Predicate<String> matches)
    {
        final Set<String> keys = new HashSet<>();
        if (MAX_ATTEMPTS_PER_PAIR.getValue() <= 0) {
            return keys;
        }
        for (final String key : pairBlocks.keySet()) {
            if (matches.test(key)) {
                keys.add(key);
            }
        }
        for (final String key : pairFailures.keySet()) {
            if (matches.test(key)) {
                keys.add(key);
            }
        }
        for (final String key : unknownPairFailures.keySet()) {
            if (matches.test(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Returns at most the given number of blocks, the latest to end first, as an immutable list.
     */
    @Nonnull
    private static List<ActiveBlock> latestEndingFirst(@Nonnull final List<ActiveBlock> blocks, final int max)
    {
        return blocks.stream()
            .sorted(Comparator.comparing(ActiveBlock::endsAt).reversed())
            .limit(Math.max(0, max))
            .toList();
    }

    @Nonnull
    private static Instant later(@Nonnull final Instant a, @Nonnull final Instant b)
    {
        return a.isAfter(b) ? a : b;
    }

    /**
     * Clears the tier 3 count (in either counts cache) and block for a username key, under the key's lock.
     */
    private void clearUsername(@Nonnull final String userKey)
    {
        removeUnderLock(usernameFailures.getLock(userKey), userKey, usernameFailures, unknownUsernameFailures, usernameBlocks);
    }

    /**
     * Removes a key from each of the given caches (a tier's counts and blocks) under the key's lock, so that a
     * concurrent attempt cannot write back a count or block that is being cleared.
     */
    @SafeVarargs
    private static void removeUnderLock(@Nonnull final Lock lock, @Nonnull final String key, @Nonnull final Cache<String, ?>... caches)
    {
        lock.lock();
        try {
            for (final Cache<String, ?> cache : caches) {
                cache.remove(key);
            }
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
     * @param key identifies the block (the key of its count and block in the caches); not meant for display.
     */
    public record ActiveBlock(@Nonnull Tier tier, @Nullable String username, boolean usernameTruncated, @Nullable String address, long failureCount, @Nonnull Instant endsAt, @Nonnull String key) {}

    /**
     * The blocks in force for one username, as returned by {@link #getBlocksForUsername(String, int)}.
     *
     * @param fromAnyAddress the tier 3 block, which refuses the username from every IP address, or null.
     * @param fromAddressTotal how many tier 1 blocks (one per IP address) the username has.
     * @param fromAddresses at most the requested number of them, the latest to end first.
     * @param refusingAddressTotal how many tier 2 blocks in force recorded refusing the username.
     * @param refusingAddresses at most the requested number of them, the latest to end first.
     * @param counted whether failed attempts are counted for the username, from one IP address or from any, in a window
     *                that has not ended; {@link #clearBlocksForUsername(String)} resets those counts.
     */
    public record BlocksForUsername(@Nullable ActiveBlock fromAnyAddress, int fromAddressTotal, @Nonnull List<ActiveBlock> fromAddresses,
                                    int refusingAddressTotal, @Nonnull List<ActiveBlock> refusingAddresses, boolean counted) {}

    /**
     * The blocks in force for one IP address, as returned by {@link #getBlocksByAddress(int, int)}.
     *
     * @param address the IP address (an IPv6 address as its /64); pass it to {@link #clearBlocksForAddress(String)}.
     * @param addressBlock the tier 2 block, which refuses every username from the address, or null.
     * @param refusedUsernames the usernames the tier 2 block most recently refused, newest first, at most
     *                         {@link #MAX_REFUSED_USERNAMES}; empty without a tier 2 block.
     * @param usernameTotal how many tier 1 blocks (one per username) the address has.
     * @param usernameBlocks at most the requested number of them, the latest to end first.
     * @param latestEnd when the last of the address's blocks ends.
     */
    public record BlocksForAddress(@Nonnull String address, @Nullable ActiveBlock addressBlock, @Nonnull List<RefusedUsername> refusedUsernames,
                                   int usernameTotal, @Nonnull List<ActiveBlock> usernameBlocks, @Nonnull Instant latestEnd) {}

    /**
     * The blocks in force grouped by IP address, as returned by {@link #getBlocksByAddress(int, int)}.
     *
     * @param total how many IP addresses have a block in force.
     * @param addresses at most the requested number of them, the latest block to end first.
     */
    public record BlocksByAddress(int total, @Nonnull List<BlocksForAddress> addresses) {}

    /**
     * A username that a tier 2 block refused, recorded on the block.
     *
     * @param key identifies the username (see {@link #getBlocksForUsername(String, int)}), or null for a username too
     *            long to name an account; not meant for display.
     * @param username the normalised username, shortened to 64 characters for display. It comes from a client, so it
     *                 must be escaped wherever it is shown.
     * @param usernameTruncated whether the username was shortened for display.
     */
    public record RefusedUsername(@Nullable String key, @Nonnull String username, boolean usernameTruncated) implements Serializable {}

    /**
     * What {@link #clearBlocksForUsername(String)} lifted.
     *
     * @param blocks the blocks that were lifted.
     * @param countsReset whether failed attempts counted for the username, in a window that had not ended, were reset.
     * @param lockLifted whether a LockOutManager lock set by the login limits was lifted.
     * @param lockLiftFailed whether the account's lock could not be checked or lifted, in which case the tier 3 block
     *                       was kept.
     */
    public record ClearedBlocks(@Nonnull List<ActiveBlock> blocks, boolean countsReset, boolean lockLifted, boolean lockLiftFailed) {}

    /**
     * Marks a tier in a {@link Reservation} that did not count the attempt.
     */
    private static final long NOT_COUNTED = -1;

    /**
     * An admitted attempt, as returned by {@link #tryAcquireAttempt(String, String)}: the keys it was counted under
     * and the start of each window it was counted in, so that a success gives it back to those windows only.
     */
    public static final class Reservation implements Serializable
    {
        private static final long serialVersionUID = 2L;

        private final String pairKey;
        private final String addressKey;
        private final long addressStart;
        private final String usernameKey;
        private final long usernameStart;

        /**
         * Whether the attempt was counted in the counts caches of usernames that have an account, rather than in
         * those of usernames that have none (see {@link UserLoginLimitManager#isKnownAccount(String)}).
         */
        private final boolean knownAccount;

        Reservation(@Nullable final String pairKey, @Nullable final String addressKey, final long addressStart,
                    @Nullable final String usernameKey, final long usernameStart, final boolean knownAccount)
        {
            this.pairKey = pairKey;
            this.addressKey = addressKey;
            this.addressStart = addressStart;
            this.usernameKey = usernameKey;
            this.usernameStart = usernameStart;
            this.knownAccount = knownAccount;
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
     * Returns a counter if its window is still open, or null if there is none or its window has ended, after which its
     * key starts afresh. The interval is the current setting, so a change applies to counters already in force.
     */
    @Nullable
    private static FailureWindow current(@Nullable final FailureWindow window, @Nonnull final Duration interval, final long now)
    {
        return window == null || window.hasEnded(interval, now) ? null : window;
    }

    /**
     * Returns a tier 1 or tier 2 block if it is in force, or null if there is none or it has ended. The block lasts
     * for the tier's block duration from the attempt that started it; the duration is the current setting, so a change
     * applies to blocks already in force.
     */
    @Nullable
    private static AddressBlock inForce(@Nullable final AddressBlock block, @Nonnull final Duration blockDuration, final long now)
    {
        return block == null || now >= block.end(blockDuration) ? null : block;
    }

    /**
     * Starts a tier 1 or tier 2 block now, for a count that has reached the tier's limit: the block is stored in the
     * tier's blocks cache, and the count is removed, so that counting starts again from zero when the block ends. The
     * block keeps the count's window start and failures, so that a success reported for an attempt counted in that
     * window can still be given back (see {@link #giveBack}). The caller holds the key's lock.
     *
     * @param refused for a tier 2 block started by the attempt it refuses, that attempt's username; otherwise empty.
     */
    private static void startBlock(@Nonnull final Cache<String, FailureWindow> counts, @Nonnull final Cache<String, AddressBlock> blocks,
                                   @Nonnull final FailureWindow window, @Nonnull final Tier tier, @Nonnull final String address, final long now,
                                   @Nonnull final List<RefusedUsername> refused)
    {
        blocks.put(window.key, new AddressBlock(window.key, tier, window.display, window.truncated, address, window.start, now, window.count, refused));
        counts.remove(window.key);
    }

    /**
     * Records on a tier 2 block in force that it refused an attempt for a username: the username becomes the newest of
     * the block's refused usernames, appearing once, and the oldest is dropped beyond {@link #MAX_REFUSED_USERNAMES}.
     * Nothing is written when the username is already the newest, so that a client retrying one username from a blocked
     * address does not write the block (a cluster-wide put) on each attempt. The caller holds the address's lock.
     */
    private static void recordRefusal(@Nonnull final Cache<String, AddressBlock> blocks, @Nonnull final AddressBlock block, @Nonnull final RefusedUsername refused)
    {
        if (!block.refused.isEmpty() && isSameUsername(block.refused.get(0), refused)) {
            return;
        }
        final List<RefusedUsername> updated = new ArrayList<>(MAX_REFUSED_USERNAMES);
        updated.add(refused);
        for (final RefusedUsername earlier : block.refused) {
            if (updated.size() >= MAX_REFUSED_USERNAMES) {
                break;
            }
            if (!isSameUsername(earlier, refused)) {
                updated.add(earlier);
            }
        }
        blocks.put(block.key, new AddressBlock(block.key, block.tier, block.display, block.truncated, block.address, block.windowStart, block.start, block.failures, updated));
    }

    /**
     * Returns whether two refused usernames are the same: by their keys where they have them, and otherwise (usernames
     * too long to name an account) by the form shown.
     */
    private static boolean isSameUsername(@Nonnull final RefusedUsername a, @Nonnull final RefusedUsername b)
    {
        if (a.key() != null || b.key() != null) {
            return a.key() != null && a.key().equals(b.key());
        }
        return a.username().equals(b.username()) && a.usernameTruncated() == b.usernameTruncated();
    }

    private static long count(@Nullable final FailureWindow window)
    {
        return window == null ? 0 : window.count;
    }

    /**
     * Counts an attempt in an open window, or starts a new window with it. If the count reaches the limit, a block
     * starts (see {@link #startBlock}); otherwise the count is stored. The caller holds the key's lock.
     *
     * @param blocks the tier's blocks cache, or null for a counter that never starts a block (tier 3).
     * @param address the address of a tier 1 or tier 2 key, or null.
     * @param blockLimit the count at which a block starts, or 0 for a counter that never starts one (tier 3).
     * @param display the username to show for the counter, shortened for display (tier 1), or null.
     * @param truncated whether the username to show was shortened.
     * @return the window, including this attempt.
     */
    private static FailureWindow countAttempt(@Nonnull final Cache<String, FailureWindow> counts, @Nullable final Cache<String, AddressBlock> blocks,
                                              @Nonnull final Tier tier, @Nonnull final String key, @Nullable final String address,
                                              @Nullable final FailureWindow open, final long now, final long blockLimit,
                                              @Nullable final String display, final boolean truncated)
    {
        final long start = open == null ? now : open.start;
        final long count = open == null ? 1 : open.count + 1;
        final FailureWindow updated = new FailureWindow(key, start, count, display, truncated);
        if (blocks != null && address != null && blockLimit > 0 && count >= blockLimit) {
            startBlock(counts, blocks, updated, tier, address, now, List.of());
        } else {
            counts.put(key, updated); // An explicit put is needed for the change to reach other cluster nodes.
        }
        return updated;
    }

    /**
     * Gives back one attempt, under the key's lock, to the window it was counted in, if that window still applies.
     *
     * While the window is still counting, its count is reduced by one. If the window has since reached the limit and
     * started a block that is still in force, the block holds the window's failures instead (see {@link #startBlock}):
     * they are reduced by one, and if that brings them below the limit, the block is lifted and the remaining failures
     * are counted again in their window, if it is still open. A refund never reduces the count of a later window.
     *
     * @param lock the key's lock, which guards its count in either counts cache and its block.
     * @param counts the counts cache the attempt was counted in.
     * @param blocks the tier's blocks cache, or null for tier 3, whose counters never start a block.
     * @param limit the tier's limit, or 0 for tier 3.
     * @param blockDuration the tier's block duration; unused for tier 3.
     */
    private static void giveBack(@Nonnull final Lock lock, @Nonnull final Cache<String, FailureWindow> counts, @Nullable final Cache<String, AddressBlock> blocks,
                                 @Nonnull final String key, final long windowStart, final long limit,
                                 @Nonnull final Duration interval, @Nonnull final Duration blockDuration, final long now)
    {
        lock.lock();
        try {
            final FailureWindow window = current(counts.get(key), interval, now);
            if (window != null && window.start == windowStart) {
                final long count = window.count - 1;
                if (count <= 0) {
                    counts.remove(key);
                } else {
                    counts.put(key, new FailureWindow(key, window.start, count, window.display, window.truncated));
                }
                return;
            }
            if (blocks == null) {
                return;
            }
            final AddressBlock block = inForce(blocks.get(key), blockDuration, now);
            if (block == null || block.windowStart != windowStart) {
                return;
            }
            final long failures = block.failures - 1;
            if (failures >= limit) {
                blocks.put(key, new AddressBlock(key, block.tier, block.display, block.truncated, block.address, block.windowStart, block.start, failures, block.refused));
                return;
            }
            blocks.remove(key);
            if (failures > 0 && now < windowStart + interval.toMillis()) {
                counts.put(key, new FailureWindow(key, windowStart, failures, block.display, block.truncated));
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

    /**
     * Returns whether a normalised username names an existing account, which chooses the counts caches its attempt is
     * counted in for tiers 1 and 3: those of usernames that have an account, or those of usernames that have none
     * (see {@link #unknownPairFailures}). Keeping them apart means that a flood of attempts for made-up usernames can
     * fill and evict only the counts of usernames that have no account, so it cannot reset a real user's count.
     *
     * A username that is not a valid node cannot name an account, so it is treated as having none. If the lookup fails
     * in any other way, the username is treated as having an account, so that a real user's count is never put where
     * made-up usernames can evict it. User providers report most failures of the user store as the user not being
     * found, so during a store outage real users' counts go to the caches of usernames without an account; no password
     * can be checked then either, so no guess gains anything from it.
     *
     * The answer is looked up once for each attempt, when it is reserved. UserManager caches the accounts it finds but
     * not the usernames it does not, so during a flood of made-up usernames each attempt adds one user store lookup,
     * about as many as the attempts already cause. The answer is not stored: a count started before the account was
     * created or deleted stays in the cache where it started until its window ends, and later attempts are counted
     * in the other cache.
     */
    private boolean isKnownAccount(@Nonnull final String user)
    {
        try {
            return userExists.test(user);
        } catch (final IllegalArgumentException e) {
            return false;
        } catch (final RuntimeException e) {
            Log.debug("Unable to determine whether account {} exists; counting its attempts as an account's", logName(user), e);
            return true;
        }
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
     * A tier 1 or tier 2 block, stored apart from the counts (see {@link #pairBlocks}) under the key of the count that
     * reached the limit. It holds when it started rather than when it ends, so that a change to the tier's block
     * duration applies to blocks already in force.
     */
    @VisibleForTesting
    static final class AddressBlock implements Cacheable
    {
        private static final long serialVersionUID = 2L;

        /**
         * The key the block is stored under, held here so that the cached size accounts for it.
         */
        private final String key;

        /**
         * The tier of the block: {@link Tier#USERNAME_FROM_ADDRESS} or {@link Tier#ADDRESS}.
         */
        private final Tier tier;

        /**
         * For a tier 1 block: the username, shortened for display; otherwise null.
         */
        private final String display;

        /**
         * Whether {@link #display} was shortened.
         */
        private final boolean truncated;

        /**
         * The address the block applies to (an IPv6 address as its /64).
         */
        private final String address;

        /**
         * When the window whose count reached the limit started, in epoch milliseconds, so that a success reported for
         * an attempt counted in it can be given back.
         */
        private final long windowStart;

        /**
         * When the block started, in epoch milliseconds.
         */
        private final long start;

        /**
         * The number of attempts that led to the block.
         */
        private final long failures;

        /**
         * For a tier 2 block: the usernames it most recently refused, newest first, each once, at most
         * {@link #MAX_REFUSED_USERNAMES}; otherwise empty. An immutable list.
         */
        private final List<RefusedUsername> refused;

        AddressBlock(@Nonnull final String key, @Nonnull final Tier tier, @Nullable final String display, final boolean truncated,
                     @Nonnull final String address, final long windowStart, final long start, final long failures)
        {
            this(key, tier, display, truncated, address, windowStart, start, failures, List.of());
        }

        AddressBlock(@Nonnull final String key, @Nonnull final Tier tier, @Nullable final String display, final boolean truncated,
                     @Nonnull final String address, final long windowStart, final long start, final long failures,
                     @Nonnull final List<RefusedUsername> refused)
        {
            this.key = key;
            this.tier = tier;
            this.display = display;
            this.truncated = truncated;
            this.address = address;
            this.windowStart = windowStart;
            this.start = start;
            this.failures = failures;
            this.refused = List.copyOf(refused);
        }

        /**
         * Returns when the block ends, in epoch milliseconds, for the tier's current block duration.
         */
        long end(@Nonnull final Duration blockDuration)
        {
            return start + blockDuration.toMillis();
        }

        String key() { return key; }

        Tier tier() { return tier; }

        String display() { return display; }

        boolean truncated() { return truncated; }

        String address() { return address; }

        long windowStart() { return windowStart; }

        long start() { return start; }

        long failures() { return failures; }

        List<RefusedUsername> refused() { return refused; }

        @Override
        public int getCachedSize()
        {
            // The key is stored by the cache alongside this value, but caches size only the value: account for both.
            // The tier is a reference to a shared constant.
            int size = CacheSizes.sizeOfObject() + 3 * CacheSizes.sizeOfLong() + CacheSizes.sizeOfBoolean() + CacheSizes.sizeOfInt()
                + 2 * CacheSizes.sizeOfString(key) + CacheSizes.sizeOfString(address)
                + (display == null ? 0 : CacheSizes.sizeOfString(display))
                + CacheSizes.sizeOfObject(); // The list of refused usernames.
            for (final RefusedUsername username : refused) {
                size += CacheSizes.sizeOfObject() + CacheSizes.sizeOfString(username.key()) + CacheSizes.sizeOfString(username.username()) + CacheSizes.sizeOfBoolean();
            }
            return size;
        }
    }

    /**
     * The failures counted for one key in one fixed window. A tier 1 or tier 2 count that reaches its limit is
     * replaced by an {@link AddressBlock}.
     */
    @VisibleForTesting
    static final class FailureWindow implements Cacheable
    {
        private static final long serialVersionUID = 3L;

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
         * For a tier 1 counter, whose key holds the username's digest: the username, shortened for display; otherwise
         * null.
         */
        private final String display;

        /**
         * Whether {@link #display} was shortened.
         */
        private final boolean truncated;

        FailureWindow(@Nonnull final String key, final long start, final long count, @Nullable final String display, final boolean truncated)
        {
            this.key = key;
            this.start = start;
            this.count = count;
            this.display = display;
            this.truncated = truncated;
        }

        String key() { return key; }

        long start() { return start; }

        long count() { return count; }

        String display() { return display; }

        boolean truncated() { return truncated; }

        boolean hasEnded(@Nonnull final Duration interval, final long now)
        {
            return now >= start + interval.toMillis();
        }

        @Override
        public int getCachedSize()
        {
            // The key is stored by the cache alongside this value, but caches size only the value: account for both.
            return CacheSizes.sizeOfObject() + 2 * CacheSizes.sizeOfLong() + CacheSizes.sizeOfBoolean() + 2 * CacheSizes.sizeOfString(key)
                + (display == null ? 0 : CacheSizes.sizeOfString(display));
        }
    }
}
