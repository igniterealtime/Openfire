package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * 
 * Some clients try to check how long a contact is already offline. This feature
 * is not supported by spectrum so it won't response to this IQ stanza. To
 * prevent the client from waiting for a response we could answer with a
 * service-unavailable message as described in XEP-12.
 * 
 * @author Holger Bergunde
 * @author axel.frederik.brand
 * 
 */
public class IQLastProcessor extends AbstractRemoteRosterProcessor{

	public IQLastProcessor() {
		Log.debug("Created IQLastProcessor");
	}
	
	/**
	 * At this point we know:
	 * IQLastFilter is ACTIVATED
	 * Packet is incoming and NOT processed
	 * Packet is instance of IQ
	 * To contains the watched subdomain represented in String subdomain
	 */
	@Override
	public void process(Packet packet, String subdomain, String to, String from)
			throws PacketRejectedException {
		
		IQ iqpacket = (IQ) packet;
		
		if (iqpacket.getType().equals(IQ.Type.get)) {
			Log.debug("Processing IQLast Packet for " + subdomain);
			IQ answer = IQ.createResultIQ(iqpacket);
			answer.setType(IQ.Type.error);
			
			DefaultElement errorElement = new DefaultElement("error");
			errorElement.addAttribute("type", "cancel");
			errorElement.addAttribute("code", "503");
			
			DefaultElement serviceElement = new DefaultElement("service-unavailable");
			serviceElement.addNamespace("", "urn:ietf:params:xml:ns:xmpp-stanzas");
			errorElement.add(serviceElement);
			answer.setChildElement(errorElement);
			
			Log.debug("Auto response to jabber:iq:last for " + subdomain);
			PacketRouter router = _server.getPacketRouter();
			router.route(answer);
			
		}
	}

}
