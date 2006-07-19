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
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;
import net.kano.joscar.snaccmd.ssi.*;
import net.kano.joscar.ssiitem.*;
import net.kano.joscar.ByteBlock;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Represents an OSCAR session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with OSCAR (AIM/ICQ).
 * 
 * @author Daniel Henninger
 */
public class OSCARSession extends TransportSession {

    /**
     * Initialize a new session object for OSCAR
     * 
     * @param info The subscription information to use during login.
     * @param gateway The gateway that created this session.
     */
    public OSCARSession(Registration registration, JID jid, OSCARTransport transport) {
        super(registration, jid, transport);
    }

    /**
     * OSCAR Session Pieces
     */
    private LoginConnection loginConn = null;
    private BOSConnection bosConn = null;
    private Set services = new HashSet();
    private Boolean loggedIn = false;
    
    /**
     * The Screenname, Password, and JID associated with this session.
     */
    private JID jid;
    private String legacyname = null;
    private String legacypass = null;
    
    /**
     * SSI tracking variables.
     */
    private Map<Integer,BuddyItem> buddies = new HashMap<Integer,BuddyItem>();
    private Map<Integer,GroupItem> groups = new HashMap<Integer,GroupItem>();
    private Integer highestBuddyId = -1;
    private Integer highestGroupId = -1;

    public void logIn() {
        if (!isLoggedIn()) {
            loginConn = new LoginConnection("login.oscar.aol.com", 5190, this);
            loginConn.connect();

            loggedIn = true;

            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
        } else {
            Log.warn(this.jid + " is already logged in");
        }
    }
    
    public Boolean isLoggedIn() {
        return loggedIn;
    }
    
    public synchronized void logOut() {
        bosConn.disconnect();
        loggedIn = false;
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
    }
    
    public void addContact(JID jid) {
        Integer newBuddyId = highestBuddyId + 1;
        Integer groupId = -1;
        for (GroupItem g : groups.values()) {
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

    public void removeContact(JID jid) {
        for (BuddyItem i : buddies.values()) {
            if (i.getScreenname().equals(jid.getNode())) {
                request(new DeleteItemsCmd(new SsiItem[] { i.toSsiItem() }));
                buddies.remove(i.getId());
            }
        }
    }

    public void sendMessage(JID jid, String message) {
        request(new SendImIcbm(jid.getNode(), message));
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
            //Log.debug("dequeuing " + pending.length + " snacs");
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
        //Log.debug("Found buddy item: " + buddy.toString());
        buddies.put(buddy.getId(), buddy);
        if (buddy.getId() > highestBuddyId) {
            highestBuddyId = buddy.getId();
        }
    }

    /**
     * We've been told about a group that exists on the buddy list.
     *
     * @param group The group we've been told about.
     */
    void gotGroup(GroupItem group) {
        //Log.debug("Found group item: " + group.toString());
        groups.put(group.getId(), group);
        if (group.getId() > highestGroupId) {
            highestGroupId = group.getId();
        }
    }

    /**
     * Apparantly we now have the entire list, lets sync.
     */
    void gotCompleteSSI() {
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (BuddyItem buddy : buddies.values()) {
            //Log.debug("CompleteSSI: adding "+buddy.getScreenname());
            String nickname = buddy.getAlias();
            if (nickname == null) {
                nickname = buddy.getScreenname();
            }
            int groupid = buddy.getGroupId();
            String groupname = groups.get(groupid).getGroupName();
            legacyusers.add(new TransportBuddy(buddy.getScreenname(), nickname, groupname));
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync oscar contact list for " + getJID());
        }
    }

    /**
     * Asks for transport to send information about a contact if possible.
     *
     * @param jid JID of contact to be probed.
     */
    public void retrieveContactStatus(JID jid) {
        bosConn.getAndSendStatus(jid.getNode());
    }

}
