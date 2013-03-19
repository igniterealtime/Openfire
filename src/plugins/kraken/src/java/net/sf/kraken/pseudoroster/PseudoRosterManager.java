/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.pseudoroster;

import net.sf.kraken.registration.Registration;
import net.sf.kraken.registration.RegistrationManager;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Handles retrieving pseudo rosters and other related tasks.
 *
 * @author Daniel Henninger
 */
public class PseudoRosterManager {

    private static PseudoRosterManager instance = null;

    static Logger Log = Logger.getLogger(PseudoRosterManager.class);

    private static final String REMOVE_ROSTER =
            "DELETE FROM ofGatewayPseudoRoster WHERE registrationID=?";
    private static final String GET_ALL_PSEUDO_ROSTERS =
            "SELECT DISTINCT registrationID FROM ofGatewayPseudoRoster";

    private PseudoRosterManager() {
    }

    /**
     * Retrieve the instance of the pseudo roster manager.
     *
     * @return Current instance of PseudoRosterManager.
     */
    public static PseudoRosterManager getInstance() {
        if (instance == null) {
            instance = new PseudoRosterManager();
        }
        return instance;
    }

    /**
     * Shuts down the pseudo roster manager.
     */
    public void shutdown() {
        if (instance != null) {
            instance = null;
        }
    }

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
        Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations(jid, type);
        if (registrations.isEmpty()) {
            // User is not registered with us.
            throw new UserNotFoundException("Unable to find registration.");
        }
        Registration registration = registrations.iterator().next();
        return getPseudoRoster(registration);
    }

    /**
     * Retrieves a list of all registration IDs that have pseudo rosters.
     *
     * @return List of registration ids that have pseudo rosters.
     */
    public List<Long> getRegistrations() {
        List<Long> registrations = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_ALL_PSEUDO_ROSTERS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                Long regId = rs.getLong(1);
                registrations.add(regId);
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return registrations;
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
