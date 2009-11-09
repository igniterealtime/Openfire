/**
 * $Revision$
 * $Date$
 *
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
package org.jivesoftware.openfire.clearspace;

import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.WSUtils.parseStringArray;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminProvider;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Handles retrieving list of admins from Clearspace.
 *
 * @author Daniel Henninger
 */
public class ClearspaceAdminProvider implements AdminProvider {

	private static final Logger Log = LoggerFactory.getLogger(ClearspaceAdminProvider.class);

    // The UserService webservice url prefix
    protected static final String PERMISSION_URL_PREFIX = "permissionService/";

    long SYSTEM_ADMIN_PERM = 0x800000000000000L;

    public ClearspaceAdminProvider() {

    }

    /**
     * The clearspace provider pulls the admin list from the userPermissions web service
     * @see org.jivesoftware.openfire.admin.AdminProvider#getAdmins()
     */
    public List<JID> getAdmins() {
        try {
            String path = PERMISSION_URL_PREFIX + "userPermissions/"+SYSTEM_ADMIN_PERM+"/true";
            Log.debug("ClearspaceAdminProvider: permissions query url is: "+path);
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);

            List<JID> admins = new ArrayList<JID>();
            for (String idStr : parseStringArray(element)) {
                Log.debug("Admin provider got ID number "+idStr);
                Long id = Long.valueOf(idStr);
                try {
                    String username = ClearspaceManager.getInstance().getUsernameByID(id);
                    Log.debug("Admin provider mapped to username "+username);
                    admins.add(XMPPServer.getInstance().createJID(username, null));
                }
                catch (UserNotFoundException e) {
                    // Hrm.  Got a response back that turned out not to exist?  This is "broken".
                }
            }
            return admins;
        }
        catch (ConnectionException e) {
            Log.error(e.getMessage(), e);
            return new ArrayList<JID>();
        }
        catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    /**
     * The clearspace provider does not allow setting admin lists from this interface
     * @see org.jivesoftware.openfire.admin.AdminProvider#setAdmins(java.util.List)  
     */
    public void setAdmins(List<JID> admins) {
        // Silently do nothing.  This shouldn't come up, but more inportantly, we don't want to bother Clearspace.
    }

    /**
     * The clearspace provider is read only
     * @see org.jivesoftware.openfire.admin.AdminProvider#isReadOnly()
     */
    public boolean isReadOnly() {
        return true;
    }

}
