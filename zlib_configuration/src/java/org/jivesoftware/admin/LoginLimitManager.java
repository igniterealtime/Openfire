/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.admin;

import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles recording admin console login attempts and handling temporary lockouts where necessary.
 *
 * @author Daniel Henninger
 */
public class LoginLimitManager {

	private static final Logger Log = LoggerFactory.getLogger(LoginLimitManager.class);

    // Wrap this guy up so we can mock out the LoginLimitManager class.
    private static class LoginLimitManagerContainer {
        private static LoginLimitManager instance = new LoginLimitManager();
    }

    /**
     * Returns a singleton instance of LoginLimitManager.
     *
     * @return a LoginLimitManager instance.
     */
    public static LoginLimitManager getInstance() {
        return LoginLimitManagerContainer.instance;
    }

    // Max number of attempts per ip address that can be performed in given time frame
    private long maxAttemptsPerIP;
    // Time frame before attempts per ip addresses are reset
    private long millisecondsBetweenPerIP;

    // Max number of attempts per username that can be performed in a given time frame
    private long maxAttemptsPerUsername;
    // Time frame before attempts per username are reset
    private long millisecondsBetweenPerUsername;

    // Record of attempts per IP address
    private Map<String,Long> attemptsPerIP;
    // Record of attempts per username
    private Map<String,Long> attemptsPerUsername;

    /**
     * Constructs a new login limit manager.
     */
    private LoginLimitManager() {
        // Set up initial maps
        attemptsPerIP = new ConcurrentHashMap<String,Long>();
        attemptsPerUsername = new ConcurrentHashMap<String,Long>();

        // Max number of attempts per ip address that can be performed in given time frame (10 attempts default)
        maxAttemptsPerIP = JiveGlobals.getLongProperty("adminConsole.maxAttemptsPerIP", 10);
        // Time frame before attempts per ip addresses are reset (15 minutes default)
        millisecondsBetweenPerIP = JiveGlobals.getLongProperty("adminConsole.perIPAttemptResetInterval", 900000);
        // Max number of attempts per username that can be performed in a given time frame (10 attempts default)
        maxAttemptsPerUsername = JiveGlobals.getLongProperty("adminConsole.maxAttemptsPerUsername", 10);
        // Time frame before attempts per ip addresses are reset (15 minutes default)
        millisecondsBetweenPerUsername = JiveGlobals.getLongProperty("adminConsole.perUsernameAttemptResetInterval", 900000);
        // Set up per username attempt reset task
        TaskEngine.getInstance().scheduleAtFixedRate(new PerUsernameTask(), 0, millisecondsBetweenPerUsername);
        // Set up per IP attempt reset task
        TaskEngine.getInstance().scheduleAtFixedRate(new PerIPAddressTask(), 0, millisecondsBetweenPerIP);
    }

    /**
     * Returns true of the entered username or connecting IP address has hit it's attempt limit.
     *
     * @param username Username being checked.
     * @param address IP address that is connecting.
     * @return True if the login attempt limit has been hit.
     */
    public boolean hasHitConnectionLimit(String username, String address) {
        if (attemptsPerIP.get(address) != null && attemptsPerIP.get(address) > maxAttemptsPerIP) {
            return true;
        }
        if (attemptsPerUsername.get(username) != null && attemptsPerUsername.get(username) > maxAttemptsPerUsername) {
            return true;
        }
        // No problem then, no limit hit.
        return false;
    }

    /**
     * Records a failed connection attempt.
     *
     * @param username Username being attempted.
     * @param address IP address that is attempting.
     */
    public void recordFailedAttempt(String username, String address) {
        Log.warn("Failed admin console login attempt by "+username+" from "+address);

        Long cnt = (long)0;
        if (attemptsPerIP.get(address) != null) {
            cnt = attemptsPerIP.get(address);
        }
        cnt++;
        attemptsPerIP.put(address, cnt);
        if (cnt > maxAttemptsPerIP) {
            Log.warn("Login attempt limit breeched for address "+address);
        }

        cnt = (long)0;
        if (attemptsPerUsername.get(username) != null) {
            cnt = attemptsPerUsername.get(username);
        }
        cnt++;
        attemptsPerUsername.put(username, cnt);
        if (cnt > maxAttemptsPerUsername) {
            Log.warn("Login attempt limit breeched for username "+username);
        }
    }

    /**
     * Clears failed login attempts if a success occurs.
     *
     * @param username Username being attempted.
     * @param address IP address that is attempting.
     */
    public void recordSuccessfulAttempt(String username, String address) {
        attemptsPerIP.remove(address);
        attemptsPerUsername.remove(username);
    }

    /**
     * Runs at configured interval to clear out attempts per username, thereby wiping lockouts.
	 */
    private class PerUsernameTask extends TimerTask {

        /**
         * Wipes failed attempt list for usernames.
         */
        @Override
        public void run() {
            attemptsPerUsername.clear();
        }
	}

    /**
     * Runs at configured interval to clear out attempts per ip address, thereby wiping lockouts.
	 */
    private class PerIPAddressTask extends TimerTask {

        /**
         * Wipes failed attempt list for ip addresses.
         */
        @Override
        public void run() {
            attemptsPerIP.clear();
        }
	}

}
