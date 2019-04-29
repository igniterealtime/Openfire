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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches lockout events. The following events are supported:
 * <ul>
 * <li><b>accountLocked</b> --&gt; An account has been disabled/locked out.</li>
 * <li><b>accountUnlocked</b> --&gt; An account has been enabled/unlocked.</li>
 * <li><b>lockedAccountDenied</b> --&gt; A locked out account has been denied login.</li>
 * </ul>
 * Use {@link #addListener(LockOutEventListener)} and {@link #removeListener(LockOutEventListener)}
 * to add or remove {@link LockOutEventListener}.
 *
 * @author Daniel Henninger
 */
public class LockOutEventDispatcher {
    private static final Logger Log = LoggerFactory.getLogger(LockOutEventDispatcher.class);

    private static List<LockOutEventListener> listeners =
            new CopyOnWriteArrayList<>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(LockOutEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(LockOutEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies the listeners that an account was just set to be disabled/locked out.
     *
     * @param flag The LockOutFlag that was set, which includes the username of the account and start/end times.
     */
    public static void accountLocked(LockOutFlag flag) {
        if (!listeners.isEmpty()) {
            for (LockOutEventListener listener : listeners) {
                try {
                    listener.accountLocked(flag);     
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'accountLocked' event!", e);
                }
            }
        }
    }

    /**
     * Notifies the listeners that an account was just enabled (lockout removed).
     *
     * @param username The username of the account that was enabled.
     */
    public static void accountUnlocked(String username) {
        if (!listeners.isEmpty()) {
            for (LockOutEventListener listener : listeners) {
                try {
                    listener.accountUnlocked(username);
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'accountUnlocked' event!", e);
                }
            }
        }
    }

    /**
     * Notifies the listeners that a locked out account attempted to log in.
     *
     * @param username The username of the account that tried to log in.
     */
    public static void lockedAccountDenied(String username) {
        if (!listeners.isEmpty()) {
            for (LockOutEventListener listener : listeners) {
                try {
                    listener.lockedAccountDenied(username);
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'lockedAccountDenied' event!", e);
                }
            }
        }
    }

}
