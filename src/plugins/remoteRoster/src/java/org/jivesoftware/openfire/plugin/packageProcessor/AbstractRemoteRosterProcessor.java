package org.jivesoftware.openfire.plugin.packageProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
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

	/**
	 * Search the specified document for Nodes corresponding to the xpath Keep
	 * in mind that you have to use xmpp namespace for searching e.g.
	 * '//roster:features'
	 * 
	 * @param doc
	 *            document
	 * @param xpath
	 *            with roster namespace for searching in query nodes
	 * @return list of nodes
	 */
	protected List<Node> findNodesInDocument(Document doc, String xpath)
	{
		Map<String, String> namespaceUris = new HashMap<String, String>();
		namespaceUris.put("roster", "jabber:iq:roster");
		XPath xPath = DocumentHelper.createXPath(xpath);
		xPath.setNamespaceURIs(namespaceUris);
		return xPath.selectNodes(doc);
	}

	protected String getUsernameFromJid(String jid)
	{
		int firstAtPos = jid.indexOf("@");
		return firstAtPos != -1 ? jid.substring(0, firstAtPos) : jid;
	}

	protected String getServerNameFromComponentName(String componentName)
	{
		int intServer = componentName.lastIndexOf(".");
		return componentName.substring(0, intServer);

	}

}
