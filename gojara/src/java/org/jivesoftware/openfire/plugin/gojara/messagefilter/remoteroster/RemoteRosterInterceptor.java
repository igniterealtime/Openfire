package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster;

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.processors.AbstractRemoteRosterProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.processors.ClientToComponentUpdateProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.processors.DiscoIQRegisteredProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.processors.IQRosterPayloadProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.*;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * This intercepter handles the main functionality described in the XEP-xxx
 * Remote Roster Management standard. <a
 * href="http://jkaluza.fedorapeople.org/remote-roster.html">XEP-xxx</a>
 * 
 * It must be registered as an PacketInterceptor for each gateway. It will check
 * the incoming packages for several preconditions and redirects the packages
 * using a command pattern to individual packet handles.
 * 
 * @author Holger Bergunde
 * 
 */
public class RemoteRosterInterceptor {

	private static final Logger Log = LoggerFactory
			.getLogger(RemoteRosterInterceptor.class);
	// private String _mySubdomain;
	private Map<String, AbstractRemoteRosterProcessor> _packetProcessor = new HashMap<String, AbstractRemoteRosterProcessor>();
	private DatabaseManager _db;
	private String _host;

	public RemoteRosterInterceptor(String initialSubdomain) {

		_db = DatabaseManager.getInstance();
		Log.debug("Starting Package Interceptor for " + initialSubdomain);
		XMPPServer server = XMPPServer.getInstance();
		RosterManager rosterMananger = server.getRosterManager();
		AbstractRemoteRosterProcessor sendroster = new IQRosterPayloadProcessor(
				rosterMananger);
		AbstractRemoteRosterProcessor receiveChanges = new ReceiveComponentUpdatesProcessor(rosterMananger);
		AbstractRemoteRosterProcessor iqRegistered = new DiscoIQRegisteredProcessor();
		AbstractRemoteRosterProcessor nonPersistant = new NonPersistantRosterProcessor(
				rosterMananger);
		AbstractRemoteRosterProcessor updateToComponent = new ClientToComponentUpdateProcessor();
		_packetProcessor.put("sendRoster", sendroster);
		_packetProcessor.put("receiveChanges", receiveChanges);
		_packetProcessor.put("sparkIQRegistered", iqRegistered);
		_packetProcessor.put("handleNonPersistant", nonPersistant);
		_packetProcessor.put("clientToComponentUpdate", updateToComponent);
	}

	public RemoteRosterInterceptor() {
		_db = DatabaseManager.getInstance();
		Log.debug("Starting RemoteRosterInterceptor - BINGO");
		XMPPServer server = XMPPServer.getInstance();
		RosterManager rosterMananger = server.getRosterManager();
		AbstractRemoteRosterProcessor sendroster = new IQRosterPayloadProcessor(
				rosterMananger);
		AbstractRemoteRosterProcessor receiveChanges = new ReceiveComponentUpdatesProcessor(rosterMananger);
		AbstractRemoteRosterProcessor iqRegistered = new DiscoIQRegisteredProcessor();
		AbstractRemoteRosterProcessor nonPersistant = new NonPersistantRosterProcessor(
				rosterMananger);
		AbstractRemoteRosterProcessor updateToComponent = new ClientToComponentUpdateProcessor();
		AbstractRemoteRosterProcessor discoPackage = new DiscoPackageProcessor();
		
		_packetProcessor.put("sendRoster", sendroster);
		_packetProcessor.put("receiveChanges", receiveChanges);
		_packetProcessor.put("sparkIQRegistered", iqRegistered);
		_packetProcessor.put("handleNonPersistant", nonPersistant);
		_packetProcessor.put("clientToComponentUpdate", updateToComponent);
		_packetProcessor.put("DiscoPackage",discoPackage);
		
		_host = server.getServerInfo().getHostname();
	}

	public void interceptPacket(Packet packet, Session session,
			boolean incoming, boolean processed, String subdomain)
			throws PacketRejectedException {
		if (!processed && incoming) {
			if (packet instanceof IQ) {
				Log.debug("Incoming unprocessed package i might be interested in. I am: "
						+ this.hashCode()
						+ " my subd: "
						+ subdomain
						+ " Package: \n" + packet.toString() + "\n");
				IQ myPacket = (IQ) packet;
				if (myPacket.getTo() == null) {
					/*
					 * If getTo() == null this is maybe a roster update from the
					 * Client to the Server, check if we should mirror this
					 * package to external component
					 */
					if (myPacket.getFrom() != null
							&& myPacket.getType().equals(IQ.Type.set)) {
						if (XpathHelper.findNodesInDocument(
								myPacket.getChildElement().getDocument(),
								"//roster:item").size() > 0) {
							_packetProcessor.get("clientToComponentUpdate")
									.process(myPacket, subdomain);
						}
					}
					return;
				}
				
//				String ns = root.getNamespaceURI();
//				if (ns.equals("http://jabber.org/protocol/disco#items") && iqpacket.getType().equals(IQ.Type.result)){
//					
//				}

				@SuppressWarnings("unused")
				String from = myPacket.getFrom().toString();
				String to = myPacket.getTo().toString();
				if (from.equals(_host)){
					_packetProcessor.get("DiscoPackage").process(packet,subdomain);
					
				} else if (from.equals(subdomain)) {
					if (myPacket.getType().equals(IQ.Type.get)
							&& XpathHelper.findNodesInDocument(
									myPacket.getElement().getDocument(),
									"//roster:*").size() == 1) {
						// This Package is a roster request by remote component
						_packetProcessor.get("sendRoster").process(packet,
								subdomain);
					} else if (myPacket.getType().equals(IQ.Type.set)
							&& XpathHelper.findNodesInDocument(
									myPacket.getElement().getDocument(),
									"//roster:item").size() >= 1) {
						// Component sends roster update
						_packetProcessor.get("receiveChanges").process(packet,
								subdomain);
					}
				} else if (to.equals(subdomain)
						&& myPacket.getType().equals(IQ.Type.get)
						&& myPacket.toString().contains(
								"http://jabber.org/protocol/disco#info")) {
					/*
					 * modify the disco#info for spark clients if enabled in
					 * admin panel
					 */
					_packetProcessor.get("sparkIQRegistered").process(packet,
							subdomain);
				}
			} else if (!JiveGlobals.getBooleanProperty(
					"plugin.remoteroster.persistent", false)) {
				if (packet instanceof Presence
						&& packet.getFrom().toString().equals(subdomain)
						&& !packet.getElement().getStringValue()
								.equals("Connecting")) {
					System.out
							.println("Test for NonPersistant-Roster Cleanup!");
					_packetProcessor.get("handleNonPersistant").process(packet,
							subdomain);
				}
			}
		}
	}
}

// currently we dont need this, and it didnt seem to occur often anyway. Will
// test it later.
// could potentially save some traffic
// else if (packet instanceof Presence){
// String to = packet.getTo().toString();
// if (!to.equals(_mySubdomain) && to.contains(_mySubdomain)){
//
// Presence myPacket = ((Presence) packet);
// if (myPacket.getType().equals(Presence.Type.unavailable) ||
// myPacket.getType().equals(Presence.Type.probe) ||
// myPacket.getType().equals(null)){
// System.out.println("this presence would be wasted");
// throw new PacketRejectedException();
// }
// } 