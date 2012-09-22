package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster;

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.AbstractRemoteRosterProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.CleanUpRosterProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.ClientToComponentUpdateProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.DiscoIQResigteredProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.ReceiveComponentUpdatesProcessor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.SendRosterProcessor;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

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
public class RemoteRosterInterceptor implements PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(RemoteRosterInterceptor.class);
	private String _mySubdomain;
	private Map<String, AbstractRemoteRosterProcessor> _packetProcessor = new HashMap<String, AbstractRemoteRosterProcessor>();

	public RemoteRosterInterceptor(String initialSubdomain) {

		Log.debug("Starting Package Interceptor for " + initialSubdomain);
		_mySubdomain = initialSubdomain;
		XMPPServer server = XMPPServer.getInstance();
		RosterManager rosterMananger = server.getRosterManager();
		AbstractRemoteRosterProcessor sendroster = new SendRosterProcessor(rosterMananger, _mySubdomain);
		AbstractRemoteRosterProcessor receiveChanges = new ReceiveComponentUpdatesProcessor(rosterMananger,
				_mySubdomain);
		AbstractRemoteRosterProcessor iqRegistered = new DiscoIQResigteredProcessor(_mySubdomain);
		AbstractRemoteRosterProcessor cleanUp = new CleanUpRosterProcessor(rosterMananger, _mySubdomain);
		AbstractRemoteRosterProcessor updateToComponent = new ClientToComponentUpdateProcessor(_mySubdomain);
		_packetProcessor.put("sendRoster", sendroster);
		_packetProcessor.put("receiveChanges", receiveChanges);
		_packetProcessor.put("sparkIQRegistered", iqRegistered);
		_packetProcessor.put("handleCleanUp", cleanUp);
		_packetProcessor.put("clientToComponentUpdate", updateToComponent);
	}

	@Override
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
			throws PacketRejectedException {
		if (!processed && incoming) {
			if (packet instanceof IQ) {
				Log.debug("Incomping unprocessed package i might be interested in. Package: \n" + packet.toString()
						+ "\n");
				IQ myPacket = (IQ) packet;
				if (myPacket.getFrom() == null || myPacket.getTo() == null) {
					/*
					 * If getTo() == null this is maybe a roster update from the
					 * Client to the Server, check if we should mirror this
					 * package to external component
					 */
					if (myPacket.getFrom() != null && myPacket.getType().equals(IQ.Type.set)
							&& myPacket.getTo() == null) {
						if (XpathHelper.findNodesInDocument(myPacket.getChildElement().getDocument(), "//roster:item")
								.size() > 0) {
							_packetProcessor.get("clientToComponentUpdate").process(myPacket);
						}
					}
					return;
				}
				@SuppressWarnings("unused")
				String to = myPacket.getTo().toString();
				String from = myPacket.getFrom().toString();

				if (myPacket.getType().equals(IQ.Type.get) && from.equals(_mySubdomain)) {
					if (XpathHelper.findNodesInDocument(myPacket.getElement().getDocument(), "//roster:*").size() == 1) {
						// This Package is a roster request by remote component
						_packetProcessor.get("sendRoster").process(packet);
					}
				} else if (myPacket.getType().equals(IQ.Type.set) && from.equals(_mySubdomain)) {
					if (XpathHelper.findNodesInDocument(myPacket.getElement().getDocument(), "//roster:item").size() >= 1) {
						// Component sends roster update
						_packetProcessor.get("receiveChanges").process(packet);
					}
				} else if (myPacket.getType().equals(IQ.Type.get)
						&& myPacket.toString().contains("http://jabber.org/protocol/disco#info")
						&& myPacket.getTo().toString().equals(_mySubdomain)) {
					/*
					 * modify the disco#info for spark clients if enabled in
					 * admin panel
					 */
					_packetProcessor.get("sparkIQRegistered").process(packet);
				} else if (myPacket.getType().equals(IQ.Type.set) && myPacket.getTo().toString().equals(_mySubdomain)) {
					System.out.println("war das ein remove an mich????");
					_packetProcessor.get("handleCleanUp").process(packet);
				}
			}
			// else if (packet instanceof Presence) {
			// if (packet.getFrom().toString().equals(_mySubdomain)
			// &&
			// !JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent",
			// false)) {
			// System.out.println("MACH EIN CLEANUP!!!!!!");
			// _packetProcessor.get("handleCleanUp").process(packet);
			// }
			// }
		}
	}

}
