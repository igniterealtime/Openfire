package com.reucon.openfire.plugin.archive.model;

import org.jivesoftware.database.JiveID;
import org.xmpp.packet.JID;

import java.util.Date;

/**
 * An archived message.
 */
@JiveID(601)
public class ArchivedMessage {
	public enum Direction {
		/**
		 * A message sent by the owner.
		 */
		to,

		/**
		 * A message received by the owner.
		 */
		from
	}

	private Long id;
	private final Date time;
	private final Direction direction;
	private final String type;
	private String subject;
	private String body;
	private Conversation conversation;
	private JID withJid;

	public ArchivedMessage(Date time, Direction direction, String type, JID withJid) {
		this.time = time;
		this.direction = direction;
		this.type = type;
		this.withJid = withJid;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getTime() {
		return time;
	}

	public Direction getDirection() {
		return direction;
	}

	public String getType() {
		return type;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public Conversation getConversation() {
		return conversation;
	}

	public void setConversation(Conversation conversation) {
		this.conversation = conversation;
	}

	/**
	 * Checks if this message contains payload that should be archived.
	 * 
	 * @return <code>true</code> if this message is empty, <code>false</code>
	 *         otherwise.
	 */
	public boolean isEmpty() {
		return subject == null && body == null;
	}

	public JID getWithJid() {
		return withJid;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("ArchivedMessage[id=").append(id).append(",");
		sb.append("time=").append(time).append(",");
		sb.append("direction=").append(direction).append("]");

		return sb.toString();
	}
}
