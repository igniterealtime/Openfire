/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.mediaproxy;

import org.jivesoftware.util.Log;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Media Proxy relays UDP traffic between two IPs to provide connectivity between
 * two parties that are behind NAT devices. It also provides connectivity
 * between two parties that are directly connected to the internet or one party on the
 * internet and another behind a NAT.<p>
 *
 * Each connection relay between two parties is called a session. You can setup a MediaProxy
 * for all network interfaces with an empty constructor, or bind it to a specific interface
 * with the MediaProxy(String localhost) constructor. <i>The media proxy ONLY works if you
 * are directly connected to the Internet with a valid IP address.</i>.
 *
 * @author Thiago Camargo
 */
public class MediaProxy implements SessionListener {

    final private Map<String, MediaProxySession> sessions = new ConcurrentHashMap<String, MediaProxySession>();

    private String ipAddress;

    private int minPort = 10000;
    private int maxPort = 20000;

    private long idleTime = 60000;

    // Lifetime of a Channel in Seconds
    private long lifetime = 9000;

    /**
     * Contruct a MediaProxy instance that will listen on a specific network interface.
     *
     * @param ipAddress the IP address on this server that will listen for packets.
     */
    public MediaProxy(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Get the public IP of this media proxy that listen for incomming packets.
     *
     * @return the host that listens for incomming packets.
     */
    public String getPublicIP() {
        return ipAddress;
    }

    /**
     * Returns the max time (in millis) that a session can remain open without
     * receiving any packets. After this time period elapses, the session is
     * automatically closed. 
     *
     * @return the max idle time (in millis).
     */
    public long getIdleTime() {
        return idleTime;
    }

    /**
     * Sets the max time (in millis) that a session can remain open without
     * receiving any packets. After this time period elapses, the session is
     * automatically closed.
     *
     * @param idleTime the max idle time in millis.
     */
    public void setIdleTime(long idleTime) {
        this.idleTime = idleTime;
    }

    /**
     * Returns the list of all currently active and running sessions.
     *
     * @return List of the Agents
     */
    public Collection<MediaProxySession> getSessions() {
        return sessions.values();
    }

    /**
     * Returns the minimum port value to listen for incoming packets.
     *
     * @return the minimum port value.
     */
    public int getMinPort() {
        return minPort;
    }

    /**
     * Sets the minimum port value to listen from incoming packets.
     *
     * @param minPort the minimum port value.
     */
    public void setMinPort(int minPort) {
        this.minPort = minPort;
    }

    /**
     * Returns the maximum port value to listen for incoming packets.
     *
     * @return the maximun port value.
     */
    public int getMaxPort() {
        return maxPort;
    }

    /**
     * Sets the maximum port value to listen for incoming packets.
     *
     * @param maxPort the maximun port value.
     */
    public void setMaxPort(int maxPort) {
        this.maxPort = maxPort;
    }

    /**
     * Returns the maximum lifetime (in seconds) of a session. After the time period
     * elapses, the session will be destroyed even if currently active.
     * 
     * @return the max lifetime of a session (in seconds).
     */
    public long getLifetime() {
        return lifetime;
    }

    /**
     * Sets the maximum lifetime (in seconds) of a session. After the time period
     * elapses, the session will be destroyed even if currently active.
     *
     * @param lifetime the max lifetime of a session (in seconds).
     */
    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * Returns a media proxy session with the specified ID.
     *
     * @param sid the session ID.
     * @return the session or <tt>null</tt> if the session doesn't exist.
     */
    public MediaProxySession getSession(String sid) {
        MediaProxySession proxySession = sessions.get(sid);
        if (proxySession != null) {
            if (Log.isDebugEnabled()) {
                Log.debug("MediaProxy: SID: " + sid + " agentSID: " + proxySession.getSID());
                return proxySession;
            }
        }
        return null;
    }

    /**
     * Implements Session Listener stopAgent event.
     * Remove the stopped session from the sessions list.
     *
     * @param session the session that stopped
     */
    public void sessionClosed(MediaProxySession session) {
        sessions.remove(session.getSID());
        if (Log.isDebugEnabled()) {
            Log.debug("MediaProxy: Session: " + session.getSID() + " removed.");
        }
    }

    /**
     * Add a new Dynamic Session to the mediaproxy for defined IPs and ports.
     * The IP and port pairs can change depending of the Senders IP and port.
     * Which means that the IP and port values of the points can dynamic change after the Channel is opened.
     * When the agent receives a packet from Point A, the channel set the point A IP and port according to
     * the received packet sender IP and port.
     * Every packet received from Point B will be relayed to the new Point A IP and port.
     * When the agent receives a packet from Point B, the channel set the point B IP and port according to
     * the received packet sender IP and port.
     * Every packet received from Point A will be relayed to the new Point B IP and port.
     * Create a dynamic channel between two IPs. ( Dynamic Point A - Dynamic Point B )
     *
     * @param id      id of the candidate returned (Could be a Jingle session ID)
     * @param creator the agent creator name or description
     * @param hostA   the hostname or IP of the point A of the Channel
     * @param portA   the port number point A of the Channel
     * @param hostB   the hostname or IP of the point B of the Channel
     * @param portB   the port number point B of the Channel
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addRelayAgent(String id, String creator, String hostA, int portA,
            String hostB, int portB)
    {
        RelaySession session = new RelaySession(id, creator, ipAddress, hostA, portA, hostB, portB, minPort, maxPort);
        sessions.put(id, session);
        session.addKeepAlive(idleTime);
        session.addLifeTime(lifetime);
        session.addAgentListener(this);
        return session;
    }

    /**
     * Add a new Dynamic Session to the mediaproxy WITHOUT defined IPs and ports.
     * The IP and port pairs WILL change depending of the Senders IP and port.
     * Which means that the IP and port values of the points will dynamic change after the Channel is opened
     * and received packet from both points.
     * When the agent receives a packet from Point A, the channel set the point A IP and port according to
     * the received packet sender IP and port.
     * Every packet received from Point B will be relayed to the new Point A IP and port.
     * When the agent receives a packet from Point B, the channel set the point B IP and port according to
     * the received packet sender IP and port.
     * Every packet received from Point A will be relayed to the new Point B IP and port.
     * Create a dynamic channel between two IPs. ( Dynamic Point A - Dynamic Point B )
     *
     * @param id id of the candidate returned (Could be a Jingle session ID)
     * @param creator the agent creator name or description
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addRelayAgent(String id, String creator) {
        return addRelayAgent(id, creator, ipAddress, 40000, ipAddress, 40004);
    }

    /**
     * Stop every running sessions.
     */
    void stopProxy() {
        for (MediaProxySession session : getSessions()) {
            try {
                session.clearAgentListeners();
                session.stopAgent();
            }
            catch (Exception e) {
                Log.error("Error cleaning up media proxy sessions", e);
            }
        }
        sessions.clear();
    }
}