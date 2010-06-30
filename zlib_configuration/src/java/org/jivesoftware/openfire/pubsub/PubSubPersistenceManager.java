/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
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

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * A manager responsible for ensuring node persistence.
 *
 * @author Matt Tucker
 */
public class PubSubPersistenceManager {

	private static final Logger Log = LoggerFactory.getLogger(PubSubPersistenceManager.class);

    private static final String LOAD_NON_LEAF_NODES =
            "SELECT nodeID, leaf, creationDate, modificationDate, parent, deliverPayloads, " +
            "maxPayloadSize, persistItems, maxItems, notifyConfigChanges, notifyDelete, " +
            "notifyRetract, presenceBased, sendItemSubscribe, publisherModel, " +
            "subscriptionEnabled, configSubscription, accessModel, payloadType, " +
            "bodyXSLT, dataformXSLT, creator, description, language, name, " +
            "replyPolicy, associationPolicy, maxLeafNodes FROM ofPubsubNode " +
            "WHERE serviceID=? AND leaf=0 ORDER BY nodeID";
    private static final String LOAD_LEAF_NODES =
            "SELECT nodeID, leaf, creationDate, modificationDate, parent, deliverPayloads, " +
            "maxPayloadSize, persistItems, maxItems, notifyConfigChanges, notifyDelete, " +
            "notifyRetract, presenceBased, sendItemSubscribe, publisherModel, " +
            "subscriptionEnabled, configSubscription, accessModel, payloadType, " +
            "bodyXSLT, dataformXSLT, creator, description, language, name, " +
            "replyPolicy, associationPolicy, maxLeafNodes FROM ofPubsubNode " +
            "WHERE serviceID=? AND leaf=1 ORDER BY nodeID";
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
    private static final String ADD_NODE_JIDS =
            "INSERT INTO ofPubsubNodeJIDs (serviceID, nodeID, jid, associationType) " +
            "VALUES (?,?,?,?)";
    private static final String DELETE_NODE_JIDS =
            "DELETE FROM ofPubsubNodeJIDs WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_NODES_GROUPS =
            "SELECT nodeID, rosterGroup FROM ofPubsubNodeGroups WHERE serviceID=?";
    private static final String ADD_NODE_GROUPS =
            "INSERT INTO ofPubsubNodeGroups (serviceID, nodeID, rosterGroup) " +
            "VALUES (?,?,?)";
    private static final String DELETE_NODE_GROUPS =
            "DELETE FROM ofPubsubNodeGroups WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_AFFILIATIONS =
            "SELECT nodeID,jid,affiliation FROM ofPubsubAffiliation WHERE serviceID=? " +
            "ORDER BY nodeID";
    private static final String ADD_AFFILIATION =
            "INSERT INTO ofPubsubAffiliation (serviceID,nodeID,jid,affiliation) VALUES (?,?,?,?)";
    private static final String UPDATE_AFFILIATION =
            "UPDATE ofPubsubAffiliation SET affiliation=? WHERE serviceID=? AND nodeID=? AND jid=?";
    private static final String DELETE_AFFILIATION =
            "DELETE FROM ofPubsubAffiliation WHERE serviceID=? AND nodeID=? AND jid=?";
    private static final String DELETE_AFFILIATIONS =
            "DELETE FROM ofPubsubAffiliation WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_SUBSCRIPTIONS =
            "SELECT nodeID, id, jid, owner, state, deliver, digest, digest_frequency, " +
            "expire, includeBody, showValues, subscriptionType, subscriptionDepth, " +
            "keyword FROM ofPubsubSubscription WHERE serviceID=? ORDER BY nodeID";
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

    private static final String LOAD_ALL_ITEMS =
            "SELECT id,jid,creationDate,payload,nodeID FROM ofPubsubItem " +
            "WHERE serviceID=? ORDER BY creationDate";
    private static final String LOAD_ITEMS =
            "SELECT id,jid,creationDate,payload FROM ofPubsubItem " +
            "WHERE serviceID=? AND nodeID=? ORDER BY creationDate";
    private static final String ADD_ITEM =
            "INSERT INTO ofPubsubItem (serviceID,nodeID,id,jid,creationDate,payload) " +
            "VALUES (?,?,?,?,?,?)";
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
            "accessModel=?, language=? replyPolicy=?, associationPolicy=?, maxLeafNodes=? " +
            "WHERE serviceID=? AND leaf=?";
    private static final String ADD_DEFAULT_CONF =
            "INSERT INTO ofPubsubDefaultConf (serviceID, leaf, deliverPayloads, maxPayloadSize, " +
            "persistItems, maxItems, notifyConfigChanges, notifyDelete, notifyRetract, " +
            "presenceBased, sendItemSubscribe, publisherModel, subscriptionEnabled, " +
            "accessModel, language, replyPolicy, associationPolicy, maxLeafNodes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final int POOL_SIZE = 50; 
    
    /**
     * Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers.
     */
    private static BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<SAXReader>(POOL_SIZE);

    static {
        // Initialize the pool of sax readers
        for (int i=0; i<POOL_SIZE; i++) {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlReaders.add(xmlReader);
        }
    }

    /**
     * Creates and stores the node configuration in the database.
     *
     * @param service The pubsub service that is hosting the node.
     * @param node The newly created node.
     */
    public static void createNode(PubSubService service, Node node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(ADD_NODE);
            pstmt.setString(1, service.getServiceID());
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
            saveAssociatedElements(con, node, service);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Updates the node configuration in the database.
     *
     * @param service The pubsub service that is hosting the node.
     * @param node The updated node.
     */
    public static void updateNode(PubSubService service, Node node) {
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
            pstmt.setString(25, service.getServiceID());
            pstmt.setString(26, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove existing JIDs associated with the the node
            pstmt = con.prepareStatement(DELETE_NODE_JIDS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove roster groups associated with the the node being deleted
            pstmt = con.prepareStatement(DELETE_NODE_GROUPS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();

            // Save associated JIDs and roster groups
            saveAssociatedElements(con, node, service);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    private static void saveAssociatedElements(Connection con, Node node,
            PubSubService service) throws SQLException {
        // Add new JIDs associated with the the node
        PreparedStatement pstmt = con.prepareStatement(ADD_NODE_JIDS);
        try {
            for (JID jid : node.getContacts()) {
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, jid.toString());
                pstmt.setString(4, "contacts");
                pstmt.executeUpdate();
            }
            for (JID jid : node.getReplyRooms()) {
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, jid.toString());
                pstmt.setString(4, "replyRooms");
                pstmt.executeUpdate();
            }
            for (JID jid : node.getReplyTo()) {
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, jid.toString());
                pstmt.setString(4, "replyTo");
                pstmt.executeUpdate();
            }
            if (node.isCollectionNode()) {
                for (JID jid : ((CollectionNode) node).getAssociationTrusted()) {
                    pstmt.setString(1, service.getServiceID());
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
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, groupName);
                pstmt.executeUpdate();
            }
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    /**
     * Removes the specified node from the DB.
     *
     * @param service The pubsub service that is hosting the node.
     * @param node The node that is being deleted.
     * @return true If the operation was successful.
     */
    public static boolean removeNode(PubSubService service, Node node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_NODE);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove JIDs associated with the the node being deleted
            pstmt = con.prepareStatement(DELETE_NODE_JIDS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove roster groups associated with the the node being deleted
            pstmt = con.prepareStatement(DELETE_NODE_GROUPS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove published items of the node being deleted
            pstmt = con.prepareStatement(DELETE_ITEMS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.closeStatement(pstmt);

            // Remove all affiliates from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_AFFILIATIONS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove users that were subscribed to the node
            pstmt = con.prepareStatement(DELETE_SUBSCRIPTIONS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        return !abortTransaction;
    }

    /**
     * Loads all nodes from the database and adds them to the PubSub service.
     *
     * @param service the pubsub service that is hosting the nodes.
     */
    public static void loadNodes(PubSubService service) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Map<String, Node> nodes = new HashMap<String, Node>();
        try {
            con = DbConnectionManager.getConnection();
            // Get all non-leaf nodes (to ensure parent nodes are loaded before their children)
            pstmt = con.prepareStatement(LOAD_NON_LEAF_NODES);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Rebuild loaded non-leaf nodes
            while(rs.next()) {
                loadNode(service, nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // Get all leaf nodes (remaining unloaded nodes)
            pstmt = con.prepareStatement(LOAD_LEAF_NODES);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Rebuild loaded leaf nodes
            while(rs.next()) {
                loadNode(service, nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

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
                loadSubscriptions(service, nodes, rs);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // TODO We may need to optimize memory consumption and load items on-demand
            // Load published items of all nodes
            pstmt = con.prepareStatement(LOAD_ALL_ITEMS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the correspondiding subscriptions
            while(rs.next()) {
                loadItems(nodes, rs);
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
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

    private static void loadNode(PubSubService service, Map<String, Node> loadedNodes,
            ResultSet rs) {
        Node node;
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            boolean leaf = rs.getInt(2) == 1;
            String parent = decodeNodeID(rs.getString(5));
            JID creator = new JID(rs.getString(22));
            CollectionNode parentNode = null;
            if (parent != null) {
                // Check if the parent has already been loaded
                parentNode = (CollectionNode) loadedNodes.get(parent);
                if (parentNode == null) {
                    // Parent is not in memory so try to load it
                    Log.warn("Node not loaded due to missing parent. NodeID: " + nodeID);
                    return;
                }
            }

            if (leaf) {
                // Retrieving a leaf node
                node = new LeafNode(service, parentNode, nodeID, creator);
            }
            else {
                // Retrieving a collection node
                node = new CollectionNode(service, parentNode, nodeID, creator);
            }
            node.setCreationDate(new Date(Long.parseLong(rs.getString(3).trim())));
            node.setModificationDate(new Date(Long.parseLong(rs.getString(4).trim())));
            node.setPayloadDelivered(rs.getInt(6) == 1);
            if (leaf) {
                ((LeafNode) node).setMaxPayloadSize(rs.getInt(7));
                ((LeafNode) node).setPersistPublishedItems(rs.getInt(8) == 1);
                ((LeafNode) node).setMaxPublishedItems(rs.getInt(9));
                ((LeafNode) node).setSendItemSubscribe(rs.getInt(14) == 1);
            }
            node.setNotifiedOfConfigChanges(rs.getInt(10) == 1);
            node.setNotifiedOfDelete(rs.getInt(11) == 1);
            node.setNotifiedOfRetract(rs.getInt(12) == 1);
            node.setPresenceBasedDelivery(rs.getInt(13) == 1);
            node.setPublisherModel(PublisherModel.valueOf(rs.getString(15)));
            node.setSubscriptionEnabled(rs.getInt(16) == 1);
            node.setSubscriptionConfigurationRequired(rs.getInt(17) == 1);
            node.setAccessModel(AccessModel.valueOf(rs.getString(18)));
            node.setPayloadType(rs.getString(19));
            node.setBodyXSLT(rs.getString(20));
            node.setDataformXSLT(rs.getString(21));
            node.setDescription(rs.getString(23));
            node.setLanguage(rs.getString(24));
            node.setName(rs.getString(25));
            if (rs.getString(26) != null) {
                node.setReplyPolicy(Node.ItemReplyPolicy.valueOf(rs.getString(26)));
            }
            if (!leaf) {
                ((CollectionNode) node).setAssociationPolicy(
                        CollectionNode.LeafNodeAssociationPolicy.valueOf(rs.getString(27)));
                ((CollectionNode) node).setMaxLeafNodes(rs.getInt(28));
            }

            // Add the load to the list of loaded nodes
            loadedNodes.put(node.getNodeID(), node);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
    }

    private static void loadAssociatedJIDs(Map<String, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = nodes.get(nodeID);
            if (node == null) {
                Log.warn("JID associated to a non-existent node: " + nodeID);
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
            Log.error(ex.getMessage(), ex);
        }
    }

    private static void loadAssociatedGroups(Map<String, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = nodes.get(nodeID);
            if (node == null) {
                Log.warn("Roster Group associated to a non-existent node: " + nodeID);
                return;
            }
            node.addAllowedRosterGroup(rs.getString(2));
        }
        catch (SQLException ex) {
            Log.error(ex.getMessage(), ex);
        }
    }

    private static void loadAffiliations(Map<String, Node> nodes, ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = nodes.get(nodeID);
            if (node == null) {
                Log.warn("Affiliations found for a non-existent node: " + nodeID);
                return;
            }
            NodeAffiliate affiliate = new NodeAffiliate(node, new JID(rs.getString(2)));
            affiliate.setAffiliation(NodeAffiliate.Affiliation.valueOf(rs.getString(3)));
            node.addAffiliate(affiliate);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
    }

    private static void loadSubscriptions(PubSubService service, Map<String, Node> nodes,
            ResultSet rs) {
        try {
            String nodeID = decodeNodeID(rs.getString(1));
            Node node = nodes.get(nodeID);
            if (node == null) {
                Log.warn("Subscription found for a non-existent node: " + nodeID);
                return;
            }
            String subID = rs.getString(2);
            JID subscriber = new JID(rs.getString(3));
            JID owner = new JID(rs.getString(4));
            if (node.getAffiliate(owner) == null) {
                Log.warn("Subscription found for a non-existent affiliate: " + owner +
                        " in node: " + nodeID);
                return;
            }
            NodeSubscription.State state = NodeSubscription.State.valueOf(rs.getString(5));
            NodeSubscription subscription =
                    new NodeSubscription(service, node, owner, subscriber, state, subID);
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
            Log.error(sqle.getMessage(), sqle);
        }
    }

    private static void loadItems(Map<String, Node> nodes, ResultSet rs) {
        SAXReader xmlReader = null;
        try {
            // Get a sax reader from the pool
            xmlReader = xmlReaders.take();

            String nodeID = decodeNodeID(rs.getString(5));
            LeafNode node = (LeafNode) nodes.get(nodeID);
            if (node == null) {
                Log.warn("Published Item found for a non-existent node: " + nodeID);
                return;
            }
            String itemID = rs.getString(1);
            JID publisher = new JID(rs.getString(2));
            Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
            // Create the item
            PublishedItem item = new PublishedItem(node, publisher, itemID, creationDate);
            // Add the extra fields to the published item
            if (rs.getString(4) != null) {
                item.setPayload(
                        xmlReader.read(new StringReader(rs.getString(4))).getRootElement());
            }
            // Add the published item to the node
            node.addPublishedItem(item);
        }
        catch (Exception sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            // Return the sax reader to the pool
            if (xmlReader != null) {
                xmlReaders.add(xmlReader);
            }
        }
    }

    /**
     * Update the DB with the new affiliation of the user in the node.
     *
     * @param service   The pubsub service that is hosting the node.
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The new affiliation of the user in the node.
     * @param create    True if this is a new affiliate.
     */
    public static void saveAffiliation(PubSubService service, Node node, NodeAffiliate affiliate,
            boolean create) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (create) {
                // Add the user to the generic affiliations table
                pstmt = con.prepareStatement(ADD_AFFILIATION);
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, encodeNodeID(node.getNodeID()));
                pstmt.setString(3, affiliate.getJID().toString());
                pstmt.setString(4, affiliate.getAffiliation().name());
                pstmt.executeUpdate();
            }
            else {
                // Update the affiliate's data in the backend store
                pstmt = con.prepareStatement(UPDATE_AFFILIATION);
                pstmt.setString(1, affiliate.getAffiliation().name());
                pstmt.setString(2, service.getServiceID());
                pstmt.setString(3, encodeNodeID(node.getNodeID()));
                pstmt.setString(4, affiliate.getJID().toString());
                pstmt.executeUpdate();
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Removes the affiliation and subsription state of the user from the DB.
     *
     * @param service   The pubsub service that is hosting the node.
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The existing affiliation and subsription state of the user in the node.
     */
    public static void removeAffiliation(PubSubService service, Node node,
            NodeAffiliate affiliate) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_AFFILIATION);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.setString(3, affiliate.getJID().toString());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates the DB with the new subsription of the user to the node.
     *
     * @param service   The pubsub service that is hosting the node.
     * @param node      The node where the user has subscribed to.
     * @param subscription The new subscription of the user to the node.
     * @param create    True if this is a new affiliate.
     */
    public static void saveSubscription(PubSubService service, Node node,
            NodeSubscription subscription, boolean create) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (create) {
                // Add the subscription of the user to the database
                pstmt = con.prepareStatement(ADD_SUBSCRIPTION);
                pstmt.setString(1, service.getServiceID());
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
            else {
                if (NodeSubscription.State.none == subscription.getState()) {
                    // Remove the subscription of the user from the table
                    pstmt = con.prepareStatement(DELETE_SUBSCRIPTION);
                    pstmt.setString(1, service.getServiceID());
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
                    pstmt.setString(12, service.getServiceID());
                    pstmt.setString(13, encodeNodeID(node.getNodeID()));
                    pstmt.setString(14, subscription.getID());
                    pstmt.executeUpdate();
                }
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Removes the subscription of the user from the DB.
     *
     * @param service     The pubsub service that is hosting the node.
     * @param node        The node where the user was subscribed to.
     * @param subscription The existing subsription of the user to the node.
     */
    public static void removeSubscription(PubSubService service, Node node,
            NodeSubscription subscription) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_SUBSCRIPTION);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            pstmt.setString(3, subscription.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Loads and adds the published items to the specified node.
     *
     * @param service the pubsub service that is hosting the node.
     * @param node the leaf node to load its published items.
     */
    public static void loadItems(PubSubService service, LeafNode node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        SAXReader xmlReader = null;
        try {
            // Get a sax reader from the pool
            xmlReader = xmlReaders.take();
            con = DbConnectionManager.getConnection();
            // Get published items of the specified node
            pstmt = con.prepareStatement(LOAD_ITEMS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(node.getNodeID()));
            rs = pstmt.executeQuery();
            // Rebuild loaded published items
            while(rs.next()) {
                String itemID = rs.getString(1);
                JID publisher = new JID(rs.getString(2));
                Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
                // Create the item
                PublishedItem item = new PublishedItem(node, publisher, itemID, creationDate);
                // Add the extra fields to the published item
                if (rs.getString(4) != null) {
                    item.setPayload(
                            xmlReader.read(new StringReader(rs.getString(4))).getRootElement());
                }
                // Add the published item to the node
                node.addPublishedItem(item);
            }
        }
        catch (Exception sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            // Return the sax reader to the pool
            if (xmlReader != null) {
                xmlReaders.add(xmlReader);
            }
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Creates and stores the published item in the database.
     *
     * @param service the pubsub service that is hosting the node.
     * @param item The published item to save.
     * @return true if the item was successfully saved to the database.
     */
    public static boolean createPublishedItem(PubSubService service, PublishedItem item) {
        boolean success = false;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the published item from the database
            pstmt = con.prepareStatement(ADD_ITEM);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(item.getNode().getNodeID()));
            pstmt.setString(3, item.getID());
            pstmt.setString(4, item.getPublisher().toString());
            pstmt.setString(5, StringUtils.dateToMillis(item.getCreationDate()));
            pstmt.setString(6, item.getPayloadXML());
            pstmt.executeUpdate();
            // Set that the item was successfully saved to the database
            success = true;
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return success;
    }

    /**
     * Removes the specified published item from the DB.
     *
     * @param service the pubsub service that is hosting the node.
     * @param item The published item to delete.
     * @return true if the item was successfully deleted from the database.
     */
    public static boolean removePublishedItem(PubSubService service, PublishedItem item) {
        boolean success = false;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the published item from the database
            pstmt = con.prepareStatement(DELETE_ITEM);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, encodeNodeID(item.getNode().getNodeID()));
            pstmt.setString(3, item.getID());
            pstmt.executeUpdate();
            // Set that the item was successfully deleted from the database
            success = true;
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return success;
    }

    /**
     * Loads from the database the default node configuration for the specified node type
     * and pubsub service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param isLeafType true if loading default configuration for leaf nodes.
     * @return the loaded default node configuration for the specified node type and service
     *         or <tt>null</tt> if none was found.
     */
    public static DefaultNodeConfiguration loadDefaultConfiguration(PubSubService service,
            boolean isLeafType) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        DefaultNodeConfiguration config = null;
        try {
            con = DbConnectionManager.getConnection();
            // Get default node configuration for the specified service
            pstmt = con.prepareStatement(LOAD_DEFAULT_CONF);
            pstmt.setString(1, service.getServiceID());
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
        }
        catch (Exception sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return config;
    }

    /**
     * Creates a new default node configuration for the specified service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param config the default node configuration to create in the database.
     */
    public static void createDefaultConfiguration(PubSubService service,
            DefaultNodeConfiguration config) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_DEFAULT_CONF);
            pstmt.setString(1, service.getServiceID());
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
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates the default node configuration for the specified service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param config the default node configuration to update in the database.
     */
    public static void updateDefaultConfiguration(PubSubService service,
            DefaultNodeConfiguration config) {
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
            pstmt.setString(17, service.getServiceID());
            pstmt.setInt(18, (config.isLeaf() ? 1 : 0));
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /*public static Node loadNode(PubSubService service, String nodeID) {
        Connection con = null;
        Node node = null;
        try {
            con = DbConnectionManager.getConnection();
            node = loadNode(service, nodeID, con);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            try { if (con != null) con:close(); }
            catch (Exception e) { Log.error(e.getMessage(), e); }
        }
        return node;
    }

    private static Node loadNode(PubSubService service, String nodeID, Connection con) {
        Node node = null;
        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement(LOAD_NODE);
            pstmt.setString(1, encodeNodeID(nodeID));
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                // No node was found for the specified nodeID so return null
                return null;
            }
            boolean leaf = rs.getInt(1) == 1;
            String parent = decodeNodeID(rs.getString(4));
            JID creator = new JID(rs.getString(20));
            CollectionNode parentNode = null;
            if (parent != null) {
                // Check if the parent has already been loaded
                parentNode = (CollectionNode) service.getNode(parent);
                if (parentNode == null) {
                    // Parent is not in memory so try to load it
                    synchronized (parent.intern()) {
                        // Check again if parent has not been already loaded (concurrency issues)
                        parentNode = (CollectionNode) service.getNode(parent);
                        if (parentNode == null) {
                            // Parent was never loaded so load it from the database now
                            parentNode = (CollectionNode) loadNode(service, parent, con);
                        }
                    }
                }
            }

            if (leaf) {
                // Retrieving a leaf node
                node = new LeafNode(parentNode, nodeID, creator);
            }
            else {
                // Retrieving a collection node
                node = new CollectionNode(parentNode, nodeID, creator);
            }
            node.setCreationDate(new Date(Long.parseLong(rs.getString(2).trim())));
            node.setModificationDate(new Date(Long.parseLong(rs.getString(3).trim())));
            node.setPayloadDelivered(rs.getInt(5) == 1);
            node.setMaxPayloadSize(rs.getInt(6));
            node.setPersistPublishedItems(rs.getInt(7) == 1);
            node.setMaxPublishedItems(rs.getInt(8));
            node.setNotifiedOfConfigChanges(rs.getInt(9) == 1);
            node.setNotifiedOfDelete(rs.getInt(10) == 1);
            node.setNotifiedOfRetract(rs.getInt(11) == 1);
            node.setPresenceBasedDelivery(rs.getInt(12) == 1);
            node.setSendItemSubscribe(rs.getInt(13) == 1);
            node.setPublisherModel(Node.PublisherModel.valueOf(rs.getString(14)));
            node.setSubscriptionEnabled(rs.getInt(15) == 1);
            node.setAccessModel(Node.AccessModel.valueOf(rs.getString(16)));
            node.setPayloadType(rs.getString(17));
            node.setBodyXSLT(rs.getString(18));
            node.setDataformXSLT(rs.getString(19));
            node.setDescription(rs.getString(21));
            node.setLanguage(rs.getString(22));
            node.setName(rs.getString(23));
            rs:close();
            pstmt:close();

            pstmt = con.prepareStatement(LOAD_HISTORY);
            // Recreate the history until two days ago
            long from = System.currentTimeMillis() - (86400000 * 2);
            pstmt.setString(1, StringUtils.dateToMillis(new Date(from)));
            pstmt.setLong(2, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                // Recreate the history only for the rooms that have the conversation logging
                // enabled
                if (room.isLogEnabled()) {
                    room.getRoomHistory().addOldMessage(senderJID, nickname, sentDate, subject,
                            body);
                }
            }
            rs:close();
            pstmt:close();

            pstmt = con.prepareStatement(LOAD_NODE_AFFILIATIONS);
            pstmt.setString(1, encodeNodeID(node.getNodeID()));
            rs = pstmt.executeQuery();
            while (rs.next()) {
                NodeAffiliate affiliate = new NodeAffiliate(new JID(rs.getString(1)));
                affiliate.setAffiliation(NodeAffiliate.Affiliation.valueOf(rs.getString(2)));
                affiliate.setSubscription(NodeAffiliate.State.valueOf(rs.getString(3)));
                node.addAffiliate(affiliate);
            }
            rs:close();

            // Set now that the room's configuration is updated in the database. Note: We need to
            // set this now since otherwise the room's affiliations will be saved to the database
            // "again" while adding them to the room!
            node.setSavedToDB(true);

            // Add the retrieved node to the pubsub service
            service.addChildNode(node);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            try { if (pstmt != null) pstmt:close(); }
            catch (Exception e) { Log.error(e.getMessage(), e); }
            try { if (con != null) con:close(); }
            catch (Exception e) { Log.error(e.getMessage(), e); }
        }
        return node;
    }*/

    private static String encodeWithComma(Collection<String> strings) {
        StringBuilder sb = new StringBuilder(90);
        for (String group : strings) {
            sb.append(group).append(",");
        }
        if (!strings.isEmpty()) {
            sb.setLength(sb.length()-1);
        }
        else {
            // Add a blank so an empty string is never replaced with NULL (oracle...arggg!!!)
            sb.append(" ");
        }
        return sb.toString();
    }

    private static Collection<String> decodeWithComma(String strings) {
        Collection<String> decodedStrings = new ArrayList<String>();
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
}
