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

import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.OfflineMessageStore;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.Iterator;

/**
 * Standard security proxy
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStoreProxy implements OfflineMessageStore {

    private OfflineMessageStore store;

    private org.jivesoftware.messenger.auth.AuthToken authToken;
    private Permissions permissions;

    public OfflineMessageStoreProxy(OfflineMessageStore store, AuthToken authToken, Permissions permissions) {
        this.store = store;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public void addMessage(Message message) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            store.addMessage(message);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public Iterator getMessages(String userName) throws UnauthorizedException, UserNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return store.getMessages(userName);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public Iterator getMessages(long userID) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return store.getMessages(userID);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public int getSize(long userID) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return store.getSize(userID);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public int getSize(String userName) throws UnauthorizedException, UserNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return store.getSize(userName);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }
}
