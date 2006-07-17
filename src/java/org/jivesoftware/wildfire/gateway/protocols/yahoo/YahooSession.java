/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import java.io.IOException;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import ymsg.network.LoginRefusedException;
import ymsg.network.Session;

/**
 * Represents a Yahoo session.
 * 
 * This is the interface with which the base transport functionality will
 * communicate with Yahoo.
 *
 * @author Daniel Henninger
 * Heavily inspired by Noah Campbell's work.
 */
public class YahooSession extends TransportSession {

    /**
     * Create a Yahoo Session instance.
     *
     * @param registration Registration informationed used for logging in.
     */
    public YahooSession(Registration registration, YahooTransport transport) {
        super(registration, transport);

        yahooSession = new Session();
        yahooSession.addSessionListener(new YahooSessionListener(this));
    }

    /**
     * Are we logged in?
     */
    private Boolean loggedIn = false;

    /**
     * Are we trying to log in right now?
     */
    private Boolean loggingIn = false;

    /**
     * How many attempts have been made so far?
     */
    private Integer loginAttempts = 0;

    /**
     * Yahoo session
     */
    private final Session yahooSession;

    /**
     * Log in to Yahoo.
     */
    public void logIn() {
        if (!isLoggedIn() && !loggingIn && loginAttempts <= 3) {
            loggingIn = true;
            new Thread() {
                public void run() {
                    try {
                        loginAttempts++;
                        yahooSession.login(registration.getUsername(), registration.getPassword());
                        loggedIn = true;

                        Presence p = new Presence();
                        p.setTo(registration.getJID());
                        p.setFrom(getTransport().getJID());
                        getTransport().sendPacket(p);
                    }
                    catch (LoginRefusedException e) {
                        yahooSession.reset();
                        Log.warn("Yahoo login failed for " + registration.getJID());
                    }
                    catch (IOException e) {
                        Log.error("Yahoo login caused IO exception: " + e.toString());
                    }
                    loggingIn = false;
                }
            }.run();
        }
    }

    /**
     * Log out of Yahoo.
     */
    public void logOut() {
        try {
            yahooSession.logout();
        }
        catch (IOException e) {
            Log.debug("Failed to log out from Yahoo.");
        }
        yahooSession.reset();
        loggedIn = false;
        loggingIn = false;
        loginAttempts = 0;
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(registration.getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
    }

    /**
     * Have we successfully logged in to Yahoo?
     */
    public Boolean isLoggedIn() {
        return loggedIn;
    }

    /**
     * Adds a contact to the user's Yahoo contact list.
     *
     * @param jid JID of contact to be added.
     */
    public void addContact(JID jid) {
        // TODO: check jabber group and use it
        try {
            yahooSession.addFriend(jid.getNode(), "Yahoo Transport");
        }
        catch (IOException e) {
            Log.error("Failed to send message to yahoo user.");
        }
    }

    /**
     * Removes a contact from the user's Yahoo contact list.
     *
     * @param jid JID of contact to be added.
     */
    public void removeContact(JID jid) {
        // TODO: check jabber group and use it
        try {
            yahooSession.removeFriend(jid.getNode(), "Yahoo Transport");
        }
        catch (IOException e) {
            Log.error("Failed to send message to yahoo user.");
        }
    }

    /**
     * Sends a message from the jabber user to a Yahoo contact.
     *
     * @param jid JID of contact to send message to.
     * @param message Message to send to yahoo contact.
     */
    public void sendMessage(JID jid, String message) {
        try {
            yahooSession.sendMessage(jid.getNode(), message);
        }
        catch (IOException e) {
            Log.error("Failed to send message to yahoo user.");
        }
    }

}
