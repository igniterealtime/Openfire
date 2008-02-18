/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.lockout;

import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import java.util.Map;
import java.util.Date;

/**
 * The LockOutManager manages the LockOutProvider configured for this server, caches knowledge of
 * whether accounts are disabled or enabled, and providers a single point of entry for handling
 * locked/disabled accounts.
 *
 * The provider can be specified in <tt>openfire.xml</tt> by adding:
 *  ...
 *    <provider>
 *       <lockout>
 *          <className>my.lock.out.provider</className>
 *       </lockout>
 *    </provider>
 *  ...
 *
 * @author Daniel Henninger
 */
public class LockOutManager {

    // Wrap this guy up so we can mock out the LockOutManager class.
    private static final class LockOutManagerContainer {
        private static final LockOutManager instance = new LockOutManager();
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
        return provider;
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
    private static LockOutProvider provider = null;

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
            public void propertySet(String property, Map params) {
                //Ignore
            }

            public void propertyDeleted(String property, Map params) {
                //Ignore
            }

            public void xmlPropertySet(String property, Map params) {
                if ("provider.lockout.className".equals(property)) {
                    initProvider();
                }
            }

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
    private static void initProvider() {
        String className = JiveGlobals.getXMLProperty("provider.lockout.className",
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
     * @return The LockOutFlag instance describing the accounts disabled status.
     * @throws NotLockedOutException if user account specified is not currently locked out (disabled).
     */
    public LockOutFlag getDisabledStatus(String username) throws NotLockedOutException {
        LockOutFlag flag = lockOutCache.get(username);
        // If ID wan't found in cache, load it up and put it there.
        if (flag == null) {
            synchronized (username.intern()) {
                flag = lockOutCache.get(username);
                // If group wan't found in cache, load it up and put it there.
                if (flag == null) {
                    flag = provider.getDisabledStatus(username);
                    lockOutCache.put(username, flag);
                }
            }
        }
        return flag;
    }

    /**
     * Returns true or false if an account is currently locked out.
     *
     * @param username Username of account to check on.
     * @return True or false if the account is currently locked out.
     */
    public boolean isAccountDisabled(String username) {
        try {
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
        catch (NotLockedOutException e) {
            return false;
        }
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
        // Add lockout data to cache.
        lockOutCache.put(username, flag);
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
        // Remove lockout data from cache.
        lockOutCache.remove(username);
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

}
