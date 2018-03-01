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
package org.jivesoftware.database.bugfix;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SchemaManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.JiveConstants;

/**
 * This class implements a fix for a problem identified as issue OF-33 in the
 * bugtracker of Openfire.
 * <p>
 * The code in this class is intended to be executed only once, under very
 * strict circumstances. The only class responsible for calling this code should
 * be an instance of {@link SchemaManager}. The database update version
 * corresponding to this fix is 21.
 * 
 * @author G&uuml;nther Nie&szlig;
 * @see <a href="http://www.igniterealtime.org/issues/browse/OF-33">Openfire
 *      bugtracker: OF-33</a>
 */
public final class OF33 {

    /**
     * Check and repair the serviceIDs for the ofMucService table.
     * 
     * @param con
     *            the database connection to use to check the MultiUserChat
     *            services.
     */
    public static void executeFix(Connection con) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String answer = null;
        try {
            // Get the current ID for MUC services
            pstmt = con.prepareStatement("SELECT id FROM ofID WHERE idType="
                    + JiveConstants.MUC_SERVICE);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                answer = rs.getString(1);
            }
            rs.close();
            if (answer == null) {
                // No MultiUserService entries are found, so nothing to do.
                return;
            }
            if (answer.equals("1")) {
                // The initial configuration wasn't modified. We must only
                // update the ID.
                pstmt = con
                        .prepareStatement("UPDATE ofID SET id=2 WHERE idType="
                                + JiveConstants.MUC_SERVICE);
                pstmt.executeUpdate();
                return;
            } else {
                // Check for duplicated entries with ID=1.
                String subdomain = null;
                try {
                    pstmt = con
                            .prepareStatement("SELECT subdomain FROM ofMucService WHERE serviceID=1");
                    rs = pstmt.executeQuery();
                    if (rs.next()) {
                        subdomain = rs.getString(1);
                    }
                    if (subdomain == null || !rs.next()) {
                        // No duplicated entries found, so nothing to do.
                        return;
                    }
                    subdomain = rs.getString(1);
                } finally {
                    rs.close();
                }
                // Set new serviceID for duplicated MUC service
                long newID = SequenceManager.nextID(JiveConstants.MUC_SERVICE);
                pstmt = con
                        .prepareStatement("UPDATE ofMucService SET serviceID=? WHERE serviceID=1 AND subdomain=?");
                pstmt.setLong(1, newID);
                pstmt.setString(2, subdomain);
                pstmt.executeUpdate();
                // Copy service properties.
                try {
                    pstmt = con
                            .prepareStatement("SELECT name, propValue FROM ofMucServiceProp WHERE serviceID=1");
                    rs = pstmt.executeQuery();
                    String name = null;
                    String value = null;
                    while (rs.next()) {
                        name = rs.getString(1);
                        value = rs.getString(2);
                        if (name != null && value != null) {
                            pstmt = con
                                    .prepareStatement("INSERT INTO ofMucServiceProp(serviceID, name, propValue) "
                                            + "VALUES(?,?,?)");
                            pstmt.setLong(1, newID);
                            pstmt.setString(2, name);
                            pstmt.setString(3, value);
                            pstmt.executeUpdate();
                        }
                    }
                } finally {
                    rs.close();
                }
                // Copy rooms.
                try {
                    Long roomID, newRoomID;
                    ResultSet roomRS = null;
                    pstmt = con
                            .prepareStatement("SELECT roomID, creationDate, modificationDate, "
                                    + "name, naturalName, description, lockedDate, emptyDate, "
                                    + "canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, "
                                    + "canInvite, roomPassword, canDiscoverJID, logEnabled, subject, "
                                    + "rolesToBroadcast, useReservedNick, canChangeNick, canRegister "
                                    + "FROM ofMucRoom WHERE serviceID=1");
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        roomID = rs.getLong(1);
                        newRoomID = SequenceManager
                                .nextID(JiveConstants.MUC_ROOM);
                        pstmt = con
                                .prepareStatement("INSERT INTO ofMucRoom (serviceID, roomID, "
                                        + "creationDate, modificationDate, name, naturalName, description, "
                                        + "lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, "
                                        + "moderated, membersOnly, canInvite, roomPassword, canDiscoverJID, "
                                        + "logEnabled, subject, rolesToBroadcast, useReservedNick, "
                                        + "canChangeNick, canRegister) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                        pstmt.setLong(1, newID);
                        pstmt.setLong(2, newRoomID);
                        pstmt.setString(3, rs.getString(2));
                        pstmt.setString(4, rs.getString(3));
                        pstmt.setString(5, rs.getString(4));
                        pstmt.setString(6, rs.getString(5));
                        pstmt.setString(7, rs.getString(6));
                        pstmt.setString(8, rs.getString(7));
                        pstmt.setString(9, rs.getString(8));
                        pstmt.setInt(10, rs.getInt(9));
                        pstmt.setInt(11, rs.getInt(10));
                        pstmt.setInt(12, rs.getInt(11));
                        pstmt.setInt(13, rs.getInt(12));
                        pstmt.setInt(14, rs.getInt(13));
                        pstmt.setInt(15, rs.getInt(14));
                        pstmt.setString(16, rs.getString(15));
                        pstmt.setInt(17, rs.getInt(16));
                        pstmt.setInt(18, rs.getInt(17));
                        pstmt.setString(19, rs.getString(18));
                        pstmt.setInt(20, rs.getInt(19));
                        pstmt.setInt(21, rs.getInt(20));
                        pstmt.setInt(22, rs.getInt(21));
                        pstmt.setInt(23, rs.getInt(22));
                        pstmt.executeUpdate();
                        // Copy room properties.
                        try {
                            pstmt = con
                                    .prepareStatement("SELECT name, propValue FROM ofMucRoomProp WHERE roomID=?");
                            pstmt.setLong(1, roomID);
                            roomRS = pstmt.executeQuery();
                            String name = null;
                            String value = null;
                            while (roomRS.next()) {
                                name = roomRS.getString(1);
                                value = roomRS.getString(2);
                                if (name != null && value != null) {
                                    pstmt = con
                                            .prepareStatement("INSERT INTO ofMucRoomProp(roomID, name, propValue) "
                                                    + "VALUES(?,?,?)");
                                    pstmt.setLong(1, newRoomID);
                                    pstmt.setString(2, name);
                                    pstmt.setString(3, value);
                                    pstmt.executeUpdate();
                                }
                            }
                        } finally {
                            roomRS.close();
                        }
                        // Copy affiliations.
                        try {
                            pstmt = con
                                    .prepareStatement("SELECT jid, affiliation FROM ofMucAffiliation WHERE roomID=?");
                            pstmt.setLong(1, roomID);
                            roomRS = pstmt.executeQuery();
                            while (roomRS.next()) {
                                pstmt = con
                                        .prepareStatement("INSERT INTO ofMucAffiliation(roomID, jid, affiliation) "
                                                + "VALUES(?,?,?)");
                                pstmt.setLong(1, newRoomID);
                                pstmt.setString(2, roomRS.getString(1));
                                pstmt.setInt(3, roomRS.getInt(2));
                                pstmt.executeUpdate();
                            }
                        } finally {
                            roomRS.close();
                        }
                        // Copy members.
                        try {
                            pstmt = con
                                    .prepareStatement("SELECT jid, nickname, firstName, lastName, url, email, faqentry "
                                            + "FROM ofMucMember WHERE roomID=?");
                            pstmt.setLong(1, roomID);
                            roomRS = pstmt.executeQuery();
                            while (roomRS.next()) {
                                pstmt = con
                                        .prepareStatement("INSERT INTO ofMucMember(roomID, jid, nickname, firstName, "
                                                + "lastName, url, email, faqentry) VALUES(?,?,?,?,?,?,?,?)");
                                pstmt.setLong(1, newRoomID);
                                pstmt.setString(2, roomRS.getString(1));
                                pstmt.setString(3, roomRS.getString(2));
                                pstmt.setString(4, roomRS.getString(3));
                                pstmt.setString(5, roomRS.getString(4));
                                pstmt.setString(6, roomRS.getString(5));
                                pstmt.setString(7, roomRS.getString(6));
                                pstmt.setString(8, roomRS.getString(7));
                                pstmt.executeUpdate();
                            }
                        } finally {
                            roomRS.close();
                        }
                        // Copy conversation history.
                        try {
                            pstmt = con
                                    .prepareStatement("SELECT sender, nickname, logTime, subject, body "
                                            + "FROM ofMucConversationLog WHERE roomID=?");
                            pstmt.setLong(1, roomID);
                            roomRS = pstmt.executeQuery();
                            while (roomRS.next()) {
                                pstmt = con
                                        .prepareStatement("INSERT INTO ofMucConversationLog(roomID, "
                                                + "sender, nickname, logTime, subject, body VALUES(?,?,?,?,?,?)");
                                pstmt.setLong(1, newRoomID);
                                pstmt.setString(2, roomRS.getString(1));
                                pstmt.setString(3, roomRS.getString(2));
                                pstmt.setString(4, roomRS.getString(3));
                                pstmt.setString(5, roomRS.getString(4));
                                pstmt.setString(6, roomRS.getString(5));
                                pstmt.executeUpdate();
                            }
                        } finally {
                            roomRS.close();
                        }
                    }
                } finally {
                    rs.close();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

}
