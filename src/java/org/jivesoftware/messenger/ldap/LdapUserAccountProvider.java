/**
 *
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2003 JiveSoftware. All rights reserved.
 *
 * This software is the proprietary information of Jive Software.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.ldap;

import org.jivesoftware.messenger.user.UserAccountProvider;

/**
 * <p>Defines the provider methods required for creating and deleting user accounts.</p>
 * <p/>
 * <p>Since the LDAP datastore is currently considered read-only, all the methods here throw
 * UnsupportedOperationExceptions</p>
 *
 * @author Jim Berrettini
 */
public class LdapUserAccountProvider implements UserAccountProvider {
    /**
     * <p>Called to create a new user account. Not implemented, throws UnsupportedOperationException.</p>
     *
     * @param username the name of the user
     * @param password the user's password
     * @param email    the user's email address
     * @return the userID of the created account
     * @throws UnsupportedOperationException always thrown because it is not supported
     */
    public long createUser(String username, String password, String email) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Creating new users not supported.");
    }

    /**
     * <p>Called to delete a user account. Not implemented, throws UnsupportedOperationException.</p>
     *
     * @param id the id of the user to delete
     * @throws UnsupportedOperationException always thrown because it is not supported
     */
    public void deleteUser(long id) throws UnsupportedOperationException {
        // todo -- we can use this method to clean up the database.
        throw new UnsupportedOperationException();
    }
}
