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

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.ProxyFactory;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.user.User;
import java.util.Iterator;

/**
 * Protection proxy for Iterators of users.
 *
 * @author Iain Shigeoka
 */
public class UserIteratorProxy implements Iterator {

    private Iterator iterator;
    private Object nextElement = null;

    private AuthToken auth;
    private Permissions permissions;
    private ProxyFactory proxyFactory;

    /**
     * Creates a new user iterator proxy.
     *
     * @param iterator    the Iterator to create proxies for.
     * @param auth        the authorization token.
     * @param permissions the permissions that the new proxy will inherit.
     */
    public UserIteratorProxy(Iterator iterator, AuthToken auth, Permissions permissions) {
        this.iterator = iterator;
        this.auth = auth;
        this.permissions = permissions;

        // Create a class that wraps users with proxies.
        proxyFactory = new ProxyFactory() {
            public Object createProxy(Object obj, AuthToken auth, Permissions perms) {
                User user = (User)obj;
                Permissions userPerms = user.getPermissions(auth);
                Permissions newPerms = new Permissions(userPerms, perms);
                return new UserProxy(user, auth, newPerms);
            }
        };
    }

    /**
     * Returns true if there are more elements in the iteration.
     *
     * @return true if the iterator has more elements.
     */
    public boolean hasNext() {
        // If we are at the end of the list, there can't be any more elements to iterate through.
        if (!iterator.hasNext() && nextElement == null) {
            return false;
        }
        // Otherwise, see if nextElement is null. If so, try to load the next element to make sure
        // it exists.
        if (nextElement == null) {
            nextElement = getNextElement();
            if (nextElement == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the next element.
     *
     * @return the next element.
     * @throws java.util.NoSuchElementException
     *          if there are no more elements.
     */
    public Object next() throws java.util.NoSuchElementException {
        Object element = null;
        if (nextElement != null) {
            element = nextElement;
            nextElement = null;
        }
        else {
            element = getNextElement();
            if (element == null) {
                throw new java.util.NoSuchElementException();
            }
        }
        return element;
    }

    /**
     * Not supported for security reasons.
     */
    public void remove() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the next available element, or null if there are no more elements.
     *
     * @return the next available element.
     */
    public Object getNextElement() {
        while (iterator.hasNext()) {
            Object element = proxyFactory.createProxy(iterator.next(), auth, permissions);
            if (element != null) {
                return element;
            }
        }
        return null;
    }
}