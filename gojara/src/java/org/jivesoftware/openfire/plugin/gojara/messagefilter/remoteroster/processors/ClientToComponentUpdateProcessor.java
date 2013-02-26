package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
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
 * @author Holger Bergunde
 * 
 */
public class ClientToComponentUpdateProcessor extends AbstractRemoteRosterProcessor {

//	private String _myDomain;

	public ClientToComponentUpdateProcessor() {
		Log.debug("Created ClientToComponentUpdateProcessor");
//		_myDomain = mySubdomain;
	}

	@Override
	public void process(Packet packet, String subdomain) throws PacketRejectedException {
		Log.debug("Processing packet in ClientToComponentUpdateProcessor for " + subdomain);
		Element query = ((IQ) packet).getChildElement();
		if (query != null && query.getNamespaceURI().equals("jabber:iq:roster")) {
			if (findNodesInDocument(query.getDocument(), "//roster:item").size() > 0) {
				for (Node n : findNodesInDocument(query.getDocument(), "//roster:item")) {
					String jid = n.valueOf("@jid");
					// TODO: We ignore remove iq packets for now. There might be
					// conflicts
					// when we remove our legacy network registration.
					if (jid.contains("@" + subdomain) && !n.valueOf("@subscription").equals("remove")) {
						Log.debug("Mirroring packet from local network to legacy component " + subdomain);
						IQ forward = (IQ) packet.createCopy();
						forward.setTo(subdomain);
						dispatchPacket(forward);
					}
				}
			}
		}
	}
}
