package com.reucon.openfire.plugin.archive;

import java.util.Date;

import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;

/**
 * Factory to create model objects.
 */
public class ArchiveFactory {
	private ArchiveFactory() {

	}

	public static ArchivedMessage createArchivedMessage(Session session,
			Message message, ArchivedMessage.Direction direction, JID withJid) {
		final ArchivedMessage archivedMessage;

		archivedMessage = new ArchivedMessage(new Date(), direction, message
				.getType().toString(), withJid);
		archivedMessage.setSubject(message.getSubject());
		archivedMessage.setBody(message.getBody());

		return archivedMessage;
	}
}
