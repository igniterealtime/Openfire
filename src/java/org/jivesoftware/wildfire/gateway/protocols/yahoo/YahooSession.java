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
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import ymsg.network.LoginRefusedException;
import ymsg.network.Session;
import ymsg.network.StatusConstants;
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
     */
    public YahooSession(Registration registration, JID jid, YahooTransport transport) {
        super(registration, jid, transport);

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
                        p.setTo(getJID());
                        p.setFrom(getTransport().getJID());
                        Log.debug("Logged in, sending: " + p.toString());
                        getTransport().sendPacket(p);

                        syncUsers();
                    }
                    catch (LoginRefusedException e) {
                        yahooSession.reset();
                        Log.warn("Yahoo login failed for " + getJID());
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
        p.setTo(getJID());
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
        for (YahooUser user : (Collection<YahooUser>)yahooSession.getUsers().values()) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(user.getId()));

            String custommsg = user.getCustomStatusMessage();
            if (custommsg != null) {
                p.setStatus(custommsg);
            }

            long statusid = user.getStatus();
            if (statusid == StatusConstants.STATUS_AVAILABLE) {
                // We're good, leave the type as blank for available.
            }
            else if (statusid == StatusConstants.STATUS_BRB) {
                p.setShow(Presence.Show.away);
            }
            else if (statusid == StatusConstants.STATUS_BUSY) {
                p.setShow(Presence.Show.dnd);
            }
            else if (statusid == StatusConstants.STATUS_IDLE) {
                p.setShow(Presence.Show.away);
            }
            else if (statusid == StatusConstants.STATUS_OFFLINE) {
                p.setType(Presence.Type.unavailable);
            }
            else if (statusid == StatusConstants.STATUS_NOTATDESK) {
                p.setShow(Presence.Show.away);
            }
            else if (statusid == StatusConstants.STATUS_NOTINOFFICE) {
                p.setShow(Presence.Show.away);
            }
            else if (statusid == StatusConstants.STATUS_ONPHONE) {
                p.setShow(Presence.Show.away);
            }
            else if (statusid == StatusConstants.STATUS_ONVACATION) {
                p.setShow(Presence.Show.xa);
            }
            else if (statusid == StatusConstants.STATUS_OUTTOLUNCH) {
                p.setShow(Presence.Show.xa);
            }
            else if (statusid == StatusConstants.STATUS_STEPPEDOUT) {
                p.setShow(Presence.Show.away);
            }
            else {
                // Not something we handle, we're going to ignore it.
            }

            getTransport().sendPacket(p);
        }
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

    /**
     * Asks for transport to send information about a contact if possible.
     *
     * @param jid JID of contact to be probed.
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

        long statusid = user.getStatus();
        if (statusid == StatusConstants.STATUS_AVAILABLE) {
            // We're good, leave the type as blank for available.
        }
        else if (statusid == StatusConstants.STATUS_BRB) {
            p.setShow(Presence.Show.away);
        }
        else if (statusid == StatusConstants.STATUS_BUSY) {
            p.setShow(Presence.Show.dnd);
        }
        else if (statusid == StatusConstants.STATUS_IDLE) {
            p.setShow(Presence.Show.away);
        }
        else if (statusid == StatusConstants.STATUS_OFFLINE) {
            p.setType(Presence.Type.unavailable);
        }
        else if (statusid == StatusConstants.STATUS_NOTATDESK) {
            p.setShow(Presence.Show.away);
        }
        else if (statusid == StatusConstants.STATUS_NOTINOFFICE) {
            p.setShow(Presence.Show.away);
        }
        else if (statusid == StatusConstants.STATUS_ONPHONE) {
            p.setShow(Presence.Show.away);
        }
        else if (statusid == StatusConstants.STATUS_ONVACATION) {
            p.setShow(Presence.Show.xa);
        }
        else if (statusid == StatusConstants.STATUS_OUTTOLUNCH) {
            p.setShow(Presence.Show.xa);
        }
        else if (statusid == StatusConstants.STATUS_STEPPEDOUT) {
            p.setShow(Presence.Show.away);
        }
        else {
            // Not something we handle, we're going to ignore it.
        }

        getTransport().sendPacket(p);
    }   

}
