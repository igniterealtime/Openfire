/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    List<JID> getAdmins();

    /**
     * Sets the list of admin accounts, by JID.
     *
     * @param admins List of JIDs of accounts to grant admin access to.
     */
    void setAdmins( List<JID> admins );

    /**
     * Indicates whether the admin list is read-only or not.  In other words, whether an admin can
     * change who is an admin from the Openfire admin interface.
     *
     * @return True or false if the admin list can be edited.
     */
    boolean isReadOnly();

}
