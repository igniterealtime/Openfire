package org.jivesoftware.openfire.plugin.gojara.messagefilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
	private String serverDomain;
	private Boolean frozen;

	public MainInterceptor() {
		Log.debug("Started MainInterceptor for GoJara Plugin.");
		XMPPServer server = XMPPServer.getInstance();
		serverDomain = server.getServerInfo().getXMPPDomain();

		RosterManager rosterMananger = server.getRosterManager();
		AbstractRemoteRosterProcessor updateToComponent = new ClientToComponentUpdateProcessor();
		AbstractRemoteRosterProcessor iqRegistered = new DiscoIQRegisteredProcessor();
		AbstractRemoteRosterProcessor iqRosterPayload = new IQRosterPayloadProcessor(rosterMananger);
		AbstractRemoteRosterProcessor nonPersistant = new NonPersistantRosterProcessor(rosterMananger);
		AbstractRemoteRosterProcessor statisticsProcessor = new StatisticsProcessor();
		AbstractRemoteRosterProcessor iqLastProcessor = new IQLastProcessor();
		AbstractRemoteRosterProcessor whitelistProcessor = new WhitelistProcessor();
		packetProcessors.put("clientToComponentUpdate", updateToComponent);
		packetProcessors.put("sparkIQRegistered", iqRegistered);
		packetProcessors.put("iqRosterPayload", iqRosterPayload);
		packetProcessors.put("handleNonPersistant", nonPersistant);
		packetProcessors.put("statisticsProcessor", statisticsProcessor);
		packetProcessors.put("iqLastProcessor", iqLastProcessor);
		packetProcessors.put("whitelistProcessor", whitelistProcessor);

		frozen = false;
	}

	//These get called from our RemoteRosterPlugin
	public boolean addTransport(String subDomain) {
		Log.debug("Trying to add " +subDomain + "to Set of watched Transports.");
		return this.activeTransports.add(subDomain);
	}

	public boolean removeTransport(String subDomain) {
		Log.debug("Trying to remove " + subDomain + " from Set of watched Transports.");
		return this.activeTransports.remove(subDomain);
		//		if (this.activeTransports.contains(subDomain)) {
		//			this.activeTransports.remove(subDomain);
		//			return true;
		//		}
		//		return false;
	}

	//idk if this "smells" but as we still dont know why OF is not correctly shutting down our Interceptor
	//better safe then sorry
	public void freeze(){
		frozen = true;		
	}
	/*
	As our Set of Subdomains is a Hash of Strings like icq.domain.tld, if we 
	want to check if a jid CONTAINS a watched subdomain we need to iterate over the set.
	We also return the subdomain as a string so we can use it if we find it.
	 */
	private String searchJIDforSubdomain(String jid) {
		for (String subdomain : activeTransports) {
			if (subdomain.contains(jid))
				return subdomain;
		}
		return "";
	}
	
	private  String generateSubdomainString() {
		String subdomainstring = "";
		for (String subdomain : activeTransports) {
			subdomainstring += subdomain + "#";
		}
		return subdomainstring;
	}
	/*
	 * This Interceptor tests if GoJara needs to process this package. We decided to do one global Interceptor
	 * so we would'nt redundantly test for cases we already checked in previous Interceptors, also we have only one big ugly If Structure
	 * to maintain instead of several.
	 * @see org.jivesoftware.openfire.interceptor.PacketInterceptor#interceptPacket(org.xmpp.packet.Packet, org.jivesoftware.openfire.session.Session, boolean, boolean)
	 */
	public void interceptPacket(Packet packet, Session session,	boolean incoming, boolean processed)
			throws PacketRejectedException {
		if (frozen)
			return;
		
		//We have to watch out for null else this will throw Exceptions
		String from,to;
		try {
			from = (packet.getFrom() != null) ? packet.getFrom().toString() : "";
			to = (packet.getTo() != null) ? packet.getTo().toString() : "";
		} catch (IllegalArgumentException e) {
			Log.warn("There was an illegal JID while intercepting Message for GoJara. Not Intercepting it! "+e.getMessage());
			return;
		}
		
		//We dont want this to get too packed, so here we test only for stuff we can test on PACKET + Instanceof
		if (incoming && processed) {
			Log.debug("Incoming processed Package i might be interested in. I'm "+ this.hashCode() + "\n Package: \n " + packet.toString() + "\n");
			//if to is Empty, this might be a Client to Component Update we have to forward.
			if (to.isEmpty()) 
				packetProcessors.get("clientToComponentUpdate").process(packet, generateSubdomainString(), to, from);
			//if to is equal to a subdomain of a INCOMING package we need to check if its for registering
			else if (activeTransports.contains(to) && packet instanceof IQ)
				packetProcessors.get("sparkIQRegistered").process(packet,to, to, from);
			//If from EQUALS the subdomain, this is likely a RosterPush or Presence we have to react to.
			else if (!from.isEmpty() && activeTransports.contains(from)) {
				
				if (packet instanceof IQ)
					packetProcessors.get("iqRosterPayload").process(packet,from, to, from);
				//it could also be a presence from Transport, so we test for Non-Persistancy
				else if (packet instanceof Presence && !JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false))
					packetProcessors.get("handleNonPersistant").process(packet,from, to, from);
			}
			//Functionality Processors for this Case are Done, now Logging. We need to be sure its not
			//The Ping Spectrum send itself, and one of from or to contains a watched subdomain.
			String from_s = searchJIDforSubdomain(from);
			String to_s = searchJIDforSubdomain(to);
			String subdomain = from_s.isEmpty() ? to_s : from_s;
			if (!from.equals(to) &&  !subdomain.isEmpty() && !(from.isEmpty() && to.isEmpty()) && packet instanceof IQ)
				packetProcessors.get("statisticsProcessor").process(packet,subdomain, to, from);

		}
		else if (incoming && !processed && JiveGlobals.getBooleanProperty("plugin.remoteroster.iqLastFilter", false)) {
			Log.debug("Incoming unprocessed Package i might be interested in. I'm "+ this.hashCode() + "\n Package: \n " + packet.toString() + "\n");
			// if to is EQUAL to the subdomain or a string containing a subdomain
			String subdomain = searchJIDforSubdomain(to);
			if (!subdomain.isEmpty() && packet instanceof IQ)
				packetProcessors.get("iqLastProcessor").process(packet,subdomain, to, from);
		}
		else if(!incoming && processed) {
			Log.debug("Outgoing processed Package i might be interested in. I'm "+ this.hashCode() + " Package: \n " + packet.toString() + "\n");
			if ((from.isEmpty() || from.equals(serverDomain)) && packet instanceof IQ) 
				//This might be a Disco IQ from the SERVER itself, so we have to check for Acess-Restriction
				packetProcessors.get("whiteListProcessor").process(packet, generateSubdomainString(), to, from);
			//If we want the StatisticsProcessor to diff between Outgoing and Incoming, it would go here
		}
	}

}



