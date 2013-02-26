/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.oscar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.OscarTools;
import net.kano.joscar.SeqNum;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.snac.SnacRequest;
import net.kano.joscar.snac.SnacRequestListener;
import net.kano.joscar.snaccmd.CapabilityBlock;
import net.kano.joscar.snaccmd.ExtraInfoBlock;
import net.kano.joscar.snaccmd.ExtraInfoData;
import net.kano.joscar.snaccmd.InfoData;
import net.kano.joscar.snaccmd.conn.ServiceRequest;
import net.kano.joscar.snaccmd.conn.SetExtraInfoCmd;
import net.kano.joscar.snaccmd.icbm.SendImIcbm;
import net.kano.joscar.snaccmd.icbm.SendTypingNotification;
import net.kano.joscar.snaccmd.icq.MetaShortInfoRequest;
import net.kano.joscar.snaccmd.icq.OfflineMsgIcqRequest;
import net.kano.joscar.snaccmd.loc.SetInfoCmd;
import net.kano.joscar.snaccmd.mailcheck.MailCheckCmd;
import net.kano.joscar.snaccmd.ssi.AuthFutureCmd;
import net.kano.joscar.snaccmd.ssi.DeleteItemsCmd;
import net.kano.joscar.ssiitem.BuddyItem;
import net.kano.joscar.ssiitem.VisibilityItem;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.ChatStateType;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.SupportedFeature;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;

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
public class OSCARSession extends TransportSession<OSCARBuddy> {

    static Logger Log = Logger.getLogger(OSCARSession.class);

    private BOSConnection bosConn = null;
    private LoginConnection loginConn = null;
    private final Set<ServiceConnection> services = new HashSet<ServiceConnection>();
    private String propertyPrefix;
    private final SeqNum icqSeqNum = new SeqNum(0, Integer.MAX_VALUE);

    /**
     * Representation of the Server Sided Item hierarchy of this session. 
     */
    private final SSIHierarchy ssiHierarchy;

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
        setSupportedFeature(SupportedFeature.chatstates);

        ssiHierarchy = new SSIHierarchy(this);
        this.propertyPrefix = "plugin.gateway."+transport.getType().toString();
        OscarTools.setDefaultCharset(JiveGlobals.getProperty(this.propertyPrefix+".encoding", "ISO8859-1"));
        if (JiveGlobals.getBooleanProperty("plugin.gateway."+transport.getType()+".crosschat", true)) {
            MY_CAPS.add(CapabilityBlock.BLOCK_ICQCOMPATIBLE);
        }
        if (transport.getType().equals(TransportType.icq)) {
            MY_CAPS.add(CapabilityBlock.BLOCK_ICQ_UTF8);
        }
    }
    
    /**
     * Returns the Server Sided Item hierarchy of this session.
     * 
     * @return Server Sided Item hierarchy
     */
    public SSIHierarchy getSsiHierarchy() {
        return ssiHierarchy;
    }
    
    private final List<CapabilityBlock> MY_CAPS = new ArrayList<CapabilityBlock>();

    public List<CapabilityBlock> getCapabilities() {
        return MY_CAPS;
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public synchronized void logIn(PresenceType presenceType, String verboseStatus) {
        setPendingPresenceAndStatus(presenceType, verboseStatus);
        if (!isLoggedIn()) {
            loginConn = new LoginConnection(new ConnDescriptor(
                    JiveGlobals.getProperty(propertyPrefix+".connecthost", (this.getTransport().getType().equals(TransportType.icq) ? "login.icq.com" : "login.oscar.aol.com")),
                    JiveGlobals.getIntProperty(propertyPrefix+".connectport", 5190)),
                    this);
            loginConn.connect();
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logOut()
     */
    @Override
    public synchronized void logOut() {
        cleanUp();
        sessionDisconnectedNoReconnect(null);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#cleanUp()
     */
    @Override
    public synchronized void cleanUp() {
        if (loginConn != null) {
            try {
                loginConn.stopKeepAlive();
            }
            catch (Exception e) {
                // Ignore.
            }
            try {
                loginConn.disconnect();
            }
            catch (Exception e) {
                // Ignore.
            }
            loginConn = null;
        }
        if (bosConn != null) {
            try {
                bosConn.stopKeepAlive();
            }
            catch (Exception e) {
                // Ignore.
            }
            try {
                bosConn.disconnect();
            }
            catch (Exception e) {
                // Ignore.
            }
            bosConn = null;
        }
        for (ServiceConnection conn : getServiceConnections()) {
            try {
                conn.stopKeepAlive();
            }
            catch (Exception e) {
                // Ignore.
            }
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
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
        String legacyId = getTransport().convertJIDToID(jid);
        if (nickname == null || nickname.equals("")) {
            nickname = legacyId;
        }

        if (getTransport().getType().equals(TransportType.icq)) {
            request(new AuthFutureCmd(legacyId, null));
        }

        // Syncing takes care of all the dirty work.
        ssiHierarchy.syncContactGroupsAndNickname(legacyId, nickname, groups);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(OSCARBuddy oscarBuddy) {
        String legacyId = getTransport().convertJIDToID(oscarBuddy.getJID());
        for (BuddyItem i : oscarBuddy.getBuddyItems()) {
            if (i.getScreenname().equalsIgnoreCase(legacyId)) {
                request(new DeleteItemsCmd(i.toSsiItem()));
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(OSCARBuddy contact) {
        String legacyId = getTransport().convertJIDToID(contact.getJID());
        String nickname = contact.getNickname();
        if (nickname == null || nickname.equals("")) {
            nickname = legacyId;
        }

        // Syncing takes care of all of the dirty work.
        ssiHierarchy.syncContactGroupsAndNickname(legacyId, nickname, (List<String>)contact.getGroups());
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("OSCAR: accept-adding is currently not implemented."
                + " Cannot accept-add: " + userID);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
        SendImIcbm icbm = new SendImIcbm(getTransport().convertJIDToID(jid), message);
        // TODO: Should we consider checking to see if they really are offline?
        if (getTransport().getType().equals(TransportType.icq)) {
            icbm.setOffline(true);
        }
        request(icbm);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID,net.sf.kraken.type.ChatStateType)
     */
    @Override
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
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateLegacyAvatar(String, byte[])
     */
    @Override
    public void updateLegacyAvatar(String type, byte[] data) {
        ssiHierarchy.setIcon(type, data);
    }

    /**
     * Opens/creates a new BOS connection to a specific server and port, given a cookie.
     *
     * @param server Server to connect to.
     * @param port Port to connect to.
     * @param cookie Auth cookie.
     */
    synchronized void startBosConn(String server, int port, ByteBlock cookie) {
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
        Log.debug("Handling request "+request);
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
                Log.debug("eep! can't find a service redirector server.");
            }
        }
    }

    SnacRequest request(SnacCommand cmd) {
        Log.debug("Sending SNAC command: "+cmd);
        return request(cmd, null);
    }

    private SnacRequest request(SnacCommand cmd, SnacRequestListener listener) {
        Log.debug("Setting up SNAC request and listener: "+cmd+","+listener);
        SnacRequest req = new SnacRequest(cmd, listener);
        handleRequest(req);
        return req;
    }

    void connectToService(int snacFamily, String host, ByteBlock cookie) {
        Log.debug("Connection to service "+snacFamily+" on host "+host);
        ServiceConnection conn;
        if (snacFamily == MailCheckCmd.FAMILY_MAILCHECK) {
            conn = new EmailConnection(new ConnDescriptor(host,
                    JiveGlobals.getIntProperty(propertyPrefix+".connectport", 5190)),
                    this,
                    cookie,
                    snacFamily);
        }
        else {
            conn = new ServiceConnection(new ConnDescriptor(host,
                    JiveGlobals.getIntProperty(propertyPrefix+".connectport", 5190)),
                    this,
                    cookie,
                    snacFamily);
        }

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

    /**
     * Apparently we now have the entire list, lets sync.
     */
    void gotCompleteSSI() {
        ArrayList<Integer> nicknameRequests = new ArrayList<Integer>();
        TransportBuddyManager<OSCARBuddy> manager = getBuddyManager();
        for (OSCARBuddy buddy : manager.getBuddies()) {
            String nickname = buddy.getNickname();

            buddy.populateGroupList();

            for (BuddyItem buddyItem : buddy.getBuddyItems()) {
                if (buddyItem.isAwaitingAuth()) {
                    buddy.setAskType(RosterItem.ASK_SUBSCRIBE);
                    buddy.setSubType(RosterItem.SUB_NONE);
                }
                try {
                    if (nickname.equalsIgnoreCase(buddyItem.getScreenname())) {
                        Integer buddyUIN = Integer.parseInt(buddyItem.getScreenname());
                        Log.debug("REQUESTING SHORT INFO FOR "+buddyUIN);
                        nicknameRequests.add(buddyUIN);
                    }
                }
                catch (NumberFormatException e) {
                    // Not an ICQ number then  ;D
                }
            }
        }

        try {
            getTransport().syncLegacyRoster(getJID(), getBuddyManager().getBuddies());
        }
        catch (UserNotFoundException e) {
            Log.debug("Unable to sync oscar contact list for " + getJID(), e);
        }

        getBuddyManager().activate();

        request(new SetInfoCmd(InfoData.forCapabilities(getCapabilities())));

//        if (JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".avatars", true) && getAvatar() != null) {
//            if (storedIconInfo == null || !StringUtils.encodeHex(storedIconInfo.getIconInfo().getData().toByteArray()).equals(getAvatar().getLegacyIdentifier())) {
//                try {
//                    updateLegacyAvatar(getAvatar().getMimeType(), Base64.decode(getAvatar().getImageData()));
//                }
//                catch (NotFoundException e) {
//                    // No avatar found, moving on
//                }
//            }
//        }

        updateStatus(getPresence(), getVerboseStatus());

        ssiHierarchy.setVisibilityFlag(VisibilityItem.MASK_DISABLE_RECENT_BUDDIES);

        if (getTransport().getType().equals(TransportType.icq)) {
            request(new OfflineMsgIcqRequest(getUIN(), (int)nextIcqId()));
        }

        if (JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".mailnotifications", true)) {
            request(new ServiceRequest(MailCheckCmd.FAMILY_MAILCHECK));
        }


        for (Integer uin : nicknameRequests) {
            MetaShortInfoRequest req = new MetaShortInfoRequest(getUIN(), (int)nextIcqId(), uin);
            Log.debug("Doing a MetaShortInfoRequest for "+uin+" as "+req);
            request(req);
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateStatus(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        if (getTransport().getType().equals(TransportType.icq)) {
            request(new SetExtraInfoCmd(((OSCARTransport)getTransport()).convertXMPPStatusToICQ(presenceType)));
        }
        if (presenceType != PresenceType.available && presenceType != PresenceType.chat) {
            String awayMsg = LocaleUtils.getLocalizedString("gateway.oscar.away", "kraken");
            if (verboseStatus != null && verboseStatus.length() > 0) {
                awayMsg = verboseStatus;
            }
            request(new SetInfoCmd(InfoData.forAwayMessage(awayMsg)));
            if (!getTransport().getType().equals(TransportType.icq)) {
                presenceType = PresenceType.away;
            }
        }
        else {
            request(new SetInfoCmd(InfoData.forAwayMessage(InfoData.NOT_AWAY)));
            request(new SetExtraInfoCmd(new ExtraInfoBlock(ExtraInfoBlock.TYPE_AVAILMSG, ExtraInfoData.getAvailableMessageBlock(verboseStatus == null ? "" : verboseStatus))));
        }
        setPresenceAndStatus(presenceType, verboseStatus);
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
        try {
            TransportBuddy buddy = getBuddyManager().getBuddy(getTransport().convertIDToJID(sn));
            buddy.setNickname(nickname);
            try {
                getTransport().addOrUpdateRosterItem(getJID(), buddy.getName(), buddy.getNickname(), buddy.getGroups());
            }
            catch (UserNotFoundException e) {
                // Can't update something that's not really in our list.
            }
        }
        catch (NotFoundException e) {
            // Can't update something that's not really in our list.
        }
    }

}
