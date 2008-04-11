/**
 * $RCSfile$
 * $Revision: 1526 $
 * $Date: 2005-06-16 02:50:35 -0300 (Thu, 16 Jun 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
    public void userCreated(User user, Map<String,Object> params);

    /**
     * A user is being deleted.
     *
     * @param user the user.
     * @param params event parameters.
     */
    public void userDeleting(User user, Map<String,Object> params);

    /**
     * A user's name, email, or an extended property was changed.
     *
     * @param user the user.
     * @param params event parameters.
     */
    public void userModified(User user, Map<String,Object> params);
}