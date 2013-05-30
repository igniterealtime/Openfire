package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager;
import org.xmpp.packet.Packet;

/**
 * 
 * This class is only for logging messages between the gateway and the
 * registered clients. It uses the database to save the packets from the past 60
 * (configurable) minutes.
 * 
 * @author Holger Bergunde
 * @author axel.frederik.brand
 */
public class TransportSessionProcessor extends AbstractRemoteRosterProcessor{

	private DatabaseManager _db;
	
	public TransportSessionProcessor() {
		Log.info("Created StatisticsProcessor");
		_db = DatabaseManager.getInstance();
	}

	/**
	 * At this Point we Already know:
	 * neither of both JIDS is malformed (Package wouldn't have been intercepted)
	 * Package is incoming & processed
	 * 
	 * Either From or To contains the watched,passed subdomain
	 * From does not Equal To (This way we exclude PING sent by spectrum To spectrum
	 * From AND To are NOT empty (null), this way we exclude packets sent to server itself...change Maininterceptor if we want to change this
	 * 
	 */
	@Override
	public void process(Packet packet, String subdomain, String to, String from)
			throws PacketRejectedException {
		
		String type = packet.getClass().getName();
		_db.addNewLogEntry(subdomain, type, from, to);
	}

}
