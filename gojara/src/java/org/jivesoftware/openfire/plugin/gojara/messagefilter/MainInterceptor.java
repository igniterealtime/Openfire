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
import org.xmpp.packet.Packet;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Presence;

public class MainInterceptor implements PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(MainInterceptor.class);
	private Set<String> activeTransports = new ConcurrentHashSet<String>();
	private Map<String, AbstractRemoteRosterProcessor> packetProcessors = new HashMap<String, AbstractRemoteRosterProcessor>();
	private Boolean frozen;

	public MainInterceptor() {
		Log.debug("Started MainInterceptor for GoJara Plugin.");
		XMPPServer server = XMPPServer.getInstance();
		RosterManager rosterMananger = server.getRosterManager();

		AbstractRemoteRosterProcessor iqRegistered = new DiscoIQRegisteredProcessor();
		AbstractRemoteRosterProcessor iqRosterPayload = new IQRosterPayloadProcessor(rosterMananger);
		AbstractRemoteRosterProcessor nonPersistant = new NonPersistantRosterProcessor(rosterMananger);
		AbstractRemoteRosterProcessor statisticsProcessor = new StatisticsProcessor();
		AbstractRemoteRosterProcessor iqLastProcessor = new IQLastProcessor();
		AbstractRemoteRosterProcessor updateToComponent = new ClientToComponentUpdateProcessor(activeTransports);
		AbstractRemoteRosterProcessor whitelistProcessor = new WhitelistProcessor(activeTransports);
		packetProcessors.put("sparkIQRegistered", iqRegistered);
		packetProcessors.put("iqRosterPayload", iqRosterPayload);
		packetProcessors.put("handleNonPersistant", nonPersistant);
		packetProcessors.put("statisticsProcessor", statisticsProcessor);
		packetProcessors.put("iqLastProcessor", iqLastProcessor);
		packetProcessors.put("clientToComponentUpdate", updateToComponent);
		packetProcessors.put("whitelistProcessor", whitelistProcessor);

		frozen = false;
	}

	// These get called from our RemoteRosterPlugin
	public boolean addTransport(String subDomain) {
		Log.debug("Trying to add " + subDomain + "to Set of watched Transports.");
		return this.activeTransports.add(subDomain);
	}

	public boolean removeTransport(String subDomain) {
		Log.debug("Trying to remove " + subDomain + " from Set of watched Transports.");
		return this.activeTransports.remove(subDomain);
		// if (this.activeTransports.contains(subDomain)) {
		// this.activeTransports.remove(subDomain);
		// return true;
		// }
		// return false;
	}

	// idk if this "smells" but as we still dont know why OF is not correctly
	// shutting down our Interceptor better safe then sorry
	public void freeze() {
		frozen = true;
	}

	/**
	 * As our Set of Subdomains is a Hash of Strings like icq.domain.tld, if we
	 * want to check if a jid CONTAINS a watched subdomain we need to iterate
	 * over the set. We also return the subdomain as a string so we can use it
	 * if we find it.
	 */
	private String searchJIDforSubdomain(String jid) {
		for (String subdomain : activeTransports) {
			if (subdomain.contains(jid))
				return subdomain;
		}
		return "";
	}

	/**
	 * This Interceptor tests if GoJara needs to process this package. We
	 * decided to do one global Interceptor so we would'nt redundantly test for
	 * cases we already checked in previous Interceptors, also we have only one
	 * big ugly If Structure to maintain instead of several.
	 * 
	 * @see org.jivesoftware.openfire.interceptor.PacketInterceptor#interceptPacket
	 *      (org.xmpp.packet.Packet, org.jivesoftware.openfire.session.Session,
	 *      boolean, boolean)
	 */
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
		if (frozen)
			return;

		String from = "";
		String to = "";
		if (!processed || (incoming && processed)){

			try {
				if (packet.getFrom() != null)
					from = packet.getFrom().toString();
				if (packet.getTo() != null)
					to = packet.getTo().toString();
			} catch (IllegalArgumentException e) {
				Log.warn("There was an illegal JID while intercepting Message for GoJara. Not Intercepting it! " + e.getMessage());
				return;
			}
		}


		if (incoming && !processed) {
			// We ignore Pings from S2 to S2 itself. We test for Log first so
			// that we can return in case
			// The packet doesnt have any watched namespace, but we still may want to log it.
			String from_s = searchJIDforSubdomain(from);
			String to_s = searchJIDforSubdomain(to);
			String subdomain = from_s.isEmpty() ? to_s : from_s;
			if (!from.equals(to) && !subdomain.isEmpty() && !(from.isEmpty() && to.isEmpty()))
				packetProcessors.get("statisticsProcessor").process(packet, subdomain, to, from);

			if (packet instanceof IQ) {
				IQ myPacket = (IQ) packet;
				Element query = myPacket.getChildElement();
				if (query == null)
					return;
				// Jabber:IQ:roster Indicates Client to Component update or Rosterpush
				else if (query.getNamespaceURI().equals("jabber:iq:roster")) {
					Log.debug("Incoming RosterPackage i might be interested in. I'm " + this.hashCode() + "\n Package: \n "
							+ packet.toString() + "\n");
					if (to.isEmpty())
						packetProcessors.get("clientToComponentUpdate").process(packet, "", to, from);
					else if (!from.isEmpty() && activeTransports.contains(from))
						packetProcessors.get("iqRosterPayload").process(packet, from, to, from);
				}
				// Disco#Info - check for register processing
				else if (query.getNamespaceURI().equals("http://jabber.org/protocol/disco#info") && !to.isEmpty()
						&& activeTransports.contains(to)) {
					Log.debug("Incoming Disco#info might be interested in. I'm " + this.hashCode() + "\n Package: \n " + packet.toString()
							+ "\n");
					packetProcessors.get("sparkIQRegistered").process(packet, to, to, from);
				}

				// Check for Rostercleanup in Nonpersistant-mode
			} else if (packet instanceof Presence && !JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false)) {
				if (activeTransports.contains(from))
					packetProcessors.get("handleNonPersistant").process(packet, from, to, from);
			}

		} else if (incoming && processed && JiveGlobals.getBooleanProperty("plugin.remoteroster.iqLastFilter", false)) {
			// JABBER:IQ:LAST
			if (packet instanceof IQ) {
				IQ myPacket = (IQ) packet;
				Element query = myPacket.getChildElement();
				if (query != null && query.getNamespaceURI().equals("jabber:iq:last")) {
					String subdomain = searchJIDforSubdomain(to);
					if (!subdomain.isEmpty()) {
						Log.debug("Incoming unprocessed Package i might be interested in. I'm " + this.hashCode() + "\n Package: \n "
								+ packet.toString() + "\n");
						packetProcessors.get("iqLastProcessor").process(packet, subdomain, to, from);
					}
				}
			}
		} else if (!incoming && !processed) {

			// DISCO#ITEMS - Whitelisting Feature
			if (packet instanceof IQ) {
				IQ myPacket = (IQ) packet;
				Element query = myPacket.getChildElement();
				if (query != null && query.getNamespaceURI().equals("http://jabber.org/protocol/disco#items")) {
					Log.debug("Outgoing processed Package i might be interested in. I'm " + this.hashCode() + " Package: \n "
							+ packet.toString() + "\n");
					packetProcessors.get("whiteListProcessor").process(packet, "", to, from);
				}
			}

			// If we want the StatisticsProcessor to diff between Outgoing and
			// Incoming, it would go here
		}
	}
}
