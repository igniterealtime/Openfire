/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.jivesoftware.wildfire.gateway.AbstractGatewaySession;
import org.jivesoftware.wildfire.gateway.Endpoint;
import org.jivesoftware.wildfire.gateway.EndpointValve;
import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.JabberEndpoint;
import org.jivesoftware.wildfire.gateway.SubscriptionInfo;
import org.jivesoftware.wildfire.gateway.roster.AbstractForeignContact;
import org.jivesoftware.wildfire.gateway.roster.ForeignContact;
import org.jivesoftware.wildfire.gateway.roster.NormalizedJID;
import org.jivesoftware.wildfire.gateway.roster.PersistenceManager;
import org.jivesoftware.wildfire.gateway.roster.UnknownForeignContactException;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import ymsg.network.LoginRefusedException;
import ymsg.network.Session;
import ymsg.network.YahooGroup;
import ymsg.network.YahooUser;

/**
 * NOT THREAD SAFE
 * 
 * Manages the session to the underlying legacy system.
 * 
 * @author Noah Campbell
 */
public class YahooGatewaySession extends AbstractGatewaySession implements Endpoint {

    /**
     * Yahoo Session
     */
    public final Session session; 

    /**
     * The JID associated with this session.
     * 
     * @see org.xmpp.packet.JID
     */
    private final JID jid;  // JID associated with this session

    /**
     * Initialize a new session object for Yahoo!
     * 
     * @param info The subscription information to use during login.
     * @param gateway The gateway that created this session.
     */
    public YahooGatewaySession(SubscriptionInfo info, Gateway gateway) {
        super(info, gateway);
        this.jid = info.jid;

        session = new Session();
        session.addSessionListener(new YahooSessionListener(this));
    }

    /** The attemptingLogin. */
    private boolean attemptingLogin = false;

    /** The loginFailed. */
    private boolean loginFailed = false;

    /** The loginFailedCount. */
    private int loginFailedCount = 0;

    /**
     * Manage presense information.
     */
//    public void updatePresence() {
//        if(isConnected()) {
//
//            for(YahooGroup group : session.getGroups()) {
//                Vector members = group.getMembers();
//                for(Object member : members) {
//                    YahooUser user = (YahooUser)member;
//                    Log.info("Adding foreign contact: " + user.getId());
//                    String foreignId = user.getId();
//                    NormalizedJID whois = NormalizedJID.wrap(gateway.whois(foreignId));
//                    AbstractForeignContact fc = PersistenceManager.Factory.get(gateway)
//                        .getContactManager().getRoster(this.jid)
//                        .getForeignContact(foreignId, gateway);
//                    
//                    if(fc == null || fc.status == null) {
//                        Log.warn("Unable to find Foreign Contact for: " + whois);
//                        continue;
//                    }
//                    if(user == null) {
//                        Log.warn("Invalid Yahoo user");
//                        continue;
//                    }
//                    if(fc.status.getValue() != null && !fc.status.getValue().equalsIgnoreCase(user.getCustomStatusMessage())) {
//                        Log.debug(LocaleUtils.getLocalizedString("yahoogatewaysession.status", "gateway", Arrays.asList(new Object[] {fc.status})));
//                        try {
//                            Presence p = new Presence();
//                            if(!fc.status.isSubscribed()) {
//                                p.setType(Presence.Type.subscribe);
//                                fc.status.setSubscribed(true);
//                            }
//                            p.setFrom(user.getId() +  "@" + gateway.getName() + "." + gateway.getDomain());
//                            p.setTo(this.jid);
//                            gateway.sendPacket(p);
//                            
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }        
//                    }
//                    
//                }
//            }
//        }
//    } 

    /**
     * Login to the Yahoo! Messenger serverice
     * @throws Exception 
     */
    public synchronized void login() throws Exception {
        if (!isConnected() && !loginFailed && !attemptingLogin) {
            attemptingLogin = true;
            new Thread() {
                @Override
                public void run() {
                    try {
                        session.login(getSubscriptionInfo().username, 
                                new String(getSubscriptionInfo().password));
                        /**
                         * Password is stored in the JVM as a string in JVM
                         * util termination.
                         */
                        Log.info(LocaleUtils.getLocalizedString("yahoogatewaysession.login", "gateway", Arrays.asList(new Object[]{jid, getSubscriptionInfo().username})));
                        getJabberEndpoint().getValve().open(); // allow any buffered messages to pass through
//                        updatePresence();
                    }
                    catch (LoginRefusedException lre) {
                        session.reset();
                        if (loginFailedCount++ > 3) { 
                            loginFailed = true;
                        }
                        Log.warn("Login failed for " + getSubscriptionInfo().username);
                    }
                    catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    attemptingLogin = false;
                }
            }.run(); // intentionally not forked.
        }
        else {
            Log.warn(this.jid + " is already logged in");
        }
    }

    /**
     * Is the connection connected?
     * @return connected is the session connected?
     */
    public boolean isConnected() {
        return session.getSessionStatus() == Session.MESSAGING;
    }

    /**
     * Logout from Yahoo!
     * @throws Exception 
     */
    public synchronized void logout() throws Exception {
        Log.info("[" + this.jid + "]" + getSubscriptionInfo().username + " logged out.");
        session.logout();
        session.reset();
    }

    /**
     * @see java.lang.Object#toString() 
     */
    @Override
    public String toString() {
        return "[" + this.getSubscriptionInfo().username + " CR:" + clientRegistered + " SR:" + serverRegistered + "]";
    }

    /**
     * Return the id of this session as a <code>String</code>.
     * @return ID the jid for this session.
     * @see org.xmpp.packet.JID
     */
    public String getId() {
        return this.jid.toBareJID();
    }

    /**
     * Returns all the contacts associated with this Session.
     * @return contacts A list of <code>String</code>s.
     */
    @SuppressWarnings("unchecked")
    public List<ForeignContact> getContacts() {
        Map users = session.getUsers();
        ArrayList<ForeignContact> contacts = new ArrayList<ForeignContact>(users.size());
        for (YahooUser user : (Collection<YahooUser>)session.getUsers().values()) {
            contacts.add(new YahooForeignContact(user, this.gateway));
        }
        return contacts;
    }

    /**
     * Return the <code>JID</code> for this session.
     * @return JID The jid for this session.
     * @see org.xmpp.packet.JID
     */
    public JID getSessionJID() {
        return this.jid;
    }

    /**
     * Returns the <code>JID> for this session.
     * @return JID The jid for this session.
     * @see org.xmpp.packet.JID
     * @deprecated
     */
    public JID getJID() {
        return this.jid;
    }

    /**
     * Return the status for the JID.  This will be translated into a legacy request.
     * @param to JID of user we're looking for
     * @return String status of JID
     */
    public String getStatus(JID to) {
        Map table = this.session.getUsers();
        if (isConnected() && table != null && to.getNode() != null && table.containsKey(to.getNode())) {
            YahooUser user = this.session.getUser(to.getNode());
            return user.getCustomStatusMessage();
        }
        else {
            return null;
        }
    }

    /**
     * Add a contact to this session.  This will update the legacy roster and 
     * will be persisted across sessions.
     * @param jid The JID of the friend.
     * @throws Exception
     */
    public void addContact(JID jid) throws Exception {
        String node = jid.getNode();
        session.addFriend(node, "jabber");
    }

    /**
     * Remove a contact from this session.  This will update the legacy roster
     * and will be persisted across session.
     * @param jid The friend to be removed.
     * @throws Exception 
     */
    public void removeContact(JID jid) throws Exception {
        String node = jid.getNode();
        session.removeFriend(node, "jabber");
    }

    /**
     * Sends a packet to the legacy system.  It'll translate the XMPP request
     * into a custom request.
     * @param packet incoming packet from XMPP Server (typically from client)
     */
    public void sendPacket(Packet packet) {
        Log.debug(packet.toString());
        if (packet instanceof Message) {
            Message m = (Message)packet;
            try {
                session.sendMessage(packet.getTo().getNode(), m.getBody());
            }
            catch (IOException ioe) {
                Log.warn(ioe.getLocalizedMessage());
            }
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.GatewaySession#getContact(org.xmpp.packet.JID)
     */
    public ForeignContact getContact(JID to) throws UnknownForeignContactException {
        String node = to.getNode();
        if (node == null || node.length() == 0) throw new UnknownForeignContactException("invalidnode", node.toString());
        YahooUser user = session.getUser(node);
        Log.debug("getUser on node " + node);
        if (user == null) throw new UnknownForeignContactException("invaliduser");
        return new YahooForeignContact(session.getUser(to.getNode()), this.gateway);
    }

}
