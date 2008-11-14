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

/**
 * Interface to listen for lockout events. Use the
 * {@link LockOutEventDispatcher#addListener(LockOutEventListener)}
 * method to register for events.
 *
 * @author Daniel Henninger
 */
public interface LockOutEventListener {

    /**
     * Notifies the listeners that an account was just set to be disabled/locked out.
     *
     * @param flag The LockOutFlag that was set, which includes the username of the account and start/end times.
     */
    public void accountLocked(LockOutFlag flag);

    /**
     * Notifies the listeners that an account was just enabled (lockout removed).
     *
     * @param username The username of the account that was enabled.
     */
    public void accountUnlocked(String username);

    /**
     * Notifies the listeners that a locked out account attempted to log in.
     *
     * @param username The username of the account that tried to log in.
     */
    public void lockedAccountDenied(String username);

}
