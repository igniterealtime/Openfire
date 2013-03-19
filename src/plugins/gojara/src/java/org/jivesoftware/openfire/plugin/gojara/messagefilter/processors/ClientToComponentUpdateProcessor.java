package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.List;
import java.util.Set;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * This class implements the XEP-xxx Remote Roster Management standard
 * "2.4 Client sends user update". Part of command pattern used in
 * {@link RemoteRosterInterceptor}
 * 
 * Further information: <a
 * href="http://jkaluza.fedorapeople.org/remote-roster.html#sect-id215516"
 * >Here</a>
 * 
 * @author Holger Bergunde <iq id="FSwIU-68" type="set"
 *         from="user@example/resource"> // <query xmlns="jabber:iq:roster"> //
 *         <item jid="123456789@subdomain" name="wulschti" subscription="both">
 *         // <group>General</group> // </item> // </query> // </iq>
 */
public class ClientToComponentUpdateProcessor extends AbstractRemoteRosterProcessor {
	private Set<String> watchedSubdomains;

	public ClientToComponentUpdateProcessor(Set<String> activeTransports) {
		watchedSubdomains = activeTransports;
		Log.info("Created ClientToComponentUpdateProcessor");
	}

	private String searchJIDforSubdomain(String jid) {
		for (String subdomain : watchedSubdomains) {
			if (jid.contains(subdomain))
				return subdomain;
		}
		return "";
	}

	@Override
	public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
		Log.debug("Processing packet in ClientToComponentUpdateProcessor: " + packet.toString());

		Element query = ((IQ) packet).getChildElement();
		List<Node> nodes = findNodesInDocument(query.getDocument(), "//roster:item");
		if (nodes.size() > 0) {
			// We now know we have to check the JID of the to be added User
			// against our valid subdomains.
			for (Node n : nodes) {
				String jid = n.valueOf("@jid");
				// TODO: We ignore remove iq packets for now. There might be
				// conflicts
				// when we remove our legacy network registration.
				String found_subdomain = searchJIDforSubdomain(jid);
				if (!found_subdomain.isEmpty() && !n.valueOf("@subscription").equals("remove")) {

					Log.debug("Mirroring packet from local network to legacy component " + found_subdomain);
					IQ forward = (IQ) packet.createCopy();
					forward.setTo(found_subdomain);
					dispatchPacket(forward);
				}
			}
		}
	}
}
