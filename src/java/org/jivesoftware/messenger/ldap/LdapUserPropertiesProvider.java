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

import org.jivesoftware.messenger.user.UserPropertiesProvider;
import java.util.Collections;
import java.util.Map;

/**
 * <p>Implement this provider to store user and vcard properties somewhere
 * other than the Jive tables, or to capture jive property events. Currently this is unimplemented.</p>
 *
 * @author Jim Berrettini
 */
public class LdapUserPropertiesProvider implements UserPropertiesProvider {

    public void deleteVcardProperty(String username, String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public void deleteUserProperty(String username, String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public void insertVcardProperty(String username, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public void insertUserProperty(String username, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public void updateVcardProperty(String username, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public void updateUserProperty(String username, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public Map getVcardProperties(String username) {
        return Collections.EMPTY_MAP;
    }

    public Map getUserProperties(String username) {
        return Collections.EMPTY_MAP;
    }
}