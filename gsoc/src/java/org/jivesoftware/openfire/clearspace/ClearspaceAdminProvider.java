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
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.admin.AdminProvider;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.WSUtils.parseStringArray;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.dom4j.Element;

import java.util.List;
import java.util.ArrayList;

/**
 * Handles retrieving list of admins from Clearspace.
 *
 * @author Daniel Henninger
 */
public class ClearspaceAdminProvider implements AdminProvider {

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
        } catch (Exception e) {
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
