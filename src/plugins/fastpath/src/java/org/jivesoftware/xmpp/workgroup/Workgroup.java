/**
 * $RCSfile$
 * $Revision$
 * $Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

import java.net.URL;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.chatbot.Chatbot;
import org.jivesoftware.xmpp.workgroup.dispatcher.BasicDispatcherInfo;
import org.jivesoftware.xmpp.workgroup.dispatcher.DispatcherInfoProvider;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventDispatcher;
import org.jivesoftware.xmpp.workgroup.interceptor.InterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.PacketRejectedException;
import org.jivesoftware.xmpp.workgroup.interceptor.RoomInterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.WorkgroupInterceptorManager;
import org.jivesoftware.xmpp.workgroup.request.InvitationRequest;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.TransferRequest;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.routing.RoutingManager;
import org.jivesoftware.xmpp.workgroup.spi.BasicRequestFilterFactory;
import org.jivesoftware.xmpp.workgroup.spi.JiveLiveProperties;
import org.jivesoftware.xmpp.workgroup.spi.dispatcher.DbDispatcherInfoProvider;
import org.jivesoftware.xmpp.workgroup.utils.DbWorkgroup;
import org.jivesoftware.xmpp.workgroup.utils.FastpathConstants;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.muc.DestroyRoom;
import org.xmpp.muc.Invitation;
import org.xmpp.muc.JoinRoom;
import org.xmpp.muc.LeaveRoom;
import org.xmpp.muc.RoomConfiguration;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * <p>Database implementation of a workgroup agent.</p>
 * <p>The current implementation doesn't store any meta data about an agent.</p>
 *
 * @author Derek DeMoro
 */
public class Workgroup {

    private static final String LOAD_WORKGROUP =
            "SELECT jid, displayName, description, status, modes, creationDate, " +
            "modificationDate, maxchats, minchats, offerTimeout, requestTimeout, " +
            "schedule FROM fpWorkgroup WHERE workgroupID=?";
    private static final String UPDATE_WORKGRUP =
            "UPDATE fpWorkgroup SET displayName=?,description=?,status=?,modes=?," +
            "creationDate=?,modificationDate=?,maxchats=?,minchats=?,offerTimeout=?," +
            "requestTimeout=?,schedule=? WHERE workgroupID=?";
    private static final String LOAD_QUEUES =
            "SELECT queueID FROM fpQueue WHERE workgroupID=?";
    private static final String CREATE_QUEUE =
            "INSERT into fpQueue (queueID,workgroupID,name,priority,maxchats,minchats," +
            "overflow,backupQueue) VALUES (?,?,?,?,?,?,0,0)";
    private static final String DELETE_QUEUE =
            "DELETE FROM fpQueue WHERE queueID=?";
    private static final String DELETE_QUEUE_PROPS =
            "DELETE FROM fpDispatcherProp WHERE ownerID=?";

    private static final FastDateFormat UTC_FORMAT = FastDateFormat
        .getInstance("yyyyMMdd'T'HH:mm:ss", TimeZone.getTimeZone("GMT+0"));

    /**
     * Flag indicating if the workgroup should accept new requests.
     */
    private boolean open;

    /**
     * Flag indicating if the workgroup is following its schedule.
     */
    private boolean followSchedule;

    /**
     * The schedule the workgroup follows when followSchedule is true.
     */
    private Schedule schedule;

    /**
     * The description for the workgroup (used in the admin UI only - lazily loaded).
     */
    private String description = null;
    private Date creationDate;
    private Date modDate;
    private long offerTimeout = -1;
    private long requestTimeout = -1;
    private int maxChats;
    private int minChats;

    /**
     * Convenience copy of the WorkgroupManager's agent manager (similar to deliverer convenience).
     */
    private AgentManager agentManager;

    /**
     * Used when creating queues.
     */
    private DispatcherInfoProvider dispatcherInfoProvider = new DbDispatcherInfoProvider();

    /**
     * Factory to produce request filters.
     */
    private RequestFilterFactory requestFilterFactory = new BasicRequestFilterFactory();

    /**
     * Chatbot that will handle all the messages sent to this workgroup. If no chatbot was
     * defined then messages will not be answered.
     */
    private Chatbot chatbot;
    // ------------------------------------------------------------------------
    // Packet handlers
    // ------------------------------------------------------------------------
    private WorkgroupPresence workgroupPresenceHandler;
    private WorkgroupIQHandler workgroupIqHandler;
    private MessageHandler messageHandler;


    private Map<Long, RequestQueue> queues = new HashMap<Long, RequestQueue>();

    /**
     * Custom properties for the workgroup.
     */
    private JiveLiveProperties properties;

    private String workgroupName;
    private String displayName;
    private long id;

    /**
     * Keep the conversation transcript of each room that this workgroup has created. Rooms are
     * destroyed after the support session is over. Therefore, this variable keeps track of the
     * chat transcripts of the current support sessions.
     */
    private Map<String, Map<Packet, java.util.Date>> transcripts =
            new ConcurrentHashMap<String, Map<Packet, java.util.Date>>();

    /**
     * Keep a counter of occupants for each room that the workgroup created. The
     * occupants will be updated every time a presence packet is received from the
     * room. When all the occupants (except the workgroup) has left the room then the
     * workgroup will leave the room thus destroying it.
     */
    private Map<String, Set<String>> occupantsCounter = new ConcurrentHashMap<String, Set<String>>();

    /**
     * Keep a list of the requests for which a room was created and still hasn't been destroyed.
     * A new entry is added when sending room invitations and the same entry will be removed when
     * the room is destroyed.
     * Key: sessionID (ie. roomID), value: Request
     */
    private Map<String, UserRequest> requests = new ConcurrentHashMap<String, UserRequest>();

    public Workgroup(long id, AgentManager agentManager) {
        this.id = id;
        this.agentManager = agentManager;
        // Initialize standalone runtime fields

        // Initialize runtime fields that only save reference to the workgroup
        workgroupPresenceHandler = new WorkgroupPresence(this);

        workgroupIqHandler = new WorkgroupIQHandler();
        workgroupIqHandler.setWorkgroup(this);
        messageHandler = new MessageHandler(this);

        // Load settings from database
        loadWorkgroup();
        loadQueues();

        // Send presence to let everyone know you're available/unavailable
        broadcastPresence();
        broadcastQueuesStatus();
    }

    /**
     * Broadcasts the presence of the workgroup to all users and agents of the workgroup.
     */
    public void broadcastPresence() {
        // TODO Send presence from the workgroup to the server

        TaskEngine.getInstance().submit(new Runnable() {
            public void run() {
                try {
                    // Send the status of this workgroup to all the connected agents
                    Collection<AgentSession> sessions = getAgentSessions();
                    for (AgentSession session : sessions) {
                        workgroupPresenceHandler.sendPresence(session.getJID());
                    }

                    // Get all the users' JID in a Set since a user may be having a chat with
                    // many agents
                    Set<JID> jids = new HashSet<JID>();
                    for (AgentSession session : sessions) {
                        jids.addAll(session.getUsersJID(Workgroup.this));
                    }
                    // Send the status to all the users that are having a chat with agents of
                    // the workgroup
                    for (JID jid : jids) {
                        workgroupPresenceHandler.sendPresence(jid);
                    }
                }
                catch (Exception e) {
                    Log.error("Error broadcasting workgroup presence", e);
                }
            }
        });
    }

    /**
     * Broadcasts the presence of all the queues in the workgroup to all agents.
     */
    public void broadcastQueuesStatus() {
        TaskEngine.getInstance().submit(new Runnable() {
            public void run() {
                try {
                    // Send status of each queue to all the connected agents
                    for (RequestQueue requestQueue : getRequestQueues()) {
                        requestQueue.getAgentSessionList().broadcastQueueStatus(requestQueue);
                    }
                }
                catch (Exception e) {
                    Log.error("Error broadcasting status of queues", e);
                }
            }
        });
    }

    /**
     * Return true if the Workgroup is available to take requests.
     *
     * @return true if the workgroup is available to take requests.
     */
    public boolean isAvailable() {
        for (RequestQueue requestQueue : getRequestQueues()) {
            Presence presence = requestQueue.getDetailedStatusPresence();
            if (presence.getType() == null) {
                return true;
            }
        }
        return false;
    }


    // ###############################################################################
    // Request queue management
    // ###############################################################################
    public RequestQueue createRequestQueue(String name) throws UnauthorizedException {
        RequestQueue queue = null;
        long queueID = SequenceManager.nextID(FastpathConstants.WORKGROUP_QUEUE);
        // This should probably be moved into a queue manager class
        // First create the queue, then the dispatcher,
        // then the queue implementation object
        boolean queueCreated = createQueue(queueID, name);
        if (queueCreated) {
            BasicDispatcherInfo info = new BasicDispatcherInfo(this, queueID,
                "Round Robin Dispatcher", "None", -1, -1);
            try {
                dispatcherInfoProvider.insertDispatcherInfo(queueID, info);
                queue = new RequestQueue(this, queueID);
            }
            catch (UserAlreadyExistsException e) {
                Log.error(e);
            }
        }
        else {
            throw new UnauthorizedException();
        }
        queues.put(queueID, queue);
        return queue;
    }

    public void deleteRequestQueue(RequestQueue queue) {
        queues.remove(queue.getID());
        // Delete the RequestQueue from the database
        if (deleteQueue(queue.getID())) {
            // Stop processing requests in this queue
            queue.shutdown();
            // Remove the agents from this queue
            for (Agent agent : queue.getMembers()) {
                queue.removeMember(agent);
            }
            // Remove the agent groups from this queue
            for (Group group : queue.getGroups()) {
                queue.removeGroup(group);
            }
            try {
                // Delete the dispatcher of this queue from the database
                dispatcherInfoProvider.deleteDispatcherInfo(queue.getID());
            }
            catch (UnauthorizedException e) {
                Log.error(e);
            }
        }
    }

    /**
     * Returns the Collection of RequestQueues ordered by ID.
     *
     * @return the collection of request queues.
     */
    public Collection<RequestQueue> getRequestQueues() {
        final List<RequestQueue> queueList = new ArrayList<RequestQueue>(queues.values());

        // Sort by ID
        Collections.sort(queueList, queueComparator);
        return Collections.unmodifiableList(queueList);
    }

    public int getRequestQueueCount() {
        return queues.size();
    }

    public RequestQueue getRequestQueue(long queueID) throws NotFoundException {
        RequestQueue requestQueue = queues.get(queueID);
        if (requestQueue == null) {
            throw new NotFoundException("Queue not found for ID: " + queueID);
        }
        return requestQueue;
    }

    public RequestQueue getRequestQueue(String queueName) throws NotFoundException {
        for (RequestQueue queue : queues.values()) {
            if (queueName.equals(queue.getName())) {
                return queue;
            }
        }
        throw new NotFoundException("Queue not found for name: " + queueName);
    }

    /**
     * Retuns the UserRequest that is associated to the support session being currently serviced or
     * <tt>null</tt> if none was found.
     *
     * @param sessionID the ID of the support session.
     * @return the UserRequest that is associated to the support session being currently serviced or
     *         null if none was found.
     */
    public UserRequest getUserRequest(String sessionID) {
        return requests.get(sessionID);
    }

    /**
     * <p>Loads up a request queue from the database given the queueID.</p>
     * <p>Used by the workgroup queue loading SQL code.</p>
     *
     * @param queueID The ID of the existing queue to load from the DB
     *                <p/>
     *                <!-- DbC -->
     */
    private void loadRequestQueue(long queueID) {
        queues.put(queueID, new RequestQueue(this, queueID));
    }

    /**
     * Adds the request to a queue in the workgroup. If the workgroup is closed or the request
     * does not pass a filter then the request will be rejected and this method will return false.
     *
     * @param request the request to add to a queue of this wokrgroup.
     * @return true if the request was added to a queue.
     */
    public boolean queueRequest(UserRequest request) {
        // Retrieve routing manager
        RoutingManager routingManager = RoutingManager.getInstance();

        // Check if they require referer validation.
        boolean contains = containsValidReferer(request);
        if (!contains) {
            return false;
        }

        if (getStatus() != Workgroup.Status.OPEN) {
            return false;
        }

        // Check if the request may be accepted by the workgroup
        PacketError.Condition error = requestFilterFactory.getFilter().filter(request);
        if (error == null) {
            synchronized (routingManager) {
                // Add the request to the best queue of the workgroup
                routingManager.routeRequest(this, request);
                return true;
            }
        }
        return false;
    }

    public void send(Packet packet) {
        InterceptorManager interceptorManager = WorkgroupInterceptorManager.getInstance();
        try {
            interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, false, false);
            WorkgroupManager.getInstance().send(packet);
            interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, false, true);
        }
        catch (PacketRejectedException e) {
            Log.warn("Packet was not sent " +
                "due to interceptor REJECTION: " + packet.toXML(), e);
        }
    }

    // ##############################################################################
    // Packet handler methods - We pass through to specific packet handling classes
    // ##############################################################################
    public void process(Presence packet) {
        workgroupPresenceHandler.process(packet);
    }

    public void process(IQ packet) {
        workgroupIqHandler.process(packet);
    }

    public void process(Message packet) {
        messageHandler.process(packet);
    }

    public void process(Packet packet) {
        InterceptorManager interceptorManager = WorkgroupInterceptorManager.getInstance();
        try {
            interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, true, false);

            String mucDomain = WorkgroupManager.getInstance().getMUCServiceName();
            if (mucDomain.equals(packet.getFrom().getDomain())) {
                roomActivity(packet);
            }
            else if (packet instanceof Message) {
                process((Message)packet);
            }
            else if (packet instanceof Presence) {
                process((Presence)packet);
            }
            else if (packet instanceof IQ) {
                process((IQ)packet);
            }
            interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, true, true);
        }
        catch (PacketRejectedException e) {
            rejectPacket(packet, e);
        }
    }

    public void rejectPacket(Packet packet, PacketRejectedException e) {
        if (packet instanceof IQ) {
            IQ reply = new IQ();
            reply.setChildElement(((IQ)packet).getChildElement().createCopy());
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_allowed);
            send(reply);
        }
        else if (packet instanceof Presence) {
            Presence reply = new Presence();
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_allowed);
            send(reply);
        }
        // Check if a message notifying the rejection should be sent
        if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
            // A message for the rejection will be sent to the sender of the rejected packet
            Message notification = new Message();
            notification.setTo(packet.getFrom());
            notification.setFrom(packet.getTo());
            notification.setBody(e.getRejectionMessage());
            send(notification);
        }
        Log.warn("Packet was REJECTED " +
            "by interceptor: " + packet.toXML(), e);
    }

    // ###############################################################################
    // MUC related packets
    // ###############################################################################

    /**
     * Notification message indicating that there has been new activity in a room. This implies
     * that we need to update the conversation transcript of the group chat room and possibly
     * update the number of occupants within the room.<p>
     * <p/>
     * If only the workgroup is present in the room then leave the room (i.e. destroying room) and
     * proceed to save the room conversation transcript to the database.<p>
     *
     * @param packet the packet that was sent to the group chat room.
     */
    private void roomActivity(Packet packet) {
        // Skip packet sent from this workgroup in the room
        if (packet.getFrom().toBareJID().equals(getGroupChatRoomName())) {
            return;
        }
        RoomInterceptorManager interceptorManager = RoomInterceptorManager.getInstance();
        String roomID = packet.getFrom().getNode();
        // Get the sessionID
        String sessionID = packet.getFrom().getNode();
        synchronized (sessionID.intern()) {
            if (packet instanceof Presence) {
                Presence presence = (Presence)packet;
                if (Presence.Type.error == presence.getType()) {
                    // A configuration must be wrong (eg. workgroup is not allowed to create rooms).
                    // Log the error presence
                    String warnMessage = "Possible server misconfiguration. Received error " +
                        "presence:" + presence.toXML();
                    Log.warn(warnMessage);
                    return;
                }
                // Get the JID of the presence's user
                Element mucUser = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                // Skip this presence if no extended info was included in the presence
                if (mucUser == null) {
                    return;
                }
                Element item = mucUser.element("item");
                // Skip this presence if no item was included in the presence
                if (item == null) {
                    return;
                }
                // Skip this presence if it's the presence of this workgroup in the room
                if (workgroupName.equals(packet.getFrom().getResource())) {
                    return;
                }
                JID presenceFullJID = new JID(item.attributeValue("jid"));
                String presenceJID = presenceFullJID.toBareJID();
                // Invoke the room interceptor before processing the presence
                interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, false, false);
                // Get the userID associated to this sessionID
                UserRequest initialRequest = requests.get(sessionID);
                // Add the new presence to the list of sent packets
                Map<Packet, java.util.Date> messageList = transcripts.get(roomID);
                if (messageList == null) {
                    messageList = new LinkedHashMap<Packet, java.util.Date>();
                    transcripts.put(roomID, messageList);
                    // Trigger the event that a chat support has started
                    WorkgroupEventDispatcher.chatSupportStarted(this, sessionID);
                }
                messageList.put(packet.createCopy(), new java.util.Date());

                // Update the number of occupants in the room.
                boolean occupantAdded = false;
                Set<String> set = occupantsCounter.get(roomID);
                if (set == null) {
                    set = new HashSet<String>();
                    occupantsCounter.put(roomID, set);
                }
                if (presence.isAvailable()) {
                    occupantAdded = set.add(presenceJID);
                }
                else {
                    String xpath = "/presence/*[name()='x']/*[name()='status']";
                    Element status = (Element)presence.getElement().selectSingleNode(xpath);
                    if (status == null || !"303".equals(status.attributeValue("code"))) {
                        // Remove the occupant unless the occupant is changing his nickname
                        set.remove(presenceJID);
                    }
                }
                // If the presence belongs to an Agent then create/update a track
                // Look for an agent whose JID matches the presence's JID
                String agentJID = null;
                for (Agent agent : getAgents()) {
                    if (agent.getAgentJID().toBareJID().equals(presenceJID)) {
                        agentJID = agent.getAgentJID().toBareJID();
                    }
                }
                if (agentJID != null) {
                    AgentSession agentSession;
                    // Update the current chats that the agent is having
                    try {
                        agentSession = agentManager.getAgentSession(presenceFullJID);
                        if (agentSession != null) {
                            if (presence.isAvailable()) {
                                if (occupantAdded) {
                                    agentSession.addChatInfo(this, sessionID, initialRequest, new java.util.Date());
                                    // Trigger the event that an agent has joined a chat session
                                    WorkgroupEventDispatcher.agentJoinedChatSupport(this, sessionID, agentSession);
                                }
                            }
                            else {
                                agentSession.removeChatInfo(this, sessionID);
                                // Trigger the event that an agent has left a chat session
                                WorkgroupEventDispatcher.agentLeftChatSupport(this, sessionID, agentSession);
                            }
                        }
                    }
                    catch (AgentNotFoundException e) {
                        // Do nothing since the AgentSession was not found
                    }
                    if (presence.isAvailable()) {
                        if (occupantAdded) {
                            // Store in the DB that an agent has joined a room
                            DbWorkgroup.updateJoinedSession(sessionID, agentJID, true);
                        }
                    }
                    else {
                        // Store in the DB that an agent has left a room
                        DbWorkgroup.updateJoinedSession(sessionID, agentJID, false);
                    }
                }
                else {
                    if (occupantAdded) {
                        // Notify the request that the user has joined a support session
                        initialRequest.supportStarted(roomID);
                    }
                }
                if (occupantAdded) {
                    initialRequest.userJoinedRoom(new JID(packet.getFrom().toBareJID()), presenceFullJID);
                }

                // If just the user has left the room, just persist the transcript
                boolean isAgent = false;
                try {
                    isAgent = agentManager.getAgentSession(presenceFullJID) != null;
                }
                catch (AgentNotFoundException e) {
                    // Ignore.
                }

                if (!((Presence)packet).isAvailable() && !isAgent) {
                    // Build the XML for the transcript
                    Map<Packet, java.util.Date> map = transcripts.get(roomID);
                    StringBuilder buf = new StringBuilder();
                    buf.append("<transcript>");
                    for (Packet p : map.keySet()) {
                        java.util.Date date = map.get(p);
                        // Add the delay information
                        if (p instanceof Message) {
                            Message storedMessage = (Message)p;
                            Element delay = storedMessage.addChildElement("x", "jabber:x:delay");
                            delay.addAttribute("stamp", UTC_FORMAT.format(date));
                            if (ModelUtil.hasLength(storedMessage.getBody())) {
                                buf.append(p.toXML());
                            }
                        }
                        else {
                            Presence storedPresence = (Presence)p;
                            Element delay = storedPresence.addChildElement("x", "jabber:x:delay");
                            delay.addAttribute("stamp", UTC_FORMAT.format(date));
                            buf.append(p.toXML());
                        }
                        // Append an XML representation of the packet to the string buffer
                    }
                    buf.append("</transcript>");
                    // Save the transcript (in XML) to the DB
                    DbWorkgroup.updateTranscript(sessionID, buf.toString(), new java.util.Date());
                }

                // If the agent and the user left the room then proceed to dump the transcript to
                // the DB and destroy the room
                if (!((Presence)packet).isAvailable() && set.isEmpty()) {
                    // Delete the counter of occupants for this room
                    occupantsCounter.remove(roomID);
                    initialRequest = requests.remove(sessionID);
                    if (initialRequest != null && initialRequest.hasJoinedRoom()) {
                        // Notify the request that the support session has finished
                        initialRequest.supportEnded();
                    }
                    // Build the XML for the transcript
                    Map<Packet, java.util.Date> map = transcripts.get(roomID);
                    StringBuilder buf = new StringBuilder();
                    buf.append("<transcript>");
                    for (Packet p : map.keySet()) {
                        java.util.Date date = map.get(p);
                        // Add the delay information
                        if (p instanceof Message) {
                            Message storedMessage = (Message)p;
                            Element delay = storedMessage.addChildElement("x", "jabber:x:delay");
                            delay.addAttribute("stamp", UTC_FORMAT.format(date));
                            if (ModelUtil.hasLength(storedMessage.getBody())) {
                                buf.append(p.toXML());
                            }
                        }
                        else {
                            Presence storedPresence = (Presence)p;
                            Element delay = storedPresence.addChildElement("x", "jabber:x:delay");
                            delay.addAttribute("stamp", UTC_FORMAT.format(date));
                            buf.append(p.toXML());
                        }
                        // Append an XML representation of the packet to the string buffer
                    }
                    buf.append("</transcript>");
                    // Save the transcript (in XML) to the DB
                    //DbWorkgroup.updateTranscript(sessionID, buf.toString(), new java.util.Date());

                    // Leave Chat Room (the room will be destroyed)
                    String roomJID = packet.getFrom().toString() + "/" + getJID().getNode();
                    LeaveRoom leaveRoom = new LeaveRoom(getFullJID().toString(), roomJID);
                    send(leaveRoom);
                    // Remove the transcript information of this room since the room no
                    // longer exists
                    transcripts.remove(roomID);
                    // Trigger the event that a chat support has finished
                    WorkgroupEventDispatcher.chatSupportFinished(this, sessionID);
                }
                // Invoke the room interceptor after the presence has been processed
                interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, false, true);
            }
            else if (packet instanceof Message) {
                // Filter messages sent from the room itself since we don't want the
                // transcript to include things like "room locked"
                if (packet.getFrom().getResource() != null) {
                    // Invoke the room interceptor before processing the presence
                    interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, false, false);
                    // Add the new message to the list of sent packets
                    Map<Packet, java.util.Date> messageList = transcripts.get(roomID);
                    if (messageList == null) {
                        messageList = new LinkedHashMap<Packet, java.util.Date>();
                        transcripts.put(roomID, messageList);
                    }
                    messageList.put(packet.createCopy(), new java.util.Date());
                    // Invoke the room interceptor after the presence has been processed
                    interceptorManager.invokeInterceptors(getJID().toBareJID(), packet, false, true);
                }
            }
        }
    }

    /**
     * An agent has accepted the offer and was choosen to answer the user's requests. The workgroup
     * will create a new room where the agent can answer the user's needs. Once the room has been
     * created, the Agent and the user that made the request will receive invitiations to join the
     * newly created room.<p>
     * <p/>
     * The workgroup will listen for all the packets sent to the room and generate a conversation
     * transcript.
     *
     * @param agent   the AgentSession that accepted and was choosen to respond the user's requests.
     * @param request the request made by a user.
     */
    public void sendInvitation(AgentSession agent, UserRequest request) {
        // TODO When running LA as a plugin (internal component) and if the plugin is removed then
        // we need to destroy all MUC rooms created by workgroups
        try {
            RoomInterceptorManager interceptorManager = RoomInterceptorManager.getInstance();

            WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
            String userJID = request.getUserJID().toString();
            final Workgroup sessionWorkgroup = request.getWorkgroup();
            final String sessionID = request.getSessionID();

            String workgroupName = getJID().getNode();
            final String serviceName = workgroupManager.getMUCServiceName();
            final String roomName = sessionID + "@" + serviceName;
            final String roomJID = roomName + "/" + workgroupName;

            // Create the room by joining it. The workgroup will be the owner of the room and will
            // invite the Agent and the user to join the room
            JoinRoom joinRoom = new JoinRoom(getFullJID().toString(), roomJID);
            interceptorManager.invokeInterceptors(getJID().toBareJID(), joinRoom, false, false);
            send(joinRoom);
            interceptorManager.invokeInterceptors(getJID().toBareJID(), joinRoom, false, true);

            // Configure the newly created room
            Map<String, Collection<String>> fields = new HashMap<String, Collection<String>>();
            // Make a non-public room
            List<String> values = new ArrayList<String>();
            values.add("0");
            fields.put("muc#roomconfig_publicroom", values);
            // Set the room description
            values = new ArrayList<String>();
            values.add(roomName);
            fields.put("muc#roomconfig_roomdesc", values);
            // Set that anyone can change the room subject
            values = new ArrayList<String>();
            values.add("1");
            fields.put("muc#roomconfig_changesubject", values);
            // Make the room temporary
            values = new ArrayList<String>();
            values.add("0");
            fields.put("muc#roomconfig_persistentroom", values);
            // Set that only moderators can see the occupants' JID
            values = new ArrayList<String>();
            values.add("moderators");
            fields.put("muc#roomconfig_whois", values);
            // Set that we want packets to include the real JID
            values = new ArrayList<String>();
            values.add("0");
            fields.put("anonymous", values);
            // Only broadcast presences of participants and visitors
            values = new ArrayList<String>();
            values.add("participant");
            values.add("visitor");
            fields.put("muc#roomconfig_presencebroadcast", values);

            RoomConfiguration conf = new RoomConfiguration(fields);
            conf.setTo(roomName);
            conf.setFrom(getFullJID());
            interceptorManager.invokeInterceptors(getJID().toBareJID(), conf, false, false);
            send(conf);
            interceptorManager.invokeInterceptors(getJID().toBareJID(), conf, false, true);

            // Create a new entry for the active session and the request made by the user
            requests.put(sessionID, request);

            // Invite the Agent to the new room
            Invitation invitation = new Invitation(agent.getJID().toString(), sessionID);
            invitation.setTo(roomName);
            invitation.setFrom(getFullJID());
            // Add workgroup extension that includes the JID of the user that made the request
            Element element = invitation.addChildElement("offer", "http://jabber.org/protocol/workgroup");
            element.addAttribute("jid", userJID);
            // Add custom extension that includes the sessionID
            element = invitation.addChildElement("session", "http://jivesoftware.com/protocol/workgroup");
            element.addAttribute("workgroup", sessionWorkgroup.getJID().toString());
            element.addAttribute("id", sessionID);
            // Add custom extension that includes the userID if the session belongs to an
            // anonymous user
            if (request.isAnonymousUser()) {
                element = invitation.addChildElement("user", "http://jivesoftware.com/protocol/workgroup");
                element.addAttribute("id", request.getUserID());
            }
            interceptorManager.invokeInterceptors(getJID().toBareJID(), invitation, false, false);
            send(invitation);
            interceptorManager.invokeInterceptors(getJID().toBareJID(), invitation, false, true);

            // Invite the user to the new room
            sendUserInvitiation(request, roomName);

            // Notify the request that invitations for support have been sent
            request.invitationsSent(sessionID);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Sends the room invitation to the user that made the request.
     *
     * @param request the Request that the user made to join a workgroup.
     * @param roomID  the id of the room where the user is being invited.
     */
    public void sendUserInvitiation(UserRequest request, String roomID) {
        String userJID = request.getUserJID().toString();
        final String sessionID = request.getSessionID();
        final String serviceName = WorkgroupManager.getInstance().getMUCServiceName();
        final String roomName = sessionID + "@" + serviceName;

        Invitation invitation = new Invitation(userJID, "Please join me for a chat.");
        invitation.setTo(roomName);
        invitation.setFrom(getFullJID());
        // Add workgroup extension that includes the JID of the workgroup
        Element element = invitation.addChildElement("workgroup",
            "http://jabber.org/protocol/workgroup");
        element.addAttribute("jid", getJID().toBareJID());
        // Add custom extension that includes the sessionID
        element =
            invitation.addChildElement("session", "http://jivesoftware.com/protocol/workgroup");
        element.addAttribute("id", sessionID);
        RoomInterceptorManager interceptorManager = RoomInterceptorManager.getInstance();
        interceptorManager.invokeInterceptors(getJID().toBareJID(), invitation, false, false);
        send(invitation);
        interceptorManager.invokeInterceptors(getJID().toBareJID(), invitation, false, true);
    }

    /**
     * Users that have received an invitation to join a room and haven't done so may receive
     * another invitation. The exact (recovery) action to do will depend on the type of client
     * used by the user to join the workgroup. For instance, if the user was using a chatbot to
     * join the workgroup then instead of receiving another invitation he may be asked if he wants
     * to receive another invitation.
     */
    public void checkRequests() {
        for (String roomID : requests.keySet()) {
            UserRequest request = requests.get(roomID);
            // Check invitations if an invitation was sent and the user hasn't joined the room yet
            if (request != null) {
                request.checkRequest(roomID);
            }
        }
    }

    /**
     * Returns the list of packets, including Presence and Messages, sent to the room or
     * <tt>null</tt> if the room has currently no occupants (ie. does not exist).
     *
     * @param roomID the id of a room (node of the JID) that exists and was created by this
     *               workgroup for a chat with a user
     * @return the list of packets sent to an existing room
     */
    public Map<Packet, java.util.Date> getTranscript(String roomID) {
        return transcripts.get(roomID);
    }

    // #############################################################################
    // Package access methods - For classes that need extra workgroup access
    // #############################################################################

    /**
     * <p>Obtain the agent manager associated with the workgroup.</p>
     *
     * @return The agent manager for this workgroup
     *         <p/>
     *         <!-- DbC -->
     */
    public AgentManager getAgentManager() {
        return agentManager;
    }

    /**
     * Returns a collection with all the agent session that are present in the workgroup. If the
     * same agent is present in more than one queue then the answer will only include one instance
     * of the agent session.
     *
     * @return a collection with all the agent session that are present in the workgroup.
     */
    public Collection<AgentSession> getAgentSessions() {
        Collection<AgentSession> answer = new HashSet<AgentSession>();
        for (RequestQueue queue : queues.values()) {
            answer.addAll(queue.getAgentSessionList().getAgentSessions());
        }
        return Collections.unmodifiableCollection(answer);
    }

    /**
     * Returns a collection with all the agent session that are available for chat in the workgroup.
     * A chat session is available for chat based on the presence status. If the same agent is
     * present in more than one queue then the answer will only include one instance of the agent
     * session.
     *
     * @return a collection with all the agent session that are available for chat in the workgroup.
     */
    public Collection<AgentSession> getAgentAvailableSessions() {
        Collection<AgentSession> answer = new HashSet<AgentSession>();
        for (RequestQueue queue : queues.values()) {
            for (AgentSession session : queue.getAgentSessionList().getAgentSessions()) {
                if (session.isAvailableToChat()) {
                    answer.add(session);
                }
            }
        }
        return Collections.unmodifiableCollection(answer);
    }

    /**
     * Returns a collection with all the agents that belong to the workgroup. If the same agent
     * is present in more than one queue then the answer will only include one instance
     * of the agent.
     *
     * @return a collection with all the agents that are belong to the workgroup.
     */
    public Collection<Agent> getAgents() {
        Collection<Agent> answer = new HashSet<Agent>();
        for (RequestQueue queue : queues.values()) {
            answer.addAll(queue.getMembers());

            for (Group group : queue.getGroups()) {
                for (Agent agent : agentManager.getAgents(group)) {
                    answer.add(agent);
                }
            }
        }
        return Collections.unmodifiableCollection(answer);
    }

    // #############################################################################
    // Field access methods
    // #############################################################################
    public void setDescription(String description) {
        if (description == null) {
            description = "";
        }

        if (description.equals(this.description)) {
            // Do nothing
            return;
        }
        this.description = description;
        updateWorkgroup();
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the chatbot that will respond to messages sent to this workgroup. Workgroups may
     * have a chatbot but it's not mandatory to have one.
     *
     * @return the chatbot that will respond to messages sent to this workgroup.
     */
    public Chatbot getChatBot() {
        if (!isChatbotEnabled()) {
            return null;
        }
        if (chatbot == null) {
            synchronized (this) {
                if (chatbot == null) {
                    chatbot = new Chatbot(this);
                }
            }
        }
        return chatbot;
    }

    /**
     * Sets if the workgroup should use a chatbot for answering the messages sent to the workgroup.
     *
     * @param enabled true if a chatbot will respond to the messages sent to the workgroup.
     * @throws UnauthorizedException if not allowed to change the workgroup property
     */
    public void chatbotEnabled(boolean enabled) throws UnauthorizedException {
        getProperties().setProperty("chatbot.enabled", enabled ? "true" : "false");
    }

    /**
     * Returns true if the chatbot is enabled. When the chatbot is enabled it will answer
     * messages sent to the workgroup.
     *
     * @return true if the chatbot is enabled.
     */
    public boolean isChatbotEnabled() {
        return "true".equals(getProperties().getProperty("chatbot.enabled"));
    }

    public Status getStatus() {
        // TODO: The logic in this method appears too complex. May need refactor after
        // TODO: removing schedule feature. 
        boolean actualOpenStatus = open;

        // Workgroup can only be open if there are agents in the workgroup.
        if (actualOpenStatus) {
            actualOpenStatus = isOpen();
            if (open) {
                if (actualOpenStatus) {
                    return Status.OPEN;
                }
                else {
                    return Status.READY;
                }
            }
            else {
                return Status.CLOSED;
            }
        }
        return Status.CLOSED;
    }

    private boolean isOpen() {
        boolean opened = false;
        for (RequestQueue requestQueue : getRequestQueues()) {
            opened = requestQueue.getAgentSessionList().containsAvailableAgents();
            if (opened) {
                break;
            }
        }
        return opened;
    }

    public void setStatus(Status status) {
        if (status == Status.OPEN || status == Status.READY) {
            if (open) {
                // Do nothing if the value is not going to change
                return;
            }
            this.open = true;
        }
        else {
            if (!open) {
                // Do nothing if the value is not going to change
                return;
            }
            this.open = false;
        }
        // Seems that this method is being used as an initialization resort so we are resetting
        // the schedule of the workgroup (if there was one)
        disableSchedule();
        if (updateWorkgroup()) {
            broadcastPresence();
        }
    }

    /**
     * Notification message that the some images of the workgroup has changed.
     */
    public void imagesChanged() {
        // Note: The update to the DB is useless except for updating the last modified date though
        // the actual modification took place in another table. But we could say that images are
        // an internal component of this object. :) The last modified date will be sent in the
        // broadcasted presence
        if (updateWorkgroup()) {
            broadcastPresence();
        }
    }

    public boolean isFollowingSchedule() {
        return false;
    }

    /**
     * Disables the schedule that this workgroup might be following. To enable the schedule a new
     * schedule must be assigned to the workgroup.
     */
    public void disableSchedule() {
        followSchedule = false;
        if (schedule != null) {
            schedule.clear();
        }
        if (updateWorkgroup()) {
            broadcastPresence();
        }
    }

    public Schedule getSchedule() {
        return schedule;
    }

    /**
     * Sets a new schedule for this workgroup thus enabling the scheduling feature.
     *
     * @param schedule the new schedule to follow for this workgroup.
     */
    public void setSchedule(Schedule schedule) {
        if (schedule == null || schedule.getID() != id) {
            throw new IllegalArgumentException();
        }
        followSchedule = true;
        if (updateWorkgroup()) {
            broadcastPresence();
        }
    }


    public int getMaxChats() {
        if (isDefaultMaxChats()) {
            return WorkgroupManager.getInstance().getDefaultMaxChats();
        }
        return maxChats;
    }

    public void setMaxChats(int max) {
        if (max == maxChats) {
            // Do nothing
            return;
        }
        maxChats = max;
        updateWorkgroup();
    }

    public int getMinChats() {
        if (isDefaultMinChats()) {
            return WorkgroupManager.getInstance().getDefaultMinChats();
        }
        return minChats;
    }

    public void setMinChats(int min) {
        if (min == minChats) {
            // Do nothing
            return;
        }
        minChats = min;
        updateWorkgroup();
    }

    public void setRequestTimeout(long timeout) {
        if (timeout == requestTimeout) {
            // Do nothing
            return;
        }
        requestTimeout = timeout;
        updateWorkgroup();
    }

    public long getRequestTimeout() {
        if (isDefaultRequestTimeout()) {
            return WorkgroupManager.getInstance().getDefaultRequestTimeout();
        }
        return requestTimeout;
    }

    public void setOfferTimeout(long timeout) {
        if (timeout == offerTimeout) {
            // Do nothing
            return;
        }
        offerTimeout = timeout;
        updateWorkgroup();
    }

    public long getOfferTimeout() {
        if (isDefaultOfferTimeout()) {
            return WorkgroupManager.getInstance().getDefaultOfferTimeout();
        }
        return offerTimeout;
    }

    public boolean isDefaultMaxChats() {
        return maxChats == -1;
    }

    public boolean isDefaultMinChats() {
        return minChats == -1;
    }

    public boolean isDefaultRequestTimeout() {
        return requestTimeout == -1;
    }

    public boolean isDefaultOfferTimeout() {
        return offerTimeout == -1;
    }

    public DbProperties getProperties() {
        if (properties == null) {
            properties = new JiveLiveProperties("fpWorkgroupProp", id);
        }
        return properties;
    }

    public void shutdown() {
        for (RequestQueue requestQueue : getRequestQueues()) {
            requestQueue.shutdown();
        }
        // Release the chatbot
        chatbot = null;
        queueComparator = null;
    }

    private void loadWorkgroup() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_WORKGROUP);

            pstmt.setLong(1, id);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                workgroupName = rs.getString(1);
                if (rs.getString(2) != null && rs.getString(2).length() > 0) {
                    displayName = rs.getString(2);
                }
                else {
                    displayName = workgroupName;
                }
                description = rs.getString(3);
                open = rs.getInt(4) == 1;
                followSchedule = rs.getInt(5) == 1;
                creationDate = new Date(Long.parseLong(rs.getString(6).trim()));
                modDate = new Date(Long.parseLong(rs.getString(7).trim()));
                maxChats = rs.getInt(8);
                minChats = rs.getInt(9);
                offerTimeout = rs.getInt(10);
                requestTimeout = rs.getInt(11);
                schedule = new Schedule(id, rs.getString(12));
            }
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void loadQueues() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_QUEUES);
            pstmt.setLong(1, id);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                loadRequestQueue(rs.getLong(1));
            }
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private boolean createQueue(long handbackid, Object data) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CREATE_QUEUE);
            pstmt.setLong(1, handbackid);
            pstmt.setLong(2, id);
            pstmt.setString(3, (String)data);
            pstmt.setInt(4, 0);
            pstmt.setInt(5, -1);
            pstmt.setInt(6, -1);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private boolean deleteQueue(long handbackid) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_QUEUE);
            pstmt.setLong(1, handbackid);
            pstmt.executeUpdate();
            pstmt.close();
            // Delete dispatcher properties
            pstmt = con.prepareStatement(DELETE_QUEUE_PROPS);
            pstmt.setLong(1, handbackid);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private boolean updateWorkgroup() {
        // Update the last modification date
        modDate = new Date(System.currentTimeMillis());
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_WORKGRUP);
            pstmt.setString(1, displayName);
            pstmt.setString(2, description);
            pstmt.setInt(3, open ? 1 : 0);
            pstmt.setInt(4, followSchedule ? 1 : 0);
            pstmt.setString(5, StringUtils.dateToMillis(creationDate));
            pstmt.setString(6, StringUtils.dateToMillis(modDate));
            pstmt.setInt(7, maxChats);
            pstmt.setInt(8, minChats);
            pstmt.setLong(9, offerTimeout);
            pstmt.setLong(10, requestTimeout);
            pstmt.setString(11, schedule.toString());
            pstmt.setLong(12, id);
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    /**
     * Returns the JID of the workgroup. The node of the JID will be the workgroup name and
     * the domain will have a "workgroup." prefix before the hostname of the server. No
     * resource is included in the returned JID.
     *
     * @return the JID of the workgroup.
     */
    public JID getJID() {
        return new JID(workgroupName + "@workgroup." +
            ComponentManagerFactory.getComponentManager().getServerName());
    }

    /**
     * Returns a full JID of the workgroup composed by the workgroup JID and a resource that
     * will match the workgroup name. The only place where a full JID may be required is when
     * sending packets to the conference service. Somce servers require that a full JID might
     * be used to route packets between components.
     *
     * @return a full JID of the workgroup composed by the workgroup JID and a resource that
     *         will match the workgroup name.
     */
    public JID getFullJID() {
        return new JID(workgroupName,
            "workgroup." + ComponentManagerFactory.getComponentManager().getServerName(),
            workgroupName);
    }

    public long getID() {
        return id;
    }

    /**
     * Returns the name to use when displaying the workgroup. This information can be used
     * when showing the workgroup as a roster item.
     *
     * @return the name to use when displaying the workgroup.
     */
    public String getDisplayName() {
        return displayName;
    }


    /**
     * Sets the name to use when displaying the workgroup. This information can be used
     * when showing the workgroup as a roster item.
     *
     * @param displayName the name to use when displaying the workgroup.
     */
    public void setDisplayName(String displayName) {
        if (displayName.equals(this.displayName)) {
            // Do nothing
            return;
        }
        this.displayName = displayName;
        updateWorkgroup();
    }

    /**
     * Returns the last date when the workgroup properties were modified.
     *
     * @return the last date when the workgroup properties were modified.
     */
    public Date getModificationDate() {
        return modDate;
    }

    /**
     * Notification message that a new AgentSession has been started. This method is useful
     * for triggering events (in the future).
     *
     * @param agentSession the session that has just been started.
     */
    public void agentJoined(AgentSession agentSession) {
        // Since the session has received the queues status details we need to update the status
        // in the manager so that the thread that is polling for changes in the status does not
        // send the queues status details again to this session
        WorkgroupManager.getInstance().updateWorkgroupStatus(this);
        // Trigger the event that an agent has joined the workgroup
        WorkgroupEventDispatcher.agentJoined(this, agentSession);
    }

    /**
     * Notification message that an AgentSession has ended. This method is useful
     * for triggering events (in the future).
     *
     * @param agentSession the session that has ended.
     */
    public void agentDeparted(AgentSession agentSession) {
        // Trigger the event that an agent has left the workgroup
        WorkgroupEventDispatcher.agentDeparted(this, agentSession);
        // Update the status in the manager so that the thread that is polling for changes in
        // the status does not send the queues status details again to this session
        WorkgroupManager.getInstance().updateWorkgroupStatus(this);
    }

    /**
     * Notification method saying that the workgroup has been opened.
     */
    public void notifyOpened() {
        // Notify the prensence handler of this workgroup that the workgroup is now opened. The
        // presence handler will notify the availability of the workgroup to the users that are
        // tracking the workgroup's presence
        workgroupPresenceHandler.broadcastWorkgroupPresence();
        // Trigger the event that the workgroup has been opened
        WorkgroupEventDispatcher.workgroupOpened(this);
    }

    /**
     * Notification method saying that the workgroup has been closed.
     */
    public void notifyClosed() {
        // Notify the prensence handler of this workgroup that the workgroup is now closed. The
        // presence handler will notify the availability of the workgroup to the users that are
        // tracking the workgroup's presence
        workgroupPresenceHandler.broadcastWorkgroupPresence();
        // Trigger the event that the workgroup has been closed
        WorkgroupEventDispatcher.workgroupClosed(this);
    }

    public void cleanup() {
        // TODO Clean up dangling requests
        // TODO Destroy rooms that never got occupants except the workgroup
        // Clean up the chatbot sessions
        if (chatbot != null) {
            chatbot.cleanup();
        }
    }

    /**
     * Sends information to the agent that requested it about the occupants in the specified
     * room. If the room does no longer exist then no information will be returned. This means
     * that the chat should be happening at the moment of the query.
     *
     * @param packet the request sent by the agent.
     * @param roomID the id of the room that the agent is requesting information
     */
    public void sendOccupantsInfo(IQ packet, String roomID) {
        IQ statusPacket = IQ.createResultIQ(packet);
        Element occupantsInfo = statusPacket.setChildElement("occupants-info",
            "http://jivesoftware.com/protocol/workgroup");
        occupantsInfo.addAttribute("roomID", roomID);
        Map<Packet, java.util.Date> packets = transcripts.get(roomID);
        if (packets != null) {
            Collection<String> processed = new ArrayList<String>();
            for (Packet p : packets.keySet()) {
                if (p instanceof Presence) {
                    Presence presence = (Presence)p;
                    // Get the JID of the presence's user
                    String userJID = presence.getChildElement("x",
                        "http://jabber.org/protocol/muc#user")
                        .element("item")
                        .attributeValue("jid");
                    // Only add information about the first presence so we know the time when the
                    // occupant joined the room
                    if (!processed.contains(userJID)) {
                        processed.add(userJID);
                        Element occupantInfo = occupantsInfo.addElement("occupant");
                        occupantInfo.addElement("jid").setText(userJID);
                        occupantInfo.addElement("nickname").setText(presence.getFrom().getResource());
                        occupantInfo.addElement("joined").setText(UTC_FORMAT.format(packets.get(p)));
                    }
                }
            }
        }
        // Send the response
        send(statusPacket);
    }

    public void processInvitation(InvitationRequest invitation, IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        reply.setFrom(getJID());
        // Verify that requester is a valid agent
        AgentSession agentSession = null;
        try {
             agentSession = agentManager.getAgentSession(packet.getFrom());
        } catch (AgentNotFoundException e) {
            // Ignore
        }
        if (agentSession == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            send(reply);
            Log.debug("Agent not found while accepting offer");
            return;
        }
        // Answer that the invitation was received and that it is being processed
        send(reply);
        // Execute the invitation
        invitation.execute();
    }

    public void processTransfer(TransferRequest transfer, IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        reply.setFrom(getJID());
        // Verify that requester is a valid agent
        AgentSession agentSession = null;
        try {
             agentSession = agentManager.getAgentSession(packet.getFrom());
        } catch (AgentNotFoundException e) {
            // Ignore
        }
        if (agentSession == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            send(reply);
            Log.debug("Agent not found while accepting offer");
            return;
        }
        // Answer that the transfer was received and that it is being processed
        send(reply);
        // Execute the transfer
        transfer.execute();
    }

    void createGroupChatRoom() {
        String roomJID = getGroupChatRoomName() + "/workgroup";
        // Create the room by joining it. The workgroup will be the owner of the room and will
        // invite the Agent and the user to join the room
        JoinRoom joinRoom = new JoinRoom(getFullJID().toString(), roomJID);
        send(joinRoom);

        // Configure the newly created room
        Map<String, Collection<String>> fields = new HashMap<String, Collection<String>>();
        // Make a non-public room
        List<String> values = new ArrayList<String>();
        values.add("0");
        fields.put("muc#roomconfig_publicroom", values);
        // Set the room name
        values = new ArrayList<String>();
        values.add("Workgroup " + getJID().getNode() + " Chat Room");
        fields.put("muc#roomconfig_roomname", values);
        // Set the room description
        values = new ArrayList<String>();
        values.add("Workgroup Chat Room");
        fields.put("muc#roomconfig_roomdesc", values);
        // Set the max number of occupants to unlimited
        values = new ArrayList<String>();
        values.add("0");
        fields.put("muc#roomconfig_maxusers", values);
        // Set that anyone can change the room subject
        values = new ArrayList<String>();
        values.add("1");
        fields.put("muc#roomconfig_changesubject", values);
        // Make the room persistent
        values = new ArrayList<String>();
        values.add("1");
        fields.put("muc#roomconfig_persistentroom", values);
        // Make the room not moderated
        values = new ArrayList<String>();
        values.add("0");
        fields.put("muc#roomconfig_moderatedroom", values);
        // Make the room not members-only
        values = new ArrayList<String>();
        values.add("0");
        fields.put("muc#roomconfig_membersonly", values);
        // Set that anyone can send invitations
        values = new ArrayList<String>();
        values.add("1");
        fields.put("muc#roomconfig_allowinvites", values);
        // Make the room not password protected
        values = new ArrayList<String>();
        values.add("0");
        fields.put("muc#roomconfig_passwordprotectedroom", values);
        // Enable the log for the room
        values = new ArrayList<String>();
        values.add("1");
        fields.put("muc#roomconfig_enablelogging", values);
        // Set that only moderators can see the occupants' JID
        values = new ArrayList<String>();
        values.add("moderators");
        fields.put("muc#roomconfig_whois", values);
        // Only broadcast presences of participants and visitors
        values = new ArrayList<String>();
        values.add("moderator");
        values.add("participant");
        values.add("visitor");
        fields.put("muc#roomconfig_presencebroadcast", values);
        RoomConfiguration conf = new RoomConfiguration(fields);
        conf.setTo(getGroupChatRoomName());
        conf.setFrom(getFullJID());
        send(conf);

        // Change the subject of the room by sending a new message
        Message message = new Message();
        message.setType(Message.Type.groupchat);
        message.setSubject("This is a private discussion room for members of this workgroup.");
        message.setFrom(getFullJID());
        message.setTo(getGroupChatRoomName());
        send(message);

        // Leave Chat Room
        LeaveRoom leaveRoom = new LeaveRoom(getFullJID().toString(), roomJID);
        send(leaveRoom);
    }

    /**
     * The workgroup is been deleted so destroy the room and remove the accepted
     * presence subscriptions.
     */
    void destroy() {
        // Notify the handler of the accepted presence subscriptions that the workgroup is been
        // destroyed
        workgroupPresenceHandler.workgroupDestroyed();
        // Destroy the group chat room of this workgroup
        destroyGroupChatRoom();
    }

    private void destroyGroupChatRoom() {
        String roomJID = getGroupChatRoomName() + "/workgroup";
        // We need to be an occupant of the room to destroy it
        JoinRoom joinRoom = new JoinRoom(getFullJID().toString(), roomJID);
        send(joinRoom);

        // Destroy the group chat room of the workgroup
        DestroyRoom destroy = new DestroyRoom(null, null);
        destroy.setFrom(getFullJID());
        destroy.setTo(getGroupChatRoomName());
        send(destroy);
    }

    private String getGroupChatRoomName() {
        String serviceName = WorkgroupManager.getInstance().getMUCServiceName();
        return "workgroup-" + this.getJID().getNode() + "@" + serviceName;
    }

    private boolean containsValidReferer(Request request) {
        try {
            // Check for valid domains
            final DbProperties props = getProperties();
            String validDomains = props.getProperty("validDomains");

            // If there are valid domains specified, then validate
            if (ModelUtil.hasLength(validDomains)) {
                Map metadata = request.getMetaData();

                List list = (List)metadata.get("referer");
                if (metadata.containsKey("referer")) {
                    metadata.remove("referer");
                }

                if (list != null && list.size() > 0) {
                    String referer = (String)list.get(0);
                    URL refererURL = new URL(referer);

                    String domain = refererURL.getHost().toLowerCase();

                    StringTokenizer tkn = new StringTokenizer(validDomains, ",");
                    boolean match = false;
                    while (tkn.hasMoreTokens()) {
                        String token = tkn.nextToken().trim().toLowerCase();
                        if (domain.endsWith(token)) {
                            match = true;
                            break;
                        }
                    }
                    return match;
                }
                else {
                    return false;
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        return true;
    }

    public WorkgroupPresence getWorkgroupPresenceHandler(){
        return workgroupPresenceHandler;
    }

    /**
     * Sorts all <code>RequestQueue</code> by ID.
     */
    static Comparator<RequestQueue> queueComparator = new Comparator<RequestQueue>() {
        public int compare(RequestQueue queue1, RequestQueue queue2) {
            float int1 = queue1.getID();
            float int2 = queue2.getID();

            if (int1 == int2) {
                return 0;
            }

            if (int1 > int2) {
                return 1;
            }

            if (int1 < int2) {
                return -1;
            }

            return 0;
        }
    };

    public static enum Status {
        CLOSED,
        READY,
        OPEN
    }
}
