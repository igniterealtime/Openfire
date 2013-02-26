/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.roster;

import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.PresenceType;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;

/**
 * Manager for all legacy buddies on a legacy service.
 *
 * This stores an entire "roster" worth of contacts associated with a particular session.
 * 
 * @author Daniel Henninger
 */
public class TransportBuddyManager<B extends TransportBuddy> {

    static Logger Log = Logger.getLogger(TransportBuddyManager.class);

    /**
     * Creates the transport buddy manager instance and initializes.
     *
     * @param session Transport session associated with this buddy manager.
     */
    public TransportBuddyManager(TransportSession<B> session) {
        this.sessionRef = new WeakReference<TransportSession<B>>(session);
    }

    private final ConcurrentHashMap<JID,B> buddies = new ConcurrentHashMap<JID,B>();
    private final ConcurrentHashMap<JID,PresenceType> pendingPresences = new ConcurrentHashMap<JID,PresenceType>();
    private final ConcurrentHashMap<JID,String> pendingVerboseStatuses = new ConcurrentHashMap<JID,String>();

    private WeakReference<TransportSession<B>> sessionRef = null;

    private boolean isActive = false;

    public TransportSession<B> getSession() {
        return sessionRef.get();
    }

    /**
     * Has the buddy manager been activated?
     *
     * @return True or false if the buddy manager has been activated.
     */
    public boolean isActivated() {
        return isActive;
    }

    /**
     * Activates the full functionality of the buddy manager and sends available presences to client.
     */
    public synchronized void activate() {
        for (JID jid : pendingPresences.keySet()) {
            if (pendingVerboseStatuses.containsKey(jid)) {
                try {
                    B buddy = getBuddy(jid);
                    buddy.setPresenceAndStatus(pendingPresences.get(jid), pendingVerboseStatuses.get(jid));
                }
                catch (NotFoundException e) {
                    // Alrighty then....
                }
                pendingVerboseStatuses.remove(jid);
            }
            else {
                try {
                    B buddy = getBuddy(jid);
                    buddy.setPresence(pendingPresences.get(jid));
                }
                catch (NotFoundException e) {
                    // Alrighty then....
                }
            }
        }
        for (JID jid : pendingVerboseStatuses.keySet()) {
            try {
                B buddy = getBuddy(jid);
                buddy.setVerboseStatus(pendingVerboseStatuses.get(jid));
            }
            catch (NotFoundException e) {
                // Alrighty then....
            }
        }
        pendingPresences.clear();
        pendingVerboseStatuses.clear();
        isActive = true;
        sendAllAvailablePresences(getSession().getJID());
    }

    /**
     * Stores a status setting to be recorded after the buddy manager has been activated.
     * This is typically used when status's arrive before the buddy list is activated.
     *
     * @param jid JID of the contact to set status for later.
     * @param presence Presence of contact.
     * @param status verbose status string of contact.
     */
    public synchronized void storePendingStatus(JID jid, PresenceType presence, String status) {
        if (!isActivated()) {
            pendingPresences.put(jid, presence);
            pendingVerboseStatuses.put(jid, status);
        }
        else {
            try {
                B buddy = getBuddy(jid);
                buddy.setPresenceAndStatus(presence, status);
            }
            catch (NotFoundException e) {
                // Alrighty then....
            }
        }
    }

    /**
     * Resets (clears) the list of buddies assigned to the buddy manager.
     */
    public void resetBuddies() {
        buddies.clear();
    }

    /**
     * Retrieve the buddy instance for a given JID.
     *
     * @param jid JID of the buddy to be retrieved.
     * @throws NotFoundException if the given jid is not found.
     * @return TransportBuddy instance requested.
     */
    public B getBuddy(JID jid) throws NotFoundException {
        B buddy = buddies.get(jid);
        if (buddy == null) {
            throw new NotFoundException("Could not find buddy requested.");
        }
        return buddy;
    }
    
    /**
     * Retrieve the buddy instance for a given user.
     *
     * @param username Username of the buddy to be retrieved.
     * @throws NotFoundException if the given username is not found.
     * @return TransportBuddy instance requested.
     */
// Disabling this because it can cause confusion in one's attempt to match a buddy.
//    public B getBuddy(String username) throws NotFoundException {
//        B buddy = buddies.get(username.toLowerCase());
//        if (buddy == null) {
//            throw new NotFoundException("Could not find buddy requested.");
//        }
//        return buddy;
//    }

    /**
     * Stores a new buddy instance in the buddy list.
     *
     * @param buddy TransportBuddy associated with the username.
     */
    public void storeBuddy(B buddy) {
        if (!buddies.containsKey(buddy.jid)) {
            Log.debug("("+getSession().getTransport().getType().toString().toUpperCase()+") Storing new buddy: "+buddy);
            buddies.put(buddy.jid, buddy);
            if (isActivated()) {
                getSession().lockRoster(buddy.getJID().toString());
                try {
                    getSession().getTransport().addOrUpdateRosterItem(getSession().getJID(), buddy.getJID(), buddy.getNickname(), buddy.getGroups());
                }
                catch (UserNotFoundException e) {
                    Log.error("TransportBuddyManager: Unable to find roster when adding contact.");
                } finally {
                    getSession().unlockRoster(buddy.getJID().toString());
                }
            }
        }
        else {
            Log.debug("("+getSession().getTransport().getType().toString().toUpperCase()+") Replacing buddy: "+buddy);
            buddies.put(buddy.jid, buddy);
        }
    }

    /**
     * Removes a buddy instance from the buddy list.
     *
     * @param username buddy to be removed.
     */
    public void removeBuddy(String username) {
        B buddy = buddies.remove(getSession().getTransport().convertIDToJID(username));
        if (buddy != null && isActivated()) {
            Log.debug("TransportBuddyManager: Triggering contact removal for "+buddy);
            getSession().removeContact(buddy);
        }
    }

    /**
     * Retrieves a collection of all buddies.
     *
     * @return List of buddies.
     */
    public Collection<B> getBuddies() {
        return buddies.values();
    }

    /**
     * Conduit for sending presence packets, so that packets can be held at bay temporarily.
     *
     * @param packet Packet to send.
     */
    public void sendPacket(Packet packet) {
        if (isActivated()) {
            getSession().getTransport().sendPacket(packet);
        }
    }

    /**
     * Sends all presences regardless of status.
     *
     * Typically used to send initial presences.
     *
     * @param to JID to send presence updates to.
     */
    public void sendAllPresences(JID to) {
        for (B buddy : buddies.values()) {
            buddy.sendPresence(to);
        }
    }

    /**
     * Sends all presences that are not unavailable.
     *
     * Typically used to send initial presences.
     *
     * @param to JID to send presence updates to.
     */
    public void sendAllAvailablePresences(JID to) {
        for (B buddy : buddies.values()) {
            buddy.sendPresenceIfAvailable(to);
        }
    }

    /**
     * Sends all presences that are not unavailable.
     *
     * Typically used to send initial presences.
     *
     * @param to JID to send presence updates to.
     */
    public void sendOfflineForAllAvailablePresences(JID to) {
        for (B buddy : buddies.values()) {
            buddy.sendOfflinePresenceIfAvailable(to);
        }
    }

    public boolean hasBuddy(JID jid) {
        return buddies.containsKey(jid);
    }
}
