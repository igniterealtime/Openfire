/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.event;

import org.jivesoftware.openfire.user.User;

import java.util.Map;

/**
 * Interface to listen for group events. Use the
 * {@link UserEventDispatcher#addListener(UserEventListener)}
 * method to register for events.
 *
 * @author Matt Tucker
 */
public interface UserEventListener {

    /**
     * A user was created.
     *
     * @param user the user.
     * @param params event parameters.
     */
    void userCreated( User user, Map<String, Object> params );

    /**
     * A user is being deleted.
     *
     * @param user the user.
     * @param params event parameters.
     */
    void userDeleting( User user, Map<String, Object> params );

    /**
     * A user's name, email, or an extended property was changed.
     *
     * @param user the user.
     * @param params event parameters.
     */
    void userModified( User user, Map<String, Object> params );
}
