/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

import org.xmpp.packet.JID;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.database.DbConnectionManager;

import java.util.Collection;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Handles retrieving pseudo rosters and other related tasks.
 *
 * @author Daniel Henninger
 */
public class PseudoRosterManager {

    private static final String REMOVE_ROSTER =
            "DELETE FROM gatewayPseudoRoster WHERE registrationID=?";

    /**
     * Manages registration information.
     * @see org.jivesoftware.openfire.gateway.RegistrationManager
     */
    public final RegistrationManager registrationManager = new RegistrationManager();

    /**
     * Retrieves a pseudo roster based off of a registration ID.
     *
     * @param registrationID To retrieve the roster for.
     * @return A Pseudo roster
     */
    public PseudoRoster getPseudoRoster(Long registrationID) {
        return new PseudoRoster(registrationID);
    }

    /**
     * Retrieves a pseudo roster based off of a registration.
     *
     * @param registration To retrieve the roster for.
     * @return A Pseudo roster
     */
    public PseudoRoster getPseudoRoster(Registration registration) {
        return getPseudoRoster(registration.getRegistrationID());
    }

    /**
     * Retrieves a pseudo roster based off of a registration.
     *
     * @param jid To retrieve the roster for.
     * @param type TransportType the roster is for.
     * @return A Pseudo roster
     * @throws UserNotFoundException if the user is not actually registered.
     */
    public PseudoRoster getPseudoRoster(JID jid, TransportType type) throws UserNotFoundException {
        Collection<Registration> registrations = registrationManager.getRegistrations(jid, type);
        if (registrations.isEmpty()) {
            // User is not registered with us.
            throw new UserNotFoundException("Unable to find registration.");
        }
        Registration registration = registrations.iterator().next();
        return getPseudoRoster(registration);
    }

    /**
     * Removes a pseudo roster entirely.
     *
     * @param registrationID ID to be removed.
     * @throws SQLException if the SQL statement is wrong for whatever reason.
     */
    public void removePseudoRoster(Long registrationID) throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(REMOVE_ROSTER);
            pstmt.setLong(1, registrationID);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

}
