/**
 * $RCSfile$
 * $Revision: 32958 $
 * $Date: 2006-08-07 09:12:40 -0700 (Mon, 07 Aug 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.util.WebManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class WorkgroupAdminManager extends WebManager {
    private int range = 15;

    public WorkgroupAdminManager() {
    }

    public WorkgroupManager getWorkgroupManager() {
        return WorkgroupManager.getInstance();
    }

    /**
     * Returns the number of active agents in a workgroup.
     *
     * @param workgroup the workgroup.
     * @return number of active agents.
     */
    public int getActiveAgentMemberCount(Workgroup workgroup) {
        Map<Agent, Agent> agents = new HashMap<Agent, Agent>();
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            for (Agent agent : requestQueue.getMembers()) {
                AgentSession agentSession = agent.getAgentSession();
                if (agentSession != null && agentSession.isAvailableToChat() && agentSession.getWorkgroups().contains(workgroup)) {
                    agents.put(agent, agent);
                }
            }

            final AgentManager agentManager = workgroup.getAgentManager();
            for (Group group : requestQueue.getGroups()) {
                for (Agent agent : agentManager.getAgents(group)) {
                    AgentSession agentSession = agent.getAgentSession();
                    if (agentSession != null && agentSession.isAvailableToChat() && agentSession.getWorkgroups().contains(workgroup)) {
                        agents.put(agent, agent);
                    }
                }
            }
        }
        return agents.size();
    }


    public int getWaitingCustomerCount(Workgroup workgroup) {
        int count = 0;
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            count += requestQueue.getRequestCount();
        }
        return count;
    }

    // TODO: should allow pretty printed version of agent names aka name <address>, or name, or address
    public Collection getAgentsInWorkgroup(Workgroup workgroup) {
        TreeSet<Agent> agents = new TreeSet<Agent>(new AgentAddressComparator());
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            for (Agent member : requestQueue.getMembers()) {
                agents.add(member);
            }
        }
        return agents;
    }


    public int getNumPages() {
        return (int)Math.ceil((double)getWorkgroupManager().getWorkgroupCount() / (double)range);
    }

    /**
     * <p>A comparator that sorts agents by address (toBareStringPrep()).</p>
     * <p>The comparator does not handle other objects, using Agents with any other
     * object type in the same sorted container will cause a ClassCastException to be thrown.</p>
     */
    public static class AgentAddressComparator implements Comparator<Agent> {
        public int compare(Agent o1, Agent o2) {
            return o1.getAgentJID().toBareJID().compareTo(o2.getAgentJID().toBareJID());
        }
    }
}
