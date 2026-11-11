/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.lockout;

import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * The LockOutManager manages the LockOutProvider configured for this server, caches knowledge of
 * whether accounts are disabled or enabled, and provides a single point of entry for handling
 * locked/disabled accounts.
 *
 * Unless the provider says that its data should not be cached, both outcomes of a lookup are cached, as one entry per
 * account: the lock of a locked account, or the fact that an account is not locked. A lock set or lifted through this
 * class replaces the cached entry at once, across a cluster. A lock set or lifted outside Openfire (directly in the
 * provider's store) is seen only when the cached entry expires, which can take up to the lifetime of the cache.
 *
 * The provider can be specified in system properties by adding:
 *
 * <ul>
 * <li>{@code provider.lockout.className = my.lock.out.provider}</li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class LockOutManager {

    /**
     * The name of the cache of what is known of each account's lock: its lock, or that it has none.
     *
     * This must not be "Locked Out Accounts", the name earlier versions use for a cache whose values are
     * {@code LockOutFlag}s. This cache's values are {@code CacheableOptional<LockOutFlag>}s, and during a rolling
     * upgrade nodes of both versions would share one cache and fail with a {@code ClassCastException} reading each
     * other's entries.
     */
    public static final String LOCKED_OUT_CACHE_NAME = "Account Lock States";

    public static final SystemProperty<Class> LOCKOUT_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.lockout.className")
        .setBaseClass(LockOutProvider.class)
        .setDefaultValue(DefaultLockOutProvider.class)
        .addListener(LockOutManager::initProvider)
        .setDynamic(true)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(LockOutManager.class);

    // Wrap this guy up so we can mock out the LockOutManager class.
    private static class LockOutManagerContainer {
        private static LockOutManager instance = new LockOutManager();
    }

    /**
     * Returns the currently-installed LockOutProvider. <b>Warning:</b> in virtually all
     * cases the lockout provider should not be used directly. Instead, the appropriate
     * methods in LockOutManager should be called. Direct access to the lockout provider is
     * only provided for special-case logic.
     *
     * @return the current LockOutProvider.
     */
    public static LockOutProvider getLockOutProvider() {
        return LockOutManagerContainer.instance.provider;
    }

    /**
     * Returns a singleton instance of LockOutManager.
     *
     * @return a LockOutManager instance.
     */
    public static LockOutManager getInstance() {
        return LockOutManagerContainer.instance;
    }

    /**
     * Cache of what is known of each account's lock: a present value is the account's lock, and an absent value means
     * the account was found not to be locked, so that looking it up again needs no query. An account with no entry has
     * not been looked up since its entry expired.
     */
    private final Cache<String,CacheableOptional<LockOutFlag>> lockOutCache;

    private final Supplier<LockOutProvider> providerSupplier;

    private static LockOutProvider provider;

    /**
     * Constructs a LockOutManager, setting up it's cache, property listener, and setting up the provider.
     */
    private LockOutManager() {
        this(CacheFactory.createCache(LOCKED_OUT_CACHE_NAME), () -> provider);

        // Load an lockout provider.
        initProvider(LOCKOUT_PROVIDER.getValue());
    }

    /**
     * Constructs a LockOutManager with the cache and provider it uses (for tests).
     *
     * @param lockOutCache the cache of what is known of each account's lock.
     * @param providerSupplier supplies the lockout provider to use.
     */
    LockOutManager(final Cache<String,CacheableOptional<LockOutFlag>> lockOutCache, final Supplier<LockOutProvider> providerSupplier) {
        this.lockOutCache = lockOutCache;
        this.providerSupplier = providerSupplier;
    }

    /**
     * Initializes the server's lock out provider, based on configuration and defaults to
     * DefaultLockOutProvider if the specified provider is not valid or not specified.
     */
    private static void initProvider(final Class<? extends LockOutProvider> clazz) {
        if (provider == null || !clazz.equals(provider.getClass())) {
            try {
                provider = clazz.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading lockout provider: " + clazz.getName(), e);
                provider = new DefaultLockOutProvider();
            }
        }
    }

    /**
     * Returns a LockOutFlag for a given username, which contains information about the time
     * period that the specified account is going to be disabled.
     *
     * @param username Username of account to request status of.
     * @return The LockOutFlag instance describing the accounts disabled status or null if user
     *         account specified is not currently locked out (disabled).
     */
    public LockOutFlag getDisabledStatus(String username) {
        if (username == null) {
            throw new UnsupportedOperationException("Null username not allowed!");
        }
        LockOutFlag lockOutFlag = getUserLockOut(username);
        return getUnExpiredLockout(lockOutFlag);
    }

    /**
     * Returns true or false if an account is currently locked out.
     *
     * @param username Username of account to check on.
     * @return True or false if the account is currently locked out.
     */
    public boolean isAccountDisabled(String username) {
        LockOutFlag flag = getDisabledStatus(username);
        if (flag == null) {
            return false;
        }
        Date curDate = new Date();
        if (flag.getStartTime() != null && curDate.before(flag.getStartTime())) {
            return false;
        }
        if (flag.getEndTime() != null && curDate.after(flag.getEndTime())) {
            return false;
        }
        return true;
    }

    /**
     * Sets an account to disabled, starting at an optional time and ending at an optional time.
     * If either times are set to null, the lockout is considered "forever" in that direction.
     * For example, if you had a start time of 2 hours from now, and a null end time, then the account
     * would be locked out in two hours, and never unlocked until someone manually unlcoked the account.
     *
     * @param username User whose account will be disabled.
     * @param startTime When to start the lockout, or null if immediately.
     * @param endTime When to end the lockout, or null if forever.
     * @throws UnsupportedOperationException if the provider is readonly.
     */
    public void disableAccount(String username, Date startTime, Date endTime) throws UnsupportedOperationException {
        final LockOutProvider provider = providerSupplier.get();
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException();
        }
        LockOutFlag flag = new LockOutFlag(username, startTime, endTime);
        if (provider.shouldNotBeCached()) {
            provider.setDisabledStatus(flag);
        } else {
            // Under the account's cluster lock, so that a lookup cannot cache 'not locked' from a read made before
            // the lock was stored.
            final Lock lock = lockOutCache.getLock(username);
            lock.lock();
            try {
                provider.setDisabledStatus(flag);
                lockOutCache.put(username, CacheableOptional.of(flag));
            } finally {
                lock.unlock();
            }
        }
        // Fire event.
        LockOutEventDispatcher.accountLocked(flag);
    }

    /**
     * Enables an account that may or may not have previously been disabled.  This erases any
     * knowledge of a lockout, including one that wasn't necessarily in effect at the time the
     * method was called.
     *
     * @param username User to enable.
     * @throws UnsupportedOperationException if the provider is readonly.
     */
    public void enableAccount(String username) throws UnsupportedOperationException {
        final LockOutProvider provider = providerSupplier.get();
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException();
        }
        if (provider.shouldNotBeCached()) {
            provider.unsetDisabledStatus(username);
        } else {
            // Under the account's cluster lock, so that a lookup cannot cache a lock read before it was lifted.
            final Lock lock = lockOutCache.getLock(username);
            lock.lock();
            try {
                provider.unsetDisabledStatus(username);
                lockOutCache.put(username, CacheableOptional.of(null));
            } finally {
                lock.unlock();
            }
        }
        // Fire event.
        LockOutEventDispatcher.accountUnlocked(username);
    }

    /**
     * "Records" (notifies all listeners) that a failed login occurred.
     *
     * @param username Locked out user that attempted to login.
     */
    public void recordFailedLogin(String username) {
        // Fire event.
        LockOutEventDispatcher.lockedAccountDenied(username);
    }

    /**
     * Gets the user lock out, from the cache where possible.
     *
     * An answer from the cache, either way, takes no lock. A lookup that has to query the provider does so under the
     * account's cluster lock, as do {@link #disableAccount(String, Date, Date)} and {@link #enableAccount(String)},
     * so that what it caches cannot overwrite or outlive a lock set or lifted while it was querying, on any node.
     *
     * @param username
     *            the username
     * @return the user lock out, or null if the account has none
     */
    private LockOutFlag getUserLockOut(String username) {
        final LockOutProvider provider = providerSupplier.get();
        if (provider.shouldNotBeCached()) {
            return provider.getDisabledStatus(username);
        }
        final CacheableOptional<LockOutFlag> cached = lockOutCache.get(username);
        if (cached != null) {
            return cached.get();
        }
        // A username that cannot name an account is refused, as it always has been, rather than cached.
        JID.nodeprep(username);
        final Lock lock = lockOutCache.getLock(username);
        lock.lock();
        try {
            final CacheableOptional<LockOutFlag> loaded = lockOutCache.get(username);
            if (loaded != null) {
                return loaded.get();
            }
            final LockOutFlag flag = provider.getDisabledStatus(username);
            lockOutCache.put(username, CacheableOptional.of(flag));
            return flag;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the flag, or null if it has ended, in which case it is removed and the account is recorded as not locked.
     *
     * @param flag
     *            the flag
     * @return the flag, a lock set since it was read, or null if the account is no longer locked
     */
    private LockOutFlag getUnExpiredLockout(LockOutFlag flag) {
        if (flag == null || !hasEnded(flag)) {
            return flag;
        }
        final LockOutProvider provider = providerSupplier.get();
        final String username = flag.getUsername();
        if (provider.shouldNotBeCached()) {
            provider.unsetDisabledStatus(username);
            return null;
        }
        final Lock lock = lockOutCache.getLock(username);
        lock.lock();
        try {
            // A lock set since the ended one was read replaces it, and must not be removed with it.
            final CacheableOptional<LockOutFlag> cached = lockOutCache.get(username);
            final LockOutFlag current = cached == null ? null : cached.get();
            if (current != null && !hasEnded(current)) {
                return current;
            }
            // Remove expired lockout entry
            provider.unsetDisabledStatus(username);
            lockOutCache.put(username, CacheableOptional.of(null));
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether a lock's end time has passed.
     */
    private static boolean hasEnded(final LockOutFlag flag) {
        return flag.getEndTime() != null && new Date().after(flag.getEndTime());
    }

}
