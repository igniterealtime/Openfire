/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chatbot.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.ChatbotInfo;
import org.jivesoftware.messenger.chatbot.ChatbotInfoProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DbChatbotInfoProvider implements ChatbotInfoProvider {
    public DbChatbotInfoProvider() {
    }

    private static final String LOAD_CHATBOT_BY_ID =
            "SELECT description, creationDate, modificationDate FROM jiveChatbot WHERE chatbotID=?";

    public ChatbotInfo getInfo(long id) throws UserNotFoundException {
        BasicChatbotInfo userInfo = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CHATBOT_BY_ID);
            pstmt.setLong(1, id);

            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            // We trim() the dates before trying to parse them because some
            // databases pad with extra characters when returning the data.
            userInfo = new BasicChatbotInfo(id,
                    rs.getString(1), // description
                    new java.util.Date(Long.parseLong(rs.getString(2).trim())), // creation date
                    new java.util.Date(Long.parseLong(rs.getString(3).trim()))); // modification date

        }
        catch (SQLException e) {
            throw new UserNotFoundException("Failed to read user " + id + " from database.", e);
        }
        catch (NumberFormatException nfe) {
            Log.error("WARNING: There was an error parsing the dates " +
                    "returned from the database. Ensure that they're being stored " +
                    "correctly.");
            throw new UserNotFoundException("User with id "
                    + id + " could not be loaded from the database.");
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return userInfo;
    }

    private static final String UPDATE_CHATBOT =
            "UPDATE jiveChatbot SET description=?, creationDate=?, modificationDate=? WHERE chatbotID=?";

    public void updateInfo(long id, ChatbotInfo info) throws UserNotFoundException, UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_CHATBOT);
            pstmt.setString(1, info.getDescription());
            pstmt.setString(2, StringUtils.dateToMillis(info.getCreationDate()));
            pstmt.setString(3, StringUtils.dateToMillis(info.getModificationDate()));
            pstmt.setLong(4, id);
            pstmt.execute();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }
}
