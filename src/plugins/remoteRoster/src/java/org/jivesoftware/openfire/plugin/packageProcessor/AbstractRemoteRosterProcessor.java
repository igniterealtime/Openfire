package org.jivesoftware.openfire.plugin.packageProcessor;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.Utils;
import org.xmpp.packet.Packet;

abstract public class AbstractRemoteRosterProcessor {

	XMPPServer _server;
	PacketRouter _router;

	public AbstractRemoteRosterProcessor() {
		_server = XMPPServer.getInstance();

	}

	abstract public void process(Packet packet) throws PacketRejectedException;

	protected void dispatchPacket(Packet packet)
	{
		PacketRouter router = _server.getPacketRouter();
		router.route(packet);
	}

	protected List<Node> findNodesInDocument(Document doc, String xpath)
	{
		return Utils.findNodesInDocument(doc, xpath);
	}

	protected String getUsernameFromJid(String jid)
	{
		return Utils.getUsernameFromJid(jid);
	}

}
