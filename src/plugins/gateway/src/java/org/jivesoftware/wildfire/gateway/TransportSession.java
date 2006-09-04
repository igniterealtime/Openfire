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
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.roster.RosterItem;

import java.util.TreeMap;

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
    public TreeMap<Integer,String> resources = new TreeMap<Integer,String>();

    /**
     * Is this session valid?  Set to false when session is done.
     */
    public boolean validSession = true;

    /**
     * Associates a resource with the session, and tracks it's priority.
     */
    public void addResource(String resource, Integer priority) {
        resources.put(priority, resource);
    }

    /**
     * Removes an association of a resource with the session.
     */
    public void removeResource(String resource) {
        for (Integer i : resources.keySet()) {
            if (resources.get(i).equals(resource)) {
                resources.remove(i);
                try {
                    getTransport().notifyRosterOffline(new JID(getJID().getNode(),getJID().getDomain(),resource));
                }
                catch (UserNotFoundException e) {
                    // Don't care
                }
                break;
            }
        }
    }

    /**
     * Removes all resources associated with a session.
     */
    public void removeAllResources() {
        for (String resource : resources.values()) {
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
     * Retrieves the registration information associated with the session.
     *
     * @return Registration information of the user associated with the session.
     */
    public Registration getRegistration() {
        return registration;
    }

    /**
     * Retrieves the transport associated wtih the session.
     *
     * @return Transport associated with the session.
     */
    public BaseTransport getTransport() {
        return transport;
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
        return new JID(jid.getNode(),jid.getDomain(),resources.get(resources.lastKey()));
    }

    /**
     * Given a resource, returns whether it's priority is the highest.
     *
     * @param resource Resource to be checked.
     * @return True or false if the resource is the highest priority.
     */
    public Boolean isHighestPriority(String resource) {
        return (resources.get(resources.lastKey()).equals(resource));
    }

    /**
     * Change the priority of a given resource.
     *
     * @param resource Resource to be changed.
     * @param priority New priority of resource
     */
    public void updatePriority(String resource, Integer priority) {
        removeResource(resource);
        addResource(resource, priority);
    }

    /**
     * Retrieves the priority of a given resource.
     *
     * @param resource Resource to be checked.
     * @return Priority of the resource, or null if not found.
     */
    public Integer getPriority(String resource) {
        for (Integer i : resources.keySet()) {
            if (resources.get(i).equals(resource)) {
                return i;
            }
        }
        return null;
    }

    /**
     * Given a resource, returns whether the resource is currently associated with this session.
     *
     * @param resource Resource to be checked.
     * @return True of false if the resource is associated with this session.
     */
    public Boolean hasResource(String resource) {
        return (resources.containsValue(resource));
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
     * Updates status on legacy service.
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public abstract void updateStatus(PresenceType presenceType, String verboseStatus);

    /**
     * Is the legacy service account logged in?
     *
     * @return True or false if the legacy account is logged in.
     */
    public abstract Boolean isLoggedIn();

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
     * Sends an outgoing message through the legacy serivce.
     *
     * @param jid JID associated with the target contact.
     * @param message Message to be sent.
     */
    public abstract void sendMessage(JID jid, String message);

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

}
