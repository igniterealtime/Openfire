/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * @author Daniel Henninger
 */
public class ClearspaceUserProvider implements UserProvider {
    public User loadUser(String username) throws UserNotFoundException {
        return new User(username, "test user", "test@example.org", new Date(), new Date());
    }

    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
        return new User(username, name, email, new Date(), new Date());
    }

    public void deleteUser(String username) {
        // Nothing
    }

    public int getUserCount() {
        return 0;
    }

    public Collection<User> getUsers() {
        return null;
    }

    public Collection<String> getUsernames() {
        return null;
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        return null;
    }

    public void setName(String username, String name) throws UserNotFoundException {
        // Nothing
    }

    public void setEmail(String username, String email) throws UserNotFoundException {
        // Nothing
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        // Nothing
    }

    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        // Nothing
    }

    public Set<String> getSearchFields() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public boolean isReadOnly() {
        return true;
    }
}
