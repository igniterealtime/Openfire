package org.jivesoftware.openfire.plugin.gojara.messagefilter.interceptors;

import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * 
 * This class is only for logging messages between the gateway and the
 * registered clients. It uses the database to save the packets from the past 60
 * (configurable) minutes.
 * 
 * @author Holger Bergunde
 * 
 */
public class StatisticPackageInterceptor implements PacketInterceptor {

	private String _subdomain;
	private DatabaseManager _db;

	public StatisticPackageInterceptor(String subdomain) {
		_subdomain = subdomain;
		_db = DatabaseManager.getInstance();
	}

	@Override
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
			throws PacketRejectedException {
		JID from = packet.getFrom();
		JID to = packet.getTo();

		if (from != null && to != null && processed && incoming) {
			if (from.toString().contains(_subdomain) || to.toString().contains(_subdomain)) {
				/*
				 * Spectrum sends a Ping to itself through the server to check
				 * if the server is alive. We ignore that for statistics
				 */
				if (to.toString().equals(from.toString()) && to.toString().equals(_subdomain))
					return;
				String type = packet.getClass().getName();
				_db.addNewLogEntry(_subdomain, type, from.toString(), to.toString());
			}
		}
	}

}
