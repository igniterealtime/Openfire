/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.xmpp.packet.JID;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.wildfire.group.GroupManager;
import org.jivesoftware.wildfire.group.Group;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Registration Permissions Manager
 *
 * Handles who has access to a given transport, both for checking access and for
 * managing who is in the access list.  Should be used regardless of whether permissions
 * are set to "all" or "none" or not as this class checks for those settings on it's own.
 *
 * @author Daniel Henninger
 */
public class PermissionManager {

    private static final String IS_USER_LISTED =
            "SELECT count(*) FROM gatewayRestrictions WHERE transportType=? AND username=?";
    private static final String GROUPS_LISTED =
            "SELECT groupname FROM gatewayRestrictions WHERE transportType=?";

    public boolean hasAccess(TransportType type, JID jid) {
        int setting = JiveGlobals.getIntProperty("plugin.gateway."+type.toString()+".registration", 1);
        if (setting == 1) { return true; }
        if (setting == 3) { return false; }
        if (isUserAllowed(type, jid)) { return true; }
        if (isUserInAllowedGroup(type, jid)) { return true; }
        return false;
    }

    public boolean isUserAllowed(TransportType type, JID jid) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(IS_USER_LISTED);
            pstmt.setString(1, type.toString());
            pstmt.setString(2, jid.getNode());
            rs = pstmt.executeQuery();
            rs.next();
            return (rs.getInt(1) > 1);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return false;
    }

    public boolean isUserInAllowedGroup(TransportType type, JID jid) {
        ArrayList<String> allowedGroups = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GROUPS_LISTED);
            pstmt.setString(1, type.toString());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                allowedGroups.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collection<Group> userGroups = GroupManager.getInstance().getGroups(jid);
        for (Group g : userGroups) {
            if (allowedGroups.contains(g.getName())) {
                return true;
            }
        }
        return false;
    }

}
