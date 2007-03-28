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
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.picocontainer.Startable;

import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

/**
 * Manages registration data for transports. Individual transports use the registration data
 * and then create sessions used to exchange messages and presence data.
 *
 * @author Matt Tucker
 */
public class RegistrationManager implements Startable {

    private static final String DELETE_REGISTRATION =
            "DELETE FROM gatewayRegistration WHERE registrationID=?";
    private static final String ALL_REGISTRATION_COUNT =
            "SELECT count(*) FROM gatewayRegistration";
    private static final String ALL_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration ORDER BY jid,transportType";
    private static final String LOAD_REGISTRATION =
            "SELECT registrationID FROM gatewayRegistration WHERE jid=? AND transportType=? AND username=?";
    private static final String ALL_USER_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration WHERE jid=? ORDER BY transportType";
    private static final String ALL_GATEWAY_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration WHERE transportType=? ORDER BY jid";
    private static final String USER_GATEWAY_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration WHERE jid=? AND transportType=?";
    private static final String DELETE_PSEUDO_ROSTER =
            "DELETE FROM gatewayPseudoRoster WHERE registrationID=?";

    public void start() {

    }

    public void stop() {

    }

    /**
     * Creates a new registration.
     *
     * @param jid the JID of the user making the registration.
     * @param transportType the type of the transport.
     * @param username the username on the transport service.
     * @param password the password on the transport service.
     * @param nickname the nickname on the transport service.
     * @return a new registration.
     */
    public Registration createRegistration(JID jid, TransportType transportType, String username,
            String password, String nickname)
    {
        return new Registration(jid, transportType, username, password, nickname);
    }

    /**
     * Deletes a registration.
     *
     * @param registration the registration to delete.
     */
    public void deleteRegistration(Registration registration) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_REGISTRATION);
            pstmt.setLong(1, registration.getRegistrationID());
            pstmt.executeUpdate();

            pstmt = con.prepareStatement(DELETE_PSEUDO_ROSTER);
            pstmt.setLong(1, registration.getRegistrationID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Returns all registrations for a particular type of transport.
     *
     * @param transportType the transport type.
     * @return all registrations for the transport type.
     */
    public Collection<Registration> getRegistrations(TransportType transportType) {
        List<Long> registrationIDs = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GATEWAY_REGISTRATIONS);
            pstmt.setString(1, transportType.name());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                registrationIDs.add(rs.getLong(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        if (registrationIDs.isEmpty()) {
            return Collections.emptyList();
        }
        else {
            return new RegistrationCollection(registrationIDs);
        }
    }

    /**
     * Returns all registrations for a particular JID.
     *
     * @param jid the JID of the user.
     * @return all registrations for the JID.
     */
    public Collection<Registration> getRegistrations(JID jid) {
        List<Long> registrationIDs = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USER_REGISTRATIONS);
            // Use the bare JID of the user.
            pstmt.setString(1, jid.toBareJID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                registrationIDs.add(rs.getLong(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        if (registrationIDs.isEmpty()) {
            return Collections.emptyList();
        }
        else {
            return new RegistrationCollection(registrationIDs);
        }
    }

    /**
     * Returns all registrations that a JID has on a particular transport type.
     * In the typical case, a JID has a single registration with a particular transport
     * type. However, it's also possible to maintain multiple registrations. For example,
     * the user "joe_smith@example.com" might have have two user accounts on the AIM
     * transport service: "jsmith" and "joesmith".
     *
     * @param jid the JID of the user.
     * @param transportType the type of the transport.
     * @return all registrations for the JID of a particular transport type.
     */
    public Collection<Registration> getRegistrations(JID jid, TransportType transportType) {
        List<Long> registrationIDs = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GATEWAY_REGISTRATIONS);
            // Use the bare JID of the user.
            pstmt.setString(1, jid.toBareJID());
            pstmt.setString(2, transportType.name());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                registrationIDs.add(rs.getLong(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        if (registrationIDs.isEmpty()) {
            return Collections.emptyList();
        }
        else {
            return new RegistrationCollection(registrationIDs);
        }
    }

    /**
     * Returns a registration given a JID, transport type, and username.
     *
     * @param jid the JID of the user.
     * @param transportType the transport type.
     * @param username the username on the transport service.
     * @return the registration.
     * @throws NotFoundException if the registration could not be found.
     */
    public Registration getRegistration(JID jid, TransportType transportType, String username)
            throws NotFoundException
    {
        long registrationID = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_REGISTRATION);
            pstmt.setString(1, jid.toBareJID());
            pstmt.setString(2, transportType.name());
            pstmt.setString(3, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Could not load registration with ID " + registrationID);
            }
            registrationID = rs.getLong(1);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return new Registration(registrationID);
    }

    /**
     * Returns the count of all registrations.
     *
     * @return the total count of registrations.
     */
    public int getRegistrationCount() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_REGISTRATION_COUNT);
            rs = pstmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return 0;
    }

    /**
     * Returns all registrations.
     *
     * @return all registrations.
     */
    public Collection<Registration> getRegistrations() {
        List<Long> registrationIDs = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_REGISTRATIONS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                registrationIDs.add(rs.getLong(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        if (registrationIDs.isEmpty()) {
            return Collections.emptyList();
        }
        else {
            return new RegistrationCollection(registrationIDs);
        }
    }

    /**
     * Converts a list of registration IDs into a Collection of Registrations.
     */
    private class RegistrationCollection extends AbstractCollection {

        private List<Long> registrationIDs;

        /**
         * Constructs a new query results object.
         *
         * @param registrationIDs the list of registration IDs.
         */
        public RegistrationCollection(List<Long> registrationIDs) {
            this.registrationIDs = registrationIDs;
        }

        public Iterator iterator() {
            final Iterator<Long> regIterator = registrationIDs.iterator();
            return new Iterator() {

                private Object nextElement = null;

                public boolean hasNext() {
                    if (nextElement == null) {
                        nextElement = getNextElement();
                        if (nextElement == null) {
                            return false;
                        }
                    }
                    return true;
                }

                public Object next() {
                    Object element;
                    if (nextElement != null) {
                        element = nextElement;
                        nextElement = null;
                    }
                    else {
                        element = getNextElement();
                        if (element == null) {
                            throw new NoSuchElementException();
                        }
                    }
                    return element;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                private Object getNextElement() {
                    if (!regIterator.hasNext()) {
                        return null;
                    }
                    while (regIterator.hasNext()) {
                        try {
                            long registrationID = regIterator.next();
                            return new Registration(registrationID);
                        }
                        catch (Exception e) {
                            Log.error(e);
                        }
                    }
                    return null;
                }
            };
        }

        public int size() {
            return registrationIDs.size();
        }
    }
}
