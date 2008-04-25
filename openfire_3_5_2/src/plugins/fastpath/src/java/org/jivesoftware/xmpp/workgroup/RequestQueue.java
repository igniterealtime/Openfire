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

import org.jivesoftware.xmpp.workgroup.dispatcher.Dispatcher;
import org.jivesoftware.xmpp.workgroup.dispatcher.RoundRobinDispatcher;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.spi.JiveLiveProperties;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maintains a queue of requests waiting to be routed to agents. The workgroup
 * adds and removes requests according to the protocol and the routing engine
 * will process requests in the queue.
 *
 * @author Derek DeMoro
 */
public class RequestQueue {

    private static final String LOAD_QUEUE =
            "SELECT name, description, priority, maxchats, minchats, overflow, backupQueue FROM " +
            "fpQueue WHERE queueID=?";
    private static final String UPDATE_QUEUE =
            "UPDATE fpQueue SET name=?, description=?, priority=?, maxchats=?, minchats=?, " +
            "overflow=?, backupQueue=? WHERE queueID=?";
    private static final String DELETE_QUEUE =
            "DELETE FROM fpQueueAgent WHERE objectType=? AND objectID=? AND queueID=?";
    private static final String LOAD_AGENTS =
            "SELECT objectID, administrator FROM fpQueueAgent WHERE queueID=? AND objectType=?";
    private static final String ADD_QUEUE_AGENT =
            "INSERT INTO fpQueueAgent (objectType, objectID, queueID, administrator) " +
            "VALUES (?,?,?,0)";
    private static final String LOAD_QUEUE_GROUPS =
            "SELECT groupName FROM fpQueueGroup WHERE queueID=?";
    private static final String ADD_QUEUE_GROUP =
            "INSERT INTO fpQueueGroup (queueID, groupName) VALUES (?,?)";
    private static final String DELETE_QUEUE_GROUP =
            "DELETE FROM fpQueueGroup WHERE queueID=? AND groupName=?";

    /**
     * A map of all requests in the workgroup keyed by the request's JID.
     * Lets the server route request packets to the correct request.
     */
    private LinkedList<UserRequest> requests = new LinkedList<UserRequest>();

    /**
     * A listof active agent sessions that belong to this queue.
     */
    private AgentSessionList activeAgents = new AgentSessionList();

    /**
     * The current average time a request spends in this queue.
     */
    private int averageTime;

    /**
     * The workgroup this queue belongs to.
     */
    private Workgroup workgroup;

    /**
     * The default floor for maximum number of chats that an agent
     * should have in this group.
     */
    private int minChats;
    /**
     * The default ceiling for maximum number of chats that an agent
     * should have in this group.
     */
    private int maxChats;
    /**
     * The name of the queue (and the queue's resource identifier).
     */
    private String name;
    /**
     * The description of the queue for the admin UI.
     */
    private String description;
    /**
     * The routing priority of this request queue (not currently used).
     */
    private int priority;

    /**
     * The agent groups that belong to this request queue.
     */
    private Set<String> groups = new CopyOnWriteArraySet<String>();

    /**
     * The agents that belong directly to this request queue.
     */
    private Set<Agent> agents = new CopyOnWriteArraySet<Agent>();

    /**
     * The workgoup entity properties for the queue.
     */
    private JiveLiveProperties properties;

    /**
     * Dispatcher for the queue.
     */
    private RoundRobinDispatcher dispatcher;

    /**
     * The overflow type of this queue.
     */
    private OverflowType overflowType;

    /**
     * The backup queue if overflow is OVERFLOW_BACKUP.
     */
    private long backupQueueID = 0;

    /**
     * Indicates if the queue's presence should be unavailable.
     */
    private boolean presenceAvailable = true;

    /**
     * The total number of accepted requests *
     */
    private int totalChatCount;

    /**
     * The total number of requests *
     */
    private int totalRequestCount;

    /**
     * The total number of dropped requests *
     */
    private int totalDroppedRequests;

    /**
     * The creation Date *
     */
    private Date creationDate;

    /**
     * The queue id
     */
    private long id;

    /**
     * The RequestQueue XMPPAdderess
     */
    private JID address;


    private AgentManager agentManager;

    /**
     * Creates a request queue for the given workgroup given a
     * certain request queue ID.
     *
     * @param workgroup the workgroup this queue belongs to.
     * @param id  the queueID of the queue.
     */
    public RequestQueue(Workgroup workgroup, long id) {
        this.id = id;
        this.workgroup = workgroup;

        // Load up the Queue
        loadQueue();

        // Load all Groups
        loadGroups();

        // Load all Agents
        loadAgents();

        dispatcher = new RoundRobinDispatcher(this);
        creationDate = new Date();
        agentManager = workgroup.getAgentManager();
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public Workgroup getWorkgroup() {
        return workgroup;
    }

    // ############################################################################
    // The request queue
    // ############################################################################

    public int getRequestCount() {
        return requests.size();
    }

    public Collection<UserRequest> getRequests() {
        return new ArrayList<UserRequest>(requests);
    }

    public UserRequest getRequest(JID requestAddress) {
        UserRequest returnRequest = null;

        for (UserRequest request : getRequests()) {
            if (requestAddress.equals(request.getUserJID())) {
                returnRequest = request;
                break;
            }
        }

        return returnRequest;
    }

    public void clearQueue() {
        for (Request request : getRequests()) {
            request.cancel(Request.CancelType.AGENT_NOT_FOUND);
        }
        requests.clear();
    }

    public void removeRequest(UserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException();
        }
        totalRequestCount++;
        if (request.getOffer() == null || !request.getOffer().isCancelled()) {
            int waitTime = (int)(System.currentTimeMillis()
                    - request.getCreationTime().getTime()) / 1000;
            if (averageTime == 0) {
                averageTime = waitTime;
            }
            averageTime = (averageTime + waitTime) / 2;
            totalChatCount++;
        }
        else {
            final int timeout = (int)request.getOffer().getTimeout() / 1000;
            int waitTime = (int)(System.currentTimeMillis()
                    - request.getCreationTime().getTime()) / 1000;

            if (waitTime > timeout) {
                // This was never left and timed-out
                totalDroppedRequests++;
            }
        }

        int index = requests.indexOf(request);
        requests.remove(request);
        if (requests.size() > 0 && index < requests.size()) {
            sendRequestStatus(getRequests());
        }
        activeAgents.broadcastQueueStatus(this);
        request.setRequestQueue(null);
    }

    public void addRequest(UserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException();
        }

        request.setRequestQueue(this);
        requests.add(request);
        activeAgents.broadcastQueueStatus(this);
        request.updateQueueStatus(false);
    }


    public UserRequest getFirst() {
        return requests.getFirst();
    }

    public int getPosition(UserRequest request) {
        return requests.indexOf(request);
    }

    public void sendStatus(JID recipient) {
        try {
            Presence queueStatus = getStatusPresence();
            queueStatus.setTo(recipient);
            workgroup.send(queueStatus);
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
    }

    public void sendDetailedStatus(JID recipient) {
        try {
            // queue details
            Presence queueStatus = getDetailedStatusPresence();
            queueStatus.setTo(recipient);
            workgroup.send(queueStatus);
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
    }

    public Presence getStatusPresence() {
        Presence queueStatus = new Presence();
        queueStatus.setFrom(address);

        // Add Notify Queue
        Element status = queueStatus.addChildElement("notify-queue", "http://jabber.org/protocol/workgroup");

        Element countElement = status.addElement("count");
        countElement.setText(Integer.toString(getRequestCount()));
        if (getRequestCount() > 0) {
            Element oldestElement = status.addElement("oldest");
            oldestElement.setText(UTC_FORMAT.format(getFirst().getCreationTime()));
        }

        Element timeElement = status.addElement("time");
        timeElement.setText(Integer.toString(getAverageTime()));
        Element statusElement = status.addElement("status");

        if (workgroup.getStatus() == Workgroup.Status.OPEN && presenceAvailable) {
            statusElement.setText("open");
        }
        else {
            queueStatus.setType(Presence.Type.unavailable);
            // TODO: actually active should be a full-blown workgroup state since queues
            // may be empty but still active
            if (getRequestCount() > 0) {
                statusElement.setText("active");
            }
            else {
                if (workgroup.getStatus() == Workgroup.Status.READY) {
                    statusElement.setText("ready");
                }
                else {
                    statusElement.setText("closed");
                }
            }
        }
        return queueStatus;
    }

    public Presence getDetailedStatusPresence() {
        Presence queueStatus = new Presence();
        queueStatus.setFrom(address);
        if (workgroup.getStatus() == Workgroup.Status.OPEN && presenceAvailable) {
            queueStatus.setType(null);
        }
        else {
            queueStatus.setType(Presence.Type.unavailable);
        }

        Element details = queueStatus.addChildElement("notify-queue-details", "http://jabber.org/protocol/workgroup");
        int i = 0;
        for (UserRequest request : getRequests()) {
            Element user = details.addElement("user", "http://jabber.org/protocol/workgroup");
            try {
                user.addAttribute("jid", request.getUserJID().toString());

                // Add Sub-Elements
                Element position = user.addElement("position");
                position.setText(Integer.toString(i));

                Element time = user.addElement("time");
                time.setText(Integer.toString(request.getTimeStatus()));

                Element joinTime = user.addElement("join-time");
                joinTime.setText(UTC_FORMAT.format(request.getCreationTime()));
                i++;
            }
            catch (Exception e) {
                // Since we are not locking the list of requests while doing this operation (for
                // performance reasons) it is possible that the request got accepted or cancelled
                // thus generating a NPE

                // Remove the request that generated the exception
                details.remove(user);
                // Log an error if the request still belongs to this queue
                if (this.equals(request.getRequestQueue())) {
                    ComponentManagerFactory.getComponentManager().getLog().error(e);
                }
            }
        }
        return queueStatus;
    }

    /**
     * Returns true if this queue is opened and may be eligible for receiving new requests. The
     * queue will be opened if there are agents connected to it.
     *
     * @return true if this queue is opened and may be eligible for receiving new requests.
     */
    public boolean isOpened() {
        return !activeAgents.isEmpty();
    }

    // #################################################################
    // Standard formatting according to locale and Jabber specs
    // #################################################################
    private static final FastDateFormat UTC_FORMAT = FastDateFormat.getInstance("yyyyMMdd'T'HH:mm:ss", TimeZone.getTimeZone("UTC"));

    static {
        //UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    // ###########################################################################
    // Misc queue runtime properties
    // ###########################################################################
    public AgentSessionList getAgentSessionList() {
        return activeAgents;
    }

    public int getAverageTime() {
        return averageTime;
    }

    private void sendRequestStatus(Collection<UserRequest> requests) {
        for (UserRequest request : requests) {
            request.updateQueueStatus(false);
        }
    }

    // ###########################################################################
    // Agent Groups
    // ###########################################################################
    public int getGroupCount() {
        return groups.size();
    }

    public Collection<Group> getGroups() {
        return Collections.unmodifiableCollection(getGroupObjects());
    }

    private Collection<Group> getGroupObjects() {
        final GroupManager groupManager = GroupManager.getInstance();
        Set<Group> objects = new HashSet<Group>(groups.size());
        for (String group : groups) {
            try {
                objects.add(groupManager.getGroup(group));
            } catch (GroupNotFoundException e) {
                ComponentManagerFactory.getComponentManager().getLog().error("Error retrieving group: " + group, e);
            }
        }
        return objects;
    }

    public boolean isMember(Agent agent) {
        if (agents.contains(agent)) {
            return true;
        }

        for (Group group : getGroupObjects()) {
            if (group.isUser(agent.getAgentJID())) {
                return true;
            }
        }
        return false;
    }


    public boolean hasGroup(Group group) {
        return groups.contains(group.getName());
    }

    public void addGroup(Group group) {
        if (!groups.contains(group.getName())) {
            boolean added = insertGroup(group.getName());
            if (added) {
                groups.add(group.getName());

                WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
                AgentManager agentManager = workgroupManager.getAgentManager();

                for (Agent agent : agentManager.getAgents(group)) {
                    agent.sendAgentAddedToAllAgents(this);
                }
            }
        }
    }

    public void removeGroup(Group group) {
        deleteGroup(group.getName());
        if (groups.remove(group.getName())) {
            for (Agent agent : agentManager.getAgents(group)) {
                agent.sendAgentRemovedToAllAgents(this);

                // Remove agent if necessary.
                agentManager.removeAgentIfNecessary(agent);
            }
        }
    }

    // ##########################################################################
    // Request Queue as agent group methods
    // ##########################################################################

    public int getMemberCount() {
        int count = getMembers().size();
        for (Group group : getGroups()) {
            count += group.getMembers().size();
        }
        return count;
    }

    /**
     * Adds an individual agent to the RequestQueue.
     * @param agent the agent to add.
     */
    public void addMember(Agent agent) {
        if (!agents.contains(agent)) {
            boolean added = addAgentToDb(agent.getID(), Boolean.FALSE);
            if (added) {
                agents.add(agent);
                // Ask the new agent to notify the other agents of the queue of the new addition
                agent.sendAgentAddedToAllAgents(this);
            }
        }
    }

    /**
     * Removes an agent from the RequestQueue.
     * @param agent the agent to remove.
     */
    public void removeMember(Agent agent) {
        deleteObject(agent.getID(), Boolean.FALSE);
        agents.remove(agent);

        // Remove agent if necessary
        agentManager.removeAgentIfNecessary(agent);

        // Ask the deleted agent to notify the other agents of the queue of the deletion
        agent.sendAgentRemovedToAllAgents(this);
    }

    /**
     * Returns members belong to this RequestQueue. Note, members does not include
     * users belonging to Groups.
     *
     * @return a collection of queue members.
     */
    public Collection<Agent> getMembers() {
        final Set<Agent> agentList = new HashSet<Agent>(agents);


        return Collections.unmodifiableCollection(agentList);
    }


    // #########################################################################
    // Persistent accessor methods calls
    // #########################################################################
    public String getName() {
        return name;
    }

    public void setName(String newName) {

        // Handle empty string.
        if (!ModelUtil.hasLength(newName)) {
            return;
        }

        presenceAvailable = false;
        try {
            activeAgents.broadcastQueueStatus(this);
        }
        finally {
            presenceAvailable = true;
        }

        this.name = newName;

        JID workgroupJID = workgroup.getJID();
        address = new JID(workgroupJID.getNode(), workgroupJID.getDomain(), this.name);
        updateQueue();
        activeAgents.broadcastQueueStatus(this);
    }

    public void setDescription(String description) {
        this.description = description;
        updateQueue();
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {

    }

    public Date getModificationDate() {
        return null;
    }

    public void setModificationDate(Date modificationDate) {

    }

    public String getDescription() {
        return description;
    }

    public DbProperties getProperties() {
        if (properties == null) {
            properties = new JiveLiveProperties("jlaQueueProp", id);
        }
        return properties;
    }

    public RequestQueue.OverflowType getOverflowType() {
        return overflowType;
    }

    public void setOverflowType(RequestQueue.OverflowType type) {
        if (type != null) {
            overflowType = type;
            updateQueue();
        }
    }

    public RequestQueue getBackupQueue() {
        RequestQueue queue = null;
        if (backupQueueID > 0) {
            try {
                queue = workgroup.getRequestQueue(backupQueueID);
            }
            catch (NotFoundException e) {
                ComponentManagerFactory.getComponentManager().getLog().error(
                        "Backup queue with ID " + backupQueueID + " not found", e);
                queue = null;
            }
        }
        return queue;
    }

    public void setBackupQueue(RequestQueue queue) {
        backupQueueID = queue.getID();
        updateQueue();
    }

    private static final int AGENT_TYPE = 0;
    private static final int GROUP_TYPE = 1;

    private void loadQueue() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_QUEUE);
            pstmt.setLong(1, id);
            rs = pstmt.executeQuery();
            rs.next();
            name = rs.getString(1);
            address = new JID(workgroup.getJID().getNode(), workgroup.getJID().getDomain(), name);
            description = rs.getString(2);
            priority = rs.getInt(3);
            maxChats = rs.getInt(4);
            minChats = rs.getInt(5);
            switch (rs.getInt(6)) {
                case 1:
                    overflowType = OverflowType.OVERFLOW_RANDOM;
                    break;
                case 2:
                    overflowType = OverflowType.OVERFLOW_BACKUP;
                    break;
                default:
                    overflowType = OverflowType.OVERFLOW_NONE;
            }
            backupQueueID = rs.getLong(7);

        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void updateQueue() {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_QUEUE);
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setInt(3, priority);
            pstmt.setInt(4, maxChats);
            pstmt.setInt(5, minChats);
            pstmt.setInt(6, overflowType.ordinal());
            pstmt.setLong(7, backupQueueID);
            pstmt.setLong(8, id);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private boolean deleteObject(long objectID, Object data) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_QUEUE);
            if ((Boolean)data) {
                pstmt.setInt(1, GROUP_TYPE);
            }
            else {
                pstmt.setInt(1, AGENT_TYPE);
            }
            pstmt.setLong(2, objectID);
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private boolean addAgentToDb(long objectID, Object data) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_QUEUE_AGENT);
            if ((Boolean)data) {
                pstmt.setInt(1, GROUP_TYPE);
            }
            else {
                pstmt.setInt(1, AGENT_TYPE);
            }
            pstmt.setLong(2, objectID);
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private boolean insertGroup(String groupName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_QUEUE_GROUP);
            pstmt.setLong(1, id);
            pstmt.setString(2, groupName);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private boolean deleteGroup(String groupName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_QUEUE_GROUP);
            pstmt.setLong(1, id);
            pstmt.setString(2, groupName);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }


    private void loadGroups() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_QUEUE_GROUPS);
            pstmt.setLong(1, id);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                groups.add(rs.getString(1));
            }
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void loadAgents() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_AGENTS);
            pstmt.setLong(1, id);
            pstmt.setInt(2, AGENT_TYPE);
            rs = pstmt.executeQuery();

            AgentManager agentManager = workgroup.getAgentManager();
            while (rs.next()) {
                try {
                    Agent agent = agentManager.getAgent(rs.getLong(1));
                    agents.add(agent);
                }
                catch (AgentNotFoundException e) {
                    ComponentManagerFactory.getComponentManager().getLog().error(e);
                }
            }
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    // Stats Implementation

    public int getTotalChatCount() {
        return totalChatCount;
    }

    public int getTotalRequestCount() {
        return totalRequestCount;
    }

    public int getDroppedRequestCount() {
        return totalDroppedRequests;
    }

    public JID getAddress() {
        if (address == null) {
            throw new IllegalStateException();
        }
        return address;
    }

    public long getID() {
        return id;
    }

    public String getUsername() {
        return address.getNode().toLowerCase();
    }

    public void shutdown() {
        dispatcher.shutdown();
    }

    /**
     * Defines the overflow types available for queues.
     *
     * @author Iain Shigeoka
     */
    public static enum OverflowType {
        /**
         * Requests are not overflowed to other queues.
         */
        OVERFLOW_NONE,

        /**
         * Requests that aren't handled are overflowed to a random available queue.
         */
        OVERFLOW_RANDOM,

        /**
         * Requests that aren't handled are overflowed to the queue's backup queue.
         */
        OVERFLOW_BACKUP
    }
}