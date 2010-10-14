/**
 * $RCSfile$
 * $Revision$
 * $Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.openfire.fastpath.commands.CreateWorkgroup;
import org.jivesoftware.openfire.fastpath.commands.DeleteWorkgroup;
import org.jivesoftware.openfire.fastpath.events.EmailTranscriptEvent;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager;
import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.jivesoftware.openfire.fastpath.util.WorkgroupUtils;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.disco.IQDiscoInfoHandler;
import org.jivesoftware.xmpp.workgroup.disco.IQDiscoItemsHandler;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventDispatcher;
import org.jivesoftware.xmpp.workgroup.routing.RoutingManager;
import org.jivesoftware.xmpp.workgroup.search.ChatSearchManager;
import org.jivesoftware.xmpp.workgroup.search.IQChatSearchHandler;
import org.jivesoftware.xmpp.workgroup.utils.FastpathConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * Manages workgroups in the system. This manager primarily defers to the workgroups
 * to manage themselves and serves as a 'factory' for creating, obtaining and deleting them.
 *
 * @author Derek DeMoro
 */
public class WorkgroupManager implements Component {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupManager.class);
	
    private static final String LOAD_WORKGROUPS =
        "SELECT workgroupID FROM fpWorkgroup";
    private static final String ADD_WORKGROUP =
        "INSERT INTO fpWorkgroup (workgroupID, jid, displayName, description, status, " +
            "creationDate, modificationDate, maxchats, minchats, offerTimeout, requestTimeout, " +
            "modes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String DELETE_WORKGROUP =
        "DELETE FROM fpWorkgroup WHERE workgroupID=?";

    private static WorkgroupManager instance = new WorkgroupManager();
    private EmailTranscriptEvent emailTranscriptEvent;

    /**
     * Returns a workgroup manager instance (singleton).
     *
     * @return a workgroup manager instance.
     */
    public synchronized static WorkgroupManager getInstance() {
        if (instance == null) {
            instance = new WorkgroupManager();
        }
        return instance;
    }

    /**
     * Track all Workgroups
     */
    private Map<String, Workgroup> workgroups = new ConcurrentHashMap<String, Workgroup>();

    /**
     * Tracks the last known workgroup open status
     */
    private Map<Long, Workgroup.Status> workgroupOpenStatus = new HashMap<Long, Workgroup.Status>();

    private AgentManager agentManager;

    public List iqHandlers = new LinkedList();

    private int defaultMaxChats = 4; // default value, usually overridden by config file
    private int defaultMinChats = 1;
    private long defaultOfferTimeout = 20 * 1000; // 20 seconds
    private long defaultRequestTimeout = 4 * 60 * 1000; // 4 minutes

    /**
     * <p>Simple flag to track whether the workgroups have been loaded or not.</p>
     */
    private boolean loaded = false;

    private ReentrantReadWriteLock workgroupLock = new ReentrantReadWriteLock();

    private JID serviceAddress = null;
    private String mucServiceName;

    /**
     * Holds the object responsible for handling disco#info packets to this manager.
     */
    private IQDiscoInfoHandler iqDiscoInfoHandler;
    /**
     * Holds the object responsible for handling disco#items packets to this manager.
     */
    private IQDiscoItemsHandler iqDiscoItemsHandler;
    /**
     * Holds the object responsible for handling transcript searches for this manager.
     */
    private IQChatSearchHandler iqChatSearchHandler = new IQChatSearchHandler(this);
    /**
     * Manager that keeps the list of ad-hoc commands and processing command requests.
     */
    private AdHocCommandManager commandManager;

    private GroupEventListener groupEventListener;

    private TimerTask presenceCheckTask;

    /**
     * Creates a workgroup manager implementation.
     */
    public WorkgroupManager() {
        // Load Chat Properties
        String minChats = JiveGlobals.getProperty("xmpp.live.defaults.minchats");
        String maxChats = JiveGlobals.getProperty("xmpp.live.defaults.maxchats");
        if (minChats != null && minChats.trim().length() > 0) {
            defaultMinChats = Integer.parseInt(minChats);
        }
        if (maxChats != null && maxChats.trim().length() > 0) {
            defaultMaxChats = Integer.parseInt(maxChats);
        }
        String offerTimeout = JiveGlobals.getProperty("xmpp.live.defaults.offerTimeout");
        String requestTimeout = JiveGlobals.getProperty("xmpp.live.defaults.requestTimeout");
        if (offerTimeout != null && offerTimeout.trim().length() > 0) {
            defaultOfferTimeout = Integer.parseInt(offerTimeout);
        }
        if (requestTimeout != null && requestTimeout.trim().length() > 0) {
            defaultRequestTimeout = Integer.parseInt(requestTimeout);
        }

        // Initialize chat settings manager. TODO This will be moved over to an extension file.
        ChatSettingsManager.getInstance();

        // Initialize EmailTranscript Event. TODO This will be moved over to an extension file.
        emailTranscriptEvent = new EmailTranscriptEvent();

        addGroupManagerListener();
        // Create responsible for handling ad-hoc commands in this service
        commandManager = new AdHocCommandManager();

        iqDiscoInfoHandler = new IQDiscoInfoHandler(this, commandManager);
        iqDiscoItemsHandler = new IQDiscoItemsHandler(this, commandManager);

        presenceCheckTask = new TimerTask() {
            @Override
			public void run() {
                handleOutdatePresence();
            }
        };

        TaskEngine.getInstance().scheduleAtFixedRate(presenceCheckTask, 5000, 5000);
    }

    public void start() {
        // Enable the shared secret SASL mechanism, which the Fastpath web client will use.
        // We use a custom SASL mechanism so that web-based customer chats can login without
        // a username or password. However, a shared secret key is still required so that
        // anonymous login doesn't have to be enabled for the whole server.
        if (!SASLAuthentication.isSharedSecretAllowed()) {
            SASLAuthentication.setSharedSecretAllowed(true);
        }

        // If the database was just created then create the "demo" user and "demo" workgroup
        // Workgroup creation requires MUC service address so we need to run this code after the
        // disco stuff
        if (!JiveGlobals.getBooleanProperty("fastpath.database.setup")) {
            boolean createUser = createDemoUser();
            if (createUser) {
                createDemoWorkgroup();
            }
            JiveGlobals.setProperty("fastpath.database.setup", "true");
        }

        // Register ad-hoc commands
        commandManager.addCommand(new CreateWorkgroup());
        commandManager.addCommand(new DeleteWorkgroup());
    }

    public void shutdown() {
        workgroups.clear();
        GroupEventDispatcher.removeListener(groupEventListener);
        instance = null;
        ChatSearchManager.shutdown();
        ChatSettingsManager.shutdown();
        RoutingManager.shutdown();
        WorkgroupProviderManager.shutdown();
        emailTranscriptEvent.shutdown();
        workgroupComparator = null;

        TaskEngine.getInstance().cancelScheduledTask(presenceCheckTask);
    }

    public void stop() {
        for (Workgroup workgroup : getWorkgroups()) {
            workgroup.shutdown();
        }
        workgroups.clear();
        workgroupOpenStatus.clear();
    }

    public AgentManager getAgentManager() {
        return agentManager;
    }

    public int getDefaultMinChats() {
        return defaultMinChats;
    }

    public void setDefaultMinChats(int minChats) {
        if (minChats >= 0) {
            defaultMinChats = minChats;
            JiveGlobals.setProperty("xmpp.live.defaults.minchats", Integer.toString(minChats));
        }
    }

    public int getDefaultMaxChats() {
        return defaultMaxChats;
    }

    public void setDefaultMaxChats(int maxChats) {
        if (maxChats >= 0) {
            defaultMaxChats = maxChats;
            JiveGlobals.setProperty("xmpp.live.defaults.maxchats", Integer.toString(maxChats));
        }
    }

    public long getDefaultOfferTimeout() {
        return defaultOfferTimeout;
    }

    public void setDefaultOfferTimeout(long defaultOfferTimeout) {
        if (defaultOfferTimeout >= 0) {
            this.defaultOfferTimeout = defaultOfferTimeout;
            JiveGlobals.setProperty("xmpp.live.defaults.offerTimeout",
                Long.toString(defaultOfferTimeout));
        }
    }

    public long getDefaultRequestTimeout() {
        return defaultRequestTimeout;
    }

    public void setDefaultRequestTimeout(long defaultRequestTimeout) {
        if (defaultRequestTimeout >= 0) {
            this.defaultRequestTimeout = defaultRequestTimeout;
            JiveGlobals.setProperty("xmpp.live.defaults.requestTimeout",
                Long.toString(defaultRequestTimeout));
        }
    }

    /**
     * Creates a workgroup with default settings.
     *
     * @param name the name of the workgroup.
     * @return the created workgroup
     * @throws UnauthorizedException      if not allowed to create the workgroup.
     * @throws UserAlreadyExistsException If the address is already in use
     */
    public Workgroup createWorkgroup(String name) throws UserAlreadyExistsException, UnauthorizedException {
        if (workgroups.containsKey(name + "@" + serviceAddress.toBareJID())) {
            throw new UserAlreadyExistsException(name);
        }

        // Reserve the username - user ID from the jiveUserID table
        long id = -1;
        Workgroup workgroup = null;
        try {
            id = SequenceManager.nextID(FastpathConstants.WORKGROUP);
            boolean workgroupAdded = addWorkgroup(id, name);
            if (workgroupAdded) {
                workgroupLock.writeLock().lock();
                try {
                    workgroup = new Workgroup(id, agentManager);
                    workgroups.put(workgroup.getJID().toBareJID(), workgroup);
                    workgroupOpenStatus.put(workgroup.getID(), workgroup.getStatus());
                }
                finally {
                    workgroupLock.writeLock().unlock();
                }
                // Create a chat room for this workgroup
                workgroup.createGroupChatRoom();
                // Trigger the event that a workgroup has been created
                WorkgroupEventDispatcher.workgroupCreated(workgroup);
            }
            else {
                throw new UnauthorizedException("Could not insert workgroup in database");
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            if (id != -1) {
                try {
                    if (workgroup != null) {
                        workgroups.remove(workgroup.getJID().toBareJID());
                        workgroupOpenStatus.remove(workgroup.getID());
                        deleteWorkgroup(id);
                    }
                }
                catch (Exception e1) {
                    Log.error(e1.getMessage(), e1);
                }
            }
            if (e instanceof UserAlreadyExistsException) {
                throw (UserAlreadyExistsException)e;
            }
            else {
                throw new UnauthorizedException();
            }
        }
        return workgroup;
    }

    /**
     * Remove a workgroup from the system.
     *
     * @param workgroup the workgroup to remove.
     * @throws UnauthorizedException if not allowed to delete the workgroup.
     */
    public void deleteWorkgroup(Workgroup workgroup) throws UnauthorizedException {
        if (!loaded) {
            throw new IllegalStateException("Workgroup Manager not loaded yet");
        }

        // Trigger the event that a workgroup is being deleted
        WorkgroupEventDispatcher.workgroupDeleting(workgroup);

        deleteWorkgroup(workgroup.getID());
        // Notify the workgroup that it is being destroyed
        workgroup.destroy();

        workgroupLock.writeLock().lock();
        try {
            workgroups.remove(workgroup.getJID().toBareJID());
            workgroupOpenStatus.remove(workgroup.getID());
        }
        finally {
            workgroupLock.writeLock().unlock();
        }

        // Delete the queues of the workgroup
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            workgroup.deleteRequestQueue(requestQueue);
        }
        // Trigger the event that a workgroup has been deleted
        WorkgroupEventDispatcher.workgroupDeleted(workgroup);
    }

    /**
     * Returns the number of workgroups in the system.
     *
     * @return the number of workgroups in the system.
     */
    public int getWorkgroupCount() {
        return workgroups.size();
    }

    /**
     * Returns the workgroup mapped to a specific JID.
     *
     * @param jid the JID mapped to the workgroup.
     * @return the workgroup with the specified JID.
     * @throws UserNotFoundException if the workgroup could not be loaded.
     */
    public Workgroup getWorkgroup(JID jid) throws UserNotFoundException {
        Workgroup wg = workgroups.get(jid.toBareJID());
        if (wg == null) {
            throw new UserNotFoundException(jid.toBareJID());
        }
        return wg;
    }

    /**
     * Return a workgroup based on it's node.
     *
     * @param workgroupName the name of the workgroup.
     * @return the workgroup or null if no workgroup is found.
     */
    public Workgroup getWorkgroup(String workgroupName) {
        for (Workgroup workgroup : getWorkgroups()) {
            if (workgroup.getJID().getNode().equalsIgnoreCase(workgroupName)) {
                return workgroup;
            }
        }

        return null;
    }

    /**
     * Returns all workgroups within the system sorted by ID.
     *
     * @return the collection of workgroups.
     */
    public Collection<Workgroup> getWorkgroups() {
        if (workgroups.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Workgroup> copy = new ArrayList<Workgroup>(workgroups.values());
        Collections.sort(copy, workgroupComparator);

        return Collections.unmodifiableCollection(copy);
    }

    public Iterator<Workgroup> getWorkgroups(WorkgroupResultFilter filter) {
        final List<Workgroup> wgroups = new ArrayList<Workgroup>(workgroups.values());
        Collections.sort(wgroups, workgroupComparator);

        Iterator<Workgroup> groups = filter.filter(wgroups.iterator());
        if (groups == null) {
            groups = Collections.EMPTY_LIST.iterator();
        }
        return groups;
    }

    /**
     * Returns the handler for disco#info packets sent to the workgroup service. The returned
     * handler may be used for configuring its features providers.
     *
     * @return the handler for disco#info packets sent to the workgroup service.
     */
    public IQDiscoInfoHandler getIqDiscoInfoHandler() {
        return iqDiscoInfoHandler;
    }

    /**
     * <p>Trigger an open check every 25 seconds after an initial 45 second delay.</p>
     * <p/>
     * <p>The workgroup knows when it is open according to it's own settings including
     * schedule information. However, the schedule is only checked on demand when new
     * users requests are submitted, or agents query the status of the workgroup.
     * So we must force a check of the workgroup open status periodically to see
     * if it has changed, and if so, to have the workgroup broadcast its presence.
     * This is potentially more bandwidth intensive than having each workgroup watch
     * it's own schedule but uses less threads.</p>
     * TODO: trace down all events that cause a state change so we don't have to poll
     */
    private void startTimer() {
        TaskEngine taskEngine = TaskEngine.getInstance();
        taskEngine.schedule(new TimerTask() {
            @Override
			public void run() {
                workgroupLock.readLock().lock();
                try {
                    for (Workgroup group : workgroups.values()) {
                        Workgroup.Status currentOpen = group.getStatus();
                        Workgroup.Status oldOpen = workgroupOpenStatus.get(group.getID());
                        if (oldOpen != currentOpen) {
                            group.broadcastQueuesStatus();
                            workgroupOpenStatus.put(group.getID(), currentOpen);
                            if (Workgroup.Status.OPEN != oldOpen && Workgroup.Status.OPEN == currentOpen) {
                                // Trigger the event that the workgroup has been opened
                                group.notifyOpened();
                            }
                            else if (Workgroup.Status.OPEN == oldOpen) {
                                // Trigger the event that the workgroup has been closed
                                group.notifyClosed();
                            }
                        }
                    }
                }
                finally {
                    workgroupLock.readLock().unlock();
                }
            }
        }, 45000, 9000);

        // Every 5 minutes let the workgroups clean up dead requests or dead rooms. This may occur
        // if the connections were lost or the invitations were lost or whatever
        taskEngine.schedule(new TimerTask() {
            @Override
			public void run() {
                workgroupLock.readLock().lock();
                try {
                    for (Workgroup group : workgroups.values()) {
                        group.cleanup();
                    }
                }
                finally {
                    workgroupLock.readLock().unlock();
                }
            }
        }, 60000, 300000);

        // Every 15 seconds check for not answered room invitations
        taskEngine.schedule(new TimerTask() {
            @Override
			public void run() {
                workgroupLock.readLock().lock();
                try {
                    for (Workgroup group : workgroups.values()) {
                        group.checkRequests();
                    }
                }
                finally {
                    workgroupLock.readLock().unlock();
                }
            }
        }, 10000, 15000);

        // Every 30 seconds check if the search index of the workgroups should be updated
        taskEngine.schedule(new TimerTask() {
            @Override
			public void run() {
                workgroupLock.readLock().lock();
                try {
                    for (Workgroup group : workgroups.values()) {
                        try {
                            ChatSearchManager.getInstanceFor(group).updateIndex(false);
                        }
                        catch (IOException e) {
                            Log.error(e.getMessage(), e);
                        }
                    }
                }
                finally {
                    workgroupLock.readLock().unlock();
                }
            }
        }, 10000, 30000);
    }

    void updateWorkgroupStatus(Workgroup workgroup) {
        Workgroup.Status newStatus = workgroup.getStatus();
        Workgroup.Status oldStatus = workgroupOpenStatus.put(workgroup.getID(), newStatus);
        if (Workgroup.Status.OPEN != oldStatus && Workgroup.Status.OPEN == newStatus) {
            // Trigger the event that the workgroup has been opened
            workgroup.notifyOpened();
        }
        else if (Workgroup.Status.OPEN == oldStatus && Workgroup.Status.OPEN != newStatus) {
            // Trigger the event that the workgroup has been closed
            workgroup.notifyClosed();
        }
    }

    public String getDefaultChatServer() {
        return getMUCServiceName();
    }

    private boolean addWorkgroup(long workgroupID, String workgroupName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_WORKGROUP);
            Date now = new Date();
            pstmt.setLong(1, workgroupID);
            pstmt.setString(2, workgroupName);
            pstmt.setString(3, workgroupName);
            pstmt.setString(4, "None");
            pstmt.setInt(5, 0);     // start workgroups closed
            pstmt.setString(6, StringUtils.dateToMillis(now));
            pstmt.setString(7, StringUtils.dateToMillis(now));
            pstmt.setInt(8, -1);
            pstmt.setInt(9, -1);
            pstmt.setLong(10, -1);
            pstmt.setLong(11, -1);
            pstmt.setInt(12, 0);   // start schedule mode to manual
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    private void deleteWorkgroup(long workgroupID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_WORKGROUP);
            pstmt.setLong(1, workgroupID);
            pstmt.executeUpdate();
        }
        catch (SQLException ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void loadWorkgroups() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_WORKGROUPS);
            rs = pstmt.executeQuery();
            workgroupLock.writeLock().lock();
            while (rs.next()) {
                Workgroup workgroup = new Workgroup(rs.getLong(1),
                    agentManager);

                workgroups.put(workgroup.getJID().toBareJID(), workgroup);
                workgroupOpenStatus.put(workgroup.getID(), workgroup.getStatus());
            }
        }
        catch (SQLException ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            workgroupLock.writeLock().unlock();
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Initialize routing manager
        RoutingManager.getInstance();
    }

    public void processPacket(Packet packet) {
        try {
            // Check if the packet is a disco request
            if (packet instanceof IQ) {
                if (process((IQ)packet)) {
                    return;
                }
            }
            // Check if the packet was sent to an existent workgroup. If a workgroup
            // was found then let the workgroup process the packet
            try {
                Workgroup workgroup = getWorkgroup(packet.getTo());
                workgroup.process(packet);
            }
            catch (UserNotFoundException e) {
                // Answer a not_authorized error since the workgroup was not found. A
                // not_acceptable error was chosen since we are returning that same error when
                // sending presences to a workgroup from a JID that is not an agent or when the
                // agent does not belong to the workgroup. Answering the same error code ensures
                // some kind of security since the sender cannot distinguish between any of the
                // above situations
                if (packet instanceof Presence) {
                    if (((Presence)packet).getType() == Presence.Type.error) {
                        // Skip Presence packets of type error
                        return;
                    }
                    Presence reply = new Presence();
                    reply.setID(packet.getID());
                    reply.setTo(packet.getFrom());
                    reply.setFrom(packet.getTo());
                    reply.setError(PacketError.Condition.not_authorized);
                    send(reply);
                }
                else if (packet instanceof IQ) {
                    if (((IQ)packet).getType() == IQ.Type.error) {
                        // Skip IQ packets of type error
                        return;
                    }
                    IQ reply = IQ.createResultIQ((IQ)packet);
                    reply.setChildElement(((IQ)packet).getChildElement().createCopy());
                    reply.setError(PacketError.Condition.not_authorized);
                    send(reply);
                }
                else {
                    if (((Message)packet).getType() == Message.Type.error) {
                        // Skip Message packets of type error
                        return;
                    }
                    Message reply = new Message();
                    reply.setID(packet.getID());
                    reply.setTo(packet.getFrom());
                    reply.setFrom(packet.getTo());
                    reply.setError(PacketError.Condition.not_authorized);
                    send(reply);
                }
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    /**
     * Returns true if the IQ packet was processed. This method should only process disco packets
     * sent to the workgroup service.
     *
     * @param iq the IQ packet to process.
     * @return true if the IQ packet was processed.
     */
    private boolean process(IQ iq) {
        if (iq.getType() == IQ.Type.error) {
            // Skip IQ packets of type error
            return false;
        }
        Element childElement = iq.getChildElement();
        String name = null;
        String namespace = null;
        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
            name = childElement.getName();
        }
        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            IQ reply = iqDiscoInfoHandler.handleIQ(iq);
            if (reply != null) {
                send(reply);
            }
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            IQ reply = iqDiscoItemsHandler.handleIQ(iq);
            if (reply != null) {
                send(reply);
            }
        }
        else if ("jabber:iq:version".equals(namespace)) {
            IQ reply = IQ.createResultIQ(iq);
            Element version = reply.setChildElement("query", "jabber:iq:version");
            version.addElement("name").setText("Spark Fastpath");
            version.addElement("version").setText("3.2");
            version.addElement("os").setText("Java 5");
            send(reply);
        }
        else if ("workgroups".equals(name)) {
            try {
                // Check that the sender of this IQ is an agent
                getAgentManager().getAgent(iq.getFrom());
                // Get the agent JID to return his workgroups
                String agentJID = childElement.attributeValue("jid");
                try {
                    // Answer the workgroups where the agent can work in
                    Agent agent = getAgentManager().getAgent(new JID(agentJID));
                    sendWorkgroups(iq, agent);
                }
                catch (AgentNotFoundException e) {
                    IQ reply = IQ.createResultIQ(iq);
                    reply.setChildElement(iq.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.item_not_found));
                    send(reply);
                }
            }
            catch (AgentNotFoundException e) {
                IQ reply = IQ.createResultIQ(iq);
                reply.setChildElement(iq.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.not_authorized));
                send(reply);
            }
        }
        else if ("transcript-search".equals(name)) {
            iqChatSearchHandler.handleIQ(iq);
        }
        else if ("http://jabber.org/protocol/commands".equals(namespace)) {
            // Process ad-hoc command
            IQ reply = commandManager.process(iq);
            send(reply);
        }
        else {
            return false;
        }
        return true;
    }

    private void sendWorkgroups(IQ request, Agent agent) {
        IQ reply = IQ.createResultIQ(request);
        Element workgroupsElement = reply.setChildElement("workgroups",
            "http://jabber.org/protocol/workgroup");
        workgroupsElement.addAttribute("jid", agent.getAgentJID().toBareJID());
        for (Workgroup workgroup : getWorkgroups()) {
            if (workgroup.getAgents().contains(agent)) {
                // Add the information of the workgroup
                Element workgroupElement = workgroupsElement.addElement("workgroup");
                workgroupElement.addAttribute("jid", workgroup.getJID().toBareJID());
            }
        }
        send(reply);
    }

    public String getName() {
        return "Workgroup Plugin";
    }

    public String getDescription() {
        return "Workgroup plugin for Live Assistance.";
    }

    public void initialize(JID jid, ComponentManager componentManager) throws ComponentException {
        // Set the full domain address that this component is serving
        serviceAddress = jid;

        agentManager = new AgentManager();

        // Set a default MUC service JID. This may be required when the server does
        // not support service discovery
        mucServiceName = "conference." + componentManager.getServerName();

        // Send a disco request to discover the MUC service address
        IQ disco = new IQ(IQ.Type.get);
        disco.setTo(componentManager.getServerName());
        disco.setFrom(jid);
        disco.setChildElement("query", "http://jabber.org/protocol/disco#items");
        send(disco);

        // Start the background processes
        startTimer();
        // Load Workgroups
        loadWorkgroups();
        // Set that the workgroups have been loaded
        loaded = true;
    }

    public void send(Packet packet) {
        try {
            ComponentManagerFactory.getComponentManager().sendPacket(this, packet);
        }
        catch (ComponentException e) {
            // Do nothing. This error should never happen
            Log.error(e.getMessage(), e);
        }
    }

    public JID getAddress() {
        return serviceAddress;
    }

    /**
     * Returns the service name for MUC
     *
     * @return the MUC Service Name
     */
    public final String getMUCServiceName() {
        return mucServiceName;
    }

    public void setMUCServiceName(String mucServiceName) {
        this.mucServiceName = mucServiceName;
    }

    /**
     * Listens for changes in the Group model to update respective agents.
     */
    private void addGroupManagerListener() {
        groupEventListener = new GroupEventListener() {
            public void groupCreated(Group group, Map params) {
            }

            public void groupDeleting(Group group, Map params) {
            }

            public void groupModified(Group group, Map params) {

            }

            public void memberAdded(Group group, Map params) {
                String userJID = (String)params.get("member");
                JID jid = new JID(userJID);

                if (!agentManager.hasAgent(jid)) {
                    for (Workgroup workgroup : workgroups.values()) {
                        for (RequestQueue queue : workgroup.getRequestQueues()) {
                            if (queue.hasGroup(group)) {
                                agentManager.getAgents(group);
                            }
                        }
                    }
                }
            }

            public void memberRemoved(Group group, Map params) {
            }

            public void adminAdded(Group group, Map params) {
            }

            public void adminRemoved(Group group, Map params) {
            }
        };
        GroupEventDispatcher.addListener(groupEventListener);
    }

    /**
     * Creates a demo user account.
     *
     * @return true if the user account was created.
     */
    private boolean createDemoUser() {
        // Do nothing if user store is read-only
        if (UserManager.getUserProvider().isReadOnly()) {
            return false;
        }
        try {
            UserManager.getInstance().createUser("demo", "demo", "Fastpath Demo Account", "demo@fastpath.com");
            return true;
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        return false;
    }

    /**
     * Creates a demo workgroup.
     *
     * @return true if the workgroup was created.
     */
    private boolean createDemoWorkgroup() {
        // Create example workgroup
        try {
            if (WorkgroupUtils.createWorkgroup("demo", "Demo workgroup", "demo").size() == 0) {
                JiveGlobals.setProperty("demo.workgroup", "true");
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * Checks for outdated presences caused by network failures, etc.
     */
    private void handleOutdatePresence() {
        for (Workgroup workgroup : getWorkgroups()) {
            for (AgentSession agentSession : workgroup.getAgentSessions()) {
                final JID agentJID = agentSession.getJID();
                final PresenceManager presenceManager = XMPPServer.getInstance().getPresenceManager();
                boolean isOnline = false;
                for (Presence presence : presenceManager.getPresences(agentJID.getNode())) {
                    if (presence.getFrom().equals(agentJID)) {
                        isOnline = true;
                    }
                }

                if (!isOnline) {
                    // Send offline presence to workgroup.
                    for (Workgroup wgroup : agentSession.getWorkgroups()) {
                        Presence presence = new Presence();
                        presence.setFrom(agentJID);
                        presence.setTo(wgroup.getJID());
                        presence.setType(Presence.Type.unavailable);
                        wgroup.getWorkgroupPresenceHandler().process(presence);
                    }
                }
            }
        }
    }

    /**
     * Sorts all <code>Workgroups</code> by ID.
     */
    static Comparator<Workgroup> workgroupComparator = new Comparator<Workgroup>() {
        public int compare(Workgroup item1, Workgroup item2) {
            float int1 = item1.getID();
            float int2 = item2.getID();

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
}
