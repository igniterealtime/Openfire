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
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import ymsg.network.LoginRefusedException;
import ymsg.network.Session;
import ymsg.network.YahooGroup;
import ymsg.network.YahooUser;

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
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session
     * @param priority Priority of this session
     */
    public YahooSession(Registration registration, JID jid, YahooTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

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
     * Stored Last Presence Type
     */
    public PresenceType presenceType = null;

    /**
     * Stored Last Verbose Status
     */
    public String verboseStatus = null;

    /**
     * Log in to Yahoo.
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Long representation of status.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
        final PresenceType pType = presenceType;
        if (!isLoggedIn() && !loggingIn && loginAttempts <= 3) {
            loggingIn = true;
            new Thread() {
                public void run() {
                    try {
                        loginAttempts++;
                        yahooSession.login(registration.getUsername(), registration.getPassword());
                        loggedIn = true;

                        Presence p = new Presence();
                        p.setTo(getJID());
                        p.setFrom(getTransport().getJID());
                        Log.debug("Logged in, sending: " + p.toString());
                        getTransport().sendPacket(p);

                        yahooSession.setStatus(((YahooTransport)getTransport()).convertJabStatusToYahoo(pType));

                        getRegistration().setLastLogin(new Date());

                        syncUsers();
                    }
                    catch (LoginRefusedException e) {
                        yahooSession.reset();
                        Log.warn("Yahoo login failed for " + getJID());
                    }
                    catch (IOException e) {
                        Log.error("Yahoo login caused IO exception:", e);
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
        loggedIn = false;
        loggingIn = false;
        loginAttempts = 0;        
        try {
            yahooSession.logout();
        }
        catch (IOException e) {
            Log.debug("Failed to log out from Yahoo.");
        }
        yahooSession.reset();
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#isLoggedIn()
     */
    public Boolean isLoggedIn() {
        return loggedIn;
    }

    /**
     * Syncs up the yahoo roster with the jabber roster.
     */
    public void syncUsers() {
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (YahooGroup group : yahooSession.getGroups()) {
            for (Enumeration e = group.getMembers().elements(); e.hasMoreElements();) {
                YahooUser user = (YahooUser)e.nextElement();
                legacyusers.add(new TransportBuddy(user.getId(), user.getId(), group.getName()));
            }
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync yahoo contact list for " + getJID());
        }

        // Ok, now lets check presence
        for (Object userObj : yahooSession.getUsers().values()) {
            YahooUser user = (YahooUser)userObj;
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(user.getId()));

            String custommsg = user.getCustomStatusMessage();
            if (custommsg != null) {
                p.setStatus(custommsg);
            }

            ((YahooTransport)getTransport()).setUpPresencePacket(p, user.getStatus());
            getTransport().sendPacket(p);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        // @todo check jabber group and use it
        try {
            yahooSession.addFriend(item.getJid().getNode(), "Yahoo Transport");
        }
        catch (IOException e) {
            Log.error("Failed to add yahoo user.");
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        // @todo check jabber group and use it
        try {
            yahooSession.removeFriend(item.getJid().getNode(), "Yahoo Transport");
        }
        catch (IOException e) {
            Log.error("Failed to remove yahoo user.");
        }
    }

       /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        // TODO: Do something here.
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        try {
            yahooSession.sendMessage(jid.getNode(), message);
        }
        catch (IOException e) {
            Log.error("Failed to send message to yahoo user.");
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        try {
            yahooSession.setStatus(((YahooTransport)getTransport()).convertJabStatusToYahoo(presenceType));
        }
        catch (Exception e) {
            Log.error("Unable to set Yahoo Status:", e);
        }
        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        YahooUser user = yahooSession.getUser(jid.getNode());
        Presence p = new Presence();
        p.setTo(getJID());
        p.setFrom(getTransport().convertIDToJID(user.getId()));

        String custommsg = user.getCustomStatusMessage();
        if (custommsg != null) {
            p.setStatus(custommsg);
        }

        ((YahooTransport)getTransport()).setUpPresencePacket(p, user.getStatus());
        getTransport().sendPacket(p);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        for (Object userObj : yahooSession.getUsers().values()) {
            YahooUser user = (YahooUser)userObj;
            Presence p = new Presence();
            p.setTo(jid);
            p.setFrom(getTransport().convertIDToJID(user.getId()));

            String custommsg = user.getCustomStatusMessage();
            if (custommsg != null) {
                p.setStatus(custommsg);
            }

            ((YahooTransport)getTransport()).setUpPresencePacket(p, user.getStatus());
            getTransport().sendPacket(p);
        }
    }

}
