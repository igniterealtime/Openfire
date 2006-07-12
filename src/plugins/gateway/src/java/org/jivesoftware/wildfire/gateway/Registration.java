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

import java.util.Date;

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
public class Registration {

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
        this.jid = jid;
        this.gatewayType = gatewayType;
        this.username = username;
        this.password = password;
        this.registrationDate = new Date();
        // todo: insert into db
    }

    /**
     * Loads an existing registration.
     *
     * @param jid the JID of the user making the registration.
     * @param gatewayType the type of the gateway.
     * @param username the username on the gateway.
     * @throws NotFoundException if the registration could not be loaded.
     */
    public Registration(JID jid, GatewayType gatewayType, String username)
            throws NotFoundException
    {
        if (jid == null || gatewayType == null || username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        this.jid = jid;
        this.gatewayType = gatewayType;
        this.username = username;
        // todo: load from db
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

}