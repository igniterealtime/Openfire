/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.chatbot.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.ChatbotAccountProvider;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.spi.DbUserIDProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;


// TODO: this class is a copy of user id provider except for objecttype. Need to create a base
// class and just parameterize the object type.

public class DbChatbotAccountProvider implements ChatbotAccountProvider {
    private static final String INSERT_CHATBOT_ID =
            "INSERT INTO jiveUserID (username,domainID,objectType,objectID) VALUES (?,?,?,?)";
    private static final String INSERT_CHATBOT_INFO =
            "INSERT INTO jiveChatbot (chatbotID,description,creationDate,modificationDate) VALUES (?,?,?,?)";

    public long createChatbot(String username)
            throws UnauthorizedException, UserAlreadyExistsException {
        long id = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {

            Date now = new Date();
            // Reserve the name in the jiveUserID
            id = SequenceManager.nextID(JiveConstants.USER);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_CHATBOT_ID);
            pstmt.setString(1, username);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.setLong(3, DbUserIDProvider.CHATBOT_TYPE);
            pstmt.setLong(4, id);
            pstmt.executeUpdate();
            pstmt = con.prepareStatement(INSERT_CHATBOT_INFO);
            pstmt.setLong(1, id);
            pstmt.setString(2, "None");
            pstmt.setString(3, StringUtils.dateToMillis(now));
            pstmt.setString(4, StringUtils.dateToMillis(now));
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
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
        return id;
    }

    private static final String DELETE_CHATBOT_ID =
            "DELETE FROM jiveUserID WHERE objectID=? AND objectType=? and domainID=?";
    private static final String DELETE_CHATBOT_INFO =
            "DELETE FROM jiveChatbot WHERE chatbotID=?";

    public void deleteChatbot(long userID) throws UnauthorizedException {

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;

        try {
            con = DbConnectionManager.getTransactionConnection();
            // Delete the actual chatbot info entry
            pstmt = con.prepareStatement(DELETE_CHATBOT_INFO);
            pstmt.setLong(1, userID);
            pstmt.executeUpdate();
            // Delete the actual user ID entry
            pstmt = con.prepareStatement(DELETE_CHATBOT_ID);
            pstmt.setLong(1, userID);
            pstmt.setInt(2, DbUserIDProvider.CHATBOT_TYPE);
            pstmt.setLong(3, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error(e);
            abortTransaction = true;
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
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }
}
