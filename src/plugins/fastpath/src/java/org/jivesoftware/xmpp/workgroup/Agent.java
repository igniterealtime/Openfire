/**
 * $RCSfile$
 * $Revision: 19210 $
 * $Date: 2005-07-01 12:17:56 -0700 (Fri, 01 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.xmpp.workgroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.xmpp.workgroup.spi.JiveLiveProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * Workgroup agents, which are stored in the database.
 *
 * @author Derek DeMoro
 */
public class Agent {

	private static final Logger Log = LoggerFactory.getLogger(Agent.class);

    private static final String LOAD_AGENT =
            "SELECT name, agentJID, maxchats FROM fpAgent WHERE agentID=?";
    private static final String SAVE_AGENT =
            "UPDATE fpAgent SET name=?, agentJID=?, maxchats=? WHERE agentID=?";

    /**
     * The agent session created when the agent joined the service.
     */
    private AgentSession session;

    /**
     * The ceiling on the maximumn number of chats the agent should handle.
     */
    protected int maxChats = 0;

    /**
     * Nickname of the agent.
     */
    private String nickname;

    /**
     * Custom properties for the agent.
     */
    private JiveLiveProperties properties;

    /**
     * The id of the agent.
     */
    private long id;

    /**
     * The XMPP address of the Agent.
     */
    private JID agentJID;


    public Agent(long agentID) {
        this.id = agentID;

        // Load this individual agent
        loadAgent(agentID);
    }

    public String toString() {
        return "AI-" + Integer.toHexString(hashCode()) + " JID " + agentJID.toString() + " MAX " +
                Integer.toString(maxChats);
    }

    /**
     * Returns the session of the agent or <tt>null</tt> if the agent has not logged.<p>
     *
     * @return the session of the agent in the workgroup service or null if user is not logged.
     */
    public AgentSession getAgentSession() {
        return session;
    }

    /**
     * Creates a new agent session for the agent connected at the given full JID. The user can
     * only be logged from one resource. If a user tries to have more than one session (i.e. tries
     * to join from two different resources) then depending on the settings one of this outcomes
     * could happen: 1) the existing session is closed and a new session is created or 2) the new
     * session fails to be created and a <tt>null</tt> value is returned.
     *
     * @param userJID the full JID of the user that is creating a new session.
     * @return the new agent session of <tt>null</tt> if the session failed to be created.
     */
    public synchronized AgentSession createSession(JID userJID) {
        if (session != null) {
            // Verify that existing session belongs to the same full JID
            if (!session.getJID().equals(userJID)) {
                // Check that existing session is joined to at least one group
                if (!session.getWorkgroups().isEmpty()) {
                    // TODO Implement conflict policy to decide if existing session is closed and new one is created or new one fails and null is returned 
                    // Handle conflict since the same agent is trying to connect from 2 different resources
                    return null;
                }
            }
            else {
                // User is already connected from this resource so return the existing session
                return session;
            }
        }
        session = new AgentSession(userJID, this);
        return session;
    }

    public synchronized void closeSession(JID userJID) {
        if (session != null) {
            // Verify that existing session belongs to the same full JID
            if (session.getJID().equals(userJID)) {
                session = null;
            }
        }
    }

    public String getNickname() {
        // Lazy initialize the nickname based on the JID's node. This is useful
        // for compatibility with old versions where nickname may be null.
        if (nickname == null && agentJID != null) {
            nickname = agentJID.getNode();
        }
        return nickname;
    }

    public void setNickname(String name) {
        // Do nothing if setting the same old value
        if (name != null && name.equals(nickname)) {
            return;
        }
        // Set the new value
        this.nickname = name;
        // Save the new Agent's state to the database
        saveAgent();
    }

    public Element getAgentInfo() {
        // Create an agent element
        Element element = DocumentHelper.createElement(QName.get("agent",
                "http://jabber.org/protocol/workgroup"));
        element.addAttribute("jid", getAgentJID().toString());
        // Add the name of the agent
        if (getNickname() != null) {
            element.addElement("name", "http://jivesoftware.com/protocol/workgroup").setText(getNickname());
        }

        return element;
    }

    public DbProperties getProperties() {
        if (properties == null) {
            properties = new JiveLiveProperties("fpAgentProp", id);
        }
        return properties;
    }

    private void loadAgent(long agentID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_AGENT);
            pstmt.setLong(1, agentID);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                nickname = rs.getString(1);
                String agentJID = rs.getString(2);
                // If the agentJID was just a username then create a JID where the domain is the
                // local server
                if (!agentJID.contains("@")) {
                    agentJID = agentJID + "@" +
                            ComponentManagerFactory.getComponentManager().getServerName();
                }
                this.agentJID = new JID(agentJID);
                maxChats = rs.getInt(3);
            }
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void saveAgent() {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_AGENT);
            pstmt.setString(1, nickname);
            // If the JID of the agent is of the local server then just store the username
            String hostname = ComponentManagerFactory.getComponentManager().getServerName();
            String agentBareJID = agentJID.toBareJID();
            if (hostname.equals(agentJID.getDomain())) {
                agentBareJID = agentJID.getNode();
            }
            pstmt.setString(2, agentBareJID);
            pstmt.setInt(3, maxChats);
            pstmt.setLong(4, id);
            pstmt.executeUpdate();
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public Long getID(){
        return id;
    }

    public void setAgentJID(JID agentJID) {
        // Do nothing if setting the same old value
        if (agentJID != null && agentJID.equals(agentJID)) {
            return;
        }
        // Set the new value
        this.agentJID = agentJID;
        // Save the new Agent's state to the database
        saveAgent();
    }

    public JID getAgentJID(){
        return agentJID;
    }

    /**
     * This agent has been added to a queue so we need to inform the existing agents of the queue
     * ,that previously requested agent information, of this new agent.
     *
     * @param requestQueue the queue where this agent has been added.
     */
    public void sendAgentAddedToAllAgents(RequestQueue requestQueue) {
        Workgroup workgroup = requestQueue.getWorkgroup();
        for (AgentSession session : workgroup.getAgentSessions()) {
            if (session.hasRequestedAgentInfo()) {
                IQ iq = new IQ(IQ.Type.set);
                iq.setFrom(workgroup.getJID());
                iq.setTo(session.getJID());
                Element agentStatusRequest = iq.setChildElement("agent-status-request",
                        "http://jabber.org/protocol/workgroup");
                agentStatusRequest.add(getAgentInfo());
                // Push the new agent info to the agent
                workgroup.send(iq);
            }
        }
    }

    /**
     * This agent has been removed from a queue so we need to inform the existing agents of the
     * queue ,that previously requested agent information, of the agent deletion.
     *
     * @param requestQueue the queue from where this agent has been deleted.
     */
    public void sendAgentRemovedToAllAgents(RequestQueue requestQueue) {
        Workgroup workgroup = requestQueue.getWorkgroup();
        for (AgentSession session : workgroup.getAgentSessions()) {
            if (session.hasRequestedAgentInfo()) {
                IQ iq = new IQ(IQ.Type.set);
                iq.setFrom(workgroup.getJID());
                iq.setTo(session.getJID());
                Element agentStatusRequest = iq.setChildElement("agent-status-request",
                        "http://jabber.org/protocol/workgroup");
                Element agentInfo = getAgentInfo();
                agentInfo.addAttribute("type", "remove");
                agentStatusRequest.add(agentInfo);
                // Push the new agent info to the agent
                workgroup.send(iq);
            }
        }
    }

    public void updateAgentInfo(IQ packet) {
        Element agentInfo = packet.getChildElement();
        // Set the new agent's name
        Element element = agentInfo.element("name");
        if (element != null) {
            setNickname(element.getTextTrim());
        }
        // Commented since we don't want the agent to change its JID address
        /*element = agentInfo.element("jid");
        if (element != null) {
            setAgentJID(new JID(element.getTextTrim()));
        }*/
    }

    /**
     * <p>A comparator that sorts agents by address (toBareStringPrep()).</p>
     * <p>The comparator does not handle other objects, using Agents with any other
     * object type in the same sorted container will cause a ClassCastException to be thrown.</p>
     */
    class AgentAddressComparator implements Comparator<Agent> {
        public int compare(Agent o1, Agent o2) {
            return o1.getAgentJID().toBareJID().compareTo(o2.getAgentJID().toBareJID());
        }
    }
}