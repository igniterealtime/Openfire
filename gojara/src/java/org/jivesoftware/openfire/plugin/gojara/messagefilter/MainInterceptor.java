package org.jivesoftware.openfire.plugin.gojara.messagefilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
//import org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager;
//import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors.*;
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

	//	private DatabaseManager _db;
	private static final Logger Log = LoggerFactory.getLogger(MainInterceptor.class);
	private Set<String> activeTransports = new ConcurrentHashSet<String>();
	private Map<String, AbstractRemoteRosterProcessor> packetProcessors = new HashMap<String, AbstractRemoteRosterProcessor>();
	private String serverDomain;


	//we dont need this anymore
	//	private RemoteRosterInterceptor interceptor;

	public MainInterceptor() {
		Log.debug("Started MainInterceptor for GoJara Plugin.");
		XMPPServer server = XMPPServer.getInstance();
		serverDomain = server.getServerInfo().getXMPPDomain();
		//		_db = DatabaseManager.getInstance();
		//		interceptor = new RemoteRosterInterceptor();

		//Now we need to add the PacketProcessors.
		RosterManager rosterMananger = server.getRosterManager();
		AbstractRemoteRosterProcessor sendroster = new SendRosterProcessor(rosterMananger);
		AbstractRemoteRosterProcessor receiveChanges = new ReceiveComponentUpdatesProcessor(rosterMananger);
		AbstractRemoteRosterProcessor iqRegistered = new DiscoIQRegisteredProcessor();
		AbstractRemoteRosterProcessor nonPersistant = new NonPersistantRosterProcessor(rosterMananger);
		AbstractRemoteRosterProcessor updateToComponent = new ClientToComponentUpdateProcessor();
		packetProcessors.put("sendRoster", sendroster);
		packetProcessors.put("receiveChanges", receiveChanges);
		packetProcessors.put("sparkIQRegistered", iqRegistered);
		packetProcessors.put("handleNonPersistant", nonPersistant);
		packetProcessors.put("clientToComponentUpdate", updateToComponent);
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

	//evtl noch auf bool refactoren wenni ch merke das ich das für nichts brauche(siehe to)
	private String searchJIDforSubdomain(String jid) {
		//As our Set of Subdomains is a Hash of Strings like icq.domain.tld, if we 
		//want to check if a jid CONTAINS a watched subdomain we need to iterate over the set.
		// We also return the subdomain as a string so we can use it if we find it.
		for (String subdomain : activeTransports) {
			if (subdomain.contains(jid))
				return subdomain;
		}
		return "";
	}
	/*
	 * This Interceptor tests if GoJara needs to process this package. We decided to do one global Interceptor
	 * so we would'nt redundantly test for cases we already checked in previous Interceptors.
	 * @see org.jivesoftware.openfire.interceptor.PacketInterceptor#interceptPacket(org.xmpp.packet.Packet, org.jivesoftware.openfire.session.Session, boolean, boolean)
	 */
	public void interceptPacket(Packet packet, Session session,	boolean incoming, boolean processed)
			throws PacketRejectedException {
		//We have to watch out for null else this will throw Exceptions
		String from = (packet.getFrom() != null) ? packet.getFrom().toString() : "";
		String to = (packet.getTo() != null) ? packet.getTo().toString() : "";

		//We dont want this to get too packed, so here we test only for stuff we can test on PACKET

		//if to is Empty, this might be a Client to Component Update we have to forward.
		if (to.isEmpty()) {
			packetProcessors.get("clientToComponentUpdate").process(packet);
		}
		//This might be a Disco IQ from the SERVER itself, so we have to check for Acess-Restriction
		else if (from.isEmpty() || from.equals(serverDomain)) {
			packetProcessors.get("WhiteListProcessor").process(packet);
		}
		
		//At this Point, TO is NOT EMPTY
		String to_subdomain = searchJIDforSubdomain(to);
		if (!to_subdomain.isEmpty()){
			// To can now be EQUAL to the subdomain or a string containing a subdomain
			packetProcessors.get("IQLastProcessor").process(packet,to_subdomain);

			//If TO EQUALS a watched subdomain, we test for IQRegistered Process
			if (activeTransports.contains(to))
				packetProcessors.get("sparkIQRegistered").process(packet,to);
		}
		
		//If FROM EQUALS a watched subdomain, we test for IQ:ROSTER Payload
		else if (!from.isEmpty() && activeTransports.contains(from)) {
			if (packet instanceof IQ)
				packetProcessors.get("iqRosterPayload").process(packet,from);

			//it could also be a presence from Transport, so we test for Non-Persistancy
			if (!JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false) && (packet instanceof Presence)) {
				packetProcessors.get("handleNonPersistant").process(packet,from);
			}

		}



	}



}
