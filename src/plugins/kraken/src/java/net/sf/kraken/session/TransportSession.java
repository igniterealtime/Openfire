/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.session;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.avatars.Avatar;
import net.sf.kraken.muc.MUCTransportSessionManager;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.registration.RegistrationHandler;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;
import net.sf.kraken.type.*;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.*;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
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
public abstract class TransportSession<B extends TransportBuddy> {

    static Logger Log = Logger.getLogger(TransportSession.class);

    /**
     * Convenience constructor that includes priority.
     *
     * @param registration Registration this session is associated with.
     * @param jid JID of user associated with this session.
     * @param transport Transport this session is associated with.
     * @param priority Priority associated with session.
     */
    public TransportSession(Registration registration, JID jid, BaseTransport<B> transport, Integer priority) {
        this.jid = new JID(jid.toBareJID());
        this.registration = registration;
        this.transportRef = new WeakReference<BaseTransport<B>>(transport);
        mucSessionManager = new MUCTransportSessionManager<B>(this);
        addResource(jid.getResource(), priority);
        loadAvatar();
        Log.debug("Created "+transport.getType()+" session for "+jid+" as '"+registration.getUsername()+"'");
        
        // note: vcards are supported even if avatars are not!
        setSupportedFeature(SupportedFeature.vcardtemp); 
    }

    /**
     * Registration that this session is associated with.
     */
    public Registration registration;

    /**
     * Transport this session is associated with.
     */
    public WeakReference<BaseTransport<B>> transportRef;

    /**
     * The bare JID the session is associated with.
     */
    public JID jid;

    /**
     * All JIDs (including resources) that are associated with this session.
     */
    public ConcurrentHashMap<String,Integer> resources = new ConcurrentHashMap<String,Integer>();

    /**
     * List of packets that are pending delivery while a session is detached.
     */
    private ArrayList<Packet> pendingPackets = new ArrayList<Packet>();

    /**
     * Current highest resource.
     */
    public String highestResource = null;

    public IQ getRegistrationPacket() {
        return registrationPacket;
    }

    public void setRegistrationPacket(IQ registrationPacket) {
        this.registrationPacket = registrationPacket;
    }

    /**
     * Registration packet that is awaiting a response.
     */
    public IQ registrationPacket = null;

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
     * The current reason behind a connection failure, if one has occurred.
     */
    public ConnectionFailureReason failureStatus = ConnectionFailureReason.NO_ISSUE;

    /**
     * Supported features.
     */
    public ArrayList<SupportedFeature> supportedFeatures = new ArrayList<SupportedFeature>();

    /**
     * Number of reconnection attempts made.
     */
    public Integer reconnectionAttempts = 0;

    /**
     * If set, represents a unix timestamp when the session was detached.  The expectation being it'll get cleaned
     * up if it's been hanging around for too long.
     */
    public long detachTimestamp = 0;

    /**
     * Current presence status.
     */
    public PresenceType presence = PresenceType.unavailable;

    /**
     * Current verbose status.
     */
    public String verboseStatus = "";

    /**
     * Pending presence status
     */
    public PresenceType pendingPresence = null;

    /**
     * Pending verbose status.
     */
    public String pendingVerboseStatus = null;

    /**
     * Transport buddy manager.
     */
    public TransportBuddyManager<B> buddyManager = new TransportBuddyManager<B>(this);

    /**
     * This session's avatar.
     */
    public Avatar avatar;

    /**
     *  The MUC transport session manager.
     */
    public MUCTransportSessionManager<B> mucSessionManager;

    /**
     * Retrieve the muc session manager.
     *
     * @return muc session manager instance.
     */
    public MUCTransportSessionManager<B> getMUCSessionManager() {
        return mucSessionManager;
    }

    /**
     * Retrieve the buddy manager.
     *
     * @return buddy manager instance.
     */
    public TransportBuddyManager<B> getBuddyManager() {
        return buddyManager;
    }

    /**
     * Associates a resource with the session, and tracks it's priority.
     *
     * @param resource Resource string
     * @param priority Priority of resource
     */
    public void addResource(String resource, Integer priority) {
        resources.put(resource, priority);
        if (highestResource == null || resources.get(highestResource) < priority) {
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
        JID retJID = new JID(getJID().getNode(),getJID().getDomain(),resource);
        getBuddyManager().sendOfflineForAllAvailablePresences(retJID);
        // Send unavailable message to resource that went offline
        Presence p = new Presence();
        p.setType(Presence.Type.unavailable);
        p.setTo(retJID);
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
        // Recalculate the highest resource
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
     * Detaches the session, leaving it running in the background and "suspended".
     */
    public void detachSession() {
        detachTimestamp = new Date().getTime();
    }

    /**
     * Attaches the session, indicating that it's actively used.
     */
    public void attachSession() {
        detachTimestamp = 0;
        for (Packet p : pendingPackets) {
            getTransport().sendPacket(p);
        }
        pendingPackets.clear();
    }

    /**
     * Stores a pending packet for later delivery
     */
    public void storePendingPacket(Packet p) {
        pendingPackets.add(p);
    }

    /**
     * Retrieves the detach timestamp for determining if the session is detached, and if so how long it's been
     * detached.
     */
    public long getDetachTimestamp() {
        return detachTimestamp;
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
    public BaseTransport<B> getTransport() {
        return transportRef.get();
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
        boolean currentHighest = false;
        if (isHighestPriority(resource)) {
            currentHighest = true;
        }
        updateResource(resource, priority);
        if (currentHighest && !isHighestPriority(resource)) {
            Presence p = new Presence(Presence.Type.probe);
            p.setTo(getJIDWithHighestPriority());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
        }
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
    public boolean hasResource(String resource) {
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
     * If there is a pending presence set, it will automatically commit the pending presence.
     *
     * @param status New login status.
     */
    public void setLoginStatus(TransportLoginStatus status) {
        loginStatus = status;
        if (status.equals(TransportLoginStatus.LOGGED_IN)) {
            reconnectionAttempts = 0;
            setFailureStatus(ConnectionFailureReason.NO_ISSUE);
            getRegistration().setLastLogin(new Date());
            if (pendingPresence != null && pendingVerboseStatus != null) {
                setPresenceAndStatus(pendingPresence, pendingVerboseStatus);
                pendingPresence = null;
                pendingVerboseStatus = null;
            }
            if (getRegistrationPacket() != null) {
                new RegistrationHandler(getTransport()).completeRegistration(this);
            }
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


    public ConnectionFailureReason getFailureStatus() {
        return failureStatus;
    }

    public void setFailureStatus(ConnectionFailureReason failureStatus) {
        this.failureStatus = failureStatus;
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
     * Should be called when a session has been disconnected.
     *
     * This can be anything from a standard logout to a forced disconnect from the server.
     *
     * @param errorMessage Error message to send, or null if no message.  (only sent on full disconnect)
     */
    public void sessionDisconnected(String errorMessage) {
        reconnectionAttempts++;
        if (getRegistrationPacket() != null || !JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+"reconnect", true) || (reconnectionAttempts > JiveGlobals.getIntProperty("plugin.gateway."+getTransport().getType()+"reconnectattempts", 3))) {
            sessionDisconnectedNoReconnect(errorMessage);
        }
        else {
            cleanUp();
            Log.debug("Session "+getJID()+" disconnected from "+getTransport().getJID()+".  Reconnecting... (attempt "+reconnectionAttempts+")");
            setLoginStatus(TransportLoginStatus.RECONNECTING);
            ClientSession session = XMPPServer.getInstance().getSessionManager().getSession(getJIDWithHighestPriority());
            if (session != null) {
                logIn(getTransport().getPresenceType(session.getPresence()), null);
            }
            else {
                sessionDisconnectedNoReconnect(errorMessage);
            }
        }
    }

    /**
     * Should be called when a session has been disconnected but no reconnect attempt should be made.
     *
     * It is also called internally by sessionDisconnected to handle total failed attempt.
     *
     * @param errorMessage Error message to send, or null if no message.
     */
    public void sessionDisconnectedNoReconnect(String errorMessage) {
        Log.debug("Disconnecting session "+getJID()+" from "+getTransport().getJID());
        try {
            cleanUp();
        }
        catch (Exception e) {
            Log.info("sessionDisconnectedNoReconnect: Error="+ e);
        }
        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
        if (getRegistrationPacket() != null) {
            new RegistrationHandler(getTransport()).completeRegistration(this);
        }
        else {
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
            if (errorMessage != null) {
                getTransport().sendMessage(
                        getJIDWithHighestPriority(),
                        getTransport().getJID(),
                        errorMessage,
                        Message.Type.error
                );
            }
            getBuddyManager().sendOfflineForAllAvailablePresences(getJID());
        }
        buddyManager.resetBuddies();
        getTransport().getSessionManager().removeSession(getJID());
    }

    /**
     * Retrieves the current status.
     *
     * @return Current status setting.
     */
    public PresenceType getPresence() {
        return presence;
    }

    /**
     * Sets the current status.
     *
     * @param newpresence New presence to set to.
     */
    public void setPresence(PresenceType newpresence) {
        if (newpresence == null) {
            newpresence = PresenceType.unknown;
        }
        if (newpresence.equals(PresenceType.unavailable)) {
            verboseStatus = "";
        }
        if (!presence.equals(newpresence)) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().setUpPresencePacket(p, newpresence);
            if (!verboseStatus.equals("")) {
                p.setStatus(verboseStatus);
            }
            getTransport().sendPacket(p);
        }
        presence = newpresence;
    }

    /**
     * Retrieves the current verbose status.
     *
     * @return Current verbose status.
     */
    public String getVerboseStatus() {
        return verboseStatus;
    }

    /**
     * Sets the current verbose status.
     *
     * @param newstatus New verbose status.
     */
    public void setVerboseStatus(String newstatus) {
        if (newstatus == null) {
            newstatus = "";
        }
        if (!verboseStatus.equals(newstatus)) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().setUpPresencePacket(p, presence);
            if (!newstatus.equals("")) {
                p.setStatus(newstatus);
            }
            getTransport().sendPacket(p);
        }
        verboseStatus = newstatus;
    }

    /**
     * Convenience routine to set both presence and verbose status at the same time.
     *
     * @param newpresence New presence to set to.
     * @param newstatus New verbose status.
     */
    public void setPresenceAndStatus(PresenceType newpresence, String newstatus) {
        Log.debug("Updating status ["+newpresence+","+newstatus+"] for "+this);
        if (newpresence == null) {
            newpresence = PresenceType.unknown;
        }
        if (newstatus == null) {
            newstatus = "";
        }
        if (newpresence.equals(PresenceType.unavailable)) {
            newstatus = "";
        }
        if (!presence.equals(newpresence) || !verboseStatus.equals(newstatus)) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().setUpPresencePacket(p, newpresence);
            if (!newstatus.equals("")) {
                p.setStatus(newstatus);
            }
            getTransport().sendPacket(p);
        }
        presence = newpresence;
        verboseStatus = newstatus;
    }

    /**
     * Sets a pending presence and verbose status that will trigger when login status is LOGGED_IN.
     *
     * @param newpresence New presence to set to.
     * @param newstatus New verbose status.
     */
    public void setPendingPresenceAndStatus(PresenceType newpresence, String newstatus) {
        if (newpresence == null) {
            newpresence = PresenceType.unknown;
        }
        if (newstatus == null) {
            newstatus = "";
        }
        if (newpresence.equals(PresenceType.unavailable)) {
            newstatus = "";
        }
        pendingPresence = newpresence;
        pendingVerboseStatus = newstatus;
    }

    /**
     * Sends the current presence to the session user.
     *
     * @param to JID to send presence updates to.
     */
    public void sendPresence(JID to) {
        Presence p = new Presence();
        p.setTo(to);
        p.setFrom(getTransport().getJID());
        getTransport().setUpPresencePacket(p, presence);
        p.setStatus(verboseStatus);
        getTransport().sendPacket(p);
    }

    /**
     * Sends the current presence only if it's not unavailable.
     *
     * @param to JID to send presence updates to.
     */
    public void sendPresenceIfAvailable(JID to) {
       if (!presence.equals(PresenceType.unavailable)) {
           sendPresence(to);
       }
    }

    /**
     * Retrieves the avatar for this session.
     *
     * @return Avatar instance associated with the JID of this session.
     */
    public Avatar getAvatar() {
        return avatar;
    }

    /**
     * Sets the avatar associated with this session.
     *
     * @param avatar instance to associate with this session.
     */
    public void setAvatar(Avatar avatar) {
        this.avatar = avatar;
    }

    /**
     * Loads an avatar if one is available.
     *
     * Pulls from cache.  If nothing is in cache, we attempt to check their vcard info.
     */
    private void loadAvatar() {
        if (JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".avatars", true)) {
            try {
                this.avatar = new Avatar(jid);
            }
            catch (NotFoundException e) {
                Element vcardElem = VCardManager.getInstance().getVCard(jid.getNode());
                if (vcardElem != null) {
                    Element photoElem = vcardElem.element("PHOTO");
                    if (photoElem != null) {
                        Element typeElem = photoElem.element("TYPE");
                        Element binElem = photoElem.element("BINVAL");
                        if (typeElem != null && binElem != null) {
                            byte[] imageData = Base64.decode(binElem.getText());
                            this.avatar = new Avatar(jid, imageData);
                        }
                    }
                }
            }
        }
    }

    /**
     * Provides a "neat" string representation of the session.
     */
    @Override
    public String toString() {
        return "TransportSession["+getJID()+"]";
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
     * @param jid JID associated with the legacy contact.
     * @param nickname Nickname associated with the legacy contact.
     * @param groups Groups associated with the legacy contact.
     */
    public abstract void addContact(JID jid, String nickname, ArrayList<String> groups);

    /**
     * Removes a legacy contact from the legacy service.
     *
     * @param contact Transport buddy item associated with the legacy contact.
     */
    public abstract void removeContact(B contact);

    /**
     * Updates a legacy contact on the legacy service.
     *
     * @param contact Transport buddy item associated with the legacy contact.
     */
    public abstract void updateContact(B contact);

    /**
     * Accept a legacy contact's add friend request.
     *
     * @param jid JID associated with the target contact.
     */
    public abstract void acceptAddContact(JID jid);

    /**
     * Sends an outgoing message through the legacy service.
     *
     * @param jid JID associated with the target contact.
     * @param message Message to be sent.
     */
    public abstract void sendMessage(JID jid, String message);

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
     * Sends a buzz notification through the legacy service.
     *
     * If the legacy service does not support this, ignore it.  Though sometimes a message
     * might be included and you may want to handle that in some sort of special way.
     *
     * @param jid JID associated with the target contact.
     * @param message Message tied to the buzz.
     */
    public abstract void sendBuzzNotification(JID jid, String message);

    /**
     * Updates the session's avatar on the legacy service.
     *
     * If the legacy service does not support this, ignore it.
     *
     * @param type Mime type of image.
     * @param data Binary data (byte array) of image.
     */
    public abstract void updateLegacyAvatar(String type, byte[] data);

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

    /**
     * Retrieves a list of rooms (MUCTransportRoom) that are on the server the session is attached to.
     *
     * Because of the nature of the query, the legacy service is expected to send a response itself,
     * instead of returning a list of information.  The sendRooms command in BaseMUCTransport exists to
     * facilitate this easily.
     *
     * This will never get called unless MUC support is implemented and is optional for non-MUC transports.
     */
    public void getRooms() {
        getTransport().getMUCTransport().cancelPendingRequest(getJID(), getTransport().getMUCTransport().getJID(), NameSpace.DISCO_ITEMS);
    }


    /**
     * Retrieves information about a specific room (MUCTransportRoom).
     *
     * Because of the nature of the query, the legacy service is expected to send a response itself,
     * instead of returning a list of information.  The sendRoomInfo command in BaseMUCTransport exists to
     * facilitate this easily.
     *
     * Override this if you support it.
     *
     * @param room Room to get information about.
     */
    public void getRoomInfo(String room) {
        getTransport().getMUCTransport().cancelPendingRequest(getJID(), getTransport().getMUCTransport().convertIDToJID(room, null), NameSpace.DISCO_INFO);
    }

    /**
     * Retrieves members of a specific room (MUCTransportRoom).
     *
     * Because of the nature of the query, the legacy service is expected to send a response itself,
     * instead of returning a list of information.  The sendRoomMembers command in BaseMUCTransport exists to
     * facilitate this easily.
     *
     * Override this if you support it.
     *
     * @param room Room to get members of.
     */
    public void getRoomMembers(String room) {
        getTransport().getMUCTransport().cancelPendingRequest(getJID(), getTransport().getMUCTransport().convertIDToJID(room, null), NameSpace.DISCO_ITEMS);
    }

}
