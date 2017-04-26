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
    void accountLocked( LockOutFlag flag );

    /**
     * Notifies the listeners that an account was just enabled (lockout removed).
     *
     * @param username The username of the account that was enabled.
     */
    void accountUnlocked( String username );

    /**
     * Notifies the listeners that a locked out account attempted to log in.
     *
     * @param username The username of the account that tried to log in.
     */
    void lockedAccountDenied( String username );

}
