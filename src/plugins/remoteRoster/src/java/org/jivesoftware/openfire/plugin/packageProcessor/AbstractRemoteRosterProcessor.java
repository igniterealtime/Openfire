package org.jivesoftware.openfire.plugin.packageProcessor;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

abstract public class AbstractRemoteRosterProcessor {

	protected static final Logger Log = LoggerFactory.getLogger(AbstractRemoteRosterProcessor.class);
	XMPPServer _server;
	PacketRouter _router;

	public AbstractRemoteRosterProcessor() {
		_server = XMPPServer.getInstance();

	}

	abstract public void process(Packet packet) throws PacketRejectedException;

	protected void dispatchPacket(Packet packet)
	{
		Log.debug("Sending package to PacketRouter: \n"+packet.toString()+"\n");
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
