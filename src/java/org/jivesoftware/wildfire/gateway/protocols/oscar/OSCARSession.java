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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.OscarTools;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snac.SnacRequest;
import net.kano.joscar.snac.SnacRequestListener;
import net.kano.joscar.snaccmd.ssi.CreateItemsCmd;
import net.kano.joscar.snaccmd.ssi.DeleteItemsCmd;
import net.kano.joscar.snaccmd.ssi.ModifyItemsCmd;
import net.kano.joscar.snaccmd.icbm.SendImIcbm;
import net.kano.joscar.snaccmd.icbm.SendTypingNotification;
import net.kano.joscar.snaccmd.conn.ServiceRequest;
import net.kano.joscar.snaccmd.loc.SetInfoCmd;
import net.kano.joscar.snaccmd.InfoData;
import net.kano.joscar.snaccmd.CapabilityBlock;
import net.kano.joscar.ssiitem.BuddyItem;
import net.kano.joscar.ssiitem.GroupItem;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.wildfire.gateway.*;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

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
        this.propertyPrefix = "plugin.gateway."+transport.getType().toString();
        OscarTools.setDefaultCharset(JiveGlobals.getProperty(this.propertyPrefix+".encoding", "ISO8859-1"));
    }

    private BOSConnection bosConn = null;
    private LoginConnection loginConn = null;
    private Set<ServiceConnection> services = new HashSet<ServiceConnection>();
    private PresenceType presenceType = null;
    private String verboseStatus = null;
    private String propertyPrefix;
    
    /**
     * SSI tracking variables.
     */
    private ConcurrentHashMap<String, BuddyItem> buddies = new ConcurrentHashMap<String,BuddyItem>();
    private ConcurrentHashMap<Integer, GroupItem> groups = new ConcurrentHashMap<Integer,GroupItem>();
    private ConcurrentHashMap<Integer,Integer> highestBuddyIdPerGroup = new ConcurrentHashMap<Integer,Integer>();
    private Integer highestGroupId = -1;

    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!isLoggedIn()) {
            setLoginStatus(TransportLoginStatus.LOGGING_IN);
            loginConn = new LoginConnection(new ConnDescriptor(
                    JiveGlobals.getProperty(propertyPrefix+".connecthost", "login.oscar.aol.com"),
                    JiveGlobals.getIntProperty(propertyPrefix+".connectport", 5190)),
                    this);
            loginConn.connect();

            this.presenceType = presenceType;
            this.verboseStatus = verboseStatus;
        }
        else {
            Log.warn(this.jid + " is already logged in");
        }
    }

    public synchronized void logOut() {
        if (isLoggedIn()) {
            setLoginStatus(TransportLoginStatus.LOGGING_OUT);
            if (loginConn != null) {
                loginConn.disconnect();
                loginConn = null;
            }
            if (bosConn != null) {
                bosConn.disconnect();
                bosConn = null;
            }
            for (ServiceConnection conn : getServiceConnections()) {
                try {
                    conn.disconnect();
                }
                catch (Exception e) {
                    // Ignore.
                }
                try {
                    services.remove(conn);
                }
                catch (Exception e) {
                    // Ignore.
                }
                try {
                    snacMgr.unregister(conn);
                }
                catch (Exception e) {
                    // Ignore.
                }
            }
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
            setLoginStatus(TransportLoginStatus.LOGGED_OUT);
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
            if (groupName.equalsIgnoreCase(g.getGroupName())) {
                return g.getId();
            }
        }

        // Group doesn't exist, lets create a new one.
        Integer newGroupId = highestGroupId + 1;
        GroupItem newGroup = new GroupItem(groupName, newGroupId);
        request(new CreateItemsCmd(newGroup.toSsiItem()));
        gotGroup(newGroup);

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
        Log.debug("contact = "+contact+", grouplist = "+grouplist);
        // First, lets take the known good list of groups and sync things up server side.
        for (String group : grouplist) {
            Integer groupId = getGroupIdOrCreateNew(group);

            Integer newBuddyId = 1;
            if (highestBuddyIdPerGroup.containsKey(groupId)) {
                newBuddyId = highestBuddyIdPerGroup.get(groupId) + 1;
            }

            BuddyItem newBuddy = new BuddyItem(contact, groupId, newBuddyId);
            newBuddy.setAlias(nickname);
            request(new CreateItemsCmd(newBuddy.toSsiItem()));
            gotBuddy(newBuddy);
        }
        // Now, lets clean up any groups this contact should no longer be a member of.
        for (BuddyItem buddy : buddies.values()) {
            if (buddy.getScreenname().equalsIgnoreCase(contact)) {
                if (buddy.getGroupId() == 0) {
                    // Ok this group is the "main group", which we can cheerfully remove from.
//                    Log.debug("Removing "+buddy+" because of in main group");
//                    request(new DeleteItemsCmd(buddy.toSsiItem()));
//                    buddies.remove(""+buddy.getGroupId()+"."+buddy.getId());
                }
                else if (!groups.containsKey(buddy.getGroupId())) {
                    // Well this is odd, a group we don't know about?  Nuke it.
//                    Log.debug("Removing "+buddy+" because of unknown group");
//                    request(new DeleteItemsCmd(buddy.toSsiItem()));
//                    buddies.remove(""+buddy.getGroupId()+"."+buddy.getId());
                }
                else if (!grouplist.contains(groups.get(buddy.getGroupId()).getGroupName())) {
//                    Log.debug("Removing "+buddy+" because not in list of groups");
//                    request(new DeleteItemsCmd(buddy.toSsiItem()));
//                    buddies.remove(""+buddy.getGroupId()+"."+buddy.getId());
                }
                else {
                    if (buddy.getAlias() == null || !buddy.getAlias().equals(nickname)) {
                        buddy.setAlias(nickname);
                        request(new ModifyItemsCmd(buddy.toSsiItem()));
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
//        String legacyId = getTransport().convertJIDToID(item.getJid());
//        for (BuddyItem i : buddies.values()) {
//            if (i.getScreenname().equalsIgnoreCase(legacyId)) {
//                request(new DeleteItemsCmd(i.toSsiItem()));
//                buddies.remove(""+i.getGroupId()+"."+i.getId());
//            }
//        }
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
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendServerMessage(String)
     */
    public void sendServerMessage(String message) {
        // We don't care.
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendChatState(org.xmpp.packet.JID, org.jivesoftware.wildfire.gateway.ChatStateType)
     */
    public void sendChatState(JID jid, ChatStateType chatState) {
        if (chatState.equals(ChatStateType.composing)) {
            request(new SendTypingNotification(
                    getTransport().convertJIDToID(jid),
                    SendTypingNotification.STATE_TYPING
            ));
        }
        else if (chatState.equals(ChatStateType.paused)) {
            request(new SendTypingNotification(
                    getTransport().convertJIDToID(jid),
                    SendTypingNotification.STATE_PAUSED
            ));
        }
        else if (chatState.equals(ChatStateType.inactive)) {
            request(new SendTypingNotification(
                    getTransport().convertJIDToID(jid),
                    SendTypingNotification.STATE_NO_TEXT
            ));
        }
    }

    /**
     * Opens/creates a new BOS connection to a specific server and port, given a cookie.
     *
     * @param server Server to connect to.
     * @param port Port to connect to.
     * @param cookie Auth cookie.
     */
    void startBosConn(String server, int port, ByteBlock cookie) {
        bosConn = new BOSConnection(new ConnDescriptor(server, port), this, cookie);
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
        public void dequeueSnacs(List<SnacRequest> pending) {
            for (SnacRequest request : pending) {
                handleRequest(request);
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
        }
        else {
            // it's time to request a service
            if (!(request.getCommand() instanceof ServiceRequest)) {
                snacMgr.setPending(family, true);
                snacMgr.addRequest(request);
                request(new ServiceRequest(family));
            } else {
                // TODO: Why does this occur a lot and yet not cause problems?
//                Log.error("eep! can't find a service redirector server.");
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
        ServiceConnection conn = new ServiceConnection(new ConnDescriptor(host,
                JiveGlobals.getIntProperty(propertyPrefix+".connectport", 5190)),
                this,
                cookie,
                snacFamily);

        conn.connect();
    }

    void serviceFailed(ServiceConnection conn) {
        Log.debug("OSCAR service failed: "+conn.toString());
    }

    void serviceConnected(ServiceConnection conn) {
        Log.debug("OSCAR service connected: "+conn.toString());
        services.add(conn);
    }

    public boolean isServiceConnected(ServiceConnection conn) {
        return services.contains(conn);
    }

    public Set<ServiceConnection> getServiceConnections() {
        return services;
    }

    void serviceReady(ServiceConnection conn) {
        Log.debug("OSCAR service ready: "+conn.toString());
        snacMgr.dequeueSnacs(conn);
    }

    void serviceDied(ServiceConnection conn) {
        Log.debug("OSCAR service died: "+conn.toString());
        services.remove(conn);
        snacMgr.unregister(conn);
    }

    void bosDisconnected() {
        Message m = new Message();
        m.setType(Message.Type.error);
        m.setError(PacketError.Condition.internal_server_error);
        m.setTo(getJID());
        m.setFrom(getTransport().getJID());
        m.setBody(LocaleUtils.getLocalizedString("gateway.oscar.disconnected", "gateway"));
        getTransport().sendPacket(m);
        logOut();
    }

    /**
     * We've been told about a buddy that exists on the buddy list.
     *
     * @param buddy The buddy we've been told about.
     */
    void gotBuddy(BuddyItem buddy) {
        Log.debug("Found buddy item: " + buddy.toString() + " at id " + buddy.getId());
        buddies.put(""+buddy.getGroupId()+"."+buddy.getId(), buddy);
        if (!highestBuddyIdPerGroup.containsKey(buddy.getGroupId())) {
            highestBuddyIdPerGroup.put(buddy.getGroupId(), 0);
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
            highestBuddyIdPerGroup.put(group.getId(), 0);
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
            String groupname = "Transport Buddies";
            if (groups.containsKey(groupid)) {
                groupname = groups.get(groupid).getGroupName();
                if (groupname.length() < 1) {
                    groupname = "Transport Buddies";
                }
            }

            legacyusers.add(new TransportBuddy(buddy.getScreenname(), nickname, groupname));
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync oscar contact list for " + getJID(), e);
        }

        updateStatus(this.presenceType, this.verboseStatus);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        if (isLoggedIn()) {
            if (bosConn != null) {
                bosConn.getAndSendStatus(getTransport().convertJIDToID(jid));
            }
        }
    }

    private static final List<CapabilityBlock> MY_CAPS = Arrays.asList(new CapabilityBlock[] {
        CapabilityBlock.BLOCK_ICQCOMPATIBLE,
    });

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        if (presenceType != PresenceType.available && presenceType != PresenceType.chat) {
            String awayMsg = LocaleUtils.getLocalizedString("gateway.oscar.away", "gateway");
            if (verboseStatus != null) {
                awayMsg = verboseStatus;
            }
            request(new SetInfoCmd(new InfoData(null, awayMsg, MY_CAPS, null)));
            Presence p = new Presence();
            p.setShow(Presence.Show.away);
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
        }
        else {
            request(new SetInfoCmd(new InfoData(null, InfoData.NOT_AWAY, MY_CAPS, null)));
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
        }

        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        if (bosConn != null) {
            bosConn.getAndSendAllStatuses(jid);
        }
    }

}
