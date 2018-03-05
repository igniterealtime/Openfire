/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.archive;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.*;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.archive.cluster.GetConversationCountTask;
import org.jivesoftware.openfire.archive.cluster.GetConversationTask;
import org.jivesoftware.openfire.archive.cluster.GetConversationsTask;
import org.jivesoftware.openfire.archive.cluster.HasWrittenAllDataTask;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.component.ComponentEventListener;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.CacheFactory;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Manages all conversations in the system. Optionally, conversations (messages plus meta-data) can be archived to the database. Archiving of
 * conversation data is enabled by default, but can be disabled by setting "conversation.metadataArchiving" to <tt>false</tt>. Archiving of messages
 * in a conversation is disabled by default, but can be enabled by setting "conversation.messageArchiving" to <tt>true</tt>.
 * <p>
 *
 * When running in a cluster only the senior cluster member will keep track of the active conversations. Other cluster nodes will forward conversation
 * events that occurred in the local node to the senior cluster member. If the senior cluster member goes down then current conversations will be
 * terminated and if users keep sending messages between them then new conversations will be created.
 *
 * @author Matt Tucker
 */
public class ConversationManager implements Startable, ComponentEventListener{

    private static final Logger Log = LoggerFactory.getLogger(ConversationManager.class);

    private static final String UPDATE_CONVERSATION = "UPDATE ofConversation SET lastActivity=?, messageCount=? WHERE conversationID=?";
    private static final String UPDATE_PARTICIPANT = "UPDATE ofConParticipant SET leftDate=? WHERE conversationID=? AND bareJID=? AND jidResource=? AND joinedDate=?";
    private static final String INSERT_MESSAGE = "INSERT INTO ofMessageArchive(messageID, conversationID, fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza) "
            + "VALUES (?,?,?,?,?,?,?,?,?)";
    private static final String CONVERSATION_COUNT = "SELECT COUNT(*) FROM ofConversation";
    private static final String MESSAGE_COUNT = "SELECT COUNT(*) FROM ofMessageArchive";
    private static final String DELETE_CONVERSATION_1 = "DELETE FROM ofMessageArchive WHERE conversationID=?";
    private static final String DELETE_CONVERSATION_2 = "DELETE FROM ofConParticipant WHERE conversationID=?";
    private static final String DELETE_CONVERSATION_3 = "DELETE FROM ofConversation WHERE conversationID=?";

    private static final int DEFAULT_IDLE_TIME = 10;
    private static final int DEFAULT_MAX_TIME = 60;
    public static final int DEFAULT_MAX_TIME_DEBUG = 30;

    public static final int DEFAULT_MAX_RETRIEVABLE = 0;
    private static final int DEFAULT_MAX_AGE = 0;

    public static final String CONVERSATIONS_KEY = "conversations";

    private ConversationEventsQueue conversationEventsQueue;
    private TaskEngine taskEngine;

    private Map<String, Conversation> conversations = new ConcurrentHashMap<String, Conversation>();
    private boolean metadataArchivingEnabled;
    /**
     * Flag that indicates if messages of one-to-one chats should be archived.
     */
    private boolean messageArchivingEnabled;
    /**
     * Flag that indicates if messages of group chats (in MUC rooms) should be archived.
     */
    private boolean roomArchivingEnabled;
    private boolean roomArchivingStanzasEnabled;
    /**
     * List of room names to archive. When list is empty then all rooms are archived (if roomArchivingEnabled is enabled).
     */
    private Collection<String> roomsArchived;
    private long idleTime;
    private long maxTime;
    private long maxAge;
    private long maxRetrievable;
    private PropertyEventListener propertyListener;

    private final PriorityBlockingQueue<ArchiveCandidate<Conversation>> conversationQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<ArchiveCandidate<ArchivedMessage>> messageQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<ArchiveCandidate<RoomParticipant>> participantQueue = new PriorityBlockingQueue<>();

    private ExecutorService executorService;

    private TimerTask cleanupTask;
    private TimerTask maxAgeTask;

    private Collection<ConversationListener> conversationListeners;

    /**
     * Keeps the address of those components that provide the gateway service.
     */
    private List<String> gateways;
    private XMPPServerInfo serverInfo;

    public ConversationManager(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
        this.gateways = new CopyOnWriteArrayList<String>();
        this.serverInfo = XMPPServer.getInstance().getServerInfo();
        this.conversationEventsQueue = new ConversationEventsQueue(this, taskEngine);
    }

    public void start() {
        metadataArchivingEnabled = JiveGlobals.getBooleanProperty("conversation.metadataArchiving", true);
        messageArchivingEnabled = JiveGlobals.getBooleanProperty("conversation.messageArchiving", false);
        if (messageArchivingEnabled && !metadataArchivingEnabled) {
            Log.warn("Metadata archiving must be enabled when message archiving is enabled. Overriding setting.");
            metadataArchivingEnabled = true;
        }
        roomArchivingEnabled = JiveGlobals.getBooleanProperty("conversation.roomArchiving", false);
        roomArchivingStanzasEnabled = JiveGlobals.getBooleanProperty("conversation.roomArchivingStanzas", false);
        roomsArchived = StringUtils.stringToCollection(JiveGlobals.getProperty("conversation.roomsArchived", ""));
        if (roomArchivingEnabled && !metadataArchivingEnabled) {
            Log.warn("Metadata archiving must be enabled when room archiving is enabled. Overriding setting.");
            metadataArchivingEnabled = true;
        }
        idleTime = JiveGlobals.getIntProperty("conversation.idleTime", DEFAULT_IDLE_TIME) * JiveConstants.MINUTE;
        maxTime = JiveGlobals.getIntProperty("conversation.maxTime", DEFAULT_MAX_TIME) * JiveConstants.MINUTE;

        maxAge = JiveGlobals.getIntProperty("conversation.maxAge", DEFAULT_MAX_AGE) * JiveConstants.DAY;
        maxRetrievable = JiveGlobals.getIntProperty("conversation.maxRetrievable", DEFAULT_MAX_RETRIEVABLE) * JiveConstants.DAY;

        // Listen for any changes to the conversation properties.
        propertyListener = new ConversationPropertyListener();
        PropertyEventDispatcher.addListener(propertyListener);

        conversationListeners = new CopyOnWriteArraySet<ConversationListener>();

        if ( executorService != null && !executorService.isShutdown() )
        {
            executorService.shutdownNow();
        }
        executorService = Executors.newFixedThreadPool( 3, new NamedThreadFactory( "MonitorPluginArchiver", null, null, null ) );
        executorService.submit( new ConversationArchivingRunnable( conversationQueue ) );
        executorService.submit( new MessageArchivingRunnable( messageQueue ) );
        executorService.submit( new ParticipantArchivingRunnable( participantQueue ) );

        if (JiveGlobals.getProperty("conversation.maxTimeDebug") != null) {
            Log.info("Monitoring plugin max time value deleted. Must be left over from stalled userCreation plugin run.");
            JiveGlobals.deleteProperty("conversation.maxTimeDebug");
        }
        
        // Schedule a task to do conversation cleanup.
        cleanupTask = new TimerTask() {
            @Override
            public void run() {
                for (String key : conversations.keySet()) {
                    Conversation conversation = conversations.get(key);
                    long now = System.currentTimeMillis();
                    if ((now - conversation.getLastActivity().getTime() > idleTime) || (now - conversation.getStartDate().getTime() > maxTime)) {
                        removeConversation(key, conversation, new Date(now));
                    }
                }
            }
        };
        taskEngine.scheduleAtFixedRate(cleanupTask, JiveConstants.MINUTE * 5, JiveConstants.MINUTE * 5);

        // Schedule a task to do conversation purging.
        maxAgeTask = new TimerTask() {
            @Override
            public void run() {
                if (maxAge > 0) {
                    // Delete conversations older than maxAge days
                    Connection con = null;
                    PreparedStatement pstmt1 = null;
                    PreparedStatement pstmt2 = null;
                    PreparedStatement pstmt3 = null;
                    try {
                        con = DbConnectionManager.getConnection();
                        pstmt1 = con.prepareStatement(DELETE_CONVERSATION_1);
                        pstmt2 = con.prepareStatement(DELETE_CONVERSATION_2);
                        pstmt3 = con.prepareStatement(DELETE_CONVERSATION_3);
                        Date now = new Date();
                        Date maxAgeDate = new Date(now.getTime() - maxAge);
                        ArchiveSearch search = new ArchiveSearch();
                        search.setDateRangeMax(maxAgeDate);
                        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(MonitoringConstants.NAME);
                        ArchiveSearcher archiveSearcher = (ArchiveSearcher) plugin.getModule(ArchiveSearcher.class);
                        Collection<Conversation> conversations = archiveSearcher.search(search);
                        int conversationDeleted = 0;
                        for (Conversation conversation : conversations) {
                            Log.debug("Deleting: " + conversation.getConversationID() + " with date: " + conversation.getStartDate()
                                    + " older than: " + maxAgeDate);
                            pstmt1.setLong(1, conversation.getConversationID());
                            pstmt1.execute();
                            pstmt2.setLong(1, conversation.getConversationID());
                            pstmt2.execute();
                            pstmt3.setLong(1, conversation.getConversationID());
                            pstmt3.execute();
                            conversationDeleted++;
                        }
                        if (conversationDeleted > 0) {
                            Log.info("Deleted " + conversationDeleted + " conversations with date older than: " + maxAgeDate);
                        }
                    } catch (Exception e) {
                        Log.error(e.getMessage(), e);
                    } finally {
                        DbConnectionManager.closeConnection(pstmt1, con);
                        DbConnectionManager.closeConnection(pstmt2, con);
                        DbConnectionManager.closeConnection(pstmt3, con);
                    }
                }
            }
        };
        taskEngine.scheduleAtFixedRate(maxAgeTask, JiveConstants.MINUTE, JiveConstants.MINUTE);

        // Register a statistic.
        Statistic conversationStat = new Statistic() {

            public String getName() {
                return LocaleUtils.getLocalizedString("stat.conversation.name", MonitoringConstants.NAME);
            }

            public Type getStatType() {
                return Type.count;
            }

            public String getDescription() {
                return LocaleUtils.getLocalizedString("stat.conversation.desc", MonitoringConstants.NAME);
            }

            public String getUnits() {
                return LocaleUtils.getLocalizedString("stat.conversation.units", MonitoringConstants.NAME);
            }

            public double sample() {
                return getConversationCount();
            }

            public boolean isPartialSample() {
                return false;
            }
        };
        StatisticsManager.getInstance().addStatistic(CONVERSATIONS_KEY, conversationStat);
        InternalComponentManager.getInstance().addListener(this);
    }

    public void stop() {
        executorService.shutdownNow();

        cleanupTask.cancel();
        cleanupTask = null;

        // Remove the statistics.
        StatisticsManager.getInstance().removeStatistic(CONVERSATIONS_KEY);

        PropertyEventDispatcher.removeListener(propertyListener);
        propertyListener = null;

        conversationListeners.clear();
        conversationListeners = null;

        serverInfo = null;
        InternalComponentManager.getInstance().removeListener(this);
    }

    /**
     * Returns true if metadata archiving is enabled. Conversation meta-data includes the participants, start date, last activity, and the count of
     * messages sent. When archiving is enabled, all meta-data is written to the database.
     *
     * @return true if metadata archiving is enabled.
     */
    public boolean isMetadataArchivingEnabled() {
        return metadataArchivingEnabled;
    }

    /**
     * Sets whether metadata archiving is enabled. Conversation meta-data includes the participants, start date, last activity, and the count of
     * messages sent. When archiving is enabled, all meta-data is written to the database.
     *
     * @param enabled
     *            true if archiving should be enabled.
     */
    public void setMetadataArchivingEnabled(boolean enabled) {
        this.metadataArchivingEnabled = enabled;
        JiveGlobals.setProperty("conversation.metadataArchiving", Boolean.toString(enabled));
    }

    /**
     * Returns true if one-to-one chats or group chats messages are being archived.
     *
     * @return true if one-to-one chats or group chats messages are being archived.
     */
    public boolean isArchivingEnabled() {
        return isMessageArchivingEnabled() || isRoomArchivingEnabled();
    }

    /**
     * Returns true if message archiving is enabled for one-to-one chats. When enabled, all messages in one-to-one conversations are stored in the
     * database. Note: it's not possible for meta-data archiving to be disabled when message archiving is enabled; enabling message archiving
     * automatically enables meta-data archiving.
     *
     * @return true if message archiving is enabled.
     */
    public boolean isMessageArchivingEnabled() {
        return messageArchivingEnabled;
    }

    /**
     * Sets whether message archiving is enabled. When enabled, all messages in conversations are stored in the database. Note: it's not possible for
     * meta-data archiving to be disabled when message archiving is enabled; enabling message archiving automatically enables meta-data archiving.
     *
     * @param enabled
     *            true if message should be enabled.
     */
    public void setMessageArchivingEnabled(boolean enabled) {
        this.messageArchivingEnabled = enabled;
        JiveGlobals.setProperty("conversation.messageArchiving", Boolean.toString(enabled));
        // Force metadata archiving enabled.
        if (enabled) {
            this.metadataArchivingEnabled = true;
        }
    }

    /**
     * Returns true if message archiving is enabled for group chats. When enabled, all messages in group conversations are stored in the database
     * unless a list of rooms was specified in {@link #getRoomsArchived()} . Note: it's not possible for meta-data archiving to be disabled when room
     * archiving is enabled; enabling room archiving automatically enables meta-data archiving.
     *
     * @return true if room archiving is enabled.
     */
    public boolean isRoomArchivingEnabled() {
        return roomArchivingEnabled;
    }


    public boolean isRoomArchivingStanzasEnabled() {
        return roomArchivingStanzasEnabled;
    }

    /**
     * Sets whether message archiving is enabled for group chats. When enabled, all messages in group conversations are stored in the database unless
     * a list of rooms was specified in {@link #getRoomsArchived()} . Note: it's not possible for meta-data archiving to be disabled when room
     * archiving is enabled; enabling room archiving automatically enables meta-data archiving.
     *
     * @param enabled
     *            if room archiving is enabled.
     */
    public void setRoomArchivingEnabled(boolean enabled) {
        this.roomArchivingEnabled = enabled;
        JiveGlobals.setProperty("conversation.roomArchiving", Boolean.toString(enabled));
        // Force metadata archiving enabled.
        if (enabled) {
            this.metadataArchivingEnabled = true;
        }
    }
    
    public void setRoomArchivingStanzasEnabled(boolean enabled) {
        this.roomArchivingStanzasEnabled = enabled;
        JiveGlobals.setProperty("conversation.roomArchivingStanzas", Boolean.toString(enabled));
        // Force metadata archiving enabled.
    }
    /**
     * Returns list of room names whose messages will be archived. When room archiving is enabled and this list is empty then messages of all local
     * rooms will be archived. However, when name of rooms are defined in this list then only messages of those rooms will be archived.
     *
     * @return list of local room names whose messages will be archived.
     */
    public Collection<String> getRoomsArchived() {
        return roomsArchived;
    }

    /**
     * Sets list of room names whose messages will be archived. When room archiving is enabled and this list is empty then messages of all local rooms
     * will be archived. However, when name of rooms are defined in this list then only messages of those rooms will be archived.
     *
     * @param roomsArchived
     *            list of local room names whose messages will be archived.
     */
    public void setRoomsArchived(Collection<String> roomsArchived) {
        this.roomsArchived = roomsArchived;
        JiveGlobals.setProperty("conversation.roomsArchived", StringUtils.collectionToString(roomsArchived));
    }

    /**
     * Returns the number of minutes a conversation can be idle before it's ended.
     *
     * @return the conversation idle time.
     */
    public int getIdleTime() {
        return (int) (idleTime / JiveConstants.MINUTE);
    }

    /**
     * Sets the number of minutes a conversation can be idle before it's ended.
     *
     * @param idleTime
     *            the max number of minutes a conversation can be idle before it's ended.
     * @throws IllegalArgumentException
     *             if idleTime is less than 1.
     */
    public void setIdleTime(int idleTime) {
        if (idleTime < 1) {
            throw new IllegalArgumentException("Idle time less than 1 is not valid: " + idleTime);
        }
        JiveGlobals.setProperty("conversation.idleTime", Integer.toString(idleTime));
        this.idleTime = idleTime * JiveConstants.MINUTE;
    }

    /**
     * Returns the maximum number of minutes a conversation can last before it's ended. Any additional messages between the participants in the chat
     * will be associated with a new conversation.
     *
     * @return the maximum number of minutes a conversation can last.
     */
    public int getMaxTime() {
        return (int) (maxTime / JiveConstants.MINUTE);
    }

    /**
     * Sets the maximum number of minutes a conversation can last before it's ended. Any additional messages between the participants in the chat will
     * be associated with a new conversation.
     *
     * @param maxTime
     *            the maximum number of minutes a conversation can last.
     * @throws IllegalArgumentException
     *             if maxTime is less than 1.
     */
    public void setMaxTime(int maxTime) {
        if (maxTime < 1) {
            throw new IllegalArgumentException("Max time less than 1 is not valid: " + maxTime);
        }
        JiveGlobals.setProperty("conversation.maxTime", Integer.toString(maxTime));
        this.maxTime = maxTime * JiveConstants.MINUTE;
    }

    public int getMaxAge() {
        return (int) (maxAge / JiveConstants.DAY);
    }

    public void setMaxAge(int maxAge) {
        if (maxAge < 0) {
            throw new IllegalArgumentException("Max age less than 0 is not valid: " + maxAge);
        }
        JiveGlobals.setProperty("conversation.maxAge", Integer.toString(maxAge));
        this.maxAge = maxAge * JiveConstants.DAY;
    }

    public int getMaxRetrievable() {
        return (int) (maxRetrievable / JiveConstants.DAY);
    }

    public void setMaxRetrievable(int maxRetrievable) {
        if (maxRetrievable < 0) {
            throw new IllegalArgumentException("Max retrievable less than 0 is not valid: " + maxRetrievable);
        }
        JiveGlobals.setProperty("conversation.maxRetrievable", Integer.toString(maxRetrievable));
        this.maxRetrievable = maxRetrievable * JiveConstants.DAY;
    }

    public ConversationEventsQueue getConversationEventsQueue() {
        return conversationEventsQueue;
    }

    /**
     * Returns the count of active conversations.
     *
     * @return the count of active conversations.
     */
    public int getConversationCount() {
        if (ClusterManager.isSeniorClusterMember()) {
            return conversations.size();
        }
        return (Integer) CacheFactory.doSynchronousClusterTask(new GetConversationCountTask(), ClusterManager.getSeniorClusterMember().toByteArray());
    }

    /**
     * Returns a conversation by ID.
     *
     * @param conversationID
     *            the ID of the conversation.
     * @return the conversation.
     * @throws NotFoundException
     *             if the conversation could not be found.
     */
    public Conversation getConversation(long conversationID) throws NotFoundException {
        if (ClusterManager.isSeniorClusterMember()) {
            // Search through the currently active conversations.
            for (Conversation conversation : conversations.values()) {
                if (conversation.getConversationID() == conversationID) {
                    return conversation;
                }
            }
            // Otherwise, it might be an archived conversation, so attempt to load it.
            return new Conversation(this, conversationID);
        } else {
            // Get this info from the senior cluster member when running in a cluster
            Conversation conversation = (Conversation) CacheFactory.doSynchronousClusterTask(new GetConversationTask(conversationID), ClusterManager
                    .getSeniorClusterMember().toByteArray());
            if (conversation == null) {
                throw new NotFoundException("Conversation not found: " + conversationID);
            }
            return conversation;
        }
    }

    /**
     * Returns the set of active conversations.
     *
     * @return the active conversations.
     */
    public Collection<Conversation> getConversations() {
        if (ClusterManager.isSeniorClusterMember()) {
            List<Conversation> conversationList = new ArrayList<Conversation>(conversations.values());
            // Sort the conversations by creation date.
            Collections.sort(conversationList, new Comparator<Conversation>() {
                public int compare(Conversation c1, Conversation c2) {
                    return c1.getStartDate().compareTo(c2.getStartDate());
                }
            });
            return conversationList;
        } else {
            // Get this info from the senior cluster member when running in a cluster
            return (Collection<Conversation>) CacheFactory.doSynchronousClusterTask(new GetConversationsTask(), ClusterManager
                    .getSeniorClusterMember().toByteArray());
        }
    }

    /**
     * Returns the total number of conversations that have been archived to the database. The archived conversation may only be the meta-data, or it
     * might include messages as well if message archiving is turned on.
     *
     * @return the total number of archived conversations.
     */
    public int getArchivedConversationCount() {
        int conversationCount = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CONVERSATION_COUNT);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                conversationCount = rs.getInt(1);
            }
        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return conversationCount;
    }

    /**
     * Returns the total number of messages that have been archived to the database.
     *
     * @return the total number of archived messages.
     */
    public int getArchivedMessageCount() {
        int messageCount = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(MESSAGE_COUNT);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                messageCount = rs.getInt(1);
            }
        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return messageCount;
    }

    /**
     * Adds a conversation listener, which will be notified of newly created conversations, conversations ending, and updates to conversations.
     *
     * @param listener
     *            the conversation listener.
     */
    public void addConversationListener(ConversationListener listener) {
        conversationListeners.add(listener);
    }

    /**
     * Removes a conversation listener.
     *
     * @param listener
     *            the conversation listener.
     */
    public void removeConversationListener(ConversationListener listener) {
        conversationListeners.remove(listener);
    }

    /**
     * Processes an incoming message of a one-to-one chat. The message will mapped to a conversation and then queued for storage if archiving is
     * turned on.
     *
     * @param sender
     *            sender of the message.
     * @param receiver
     *            receiver of the message.
     * @param body
     *            body of the message.
     * @param stanza
     * 			  String encoded message stanza
     * @param date
     *            date when the message was sent.
     */
    void processMessage(JID sender, JID receiver, String body, String stanza, Date date) {
        String conversationKey = getConversationKey(sender, receiver);
        synchronized (conversationKey.intern()) {
            Conversation conversation = conversations.get(conversationKey);
            // Create a new conversation if necessary.
            if (conversation == null) {
                Collection<JID> participants = new ArrayList<JID>(2);
                participants.add(sender);
                participants.add(receiver);
                XMPPServer server = XMPPServer.getInstance();
                // Check to see if this is an external conversation; i.e. one of the participants
                // is on a different server. We can use XOR since we know that both JID's can't
                // be external.
                boolean external = isExternal(server, sender) ^ isExternal(server, receiver);
                // Make sure that the user joined the conversation before a message was received
                Date start = new Date(date.getTime() - 1);
                conversation = new Conversation(this, participants, external, start);
                conversations.put(conversationKey, conversation);
                // Notify listeners of the newly created conversation.
                for (ConversationListener listener : conversationListeners) {
                    listener.conversationCreated(conversation);
                }
            }
            // Check to see if the current conversation exceeds either the max idle time
            // or max conversation time.
            else if ((date.getTime() - conversation.getLastActivity().getTime() > idleTime)
                    || (date.getTime() - conversation.getStartDate().getTime() > maxTime)) {
                removeConversation(conversationKey, conversation, conversation.getLastActivity());

                Collection<JID> participants = new ArrayList<JID>(2);
                participants.add(sender);
                participants.add(receiver);
                XMPPServer server = XMPPServer.getInstance();
                // Check to see if this is an external conversation; i.e. one of the participants
                // is on a different server. We can use XOR since we know that both JID's can't
                // be external.
                boolean external = isExternal(server, sender) ^ isExternal(server, receiver);
                // Make sure that the user joined the conversation before a message was received
                Date start = new Date(date.getTime() - 1);
                conversation = new Conversation(this, participants, external, start);
                conversations.put(conversationKey, conversation);
                // Notify listeners of the newly created conversation.
                for (ConversationListener listener : conversationListeners) {
                    listener.conversationCreated(conversation);
                }
            }
            // Record the newly received message.
            conversation.messageReceived(sender, date);
            if (metadataArchivingEnabled) {
                conversationQueue.add(new ArchiveCandidate<>(conversation));
            }
            if (messageArchivingEnabled) {
                if (body != null) {
                    /* OF-677 - Workaround to prevent null messages being archived */
                    messageQueue.add(new ArchiveCandidate<>( new ArchivedMessage(conversation.getConversationID(), sender, receiver, date, body, stanza, false) ));
                }
            }
            // Notify listeners of the conversation update.
            for (ConversationListener listener : conversationListeners) {
                listener.conversationUpdated(conversation, date);
            }
        }
    }

    /**
     * Processes an incoming message sent to a room. The message will mapped to a conversation and then queued for storage if archiving is turned on.
     *
     * @param roomJID
     *            the JID of the room where the group conversation is taking place.
     * @param sender
     *            the JID of the entity that sent the message.
     * @param nickname
     *            nickname of the user in the room when the message was sent.
     * @param body
     *            the message sent to the room.
     * @param date
     *            date when the message was sent.
     */
    void processRoomMessage(JID roomJID, JID sender, String nickname, String body, String stanza, Date date) {
        String conversationKey = getRoomConversationKey(roomJID);
        synchronized (conversationKey.intern()) {
            Conversation conversation = conversations.get(conversationKey);
            // Create a new conversation if necessary.
            if (conversation == null) {
                // Make sure that the user joined the conversation before a message was received
                Date start = new Date(date.getTime() - 1);
                conversation = new Conversation(this, roomJID, false, start);
                conversations.put(conversationKey, conversation);
                // Notify listeners of the newly created conversation.
                for (ConversationListener listener : conversationListeners) {
                    listener.conversationCreated(conversation);
                }
            }
            // Check to see if the current conversation exceeds either the max idle time
            // or max conversation time.
            else if ((date.getTime() - conversation.getLastActivity().getTime() > idleTime)
                    || (date.getTime() - conversation.getStartDate().getTime() > maxTime)) {
                removeConversation(conversationKey, conversation, conversation.getLastActivity());
                // Make sure that the user joined the conversation before a message was received
                Date start = new Date(date.getTime() - 1);
                conversation = new Conversation(this, roomJID, false, start);
                conversations.put(conversationKey, conversation);
                // Notify listeners of the newly created conversation.
                for (ConversationListener listener : conversationListeners) {
                    listener.conversationCreated(conversation);
                }
            }
            // Record the newly received message.
            conversation.messageReceived(sender, date);
            if (metadataArchivingEnabled) {
                conversationQueue.add(new ArchiveCandidate<>( conversation ));
            }
            if (roomArchivingEnabled && (roomsArchived.isEmpty() || roomsArchived.contains(roomJID.getNode()))) {
                JID jid = new JID(roomJID + "/" + nickname);
                if (body != null) {
                    /* OF-677 - Workaround to prevent null messages being archived */
                    messageQueue.add(new ArchiveCandidate<>( new ArchivedMessage(conversation.getConversationID(), sender, jid, date, body, roomArchivingStanzasEnabled ? stanza : "", false)) );
                }
            }
            // Notify listeners of the conversation update.
            for (ConversationListener listener : conversationListeners) {
                listener.conversationUpdated(conversation, date);
            }
        }
    }

    /**
     * Notification message indicating that a user joined a groupchat conversation. If no groupchat conversation was taking place in the specified
     * room then ignore this event.
     * <p>
     * <p/>
     * Eventually, when a new conversation will start in the room and if this user is still in the room then the new conversation will detect this
     * user and mark like if the user joined the converstion from the beginning.
     *
     * @param room
     *            the room where the user joined.
     * @param user
     *            the user that joined the room.
     * @param nickname
     *            nickname of the user in the room.
     * @param date
     *            date when the user joined the group coversation.
     */
    void joinedGroupConversation(JID room, JID user, String nickname, Date date) {
        Conversation conversation = getRoomConversation(room);
        if (conversation != null) {
            conversation.participantJoined(user, nickname, date.getTime());
        }
    }

    /**
     * Notification message indicating that a user left a groupchat conversation. If no groupchat conversation was taking place in the specified room
     * then ignore this event.
     *
     * @param room
     *            the room where the user left.
     * @param user
     *            the user that left the room.
     * @param date
     *            date when the user left the group coversation.
     */
    void leftGroupConversation(JID room, JID user, Date date) {
        Conversation conversation = getRoomConversation(room);
        if (conversation != null) {
            conversation.participantLeft(user, date.getTime());
        }
    }

    void roomConversationEnded(JID room, Date date) {
        Conversation conversation = getRoomConversation(room);
        if (conversation != null) {
            removeConversation(room.toString(), conversation, date);
        }
    }

    private void removeConversation(String key, Conversation conversation, Date date) {
        conversations.remove(key);
        // Notify conversation that it has ended
        conversation.conversationEnded(date);
        // Notify listeners of the conversation ending.
        for (ConversationListener listener : conversationListeners) {
            listener.conversationEnded(conversation);
        }
    }

    /**
     * Returns the group conversation taking place in the specified room or <tt>null</tt> if none.
     *
     * @param room
     *            JID of the room.
     * @return the group conversation taking place in the specified room or null if none.
     */
    private Conversation getRoomConversation(JID room) {
        String conversationKey = room.toString();
        return conversations.get(conversationKey);
    }

    private boolean isExternal(XMPPServer server, JID jid) {
        return !server.isLocal(jid) || gateways.contains(jid.getDomain());
    }

    /**
     * Returns true if the specified message should be processed by the conversation manager. Only messages between two users, group chats, or
     * gateways are processed.
     *
     * @param message
     *            the message to analyze.
     * @return true if the specified message should be processed by the conversation manager.
     */
    boolean isConversation(Message message) {
        if (Message.Type.normal == message.getType() || Message.Type.chat == message.getType()) {
            // TODO: how should conversations with components on other servers be handled?
            return isConversationJID(message.getFrom()) && isConversationJID(message.getTo());
        }
        return false;
    }

    /**
     * Returns true if the specified JID should be recorded in a conversation.
     *
     * @param jid
     *            the JID.
     * @return true if the JID should be recorded in a conversation.
     */
    private boolean isConversationJID(JID jid) {
        // Ignore conversations when there is no jid
        if (jid == null) {
            return false;
        }
        XMPPServer server = XMPPServer.getInstance();
        if (jid.getNode() == null) {
            return false;
        }

        // Always accept local JIDs or JIDs related to gateways
        // (this filters our components, MUC, pubsub, etc. except gateways).
        if (server.isLocal(jid) || gateways.contains(jid.getDomain())) {
            return true;
        }

        // If not a local JID, always record it.
        if (!jid.getDomain().endsWith(serverInfo.getXMPPDomain())) {
            return true;
        }

        // Otherwise return false.
        return false;
    }

    /**
     * Returns a unique key for a coversation between two JID's. The order of two JID parameters is irrelevant; the same key will be returned.
     *
     * @param jid1
     *            the first JID.
     * @param jid2
     *            the second JID.
     * @return a unique key.
     */
    String getConversationKey(JID jid1, JID jid2) {
        StringBuilder builder = new StringBuilder();
        if (jid1.compareTo(jid2) < 0) {
            builder.append(jid1.toBareJID()).append("_").append(jid2.toBareJID());
        } else {
            builder.append(jid2.toBareJID()).append("_").append(jid1.toBareJID());
        }
        return builder.toString();
    }

    String getRoomConversationKey(JID roomJID) {
        return roomJID.toString();
    }

    public void componentInfoReceived(IQ iq) {
        // Check if the component is a gateway
        boolean gatewayFound = false;
        Element childElement = iq.getChildElement();
        for (Iterator<Element> it = childElement.elementIterator("identity"); it.hasNext();) {
            Element identity = it.next();
            if ("gateway".equals(identity.attributeValue("category"))) {
                gatewayFound = true;
            }
        }
        // If component is a gateway then keep track of the component
        if (gatewayFound) {
            gateways.add(iq.getFrom().getDomain());
        }
    }

    public void componentRegistered(JID componentJID) {
        // Do nothing
    }

    public void componentUnregistered(JID componentJID) {
        // Remove stored information about this component
        gateways.remove(componentJID.getDomain());
    }

    void queueParticipantLeft(Conversation conversation, JID user, ConversationParticipation participation) {
        RoomParticipant updatedParticipant = new RoomParticipant();
        updatedParticipant.conversationID = conversation.getConversationID();
        updatedParticipant.user = user;
        updatedParticipant.joined = participation.getJoined();
        updatedParticipant.left = participation.getLeft();
        participantQueue.add(new ArchiveCandidate<>( updatedParticipant ));
    }

    /**
     * A to-be-archived entity.
     *
     * Note that the ordering imposed by the Comparable implementation is not consistent with equals, and serves only
     * to order instances by their creation timestamp.
     */
    private static class ArchiveCandidate<E> implements Comparable<ArchiveCandidate<E>> {
        private final Date creation = new Date();

        private final E element;

        public ArchiveCandidate( E element ) {
            if ( element == null ) {
                throw new IllegalArgumentException( "Argument 'element' cannot be null." );
            }
            this.element = element;
        }

        public Date createdAt()
        {
            return creation;
        }

        public E getElement()
        {
            return element;
        }

        @Override
        public int compareTo( ArchiveCandidate<E> o )
        {
            return creation.compareTo( o.creation );
        }
    }

    /**
     * Returns true if none of the queues hold data that was delivered before the provided argument.
     *
     * This method is intended to be used to determine if it's safe to construct an answer (based on database
     * content) to a request for archived data. Such response should only be generated after all data that was
     * queued before the request arrived has been written to the database.
     *
     * This method performs a cluster-wide check, unlike {@link #hasLocalNodeWrittenAllDataBefore(Date)}.
     *
     * @param date A date (cannot be null).
     * @return false if any of the the queues contain work that was created before the provided date, otherwise true.
     */
    public boolean hasWrittenAllDataBefore( Date date )
    {
        final boolean localNode = hasLocalNodeWrittenAllDataBefore( date );
        if ( !localNode )
        {
            return false;
        }

        // Check all other cluster nodes.
        final Collection<Object> objects = CacheFactory.doSynchronousClusterTask( new HasWrittenAllDataTask( date ), false );
        for ( final Object object : objects )
        {
            if ( !( (Boolean) object ) ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if none of the queues hold data that was delivered before the provided argument.
     *
     * This method is intended to be used to determine if it's safe to construct an answer (based on database
     * content) to a request for archived data. Such response should only be generated after all data that was
     * queued before the request arrived has been written to the database.
     *
     * This method performs a check on the local cluster-node only, unlike {@link #hasWrittenAllDataBefore(Date)}.
     *
     * @param date A date (cannot be null).
     * @return false if any of the the queues contain work that was created before the provided date, otherwise true.
     */
    public boolean hasLocalNodeWrittenAllDataBefore( Date date )
    {
        if ( date == null )
        {
            throw new IllegalArgumentException( "Argument 'date' cannot be null." );
        }
        final ArchiveCandidate c = conversationQueue.peek();
        final ArchiveCandidate m = messageQueue.peek();
        final ArchiveCandidate p = participantQueue.peek();
        return ( c == null || c.creation.after( date ) )
            && ( m == null || m.creation.after( date ) )
            && ( p == null || p.creation.after( date ) );
    }

    /**
     * An abstract runnable that adds to-be-archived data to the database.
     *
     * This implementation is designed to reduce the work load on the database, by batching work where possible, without
     * severely delaying database writes.
     *
     * This implementation acts as a consumer (in context of the producer-consumer design pattern), where the queue that
     * is used to relay work from both processes is passed as an argument to the constructor of this class.
     *
     * @author Guus der Kinderen, guus.der.kinderen@gmail.com
     */
    private static abstract class ArchivingRunnable<E> implements Runnable
    {
        // Do not add more than this amount of queries in a batch.
        final int maxWorkQueueSize = 500; // TODO make this value configurable.

        // Do not delay longer than this amount of milliseconds before storing data in the database.
        final long maxPurgeInterval = 1000; // TODO make this value configurable.

        // Maximum amount of milliseconds to wait for 'more' work to arrive, before committing the batch.
        final long gracePeriod = 50; // TODO make this value configurable.

        // Reference to the queue in which work is produced.
        final PriorityBlockingQueue<ArchiveCandidate<E>> queue;

        ArchivingRunnable( PriorityBlockingQueue<ArchiveCandidate<E>> queue )
        {
            if ( queue == null )
            {
                throw new IllegalArgumentException( "Argument 'queue' cannot be null." );
            }
            this.queue = queue;
        }

        public void run()
        {
            boolean running = true;

            // This loop is designed to write data to be stored in the database without much delay, while at the same
            // time allowing for batching of work that's produced at roughly the same time (which improves performance).
            while ( running )
            {
                // The batch of work for this iteration.
                final List<ArchiveCandidate<E>> workQueue = new ArrayList<>();

                try
                {
                    // Blocks until work is produced.
                    ArchiveCandidate<E> work = queue.take();
                    workQueue.add( work );

                    // Continue filling up this batch as long as new archive candidates can be retrieved pretty much
                    // instantaneously, but don't take longer than the maximum allowed purge interval (this is intended
                    // to make sure that the database content is updated regularly)
                    final long start = System.currentTimeMillis();
                    while ( ( workQueue.size() < maxWorkQueueSize ) // Don't allow the batch to grow to big.
                        && ( System.currentTimeMillis() - start < maxPurgeInterval - gracePeriod ) // Don't take to long between commits.
                        && ( ( work = queue.poll( gracePeriod, TimeUnit.MILLISECONDS ) ) != null ) )
                    {
                        workQueue.add( work );
                    }
                }
                catch ( InterruptedException e )
                {
                    // Causes the thread to stop.
                    running = false;
                }

                // Store all produced work in the database.
                store( workQueue );
            }
        }

        abstract void store( List<ArchiveCandidate<E>> workQueue );
    }

    /**
     * Stores Conversations in the database.
     */
    private static class ConversationArchivingRunnable extends ArchivingRunnable<Conversation>
    {
        ConversationArchivingRunnable( PriorityBlockingQueue<ArchiveCandidate<Conversation>> queue )
        {
            super( queue );
        }

        protected void store( List<ArchiveCandidate<Conversation>> workQueue )
        {
            if ( workQueue.isEmpty() )
            {
                return;
            }

            Connection con = null;
            PreparedStatement pstmt = null;

            try
            {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(UPDATE_CONVERSATION);

                for ( final ArchiveCandidate<Conversation> work : workQueue )
                {
                    pstmt.setLong( 1, work.getElement().getLastActivity().getTime() );
                    pstmt.setInt( 2, work.getElement().getMessageCount() );
                    pstmt.setLong( 3, work.getElement().getConversationID() );
                    if ( DbConnectionManager.isBatchUpdatesSupported() )
                    {
                        pstmt.addBatch();
                    }
                    else
                    {
                        pstmt.execute();
                    }
                }

                if ( DbConnectionManager.isBatchUpdatesSupported() )
                {
                    pstmt.executeBatch();
                }
            }
            catch ( Exception e )
            {
                Log.error( "Unable to archive conversation data!", e );
            }
            finally
            {
                DbConnectionManager.closeConnection( pstmt, con );
            }
        }
    }

    /**
     * Stores Messages in the database.
     */
    private class MessageArchivingRunnable extends ArchivingRunnable<ArchivedMessage>
    {
        MessageArchivingRunnable( PriorityBlockingQueue<ArchiveCandidate<ArchivedMessage>> queue )
        {
            super( queue );
        }

        @Override
        void store( List<ArchiveCandidate<ArchivedMessage>> workQueue )
        {
            if ( workQueue.isEmpty() )
            {
                return;
            }

            Connection con = null;
            PreparedStatement pstmt = null;

            try
            {
                int msgCount = getArchivedMessageCount();

                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(INSERT_MESSAGE);

                for ( final ArchiveCandidate<ArchivedMessage> work : workQueue )
                {
                    pstmt.setInt(1, ++msgCount);
                    pstmt.setLong(2, work.getElement().getConversationID());
                    pstmt.setString(3, work.getElement().getFromJID().toBareJID());
                    pstmt.setString(4, work.getElement().getFromJID().getResource());
                    pstmt.setString(5, work.getElement().getToJID().toBareJID());
                    pstmt.setString(6, work.getElement().getToJID().getResource());
                    pstmt.setLong(7, work.getElement().getSentDate().getTime());
                    DbConnectionManager.setLargeTextField(pstmt, 8, work.getElement().getBody());
                    DbConnectionManager.setLargeTextField(pstmt, 9, work.getElement().getStanza());

                    if ( DbConnectionManager.isBatchUpdatesSupported() )
                    {
                        pstmt.addBatch();
                    }
                    else
                    {
                        pstmt.execute();
                    }
                }

                if ( DbConnectionManager.isBatchUpdatesSupported() )
                {
                    pstmt.executeBatch();
                }
            }
            catch ( Exception e )
            {
                Log.error( "Unable to archive message data!", e );
            }
            finally
            {
                DbConnectionManager.closeConnection( pstmt, con );
            }
        }
    }

    /**
     * Stores Participants in the database.
     */
    private static class ParticipantArchivingRunnable extends ArchivingRunnable<RoomParticipant>
    {
        ParticipantArchivingRunnable( PriorityBlockingQueue<ArchiveCandidate<RoomParticipant>> queue )
        {
            super( queue );
        }

        protected void store( List<ArchiveCandidate<RoomParticipant>> workQueue )
        {
            if ( workQueue.isEmpty() )
            {
                return;
            }

            Connection con = null;
            PreparedStatement pstmt = null;

            try
            {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement( UPDATE_PARTICIPANT );

                for ( final ArchiveCandidate<RoomParticipant> work : workQueue )
                {
                    pstmt.setLong(1, work.getElement().left.getTime());
                    pstmt.setLong(2, work.getElement().conversationID);
                    pstmt.setString(3, work.getElement().user.toBareJID());
                    pstmt.setString(4, work.getElement().user.getResource() == null ? " " : work.getElement().user.getResource());
                    pstmt.setLong(5, work.getElement().joined.getTime());
                    if ( DbConnectionManager.isBatchUpdatesSupported() )
                    {
                        pstmt.addBatch();
                    }
                    else
                    {
                        pstmt.execute();
                    }
                }

                if ( DbConnectionManager.isBatchUpdatesSupported() )
                {
                    pstmt.executeBatch();
                }
            }
            catch ( Exception e )
            {
                Log.error( "Unable to archive participant data!", e );
            }
            finally
            {
                DbConnectionManager.closeConnection( pstmt, con );
            }
        }
    }

    /**
     * A PropertyEventListener that tracks updates to Jive properties that are related to conversation tracking and archiving.
     */
    private class ConversationPropertyListener implements PropertyEventListener {

        public void propertySet(String property, Map<String, Object> params) {
            if (property.equals("conversation.metadataArchiving")) {
                String value = (String) params.get("value");
                metadataArchivingEnabled = Boolean.valueOf(value);
            } else if (property.equals("conversation.messageArchiving")) {
                String value = (String) params.get("value");
                messageArchivingEnabled = Boolean.valueOf(value);
                // Force metadata archiving enabled on if message archiving on.
                if (messageArchivingEnabled) {
                    metadataArchivingEnabled = true;
                }
            } else if (property.equals("conversation.roomArchiving")) {
                String value = (String) params.get("value");
                roomArchivingEnabled = Boolean.valueOf(value);
                // Force metadata archiving enabled on if message archiving on.
                if (roomArchivingEnabled) {
                    metadataArchivingEnabled = true;
                }
            } else if( property.equals( "conversation.roomArchivingStanzas" ) ) {
                String value = (String) params.get( "value" );
                roomArchivingStanzasEnabled = Boolean.valueOf( value );
            } else if (property.equals("conversation.roomsArchived")) {
                String value = (String) params.get("value");
                roomsArchived = StringUtils.stringToCollection(value);
            } else if (property.equals("conversation.idleTime")) {
                String value = (String) params.get("value");
                try {
                    idleTime = Integer.parseInt(value) * JiveConstants.MINUTE;
                } catch (Exception e) {
                    Log.error(e.getMessage(), e);
                    idleTime = DEFAULT_IDLE_TIME * JiveConstants.MINUTE;
                }
            } else if (property.equals("conversation.maxTime")) {
                String value = (String) params.get("value");
                try {
                    maxTime = Integer.parseInt(value) * JiveConstants.MINUTE;
                } catch (Exception e) {
                    Log.error(e.getMessage(), e);
                    maxTime = DEFAULT_MAX_TIME * JiveConstants.MINUTE;
                }
            } else if (property.equals("conversation.maxRetrievable")) {
                String value = (String) params.get("value");
                try {
                    maxRetrievable = Integer.parseInt(value) * JiveConstants.DAY;
                } catch (Exception e) {
                    Log.error(e.getMessage(), e);
                    maxRetrievable = DEFAULT_MAX_RETRIEVABLE * JiveConstants.DAY;
                }
            } else if (property.equals("conversation.maxAge")) {
                String value = (String) params.get("value");
                try {
                    maxAge = Integer.parseInt(value) * JiveConstants.DAY;
                } catch (Exception e) {
                    Log.error(e.getMessage(), e);
                    maxAge = DEFAULT_MAX_AGE * JiveConstants.DAY;
                }
            } else if (property.equals("conversation.maxTimeDebug")) {
                String value = (String) params.get("value");
                try {
                    Log.info("Monitoring plugin max time overridden (as used by userCreation plugin)");
                    maxTime = Integer.parseInt(value);
                } catch (Exception e) {
                    Log.error(e.getMessage(), e);
                    Log.info("Monitoring plugin max time reset back to " + DEFAULT_MAX_TIME + " minutes");
                    maxTime = DEFAULT_MAX_TIME * JiveConstants.MINUTE;
                }
            }
        }

        public void propertyDeleted(String property, Map<String, Object> params) {
            if (property.equals("conversation.metadataArchiving")) {
                metadataArchivingEnabled = true;
            } else if (property.equals("conversation.messageArchiving")) {
                messageArchivingEnabled = false;
            } else if (property.equals("conversation.roomArchiving")) {
                roomArchivingEnabled = false;
            } else if (property.equals("conversation.roomArchivingStanzas")) {
                roomArchivingStanzasEnabled = false;
            } else if (property.equals("conversation.roomsArchived")) {
                roomsArchived = Collections.emptyList();
            } else if (property.equals("conversation.idleTime")) {
                idleTime = DEFAULT_IDLE_TIME * JiveConstants.MINUTE;
            } else if (property.equals("conversation.maxTime")) {
                maxTime = DEFAULT_MAX_TIME * JiveConstants.MINUTE;
            } else if (property.equals("conversation.maxAge")) {
                maxAge = DEFAULT_MAX_AGE * JiveConstants.DAY;
            } else if (property.equals("conversation.maxRetrievable")) {
                maxRetrievable = DEFAULT_MAX_RETRIEVABLE * JiveConstants.DAY;
            }  else if (property.equals("conversation.maxTimeDebug")) {
                Log.info("Monitoring plugin max time reset back to " + DEFAULT_MAX_TIME + " minutes");
                maxTime = DEFAULT_MAX_TIME * JiveConstants.MINUTE;
            }
        }

        public void xmlPropertySet(String property, Map<String, Object> params) {
            // Ignore.
        }

        public void xmlPropertyDeleted(String property, Map<String, Object> params) {
            // Ignore.
        }
    }

    private static class RoomParticipant {
        private long conversationID = -1;
        private JID user;
        private Date joined;
        private Date left;
    }
}
