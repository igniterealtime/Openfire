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

package org.jivesoftware.messenger.ldap;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserInfo;
import org.jivesoftware.messenger.user.UserInfoProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.spi.BasicUserInfo;
import java.util.Date;
import javax.naming.directory.*;

/**
 * LDAP implementation of the UserInfoProvider interface. The LdapUserIDProvider
 * can operate in two modes -- in the pure LDAP mode, all user data is stored in
 * the LDAP store. This mode generally requires modifications to the LDAP schema
 * to accommodate data that Messenger needs. In the mixed mode, data that Messenger
 * needs is stored locally.
 *
 * @author Jim Berrettini
 */
public class LdapUserInfoProvider implements UserInfoProvider {

    private LdapManager manager;

    /**
     * Constructor initializes the internal LdapManager instance.
     */
    public LdapUserInfoProvider() {
        manager = LdapManager.getInstance();
    }

    /**
     * <p>Obtain the UserInfo of a user. Will retrieve either from LDAP or locally, depending on mode of operation.</p>
     *
     * @param username the username.
     * @return a user info object.
     * @throws UserNotFoundException
     */
    public UserInfo getInfo(String username) throws UserNotFoundException {
        return getInfoFromLdap(username);
    }

    /**
     * <p>Sets the user's info. In pure LDAP mode, this is unsupported.</p>
     *
     * @param username username for setting info.
     * @param info to set.
     * @throws UserNotFoundException
     * @throws UnauthorizedException
     * @throws UnsupportedOperationException
     */
    public void setInfo(String username, UserInfo info)
            throws UserNotFoundException, UnauthorizedException, UnsupportedOperationException {
        throw new UnsupportedOperationException("All LDAP mode: Cannot modify data in LDAP.");
    }

    /**
     * Pure LDAP method for getting info for a given userID.
     *
     * @param username the username.
     * @return UserInfo for that user.
     * @throws UserNotFoundException
     */
    private UserInfo getInfoFromLdap(String username) throws UserNotFoundException {
        BasicUserInfo userInfo = null;
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Load record.
            String[] attributes = new String[]{
                manager.getUsernameField(), manager.getNameField(),
                manager.getEmailField()
            };
            ctx = manager.getContext();
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            String name = null;
            String email = null;
            Attribute nameField = attrs.get(manager.getNameField());
            if (nameField != null) {
                name = (String)nameField.get();
            }
            Attribute emailField = attrs.get(manager.getEmailField());
            if (emailField != null) {
                email = (String)emailField.get();
            }
            userInfo = new BasicUserInfo(username, name, email, new Date(), new Date());
        }
        catch (Exception e) {
            throw new UserNotFoundException(e);
        }
        finally {
            try {
                ctx.close();
            }
            catch (Exception e) {
            }
        }
        return userInfo;
    }
}