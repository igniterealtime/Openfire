package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

/**
 * This packet and sub packet implements the command pattern. Every processor
 * that extends this class have to implement the process function. The
 * {@link MainInterceptor} will register different implementations of
 * this processor and redirect packages according to their functionality
 * 
 * @author Holger Bergunde
 * @author axel.frederik.brand
 * 
 */
abstract public class AbstractRemoteRosterProcessor {

	protected static final Logger Log = LoggerFactory.getLogger(AbstractRemoteRosterProcessor.class);
	XMPPServer _server;
	PacketRouter _router;

	public AbstractRemoteRosterProcessor() {
		_server = XMPPServer.getInstance();

	}

	/**
	 * Handles the passed packet. Might throw {@link PacketRejectedException} if
	 * the package should not be processed by openfire.
	 * See actual classes for info about their implementation.
	 * @param packet Packet itself
	 * @param subdomain String with subdomain contained in either from or to, may be ""
	 * @param to String with recipient of packet, may be ""
	 * @param from String with sender of packet, may be ""
	 * @throws PacketRejectedException
	 */
	abstract public void process(Packet packet,String subdomain, String to, String from) throws PacketRejectedException;

	/**
	 * Use this method if you want to send your own packets through openfire
	 * @param packet packet to send
	 */
	protected void dispatchPacket(Packet packet) {
		Log.debug("Sending package to PacketRouter: \n" + packet.toString() + "\n");
		PacketRouter router = _server.getPacketRouter();
		router.route(packet);
	}

	/**
	 * Redirects this method. Have a closer look to {@link XpathHelper}
	 * @param doc
	 * @param xpath
	 * @return
	 */
	protected List<Node> findNodesInDocument(Document doc, String xpath) {
		return XpathHelper.findNodesInDocument(doc, xpath);
	}
	
	/**
	 * Redirects this method. Have a closer look to {@link XpathHelper}
	 * @param doc
	 * @param xpath
	 * @return
	 */
	protected String getUsernameFromJid(String jid) {
		return XpathHelper.getUsernameFromJid(jid);
	}

}
