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
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.Date;


/**
 * Contains information about the registration a user has made with an external transport.
 * Each registration includes a username and password used to login to the transport
 * as well as a registration date and last login date.<p>
 *
 * The password for the transport registration is stored in encrypted form using
 * the Openfire password encryption key. See {@link org.jivesoftware.openfire.auth.AuthFactory#encryptPassword(String)}.
 *
 * @author Matt Tucker
 */
@JiveID(125)
public class Registration {

    static Logger Log = Logger.getLogger(Registration.class);

    private static final String INSERT_REGISTRATION =
            "INSERT INTO ofGatewayRegistration(registrationID, jid, transportType, " +
            "username, password, nickname, registrationDate) VALUES (?,?,?,?,?,?,?)";
    private static final String LOAD_REGISTRATION =
            "SELECT jid, transportType, username, password, nickname, registrationDate, lastLogin " +
            "FROM ofGatewayRegistration WHERE registrationID=?";
    private static final String SET_LAST_LOGIN =
            "UPDATE ofGatewayRegistration SET lastLogin=? WHERE registrationID=?";
    private static final String SET_PASSWORD =
            "UPDATE ofGatewayRegistration SET password=? WHERE registrationID=?";
    private static final String SET_USERNAME =
            "UPDATE ofGatewayRegistration SET username=? WHERE registrationID=?";
    private static final String SET_NICKNAME =
            "UPDATE ofGatewayRegistration SET nickname=? WHERE registrationID=?";

    private long registrationID;
    private JID jid;
    private TransportType transportType;
    private String username;
    private String password;
    private String nickname;
    private Date registrationDate;
    private Date lastLogin;
    private boolean disconnectedMode = false;

    /**
     * Do not use, only for clustering support/externalizable.
     */
    public Registration() {
    }

    /**
     * Creates a new registration.
     *
     * @param jid the JID of the user making the registration.
     * @param transportType the type of the transport.
     * @param username the username on the transport.
     * @param password the password on the transport.
     * @param nickname the nickname on the transport.
     */
    public Registration(JID jid, TransportType transportType, String username, String password, String nickname) {
        if (jid == null || transportType == null || username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        // Ensure that we store the bare JID.
        this.jid = new JID(jid.toBareJID());
        this.transportType = transportType;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.registrationDate = new Date();
        try {
            insertIntoDb();
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Creates a new registration in disconnected (test) mode.
     *
     * Passing false for disconnectedMode is the same as the previous constructor.
     *
     * @param jid the JID of the user making the registration.
     * @param transportType the type of the transport.
     * @param username the username on the transport.
     * @param password the password on the transport.
     * @param nickname the nickname on the transport.
     * @param disconnectedMode True or false if we are in disconnected mode.
     */
    public Registration(JID jid, TransportType transportType, String username, String password, String nickname, Boolean disconnectedMode) {
        if (jid == null || transportType == null || username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        this.disconnectedMode = disconnectedMode;
        // Ensure that we store the bare JID.
        this.jid = new JID(jid.toBareJID());
        this.transportType = transportType;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.registrationDate = new Date();
        try {
            insertIntoDb();
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Loads an existing registration.
     *
     * @param registrationID the ID of the registration.
     * @throws org.jivesoftware.util.NotFoundException if the registration could not be loaded.
     */
    public Registration(long registrationID)
            throws NotFoundException
    {
        this.registrationID = registrationID;
        loadFromDb();
    }

    /**
     * Returns the unique ID of the registration.
     *
     * @return the registration ID.
     */
    public long getRegistrationID() {
        return registrationID;
    }

    /**
     * Returns the JID of the user that made this registration.
     *
     * @return the JID of the user.
     */
    public JID getJID() {
        return jid;
    }

    /**
     * Returns the type of the transport.
     *
     * @return the transport type.
     */
    public TransportType getTransportType() {
        return transportType;
    }

    /**
     * Returns the username used for logging in to the transport.
     *
     * @return the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password used for logging in to the transport.
     *
     * @return the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the nickname used for logging in to the transport.
     *
     * @return the nickname.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Sets the password used for logging in to the transport.
     *
     * @param password new password for registration.
     */
    public void setPassword(String password) {
        this.password = password;
        if (disconnectedMode) { return; }
        // The password is stored in encrypted form for improved security.
        String encryptedPassword = AuthFactory.encryptPassword(password);
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_PASSWORD);
            if (password != null) {
                pstmt.setString(1, encryptedPassword);
            }
            else {
                pstmt.setNull(1, Types.VARCHAR);
            }
            pstmt.setLong(2, registrationID);
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
     * Sets the username used for logging in to the transport.
     *
     * @param username New username for transport registration.
     */
    public void setUsername(String username) {
        if (username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        this.username = username;
        if (disconnectedMode) { return; }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_USERNAME);
            pstmt.setString(1, username);
            pstmt.setLong(2, registrationID);
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
     * Sets the nickname used for logging in to the transport.
     *
     * @param nickname New nickname for transport registration.
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
        if (disconnectedMode) { return; }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_NICKNAME);
            if (nickname != null) {
                pstmt.setString(1, nickname);
            }
            else {
                pstmt.setNull(1, Types.VARCHAR);
            }
            pstmt.setLong(2, registrationID);
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
     * Returns the date that this transport registration was created.
     *
     * @return the date the registration was created.
     */
    public Date getRegistrationDate() {
        return registrationDate;
    }

    /**
     * Returns the date that the user last logged in to the transport using this
     * registration data, or <tt>null</tt> if the user has never logged in.
     *
     * @return the last login date.
     */
    public Date getLastLogin() {
        return lastLogin;
    }

    /**
     * Sets the data that the user last logged into the transport.
     *
     * @param lastLogin the last login date.
     */
    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
        if (disconnectedMode) { return; }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_LAST_LOGIN);
            pstmt.setLong(1, lastLogin.getTime());
            pstmt.setLong(2, registrationID);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public String toString() {
        return jid + ", " + transportType + ", " + username;
    }

    /**
     * Inserts a new registration into the database.
     *
     * @throws SQLException if the SQL statement is wrong for whatever reason.
     */
    private void insertIntoDb() throws SQLException {
        if (disconnectedMode) { return; }
        this.registrationID = SequenceManager.nextID(this);
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(INSERT_REGISTRATION);
            pstmt.setLong(1, registrationID);
            pstmt.setString(2, jid.toString());
            pstmt.setString(3, transportType.name());
            pstmt.setString(4, username);
            if (password != null) {
                // The password is stored in encrypted form for improved security.
                String encryptedPassword = AuthFactory.encryptPassword(password);
                pstmt.setString(5, encryptedPassword);
            }
            else {
                pstmt.setNull(5, Types.VARCHAR);
            }
            if (nickname != null) {
                pstmt.setString(6, nickname);
            }
            else {
                pstmt.setNull(6, Types.VARCHAR);
            }
            pstmt.setLong(7, registrationDate.getTime());
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
     * Load registration from database.
     *
     * @throws NotFoundException if registration was not found in database.
     */
    private void loadFromDb() throws NotFoundException {
        if (disconnectedMode) { return; }        
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_REGISTRATION);
            pstmt.setLong(1, registrationID);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Registration not found: " + registrationID);
            }
            this.jid = new JID(rs.getString(1));
            this.transportType = TransportType.valueOf(rs.getString(2));
            this.username = rs.getString(3);
            // The password is stored in encrypted form, so decrypt it.
            this.password = AuthFactory.decryptPassword(rs.getString(4));
            this.nickname = rs.getString(5);
            this.registrationDate = new Date(rs.getLong(6));
            long loginDate = rs.getLong(7);
            if (rs.wasNull()) {
                this.lastLogin = null;
            }
            else {
                this.lastLogin = new Date(loginDate);
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
