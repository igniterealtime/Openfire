package com.reucon.openfire.plugin.archive.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import com.reucon.openfire.plugin.archive.ArchivedMessageConsumer;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage.Direction;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;

/**
 * Manages database persistence.
 */
public class JdbcPersistenceManager implements PersistenceManager {
	public static final int DEFAULT_MAX = 1000;

	public static final String SELECT_MESSAGES_BY_CONVERSATION = "SELECT DISTINCT "
			+ "ofConversation.conversationID, "
			+ "ofConversation.room, "
			+ "ofConversation.isExternal, "
			+ "ofConversation.startDate, "
			+ "ofConversation.lastActivity, "
			+ "ofConversation.messageCount, "
			+ "ofConParticipant.joinedDate, "
			+ "ofConParticipant.leftDate, "
			+ "ofConParticipant.bareJID, "
			+ "ofConParticipant.jidResource, "
			+ "ofConParticipant.nickname, "
			+ "ofMessageArchive.fromJID, "
			+ "ofMessageArchive.toJID, "
			+ "ofMessageArchive.sentDate, "
			+ "ofMessageArchive.body "
			+ "FROM ofConversation "
			+ "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
			+ "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
			+ "WHERE ofConversation.conversationID = ? AND ofConParticipant.bareJID = ? ORDER BY ofMessageArchive.sentDate";

	// public static final String SELECT_MESSAGES_BY_CONVERSATION =
	// "SELECT messageId,time,direction,type,subject,body "
	// + "FROM archiveMessages WHERE conversationId = ? ORDER BY time";

	public static final String SELECT_CONVERSATIONS = "SELECT DISTINCT "
			+ "ofConversation.conversationID, "
			+ "ofConversation.room, "
			+ "ofConversation.isExternal, "
			+ "ofConversation.startDate, "
			+ "ofConversation.lastActivity, "
			+ "ofConversation.messageCount, "
			+ "ofConParticipant.joinedDate, "
			+ "ofConParticipant.leftDate, "
			+ "ofConParticipant.bareJID, "
			+ "ofConParticipant.jidResource, "
			+ "ofConParticipant.nickname, "
			+ "ofMessageArchive.fromJID, "
			+ "ofMessageArchive.toJID, "
			+ "ofMessageArchive.sentDate, "
			+ "ofMessageArchive.body "
			+ "FROM ofConversation "
			+ "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
			+ "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID";

	// public static final String SELECT_CONVERSATIONS =
	// "SELECT c.conversationId,c.startTime,c.endTime,c.ownerJid,c.ownerResource,c.withJid,c.withResource,"
	// + " c.subject,c.thread " + "FROM archiveConversations AS c";

	public static final String COUNT_CONVERSATIONS = "SELECT COUNT(DISTINCT ofConversation.conversationID) FROM ofConversation "
			+ "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
			+ "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID";

	// public static final String COUNT_CONVERSATIONS =
	// "SELECT count(*) FROM archiveConversations AS c";

	public static final String CONVERSATION_ID = "ofConversation.conversationID";
	// public static final String CONVERSATION_ID = "c.conversationId";

	public static final String CONVERSATION_START_TIME = "ofConversation.startDate";
	// public static final String CONVERSATION_START_TIME = "c.startTime";

	public static final String CONVERSATION_END_TIME = "ofConversation.lastActivity";
	// public static final String CONVERSATION_END_TIME = "c.endTime";

	public static final String CONVERSATION_OWNER_JID = "ofConParticipant.bareJID";
	// public static final String CONVERSATION_OWNER_JID = "c.ownerJid";

	public static final String CONVERSATION_WITH_JID = "(ofMessageArchive.toJID = ? OR ofMessageArchive.fromJID = ?)";
	// public static final String CONVERSATION_WITH_JID = "c.withJid";

	public static final String SELECT_ACTIVE_CONVERSATIONS = "SELECT DISTINCT "
			+ "ofConversation.conversationID, "
			+ "ofConversation.room, "
			+ "ofConversation.isExternal, "
			+ "ofConversation.startDate, "
			+ "ofConversation.lastActivity, "
			+ "ofConversation.messageCount, "
			+ "ofConParticipant.joinedDate, "
			+ "ofConParticipant.leftDate, "
			+ "ofConParticipant.bareJID, "
			+ "ofConParticipant.jidResource, "
			+ "ofConParticipant.nickname, "
			+ "ofMessageArchive.fromJID, "
			+ "ofMessageArchive.toJID, "
			+ "ofMessageArchive.sentDate, "
			+ "ofMessageArchive.body "
			+ "FROM ofConversation "
			+ "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
			+ "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
			+ "WHERE ofConversation.lastActivity > ?";

	// public static final String SELECT_ACTIVE_CONVERSATIONS =
	// "SELECT c.conversationId,c.startTime,c.endTime,c.ownerJid,c.ownerResource,withJid,c.withResource,"
	// + " c.subject,c.thread "
	// + "FROM archiveConversations AS c WHERE c.endTime > ?";

	public static final String SELECT_PARTICIPANTS_BY_CONVERSATION = "SELECT DISTINCT "
			+ "ofConversation.conversationID, "
			+ "ofConversation.startDate, "
			+ "ofConversation.lastActivity, "
			+ "ofConParticipant.bareJID "
			+ "FROM ofConversation "
			+ "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
			+ "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
			+ "WHERE ofConversation.conversationID = ? ORDER BY ofConversation.startDate";

	// public static final String SELECT_PARTICIPANTS_BY_CONVERSATION =
	// "SELECT participantId,startTime,endTime,jid FROM archiveParticipants WHERE conversationId =? ORDER BY startTime";

	public boolean createMessage(ArchivedMessage message) {
		/* read only */
		return false;
	}

	public int processAllMessages(ArchivedMessageConsumer callback) {
		return 0;
	}

	public boolean createConversation(Conversation conversation) {
		/* read only */
		return false;
	}

	public boolean updateConversationEnd(Conversation conversation) {
		/* read only */
		return false;
	}

	public boolean createParticipant(Participant participant,
			Long conversationId) {
		return false;
	}

	public List<Conversation> findConversations(String[] participants,
			Date startDate, Date endDate) {
		final List<Conversation> conversations = new ArrayList<Conversation>();
		return conversations;
	}

	public Collection<Conversation> findConversations(Date startDate,
			Date endDate, String ownerJid, String withJid,
			XmppResultSet xmppResultSet) {
		final HashMap<Long, Conversation> conversations;
		final StringBuilder querySB;
		final StringBuilder whereSB;
		final StringBuilder limitSB;

		conversations = new HashMap<Long, Conversation>();

		querySB = new StringBuilder(SELECT_CONVERSATIONS);
		whereSB = new StringBuilder();
		limitSB = new StringBuilder();

		if (startDate != null) {
			appendWhere(whereSB, CONVERSATION_START_TIME, " >= ?");
		}
		if (endDate != null) {
			appendWhere(whereSB, CONVERSATION_END_TIME, " <= ?");
		}
		if (ownerJid != null) {
			appendWhere(whereSB, CONVERSATION_OWNER_JID, " = ?");
		}
		if (withJid != null) {
			appendWhere(whereSB, CONVERSATION_WITH_JID);
		}

		if (xmppResultSet != null) {
			Integer firstIndex = null;
			int max = xmppResultSet.getMax() != null ? xmppResultSet.getMax()
					: DEFAULT_MAX;

			xmppResultSet.setCount(countConversations(startDate, endDate,
					ownerJid, withJid, whereSB.toString()));
			if (xmppResultSet.getIndex() != null) {
				firstIndex = xmppResultSet.getIndex();
			} else if (xmppResultSet.getAfter() != null) {
				firstIndex = countConversationsBefore(startDate, endDate,
						ownerJid, withJid, xmppResultSet.getAfter(),
						whereSB.toString());
				firstIndex += 1;
			} else if (xmppResultSet.getBefore() != null) {
				firstIndex = countConversationsBefore(startDate, endDate,
						ownerJid, withJid, xmppResultSet.getBefore(),
						whereSB.toString());
				firstIndex -= max;
				if (firstIndex < 0) {
					firstIndex = 0;
				}
			}
			firstIndex = firstIndex != null ? firstIndex : 0;

			limitSB.append(" LIMIT ").append(max);
			limitSB.append(" OFFSET ").append(firstIndex);
			xmppResultSet.setFirstIndex(firstIndex);
		}

		if (whereSB.length() != 0) {
			querySB.append(" WHERE ").append(whereSB);
		}
		querySB.append(" ORDER BY ").append(CONVERSATION_ID);
		querySB.append(limitSB);

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(querySB.toString());
			bindConversationParameters(startDate, endDate, ownerJid, withJid,
					pstmt);
			rs = pstmt.executeQuery();
			Log.debug("findConversations: SELECT_CONVERSATIONS: "
					+ pstmt.toString());
			while (rs.next()) {
				Conversation conv = extractConversation(rs);
				conversations.put(conv.getId(), conv);
			}
		} catch (SQLException sqle) {
			Log.error("Error selecting conversations", sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}

		if (xmppResultSet != null && conversations.size() > 0) {
			ArrayList<Long> sortedConvKeys = new ArrayList<Long>(
					conversations.keySet());
			Collections.sort(sortedConvKeys);
			xmppResultSet.setFirst(sortedConvKeys.get(0));
			xmppResultSet
					.setLast(sortedConvKeys.get(sortedConvKeys.size() - 1));
		}
		return conversations.values();
	}

	private void appendWhere(StringBuilder sb, String... fragments) {
		if (sb.length() != 0) {
			sb.append(" AND ");
		}

		for (String fragment : fragments) {
			sb.append(fragment);
		}
	}

	private int countConversations(Date startDate, Date endDate,
			String ownerJid, String withJid, String whereClause) {
		StringBuilder querySB;

		querySB = new StringBuilder(COUNT_CONVERSATIONS);
		if (whereClause != null && whereClause.length() != 0) {
			querySB.append(" WHERE ").append(whereClause);
		}

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(querySB.toString());
			bindConversationParameters(startDate, endDate, ownerJid, withJid,
					pstmt);
			rs = pstmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			} else {
				return 0;
			}
		} catch (SQLException sqle) {
			Log.error("Error counting conversations", sqle);
			return 0;
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
	}

	private int countConversationsBefore(Date startDate, Date endDate,
			String ownerJid, String withJid, Long before, String whereClause) {
		StringBuilder querySB;

		querySB = new StringBuilder(COUNT_CONVERSATIONS);
		querySB.append(" WHERE ");
		if (whereClause != null && whereClause.length() != 0) {
			querySB.append(whereClause);
			querySB.append(" AND ");
		}
		querySB.append(CONVERSATION_ID).append(" < ?");

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			int parameterIndex;
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(querySB.toString());
			parameterIndex = bindConversationParameters(startDate, endDate,
					ownerJid, withJid, pstmt);
			pstmt.setLong(parameterIndex, before);
			rs = pstmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			} else {
				return 0;
			}
		} catch (SQLException sqle) {
			Log.error("Error counting conversations", sqle);
			return 0;
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
	}

	private int bindConversationParameters(Date startDate, Date endDate,
			String ownerJid, String withJid, PreparedStatement pstmt)
			throws SQLException {
		int parameterIndex = 1;

		if (startDate != null) {
			pstmt.setLong(parameterIndex++, dateToMillis(startDate));
		}
		if (endDate != null) {
			pstmt.setLong(parameterIndex++, dateToMillis(endDate));
		}
		if (ownerJid != null) {
			pstmt.setString(parameterIndex++, ownerJid);
		}
		if (withJid != null) {
			pstmt.setString(parameterIndex++, withJid);
			pstmt.setString(parameterIndex++, withJid);
		}
		return parameterIndex;
	}

	public Collection<Conversation> getActiveConversations(
			int conversationTimeout) {
		final Collection<Conversation> conversations;
		final long now = System.currentTimeMillis();

		conversations = new ArrayList<Conversation>();

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(SELECT_ACTIVE_CONVERSATIONS);

			pstmt.setLong(1, now - conversationTimeout * 60L * 1000L);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				conversations.add(extractConversation(rs));
			}
		} catch (SQLException sqle) {
			Log.error("Error selecting conversations", sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}

		return conversations;
	}

	public List<Conversation> getConversations(Collection<Long> conversationIds) {
		final List<Conversation> conversations;
		final StringBuilder querySB;

		conversations = new ArrayList<Conversation>();
		if (conversationIds.isEmpty()) {
			return conversations;
		}

		querySB = new StringBuilder(SELECT_CONVERSATIONS);
		querySB.append(" WHERE ");
		querySB.append(CONVERSATION_ID);
		querySB.append(" IN ( ");
		for (int i = 0; i < conversationIds.size(); i++) {
			if (i == 0) {
				querySB.append("?");
			} else {
				querySB.append(",?");
			}
		}
		querySB.append(" )");
		querySB.append(" ORDER BY ").append(CONVERSATION_END_TIME);

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(querySB.toString());

			int i = 0;
			for (Long id : conversationIds) {
				pstmt.setLong(++i, id);
			}
			rs = pstmt.executeQuery();
			while (rs.next()) {
				conversations.add(extractConversation(rs));
			}
		} catch (SQLException sqle) {
			Log.error("Error selecting conversations", sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}

		return conversations;
	}

	public Conversation getConversation(String ownerJid, String withJid,
			Date start) {
		return getConversation(null, ownerJid, withJid, start);
	}

	public Conversation getConversation(Long conversationId) {
		return getConversation(conversationId, null, null, null);
	}

	private Conversation getConversation(Long conversationId, String ownerJid,
			String withJid, Date start) {
		Conversation conversation = null;
		StringBuilder querySB;

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		querySB = new StringBuilder(SELECT_CONVERSATIONS);
		querySB.append(" WHERE ");
		if (conversationId != null) {
			querySB.append(CONVERSATION_ID).append(" = ? ");
		} else {
			querySB.append(CONVERSATION_OWNER_JID).append(" = ?");
			if (withJid != null) {
				querySB.append(" AND ");
				querySB.append(CONVERSATION_WITH_JID);
			}
			if (start != null) {
				querySB.append(" AND ");
				querySB.append(CONVERSATION_START_TIME).append(" = ? ");
			}
		}

		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(querySB.toString());
			int i = 1;

			if (conversationId != null) {
				pstmt.setLong(1, conversationId);
			} else {
				pstmt.setString(i++, ownerJid);
				if (withJid != null) {
					pstmt.setString(i++, withJid);
					pstmt.setString(i++, withJid);
				}
				if (start != null) {
					pstmt.setLong(i++, dateToMillis(start));
				}
			}
			rs = pstmt.executeQuery();
			Log.debug("getConversation: SELECT_CONVERSATIONS: "
					+ pstmt.toString());
			if (rs.next()) {
				conversation = extractConversation(rs);
			} else {
				return null;
			}

			rs.close();
			pstmt.close();

			pstmt = con.prepareStatement(SELECT_PARTICIPANTS_BY_CONVERSATION);
			pstmt.setLong(1, conversation.getId());

			rs = pstmt.executeQuery();
			Log.debug("getConversation: SELECT_PARTICIPANTS_BY_CONVERSATION: "
					+ pstmt.toString());

			while (rs.next()) {
				for (Participant participant : extractParticipant(rs)) {
					conversation.addParticipant(participant);
				}
			}

			rs.close();
			pstmt.close();

			pstmt = con.prepareStatement(SELECT_MESSAGES_BY_CONVERSATION);
			pstmt.setLong(1, conversation.getId());
			pstmt.setString(2, conversation.getOwnerJid());

			rs = pstmt.executeQuery();
			Log.debug("getConversation: SELECT_MESSAGES_BY_CONVERSATION: "
					+ pstmt.toString());

			while (rs.next()) {
				ArchivedMessage message;

				message = extractMessage(rs);
				message.setConversation(conversation);
				conversation.addMessage(message);
			}
		} catch (SQLException sqle) {
			Log.error("Error selecting conversation", sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}

		return conversation;
	}

	private String getWithJidConversations(ResultSet rs) throws SQLException {
		String bareJid = rs.getString("bareJID");
		String fromJid = rs.getString("fromJID");
		String toJid = rs.getString("toJID");
		String room = rs.getString("room");
		String result = null;
		if (bareJid != null && fromJid != null && toJid != null) {
			if (room != null && !room.equals("")) {
				result = room;
			} else if (fromJid.contains(bareJid)) {
				result = toJid;
			} else {
				result = fromJid;
			}
		}
		return result;
	}

	private Direction getDirection(ResultSet rs) throws SQLException {
		Direction direction = null;
		String bareJid = rs.getString("bareJID");
		String fromJid = rs.getString("fromJID");
		String toJid = rs.getString("toJID");
		if (bareJid != null && fromJid != null && toJid != null) {
			if (bareJid.equals(fromJid)) {
				/*
				 * if message from me to withJid then it is to the withJid
				 * participant
				 */
				direction = Direction.to;
			} else {
				/*
				 * if message to me from withJid then it is from the withJid
				 * participant
				 */
				direction = Direction.from;
			}
		}
		return direction;
	}

	private Conversation extractConversation(ResultSet rs) throws SQLException {
		final Conversation conversation;

		long id = rs.getLong("conversationID");
		Date startDate = millisToDate(rs.getLong("startDate"));
		String ownerJid = rs.getString("bareJID");
		String ownerResource = null;
		String withJid = getWithJidConversations(rs);
		String withResource = null;
		String subject = null;
		String thread = String.valueOf(id);

		conversation = new Conversation(startDate, ownerJid, ownerResource,
				withJid, withResource, subject, thread);
		conversation.setId(id);
		return conversation;
	}

	private Collection<Participant> extractParticipant(ResultSet rs)
			throws SQLException {
		Collection<Participant> participants = new HashSet<Participant>();

		Date startDate = millisToDate(rs.getLong("startDate"));
		String participantJid = rs.getString("bareJID");

		Date endDate = millisToDate(rs.getLong("lastActivity"));

		if (participantJid != null) {
			Participant participant = new Participant(startDate, participantJid);
			participant.setEnd(endDate);
			participants.add(participant);
		}

		// String withJid = getWithJid(rs);
		// if (withJid != null) {
		// Participant participant = new Participant(startDate, participantJid);
		// participant.setEnd(endDate);
		// participants.add(participant);
		// }

		return participants;
	}

	private ArchivedMessage extractMessage(ResultSet rs) throws SQLException {
		final ArchivedMessage message;
		Date time = millisToDate(rs.getLong("sentDate"));
		Direction direction = getDirection(rs);
		String type = null;
		String subject = null;
		String body = rs.getString("body");
		String bareJid = rs.getString("bareJID");
		JID withJid = null;

		if (Direction.from == direction) {
			withJid = new JID(rs.getString("fromJID"));
		}

		message = new ArchivedMessage(time, direction, null, withJid);
		// message.setId(id);
		// message.setSubject(subject);
		message.setBody(body);
		return message;
	}

	private Long dateToMillis(Date date) {
		return date == null ? null : date.getTime();
	}

	private Date millisToDate(Long millis) {
		return millis == null ? null : new Date(millis);
	}
}
