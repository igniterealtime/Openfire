/**
 * $RCSfile$
 * $Revision: 32833 $
 * $Date: 2006-08-02 15:52:36 -0700 (Wed, 02 Aug 2006) $
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.xmpp.workgroup.interceptor.AgentInterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.InterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.PacketRejectedException;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * <p>The Workgroup's presence handler processes all incoming
 * presence packets sent to the workgroup.</p>
 * <p/>
 * <p>Currently the workgroup recognizes:</p>
 * <ul>
 * <li>Presence from Agents</li>
 * <ul>
 * <li>available - Join the workgoup and create agent session</li>
 * <li>unavailable - Depart the workgroup and delete agent session</li>
 * </ul>
 * </ul>
 *
 * @author Derek DeMoro
 */
public class WorkgroupPresence {

    private static final FastDateFormat UTC_FORMAT = FastDateFormat
            .getInstance(JiveConstants.XMPP_DELAY_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));

    private static final String LOAD_ROSTER =
            "SELECT jid FROM fpWorkgroupRoster WHERE workgroupID=?";
    private static final String CREATE_ROSTER_ITEM =
            "INSERT INTO fpWorkgroupRoster (workgroupID, jid) VALUES (?, ?)";
    private static final String DELETE_ROSTER_ITEM =
            "DELETE FROM fpWorkgroupRoster WHERE workgroupID=? AND jid=?";
    private static final String DELETE_ROSTER_ITEMS =
            "DELETE FROM fpWorkgroupRoster WHERE workgroupID=?";

    private Workgroup workgroup;
    /**
     * Holds the JID of users that sent an available presence to the workgroup as a way to be
     * notified when the workgroup changes his presence status. This is like a temporary way
     * to susbcribe to the workgroup presence. The temporary subscription will be removed
     * when an unavailable presence is sent to the workgroup.
     */
    private final Set<JID> listeners = new ConcurrentHashSet<JID>();

    /**
     * Holds the bare JID address of all the users that subscribed to the presence of the
     * workgroup. Users may want to track the presence of the workgroup to find out when agents
     * are available. Users that send a presence subscribe packet will automatically get their
     * requests accepted.
     */
    private List<String> presenceSubscribers = new CopyOnWriteArrayList<String>();

    public WorkgroupPresence(Workgroup workgroup) {
        this.workgroup = workgroup;
        // Load the bare JIDs of the users that are interested in the workgroup presence
        loadRosterItems();
    }

    public void process(Presence packet) {
        try {
            JID sender = packet.getFrom();

            // Handling Subscription
            if (Presence.Type.subscribe == packet.getType()) {
                // User wants to track the workgroup presence so accept the request
                // Add sender to the workgroup roster
                // Only create item if user was not already subscribed to workgroup's presence
                if (!presenceSubscribers.contains(sender.toBareJID())) {
                    createRosterItem(sender);
                }
                // Reply that the subscription request was approved
                Presence reply = new Presence();
                reply.setTo(sender);
                reply.setFrom(workgroup.getJID());
                reply.setType(Presence.Type.subscribed);
                workgroup.send(reply);
                // Send the presence of the workgroup to the user that requested it
                sendPresence(packet.getFrom());
            }
            else if (Presence.Type.unsubscribe == packet.getType()) {
                // Remove sender from the workgroup roster
                deleteRosterItem(sender);
                // Send confirmation of unsubscription
                Presence reply = new Presence();
                reply.setTo(sender);
                reply.setFrom(workgroup.getJID());
                reply.setType(Presence.Type.unsubscribed);
                workgroup.send(reply);
                // Send unavailable presence of the workgroup
                reply = new Presence();
                reply.setTo(sender);
                reply.setFrom(workgroup.getJID());
                reply.setType(Presence.Type.unavailable);
                workgroup.send(reply);
            }
            else if (Presence.Type.subscribed == packet.getType()) {
                // ignore
            }
            else if (Presence.Type.unsubscribed == packet.getType()) {
                // ignore
            }
            else if (Presence.Type.probe == packet.getType()) {
                // Send the presence of the workgroup to the user that requested it
                sendPresence(packet.getFrom());
            }
            else {
                try {
                    agentToWorkgroup(packet);
                }
                catch (AgentNotFoundException e) {
                    Presence reply = new Presence();
                    reply.setError(new PacketError(PacketError.Condition.not_authorized));
                    reply.setTo(packet.getFrom());
                    reply.setFrom(workgroup.getJID());
                    workgroup.send(reply);

                    StringBuilder errorMessage = new StringBuilder();
                    errorMessage.append("Sender: ");
                    errorMessage.append(packet.getFrom().toString());
                    errorMessage.append(" Workgroup: ");
                    errorMessage.append(workgroup.getJID().toString());
                    Log.debug(errorMessage.toString(), e);
                }
            }

        }
        catch (Exception e) {
            Log.error(e);
            Presence reply = new Presence();
            reply.setError(new PacketError(PacketError.Condition.internal_server_error));
            reply.setTo(packet.getFrom());
            reply.setFrom(workgroup.getJID());
            workgroup.send(reply);
        }
    }

    /**
     * Sends the presence of the workgroup to the specified JID address.
     *
     * @param address the XMPP address that will receive the presence of the workgroup.
     */
    public void sendPresence(JID address) {
        Presence presence = new Presence();
        presence.setTo(address);
        presence.setFrom(workgroup.getJID());

        Presence.Type type;
        if (workgroup.isAvailable()) {
            type = null;
            // Add the a child element that will contain information about the workgroup
            Element child = presence.addChildElement("workgroup",
                    "http://jivesoftware.com/protocol/workgroup");
            // Add the last modification date of the workgroup
            child.addElement("lastModified").setText(UTC_FORMAT.format(workgroup.getModificationDate()));
        }
        else {
            type = Presence.Type.unavailable;
            // Add the a child element that will contain information about the workgroup
            Element child = presence.addChildElement("workgroup",
                    "http://jivesoftware.com/protocol/workgroup");
            // Add the last modification date of the workgroup
            child.addElement("lastModified").setText(UTC_FORMAT.format(workgroup.getModificationDate()));
        }
        presence.setType(type);
        workgroup.send(presence);
    }

    /**
     * Send the workgroup presence to the users that subscribed to the workgroup's presence.
     */
    void broadcastWorkgroupPresence() {
        TaskEngine.getInstance().submit(new Runnable() {
            public void run() {
                try {
                    // Send the workgroup presence to the workgroup subscribers
                    for (String bareJID : presenceSubscribers) {
                        sendPresence(new JID(bareJID));
                    }
                    // Send the workgroup presence to the users that temporary subscribed
                    // to the workgroup
                    for (JID tempSubscriber : listeners) {
                        sendPresence(tempSubscriber);
                    }
                }
                catch (Exception e) {
                    Log.error(
                            "Error broadcasting available presence", e);
                }
            }
        });
    }

    /**
     * The workgroup is being destroyed so remove all the accepted presence subscriptions.
     */
    void workgroupDestroyed() {
        TaskEngine.getInstance().submit(new Runnable() {
            public void run() {
                try {
                    for (String bareJID : presenceSubscribers) {
                        // Cancel the previously granted subscription request
                        Presence reply = new Presence();
                        reply.setTo(bareJID);
                        reply.setFrom(workgroup.getJID());
                        reply.setType(Presence.Type.unsubscribed);
                        workgroup.send(reply);
                    }
                    // Delete the subscribers from the database
                    deleteRosterItems();
                }
                catch (Exception e) {
                    Log.error(
                            "Error broadcasting available presence", e);
                }
            }
        });
    }

    private void agentToWorkgroup(Presence packet) throws AgentNotFoundException {
        String workgroupNode = workgroup.getJID().getNode();
        String resource = packet.getFrom().getResource();
        boolean usesAgentResource = workgroupNode.equalsIgnoreCase(resource);

        final JID sender = packet.getFrom();
        Agent agent = null;
        // Check if the sender of the Presence is an Agent otherwise return an error
        try {
            agent = workgroup.getAgentManager().getAgent(sender);
        }
        catch (AgentNotFoundException notFound) {
            if (usesAgentResource) {
                throw notFound;
            }
        }

        // Check if the presence includes the Workgroup extension
        boolean includesExtension = packet
                .getChildElement("agent-status", "http://jabber.org/protocol/workgroup") != null;
        AgentManager agentManager = workgroup.getAgentManager();


        if (agent != null && !agentManager.isInWorkgroup(agent, workgroup) && includesExtension) {
            throw new AgentNotFoundException();
        }

        if (agent == null || !agentManager.isInWorkgroup(agent, workgroup)) {
            if (packet.isAvailable()) {
                if (!sender.equals(workgroup.getJID())) {
                    // Add the user to the list of temporary subscribers. Since the
                    // user sent this presence using a directed presence the server
                    // will send an unavailable presence if the user gets disconnected.
                    // When an unavailable presence is received the subscription is
                    // going to be removed.
                    listeners.add(packet.getFrom());
                    sendPresence(packet.getFrom());
                }
            }
            else {
                listeners.remove(packet.getFrom());
            }
            return;
        }

        InterceptorManager interceptorManager = AgentInterceptorManager.getInstance();
        try {
            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet, true, false);
            // Try to update the presence of the AgentSession with the new presence
            AgentSession agentSession = agent.getAgentSession();
            if (agentSession == null) {
                if (!includesExtension) {
                    // Ignote presence packet of agents that have not joined the workgroup
                    // and does not contain the proper extension
                    return;
                }

                // Add new agent session.
                agentSession = agent.createSession(packet.getFrom());
                if (agentSession == null) {
                    // User is not able to join since an existing session from another resource already exists
                    Presence reply = new Presence();
                    reply.setID(packet.getID());
                    reply.setTo(packet.getFrom());
                    reply.setFrom(packet.getTo());
                    reply.setError(PacketError.Condition.not_allowed);
                    workgroup.send(reply);
                    return;
                }
            }

            // Update session's presence with latest presence
            agentSession.updatePresence(packet);
            if (agentSession.getPresence().getType() == null) {
                agentSession.join(workgroup);
            }
            else {
                agentSession.depart(workgroup);
            }

            interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet, true, true);
        }
        catch (PacketRejectedException e) {
            workgroup.rejectPacket(packet, e);
        }
    }

    private void loadRosterItems() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROSTER);
            pstmt.setLong(1, workgroup.getID());
            rs = pstmt.executeQuery();
            List<String> jids = new ArrayList<String>();
            while (rs.next()) {
                jids.add(rs.getString(1));
            }
            presenceSubscribers.addAll(jids);
        }
        catch (SQLException e) {
            Log.error(
                    "Error loading workgroup roster items ", e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void createRosterItem(JID sender) throws SQLException {
        String bareJID = sender.toBareJID();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CREATE_ROSTER_ITEM);
            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, bareJID);
            pstmt.executeUpdate();
            // Add the bareJID of the user to the list of users that are tracking the
            // workgroup's presence
            presenceSubscribers.add(bareJID);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void deleteRosterItem(JID sender) throws SQLException {
        String bareJID = sender.toBareJID();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM);
            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, bareJID);
            pstmt.executeUpdate();
            // Remove the bareJID of the user from the list of users that are tracking the
            // workgroup's presence
            presenceSubscribers.remove(bareJID);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void deleteRosterItems() throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEMS);
            pstmt.setLong(1, workgroup.getID());
            pstmt.executeUpdate();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private int getNumberOfAgentsOnline() {
        int onlineAgents = 0;
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        for (Workgroup workgroup : workgroupManager.getWorkgroups()) {
            onlineAgents += workgroup.getAgentSessions().size();
        }
        return onlineAgents;
    }
}