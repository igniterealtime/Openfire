package org.jivesoftware.openfire.plugin.rest.controller;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The Class MsgArchiveController.
 */
public class MsgArchiveController {

    private static final Logger Log = LoggerFactory.getLogger(MsgArchiveController.class);

	/** The Constant INSTANCE. */
	public static final MsgArchiveController INSTANCE = new MsgArchiveController();

	/** The server. */
	private XMPPServer server;


    private static final String USER_MESSAGE_COUNT = "select COUNT(1) from ofMessageArchive a " +
            "join ofPresence p on (a.sentDate > p.offlineDate) " +
            "WHERE a.toJID = ? AND p.username = ?";

	/**
	 * Gets the single instance of MsgArchiveController.
	 *
	 * @return single instance of MsgArchiveController
	 */
	public static MsgArchiveController getInstance() {
		return INSTANCE;
	}

	/**
	 * Instantiates a new user service controller.
	 */
	private MsgArchiveController() {
		server = XMPPServer.getInstance();
	}

    /**
     * Returns the total number of messages that haven't been delivered to the user.
     *
     * @return the total number of user unread messages.
     */
    public int getUnReadMessagesCount(JID jid) {
        int messageCount = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_MESSAGE_COUNT);
            pstmt.setString(1, jid.toBareJID());
            pstmt.setString(2, jid.getNode());
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
}
