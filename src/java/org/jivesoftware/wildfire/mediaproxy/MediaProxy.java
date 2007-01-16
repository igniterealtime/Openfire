/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.mediaproxy;

import org.jivesoftware.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * A Media Proxy relays UDP traffic between two IPs to provide connectivity between
 * two parties that are behind NAT devices. It also provides connectivity
 * between two parties that are directly connected to the internet or one party on the
 * internet and another behind a NAT.<p>
 *
 * Each connection relay between two parties is called a session. You can setup a MediaProxy
 * for all network interfaces with an empty constructor, or bind it to a specific interface
 * with the MediaProxy(String localhost) constructor. <i>The media proxy ONLY works if your
 * are directly connected to the Internet with a valid IP address.</i>.
 */
public class MediaProxy implements SessionListener {

    final private List<MediaProxySession> sessions = new ArrayList<MediaProxySession>();

    private String localhost;

    private int minPort = 10000;
    private int maxPort = 20000;

    private long idleTime = 60;

    // Lifetime of a Channel in Seconds
    private long lifetime = 9000;

    /**
     * Contruct a MediaProxy instance that will listen from every Network Interface.
     * Recommended.
     */
    public MediaProxy() {
        this.localhost = "localhost";
    }

    /**
     * Contruct a MediaProxy instance that will listen on a specific network interface.
     *
     * @param localhost the IP of the locahost that will listen for packets.
     */
    public MediaProxy(String localhost) {
        this.localhost = localhost;
    }

    /**
     * Get the public IP of this RTP Proxy that listen for the incomming packets
     *
     * @return the host that listens for incomming packets.
     */
    public String getPublicIP() {
        return localhost;
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
    public List<MediaProxySession> getSessions() {
        return sessions;
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
        for (MediaProxySession session : sessions) {
            if (session.getSID().equals(sid)) {
                System.out.println("SID: " + sid + " agentSID: " + session.getSID());
                return session;
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
        sessions.remove(session);
        Log.debug("Session: " + session.getSID() + " removed.");
    }

    /**
     * Add a new Static Session to the mediaproxy for defined IPs and ports.
     * Create a channel between two IPs. ( Point A - Point B )
     *
     * @param id      id of the candidate returned (Could be a Jingle session ID)
     * @param creator the agent creator name or description
     * @param hostA   the hostname or IP of the point A of the Channel
     * @param portA   the port number point A of the Channel
     * @param hostB   the hostname or IP of the point B of the Channel
     * @param portB   the port number point B of the Channel
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addAgent(String id, String creator, String hostA, int portA, String hostB,
               int portB)
    {
        MediaProxySession session = new MediaProxySession(
                id, creator, localhost, hostA, portA, hostB, portB, minPort, maxPort);
        sessions.add(session);
        session.addKeepAlive(idleTime);
        session.addLifeTime(lifetime);
        session.addAgentListener(this);
        return session;
    }

    /**
     * Add a new Dynamic Session to the mediaproxy for defined IPs and ports.
     * The IP and port pairs can change depending of the Senders IP and port.
     * Which means that the IP and port values of the points can dynamic change after the Channel is opened.
     * When the agent receives a packet from Point A, the channel set the point A IP and port according to the received packet sender IP and port.
     * Every packet received from Point B will be relayed to the new Point A IP and port.
     * When the agent receives a packet from Point B, the channel set the point B IP and port according to the received packet sender IP and port.
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
    public ProxyCandidate addSmartAgent(String id, String creator, String hostA, int portA,
            String hostB, int portB)
    {
        SmartSession session = new SmartSession(id, creator, localhost, hostA, portA, hostB, portB,
                minPort, maxPort);
        sessions.add(session);
        session.addKeepAlive(idleTime);
        session.addLifeTime(lifetime);
        session.addAgentListener(this);
        return session;
    }

    /**
     * Add a new Dynamic Session to the mediaproxy WITHOUT defined IPs and ports.
     * The IP and port pairs WILL change depending of the Senders IP and port.
     * Which means that the IP and port values of the points will dynamic change after the Channel is opened and received packet from both points.
     * When the agent receives a packet from Point A, the channel set the point A IP and port according to the received packet sender IP and port.
     * Every packet received from Point B will be relayed to the new Point A IP and port.
     * When the agent receives a packet from Point B, the channel set the point B IP and port according to the received packet sender IP and port.
     * Every packet received from Point A will be relayed to the new Point B IP and port.
     * Create a dynamic channel between two IPs. ( Dynamic Point A - Dynamic Point B )
     *
     * @param id id of the candidate returned (Could be a Jingle session ID)
     * @param creator the agent creator name or description
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addSmartAgent(String id, String creator) {
        return addSmartAgent(id, creator, localhost, 40000, localhost, 40004);
    }

    /**
     * Stop every running sessions.
     */
    public void stopProxy() {
        for (MediaProxySession session : getSessions()) {
            try {
                session.clearAgentListeners();
                session.stopAgent();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        sessions.clear();
    }
}