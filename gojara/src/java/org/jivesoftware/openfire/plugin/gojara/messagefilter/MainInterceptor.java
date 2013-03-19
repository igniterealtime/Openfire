package org.jivesoftware.openfire.plugin.gojara.messagefilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.processors.*;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

public class MainInterceptor implements PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(MainInterceptor.class);
	private Set<String> activeTransports = new ConcurrentHashSet<String>();
	private Map<String, AbstractRemoteRosterProcessor> packetProcessors = new HashMap<String, AbstractRemoteRosterProcessor>();
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
		packetProcessors.put("sparkIQRegistered", iqRegisteredProcessor);
		packetProcessors.put("iqRosterPayload", iqRosterPayloadProcessor);
		packetProcessors.put("handleNonPersistant", nonPersistantProcessor);
		packetProcessors.put("statisticsProcessor", statisticsProcessor);
		packetProcessors.put("clientToComponentUpdate", updateToComponentProcessor);
		packetProcessors.put("whitelistProcessor", whitelistProcessor);
		packetProcessors.put("mucfilterProcessor", mucfilterProcessor);

		frozen = false;
	}

	public boolean addTransport(String subDomain) {
		Log.info("Trying to add " + subDomain + " to Set of watched Transports.");
		return this.activeTransports.add(subDomain);
	}

	public boolean removeTransport(String subDomain) {
		Log.info("Trying to remove " + subDomain + " from Set of watched Transports.");
		return this.activeTransports.remove(subDomain);
		// if (this.activeTransports.contains(subDomain)) {
		// this.activeTransports.remove(subDomain);
		// return true;
		// }
		// return false;
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
		if (!jid.isEmpty()) {
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
					if (to.isEmpty() && iqPacket.getType().equals(IQ.Type.set))
						packetProcessors.get("clientToComponentUpdate").process(packet, "", to, from);
					else if (!from.isEmpty() && activeTransports.contains(from))
						packetProcessors.get("iqRosterPayload").process(packet, from, to, from);
				}
				// SPARK IQ REGISTERED Feature
				else if (query.getNamespaceURI().equals("http://jabber.org/protocol/disco#info") && !to.isEmpty()
						&& activeTransports.contains(to) && iqPacket.getType().equals(IQ.Type.get)) {
					packetProcessors.get("sparkIQRegistered").process(packet, to, to, from);
				}
				// JABBER:IQ:LAST - Autoresponse Feature
				else if (JiveGlobals.getBooleanProperty("plugin.remoteroster.iqLastFilter", false)
						&& query.getNamespaceURI().equals("jabber:iq:last")) {
					
					String to_s = searchJIDforSubdomain(to);
					if (!to_s.isEmpty() && iqPacket.getType().equals(IQ.Type.get))
						throw new PacketRejectedException();
				}
				// NONPERSISTANT Feature
			} else if (!JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false)) {
				if (packet instanceof Presence && activeTransports.contains(from))
					packetProcessors.get("handleNonPersistant").process(packet, from, to, from);
			}

		} else if (incoming && processed) {
			// We ignore Pings from S2 to S2 itself.
			// STATISTICS - Feature
			String from_s = searchJIDforSubdomain(from);
			String to_s = searchJIDforSubdomain(to);
			String subdomain = from_s.isEmpty() ? to_s : from_s;
			if (!from.equals(to) && !subdomain.isEmpty())
				packetProcessors.get("statisticsProcessor").process(packet, subdomain, to, from);

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
						&& query.getNamespaceURI().equals("http://jabber.org/protocol/disco#info") && !from.isEmpty()
						&& activeTransports.contains(from))
					packetProcessors.get("mucfilterProcessor").process(packet, from, to, from);
			} else if (packet instanceof Presence) {
				// We block Presences to users of a subdomain so OF/S2 wont log you in automatically if you have a
				// subdomain user in your roster
				String to_s = searchJIDforSubdomain(to);
				if (!to_s.isEmpty() && !activeTransports.contains(to))
					throw new PacketRejectedException();
			}
		}
	}
}
