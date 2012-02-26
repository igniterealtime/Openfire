package org.jivesoftware.openfire.plugin.gojara.messagefilter.interceptors;

import java.util.List;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.permissions.PermissionManager;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * 
 * If the access to external components or gateways is limited to a special
 * group in GoJara settings we have to filter the disco#infos from the server to
 * the client. If the user is not on the list we are hiding the specified info
 * and remove the item containing the gateways subdomain
 * 
 * @author Holger Bergunde
 * 
 */
public class DiscoPackageInterceptorHandler implements PacketInterceptor {

	private PermissionManager _permissions;
	private String _subDomain;
	private String _host;

	public DiscoPackageInterceptorHandler(String subdomain) {
		_permissions = new PermissionManager();
		_subDomain = subdomain;
		XMPPServer server = XMPPServer.getInstance();
		_host = server.getServerInfo().getHostname();
	}

	@Override
	public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
			throws PacketRejectedException {
		if (_permissions.isGatewayLimited(_subDomain)) {
			if (packet instanceof IQ) {
				IQ iqpacket = (IQ) packet;
				Element root = iqpacket.getChildElement();
				if (root == null)
					return; 
				
				String ns = root.getNamespaceURI();
				if (ns.equals("http://jabber.org/protocol/disco#items") && iqpacket.getType().equals(IQ.Type.result)) {
					if (!_permissions.allowedForUser(_subDomain, iqpacket.getTo())) {
						if (iqpacket.getFrom().toString().equals(_host)) {
							List<Node> nodes = XpathHelper.findNodesInDocument(root.getDocument(), "//discoitems:item");
							for (Node node : nodes) {
								if (node.valueOf("@jid").equals(_subDomain)) {
									root.remove(node);
								}
							}
						}
					}
				}
			}
		}
	}

}
