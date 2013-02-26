package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors;

import java.util.List;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
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
public class DiscoPackageProcessor extends AbstractRemoteRosterProcessor{
	private PermissionManager _permissions;

	public DiscoPackageProcessor() {
		_permissions = new PermissionManager();
	}

	@Override
	public void process (Packet packet, String subdomain) throws PacketRejectedException {
		if (_permissions.isGatewayLimited(subdomain)) {
			IQ iqpacket = (IQ) packet;
			Element root = iqpacket.getChildElement();
			if (root == null)
				return; 

			String ns = root.getNamespaceURI();
			if (ns.equals("http://jabber.org/protocol/disco#items") && iqpacket.getType().equals(IQ.Type.result)) {
				if (!_permissions.allowedForUser(subdomain, iqpacket.getTo())) {
					List<Node> nodes = XpathHelper.findNodesInDocument(root.getDocument(), "//discoitems:item");
					for (Node node : nodes) {
						if (node.valueOf("@jid").equals(subdomain)) {
							root.remove(node);
						}
					}
				}
			}
		}
	}

}
