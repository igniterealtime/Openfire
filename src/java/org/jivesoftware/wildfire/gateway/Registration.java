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
import org.jivesoftware.wildfire.auth.AuthFactory;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.Log;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;

import java.util.Date;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Contains information about the registration a user has made with an external gateway.
 * Each registration includes a username and password used to login to the gateway
 * as well as a registration date and last login date.<p>
 *
 * The password for the gateway registration is stored in encrypted form using
 * the Wildfire password encryption key. See {@link AuthFactory#encryptPassword(String)}.
 *
 * @author Matt Tucker
 */
@JiveID(125)
public class Registration {

    private static final String INSERT_REGISTRATION =
            "INSERT INTO entConversation(registrationID, jid, gatewayType, " +
            "username, password, registrationDate) VALUES (?,?,?,?,?,?)";
    private static final String LOAD_REGISTRATION =
            "SELECT jid, gatewayType, username, password, registrationDate, lastLogin " +
            "FROM gatewayRegistration WHERE registrationID=?";

    private long registrationID;
    private JID jid;
    private GatewayType gatewayType;
    private String username;
    private String password;
    private Date registrationDate;
    private Date lastLogin;

    /**
     * Creates a new registration.
     *
     * @param jid the JID of the user making the registration.
     * @param gatewayType the type of the gateway.
     * @param username the username on the gateway.
     * @param password the password on the gateway.
     */
    public Registration(JID jid, GatewayType gatewayType, String username, String password) {
        if (jid == null || gatewayType == null || username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        // Ensure that we store the bare JID.
        this.jid = new JID(jid.toBareJID());
        this.gatewayType = gatewayType;
        this.username = username;
        this.password = password;
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
     * @throws NotFoundException if the registration could not be loaded.
     */
    public Registration(long registrationID)
            throws NotFoundException
    {
        this.registrationID = registrationID;
        loadFromDb();
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
     * Returns the type of the gateway.
     *
     * @return the gateway type.
     */
    public GatewayType getGatewayType() {
        return gatewayType;
    }

    /**
     * Returns the username used for logging in to the gateway.
     *
     * @return the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password used for logging in to the gateway.
     *
     * @return the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password used for logging in to the gateway.
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
        // todo: save to db
    }

    /**
     * Returns the date that this gateway registration was created.
     *
     * @return the date the registration was created.
     */
    public Date getRegistrationDate() {
        return registrationDate;
    }

    /**
     * Returns the date that the user last logged in to the gateway using this
     * registration data, or <tt>null</tt> if the user has never logged in.
     *
     * @return the last login date.
     */
    public Date getLastLogin() {
        return lastLogin;
    }

    /**
     * Sets the data that the user last logged into the gateway.
     *
     * @param lastLogin the last login date.
     */
    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
        // todo: save to db
    }

    public String toString() {
        return jid + ", " + gatewayType + ", " + username;
    }

    /**
     * Inserts a new registration into the database.
     */
    private void insertIntoDb() throws SQLException {
        this.registrationID = SequenceManager.nextID(this);
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(INSERT_REGISTRATION);
            pstmt.setLong(1, registrationID);
            pstmt.setString(2, jid.toString());
            pstmt.setString(3, gatewayType.name());
            pstmt.setString(4, username);
            pstmt.setString(5, password);
            pstmt.setLong(6, registrationDate.getTime());
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

    private void loadFromDb() throws NotFoundException {
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
            this.gatewayType = GatewayType.valueOf(rs.getString(2));
            this.username = rs.getString(3);
            this.password = rs.getString(4);
            this.registrationDate = new Date(rs.getLong(5));
            long loginDate = rs.getLong(6);
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