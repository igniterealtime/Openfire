package org.jivesoftware.openfire.plugin.packageProcessor;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class ClientToComponentUpdateProcessor extends AbstractRemoteRosterProcessor {

	private String _myDomain;

	public ClientToComponentUpdateProcessor(String mySubdomain) {
		_myDomain = mySubdomain;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException
	{
		Element query = ((IQ) packet).getChildElement();
		if (query != null && query.getNamespaceURI().equals("jabber:iq:roster")) {
			if (findNodesInDocument(query.getDocument(), "//roster:item").size() > 0) {
				for (Node n : findNodesInDocument(query.getDocument(), "//roster:item")) {
					String jid = n.valueOf("@jid");
					// TODO: We ignore remove iq packets for now. There might be
					// conflicts
					// when we remove our legacy network registration.
					if (jid.contains("@" + _myDomain) && !n.valueOf("@subscription").equals("remove")) {
						IQ forward = (IQ) packet.createCopy();
						forward.setTo(_myDomain);
						dispatchPacket(forward);
					}
				}
			}
		}
	}
}
