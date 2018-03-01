package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.ArchivedMessageConsumer;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.SAXReader;
import org.eclipse.jdt.internal.compiler.apt.util.Archive;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.StringReader;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by dwd on 25/07/16.
 */
public class MucMamPersistenceManager implements PersistenceManager {
    private static final String LOAD_HISTORY =
            "SELECT sender, nickname, logTime, subject, body, stanza, messageId FROM ofMucConversationLog " +
                    "WHERE messageId IS NOT NULL AND logTime>? AND logTime <= ? AND roomID=? AND (nickname IS NOT NULL OR subject IS NOT NULL) ";
    private static final String WHERE_SENDER = " AND sender = ? ";
    private static final String WHERE_AFTER = " AND messageId > ? ";
    private static final String WHERE_BEFORE = " AND messageId < ? ";
    private static final String ORDER_BY = " ORDER BY logTime";
    @Override
    public boolean createMessage(ArchivedMessage message) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public int processAllMessages(ArchivedMessageConsumer callback) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public boolean createConversation(Conversation conversation) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public boolean updateConversationEnd(Conversation conversation) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public boolean createParticipant(Participant participant, Long conversationId) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public List<Conversation> findConversations(String[] participants, Date startDate, Date endDate) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Collection<Conversation> findConversations(Date startDate, Date endDate, String owner, String with, XmppResultSet xmppResultSet) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, String owner, String with, XmppResultSet xmppResultSet) {
        JID mucRoom = new JID(owner);
        MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
        MultiUserChatService service =  manager.getMultiUserChatService(mucRoom);
        MUCRoom room = service.getChatRoom(mucRoom.getNode());
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        // If logging isn't enabled, do nothing.
        if (!room.isLogEnabled()) return null;
        List<ArchivedMessage>msgs = new LinkedList<>();
        if (startDate == null) {
            startDate = new Date(0L);
        }
        if (endDate == null) {
            endDate = new Date();
        }
        int max = xmppResultSet.getMax();
        with = null; // TODO: Suppress this, since we don't yet have requestor information for access control.
        try {
            connection = DbConnectionManager.getConnection();
            StringBuilder sql = new StringBuilder(LOAD_HISTORY);
            if (with != null) {
                sql.append(WHERE_SENDER);
            }
            if (xmppResultSet.getAfter() != null) {
                sql.append(WHERE_AFTER);
            }
            if (xmppResultSet.getBefore() != null) {
                sql.append(WHERE_BEFORE);
            }
            sql.append(ORDER_BY);
            pstmt = connection.prepareStatement(sql.toString());
            pstmt.setString(1, StringUtils.dateToMillis(startDate));
            pstmt.setString(2, StringUtils.dateToMillis(endDate));
            pstmt.setLong(3, room.getID());
            int pos = 3;
            if (with != null) {
                pstmt.setString(++pos, with);
            }
            if (xmppResultSet.getAfter() != null) {
                pstmt.setLong(++pos, xmppResultSet.getAfter());
            }
            if (xmppResultSet.getBefore() != null) {
                pstmt.setLong(++pos, xmppResultSet.getBefore());
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                String stanza = rs.getString(6);
                long id = rs.getLong(7);
                if (stanza == null) {
                    Message message = new Message();
                    message.setType(Message.Type.groupchat);
                    message.setSubject(subject);
                    message.setBody(body);
                    // Set the sender of the message
                    if (nickname != null && nickname.trim().length() > 0) {
                        JID roomJID = room.getRole().getRoleAddress();
                        // Recreate the sender address based on the nickname and room's JID
                        message.setFrom(new JID(roomJID.getNode(), roomJID.getDomain(), nickname, true));
                    }
                    else {
                        // Set the room as the sender of the message
                        message.setFrom(room.getRole().getRoleAddress());
                    }
                    stanza = message.toString();
                }
                ArchivedMessage archivedMessage = new ArchivedMessage(sentDate, ArchivedMessage.Direction.from, null, null);
                archivedMessage.setStanza(stanza);
                archivedMessage.setId(id);
                msgs.add(archivedMessage);
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM-MUC: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        // TODO - Not great, really should be done by suitable LIMIT stuff.
        // Would need to reverse ordering in some cases and then reverse results.
        boolean complete = true;
        xmppResultSet.setCount(msgs.size());
        while (msgs.size() > max) {
            msgs.remove(msgs.size() - 1);
            complete = false;
        }
        xmppResultSet.setComplete(complete);
        if (msgs.size() > 0) {
            xmppResultSet.setFirst(msgs.get(0).getId());
            if (msgs.size() > 1) {
                xmppResultSet.setLast(msgs.get(msgs.size() - 1).getId());
            }
        }
        return msgs;
    }

    @Override
    public Collection<Conversation> getActiveConversations(int conversationTimeout) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public List<Conversation> getConversations(Collection<Long> conversationIds) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Conversation getConversation(String ownerJid, String withJid, Date start) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Conversation getConversation(Long conversationId) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }
}
