/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.admin;

import org.xmpp.packet.JID;

import java.util.List;

/**
 * An AdminProvider handles storage of information about admin accounts, and requests to
 * set the list of admin users.
 *
 * @author Daniel Henninger
 */
public interface AdminProvider {

    /**
     * Returns a list of JIDs of accounts with administrative privileges.
     *
     * @return The list of admin users.
     */
    public List<JID> getAdmins();

    /**
     * Sets the list of admin accounts, by JID.
     *
     * @param admins List of JIDs of accounts to grant admin access to.
     */
    public void setAdmins(List<JID> admins);

    /**
     * Indicates whether the admin list is read-only or not.  In other words, whether an admin can
     * change who is an admin from the Openfire admin interface.
     *
     * @return True or false if the admin list can be edited.
     */
    public boolean isReadOnly();

}
