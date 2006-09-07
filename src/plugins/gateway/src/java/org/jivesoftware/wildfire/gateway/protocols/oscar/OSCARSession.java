/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.kano.joscar.flapcmd.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.*;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;
import net.kano.joscar.snaccmd.loc.*;
import net.kano.joscar.snaccmd.ssi.*;
import net.kano.joscar.ssiitem.*;
import net.kano.joscar.ByteBlock;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * Represents an OSCAR session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with OSCAR (AIM/ICQ).
 *
 * Yeesh, this is the one I'm most familiar with and yet it's the ugliest.
 * This needs some housecleaning.
 * 
 * @author Daniel Henninger
 */
public class OSCARSession extends TransportSession {

    /**
     * Initialize a new session object for OSCAR
     * 
     * @param registration The registration information to use during login.
     * @param jid The JID associated with this session.
     * @param transport The transport that created this session.
     * @param priority Priority of this session.
     */
    public OSCARSession(Registration registration, JID jid, OSCARTransport transport, Integer priority) {
        super(registration, jid, transport, priority);
    }

    private BOSConnection bosConn = null;
    private Set<ServiceConnection> services = new HashSet<ServiceConnection>();
    private Boolean loggedIn = false;
    private PresenceType presenceType = null;
    private String verboseStatus = null;
    
    /**
     * SSI tracking variables.
     */
    private ConcurrentHashMap<String,BuddyItem> buddies = new ConcurrentHashMap<String,BuddyItem>();
    private ConcurrentHashMap<Integer,GroupItem> groups = new ConcurrentHashMap<Integer,GroupItem>();
    private ConcurrentHashMap<Integer,Integer> highestBuddyIdPerGroup = new ConcurrentHashMap<Integer,Integer>();
    private Integer highestGroupId = -1;

    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!isLoggedIn()) {
            LoginConnection loginConn = new LoginConnection("login.oscar.aol.com", 5190, this);
            loginConn.connect();

            loggedIn = true;

            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);

            this.presenceType = presenceType;
            this.verboseStatus = verboseStatus;
        } else {
            Log.warn(this.jid + " is already logged in");
        }
    }
    
    public Boolean isLoggedIn() {
        return loggedIn;
    }
    
    public synchronized void logOut() {
        if (isLoggedIn()) {
            if (bosConn != null) {
                bosConn.disconnect();
            }
            loggedIn = false;
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
        }
    }

    /**
     * Finds the id number of a group specified or creates a new one and returns that id.
     *
     * @param groupName Name of the group we are looking for.
     * @return Id number of the group.
     */
    public Integer getGroupIdOrCreateNew(String groupName) {
        for (GroupItem g : groups.values()) {
            if (groupName.equals(g.getGroupName())) {
                return g.getId();
            }
        }

        // Group doesn't exist, lets create a new one.
        Integer newGroupId = highestGroupId + 1;
        GroupItem newGroup = new GroupItem(groupName, newGroupId);
        request(new CreateItemsCmd(new SsiItem[] { newGroup.toSsiItem() }));
        highestGroupId = newGroupId;
        groups.put(newGroupId, newGroup);

        return newGroupId;
    }

    /**
     * Synchronizes the list of groups a contact is a member of, updating nicknames in
     * the process.
     *
     * @param contact Screen name/UIN of the contact.
     * @param nickname Nickname of the contact (should not be null)
     * @param grouplist List of groups the contact should be a member of.
     */
    public void syncContactGroupsAndNickname(String contact, String nickname, List<String> grouplist) {
        if (grouplist.isEmpty()) {
            grouplist.add("Transport Buddies");
        }
        // First, lets take the known good list of groups and sync things up server side.
        for (String group : grouplist) {
            Integer groupId = getGroupIdOrCreateNew(group);

            Integer newBuddyId = 1;
            if (highestBuddyIdPerGroup.containsKey(groupId)) {
                newBuddyId = highestBuddyIdPerGroup.get(groupId) + 1;
            }
            highestBuddyIdPerGroup.put(groupId, newBuddyId);

            BuddyItem newBuddy = new BuddyItem(contact, newBuddyId, groupId);
            newBuddy.setAlias(nickname);
            request(new CreateItemsCmd(new SsiItem[] { newBuddy.toSsiItem() }));
            buddies.put(groupId+"."+newBuddyId, newBuddy);
        }
        // Now, lets clean up any groups this contact should no longer be a member of.
        for (BuddyItem buddy : buddies.values()) {
            if (buddy.getScreenname().equals(contact)) {
                if (buddy.getGroupId() == 0) {
                    // Ok this group is the "main group", which we can cheerfully remove from.
                    request(new DeleteItemsCmd(new SsiItem[] { buddy.toSsiItem() }));
                    buddies.remove(buddy.getGroupId()+"."+buddy.getId());
                }
                else if (!groups.contains(buddy.getGroupId())) {
                    // Well this is odd, a group we don't know about?  Nuke it.
                    request(new DeleteItemsCmd(new SsiItem[] { buddy.toSsiItem() }));
                    buddies.remove(buddy.getGroupId()+"."+buddy.getId());
                }
                else if (!grouplist.contains(groups.get(buddy.getGroupId()).getGroupName())) {
                    request(new DeleteItemsCmd(new SsiItem[] { buddy.toSsiItem() }));
                    buddies.remove(buddy.getGroupId()+"."+buddy.getId());
                }
                else {
                    if (!buddy.getAlias().equals(nickname)) {
                        buddy.setAlias(nickname);
                        request(new ModifyItemsCmd(new SsiItem[] { buddy.toSsiItem() }));
                    }
                }
            }
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        String legacyId = getTransport().convertJIDToID(item.getJid());
        String nickname = item.getNickname();
        if (nickname == null || nickname.equals("")) {
            nickname = legacyId;
        }

        // Syncing takes care of all the dirty work.
        syncContactGroupsAndNickname(legacyId, nickname, item.getGroups());
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        String legacyId = getTransport().convertJIDToID(item.getJid());
        for (BuddyItem i : buddies.values()) {
            if (i.getScreenname().equals(legacyId)) {
                request(new DeleteItemsCmd(new SsiItem[] { i.toSsiItem() }));
                buddies.remove(i.getGroupId()+"."+i.getId());
            }
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        String legacyId = getTransport().convertJIDToID(item.getJid());
        String nickname = item.getNickname();
        if (nickname == null || nickname.equals("")) {
            nickname = legacyId;
        }

        // Syncing takes care of all of the dirty work.
        syncContactGroupsAndNickname(legacyId, nickname, item.getGroups());
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        request(new SendImIcbm(getTransport().convertJIDToID(jid), message));
    }

    /**
     * Opens/creates a new BOS connection to a specific server and port, given a cookie.
     *
     * @param server Server to connect to.
     * @param port Port to connect to.
     * @param cookie Auth cookie.
     */
    void startBosConn(String server, int port, ByteBlock cookie) {
        bosConn = new BOSConnection(server, port, this, cookie);
        bosConn.connect();
    }

    /**
     * Registers the set of SNAC families that the given connection supports.
     *
     * @param conn FLAP connection to be registered.
     */
    void registerSnacFamilies(BasicFlapConnection conn) {
        snacMgr.register(conn);
    }

    protected SnacManager snacMgr = new SnacManager(new PendingSnacListener() {
        public void dequeueSnacs(SnacRequest[] pending) {
            //Log.debug("dequeuing " + pending.length + " snacs");
            for (SnacRequest aPending : pending) {
                handleRequest(aPending);
            }
        }
    });

    synchronized void handleRequest(SnacRequest request) {
        int family = request.getCommand().getFamily();
        if (snacMgr.isPending(family)) {
            snacMgr.addRequest(request);
            return;
        }

        BasicFlapConnection conn = snacMgr.getConn(family);

        if (conn != null) {
            conn.sendRequest(request);
        } else {
            // it's time to request a service
            if (!(request.getCommand() instanceof ServiceRequest)) {
                //Log.debug("requesting " + Integer.toHexString(family)
                //        + " service.");
                snacMgr.setPending(family, true);
                snacMgr.addRequest(request);
                request(new ServiceRequest(family));
            } else {
                Log.error("eep! can't find a service redirector server.");
            }
        }
    }

    SnacRequest request(SnacCommand cmd) {
        return request(cmd, null);
    }

    private SnacRequest request(SnacCommand cmd, SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);
        handleRequest(req);
        return req;
    }

    void connectToService(int snacFamily, String host, ByteBlock cookie) {
        ServiceConnection conn = new ServiceConnection(host, 5190, this,
                cookie, snacFamily);

        conn.connect();
    }

    void serviceFailed(ServiceConnection conn) {
    }

    void serviceConnected(ServiceConnection conn) {
        services.add(conn);
    }

    public boolean isServiceConnected(ServiceConnection conn) {
        return services.contains(conn);
    }

    public Set<ServiceConnection> getServiceConnections() {
        return services;
    }

    void serviceReady(ServiceConnection conn) {
        snacMgr.dequeueSnacs(conn);
    }

    void serviceDied(ServiceConnection conn) {
        services.remove(conn);
        snacMgr.unregister(conn);
    }

    /**
     * We've been told about a buddy that exists on the buddy list.
     *
     * @param buddy The buddy we've been told about.
     */
    void gotBuddy(BuddyItem buddy) {
        Log.debug("Found buddy item: " + buddy.toString() + " at id " + buddy.getId());
        buddies.put(buddy.getGroupId()+"."+buddy.getId(), buddy);
        if (!highestBuddyIdPerGroup.containsKey(buddy.getGroupId())) {
            highestBuddyIdPerGroup.put(buddy.getGroupId(), -1);
        }
        if (buddy.getId() > highestBuddyIdPerGroup.get(buddy.getGroupId())) {
            highestBuddyIdPerGroup.put(buddy.getGroupId(), buddy.getId());
        }
    }

    /**
     * We've been told about a group that exists on the buddy list.
     *
     * @param group The group we've been told about.
     */
    void gotGroup(GroupItem group) {
        Log.debug("Found group item: " + group.toString() + " at id " + group.getId());
        groups.put(group.getId(), group);
        if (!highestBuddyIdPerGroup.containsKey(group.getId())) {
            highestBuddyIdPerGroup.put(group.getId(), -1);
        }
        if (group.getId() > highestGroupId) {
            highestGroupId = group.getId();
        }
    }

    /**
     * Apparantly we now have the entire list, lets sync.
     */
    void gotCompleteSSI() {
        getRegistration().setLastLogin(new Date());
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (BuddyItem buddy : buddies.values()) {
            //Log.debug("CompleteSSI: adding "+buddy.getScreenname());
            String nickname = buddy.getAlias();
            if (nickname == null) {
                nickname = buddy.getScreenname();
            }
            
            int groupid = buddy.getGroupId();
            String groupname = "Buddies";
            if (groups.containsKey(groupid)) {
                groupname = groups.get(groupid).getGroupName();
                if (groupname.length() < 1) {
                    groupname = "Buddies";
                }
            }

            legacyusers.add(new TransportBuddy(buddy.getScreenname(), nickname, groupname));
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync oscar contact list for " + getJID());
        }

        updateStatus(this.presenceType, this.verboseStatus);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        bosConn.getAndSendStatus(getTransport().convertJIDToID(jid));
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        if (presenceType != PresenceType.available && presenceType != PresenceType.chat) {
            String awayMsg = "Away";
            if (verboseStatus != null) {
                awayMsg = verboseStatus;
            }
            request(new SetInfoCmd(new InfoData(awayMsg)));
        }
        else {
            request(new SetInfoCmd(new InfoData(InfoData.NOT_AWAY)));
        }

        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        bosConn.getAndSendAllStatuses(jid);
    }

}
