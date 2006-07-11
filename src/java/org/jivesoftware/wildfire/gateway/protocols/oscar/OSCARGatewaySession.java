package org.jivesoftware.wildfire.gateway.protocols.oscar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.AbstractGatewaySession;
import org.jivesoftware.wildfire.gateway.Endpoint;
import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.SubscriptionInfo;
import org.jivesoftware.wildfire.gateway.roster.ForeignContact;
import org.jivesoftware.wildfire.gateway.roster.UnknownForeignContactException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import net.kano.joscar.flapcmd.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;
import net.kano.joscar.snaccmd.ssi.*;
import net.kano.joscar.ssiitem.*;
import net.kano.joscar.ByteBlock;

/**
 * Manages the session to the underlying legacy system.
 * 
 * @author Daniel Henninger
 */
public class OSCARGatewaySession extends AbstractGatewaySession implements Endpoint {

    /**
     * OSCAR Session Pieces
     */
    private LoginConnection loginConn = null;
    private BOSConnection bosConn = null;
    private Set services = new HashSet();
    private Boolean connected = false;
    
    /**
     * The Screenname, Password, and JID associated with this session.
     */
    private JID jid;
    private String legacyname = null;
    private String legacypass = null;
    
    /**
     * Misc tracking variables.
     */
    private ArrayList<ForeignContact> contacts = new ArrayList<ForeignContact>();
    private ArrayList<GroupItem> groups = new ArrayList<GroupItem>();
    private Integer highestBuddyId = -1;
    private Integer highestGroupId = -1;

    /**
     * Initialize a new session object for OSCAR
     * 
     * @param info The subscription information to use during login.
     * @param gateway The gateway that created this session.
     */
    public OSCARGatewaySession(SubscriptionInfo info, Gateway gateway) {
        super(info, gateway);
        this.jid = info.jid;
        this.legacyname = info.username;
        this.legacypass = info.password;
    }

    public synchronized void login() throws Exception {
        Log.debug("Login called");
        if (!isConnected()) {
            Log.debug("Connecting...");
            loginConn = new LoginConnection("login.oscar.aol.com", 5190, this);
            loginConn.connect();

            getJabberEndpoint().getValve().open(); // allow any buffered messages to pass through
            connected = true;
        } else {
            Log.warn(this.jid + " is already logged in");
        }
    }
    
    public boolean isConnected() {
        Log.debug("isConnected called");
        return connected;
    }
    
    public synchronized void logout() throws Exception {
        Log.debug("logout called");
        Log.info("[" + this.jid + "]" + getSubscriptionInfo().username + " logged out.");
        bosConn.disconnect();
        connected = false;
    }
    
    @Override
    public String toString() { return "[" + this.getSubscriptionInfo().username + " CR:" + clientRegistered + " SR:" + serverRegistered + "]"; }

    public String getId() {
        Log.debug("getId called");
        return this.jid.toBareJID();
    }

    public String getLegacyName() {
        Log.debug("getLegacyName called");
        return this.legacyname;
    }

    public String getLegacyPassword() {
        Log.debug("getLegacyPassword called");
        return this.legacypass;
    }

    @SuppressWarnings("unchecked")
    public List<ForeignContact> getContacts() {
        Log.debug("getContacts called");
        return contacts;
    }

    public JID getSessionJID() {
        Log.debug("getSessionJID called");
        return this.jid;
    }

    public JID getJID() {
        Log.debug("getJID called");
        return this.jid;
    }

    public String getStatus(JID to) {
        Log.debug("getStatus called");
        for (ForeignContact c : contacts) {
            if (c.getName().equals(to.getNode())) {
                return c.getStatus().getValue();
            }
        }
        return null;
    }

    public void addContact(JID jid) throws Exception {
        Log.debug("addContact called");
        Integer newBuddyId = highestBuddyId + 1;
        Integer groupId = -1;
        for (GroupItem g : groups) {
            if ("Transport Buddies".equals(g.getGroupName())) {
                groupId = g.getId();
            }
        }
        if (groupId == -1) {
            Integer newGroupId = highestGroupId + 1;
            request(new CreateItemsCmd(new SsiItem[] {
                new GroupItem("Transport Buddies", newGroupId).toSsiItem() }));
            highestGroupId = newGroupId;
            groupId = newGroupId;
        }
        request(new CreateItemsCmd(new SsiItem[] {
            new BuddyItem(jid.getNode(), newBuddyId, groupId).toSsiItem() }));
    }

    public void removeContact(JID jid) throws Exception {
        Log.debug("removeContact called");
        for (ForeignContact c : contacts) {
            if (c.getName().equals(jid.getNode())) {
                OSCARForeignContact oc = (OSCARForeignContact)c;
                request(new DeleteItemsCmd(new SsiItem[] { oc.getSSIItem() }));
                contacts.remove(contacts.indexOf(c));
            }
        }
    }

    public void sendPacket(Packet packet) {
        Log.debug("sendPacket called:"+packet.toString());
        if (packet instanceof Message) {
            Message m = (Message)packet;
            request(new SendImIcbm(packet.getTo().getNode(), m.getBody()));
        }
    }

    public ForeignContact getContact(JID to) throws UnknownForeignContactException {
        Log.debug("getContact called");
        for (ForeignContact c : contacts) {
            if (c.getName().equals(to.getNode())) {
                return c;
            }
        }
        return null;
    }

    void startBosConn(String server, int port, ByteBlock cookie) {
        bosConn = new BOSConnection(server, port, this, cookie);
        bosConn.connect();
    }

    void registerSnacFamilies(BasicFlapConnection conn) {
        snacMgr.register(conn);
    }

    protected SnacManager snacMgr = new SnacManager(new PendingSnacListener() {
        public void dequeueSnacs(SnacRequest[] pending) {
            Log.debug("dequeuing " + pending.length + " snacs");
            for (int i = 0; i < pending.length; i++) {
                handleRequest(pending[i]);
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
                Log.debug("requesting " + Integer.toHexString(family)
                        + " service.");
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

    void serviceReady(ServiceConnection conn) {
        snacMgr.dequeueSnacs(conn);
    }

    void serviceDied(ServiceConnection conn) {
        services.remove(conn);
        snacMgr.unregister(conn);
    }

    void gotBuddy(BuddyItem buddy) {
        contacts.add(new OSCARForeignContact(buddy, this.gateway));
        if (buddy.getId() > highestBuddyId) {
            highestBuddyId = buddy.getId();
        }
    }

    void gotGroup(GroupItem group) {
        groups.add(group);
        if (group.getId() > highestGroupId) {
            highestGroupId = group.getId();
        }
    }

}
