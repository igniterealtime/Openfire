package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

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
 * 			// Our Query is now a <item jid="Example@subdomain" name="contact"><group></item>iq id="FSwIU-68" type="set" from="axel.brand@lxhamrztst02.ger.win.int.kn/Spark 2.7.0 #272">
//			  <query xmlns="jabber:iq:roster">
//			    <item jid="129443529@icq.lxhamrztst02.ger.win.int.kn" name="wulschti" subscription="both">
//			      <group>General</group>
//			    </item>
//			  </query>
//			</iq>
 */
public class ClientToComponentUpdateProcessor extends AbstractRemoteRosterProcessor {

	public ClientToComponentUpdateProcessor() {
		Log.debug("Created ClientToComponentUpdateProcessor");
	}

	private String searchJIDforSubdomain(String[] valid_subdomains,String jid){
		for (String s : valid_subdomains){
			if (jid.contains(s))
				return s;
		}
		return "";
	}
	@Override
	public void process(Packet packet, String subdomain) throws PacketRejectedException {
		Log.debug("Processing packet in ClientToComponentUpdateProcessor");

		Element query = ((IQ) packet).getChildElement();
		if (query != null && query.getNamespaceURI().equals("jabber:iq:roster")) {
			if (findNodesInDocument(query.getDocument(), "//roster:item").size() > 0) {
				// We now know we have to check the JID of the to be added User against our valid subdomains.
				String[] valid_subdomains = subdomain.split("#");
				for (Node n : findNodesInDocument(query.getDocument(), "//roster:item")) {
					String jid = n.valueOf("@jid");
					// TODO: We ignore remove iq packets for now. There might be
					// conflicts
					// when we remove our legacy network registration.
					String found_subdomain = searchJIDforSubdomain(valid_subdomains, jid);
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
}
