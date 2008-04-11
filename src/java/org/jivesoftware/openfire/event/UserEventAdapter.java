/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.event;

import org.jivesoftware.openfire.user.User;

import java.util.Map;

/**
 * An abstract adapter class for receiving user events. 
 * The methods in this class are empty. This class exists as convenience for creating listener objects.
 */
public class UserEventAdapter implements UserEventListener  {
    public void userCreated(User user, Map params) {
    }

    public void userDeleting(User user, Map params) {
    }

    public void userModified(User user, Map params) {
    }
}
