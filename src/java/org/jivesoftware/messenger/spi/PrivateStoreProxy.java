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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.PrivateStore;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.dom4j.Element;

/**
 * Standard security proxy.
 *
 * @author Iain Shigeoka
 */
public class PrivateStoreProxy implements PrivateStore {

    /**
     * The store being wrapped
     */
    private PrivateStore store;

    /**
     * Authentication token
     */
    private AuthToken authToken;
    /**
     * User permissions
     */
    private Permissions permissions;

    public PrivateStoreProxy(PrivateStore store, AuthToken authToken, Permissions permissions) {
        this.store = store;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public boolean isEnabled() {
        return store.isEnabled();
    }

    public void setEnabled(boolean enabled) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            store.setEnabled(enabled);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void add(String username, Element data) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            store.add(username, data);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Element get(String username, Element data) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return store.get(username, data);
        }
        else {
            throw new UnauthorizedException();
        }
    }
}
