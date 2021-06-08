/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.pubsub;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.DbConnectionManager.DatabaseType;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * A manager responsible for ensuring node persistence.
 *
 * @author Matt Tucker
 */
public class DefaultPubSubPersistenceProvider implements PubSubPersistenceProvider {

    private static final Logger log = LoggerFactory.getLogger( DefaultPubSubPersistenceProvider.class);

    private static final String PERSISTENT_NODES = "SELECT DISTINCT serviceID, nodeID, maxItems " +
    		"FROM ofPubsubNode WHERE leaf=1 AND persistItems=1 AND maxItems > 0";
    
    private static final String PURGE_FOR_SIZE =
    		"DELETE FROM ofPubsubItem LEFT JOIN " +
			"(SELECT id FROM ofPubsubItem WHERE serviceID=? AND nodeID=? " +
			"ORDER BY creationDate DESC LIMIT ?) AS noDelete " +
			"ON ofPubsubItem.id = noDelete.id WHERE noDelete.id IS NULL AND " +
			"ofPubsubItem.serviceID = ? AND nodeID = ?";

    private static final String PURGE_FOR_SIZE_ORACLE =
            "DELETE from ofPubsubItem where id in " +
            "(select ofPubsubItem.id FROM ofPubsubItem LEFT JOIN " +
            "(SELECT id FROM ofPubsubItem WHERE serviceID=? AND nodeID=? " +
            "ORDER BY creationDate DESC FETCH FIRST ? ROWS ONLY) noDelete " +
            "ON ofPubsubItem.id = noDelete.id WHERE noDelete.id IS NULL " +
            "AND ofPubsubItem.serviceID = ? AND nodeID = ?)";

    private static final String PURGE_FOR_SIZE_SQLSERVER =
            "DELETE FROM ofPubsubItem WHERE serviceID=? AND nodeID=? AND id NOT IN " +
            "(SELECT TOP(?) id FROM ofPubsubItem WHERE serviceID=? AND nodeID=? ORDER BY creationDate DESC)";

    private static final String PURGE_FOR_SIZE_MYSQL =
		"DELETE ofPubsubItem FROM ofPubsubItem LEFT JOIN " +
			"(SELECT id FROM ofPubsubItem WHERE serviceID=? AND nodeID=? " +
			"ORDER BY creationDate DESC LIMIT ?) AS noDelete " +
			"ON ofPubsubItem.id = noDelete.id WHERE noDelete.id IS NULL AND " +
			"ofPubsubItem.serviceID = ? AND nodeID = ?";

    private static final String PURGE_FOR_SIZE_POSTGRESQL = 
    		"DELETE from ofPubsubItem where id in " +
    		"(select ofPubsubItem.id FROM ofPubsubItem LEFT JOIN " +
    		"(SELECT id FROM ofPubsubItem WHERE serviceID=? AND nodeID=? " +
    		"ORDER BY creationDate DESC LIMIT ?) AS noDelete " +
    		"ON ofPubsubItem.id = noDelete.id WHERE noDelete.id IS NULL " +
    		"AND ofPubsubItem.serviceID = ? AND nodeID = ?)";

	private static final String PURGE_FOR_SIZE_HSQLDB = "DELETE FROM ofPubsubItem WHERE serviceID=? AND nodeID=? AND id NOT IN "
			+ "(SELECT id FROM ofPubsubItem WHERE serviceID=? AND nodeID=? ORDER BY creationDate DESC LIMIT ?)";

	private static final String LOAD_NODES =
            "SELECT nodeID, leaf, creationDate, modificationDate, parent, deliverPayloads, " +
            "maxPayloadSize, persistItems, maxItems, notifyConfigChanges, notifyDelete, " +
            "notifyRetract, presenceBased, sendItemSubscribe, publisherModel, " +
            "subscriptionEnabled, configSubscription, accessModel, payloadType, " +
            "bodyXSLT, dataformXSLT, creator, description, language, name, " +
            "replyPolicy, associationPolicy, maxLeafNodes FROM ofPubsubNode " +
 "WHERE serviceID=?";

	private static final String LOAD_NODE = LOAD_NODES + " AND nodeID=?";

    private static final String UPDATE_NODE =
            "UPDATE ofPubsubNode SET modificationDate=?, parent=?, deliverPayloads=?, " +
            "maxPayloadSize=?, persistItems=?, maxItems=?, " +
            "notifyConfigChanges=?, notifyDelete=?, notifyRetract=?, presenceBased=?, " +
            "sendItemSubscribe=?, publisherModel=?, subscriptionEnabled=?, configSubscription=?, " +
            "accessModel=?, payloadType=?, bodyXSLT=?, dataformXSLT=?, description=?, " +
            "language=?, name=?, replyPolicy=?, associationPolicy=?, maxLeafNodes=? " +
            "WHERE serviceID=? AND nodeID=?";
    private static final String ADD_NODE =
            "INSERT INTO ofPubsubNode (serviceID, nodeID, leaf, creationDate, modificationDate, " +
            "parent, deliverPayloads, maxPayloadSize, persistItems, maxItems, " +
            "notifyConfigChanges, notifyDelete, notifyRetract, presenceBased, " +
            "sendItemSubscribe, publisherModel, subscriptionEnabled, configSubscription, " +
            "accessModel, payloadType, bodyXSLT, dataformXSLT, creator, description, " +
            "language, name, replyPolicy, associationPolicy, maxLeafNodes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String DELETE_NODE =
            "DELETE FROM ofPubsubNode WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_NODES_JIDS =
            "SELECT nodeID, jid, associationType FROM ofPubsubNodeJIDs WHERE serviceID=?";
	private static final String LOAD_NODE_JIDS = "SELECT nodeID, jid, associationType FROM ofPubsubNodeJIDs WHERE serviceID=? AND nodeID=?";
	private static final String ADD_NODE_JIDS =
            "INSERT INTO ofPubsubNodeJIDs (serviceID, nodeID, jid, associationType) " +
            "VALUES (?,?,?,?)";
    private static final String DELETE_NODE_JIDS =
            "DELETE FROM ofPubsubNodeJIDs WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_NODES_GROUPS =
            "SELECT nodeID, rosterGroup FROM ofPubsubNodeGroups WHERE serviceID=?";
	private static final String LOAD_NODE_GROUPS = "SELECT nodeID, rosterGroup FROM ofPubsubNodeGroups WHERE serviceID=? AND nodeID=?";
    private static final String ADD_NODE_GROUPS =
            "INSERT INTO ofPubsubNodeGroups (serviceID, nodeID, rosterGroup) " +
            "VALUES (?,?,?)";
    private static final String DELETE_NODE_GROUPS =
            "DELETE FROM ofPubsubNodeGroups WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_AFFILIATIONS =
            "SELECT nodeID,jid,affiliation FROM ofPubsubAffiliation WHERE serviceID=? " +
            "ORDER BY nodeID";
	private static final String LOAD_NODE_AFFILIATIONS = "SELECT nodeID,jid,affiliation FROM ofPubsubAffiliation WHERE serviceID=? AND nodeID=?";
    private static final String ADD_AFFILIATION =
            "INSERT INTO ofPubsubAffiliation (serviceID,nodeID,jid,affiliation) VALUES (?,?,?,?)";
    private static final String UPDATE_AFFILIATION =
            "UPDATE ofPubsubAffiliation SET affiliation=? WHERE serviceID=? AND nodeID=? AND jid=?";
    private static final String DELETE_AFFILIATION =
            "DELETE FROM ofPubsubAffiliation WHERE serviceID=? AND nodeID=? AND jid=?";
    private static final String DELETE_AFFILIATIONS =
            "DELETE FROM ofPubsubAffiliation WHERE serviceID=? AND nodeID=?";

	private static final String LOAD_SUBSCRIPTIONS_BASE = "SELECT nodeID, id, jid, owner, state, deliver, digest, digest_frequency, "
			+ "expire, includeBody, showValues, subscriptionType, subscriptionDepth, "
			+ "keyword FROM ofPubsubSubscription WHERE serviceID=? ";
	private static final String LOAD_NODE_SUBSCRIPTION = LOAD_SUBSCRIPTIONS_BASE + "AND nodeID=? AND id=?";
	private static final String LOAD_NODE_SUBSCRIPTIONS = LOAD_SUBSCRIPTIONS_BASE + "AND nodeID=?";
	private static final String LOAD_SUBSCRIPTIONS = LOAD_SUBSCRIPTIONS_BASE + "ORDER BY nodeID";

    private static final String FIND_SUBCRIBED_NODES = "SELECT serviceID, nodeID, jid FROM ofPubsubSubscription WHERE jid LIKE ? AND state LIKE ?";

    private static final String ADD_SUBSCRIPTION =
            "INSERT INTO ofPubsubSubscription (serviceID, nodeID, id, jid, owner, state, " +
            "deliver, digest, digest_frequency, expire, includeBody, showValues, " +
            "subscriptionType, subscriptionDepth, keyword) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_SUBSCRIPTION =
            "UPDATE ofPubsubSubscription SET owner=?, state=?, deliver=?, digest=?, " +
            "digest_frequency=?, expire=?, includeBody=?, showValues=?, subscriptionType=?, " +
            "subscriptionDepth=?, keyword=? WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_SUBSCRIPTION =
            "DELETE FROM ofPubsubSubscription WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_SUBSCRIPTIONS =
            "DELETE FROM ofPubsubSubscription WHERE serviceID=? AND nodeID=?";
    private static final String LOAD_ITEMS =
            "SELECT id,jid,creationDate,payload FROM ofPubsubItem " +
            "WHERE serviceID=? AND nodeID=? ORDER BY creationDate DESC";
    private static final String LOAD_ITEM =
            "SELECT jid,creationDate,payload FROM ofPubsubItem " +
            "WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String LOAD_LAST_ITEM =
            "SELECT id,jid,creationDate,payload FROM ofPubsubItem " +
            "WHERE serviceID=? AND nodeID=? ORDER BY creationDate DESC";
    private static final String ADD_ITEM =
            "INSERT INTO ofPubsubItem (serviceID,nodeID,id,jid,creationDate,payload) " +
            "VALUES (?,?,?,?,?,?)";
    private static final String UPDATE_ITEM =
            "UPDATE ofPubsubItem SET jid=?, creationDate=?, payload=? " +
            "WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_ITEM =
            "DELETE FROM ofPubsubItem WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_ITEMS =
            "DELETE FROM ofPubsubItem WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_DEFAULT_CONF =
            "SELECT deliverPayloads, maxPayloadSize, persistItems, maxItems, " +
            "notifyConfigChanges, notifyDelete, notifyRetract, presenceBased, " +
            "sendItemSubscribe, publisherModel, subscriptionEnabled, accessModel, language, " +
            "replyPolicy, associationPolicy, maxLeafNodes " +
            "FROM ofPubsubDefaultConf WHERE serviceID=? AND leaf=?";
    private static final String UPDATE_DEFAULT_CONF =
            "UPDATE ofPubsubDefaultConf SET deliverPayloads=?, maxPayloadSize=?, persistItems=?, " +
            "maxItems=?, notifyConfigChanges=?, notifyDelete=?, notifyRetract=?, " +
            "presenceBased=?, sendItemSubscribe=?, publisherModel=?, subscriptionEnabled=?, " +
            "accessModel=?, language=?, replyPolicy=?, associationPolicy=?, maxLeafNodes=? " +
            "WHERE serviceID=? AND leaf=?";
    private static final String ADD_DEFAULT_CONF =
            "INSERT INTO ofPubsubDefaultConf (serviceID, leaf, deliverPayloads, maxPayloadSize, " +
            "persistItems, maxItems, notifyConfigChanges, notifyDelete, notifyRetract, " +
            "presenceBased, sendItemSubscribe, publisherModel, subscriptionEnabled, " +
            "accessModel, language, replyPolicy, associationPolicy, maxLeafNodes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final static String GET_PEP_SERVICE = "SELECT DISTINCT serviceID FROM ofPubsubNode WHERE serviceID=?";

    /**
     * Pseudo-random number generator is used to offset timing for scheduled tasks
     * within a cluster (so they don't run at the same time on all members).
     */
    private final Random prng = new Random();

    /**
     * Purge timer delay is configurable, but not less than 60 seconds (default: 5 mins)
     */
    private static long purgeTimerDelay = Math.max(60000, JiveGlobals.getIntProperty("xmpp.pubsub.purge.timer", 300)*1000);

    /**
     * Maximum number of rows that will be fetched from the published items table.
     */
    private static final int MAX_ROWS_FETCH = JiveGlobals.getIntProperty("xmpp.pubsub.fetch.max", 2000);

    /**
     * Cache name for recently accessed published items.
     */
    private static final String DEFAULT_CONF_CACHE = "Default Node Configurations";

    /**
     * Cache for default configurations
     */
    private final Cache<String, DefaultNodeConfiguration> defaultNodeConfigurationCache = CacheFactory.createCache(DEFAULT_CONF_CACHE);

    @Override
    public void initialize()
    {
        log.debug( "Initializing" );
        try {
            // increase the timer delay when running in cluster mode
            // because other members are also running the purge task
            if ( ClusterManager.isClusteringEnabled()) {
                purgeTimerDelay = purgeTimerDelay*2;
            }
            TaskEngine.getInstance().schedule(new TimerTask() {
                @Override
                public void run() { purgeItems(); }
            }, Math.abs(prng.nextLong())%purgeTimerDelay, purgeTimerDelay);

        } catch (Exception ex) {
            log.error("Failed to initialize pubsub maintenance tasks", ex);
        }
    }

    @Override
    public void createNode(Node node)
    {
        log.trace( "Creating node: {} (write to database)", node.getUniqueIdentifier() );

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(ADD_NODE);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.setInt(3, (node.isCollectionNode() ? 0 : 1));
            pstmt.setString(4, StringUtils.dateToMillis(node.getCreationDate()));
            pstmt.setString(5, StringUtils.dateToMillis(node.getModificationDate()));
            pstmt.setString(6, node.getParent() != null ? encodeNodeID(node.getParent().getNodeID()) : null);
            pstmt.setInt(7, (node.isPayloadDelivered() ? 1 : 0));
            if (!node.isCollectionNode()) {
                pstmt.setInt(8, ((LeafNode) node).getMaxPayloadSize());
                pstmt.setInt(9, (((LeafNode) node).isPersistPublishedItems() ? 1 : 0));
                pstmt.setInt(10, ((LeafNode) node).getMaxPublishedItems());
            }
            else {
                pstmt.setInt(8, 0);
                pstmt.setInt(9, 0);
                pstmt.setInt(10, 0);
            }
            pstmt.setInt(11, (node.isNotifiedOfConfigChanges() ? 1 : 0));
            pstmt.setInt(12, (node.isNotifiedOfDelete() ? 1 : 0));
            pstmt.setInt(13, (node.isNotifiedOfRetract() ? 1 : 0));
            pstmt.setInt(14, (node.isPresenceBasedDelivery() ? 1 : 0));
            pstmt.setInt(15, (node.isSendItemSubscribe() ? 1 : 0));
            pstmt.setString(16, node.getPublisherModel().getName());
            pstmt.setInt(17, (node.isSubscriptionEnabled() ? 1 : 0));
            pstmt.setInt(18, (node.isSubscriptionConfigurationRequired() ? 1 : 0));
            pstmt.setString(19, node.getAccessModel().getName());
            pstmt.setString(20, node.getPayloadType());
            pstmt.setString(21, node.getBodyXSLT());
            pstmt.setString(22, node.getDataformXSLT());
            pstmt.setString(23, node.getCreator().toString());
            pstmt.setString(24, node.getDescription());
            pstmt.setString(25, node.getLanguage());
            pstmt.setString(26, node.getName());
            if (node.getReplyPolicy() != null) {
                pstmt.setString(27, node.getReplyPolicy().name());
            }
            else {
                pstmt.setString(27, null);
            }
            if (node.isCollectionNode()) {
                pstmt.setString(28, ((CollectionNode)node).getAssociationPolicy().name());
                pstmt.setInt(29, ((CollectionNode)node).getMaxLeafNodes());
            }
            else {
                pstmt.setString(28, null);
                pstmt.setInt(29, 0);
            }
            pstmt.executeUpdate();

            // Save associated JIDs and roster groups
            saveAssociatedElements(con, node);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while creating a node ({}) in the database.", node.getUniqueIdentifier(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    @Override
    public void updateNode(Node node)
    {
        log.trace( "Updating node: {} (write to database)", node.getUniqueIdentifier() );

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(UPDATE_NODE);
            pstmt.setString(1, StringUtils.dateToMillis(node.getModificationDate()));
            pstmt.setString(2, node.getParent() != null ? encodeNodeID(node.getParent().getNodeID()) : null);
            pstmt.setInt(3, (node.isPayloadDelivered() ? 1 : 0));
            if (!node.isCollectionNode()) {
                pstmt.setInt(4, ((LeafNode) node).getMaxPayloadSize());
                pstmt.setInt(5, (((LeafNode) node).isPersistPublishedItems() ? 1 : 0));
                pstmt.setInt(6, ((LeafNode) node).getMaxPublishedItems());
            }
            else {
                pstmt.setInt(4, 0);
                pstmt.setInt(5, 0);
                pstmt.setInt(6, 0);
            }
            pstmt.setInt(7, (node.isNotifiedOfConfigChanges() ? 1 : 0));
            pstmt.setInt(8, (node.isNotifiedOfDelete() ? 1 : 0));
            pstmt.setInt(9, (node.isNotifiedOfRetract() ? 1 : 0));
            pstmt.setInt(10, (node.isPresenceBasedDelivery() ? 1 : 0));
            pstmt.setInt(11, (node.isSendItemSubscribe() ? 1 : 0));
            pstmt.setString(12, node.getPublisherModel().getName());
            pstmt.setInt(13, (node.isSubscriptionEnabled() ? 1 : 0));
            pstmt.setInt(14, (node.isSubscriptionConfigurationRequired() ? 1 : 0));
            pstmt.setString(15, node.getAccessModel().getName());
            pstmt.setString(16, node.getPayloadType());
            pstmt.setString(17, node.getBodyXSLT());
            pstmt.setString(18, node.getDataformXSLT());
            pstmt.setString(19, node.getDescription());
            pstmt.setString(20, node.getLanguage());
            pstmt.setString(21, node.getName());
            if (node.getReplyPolicy() != null) {
                pstmt.setString(22, node.getReplyPolicy().name());
            }
            else {
                pstmt.setString(22, null);
            }
            if (node.isCollectionNode()) {
                pstmt.setString(23, ((CollectionNode) node).getAssociationPolicy().name());
                pstmt.setInt(24, ((CollectionNode) node).getMaxLeafNodes());
            }
            else {
                pstmt.setString(23, null);
                pstmt.setInt(24, 0);
            }
            pstmt.setString(25, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(26, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove existing JIDs associated with the the node
            pstmt = con.prepareStatement(DELETE_NODE_JIDS);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove roster groups associated with the the node being deleted
            pstmt = con.prepareStatement(DELETE_NODE_GROUPS);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();

            // Save associated JIDs and roster groups
            saveAssociatedElements(con, node);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while updating a node ({}) in the database.", node.getUniqueIdentifier(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    private static void saveAssociatedElements(Connection con, Node node) throws SQLException {
        log.trace( "Saving associates elements of node: {}", node.getUniqueIdentifier() );

        // Add new JIDs associated with the the node
        PreparedStatement pstmt = con.prepareStatement(ADD_NODE_JIDS);
        try {
            for (JID jid : node.getContacts()) {
                pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, jid.toString());
                pstmt.setString(4, "contacts");
                pstmt.executeUpdate();
            }
            for (JID jid : node.getReplyRooms()) {
                pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, jid.toString());
                pstmt.setString(4, "replyRooms");
                pstmt.executeUpdate();
            }
            for (JID jid : node.getReplyTo()) {
                pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, jid.toString());
                pstmt.setString(4, "replyTo");
                pstmt.executeUpdate();
            }
            if (node.isCollectionNode()) {
                for (JID jid : ((CollectionNode) node).getAssociationTrusted()) {
                    pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                    pstmt.setString(2, encodeNodeID(node.getNodeID()));
                    pstmt.setString(3, jid.toString());
                    pstmt.setString(4, "associationTrusted");
                    pstmt.executeUpdate();
                }
            }
            DbConnectionManager.fastcloseStmt(pstmt);
            // Add new roster groups associated with the the node
            pstmt = con.prepareStatement(ADD_NODE_GROUPS);
            for (String groupName : node.getRosterGroupsAllowed()) {
                pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, groupName);
                pstmt.executeUpdate();
            }
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    @Override
    public void removeNode(Node node)
    {
        log.trace( "Removing node: {} (write to database)", node.getUniqueIdentifier() );

        if ( node instanceof LeafNode ) {
            purgeNode( (LeafNode) node );
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_NODE);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove JIDs associated with the the node being deleted
            pstmt = con.prepareStatement(DELETE_NODE_JIDS);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove roster groups associated with the the node being deleted
            pstmt = con.prepareStatement(DELETE_NODE_GROUPS);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove published items of the node being deleted
			if (node instanceof LeafNode)
			{
				purgeNode((LeafNode) node, con);
			}

            // Remove all affiliates from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_AFFILIATIONS);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove users that were subscribed to the node
            pstmt = con.prepareStatement(DELETE_SUBSCRIPTIONS);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while removing a node ({}) in the database.", node.getUniqueIdentifier(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    @Override
    public void loadNodes(PubSubService service) {
        log.debug( "Loading nodes for service: {}", service.getServiceID() );

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Map<Node.UniqueIdentifier, Node> nodes = new HashMap<>();
        try {
            con = DbConnectionManager.getConnection();
            // Get all non-leaf nodes (to ensure parent nodes are loaded before their children)
			pstmt = con.prepareStatement(LOAD_NODES);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            
            Map<Node.UniqueIdentifier, Node.UniqueIdentifier> parentMappings = new HashMap<>();
            
            // Rebuild loaded non-leaf nodes
            while(rs.next()) {
                loadNode(service.getUniqueIdentifier(), nodes, parentMappings, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            if (nodes.size() == 0) {
            	log.info("No nodes found in pubsub for service {}", service.getServiceID() );
            	return;
            }
            
            for (Map.Entry<Node.UniqueIdentifier, Node.UniqueIdentifier> entry : parentMappings.entrySet()) {
            	Node child = nodes.get(entry.getKey());
            	CollectionNode parent = (CollectionNode) nodes.get(entry.getValue());
            	
            	if (parent == null) {
            		log.error("Could not find parent node " + entry.getValue() + " for node " + entry.getKey());
            	}
            	else {
                    child.changeParent(parent);
            	}
            }
            // Get JIDs associated with all nodes
            pstmt = con.prepareStatement(LOAD_NODES_JIDS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the associated JIDs
            while(rs.next()) {
                loadAssociatedJIDs(nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // Get roster groups associateds with all nodes
            pstmt = con.prepareStatement(LOAD_NODES_GROUPS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the associated Groups
            while(rs.next()) {
                loadAssociatedGroups(nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // Get affiliations of all nodes
            pstmt = con.prepareStatement(LOAD_AFFILIATIONS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the correspondiding affiliates
            while(rs.next()) {
                loadAffiliations(nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // Get subscriptions to all nodes
            pstmt = con.prepareStatement(LOAD_SUBSCRIPTIONS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the correspondiding subscriptions
            while(rs.next()) {
                loadSubscriptions(nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while loading nodes for a service ({}) from the database.", service.getUniqueIdentifier(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        for (Node node : nodes.values()) {
            // Set now that the node is persistent in the database. Note: We need to
            // set this now since otherwise the node's affiliations will be saved to the database
            // "again" while adding them to the node!
            node.setSavedToDB(true);
            // Add the node to the service
            service.addNode(node);
        }
    }

    @Override
	public void loadNode(PubSubService service, Node.UniqueIdentifier nodeIdentifier)
	{
        log.debug( "Loading node: {}", nodeIdentifier );

        Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Map<Node.UniqueIdentifier, Node> nodes = new HashMap<>();
		try
		{
			con = DbConnectionManager.getConnection();
			// Get all non-leaf nodes (to ensure parent nodes are loaded before
			// their children)
			pstmt = con.prepareStatement(LOAD_NODE);
			pstmt.setString(1, nodeIdentifier.getServiceIdentifier().getServiceId());
			pstmt.setString(2, nodeIdentifier.getNodeId());
			rs = pstmt.executeQuery();
			Map<Node.UniqueIdentifier, Node.UniqueIdentifier> parentMapping = new HashMap<>();
			
			// Rebuild loaded non-leaf nodes
			if (rs.next())
			{
				loadNode(nodeIdentifier.getServiceIdentifier(), nodes, parentMapping, rs);
			}
			DbConnectionManager.fastcloseStmt(rs, pstmt);
            Node.UniqueIdentifier parentId = parentMapping.get(nodeIdentifier);
			
			if (parentId != null) {
                nodes.get(nodeIdentifier).changeParent((CollectionNode)nodes.get(parentId));
			}
				
			// Get JIDs associated with all nodes
			pstmt = con.prepareStatement(LOAD_NODE_JIDS);
			pstmt.setString(1, nodeIdentifier.getServiceIdentifier().getServiceId());
			pstmt.setString(2, nodeIdentifier.getNodeId());
			rs = pstmt.executeQuery();
			// Add to each node the associated JIDs
			while (rs.next())
			{
				loadAssociatedJIDs(nodes, rs);
			}
			DbConnectionManager.fastcloseStmt(rs, pstmt);

			// Get roster groups associated with all nodes
			pstmt = con.prepareStatement(LOAD_NODE_GROUPS);
			pstmt.setString(1, nodeIdentifier.getServiceIdentifier().getServiceId());
			pstmt.setString(2, nodeIdentifier.getNodeId());
			rs = pstmt.executeQuery();
			// Add to each node the associated Groups
			while (rs.next())
			{
				loadAssociatedGroups(nodes, rs);
			}
			DbConnectionManager.fastcloseStmt(rs, pstmt);

			// Get affiliations of all nodes
			pstmt = con.prepareStatement(LOAD_NODE_AFFILIATIONS);
			pstmt.setString(1, nodeIdentifier.getServiceIdentifier().getServiceId());
			pstmt.setString(2, nodeIdentifier.getNodeId());
			rs = pstmt.executeQuery();
			// Add to each node the corresponding affiliates
			while (rs.next())
			{
				loadAffiliations(nodes, rs);
			}
			DbConnectionManager.fastcloseStmt(rs, pstmt);

			// Get subscriptions to all nodes
			pstmt = con.prepareStatement(LOAD_NODE_SUBSCRIPTIONS);
			pstmt.setString(1, nodeIdentifier.getServiceIdentifier().getServiceId());
			pstmt.setString(2, nodeIdentifier.getNodeId());
			rs = pstmt.executeQuery();
			// Add to each node the corresponding subscriptions
			while (rs.next())
			{
				loadSubscriptions(nodes, rs);
			}
			DbConnectionManager.fastcloseStmt(rs, pstmt);
		}
		catch (SQLException sqle)
		{
            log.error("An exception occurred while loading a node ({}) from the database.", nodeIdentifier, sqle);
		}
		finally
		{
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}

		for (Node node : nodes.values())
		{
			// Set now that the node is persistent in the database. Note: We
			// need to
			// set this now since otherwise the node's affiliations will be
			// saved to the database
			// "again" while adding them to the node!
			node.setSavedToDB(true);
			// Add the node to the service
			service.addNode(node);
		}
	}

    private void loadNode(PubSubService.UniqueIdentifier serviceId, Map<Node.UniqueIdentifier, Node> loadedNodes, Map<Node.UniqueIdentifier, Node.UniqueIdentifier> parentMappings, ResultSet rs) {
        Node node;
        try {
            Node.UniqueIdentifier nodeId = new Node.UniqueIdentifier( serviceId, decodeNodeID(rs.getString(1)) );
            boolean leaf = rs.getInt(2) == 1;
            JID creator = new JID(rs.getString(22));

            String parent = decodeNodeID(rs.getString(5));
            if (parent != null) { // beware: 'empty string' is a valid node ID! OF-2084
                Node.UniqueIdentifier parentId = new Node.UniqueIdentifier( serviceId, parent );
                parentMappings.put(nodeId, parentId);
            }

            final boolean subscriptionEnabled = rs.getInt(16) == 1;
            final boolean deliverPayloads = rs.getInt(6) == 1;
            final boolean notifyConfigChanges = rs.getInt(10) == 1;
            final boolean notifyDelete = rs.getInt(11) == 1;
            final boolean notifyRetract = rs.getInt(12) == 1;
            final boolean presenceBasedDelivery = rs.getInt(13) == 1;
            final AccessModel accessModel = AccessModel.valueOf(rs.getString(18));
            final PublisherModel publisherModel = PublisherModel.valueOf(rs.getString(15));
            final String language = rs.getString(24);
            final Node.ItemReplyPolicy replyPolicy;
            if (rs.getString(26) != null) {
                replyPolicy = Node.ItemReplyPolicy.valueOf(rs.getString(26));
            } else {
                replyPolicy = null;
            }

            if (leaf) {
                // Retrieving a leaf node
                final boolean persistPublishedItems = rs.getInt(8) == 1;
                final int maxPublishedItems = rs.getInt(9);
                final int maxPayloadSize = rs.getInt(7);
                final boolean sendItemSubscribe = rs.getInt(14) == 1;
                node = new LeafNode(serviceId, null, nodeId.getNodeId(), creator, subscriptionEnabled, deliverPayloads, notifyConfigChanges, notifyDelete, notifyRetract, presenceBasedDelivery, accessModel, publisherModel, language, replyPolicy, persistPublishedItems, maxPublishedItems, maxPayloadSize, sendItemSubscribe);
            }
            else {
                // Retrieving a collection node
                final CollectionNode.LeafNodeAssociationPolicy associationPolicy = CollectionNode.LeafNodeAssociationPolicy.valueOf(rs.getString(27));
                final int maxLeafNodes = rs.getInt(28);
                node = new CollectionNode(serviceId, null, nodeId.getNodeId(), creator, subscriptionEnabled, deliverPayloads, notifyConfigChanges, notifyDelete, notifyRetract, presenceBasedDelivery, accessModel, publisherModel, language, replyPolicy, associationPolicy, maxLeafNodes );
            }
            node.setCreationDate(new Date(Long.parseLong(rs.getString(3).trim())));
            node.setModificationDate(new Date(Long.parseLong(rs.getString(4).trim())));
            node.setSubscriptionConfigurationRequired(rs.getInt(17) == 1);
            node.setPayloadType(rs.getString(19));
            node.setBodyXSLT(rs.getString(20));
            node.setDataformXSLT(rs.getString(21));
            node.setDescription(rs.getString(23));
            node.setName(rs.getString(25));

            // Add the load to the list of loaded nodes
            loadedNodes.put(node.getUniqueIdentifier(), node);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while loading a node for a service ({}) from the database.", serviceId, sqle);
        }
    }

    private static Node lookupNode(Map<Node.UniqueIdentifier, Node> nodes, String nodeID) {
        Set<Node> matchingNodes = nodes.values().stream().filter(n -> n.getNodeID().equals(nodeID)).collect(Collectors.toSet());
        if (matchingNodes.isEmpty()) {
            return null;
        }
        if (matchingNodes.size() > 1) {
            // This is a coding error.
            throw new IllegalStateException( "Identifier does not uniquely identify node in provided map: " + nodeID );
        }
        return matchingNodes.iterator().next();
    }

    private void loadAssociatedJIDs(Map<Node.UniqueIdentifier, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = lookupNode(nodes, nodeID);
            if (node == null) {
                log.warn("JID associated to a non-existent node: {}", nodeID);
                return;
            }
            JID jid = new JID(rs.getString(2));
            String associationType = rs.getString(3);
            if ("contacts".equals(associationType)) {
                node.addContact(jid);
            }
            else if ("replyRooms".equals(associationType)) {
                node.addReplyRoom(jid);
            }
            else if ("replyTo".equals(associationType)) {
                node.addReplyTo(jid);
            }
            else if ("associationTrusted".equals(associationType)) {
                ((CollectionNode) node).addAssociationTrusted(jid);
            }
        }
        catch (Exception ex) {
            log.error("An exception occurred while loading associated JIDs for nodes from the database.", ex);
        }
    }

    private void loadAssociatedGroups(Map<Node.UniqueIdentifier, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = lookupNode(nodes, nodeID);
            if (node == null) {
                log.warn("Roster Group associated to a non-existent node: " + nodeID);
                return;
            }
            node.addAllowedRosterGroup(rs.getString(2));
        }
        catch (SQLException ex) {
            log.error("An exception occurred while loading associated groups for nodes from the database.", ex);
        }
    }

    private void loadAffiliations(Map<Node.UniqueIdentifier, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = lookupNode(nodes, nodeID);
            if (node == null) {
                log.warn("Affiliations found for a non-existent node: " + nodeID);
                return;
            }
            NodeAffiliate affiliate = new NodeAffiliate(node, new JID(rs.getString(2)));
            affiliate.setAffiliation(NodeAffiliate.Affiliation.valueOf(rs.getString(3)));
            node.addAffiliate(affiliate);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while loading affiliations for nodes from the database.", sqle);
        }
    }

    @Override
	public void loadSubscription(Node node, String subId)
	{
	    Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Map<Node.UniqueIdentifier, Node> nodes = new HashMap<>();
		nodes.put(node.getUniqueIdentifier(), node);

		try
		{
			con = DbConnectionManager.getConnection();

			// Get subscriptions to all nodes
			pstmt = con.prepareStatement(LOAD_NODE_SUBSCRIPTION);
			pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
			pstmt.setString(2, node.getNodeID());
			pstmt.setString(3, subId);
			rs = pstmt.executeQuery();

			// Add to each node the corresponding subscription
			if (rs.next())
			{
				loadSubscriptions(nodes, rs);
			}
		}
		catch (SQLException sqle)
		{
            log.error("An exception occurred while loading a subscription ({}) for a node ({}) from the database.", subId, node.getUniqueIdentifier(), sqle);
		}
		finally
		{
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
	}

    @Override
    @Nonnull
    public Set<Node.UniqueIdentifier> findDirectlySubscribedNodes(@Nonnull JID address)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Set<Node.UniqueIdentifier> result = new HashSet<>();

        try
        {
            con = DbConnectionManager.getConnection();

            // Get subscriptions to all nodes
            pstmt = con.prepareStatement(FIND_SUBCRIBED_NODES);
            // Match subscriptions that use a bare JID, or full JIDs matching the bare JID search argument.
            pstmt.setString( 1, address.toBareJID() + '%'); // note that the '%' operator matches zero or more characters. Exact matches included.
            pstmt.setString( 2, NodeSubscription.State.subscribed.name() );
            rs = pstmt.executeQuery();
            while (rs.next())
            {
                final String serviceID = rs.getString("serviceID");
                final String nodeID = rs.getString("nodeID");
                try
                {
                    final JID jid = new JID(rs.getString("jid"));
                    if ( jid.getResource() != null && !jid.equals(address))
                    {
                        // The subscription is explicit to a _different_ full JID. Do not return this one.
                        continue;
                    }
                }
                catch ( IllegalArgumentException e )
                {
                    log.warn( "Unable to parse value as a JID, for serviceID {}, nodeID {}", serviceID, nodeID);
                    continue;
                }
                final Node.UniqueIdentifier identifier = new Node.UniqueIdentifier(serviceID, nodeID);
                result.add(identifier);
            }
        }
        catch (SQLException sqle)
        {
            log.error("An exception occurred while finding subscribed nodes for {}.", address, sqle);
        }
        finally
        {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return result;
    }

    private void loadSubscriptions(Map<Node.UniqueIdentifier, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = lookupNode(nodes, nodeID);
            if (node == null) {
                log.warn("Subscription found for a non-existent node: " + nodeID);
                return;
            }
            String subID = rs.getString(2);
            JID subscriber = new JID(rs.getString(3));
            JID owner = new JID(rs.getString(4));
            if (node.getAffiliate(owner) == null) {
                log.warn("Subscription found for a non-existent affiliate: " + owner +
                        " in node: " + node.getUniqueIdentifier());
                return;
            }
            NodeSubscription.State state = NodeSubscription.State.valueOf(rs.getString(5));
			NodeSubscription subscription = new NodeSubscription(node, owner, subscriber, state, subID);
            subscription.setShouldDeliverNotifications(rs.getInt(6) == 1);
            subscription.setUsingDigest(rs.getInt(7) == 1);
            subscription.setDigestFrequency(rs.getInt(8));
            if (rs.getString(9) != null) {
                subscription.setExpire(new Date(Long.parseLong(rs.getString(9).trim())));
            }
            subscription.setIncludingBody(rs.getInt(10) == 1);
            subscription.setPresenceStates(decodeWithComma(rs.getString(11)));
            subscription.setType(NodeSubscription.Type.valueOf(rs.getString(12)));
            subscription.setDepth(rs.getInt(13));
            subscription.setKeyword(rs.getString(14));
            // Indicate the subscription that is has already been saved to the database
            subscription.setSavedToDB(true);
            node.addSubscription(subscription);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while loading a subscriptions for nodes of a service from the database.", sqle);
        }
    }

    /**
     * Update the DB with the new affiliation of the user in the node.
     *
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The new affiliation of the user in the node.
     * @param create    True if this is a new affiliate.
     * @deprecated replaced by {@link #createAffiliation(Node, NodeAffiliate)} and {@link #updateAffiliation(Node, NodeAffiliate)}
     */
    @Deprecated
    public void saveAffiliation(Node node, NodeAffiliate affiliate, boolean create) {
        if (create) {
            createAffiliation( node, affiliate );
        } else {
            updateAffiliation( node, affiliate );
        }
    }

    @Override
    public void createAffiliation(Node node, NodeAffiliate affiliate)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_AFFILIATION);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.setString(3, affiliate.getJID().toString());
            pstmt.setString(4, affiliate.getAffiliation().name());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while creating an affiliation ({}) to a node ({}) in the database.", affiliate, node.getUniqueIdentifier(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void updateAffiliation(Node node, NodeAffiliate affiliate)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_AFFILIATION);
            pstmt.setString(1, affiliate.getAffiliation().name());
            pstmt.setString(2, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(3, encodeNodeID(node.getNodeID()));
            pstmt.setString(4, affiliate.getJID().toString());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while updating an affiliation ({}) to a node ({}) in the database.", affiliate, node.getUniqueIdentifier(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void removeAffiliation(Node node, NodeAffiliate affiliate)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_AFFILIATION);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.setString(3, affiliate.getJID().toString());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while removing an affiliation ({}) to a node ({}) in the database.", affiliate, node.getUniqueIdentifier(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates the DB with the new subscription of the user to the node.
     *
     * @param node      The node where the user has subscribed to.
     * @param subscription The new subscription of the user to the node.
     * @param create    True if this is a new affiliate.
     * @deprecated Replaced by {@link #createSubscription(Node, NodeSubscription)} and {@link #updateSubscription(Node, NodeSubscription)}
     */
    @Deprecated
    public void saveSubscription(Node node, NodeSubscription subscription, boolean create) {
        if (create) {
            createSubscription( node, subscription );
        } else {
            updateSubscription( node, subscription );
        }
    }

    @Override
    public void createSubscription(Node node, NodeSubscription subscription)
    {
        log.trace( "Creating node subscription: {} {} (write to database)", node.getUniqueIdentifier(), subscription.getID() );
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Add the subscription of the user to the database
            pstmt = con.prepareStatement(ADD_SUBSCRIPTION);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.setString(3, subscription.getID());
            pstmt.setString(4, subscription.getJID().toString());
            pstmt.setString(5, subscription.getOwner().toString());
            pstmt.setString(6, subscription.getState().name());
            pstmt.setInt(7, (subscription.shouldDeliverNotifications() ? 1 : 0));
            pstmt.setInt(8, (subscription.isUsingDigest() ? 1 : 0));
            pstmt.setInt(9, subscription.getDigestFrequency());
            Date expireDate = subscription.getExpire();
            if (expireDate == null) {
                pstmt.setString(10, null);
            }
            else {
                pstmt.setString(10, StringUtils.dateToMillis(expireDate));
            }
            pstmt.setInt(11, (subscription.isIncludingBody() ? 1 : 0));
            pstmt.setString(12, encodeWithComma(subscription.getPresenceStates()));
            pstmt.setString(13, subscription.getType().name());
            pstmt.setInt(14, subscription.getDepth());
            pstmt.setString(15, subscription.getKeyword());
            pstmt.executeUpdate();
            // Indicate the subscription that is has been saved to the database
            subscription.setSavedToDB(true);
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while creating a subscription ({}) to a node ({}) in the database.", subscription, node.getUniqueIdentifier(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void updateSubscription(Node node, NodeSubscription subscription)
    {
        log.trace( "Updating node subscription: {} {} (write to database)", node.getUniqueIdentifier(), subscription.getID() );
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (NodeSubscription.State.none == subscription.getState()) {
                // Remove the subscription of the user from the table
                pstmt = con.prepareStatement(DELETE_SUBSCRIPTION);
                pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(2, subscription.getID());
                pstmt.executeUpdate();
            }
            else {
                // Update the subscription of the user in the backend store
                pstmt = con.prepareStatement(UPDATE_SUBSCRIPTION);
                pstmt.setString(1, subscription.getOwner().toString());
                pstmt.setString(2, subscription.getState().name());
                pstmt.setInt(3, (subscription.shouldDeliverNotifications() ? 1 : 0));
                pstmt.setInt(4, (subscription.isUsingDigest() ? 1 : 0));
                pstmt.setInt(5, subscription.getDigestFrequency());
                Date expireDate = subscription.getExpire();
                if (expireDate == null) {
                    pstmt.setString(6, null);
                }
                else {
                    pstmt.setString(6, StringUtils.dateToMillis(expireDate));
                }
                pstmt.setInt(7, (subscription.isIncludingBody() ? 1 : 0));
                pstmt.setString(8, encodeWithComma(subscription.getPresenceStates()));
                pstmt.setString(9, subscription.getType().name());
                pstmt.setInt(10, subscription.getDepth());
                pstmt.setString(11, subscription.getKeyword());
                pstmt.setString(12, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(13, encodeNodeID(node.getNodeID()));
                pstmt.setString(14, subscription.getID());
                pstmt.executeUpdate();
            }
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while updating a subscription ({}) to a node ({}) in the database.", subscription, node.getUniqueIdentifier(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void removeSubscription(NodeSubscription subscription)
    {
        log.trace( "Removing node subscription: {} {} (write to database)", subscription.getNode().getUniqueIdentifier(), subscription.getID() );

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_SUBSCRIPTION);
            pstmt.setString(1, subscription.getNode().getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(subscription.getNode().getNodeID()));
            pstmt.setString(3, subscription.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            log.error("An exception occurred while removing a subscription ({}) in the database.", subscription, sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void savePublishedItem(PublishedItem item) {
        // When an item with the given itemId exists, it must be overwritten (says the XEP)
        final boolean create = getPublishedItem( item.getNode(), item.getUniqueIdentifier() ) == null;
        if ( create ) {
            createPublishedItem( item );
        } else {
            updatePublishedItem( item );
        }
    }

    public void createPublishedItem(PublishedItem item)
    {
        log.trace( "Creating published item: {} (write to database)", item.getUniqueIdentifier() );

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_ITEM);
            pstmt.setString(1, item.getNode().getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(item.getNodeID()));
            pstmt.setString(3, item.getID());
            pstmt.setString(4, item.getPublisher().toString());
            pstmt.setString(5, StringUtils.dateToMillis( item.getCreationDate()));
            pstmt.setString(6, item.getPayloadXML());
            pstmt.execute();
        } catch (SQLException ex) {
            log.error("Published item could not be created in database: {}\n{}", item.getUniqueIdentifier(), item.getPayloadXML(), ex);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void updatePublishedItem(PublishedItem item)
    {
        log.trace( "Updating published item: {} (write to database)", item.getUniqueIdentifier() );

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_ITEM);
            pstmt.setString(1, item.getPublisher().toString());
            pstmt.setString(2, StringUtils.dateToMillis( item.getCreationDate()));
            pstmt.setString(3, item.getPayloadXML());
            pstmt.setString(4, item.getNode().getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(5, encodeNodeID(item.getNodeID()));
            pstmt.setString(6, item.getID());
            pstmt.execute();
        } catch (SQLException ex) {
            log.error("Published item could not be updated in database: {}\n{}", item.getUniqueIdentifier(), item.getPayloadXML(), ex);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void savePublishedItems(Connection con, List<PublishedItem> addList, boolean batch) throws SQLException
    {
        if (addList == null || addList.isEmpty() )
        {
            return;
        }

        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement(ADD_ITEM);
            boolean hasBatchItems = false;
            for ( final PublishedItem item : addList)
            {
                pstmt.setString(1, item.getNode().getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(item.getNodeID()));
                pstmt.setString(3, item.getID());
                pstmt.setString(4, item.getPublisher().toString());
                pstmt.setString(5, StringUtils.dateToMillis(item.getCreationDate()));
                pstmt.setString(6, item.getPayloadXML());
                if ( batch ) {
                    hasBatchItems = true;
                    pstmt.addBatch();
                } else {
                    pstmt.execute();
                }
            }
            if (hasBatchItems) {
                pstmt.executeBatch();
            }
        } finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    @Override
    public void removePublishedItem(PublishedItem item) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ITEM);
            pstmt.setString(1, item.getNode().getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(item.getNode().getNodeID()));
            pstmt.setString(3, item.getID());
            pstmt.execute();
        } catch (SQLException ex) {
            log.error("Failed to delete published item from DB: {}", item.getUniqueIdentifier(), ex);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    protected void removePublishedItems(Connection con, List<PublishedItem> delList, boolean batch) throws SQLException
    {
        if (delList == null || delList.isEmpty() )
        {
            return;
        }

        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement(DELETE_ITEM);
            boolean hasBatchItems = false;
            for ( final PublishedItem item : delList )
            {
                pstmt.setString(1, item.getNode().getUniqueIdentifier().getServiceIdentifier().getServiceId());
                pstmt.setString(2, encodeNodeID(item.getNode().getNodeID()));
                pstmt.setString(3, item.getID());
                if ( batch ) {
                    hasBatchItems = true;
                    pstmt.addBatch();
                } else {
                    pstmt.execute();
                }
            }
            if (hasBatchItems) {
                pstmt.executeBatch();
            }
        } finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    /**
     * Writes large changesets to the database, using batches and transactions when
     * available.
     *
     * The 'delete' list takes precedence over the 'add' list: when an item exists
     * on both lists, it is removed (and not re-added) to the database.
     *
     * To prevent duplicates to exist in the database, this method will attempt to
     * remove all items to-be-added from the database, before re-adding them.
     *
     * Note that multiple attempts to write items to the DB are made:
     * 1) insert all pending items in a single batch
     * 2) if the batch insert fails, retry by inserting each item separately
     *
     * @param addList A list of items to be added to the database.
     * @param delList A list of items to be removed from the database.
     */
    @Override
    public void bulkPublishedItems( List<PublishedItem> addList, List<PublishedItem> delList )
    {
        // TODO Consider re-instating 'xmpp.pubsub.item.retry' property, to allow for retries of failed database
        // writes for items. Note that this behavior was first introduced when the work load was cached. As that's
        // no longer the case, there likely is no reason to assume that a retry (which would be immediate) would
        // succeed. There's a case to be made to reinstate this behavior in CachingPubsubPersistneceProvider,
        // instead of here.

        // is there anything to do?
        if ( addList == null ) {
            addList = new ArrayList<>();
        }

        if ( delList == null ) {
            delList = new ArrayList<>();
        }

        if ( addList.isEmpty() && delList.isEmpty() )
        {
            return;
        }

        log.debug( "Processing collection of changes to published items. Additions: {}, deletes: {}", addList.size(), delList.size() );

        // Ensure there are no duplicates by deleting before adding.
        delList.addAll( addList );

        boolean rollback = false;
        Connection con;
        try
        {
            con = DbConnectionManager.getTransactionConnection();
        }
        catch ( SQLException e )
        {
            log.error( "Failed to obtain a database connection, to process a batch of changes to published items from DB.", e );
            return;
        }

        try
        {
            log.debug( "Try to add the pending items as a database batch." );
            removePublishedItems( con, delList, true ); // delete first (to remove possible duplicates), then add new items
            savePublishedItems( con, addList, true );
        }
        catch ( SQLException e1 )
        {
            log.warn( "Failed to process a collection of changes to published items from DB. Retrying individually.", e1);
            log.debug( "Retry each item individually, rather than rolling back.");
            try
            {
                removePublishedItems( con, delList, false ); // delete first (to remove possible duplicates), then add new items
                savePublishedItems( con, addList, false );
            }
            catch ( SQLException e2 )
            {
                // Individual retries also failed. Roll back.
                log.error( "Failed to process a collection of changes both as a database batch as wel as individual changes. Rolling back transaction and giving up. Data is lost.", e2 );
                rollback = true;
            }
        }
        finally
        {
            DbConnectionManager.closeTransactionConnection(con, rollback);
        }
    }

    private static String getDefaultNodeConfigurationCacheKey( PubSubService.UniqueIdentifier uniqueIdentifier, boolean isLeafType )
    {
        return uniqueIdentifier.getServiceId() + "|" + isLeafType;
    }

    @Override
    public DefaultNodeConfiguration loadDefaultConfiguration(PubSubService.UniqueIdentifier serviceIdentifier, boolean isLeafType)
    {
        final String key = getDefaultNodeConfigurationCacheKey( serviceIdentifier, isLeafType );
        DefaultNodeConfiguration result = defaultNodeConfigurationCache.get( key );
        if ( result == null )
        {
            final Lock lock = defaultNodeConfigurationCache.getLock( DEFAULT_CONF_CACHE );
            lock.lock();
            try
            {
                result = defaultNodeConfigurationCache.get( key );
                if ( result == null )
                {
                    Connection con = null;
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    DefaultNodeConfiguration config = null;
                    try {
                        con = DbConnectionManager.getConnection();
                        // Get default node configuration for the specified service
                        pstmt = con.prepareStatement(LOAD_DEFAULT_CONF);
                        pstmt.setString(1, serviceIdentifier.getServiceId());
                        pstmt.setInt(2, (isLeafType ? 1 : 0));
                        rs = pstmt.executeQuery();
                        if (rs.next()) {
                            config = new DefaultNodeConfiguration(isLeafType);
                            // Rebuild loaded default node configuration
                            config.setDeliverPayloads(rs.getInt(1) == 1);
                            config.setMaxPayloadSize(rs.getInt(2));
                            config.setPersistPublishedItems(rs.getInt(3) == 1);
                            config.setMaxPublishedItems(rs.getInt(4));
                            config.setNotifyConfigChanges(rs.getInt(5) == 1);
                            config.setNotifyDelete(rs.getInt(6) == 1);
                            config.setNotifyRetract(rs.getInt(7) == 1);
                            config.setPresenceBasedDelivery(rs.getInt(8) == 1);
                            config.setSendItemSubscribe(rs.getInt(9) == 1);
                            config.setPublisherModel(PublisherModel.valueOf(rs.getString(10)));
                            config.setSubscriptionEnabled(rs.getInt(11) == 1);
                            config.setAccessModel(AccessModel.valueOf(rs.getString(12)));
                            config.setLanguage(rs.getString(13));
                            if (rs.getString(14) != null) {
                                config.setReplyPolicy(Node.ItemReplyPolicy.valueOf(rs.getString(14)));
                            }
                            config.setAssociationPolicy(
                                    CollectionNode.LeafNodeAssociationPolicy.valueOf(rs.getString(15)));
                            config.setMaxLeafNodes(rs.getInt(16));
                        }
                        if ( config != null ) {
                            defaultNodeConfigurationCache.put( key, config );
                        }
                        result = config;
                    }
                    catch (Exception sqle) {
                        log.error(sqle.getMessage(), sqle);
                    }
                    finally {
                        DbConnectionManager.closeConnection(rs, pstmt, con);
                    }
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return result;
    }

    @Override
    public void createDefaultConfiguration(PubSubService.UniqueIdentifier serviceIdentifier, DefaultNodeConfiguration config)
    {
        final String key = getDefaultNodeConfigurationCacheKey( serviceIdentifier, config.isLeaf() );

        final Lock lock = defaultNodeConfigurationCache.getLock( DEFAULT_CONF_CACHE );
        lock.lock();
        try
        {
            Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(ADD_DEFAULT_CONF);
                pstmt.setString(1, serviceIdentifier.getServiceId());
                pstmt.setInt(2, (config.isLeaf() ? 1 : 0));
                pstmt.setInt(3, (config.isDeliverPayloads() ? 1 : 0));
                pstmt.setInt(4, config.getMaxPayloadSize());
                pstmt.setInt(5, (config.isPersistPublishedItems() ? 1 : 0));
                pstmt.setInt(6, config.getMaxPublishedItems());
                pstmt.setInt(7, (config.isNotifyConfigChanges() ? 1 : 0));
                pstmt.setInt(8, (config.isNotifyDelete() ? 1 : 0));
                pstmt.setInt(9, (config.isNotifyRetract() ? 1 : 0));
                pstmt.setInt(10, (config.isPresenceBasedDelivery() ? 1 : 0));
                pstmt.setInt(11, (config.isSendItemSubscribe() ? 1 : 0));
                pstmt.setString(12, config.getPublisherModel().getName());
                pstmt.setInt(13, (config.isSubscriptionEnabled() ? 1 : 0));
                pstmt.setString(14, config.getAccessModel().getName());
                pstmt.setString(15, config.getLanguage());
                if (config.getReplyPolicy() != null) {
                    pstmt.setString(16, config.getReplyPolicy().name());
                }
                else {
                    pstmt.setString(16, null);
                }
                pstmt.setString(17, config.getAssociationPolicy().name());
                pstmt.setInt(18, config.getMaxLeafNodes());
                pstmt.executeUpdate();

                defaultNodeConfigurationCache.put( key, config );
            }
            catch (SQLException sqle) {
                log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void updateDefaultConfiguration(PubSubService.UniqueIdentifier serviceIdentifier, DefaultNodeConfiguration config)
    {
        final Lock lock = defaultNodeConfigurationCache.getLock( DEFAULT_CONF_CACHE );
        lock.lock();
        try {
            Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(UPDATE_DEFAULT_CONF);
                pstmt.setInt(1, (config.isDeliverPayloads() ? 1 : 0));
                pstmt.setInt(2, config.getMaxPayloadSize());
                pstmt.setInt(3, (config.isPersistPublishedItems() ? 1 : 0));
                pstmt.setInt(4, config.getMaxPublishedItems());
                pstmt.setInt(5, (config.isNotifyConfigChanges() ? 1 : 0));
                pstmt.setInt(6, (config.isNotifyDelete() ? 1 : 0));
                pstmt.setInt(7, (config.isNotifyRetract() ? 1 : 0));
                pstmt.setInt(8, (config.isPresenceBasedDelivery() ? 1 : 0));
                pstmt.setInt(9, (config.isSendItemSubscribe() ? 1 : 0));
                pstmt.setString(10, config.getPublisherModel().getName());
                pstmt.setInt(11, (config.isSubscriptionEnabled() ? 1 : 0));
                pstmt.setString(12, config.getAccessModel().getName());
                pstmt.setString(13, config.getLanguage());
                if (config.getReplyPolicy() != null) {
                    pstmt.setString(14, config.getReplyPolicy().name());
                }
                else {
                    pstmt.setString(14, null);
                }
                pstmt.setString(15, config.getAssociationPolicy().name());
                pstmt.setInt(16, config.getMaxLeafNodes());
                pstmt.setString(17, serviceIdentifier.getServiceId());
                pstmt.setInt(18, (config.isLeaf() ? 1 : 0));
                pstmt.executeUpdate();

                // Note that 'isLeaf' might have changed.
                defaultNodeConfigurationCache.remove( getDefaultNodeConfigurationCacheKey( serviceIdentifier, true ) );
                defaultNodeConfigurationCache.remove( getDefaultNodeConfigurationCacheKey( serviceIdentifier, false ) );
                defaultNodeConfigurationCache.put( getDefaultNodeConfigurationCacheKey( serviceIdentifier, config.isLeaf() ), config );
            }
            catch (SQLException sqle) {
                log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public List<PublishedItem> getPublishedItems(LeafNode node) {
    	return getPublishedItems(node, node.getMaxPublishedItems());
    }

    @Override
    public List<PublishedItem> getPublishedItems(LeafNode node, int maxRows) {
    	Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        int max = MAX_ROWS_FETCH;
        int maxPublished = node.getMaxPublishedItems();

        // Limit the max rows until a solution is in place with Result Set Management
        if (maxRows != -1)
        	max = maxPublished == -1 ? Math.min(maxRows, MAX_ROWS_FETCH) :  Math.min(maxRows, maxPublished);
        else if (maxPublished != -1)
        	max = Math.min(MAX_ROWS_FETCH, maxPublished);

        // We don't know how many items are in the db, so we will start with an allocation of 500
		java.util.LinkedList<PublishedItem> results = new java.util.LinkedList<>();
		boolean descending = JiveGlobals.getBooleanProperty("xmpp.pubsub.order.descending", false);

		try
		{
            con = DbConnectionManager.getConnection();
            // Get published items of the specified node
            pstmt = con.prepareStatement(LOAD_ITEMS);
            pstmt.setMaxRows(max);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            rs = pstmt.executeQuery();
            int counter = 0;

            // Rebuild loaded published items
            while(rs.next() && (counter < max)) {
                String itemID = rs.getString(1);
                JID publisher = new JID(rs.getString(2));
                Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
                // Create the item
                PublishedItem item = new PublishedItem(node, publisher, itemID, creationDate);
                // Add the extra fields to the published item
                if (rs.getString(4) != null) {
                	item.setPayloadXML(rs.getString(4));
                }
                // Add the published item to the node
				if (descending)
					results.add(item);
				else
					results.addFirst(item);
                counter++;
            }
        }
        catch (Exception sqle) {
            log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return results;
    }

    @Override
    public PublishedItem getLastPublishedItem(LeafNode node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        PublishedItem item = null;

        try {
            con = DbConnectionManager.getConnection();
            // Get published items of the specified node
            pstmt = con.prepareStatement(LOAD_LAST_ITEM);
            pstmt.setFetchSize(1);
            pstmt.setMaxRows(1);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            rs = pstmt.executeQuery();
            // Rebuild loaded published items
            if (rs.next()) {
                String itemID = rs.getString(1);
                JID publisher = new JID(rs.getString(2));
                Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
                // Create the item
                item = new PublishedItem(node, publisher, itemID, creationDate);
                // Add the extra fields to the published item
                if (rs.getString(4) != null) {
                	item.setPayloadXML(rs.getString(4));
                }
            }
        }
        catch (Exception sqle) {
            log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return item;
    }

    @Override
    public PublishedItem getPublishedItem(LeafNode node, PublishedItem.UniqueIdentifier itemIdentifier)
    {
        // fetch item from DB
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ITEM);
            pstmt.setString(1, node.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, node.getNodeID());
            pstmt.setString(3, itemIdentifier.getItemId());
            rs = pstmt.executeQuery();

            // Add to each node the corresponding subscriptions
            if (rs.next()) {
                JID publisher = new JID(rs.getString(1));
                Date creationDate = new Date(Long.parseLong(rs.getString(2).trim()));
                // Create the item
                final PublishedItem result = new PublishedItem(node, publisher, itemIdentifier.getItemId(), creationDate);
                // Add the extra fields to the published item
                if (rs.getString(3) != null) {
                    result.setPayloadXML(rs.getString(3));
                }
                log.debug("Loaded item from DB");
                return result;
            }
        } catch (Exception exc) {
            log.error("An exception occurred while trying to obtain item {} from node {}", itemIdentifier.getItemId(), node.getUniqueIdentifier(), exc);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return null;
	}

	@Override
	public void purgeNode(LeafNode leafNode)
	{
		Connection con = null;
		boolean rollback = false;

		try
		{
			con = DbConnectionManager.getTransactionConnection();

			purgeNode(leafNode, con);
		}
		catch (SQLException exc)
		{
			log.error(exc.getMessage(), exc);
			rollback = true;
		}
		finally
		{
			DbConnectionManager.closeTransactionConnection(con, rollback);
		}
	}

	private void purgeNode(LeafNode leafNode, Connection con) throws SQLException
	{
        // Remove published items of the node being deleted
        PreparedStatement pstmt = null;

		try
		{
            pstmt = con.prepareStatement(DELETE_ITEMS);
            pstmt.setString(1, leafNode.getUniqueIdentifier().getServiceIdentifier().getServiceId());
            pstmt.setString(2, encodeNodeID(leafNode.getNodeID()));
            pstmt.executeUpdate();
		}
		finally
		{
			DbConnectionManager.closeStatement(pstmt);
		}
	}

    @Override
    public PEPService loadPEPServiceFromDB(JID jid) {
        PEPService pepService = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            // Get all PEP services
            pstmt = con.prepareStatement(GET_PEP_SERVICE);
            pstmt.setString(1, jid.toString());
            rs = pstmt.executeQuery();
            // Restore old PEPService
            while (rs.next()) {
                String serviceID = rs.getString(1);
                if ( !jid.toString().equals( serviceID )) {
                    log.warn( "Loading a PEP service for {} that has a different name: {}", jid, serviceID );
                }
                // Create a new PEPService
                pepService = new PEPService(XMPPServer.getInstance(), jid);
            }
        } catch (SQLException sqle) {
            log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return pepService;
    }

	private static String encodeWithComma(Collection<String> strings) {
        StringBuilder sb = new StringBuilder(90);
        for (String group : strings) {
            sb.append(group).append(',');
        }
        if (!strings.isEmpty()) {
            sb.setLength(sb.length()-1);
        }
        else {
            // Add a blank so an empty string is never replaced with NULL (oracle...arggg!!!)
            sb.append(' ');
        }
        return sb.toString();
    }

    private static Collection<String> decodeWithComma(String strings) {
        Collection<String> decodedStrings = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(strings.trim(), ",");
        while (tokenizer.hasMoreTokens()) {
            decodedStrings.add(tokenizer.nextToken());
        }
        return decodedStrings;
    }

    private static String encodeNodeID(String nodeID) {
        if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.oracle &&
                "".equals(nodeID)) {
            // Oracle stores empty strings as null so return a string with a space
            return " ";
        }
        return nodeID;
    }

    private static String decodeNodeID(String nodeID) {
        if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.oracle &&
                " ".equals(nodeID)) {
            // Oracle stores empty strings as null so convert them back to empty strings
            return "";
        }
        return nodeID;
    }

    /**
     * Purges all items from the database that exceed the defined item count on
     * all nodes.
     */
    private void purgeItems()
    {
		boolean abortTransaction = false;
        Connection con = null;
		PreparedStatement nodeConfig = null;
		PreparedStatement purgeNode = null;
        ResultSet rs = null;

        try
        {
            con = DbConnectionManager.getTransactionConnection();
			nodeConfig = con.prepareStatement(PERSISTENT_NODES);
            rs = nodeConfig.executeQuery();
            purgeNode = con.prepareStatement(getPurgeStatement(DbConnectionManager.getDatabaseType()));

            boolean hasBatchItems = false;
            while (rs.next())
            {
                hasBatchItems = true;
            	String svcId = rs.getString(1);
            	String nodeId = rs.getString(2);
            	int maxItems = rs.getInt(3);

				setPurgeParams(DbConnectionManager.getDatabaseType(), purgeNode, svcId, nodeId, maxItems);

				purgeNode.addBatch();
            }
			if (hasBatchItems) purgeNode.executeBatch();
		}
		catch (Exception sqle)
		{
		    log.error(sqle.getMessage(), sqle);
			abortTransaction = true;
		}
		finally
		{
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeStatement(nodeConfig);
            DbConnectionManager.closeStatement(purgeNode);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
		}
    }

	private static void setPurgeParams(DatabaseType dbType, PreparedStatement purgeStmt, String serviceId,
			String nodeId, int maxItems) throws SQLException
	{
        if ( dbType == DatabaseType.hsqldb )
        {
            purgeStmt.setString( 1, serviceId );
            purgeStmt.setString( 2, nodeId );
            purgeStmt.setString( 3, serviceId );
            purgeStmt.setString( 4, nodeId );
            purgeStmt.setInt( 5, maxItems );
        }
        else
        {
            purgeStmt.setString( 1, serviceId );
            purgeStmt.setString( 2, nodeId );
            purgeStmt.setInt( 3, maxItems );
            purgeStmt.setString( 4, serviceId );
            purgeStmt.setString( 5, nodeId );
        }
	}

	private static String getPurgeStatement(DatabaseType type)
	{
		switch (type)
		{
		case postgresql:
			return PURGE_FOR_SIZE_POSTGRESQL;
		case mysql:
			return PURGE_FOR_SIZE_MYSQL;
		case hsqldb:
			return PURGE_FOR_SIZE_HSQLDB;
        case oracle:
            return PURGE_FOR_SIZE_ORACLE;
        case sqlserver:
            return PURGE_FOR_SIZE_SQLSERVER;

		default:
			return PURGE_FOR_SIZE;
		}
	}

	@Override
    public void shutdown()
    {
    	log.info("Flushing write cache to database");

		// node cleanup (skip when running as a cluster)
		if (!ClusterManager.isClusteringEnabled()) {
			purgeItems();
		}
    }
}
