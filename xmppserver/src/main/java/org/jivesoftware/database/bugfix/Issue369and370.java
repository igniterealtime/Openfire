package org.jivesoftware.database.bugfix;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SchemaManager;
import org.jivesoftware.database.SequenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class to handle the migration of data for the monitoring plugin to obtain roomIDs so chat history can be cleared.
 * This is for the features identified as issue 369 and 370 for the Monitoring plugin.
 * <p>
 * The code in this class is intended to be executed only once, under very
 * strict circumstances. The only class responsible for calling this code should
 * be an instance of {@link SchemaManager}. The Monitoring database update version
 * corresponding to this fix is 9.
 *
 * @author Huy Vu
 * @see <a href="https://github.com/igniterealtime/openfire-monitoring-plugin/issues/369">Monitoring plugin issue 369</a>
 * @see <a href="https://github.com/igniterealtime/openfire-monitoring-plugin/issues/370">Monitoring plugin issue 370</a>
 */
public class Issue369and370 {
    private static final Logger Log = LoggerFactory.getLogger(Issue369and370.class);
    private static final SequenceManager roomIDSequenceManager = new SequenceManager(655, 50);

    public static void executeMigration() {
        Log.info("Migrating data to obtain roomIDs so chat can be removed.");

        Map<String, Long> roomJIDToRoomIDMap = new HashMap<>();
        Map<Long, Long> conversationIDToRoomIDMap = new HashMap<>();

        // For conversations that are not external, assign a unique roomID based on roomJID using information from the ofConversation table
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT room, conversationID FROM ofConversation WHERE isExternal = 0 ORDER BY startDate ASC", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = pstmt.executeQuery()) {

            pstmt.setFetchSize(250);
            pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);
            while (rs.next()) {
                String roomJID = rs.getString("room");
                long conversationID = rs.getLong("conversationID");
                long roomID = roomJIDToRoomIDMap.computeIfAbsent(roomJID, k -> roomIDSequenceManager.nextUniqueID());
                conversationIDToRoomIDMap.put(conversationID, roomID);
            }

            pstmt.setFetchSize(250);
            pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);
            Log.debug("1 of 7. Generated room IDs for unique rooms");
        } catch (SQLException e) {
            Log.error("Error querying ofConversation to generate room IDs for unique rooms", e);
        }

        // Update ofConversation table with roomIDs from conversationIDToRoomIDMap
        updateTable("UPDATE ofConversation SET roomID = ? WHERE conversationID = ?", conversationIDToRoomIDMap);
        Log.debug("2 of 7. Updated room IDs in ofConversation table");
        // Update the roomIDs in ofMessageArchive using conversationIDToRoomIDMap
        updateTable("UPDATE ofMessageArchive SET roomID = ? WHERE conversationID = ?", conversationIDToRoomIDMap);
        Log.debug("3 of 7. Updated room IDs in ofMessageArchive table");
        // Update the roomIDs in ofConParticipant using conversationIDToRoomIDMap
        updateTable("UPDATE ofConParticipant SET roomID = ? WHERE conversationID = ?", conversationIDToRoomIDMap);
        Log.debug("4 of 7. Updated room IDs in ofConParticipant table");

        // Insert room information into ofMucRoomStatus table from roomJIDToRoomIDMap
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement("INSERT INTO ofMucRoomStatus (roomID, roomJID, roomDestroyed) VALUES (?, ?, 0)")) {

            for (Map.Entry<String, Long> entry : roomJIDToRoomIDMap.entrySet()) {
                pstmt.setLong(1, entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            Log.debug("5 of 7. Inserted room information into ofMucRoomStatus table");
        } catch (SQLException e) {
            Log.error("Error inserting room information into ofMucRoomStatus table", e);
        }

        // Create an activeMUCs list of the roomJID@subdomain format from the ofMucRoom and ofMucService tables
        List<String> activeMUCs = new ArrayList<>();
        // Join the tables ofMucRoom and ofMucService based on serviceID so we can get the 'name' and 'subdomain' fields
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT ofMucRoom.name, ofMucService.subdomain FROM ofMucRoom JOIN ofMucService ON ofMucRoom.serviceID = ofMucService.serviceID", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = pstmt.executeQuery()) {

            pstmt.setFetchSize(250);
            pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);
            while (rs.next()) {
                String roomName = rs.getString("name");
                String subdomain = rs.getString("subdomain");

                activeMUCs.add(roomName + "@" + subdomain);
                Log.debug("Active MUC: {}", roomName + "@" + subdomain);
            }
            Log.debug("6 of 7. Created list of active MUCs so we can determine which room is destroyed");
        } catch (SQLException e) {
            Log.error("Error joining ofMucRoom and ofMucService tables", e);
        }

        // if roomJID from roomJIDToRoomIDMap is not in activeMUCs, update ofMucRoomStatus to set roomDestroyed to 1
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement("UPDATE ofMucRoomStatus SET roomDestroyed = 1 WHERE roomID = ?")) {
            for (String roomJID : roomJIDToRoomIDMap.keySet()) {
                boolean roomDestroyed = true;
                for (String activeMUCID : activeMUCs) {
                    if (roomJID.startsWith(activeMUCID)) {
                        roomDestroyed = false;
                        break;
                    }
                }
                Log.debug("roomJID={} destroyed={}", roomJID, roomDestroyed);
                if (roomDestroyed) {
                    long roomID = roomJIDToRoomIDMap.get(roomJID);
                    pstmt.setLong(1, roomID);
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
            Log.debug("7 of 7. Updated roomDestroyed status in ofMucRoomStatus table");
        } catch (SQLException e) {
            Log.error("Error updating roomDestroyed status in ofMucRoomStatus table", e);
        }
    }

    private static void updateTable(String query, Map<Long, Long> conversationIDToRoomIDMap) {
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            for (Map.Entry<Long, Long> entry : conversationIDToRoomIDMap.entrySet()) {
                pstmt.setLong(1, entry.getValue());
                pstmt.setLong(2, entry.getKey());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            Log.debug("Updated table with query: {}", query);
        } catch (SQLException e) {
            Log.error("Error updating table with query: {} ", query, e);
        }
    }
}
