package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.List;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.permissions.PermissionManager;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
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
 * @author axel.frederik.brand
 */
public class WhitelistProcessor extends AbstractRemoteRosterProcessor{
	private PermissionManager _permissions;

	public WhitelistProcessor() {
		_permissions = new PermissionManager();
		Log.debug("Created WhitelistProcessor");
	}

	/**
	 * At this point we already know:
	 * Package is NOT incoming
	 * Package is processed
	 * From is either Empty (it was null) or equals the serverDomain.
	 * Package is a IQ
	 * 
	 * @param subdomain A String containing several watched subdomains separated by [#]
	 */
	@Override
	public void process (Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
		IQ myPacket = (IQ) packet;
		Element root = myPacket.getChildElement();

		if (root == null)
			return;


		String ns = root.getNamespaceURI();
		if (ns.equals("http://jabber.org/protocol/disco#items") && myPacket.getType().equals(IQ.Type.result)) {
			Log.debug("Processing packet in Whitelistprocessor for " + to);
			//As some users can be allowed to use only specific Gateways, we have to do this for every subdomain separately
			String[] valid_subdomains = subdomain.split("[#]");
			for (String single_subdomain : valid_subdomains) {
				
				if (_permissions.isGatewayLimited(single_subdomain) && !_permissions.allowedForUser(single_subdomain, myPacket.getTo())) {
					List<Node> nodes = XpathHelper.findNodesInDocument(root.getDocument(), "//discoitems:item");
					for (Node node : nodes) {
						if (node.valueOf("@jid").equals(single_subdomain)) {
							root.remove(node);
						}
					}
				}
			}
		}
	}
	
}

