/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.lockout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches lockout events. The following events are supported:
 * <ul>
 * <li><b>accountLocked</b> --> An account has been disabled/locked out.</li>
 * <li><b>accountUnlocked</b> --> An account has been enabled/unlocked.</li>
 * <li><b>lockedAccountDenied</b> --> A locked out account has been denied login.</li>
 * </ul>
 * Use {@link #addListener(LockOutEventListener)} and {@link #removeListener(LockOutEventListener)}
 * to add or remove {@link LockOutEventListener}.
 *
 * @author Daniel Henninger
 */
public class LockOutEventDispatcher {

    private static List<LockOutEventListener> listeners =
            new CopyOnWriteArrayList<LockOutEventListener>();

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
                listener.accountLocked(flag);
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
                listener.accountUnlocked(username);
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
                listener.lockedAccountDenied(username);
            }
        }
    }

}
