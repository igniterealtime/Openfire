package org.jivesoftware.openfire.plugin.gojara.base;

import java.util.Set;

import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

public class MainInterceptor implements PacketInterceptor {

	private static final Logger Log = LoggerFactory
			.getLogger(RemoteRosterInterceptor.class);
	private Set<String> activeTransports = new ConcurrentHashSet<String>();
	private DatabaseManager _db;
	private RemoteRosterInterceptor interceptor;

	public MainInterceptor() {
		Log.debug("Started MainInterceptor");
		_db = DatabaseManager.getInstance();
		interceptor = new RemoteRosterInterceptor();
	}

	public void addTransport(String subDomain) {
		this.activeTransports.add(subDomain);
		Log.debug("Added interceptor for subdomain " + subDomain
				+ " to MainInterceptor");
	}

	public boolean removeTransport(String subDomain) {
		if (this.activeTransports.contains(subDomain)) {
			this.activeTransports.remove(subDomain);
			return true;
		}
		return false;
	}

	@Override
	public void interceptPacket(Packet packet, Session session,
			boolean incoming, boolean processed) throws PacketRejectedException {

		String from = packet.getFrom().toString();
		String to = packet.getTo().toString();

		//TODO: Not intercepting Messages & Presences
		if (activeTransports.contains(to) || activeTransports.contains(from)) {
			String subdomain = activeTransports.contains(to) ? to : from;
			interceptor.interceptPacket(packet, session, incoming, processed, subdomain);
			logForStatistic(packet, incoming, processed, to);
		}

		// // PacketInterceptor interceptor = this.activeTransports.get(to);
		// if (interceptor != null) {
		// return;
		// }
		//
		// // interceptor = this.activeTransports.get(from);
		// if (interceptor != null) {
		// interceptor.interceptPacket(packet, session, incoming, processed);
		// logForStatistic(packet, incoming, processed, from);
		// return;
		// }
	}

	private void logForStatistic(Packet packet, boolean incoming,
			boolean processed, String subdomain) {
		String from = packet.getFrom().toString();
		String to = packet.getTo().toString();

		if (from != null && to != null && processed && incoming) {
			/*
			 * Spectrum sends a Ping to itself through the server to check if
			 * the server is alive. We ignore that for statistics
			 */
			if (to.toString().equals(from.toString())
					&& to.toString().equals(subdomain))
				return;
			String type = packet.getClass().getName();
			_db.addNewLogEntry(subdomain, type, from.toString(), to.toString());
		}

	}

}
