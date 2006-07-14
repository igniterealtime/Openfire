/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.roster;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.GatewaySession;
import org.jivesoftware.wildfire.gateway.SubscriptionInfo;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

/**
 * 
 * This class is responsible for managing all the registrations between the 
 * legacy system and the XMPP Server.
 * 
 * @author Noah Campbell
 */
public class Registrar implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a new <code>Registrar</code>.
     */
    Registrar() {
        createSessionCache();
    }

    /**
     * Initializes a new session cache
     */
    private void createSessionCache() {
        sessions = new HashMap<JID, GatewaySession>();
    }

    /**
     * GatewaySessions are maintained in the registration
     */
    private final Map<NormalizedJID, SubscriptionInfo> registrar = new HashMap<NormalizedJID, SubscriptionInfo>();

    /**
     * The <code>GatewaySessions</code> mapped by <code>JID</code>
     *
     * @see java.util.Map
     */
    private transient Map<JID, GatewaySession> sessions;

    /**
     * Determine if the JID is registered (based on the bare JID)
     * @param id
     * @return true/false
     */
    public boolean isRegistered(NormalizedJID id) {
        return registrar.containsKey(id);
    }

    /**
     * Add a JID to the registrar.
     * @param id
     * @param info
     */
    public void add(JID id, SubscriptionInfo info) {
        registrar.put(NormalizedJID.wrap(id), info);
        //timer.scheduleAtFixedRate(info, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Get the Session for a JID.
     * 
     * @param jid the <code>JID</code>
     * @return session the gateway session
     * @throws Exception 
     */
    public GatewaySession getGatewaySession(JID jid) throws Exception {
        SubscriptionInfo info = registrar.get(NormalizedJID.wrap(jid));
        if (info == null) {
            throw new IllegalStateException("Please register before attempting to get a session");
        }
        if (!sessions.containsKey(jid)) {
            info.jid = jid;
            
            GatewaySession session = this.gateway.getSessionFactory().newInstance(info);
            session.login();
            Log.info("Creating session for: " + jid);
            sessions.put(jid, session);
        }
        return sessions.get(jid);
    }

    /**
     * @return collection of subscriptionInfos
     */
    public Collection<SubscriptionInfo> getAllGatewaySessions() {
        return registrar.values();
    }

    /**
     * @param gateway
     */
    void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    /**
     * The gateway.
     *
     * @see Gateway
     */
    private transient Gateway gateway;

    /**
     * Removes the NormalizedJID from the registrar.
     * @param jid
     */
    void remove(JID jid) {
        NormalizedJID wrapped = NormalizedJID.wrap(jid);
        registrar.remove(wrapped);
        GatewaySession session = sessions.get(jid);
        if (session.isConnected()) {
            try {
                session.logout();
            }
            catch (Exception e) {
               // silently ignore
            }
        }
    }

    /**
     * @param ois
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.createSessionCache();
    }

}
