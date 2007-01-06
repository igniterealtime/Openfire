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

import java.util.List;
import java.util.ArrayList;
import java.net.InetAddress;

/**
 * MediaProxy create and bind relay channels between IP pairs.
 * This relay can provite UDP connectivity between two parties that are behind NAT.
 * It also work to provide connectivity between two parties that are directly connected to the internet or one party in the internet and another behind a NAT.
 * The MediaProxy Class add and control the Bridge Agents.
 * You can setup a MediaProxy for all your network interfaces with a empty constructor,
 * or bind it to an especific interface with MediaProxy(String localhost) constructor.
 * <i>This MediaProxy ONLY works if your are Direct Connected to the Internet with a valid IP address.</i>
 */
public class MediaProxy implements SessionListener {

    final private List<MediaProxySession> sessions = new ArrayList<MediaProxySession>();

    private String localhost;

    private int minPort = 10000;
    private int maxPort = 20000;

    private long idleTime = 90000;

    /**
     * Contruct a MediaProxy instance that will listen from every Network Interface.
     * Recommended.
     */
    public MediaProxy() {
        this.localhost = "localhost";
    }

    /**
     * Contruct a MediaProxy instance that will listen from an especific Network Interface.
     *
     * @param localhost The IP of the locahost that will listen for packets.
     */
    public MediaProxy(String localhost) {
        this.localhost = localhost;
    }

    /**
     * Get the public IP of this RTP Proxy that listen for the incomming packets
     *
     * @return Your selected localhost that listen for the incomming packets
     */
    public String getPublicIP() {
        return localhost;
    }

    /**
     * Get time in millis that an Session can stay without receive any packet.
     * After this time it is auto closed.
     *
     * @return Time in millis
     */
    public long getKeepAliveDelay() {
        return idleTime;
    }

     /**
     * Returns the maximum amount of time (in milleseconds) that a session can
     * be idle before it's closed.
     *
     * @param idleTime the max idle time in millis.
     */
    public void setIdleTime(long idleTime) {
        this.idleTime = idleTime;
    }

    /**
     * Get the List of all the current active and running Agents.
     *
     * @return List of the Agents
     */
    public List<MediaProxySession> getAgents() {
        return sessions;
    }

    /**
     * Get Minimal port value to listen from incoming packets.
     *
     * @return
     */
    public int getMinPort() {
        return minPort;
    }

    /**
     * Set Minimal port value to listen from incoming packets.
     *
     * @param minPort
     */
    public void setMinPort(int minPort) {
        this.minPort = minPort;
    }

    /**
     * Get Maximum port value to listen from incoming packets.
     *
     * @return
     */
    public int getMaxPort() {
        return maxPort;
    }

    /**
     * Set Maximum port value to listen from incoming packets.
     *
     * @param maxPort
     */
    public void setMaxPort(int maxPort) {
        this.maxPort = maxPort;
    }

    /**
     * Get the agent with an especified ID
     *
     * @param sid the session ID
     */
    public MediaProxySession getAgent(String sid) {
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
     * @param session the session to
     */
    public void sessionClosed(MediaProxySession session) {
        sessions.remove(session);
        Log.debug("Session: " + session.getSID() + " removed.");
    }

    /**
     * Add a new Static Session to the mediaproxy for defined IPs and ports.
     * Create a channel between two IPs. ( Point A - Point B )
     *
     * @param id    id of the candidate returned (Could be a Jingle session ID)
     * @param hostA the hostname or IP of the point A of the Channel
     * @param portA the port number point A of the Channel
     * @param hostB the hostname or IP of the point B of the Channel
     * @param portB the port number point B of the Channel
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addAgent(String id, String creator, String hostA, int portA, String hostB,
            int portB) {
        final MediaProxySession session =
                new MediaProxySession(id, creator, localhost, hostA, portA, hostB, portB, minPort, maxPort);
        if (session != null) {
            sessions.add(session);
            session.addKeepAlive(idleTime);
            session.addAgentListener(this);
        }
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
     * @param id    id of the candidate returned (Could be a Jingle session ID)
     * @param hostA the hostname or IP of the point A of the Channel
     * @param portA the port number point A of the Channel
     * @param hostB the hostname or IP of the point B of the Channel
     * @param portB the port number point B of the Channel
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addSmartAgent(String id, String creator, String hostA, int portA,
            String hostB, int portB) {
        final SmartSession agent = new SmartSession(id, creator, localhost, hostA, portA, hostB, portB,
                minPort, maxPort);
        if (agent != null) {
            sessions.add(agent);
            agent.addKeepAlive(idleTime);
            agent.addAgentListener(this);
        }
        return agent;
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
     * @return the added ProxyCandidate
     */
    public ProxyCandidate addSmartAgent(String id, String creator) {
        return addSmartAgent(id, creator, localhost, 40000, localhost, 40004);
    }

    /**
     * Stop every running sessions.
     */
    public void stopProxy() {
        for (MediaProxySession session : getAgents()) {
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