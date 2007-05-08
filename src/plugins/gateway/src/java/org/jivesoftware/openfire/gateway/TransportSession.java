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
import org.xmpp.packet.Presence;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface for a transport session.
 *
 * This outlines all of the functionality that is required for a transport
 * to implement.  These are functions that the XMPP side of things are going
 * interact with.  The legacy transport itself is expected to handle messages
 * going to the Jabber user.
 *
 * @author Daniel Henninger
 */
public abstract class TransportSession implements Runnable {

    /**
     * Creates a TransportSession instance.
     *
     * @param registration Registration this session is associated with.
     * @param jid JID of user associated with this session.
     * @param transport Transport this session is associated with.
     */
    public TransportSession(Registration registration, JID jid, BaseTransport transport) {
        this.jid = new JID(jid.toBareJID());
        this.registration = registration;
        this.transport = transport;
        Log.debug("Created "+transport.getType()+" session for "+jid+" as '"+registration.getUsername()+"'");
    }

    /**
     * Convenience constructor that includes priority.
     *
     * @param registration Registration this session is associated with.
     * @param jid JID of user associated with this session.
     * @param transport Transport this session is associated with.
     * @param priority Priority associated with session.
     */
    public TransportSession(Registration registration, JID jid, BaseTransport transport, Integer priority) {
        this.jid = new JID(jid.toBareJID());
        this.registration = registration;
        this.transport = transport;
        addResource(jid.getResource(), priority);
        Log.debug("Created "+transport.getType()+" session for "+jid+" as '"+registration.getUsername()+"'");
    }

    /**
     * Registration that this session is associated with.
     */
    public Registration registration;

    /**
     * Transport this session is associated with.
     */
    public BaseTransport transport;

    /**
     * The bare JID the session is associated with.
     */
    public JID jid;

    /**
     * All JIDs (including resources) that are associated with this session.
     */
    public ConcurrentHashMap<String,Integer> resources = new ConcurrentHashMap<String,Integer>();

    /**
     * Current highest resource.
     */
    public String highestResource = null;

    /**
     * Is this session valid?  Set to false when session is done.
     */
    public boolean validSession = true;

    /**
     * Is the roster locked for sync editing?
     */
    public boolean rosterLocked = false;

    /**
     * Contains a list of specific roster items that are locked.
     */
    public ArrayList<String> rosterItemsLocked = new ArrayList<String>();

    /**
     * The current login status on the legacy network.
     */
    public TransportLoginStatus loginStatus = TransportLoginStatus.LOGGED_OUT;

    /**
     * Supported features.
     */
    public ArrayList<SupportedFeature> supportedFeatures = new ArrayList<SupportedFeature>();

    /**
     * Number of reconnection attempts made.
     */
    public Integer reconnectionAttempts = 0;

    /**
     * Associates a resource with the session, and tracks it's priority.
     *
     * @param resource Resource string
     * @param priority Priority of resource
     */
    public void addResource(String resource, Integer priority) {
        resources.put(resource, priority);
        if (highestResource == null || resources.get(highestResource) >= priority) {
            highestResource = resource;
        }
    }

    /**
     * Removes an association of a resource with the session.
     *
     * @param resource Resource string
     */
    public void removeResource(String resource) {
        resources.remove(resource);
        try {
            getTransport().notifyRosterOffline(new JID(getJID().getNode(),getJID().getDomain(),resource));
        }
        catch (UserNotFoundException e) {
            // Don't care
        }
        if (resource.equals(highestResource)) {
            Integer highestPriority = -255;
            String tmpHighestResource = null;
            for (String res : resources.keySet()) {
                if (resources.get(res) > highestPriority) {
                    tmpHighestResource = res;
                    highestPriority = resources.get(res);
                }
            }
            highestResource = tmpHighestResource;
        }
    }

    /**
     * Updates the priority of a resource.
     *
     * @param resource Resource string
     * @param priority New priority
     */
    public void updateResource(String resource, Integer priority) {
        resources.put(resource, priority);
        Integer highestPriority = -255;
        String tmpHighestResource = null;
        for (String res : resources.keySet()) {
            if (resources.get(res) > highestPriority) {
                tmpHighestResource = res;
                highestPriority = resources.get(res);
            }
        }
        highestResource = tmpHighestResource;
    }

    /**
     * Removes all resources associated with a session.
     */
    public void removeAllResources() {
        for (String resource : resources.keySet()) {
            removeResource(resource); 
        }
    }

    /**
     * Returns the number of active resources.
     *
     * @return Number of active resources.
     */
    public int getResourceCount() {
        return resources.size();
    }

    /**
     * Returns if the roster is currently locked.
     *
     * @return true or false if the roster is locked.
     */
    public boolean isRosterLocked() {
        return rosterLocked;
    }

    /**
     * Returns if a specific roster item is currently locked.
     *
     * Also checks global lock.
     *
     * @param jid JID to check whether it's locked.
     * @return true or false if the roster item is locked.
     */
    public boolean isRosterLocked(String jid) {
        return rosterLocked || rosterItemsLocked.contains(jid);
    }

    /**
     * Locks the roster (typically used for editing during syncing).
     */
    public void lockRoster() {
        rosterLocked = true;
    }

    /**
     * Locks a specific roster item (typically used for direct roster item updates).
     *
     * @param jid JID to lock.
     */
    public void lockRoster(String jid) {
        if (!rosterItemsLocked.contains(jid)) {
            rosterItemsLocked.add(jid);
        }
    }

    /**
     * Unlocks the roster after sync editing is complete.
     */
    public void unlockRoster() {
        rosterLocked = false;
    }

    /**
     * Unlocks a specific roster item.
     *
     * @param jid JID to unlock.
     */
    public void unlockRoster(String jid) {
        if (rosterItemsLocked.contains(jid)) {
            rosterItemsLocked.remove(jid);
        }
    }

    /**
     * Retrieves the registration information associated with the session.
     *
     * @return Registration information of the user associated with the session.
     */
    public Registration getRegistration() {
        return registration;
    }

    /**
     * Retrieves the transport associated with the session.
     *
     * @return Transport associated with the session.
     */
    public BaseTransport getTransport() {
        return transport;
    }

    /**
     * Retrieves the roster associated with the session.
     *
     * @return Roster associated with the session, or null if none.
     */
    public Roster getRoster() {
        try {
            return getTransport().getRosterManager().getRoster(getJID().getNode());
        }
        catch (UserNotFoundException e) {
            return null;
        }
    }

    /**
     * Retrieves the bare jid associated with the session.
     *
     * @return JID of the user associated with this session.
     */
    public JID getJID() {
        return jid;
    }

    /**
     * Retrieves the JID of the highest priority resource.
     *
     * @return Full JID including resource with highest priority.
     */
    public JID getJIDWithHighestPriority() {
        return new JID(jid.getNode(),jid.getDomain(),highestResource);
    }

    /**
     * Given a resource, returns whether it's priority is the highest.
     *
     * @param resource Resource to be checked.
     * @return True or false if the resource is the highest priority.
     */
    public Boolean isHighestPriority(String resource) {
        return (highestResource.equals(resource));
    }

    /**
     * Change the priority of a given resource.
     *
     * @param resource Resource to be changed.
     * @param priority New priority of resource
     */
    public void updatePriority(String resource, Integer priority) {
        updateResource(resource, priority);
        // TODO: should potentially ask for status of highest priority
    }

    /**
     * Retrieves the priority of a given resource.
     *
     * @param resource Resource to be checked.
     * @return Priority of the resource, or null if not found.
     */
    public Integer getPriority(String resource) {
        return resources.get(resource);
    }

    /**
     * Given a resource, returns whether the resource is currently associated with this session.
     *
     * @param resource Resource to be checked.
     * @return True of false if the resource is associated with this session.
     */
    public Boolean hasResource(String resource) {
        return (resources.containsKey(resource));
    }

    /**
     * Sets a feature that the client supports.
     *
     * @param feature Feature that the session supports.
     */
    public void setSupportedFeature(SupportedFeature feature) {
        if (!supportedFeatures.contains(feature)) {
            supportedFeatures.add(feature);
        }
    }

    /**
     * Removes a feature that the client supports.
     *
     * @param feature Feature to be removed from the supported list.
     */
    public void removeSupportedFeature(SupportedFeature feature) {
        supportedFeatures.remove(feature);
    }

    /**
     * Clears all of the supported features recorded.
     */
    public void clearSupportedFeatures() {
        supportedFeatures.clear();
    }

    /**
     * Retrieves whether this session supports a specific feature.
     *
     * @param feature Feature to check for support of.
     * @return True or false if the session supports the specified feature.
     */
    public Boolean isFeatureSupported(SupportedFeature feature) {
        return supportedFeatures.contains(feature);
    }

    /**
     * Updates the login status.
     *
     * @param status New login status.
     */
    public void setLoginStatus(TransportLoginStatus status) {
        loginStatus = status;
        if (status.equals(TransportLoginStatus.LOGGED_IN)) {
            reconnectionAttempts = 0;
        }
    }

    /**
     * Retrieves the current login status.
     *
     * @return Login status of session.
     */
    public TransportLoginStatus getLoginStatus() {
        return loginStatus;
    }

    /**
     * Returns true only if we are completely logged in.
     *
     * @return True or false whether we are currently completely logged in.
     */
    public Boolean isLoggedIn() {
        return (loginStatus == TransportLoginStatus.LOGGED_IN);
    }

    /**
     * Handles monitoring of whether session is still valid.
     */
    public void run() {
        while (validSession) { }
    }

    /**
     * Indicates that the session is done and should be stopped.
     */
    public void sessionDone() {
        validSession = false;
    }

    /**
     * Should be called when a session has been disconnected.
     *
     * This can be anything from a standard logout to a forced disconnect from the server.
     */
    public void sessionDisconnected() {
        reconnectionAttempts++;
        cleanUp();
        if (reconnectionAttempts > JiveGlobals.getIntProperty("plugin.gateway."+getTransport().getType()+"reconnectattempts", 3)) {
            sessionDisconnectedNoReconnect();
        }
        else {
            setLoginStatus(TransportLoginStatus.RECONNECTING);
            ClientSession session = XMPPServer.getInstance().getSessionManager().getSession(getJIDWithHighestPriority());
            logIn(getTransport().getPresenceType(session.getPresence()), null);
        }
    }

    /**
     * Should be called when a session has been disconnected but no reconnect attempt should be made.
     *
     * It is also called internally by sessionDisconnected to handle total failed attempt.
     */
    public void sessionDisconnectedNoReconnect() {
        Log.debug("Disconnecting session "+getJID()+" from "+getTransport().getJID());
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
        try {
            getTransport().notifyRosterOffline(getJID());
        }
        catch (UserNotFoundException e) {
            // Don't care
        }
    }

    /**
     * Updates status on legacy service.
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public abstract void updateStatus(PresenceType presenceType, String verboseStatus);

    /**
     * Adds a legacy contact to the legacy service.
     *
     * @param item Roster item associated with the legacy contact.
     */
    public abstract void addContact(RosterItem item);

    /**
     * Removes a legacy contact from the legacy service.
     *
     * @param item Roster item associated with the legacy contact.
     */
    public abstract void removeContact(RosterItem item);

    /**
     * Updates a legacy contact on the legacy service.
     *
     * @param item Roster item associated with the legacy contact.
     */
    public abstract void updateContact(RosterItem item);

    /**
     * Sends an outgoing message through the legacy service.
     *
     * @param jid JID associated with the target contact.
     * @param message Message to be sent.
     */
    public abstract void sendMessage(JID jid, String message);

    /**
     * Sends an outgoing message directly to the legacy service.
     *
     * Doesn't -have- to do anything.  Only occasionally useful.
     *
     * @param message Message to be sent.
     */
    public abstract void sendServerMessage(String message);

    /**
     * Sends a chat state message through the legacy service.
     *
     * Not all chat states have to be handled.  Note that composing message event
     * is sent through this as well.  (XEP-0022)  Primarily this is used with XEP-0085.
     *
     * @param jid JID associated with the target contact.
     * @param chatState Chat state to be reflected in the legacy service.
     */
    public abstract void sendChatState(JID jid, ChatStateType chatState);

    /**
     * Asks the legacy service to send a presence packet for a contact.
     *
     * This is typically response to a probe.
     *
     * @param jid JID to be checked.
    */
    public abstract void retrieveContactStatus(JID jid);

    /**
     * Asks the legacy service to send presence packets for all known contacts.
     *
     * @param jid JID to have the presence packets sent to.
     */
    public abstract void resendContactStatuses(JID jid);

    /**
     * Should be called when the service is to be logged into.
     *
     * This is expected to check for current logged in status and log in if appropriate.
     *
     * @param presenceType Initial status (away, available, etc) to be set upon logging in.
     * @param verboseStatus Descriptive status to be set upon logging in.
     */
    public abstract void logIn(PresenceType presenceType, String verboseStatus);

    /**
     * Should be called when the service is to be disconnected from.
     *
     * This is expected to check for current logged in status and log out if appropriate.
     */
    public abstract void logOut();

    /**
     * Clean up session pieces for either a log out or in preparation for a reconnection.
     */
    public abstract void cleanUp();

}
