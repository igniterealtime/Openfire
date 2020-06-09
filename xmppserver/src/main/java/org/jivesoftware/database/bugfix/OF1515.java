/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.database.bugfix;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SchemaManager;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.pubsub.CollectionNode;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.OnlyPublishers;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class implements a fix for a problem identified as issue OF-1515 in the bugtracker of Openfire.
 *
 * The code in this class is intended to be executed only once, under very strict circumstances. The only class
 * responsible for calling this code should be an instance of {@link SchemaManager}. The database update version
 * corresponding to this fix is 27.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="http://www.igniterealtime.org/issues/browse/OF-1515">Openfire bugtracker: OF-1515</a>
 */
public class OF1515
{
    private static final Logger Log = LoggerFactory.getLogger( OF1515.class );

    public static void executeFix() throws SQLException
    {
        try
        {
            Log.info( "Migrating data from Private XML Storage to Pubsub." );
            final List<PrivateXmlRecord> oldRecords = getPrivateXmlStorageData();
            final List<PubsubRecordData> newRecords = transform( oldRecords );
            toPubsubData( newRecords );
            Log.info( "Finished mgrating data from Private XML Storage to Pubsub. {} records migrated.", newRecords.size() );
        }
        catch ( SQLException e )
        {
            Log.error( "An exception occurred while migrating private XML data to PEP!", e );
            throw e;
        }
    }

    /**
     * Retrieves all data stored using XEP-0049 Private XML Storage
     * @return A collection of data (can be empty, cannot be null).
     */
    private static List<PrivateXmlRecord> getPrivateXmlStorageData() throws SQLException
    {
        Log.info( "Retrieving all data from Private XML Storage." );
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            final List<PrivateXmlRecord> result = new ArrayList<>();

            con = DbConnectionManager.getConnection();
            stmt = con.prepareStatement( "SELECT privateData, name, username, namespace FROM ofPrivate" );
            rs = stmt.executeQuery();
            while ( rs.next() ) {
                result.add( new PrivateXmlRecord( rs.getString( "privateData" ),
                                                  rs.getString( "name" ),
                                                  rs.getString( "username" ),
                                                  rs.getString( "namespace" ) ) );
            }

            return result;
        }
        finally
        {
            DbConnectionManager.closeConnection( rs, stmt, con );
        }
    }

    /**
     * Transforms XML data storage records into Pubsub node records
     *
     * @param oldRecords The records to transform (cannot be null)
     * @return Transformed records (never null, can be empty).
     */
    private static List<PubsubRecordData> transform( List<PrivateXmlRecord> oldRecords )
    {
        Log.info( "Transforming all data from Private XML Storage into Pubsub entities." );
        String domain;
        try
        {
            domain = JiveGlobals.getProperty(XMPPServerInfo.XMPP_DOMAIN.getKey(), JiveGlobals.getXMLProperty( "fqdn", InetAddress.getLocalHost().getCanonicalHostName() ) ).toLowerCase();
        }
        catch ( UnknownHostException e )
        {
            domain = "localhost";
        }
        final List<PubsubRecordData> result = new ArrayList<>();
        for ( final PrivateXmlRecord oldRecord : oldRecords )
        {
            final PubsubRecordData newRecord = new PubsubRecordData( oldRecord.username + '@' + domain, oldRecord.namespace, oldRecord.privateData );
            result.add( newRecord );
        }

        return result;
    }

    /**
     * Creates appropriate database entries for each pubsub record
     * @param newRecords A collection of pubsub representations (can be empty, cannot be null).
     */
    private static void toPubsubData( List<PubsubRecordData> newRecords )
    {
        Log.info( "Writing Pubsub entities." );
        Connection con = null;
        boolean abortTransaction = false;

        try
        {
            con = DbConnectionManager.getTransactionConnection();
            for ( final PubsubRecordData newRecord : newRecords )
            {
                if ( !hasRootNode( con, newRecord.serviceID ) )
                {
                    writeRootNode( con, newRecord.serviceID );
                }
                writeNode( con, newRecord );
                writeItem( con, newRecord );
                writeAffiliation( con, newRecord );
            }
        }
        catch ( SQLException e )
        {
            abortTransaction = true;
        }
        finally
        {
            DbConnectionManager.closeTransactionConnection( con, abortTransaction );
        }
    }

    private static boolean hasRootNode( Connection con, String serviceID ) throws SQLException
    {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            pstmt = con.prepareStatement("SELECT serviceID FROM ofPubsubNode WHERE serviceID = ? AND nodeID = ? AND parent IS NULL" );
            pstmt.setString(1, serviceID);
            pstmt.setString(2, serviceID);
            rs = pstmt.executeQuery();
            return rs.next();
        }
        finally
        {
            DbConnectionManager.fastcloseStmt( rs, pstmt );
        }
    }

    private static void writeRootNode( Connection con, String serviceID ) throws SQLException
    {
        PreparedStatement pstmt = null;
        try
        {
            pstmt = con.prepareStatement( "INSERT INTO ofPubsubNode (serviceID, nodeID, leaf, creationDate, modificationDate, " +
                                              "parent, deliverPayloads, maxPayloadSize, persistItems, maxItems, " +
                                              "notifyConfigChanges, notifyDelete, notifyRetract, presenceBased, " +
                                              "sendItemSubscribe, publisherModel, subscriptionEnabled, configSubscription, " +
                                              "accessModel, payloadType, bodyXSLT, dataformXSLT, creator, description, " +
                                              "language, name, replyPolicy, associationPolicy, maxLeafNodes) " +
                                              "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" );

            // ServiceID
            pstmt.setString( 1, serviceID );

            // NodeID
            pstmt.setString( 2, serviceID );

            // is Leaf?
            pstmt.setInt( 3, 0 );

            // creation date
            pstmt.setString( 4, StringUtils.dateToMillis( new Date() ) );

            // modification date
            pstmt.setString( 5, StringUtils.dateToMillis( new Date() ) );

            // parent
            pstmt.setString( 6, null );

            // deliver Payloads
            pstmt.setInt( 7, 0 );

            // max payload size
            pstmt.setInt( 8, 0 );

            // persist items
            pstmt.setInt( 9, 0 );

            // max items
            pstmt.setInt( 10, 0 );

            // NotifyConfigChanges
            pstmt.setInt( 11, 1 );

            // Notify delete
            pstmt.setInt( 12, 1 );

            // notidfy retract
            pstmt.setInt( 13, 1 );

            // presence based
            pstmt.setInt( 14, 0 );

            // Send item subscribe
            pstmt.setInt( 15, 0 );

            // publisher model
            pstmt.setString( 16, PublisherModel.publishers.getName() );

            // subscritpionEnabled
            pstmt.setInt( 17, 1 );

            // config subscription
            pstmt.setInt( 18, 0 );

            // access model
            pstmt.setString( 19, AccessModel.presence.getName() );

            // payload type
            pstmt.setString( 20, "" );

            // body xslt
            pstmt.setString( 21, "" );

            // dataform xslt
            pstmt.setString( 22, "" );

            // creator
            pstmt.setString( 23, serviceID );

            // description
            pstmt.setString( 24, "" );

            // language
            pstmt.setString( 25, "English" );

            // name
            pstmt.setString( 26, "" );

            // reply policy
            pstmt.setString( 27, null );

            // association policy
            pstmt.setString( 28, CollectionNode.LeafNodeAssociationPolicy.all.name() );

            // max leaf nodes
            pstmt.setInt( 29, -1 );

            pstmt.executeUpdate();
        }
        finally
        {
            DbConnectionManager.fastcloseStmt( pstmt );
        }
    }

    private static void writeNode( Connection con, PubsubRecordData record ) throws SQLException
    {
        PreparedStatement pstmt = null;
        try
        {
            pstmt = con.prepareStatement("INSERT INTO ofPubsubNode (serviceID, nodeID, leaf, creationDate, modificationDate, " +
                                             "parent, deliverPayloads, maxPayloadSize, persistItems, maxItems, " +
                                             "notifyConfigChanges, notifyDelete, notifyRetract, presenceBased, " +
                                             "sendItemSubscribe, publisherModel, subscriptionEnabled, configSubscription, " +
                                             "accessModel, payloadType, bodyXSLT, dataformXSLT, creator, description, " +
                                             "language, name, replyPolicy, associationPolicy, maxLeafNodes) " +
                                             "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" );
            pstmt.setString(1, record.serviceID);
            pstmt.setString(2, record.nodeID);
            pstmt.setInt(3, record.leaf);
            pstmt.setString(4, record.creationDate);
            pstmt.setString(5, record.modificationDate);
            pstmt.setString(6, record.parent);
            pstmt.setInt(7, record.deliverPayloads);
            pstmt.setInt(8, record.maxPayloadSize);
            pstmt.setInt(9, record.persistPublishedItems);
            pstmt.setInt(10, record.maxPublishedItems);
            pstmt.setInt(11, record.notifiedOfConfigChanges);
            pstmt.setInt(12, record.notifiedOfDelete);
            pstmt.setInt(13, record.notifiedOfRetract);
            pstmt.setInt(14, record.presenceBasedDelivery);
            pstmt.setInt(15, record.sendItemsubscribe);
            pstmt.setString(16, record.publisherModel);
            pstmt.setInt(17, record.subscriptionEnabled);
            pstmt.setInt(18, record.subscriptionConfigurationRequired);
            pstmt.setString(19, record.accessModel);
            pstmt.setString(20, record.payloadType);
            pstmt.setString(21, record.bodyXSLT);
            pstmt.setString(22, record.dataformXSLT);
            pstmt.setString(23, record.creator);
            pstmt.setString(24, record.description);
            pstmt.setString(25, record.language);
            pstmt.setString(26, record.name);
            pstmt.setString(27, record.replyPolicy);
            pstmt.setString(28, record.associationPolicy);
            pstmt.setInt(29, record.maxLeafNodes);
            pstmt.executeUpdate();
        }
        finally
        {
            DbConnectionManager.fastcloseStmt( pstmt );
        }
    }

    private static void writeItem( Connection con, PubsubRecordData record ) throws SQLException
    {
        PreparedStatement pstmt = null;
        try
        {
            pstmt = con.prepareStatement("INSERT INTO ofPubsubItem (serviceID,nodeID,id,jid,creationDate,payload) VALUES (?,?,?,?,?,?)");
            pstmt.setString(1, record.serviceID);
            pstmt.setString(2, record.nodeID);
            pstmt.setString(3, record.itemID);
            pstmt.setString(4, record.creator);
            pstmt.setString(5, record.creationDate);
            pstmt.setString(6, record.payload);
            pstmt.executeUpdate();
        }
        finally
        {
            DbConnectionManager.fastcloseStmt( pstmt );
        }
    }

    private static void writeAffiliation( Connection con, PubsubRecordData record ) throws SQLException
    {
        PreparedStatement pstmt = null;
        try
        {
            pstmt = con.prepareStatement("INSERT INTO ofPubsubAffiliation (serviceID,nodeID,jid,affiliation) VALUES (?,?,?,?)" );
            pstmt.setString(1, record.serviceID);
            pstmt.setString(2, record.nodeID);
            pstmt.setString(3, record.creator);
            pstmt.setString( 4, NodeAffiliate.Affiliation.owner.name() );
            pstmt.executeUpdate();
        }
        finally
        {
            DbConnectionManager.fastcloseStmt( pstmt );
        }
    }

    /**
     * Representation of a data record stored in the XML private data storage.
     */
    private static class PrivateXmlRecord
    {
        final String privateData; // which is a string representation of a XML element, but there's no need to deserialize for migration purposes.
        final String name;
        final String username;
        final String namespace;

        private PrivateXmlRecord( String privateData, String name, String username, String namespace )
        {
            this.privateData = privateData;
            this.name = name;
            this.username = username;
            this.namespace = namespace;
        }
    }
    /**
     * Representation of a data record stored as a PEP node.
     */
    private static class PubsubRecordData
    {
        final String serviceID; // JID
        final String nodeID; // namespace
        final int leaf = 1;
        final String creationDate = StringUtils.dateToMillis( new Date() );
        final String modificationDate = creationDate;
        final String parent; // JID
        final int deliverPayloads = 1;
        final int maxPayloadSize = 5120;
        final int persistPublishedItems = 1;
        final int maxPublishedItems = 1;
        final int notifiedOfConfigChanges = 1;
        final int notifiedOfDelete = 1;
        final int notifiedOfRetract = 1;
        final int presenceBasedDelivery = 0;
        final int sendItemsubscribe = 1;
        final String publisherModel = OnlyPublishers.publishers.getName();
        final int subscriptionEnabled = 1;
        final int subscriptionConfigurationRequired = 0;
        final String accessModel = AccessModel.whitelist.getName();
        final String payloadType = "";
        final String bodyXSLT = "";
        final String dataformXSLT = "";
        final String creator; // JID
        final String description = "";
        final String language = "English";
        final String name = "";
        final String replyPolicy = Node.ItemReplyPolicy.owner.name();
        final String associationPolicy = null;
        final int maxLeafNodes = 0;

        final String itemID = "current";
        final String payload;

        private PubsubRecordData( String jid, String namespace, String payload )
        {
            this.serviceID = jid;
            this.nodeID = namespace;
            this.parent = jid;
            this.creator = jid;
            this.payload = payload;
        }
    }
}
