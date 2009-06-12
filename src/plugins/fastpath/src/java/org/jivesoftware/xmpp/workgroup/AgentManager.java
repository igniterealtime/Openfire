/**
 * $Revision: 32969 $
 * $Date: 2006-08-07 10:40:31 -0700 (Mon, 07 Aug 2006) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.xmpp.workgroup.utils.FastpathConstants;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.group.Group;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * <p>Simple agent manager implementation without caching or intelligent/lazy loading of data.</p>
 * <p>This implementation loads all agents into memory so the number of agents is limited by the amount of
 * available heap. Our biggest customer estimates maybe 50 agents so this doesn't seem to be an unreasonable
 * implemenation strategy. Should be revisited when looking to scale beyond a few hundred agents.</p>
 *
 * @author Derek DeMoro
 */
public class AgentManager {

    private static final String LOAD_AGENTS =
            "SELECT agentID FROM fpAgent";
    private static final String INSERT_AGENT =
            "INSERT INTO fpAgent (agentID, agentJID, name, maxchats, minchats) VALUES (?,?,?,?,?)";
    private static final String DELETE_AGENT =
            "DELETE FROM fpAgent WHERE agentID=?";
    private static final String DELETE_AGENT_PROPS =
            "DELETE FROM fpAgentProp WHERE ownerID=?";

    private Map<String, Agent> agents = new HashMap<String, Agent>();

    public AgentManager() {
        loadAgents();
    }

    /**
     * Returns an agent based on it's <tt>JID</tt>.
     *
     * @param agentJID the <tt>JID</tt> of the agent.
     * @return an Agent
     * @throws AgentNotFoundException if the agent could not be loaded.
     */
    public Agent getAgent(JID agentJID) throws AgentNotFoundException {
        Agent agent = agents.get(agentJID.toBareJID());

        if (agent == null) {
            throw new AgentNotFoundException(agentJID.toBareJID());
        }
        return agent;
    }

    /**
     * Returns true if the specified agent exists.
     *
     * @param agentJID the <tt>JID</t> of the agent.
     * @return true if the agent exists, otherwise false.
     */
    public boolean hasAgent(JID agentJID) {
        try {
            getAgent(agentJID);
        }
        catch (AgentNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Returns an agent based on it's agent ID.
     *
     * @param agentID the agentID of the agent.
     * @return an agent.
     * @throws AgentNotFoundException if the agent could not be loaded. 
     */
    public Agent getAgent(long agentID) throws AgentNotFoundException {
        for (Agent agent : agents.values()) {
            if (agent.getID() == agentID) {
                return agent;
            }
        }

        throw new AgentNotFoundException(Long.toHexString(agentID));
    }

    /**
     * Returns the number of agents
     *
     * @return the total number of agents.
     */
    public int getAgentCount() {
        return agents.size();
    }

    public Collection<Agent> getAgents() {
        return Collections.unmodifiableList(new ArrayList<Agent>(agents.values()));
    }

    public Iterator getAgents(WorkgroupResultFilter filter) {
        return filter.filter(agents.values().iterator());
    }

    /**
     * Returns the session of the requested agent. If a session is not found
     * then a <tt>null</tt> value will be return. If no agent was found for the
     * specified JID then an AgentNotFoundException will be thrown.
     *
     * @param agentJID the JID of the agent to look for an AgentSession.
     * @return the session of the requested agent in this workgroup.
     * @throws AgentNotFoundException If no agent was found for the specified JID.
     */
    public AgentSession getAgentSession(JID agentJID) throws AgentNotFoundException {
        return getAgent(agentJID).getAgentSession();
    }

    /**
     * Creates a new agent from an <code>XMPPAddress</code>
     *
     * @param agentJID the <code>XMPPAddress</code>
     * @return the <code>Agent</code> created.
     * @throws IllegalArgumentException if not a valid agent JID, such as a JID without a node.
     */
    public Agent createAgent(JID agentJID) throws IllegalArgumentException {
        if (!ModelUtil.hasLength((agentJID.getNode()))) {
            throw new IllegalArgumentException("No anonymous agents allowed");
        }

        // Get the next ID for workgroup agents.
        long agentID = SequenceManager.nextID(FastpathConstants.WORKGROUP_AGENT);

        if (!insertAgent(agentID, agentJID)) {
            throw new IllegalArgumentException("Agent could not be created");
        }

        Agent agent = new Agent(agentID);
        agent.setAgentJID(agentJID);
        agent.setNickname(agentJID.getNode());
        agents.put(agentJID.toBareJID(), agent);
        return agent;
    }

    /**
     * Remove the specified agent.
     *
     * @param agentJID the jid of the agent to remove.
     * @throws IllegalArgumentException if not a valid agent JID.
     */
    public void deleteAgent(JID agentJID) throws IllegalArgumentException {
        Agent agent = agents.remove(agentJID.toBareJID());
        if (agent != null) {
            Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(DELETE_AGENT);
                pstmt.setLong(1, agent.getID());
                pstmt.executeUpdate();
                pstmt.close();

                pstmt = con.prepareStatement(DELETE_AGENT_PROPS);
                pstmt.setLong(1, agent.getID());
                pstmt.executeUpdate();
            }
            catch (SQLException sqle) {
                Log.error(sqle);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
    }

    /**
     * Returns all agents belonging to a Shared Group.
     *
     * @param group the shared group.
     * @return the collection of agents.
     */
    public Collection<Agent> getAgents(Group group) {
        final Set<Agent> set = new HashSet<Agent>();
        for (JID jid : group.getMembers()) {
            Agent agent;
            try {
                agent = getAgent(jid);
            }
            catch (AgentNotFoundException e) {
                agent = createAgent(jid);
            }
            set.add(agent);
        }

        return set;
    }

    /**
     * Return true if the agent belongs to the given workgroup.
     *
     * @param agent     the agent.
     * @param workgroup the workgroup.
     * @return true if the agent is in the workgrouop.
     */
    public boolean isInWorkgroup(Agent agent, Workgroup workgroup) {
        boolean isMember = false;
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            if (!isMember) {
                isMember = requestQueue.isMember(agent);
            }
        }
        return isMember;
    }

    /**
     * Removes an agent from the system if they no longer belong to any Workgroups.
     *
     * @param agent the agent to remove.
     */
    public void removeAgentIfNecessary(Agent agent) {
        // Go through all workgroups to see if this user is in RequestQueues
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        for (Workgroup workgroup : workgroupManager.getWorkgroups()) {
            for (RequestQueue queue : workgroup.getRequestQueues()) {
                if (queue.getMembers().contains(agent)) {
                    return;
                }

                for (Group group : queue.getGroups()) {
                    if (group.isUser(agent.getAgentJID())) {
                        return;
                    }
                }
            }
        }

        try {
            // Send Presence to Workgroup that this user is no longer available.
            AgentSession session = agent.getAgentSession();
            if (session != null) {
                for (Workgroup workgroup : session.getWorkgroups()) {
                    session.depart(workgroup);
                }
            }

            // If we get here, then remove from agent.
            deleteAgent(agent.getAgentJID());
        }
        catch (IllegalArgumentException e) {
            Log.error(e);
        }
    }

    /**
     * Adds an agent to the Database.
     *
     * @param agentID  the id of the agent to add.
     * @param agentJID the jid of the agent.
     * @return true if the agent was added, otherwise false.
     */
    private boolean insertAgent(long agentID, JID agentJID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_AGENT);
            pstmt.setLong(1, agentID);
            // If the JID of the agent is of the local server then just store the username
            String hostname = ComponentManagerFactory.getComponentManager().getServerName();
            String agentBareJID = agentJID.toBareJID();
            if (hostname.equals(agentJID.getDomain())) {
                agentBareJID = agentJID.getNode();
            }
            pstmt.setString(2, agentBareJID);
            pstmt.setString(3, agentJID.getNode());
            pstmt.setInt(4, -1);
            pstmt.setInt(5, -1);
            pstmt.executeUpdate();
            return true;
        }
        catch (Exception ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private void loadAgents() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_AGENTS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final Agent agent = new Agent(rs.getLong(1));
                agents.put(agent.getAgentJID().toBareJID(), agent);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }
}