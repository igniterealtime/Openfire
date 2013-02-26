/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.session.cluster;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.KrakenPlugin;
import net.sf.kraken.TransportInstance;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.TransportSession;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Keeps track of location of sessions across cluster.
 *
 * @author Daniel Henninger
 */
public class TransportSessionRouter implements ClusterEventListener {

    static Logger Log = Logger.getLogger(TransportSessionRouter.class);

    public static final String TRANSPORTSESSION_CACHE_NAME = "Kraken Session Location Cache";

    /**
     * Cache (unlimited, never expire) that holds the locations of a transport session.
     * Key: transport type (aim, icq, etc) + bare JID, Value: nodeID
     * We store the key like BareJID@transportType so...  user@example.org@msn
     */
    public Cache<String, byte[]> sessionLocations;

    /**
     * The instance of the kraken plugin we are attached to.
     */
    private WeakReference<KrakenPlugin> pluginRef;

    /**
     * Creates a transport session router instance associated with the plugin.
     *
     * @param plugin Kraken plugin instance we are associated with.
     */
    public TransportSessionRouter(KrakenPlugin plugin) {
        pluginRef = new WeakReference<KrakenPlugin>(plugin);
        sessionLocations = CacheFactory.createCache(TRANSPORTSESSION_CACHE_NAME);
        ClusterManager.addListener(this);
    }

    /**
     * Shuts down the transport session router.
     */
    public void shutdown() {
        ClusterManager.removeListener(this);
    }

    /**
     * Retrieves the reference to the plugin we are associated with.
     *
     * @return KrakenPlugin instance.
     */
    public KrakenPlugin getPlugin() {
        return pluginRef.get();
    }

    /**
     * Adds information about a session to the cache.
     *
     * @param transportType Type of transport the session is associated with.
     * @param jid Bare JID of session owner.
     */
    public void addSession(String transportType, String jid) {
        sessionLocations.put(jid+"@"+transportType, XMPPServer.getInstance().getNodeID().toByteArray());
    }

    /**
     * Returns the node id of a session.
     *
     * @param transportType Type of transport the session is associated with.
     * @param jid Bare JID of session owner.
     * @return Node ID that is handling 
     */
    public byte[] getSession(String transportType, String jid) {
        if (!sessionLocations.containsKey(jid+"@"+transportType)) {
            return null;
        }
        return sessionLocations.get(jid+"@"+transportType);
    }
    
    /**
     * Removes information about a session from the cache.
     *
     * @param transportType Type of transport the session is associated with.
     * @param jid Base JID of session owner.
     */
    public void removeSession(String transportType, String jid) {
        sessionLocations.remove(jid+"@"+transportType);
    }
    
    /**
     * @see org.jivesoftware.openfire.cluster.ClusterEventListener#joinedCluster()
     */
    public void joinedCluster() {
        restoreCacheContent();
    }

    /**
     * @see org.jivesoftware.openfire.cluster.ClusterEventListener#joinedCluster(byte[])
     */
    public void joinedCluster(byte[] joiningNodeID) {
        // Do nothing
    }

    /**
     * @see org.jivesoftware.openfire.cluster.ClusterEventListener#leftCluster()
     */
    public void leftCluster() {
        restoreCacheContent();
    }

    /**
     * @see org.jivesoftware.openfire.cluster.ClusterEventListener#leftCluster(byte[])
     */
    public void leftCluster(byte[] leavingNodeID) {
        KrakenPlugin plugin = getPlugin();
        // TODO: Is this correct?  Lets say another node updates an entry before I get to it, will I see the update?
        for (Map.Entry<String,byte[]> entry : sessionLocations.entrySet()) {
            if (Arrays.equals(entry.getValue(), leavingNodeID)) {
                Lock l = CacheFactory.getLock(entry.getKey()+"lc", sessionLocations);
                try {
                    l.lock();
                    String jid = entry.getKey().substring(0, entry.getKey().lastIndexOf("@"));
                    String trType = entry.getKey().substring(entry.getKey().lastIndexOf("@")+1);
                    Log.debug("Kraken: Node handling session "+jid+" on "+trType+" lost, taking over session...");
                    sessionLocations.remove(jid+"@"+trType);
                    TransportInstance trInstance = plugin.getTransportInstance(trType);
                    if (trInstance != null) {
                        BaseTransport<? extends TransportBuddy> transport = trInstance.getTransport();
                        if (transport != null) {
                            Collection<ClientSession> sessions = XMPPServer.getInstance().getSessionManager().getSessions(new JID(jid).getNode());
                            for (ClientSession session : sessions) {
                                transport.processPacket(session.getPresence());
                            }
                        }
                    }
                }
                finally {
                    l.unlock();
                }
            }
        }
    }

    /**
     * @see org.jivesoftware.openfire.cluster.ClusterEventListener#markedAsSeniorClusterMember()
     */
    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    /**
     * Populates the cache with our sessions.
     */
    private void restoreCacheContent() {
        for (String transportName : getPlugin().getTransports()) {
            if (getPlugin().serviceEnabled(transportName)) {
                TransportInstance ti = getPlugin().getTransportInstance(transportName);
                if (ti != null) {
                    BaseTransport<? extends TransportBuddy> tr = ti.getTransport();
                    if (tr != null) {
                        for (TransportSession<? extends TransportBuddy> session : tr.getSessionManager().getSessions()) {
                            addSession(transportName, session.getJID().toBareJID());
                        }
                    }
                }
            }
        }
    }

}
