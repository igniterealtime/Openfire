package org.jivesoftware.openfire.plugin.gojara.messagefilter.interceptors;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 
 */
public class IQLastInterceptor implements PacketInterceptor {

	private String _subDomain;
	protected static final Logger Log = LoggerFactory.getLogger(IQLastInterceptor.class);

	public IQLastInterceptor(String subdomain) {
		Log.debug("Createt IQLastInterceptor for " + subdomain);
		_subDomain = subdomain;
	}

	@Override
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
			throws PacketRejectedException {

		if (!iqLastInterceptorEnabled()) {
			Log.debug("Auto repsonse to jabber:iq:last is deactivated. You could enabled it using the webinterface ");
			return;
		}

		if (packet instanceof IQ && incoming && processed) {

			IQ iqpacket = (IQ) packet;
			if (!iqpacket.getTo().toString().contains(_subDomain))
				return;

			Element root = iqpacket.getChildElement();
			if (root == null)
				return;

			String ns = root.getNamespaceURI();
			if (ns.equals("jabber:iq:last") && iqpacket.getType().equals(IQ.Type.get)) {

				IQ answer = new IQ();
				answer.setType(IQ.Type.error);
				answer.setFrom(iqpacket.getTo());
				answer.setTo(iqpacket.getFrom());
				answer.setID(iqpacket.getID());

				DefaultElement errorElement = new DefaultElement("error");

				errorElement.addAttribute("type", "cancel");
				errorElement.addAttribute("code", "503");

				DefaultElement serviceElement = new DefaultElement("service-unavailable");
				serviceElement.addNamespace("", "urn:ietf:params:xml:ns:xmpp-stanzas");
				errorElement.add(serviceElement);
				answer.setChildElement(errorElement);
				Log.debug("Auto response to jabber:iq:last for " + _subDomain);
				PacketRouter router = XMPPServer.getInstance().getPacketRouter();
				router.route(answer);

			}
		}

	}

	private boolean iqLastInterceptorEnabled() {
		return JiveGlobals.getBooleanProperty("plugin.remoteroster.iqLastFilter", false);
	}

}
