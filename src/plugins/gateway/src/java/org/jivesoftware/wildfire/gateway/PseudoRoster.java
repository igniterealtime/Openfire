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

import org.jivesoftware.util.Log;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/**
 * Representation of an entire roster associated with a registration id.
 *
 * @author Daniel Henninger
 */
public class PseudoRoster {

    private static final String GET_ALL_USER_ROSTER_ITEMS =
            "SELECT username FROM gatewayPseudoRoster WHERE registrationID=?";

    private long registrationID;
    private List<String> pseudoRosterItems = new ArrayList<String>();

    /**
     * Loads an existing pseudo roster.
     *
     * @param registrationID The ID of the registration the roster item is assocaited with.
     */
    public PseudoRoster(long registrationID) {
        this.registrationID = registrationID;
        loadFromDb();
    }

    /**
     * Returns the unique ID of the registration associated with the roster.
     *
     * @return the registration ID.
     */
    public long getRegistrationID() {
        return registrationID;
    }

    /**
     * Returns the list of roster items associated with this registration ID.
     *
     * @return List of roster item usernames.
     */
    public List<String> getRosterItems() {
        return pseudoRosterItems;
    }

    private void loadFromDb() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_ALL_USER_ROSTER_ITEMS);
            pstmt.setLong(1, registrationID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                pseudoRosterItems.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

}
