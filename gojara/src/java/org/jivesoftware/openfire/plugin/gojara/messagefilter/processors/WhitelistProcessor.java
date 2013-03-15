package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.List;
import java.util.Set;

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
public class WhitelistProcessor extends AbstractRemoteRosterProcessor {
	private PermissionManager _permissions;
	private Set<String> watchedSubdomains;

	public WhitelistProcessor(Set<String> activeTransports) {
		_permissions = new PermissionManager();
		watchedSubdomains = activeTransports;
		Log.info("Created WhitelistProcessor");
	}

	/**
	 * If this is valid disco#items package for this Use-Case we iterate through
	 * the nodes and check if we have to remove nodes, this way they are not
	 * shown to the user receiving this disco#items.
	 * 
	 * @param subdomain
	 *            not the actual Subdomain here, as we have to use our set.
	 */
	@Override
	public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
		IQ myPacket = (IQ) packet;
		if (myPacket.getType().equals(IQ.Type.result) && (from.isEmpty() || from.equals(_server.getServerInfo().getXMPPDomain()))) {

			Log.debug("Processing packet in Whitelistprocessor for " + to + "Packet: " + packet.toString());
			Element root = myPacket.getChildElement();

			List<Node> nodes = XpathHelper.findNodesInDocument(root.getDocument(), "//discoitems:item");
			for (Node node : nodes) {
				String node_domain = node.valueOf("@jid");
				if (watchedSubdomains.contains(node_domain)) {
					if (_permissions.isGatewayLimited(node_domain) && !_permissions.allowedForUser(node_domain, myPacket.getTo()))
						root.remove(node);
				}
			}
		}
	}
}
