/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.registration;

import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.*;

/**
 * Manages registration data for transports. Individual transports use the registration data
 * and then create sessions used to exchange messages and presence data.
 *
 * @author Matt Tucker
 */
public class RegistrationManager {

    private static RegistrationManager instance = null;

    static Logger Log = Logger.getLogger(RegistrationManager.class);

    private static final String DELETE_REGISTRATION =
            "DELETE FROM ofGatewayRegistration WHERE registrationID=?";
    private static final String ALL_REGISTRATION_COUNT =
            "SELECT count(*) FROM ofGatewayRegistration";
    private static final String ALL_REGISTRATIONS =
            "SELECT registrationID FROM ofGatewayRegistration ORDER BY jid,transportType";
    private static final String LOAD_REGISTRATION =
            "SELECT registrationID FROM ofGatewayRegistration WHERE jid=? AND transportType=? AND username=?";
    private static final String ALL_USER_REGISTRATIONS =
            "SELECT registrationID FROM ofGatewayRegistration WHERE jid=?";
    private static final String ALL_GATEWAY_REGISTRATIONS =
            "SELECT registrationID FROM ofGatewayRegistration WHERE transportType=?";
    private static final String USER_GATEWAY_REGISTRATIONS =
            "SELECT registrationID FROM ofGatewayRegistration WHERE jid=? AND transportType=?";
    private static final String DELETE_PSEUDO_ROSTER =
            "DELETE FROM ofGatewayPseudoRoster WHERE registrationID=?";
    private static final String ALL_JIDS_REGISTERED =
            "SELECT jid FROM ofGatewayRegistration WHERE transportType=?";
    private static final String UPDATE_REGISTRATION =
            "UPDATE ofGatewayRegistration SET jid=?,transportType=?,username=?,password=?,nickname=?,registrationDate=?,lastLogin=? WHERE registrationID=?";
    public static final String GATEWAYREGISTRATIONS_CACHE_NAME = "Kraken Registration Cache";

    /* Cached known registrations. */
    /**
     * Cache (unlimited, never expire) that holds the locations of a transport session.
     * Key: transport type (aim, icq, etc) + bare JID, Value: nodeID
     * We store the key like BareJID@transportType so...  user@example.org@msn
     */
    private Cache<String,ArrayList<String>> registeredCache = CacheFactory.createCache(GATEWAYREGISTRATIONS_CACHE_NAME);

    private RegistrationManager() {
    }

    /**
     * Retrieve the instance of the registration manager.
     *
     * @return Current instance of RegistrationManager.
     */
    public static RegistrationManager getInstance() {
        if (instance == null) {
            instance = new RegistrationManager();
        }
        return instance;
    }

    /**
     * Shuts down the registration manager.
     */
    public void shutdown() {
        if (instance != null) {
            instance = null;
        }
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
        ArrayList<String> regList;
        if (!registeredCache.containsKey(transportType.toString())) {
            regList = new ArrayList<String>();
        }
        else {
            regList = registeredCache.get(transportType.toString());
        }
        regList.add(jid.toBareJID());
        registeredCache.put(transportType.toString(), regList);
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

            ArrayList<String> regList = registeredCache.get(registration.getTransportType().toString());
            regList.remove(registration.getJID().toBareJID());
            registeredCache.put(registration.getTransportType().toString(), regList);
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
     * Returns true or false if the user has a current registration.
     *
     * This needs to be -very- fast, so it operates strictly on a cached list.
     *
     * @param jid JID to check if they are registered.
     * @param transportType Transport type to check for registrations.
     * @return True or false if the user is registered.
     */
    public boolean isRegistered(JID jid, TransportType transportType) {
        cacheIfNotCached(transportType);
        try {
            return registeredCache.get(transportType.toString()).contains(jid.toBareJID());
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Caches the list of users registered with this transport if not already cached.
     *
     * @param transportType Transport type to be cached.
     */
    public void cacheIfNotCached(TransportType transportType) {
        if (registeredCache.containsKey(transportType.toString())) {
            // Already cached, no problem.
            return;
        }
        ArrayList<String> registrations = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_JIDS_REGISTERED);
            pstmt.setString(1, transportType.name());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                registrations.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
            registeredCache.put(transportType.toString(), registrations);
        }
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
     * Overwrites an existing registration with updated information
     *
     * Note: Registration must be based on an existing registration for this to work.
     *
     * @param curReg Registration we are overwriting.
     * @param newReg New registration info that will overwrite old.
     * @throws SQLException is there was an error interacting with the database.
     */
    public void overwriteExistingRegistration(Registration curReg, Registration newReg) throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(UPDATE_REGISTRATION);
            pstmt.setString(1, newReg.getJID().toString());
            pstmt.setString(2, newReg.getTransportType().name());
            pstmt.setString(3, newReg.getUsername());
            if (newReg.getPassword() != null) {
                // The password is stored in encrypted form for improved security.
                String encryptedPassword = AuthFactory.encryptPassword(newReg.getPassword());
                pstmt.setString(4, encryptedPassword);
            }
            else {
                pstmt.setNull(4, Types.VARCHAR);
            }
            if (newReg.getNickname() != null) {
                pstmt.setString(5, newReg.getNickname());
            }
            else {
                pstmt.setNull(5, Types.VARCHAR);
            }
            pstmt.setLong(6, newReg.getRegistrationDate().getTime());
            if (newReg.getLastLogin() != null) {
                pstmt.setLong(7, newReg.getLastLogin().getTime());
            }
            else {
                pstmt.setNull(7, Types.INTEGER);
            }
            pstmt.setLong(8, curReg.getRegistrationID());
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

    /**
     * Converts a list of registration IDs into a Collection of Registrations.
     */
    @SuppressWarnings("unchecked")
    private static class RegistrationCollection extends AbstractCollection<Registration> {

        private final List<Long> registrationIDs;

        /**
         * Constructs a new query results object.
         *
         * @param registrationIDs the list of registration IDs.
         */
        public RegistrationCollection(List<Long> registrationIDs) {
            this.registrationIDs = registrationIDs;
        }

        @Override
        public Iterator<Registration> iterator() {
            final Iterator<Long> regIterator = registrationIDs.iterator();
            return new Iterator() {

                private Registration nextElement = null;

                public boolean hasNext() {
                    if (nextElement == null) {
                        nextElement = getNextElement();
                        if (nextElement == null) {
                            return false;
                        }
                    }
                    return true;
                }

                public Registration next() {
                    Registration element;
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

                private Registration getNextElement() {
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

        @Override
        public int size() {
            return registrationIDs.size();
        }
    }
}
