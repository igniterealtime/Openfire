/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;

/**
 * An interface that defines a method to create proxy objects based on an authToken and permissions.
 *
 * @author Iain Shigeoka
 */
public interface ProxyFactory {

    /**
     * Creates a new proxy for <tt>obj</tt> using the specified authToken and permissions, or
     * returns null if the user doesn't have permission to read the object.
     *
     * @return a new proxy.
     */
    public Object createProxy(Object obj, AuthToken auth, Permissions perms);
}
