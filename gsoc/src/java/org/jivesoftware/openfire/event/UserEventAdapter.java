/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
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
