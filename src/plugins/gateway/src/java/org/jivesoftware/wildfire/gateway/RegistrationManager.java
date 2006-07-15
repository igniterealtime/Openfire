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
 * Manages registration data for gateways. Individual gateways use the registration data
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
            "SELECT registrationID FROM gatewayRegistration";
    private static final String LOAD_REGISTRATION =
            "SELECT registrationID FROM gatewayRegistration WHERE jid=? AND gatewayType=? " +
            "AND username=?";
    private static final String ALL_USER_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration WHERE jid=?";
    private static final String ALL_GATEWAY_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration WHERE gatewayType=?";
    private static final String USER_GATEWAY_REGISTRATIONS =
            "SELECT registrationID FROM gatewayRegistration WHERE jid=? AND gatewayType=?";

    public void start() {

    }

    public void stop() {

    }

    /**
     * Creates a new registration.
     *
     * @param jid the JID of the user making the registration.
     * @param gatewayType the type of the gateway.
     * @param username the username on the gateway service.
     * @param password the password on the gateway service.
     * @return a new registration.
     */
    public Registration createRegistration(JID jid, GatewayType gatewayType, String username,
            String password)
    {
        return new Registration(jid, gatewayType, username, password);
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
            pstmt.executeQuery();

        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Returns all registrations for a particular type of gateway.
     *
     * @param gatewayType the gateway type.
     * @return all registrations for the gateway type.
     */
    public Collection<Registration> getRegistrations(GatewayType gatewayType) {
        List<Long> registrationIDs = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GATEWAY_REGISTRATIONS);
            pstmt.setString(1, gatewayType.name());
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
     * Returns all registrations that a JID has on a particular gateway type.
     * In the typical case, a JID has a single registration with a particular gateway
     * type. However, it's also possible to maintain multiple registrations. For example,
     * the user "joe_smith@example.com" might have have two user accounts on the AIM
     * gateway service: "jsmith" and "joesmith".
     *
     * @param jid the JID of the user.
     * @param gatewayType the type of the gateway.
     * @return all registrations for the JID of a particular gateway type.
     */
    public Collection<Registration> getRegistrations(JID jid, GatewayType gatewayType) {
        List<Long> registrationIDs = new ArrayList<Long>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GATEWAY_REGISTRATIONS);
            // Use the bare JID of the user.
            pstmt.setString(1, jid.toBareJID());
            pstmt.setString(2, gatewayType.name());
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
     * Returns a registration given a JID, gateway type, and username.
     *
     * @param jid the JID of the user.
     * @param gatewayType the gateway type.
     * @param username the username on the gateway service.
     * @return the registration.
     * @throws NotFoundException if the registration could not be found.
     */
    public Registration getRegistration(JID jid, GatewayType gatewayType, String username)
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
            pstmt.setString(2, gatewayType.name());
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