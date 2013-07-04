package org.jivesoftware.openfire.plugin.gojara.messagefilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.processors.*;
import org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager;
import org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * This is the only Interceptor GoJara uses. Each IQ/Message/Presence is checked if it is of interest to us, so we can
 * process it accordingly. This is done in the specific Processors. The Interceptor keeps a set of currently connected
 * Transports.
 * 
 * @author axel.frederik.brand
 * @author Holger Bergunde
 * 
 */
public class MainInterceptor implements PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(MainInterceptor.class);
	private Set<String> activeTransports = new ConcurrentHashSet<String>();
	private Map<String, AbstractRemoteRosterProcessor> packetProcessors = new HashMap<String, AbstractRemoteRosterProcessor>();
	private TransportSessionManager tSessionManager = TransportSessionManager.getInstance();
	private GojaraAdminManager gojaraAdminmanager = GojaraAdminManager.getInstance();
	private Boolean frozen;

	public MainInterceptor() {
		Log.info("Created MainInterceptor for GoJara Plugin.");
		XMPPServer server = XMPPServer.getInstance();
		RosterManager rosterMananger = server.getRosterManager();

		AbstractRemoteRosterProcessor iqRegisteredProcessor = new DiscoIQRegisteredProcessor();
		AbstractRemoteRosterProcessor iqRosterPayloadProcessor = new IQRosterPayloadProcessor(rosterMananger);
		AbstractRemoteRosterProcessor nonPersistantProcessor = new NonPersistantRosterProcessor(rosterMananger);
		AbstractRemoteRosterProcessor statisticsProcessor = new StatisticsProcessor();
		AbstractRemoteRosterProcessor updateToComponentProcessor = new ClientToComponentUpdateProcessor(activeTransports);
		AbstractRemoteRosterProcessor whitelistProcessor = new WhitelistProcessor(activeTransports);
		AbstractRemoteRosterProcessor mucfilterProcessor = new MucFilterProcessor();
		AbstractRemoteRosterProcessor gojaraAdminProcessor = new GojaraAdminProcessor();
		packetProcessors.put("sparkIQRegistered", iqRegisteredProcessor);
		packetProcessors.put("iqRosterPayload", iqRosterPayloadProcessor);
		packetProcessors.put("handleNonPersistant", nonPersistantProcessor);
		packetProcessors.put("statisticsProcessor", statisticsProcessor);
		packetProcessors.put("clientToComponentUpdate", updateToComponentProcessor);
		packetProcessors.put("whitelistProcessor", whitelistProcessor);
		packetProcessors.put("mucfilterProcessor", mucfilterProcessor);
		packetProcessors.put("gojaraAdminProcessor", gojaraAdminProcessor);
		frozen = false;

	}

	public boolean addTransport(String subDomain) {
		Log.info("Adding " + subDomain + " to watched Transports.");
		boolean retval = this.activeTransports.add(subDomain);
		if (retval) {
			tSessionManager.addTransport(subDomain);
			 gojaraAdminmanager.testAdminConfiguration(subDomain);
		}

		return retval;
	}

	public boolean removeTransport(String subDomain) {
		Log.info("Removing " + subDomain + " from watched Transports.");
		tSessionManager.removeTransport(subDomain);
		 gojaraAdminmanager.gatewayUnregistered(subDomain);
		return this.activeTransports.remove(subDomain);
	}

	public void freeze() {
		Log.info("Freezing GoJara Maininterceptor.");
		frozen = true;
	}

	/**
	 * As our Set of Subdomains is a Hash of Strings like icq.domain.tld, if we want to check if a jid CONTAINS a
	 * watched subdomain we need to iterate over the set. We also return the subdomain as a string so we can use it if
	 * we find it.
	 */
	private String searchJIDforSubdomain(String jid) {
		if (jid.length() > 0) {
			for (String subdomain : activeTransports) {
				if (jid.contains(subdomain))
					return subdomain;
			}
		}
		return "";
	}

	/**
	 * This Interceptor tests if GoJara needs to process this package. We decided to do one global Interceptor so we
	 * would'nt redundantly test for cases we already checked in previous Interceptors, also we have only one big ugly
	 * If Structure to maintain instead of several.
	 * 
	 * @see org.jivesoftware.openfire.interceptor.PacketInterceptor#interceptPacket (org.xmpp.packet.Packet,
	 *      org.jivesoftware.openfire.session.Session, boolean, boolean)
	 */
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
		if (frozen)
			return;

		String from = "";
		String to = "";
		if (!processed || (incoming && processed)) {

			try {
				if (packet.getFrom() != null)
					from = packet.getFrom().toString();
				if (packet.getTo() != null)
					to = packet.getTo().toString();
			} catch (IllegalArgumentException e) {
				Log.debug("There was an illegal JID while intercepting Message for GoJara. Not Intercepting it! " + e.getMessage());
				return;
			}
		}

		if (incoming && !processed) {

			if (packet instanceof IQ) {
				IQ iqPacket = (IQ) packet;
				Element query = iqPacket.getChildElement();
				if (query == null)
					return;
				// Jabber:IQ:roster Indicates Client to Component update or Rosterpush
				else if (query.getNamespaceURI().equals("jabber:iq:roster")) {
					if (to.length() == 0 && iqPacket.getType().equals(IQ.Type.set))
						packetProcessors.get("clientToComponentUpdate").process(packet, "", to, from);
					else if (from.length() > 0 && activeTransports.contains(from))
						packetProcessors.get("iqRosterPayload").process(packet, from, to, from);
				}
				// SPARK IQ REGISTERED Feature
				else if (query.getNamespaceURI().equals("http://jabber.org/protocol/disco#info") && to.length() > 0
						&& activeTransports.contains(to) && iqPacket.getType().equals(IQ.Type.get)) {
					packetProcessors.get("sparkIQRegistered").process(packet, to, to, from);
				}
				// JABBER:IQ:LAST - Autoresponse Feature
				else if (JiveGlobals.getBooleanProperty("plugin.remoteroster.iqLastFilter", false)
						&& query.getNamespaceURI().equals("jabber:iq:last")) {

					String to_s = searchJIDforSubdomain(to);
					if (to_s.length() > 0 && iqPacket.getType().equals(IQ.Type.get))
						throw new PacketRejectedException();
				}

			}
			// Gojara Admin Manager Feature - Intercept responses to ADHOC commands sent via AdminManager
			else if (packet instanceof Message && activeTransports.contains(from) && to.contains("gojaraadmin")) {
				packetProcessors.get("gojaraAdminProcessor").process(packet, from, to, from);
			}
			// NONPERSISTANT Feature
			else {
				if (!JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false)) {
					if (packet instanceof Presence && activeTransports.contains(from))
						packetProcessors.get("handleNonPersistant").process(packet, from, to, from);
				}
			}

		} else if (incoming && processed) {
			// We ignore Pings from S2 to S2 itself.
			// STATISTICS - Feature
			String from_searched = searchJIDforSubdomain(from);
			String to_searched = searchJIDforSubdomain(to);
			String subdomain = from_searched.length() == 0 ? to_searched : from_searched;
			if (!from.equals(to) && subdomain.length() > 0)
				packetProcessors.get("statisticsProcessor").process(packet, subdomain, to, from);
			// TransportSession Feature
			if (packet instanceof Presence && activeTransports.contains(from)) {
				Presence presence_packet = (Presence) packet;
				if (presence_packet.getType() == null) {
					tSessionManager.connectUserTo(from, packet.getTo().getNode().toString());
				} else if (presence_packet.getType() != null && presence_packet.getType().equals(Presence.Type.unavailable)) {
					tSessionManager.disconnectUserFrom(from, packet.getTo().getNode().toString());
				}
			}
			// TransportSession Feature - track Registrations so we can reset unsuccesfull ones
			else if (packet instanceof IQ && activeTransports.contains(to)) {
				IQ iqPacket = (IQ) packet;
				Element query = iqPacket.getChildElement();
				if (query == null)
					return;
				if (query.getNamespaceURI().equals("jabber:iq:register")) {
					if (query.nodeCount() > 1)
						tSessionManager.registerUserTo(to, iqPacket.getFrom().getNode().toString());
					else
						tSessionManager.removeRegistrationOfUser(to, iqPacket.getFrom().getNode().toString());
				}

			}

		} else if (!incoming && !processed) {

			if (packet instanceof IQ) {
				IQ iqPacket = (IQ) packet;
				Element query = iqPacket.getChildElement();
				if (query == null)
					return;

				// DISCO#ITEMS - Whitelisting Feature
				if (query.getNamespaceURI().equals("http://jabber.org/protocol/disco#items"))
					packetProcessors.get("whitelistProcessor").process(packet, "", to, from);
				// DISCO#INFO - MUC-Filter-Feature
				else if (JiveGlobals.getBooleanProperty("plugin.remoteroster.mucFilter", false)
						&& query.getNamespaceURI().equals("http://jabber.org/protocol/disco#info") && from.length() > 0
						&& activeTransports.contains(from))
					packetProcessors.get("mucfilterProcessor").process(packet, from, to, from);
			} else if (packet instanceof Presence) {
				// We block Presences to users of a subdomain so OF/S2 wont log you in automatically if you have a
				// subdomain user in your roster
				String to_s = searchJIDforSubdomain(to);
				if (to_s.length() > 0 && !activeTransports.contains(to))
					throw new PacketRejectedException();
			}
		}
	}
}
