/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.oscar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.OscarTools;
import net.kano.joscar.SeqNum;
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
import net.kano.joscar.snaccmd.icq.OfflineMsgIcqRequest;
import net.kano.joscar.ssiitem.BuddyItem;
import net.kano.joscar.ssiitem.GroupItem;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.*;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.roster.RosterItem;
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
        highestBuddyIdPerGroup.put(0, 0); // Main group highest id
    }

    private BOSConnection bosConn = null;
    private LoginConnection loginConn = null;
    private Set<ServiceConnection> services = new HashSet<ServiceConnection>();
    private PresenceType presenceType = null;
    private String verboseStatus = null;
    private String propertyPrefix;
    private static final String DEFAULT_AIM_GROUP = "   "; // We're using 3 spaces to indicated main group, invalid in real aim
    private static final String DEFAULT_ICQ_GROUP = "General";
    private SeqNum icqSeqNum = new SeqNum(0, Integer.MAX_VALUE);
    
    /**
     * SSI tracking variables.
     */
    private ConcurrentHashMap<String, BuddyItem> buddies = new ConcurrentHashMap<String,BuddyItem>();
    private ConcurrentHashMap<Integer, GroupItem> groups = new ConcurrentHashMap<Integer,GroupItem>();
    private ConcurrentHashMap<Integer,Integer> highestBuddyIdPerGroup = new ConcurrentHashMap<Integer,Integer>();

    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!isLoggedIn()) {
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
        cleanUp();
        sessionDisconnectedNoReconnect();
    }

    public synchronized void cleanUp() {
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
    }

    /**
     * Finds the id number of a group specified or creates a new one and returns that id.
     *
     * @param groupName Name of the group we are looking for.
     * @return Id number of the group.
     */
    public Integer getGroupIdOrCreateNew(String groupName) {
        if (groupName.matches("/^\\s*$/")) { return 0; } // Special master group handling
        for (GroupItem g : groups.values()) {
            if (groupName.equalsIgnoreCase(g.getGroupName())) {
                return g.getId();
            }
        }

        // Group doesn't exist, lets create a new one.
        Integer newGroupId = highestBuddyIdPerGroup.get(0) + 1;
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
            if (transport.getType().equals(TransportType.icq)) {
                grouplist.add(DEFAULT_ICQ_GROUP);
            }
            else {
                grouplist.add(DEFAULT_AIM_GROUP);
            }
        }
        Log.debug("contact = "+contact+", grouplist = "+grouplist);
        // First, lets take the known good list of groups and add whatever is missing on the server.
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
                if (buddy.getGroupId() == 0 && !grouplist.contains(DEFAULT_AIM_GROUP)) {
                    // Ok this group is the "main group", but contact isn't in it.
                    Log.debug("Removing "+buddy+" from main group");
                    request(new DeleteItemsCmd(buddy.toSsiItem()));
                    buddies.remove(""+buddy.getGroupId()+"."+buddy.getId());
                }
                else if (!groups.containsKey(buddy.getGroupId())) {
                    // Well this is odd, a group we don't know about?  Nuke it.
                    Log.debug("Removing "+buddy+" because of unknown group");
                    request(new DeleteItemsCmd(buddy.toSsiItem()));
                    buddies.remove(""+buddy.getGroupId()+"."+buddy.getId());
                }
                else if (!grouplist.contains(groups.get(buddy.getGroupId()).getGroupName())) {
                    Log.debug("Removing "+buddy+" because not in list of groups");
                    request(new DeleteItemsCmd(buddy.toSsiItem()));
                    buddies.remove(""+buddy.getGroupId()+"."+buddy.getId());
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
     * @see org.jivesoftware.openfire.gateway.TransportSession#addContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        String legacyId = getTransport().convertJIDToID(item.getJid());
        String nickname = item.getNickname();
        if (nickname == null || nickname.equals("")) {
            nickname = legacyId;
        }

        // Syncing takes care of all the dirty work.
        lockRoster(item.getJid().toString());
        syncContactGroupsAndNickname(legacyId, nickname, item.getGroups());
        unlockRoster(item.getJid().toString());
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#removeContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        String legacyId = getTransport().convertJIDToID(item.getJid());
        for (BuddyItem i : buddies.values()) {
            if (i.getScreenname().equalsIgnoreCase(legacyId)) {
                lockRoster(item.getJid().toString());
                request(new DeleteItemsCmd(i.toSsiItem()));
                buddies.remove(""+i.getGroupId()+"."+i.getId());
                unlockRoster(item.getJid().toString());
            }
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#updateContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        String legacyId = getTransport().convertJIDToID(item.getJid());
        String nickname = item.getNickname();
        if (nickname == null || nickname.equals("")) {
            nickname = legacyId;
        }

        // Syncing takes care of all of the dirty work.
        lockRoster(item.getJid().toString());
        syncContactGroupsAndNickname(legacyId, nickname, item.getGroups());
        unlockRoster(item.getJid().toString());
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        request(new SendImIcbm(getTransport().convertJIDToID(jid), message));
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendServerMessage(String)
     */
    public void sendServerMessage(String message) {
        // We don't care.
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendChatState(org.xmpp.packet.JID, org.jivesoftware.openfire.gateway.ChatStateType)
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
        sessionDisconnected();
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
        if (!highestBuddyIdPerGroup.containsKey(0)) {
            highestBuddyIdPerGroup.put(0, 0);
        }
        if (group.getId() > highestBuddyIdPerGroup.get(0)) {
            highestBuddyIdPerGroup.put(0, group.getId());
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
            if (nickname == null || nickname.matches("/^\\s*$/")) {
                nickname = buddy.getScreenname();
            }
//            Doesn't work yet so disabling.
//            try {
//                if (nickname.equalsIgnoreCase(buddy.getScreenname())) {
//                    Integer buddyUIN = Integer.parseInt(buddy.getScreenname());
//                    Log.debug("REQUESTING SHORT INFO FOR "+buddyUIN);
//                    request(new MetaFullInfoRequest(getUIN(), (int)nextIcqId(), buddyUIN));
//                }
//            }
//            catch (NumberFormatException e) {
//                // Not an ICQ number then  ;D
//            }
            if (nickname == null) {
                nickname = buddy.getScreenname();
            }

            int groupid = buddy.getGroupId();
            String groupname = null;
            if (groupid != 0 && groups.containsKey(groupid)) {
                String newgroupname = groups.get(groupid).getGroupName();
                if (newgroupname.length() > 0) {
                    groupname = newgroupname;
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

        if (getTransport().getType().equals(TransportType.icq)) {
            request(new OfflineMsgIcqRequest(getUIN(), (int)nextIcqId()));
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        if (isLoggedIn()) {
            if (bosConn != null) {
                bosConn.getAndSendStatus(getTransport().convertJIDToID(jid));
            }
        }
    }

    private static final List<CapabilityBlock> MY_CAPS = Arrays.asList(
            CapabilityBlock.BLOCK_ICQCOMPATIBLE
    );

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#updateStatus(org.jivesoftware.openfire.gateway.PresenceType, String)
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
     * @see org.jivesoftware.openfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        if (bosConn != null) {
            bosConn.getAndSendAllStatuses(jid);
        }
    }

    /**
     * Retrieves the next ICQ id number and increments the counter.
     * @return The next ICQ id number.
     */
    public long nextIcqId() { return icqSeqNum.next(); }

    /**
     * Retrieves a UIN in integer format for the session.
     *
     * @return The UIN in integer format.
     */
    public int getUIN() {
        try {
            return Integer.parseInt(getRegistration().getUsername());
        }
        catch (Exception e) {
            return -1;
        }
    }

    /**
     * Updates roster nickname information about a contact.
     *
     * @param sn Screenname/UIN of contact
     * @param nickname New nickname
     */
    public void updateRosterNickname(String sn, String nickname) {
        BuddyItem buddy = buddies.get(sn);
        if (buddy == null) { return; }
        int groupid = buddy.getGroupId();
        String groupname = null;
        if (groupid != 0 && groups.containsKey(groupid)) {
            String newgroupname = groups.get(groupid).getGroupName();
            if (newgroupname.length() > 0) {
                groupname = newgroupname;
            }
        }
        try {
            getTransport().addOrUpdateRosterItem(getJID(), getTransport().convertIDToJID(sn), nickname, groupname);
        }
        catch (UserNotFoundException e) {
            Log.error("Contact not found while updating nickname.");
        }
    }

}
