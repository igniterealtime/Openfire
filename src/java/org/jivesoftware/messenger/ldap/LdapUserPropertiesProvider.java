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
    /**
     * Delete Vcard property. Currently unimplemented.
     *
     * @param id
     * @param name
     * @throws UnsupportedOperationException
     */
    public void deleteVcardProperty(long id, String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Delete user property. Currently unimplemented.
     *
     * @param id
     * @param name
     * @throws UnsupportedOperationException
     */
    public void deleteUserProperty(long id, String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Insert new vCard property. Currently unimplemented.
     *
     * @param id
     * @param name
     * @param value
     * @throws UnsupportedOperationException
     */
    public void insertVcardProperty(long id, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }


    /**
     * Insert new user property. Currently unimplemented.
     *
     * @param id
     * @param name
     * @param value
     * @throws UnsupportedOperationException
     */
    public void insertUserProperty(long id, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Update vCard. Currently unimplemented.
     *
     * @param id
     * @param name
     * @param value
     * @throws UnsupportedOperationException
     */
    public void updateVcardProperty(long id, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Update user property. Currently unimplemented.
     *
     * @param id
     * @param name
     * @param value
     * @throws UnsupportedOperationException
     */
    public void updateUserProperty(long id, String name, String value) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Get vCard properties. Unimplemented.
     *
     * @param id
     * @return empty Map
     */
    public Map getVcardProperties(long id) {
        return Collections.EMPTY_MAP;
    }

    /**
     * Get user properties. Unimplemented.
     *
     * @param id
     * @return empty Map.
     */
    public Map getUserProperties(long id) {
        return Collections.EMPTY_MAP;
    }
}
