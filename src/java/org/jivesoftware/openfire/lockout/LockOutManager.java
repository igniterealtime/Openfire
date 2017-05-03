/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
import java.util.Map;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LockOutManager manages the LockOutProvider configured for this server, caches knowledge of
 * whether accounts are disabled or enabled, and provides a single point of entry for handling
 * locked/disabled accounts.
 *
 * The provider can be specified in system properties by adding:
 *
 * <ul>
 * <li><tt>provider.lockout.className = my.lock.out.provider</tt></li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class LockOutManager {

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

    /* Cache of locked out accounts */
    private Cache<String,LockOutFlag> lockOutCache;
    private LockOutProvider provider;

    /**
     * Constructs a LockOutManager, setting up it's cache, propery listener, and setting up the provider.
     */
    private LockOutManager() {
        // Initialize the lockout cache.
        lockOutCache = CacheFactory.createCache("Locked Out Accounts");

        // Load an lockout provider.
        initProvider();

        // Detect when a new lockout provider class is set 
        PropertyEventListener propListener = new PropertyEventListener() {
            @Override
            public void propertySet(String property, Map params) {
                if ("provider.lockout.className".equals(property)) {
                    initProvider();
                }
            }

            @Override
            public void propertyDeleted(String property, Map params) {
                //Ignore
            }

            @Override
            public void xmlPropertySet(String property, Map params) {
                //Ignore
            }

            @Override
            public void xmlPropertyDeleted(String property, Map params) {
                //Ignore
            }
        };
        PropertyEventDispatcher.addListener(propListener);
    }

    /**
     * Initializes the server's lock out provider, based on configuration and defaults to
     * DefaultLockOutProvider if the specified provider is not valid or not specified.
     */
    private void initProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("provider.lockout.className");

        String className = JiveGlobals.getProperty("provider.lockout.className",
                "org.jivesoftware.openfire.lockout.DefaultLockOutProvider");
        // Check if we need to reset the provider class
        if (provider == null || !className.equals(provider.getClass().getName())) {
            try {
                Class c = ClassUtils.forName(className);
                provider = (LockOutProvider) c.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading lockout provider: " + className, e);
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
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException();
        }
        LockOutFlag flag = new LockOutFlag(username, startTime, endTime);
        provider.setDisabledStatus(flag);
        if (!provider.shouldNotBeCached()) {
            // Add lockout data to cache.
            lockOutCache.put(username, flag);
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
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException();
        }
        provider.unsetDisabledStatus(username);
        if (!provider.shouldNotBeCached()) {
            // Remove lockout data from cache.
            lockOutCache.remove(username);
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
	 * Gets the user lock out.
	 *
	 * @param username
	 *            the username
	 * @return the user lock out
	 */
	private LockOutFlag getUserLockOut(String username) {
		if (provider.shouldNotBeCached()) {
			return provider.getDisabledStatus(username);
		}
		LockOutFlag flag = lockOutCache.get(username);
		// If ID wan't found in cache, load it up and put it there.
		if (flag == null) {
			synchronized (username.intern()) {
				flag = lockOutCache.get(username);
				// If group wan't found in cache, load it up and put it there.
				if (flag == null) {
					flag = provider.getDisabledStatus(username);
					if (flag != null) lockOutCache.put(username, flag);
				}
			}
		}
		return flag;
	}

	/**
	 * Check if lockout flag is expired.
	 *
	 * @param flag
	 *            the flag
	 */
	private LockOutFlag getUnExpiredLockout(LockOutFlag flag) {
		if (flag != null) {
			Date curDate = new Date();
			if (flag.getEndTime() != null && curDate.after(flag.getEndTime())) {
				// Remove expired lockout entry
				lockOutCache.remove(flag.getUsername());
				provider.unsetDisabledStatus(flag.getUsername());
				return null;
			}
		}
		return flag;
	}

}
