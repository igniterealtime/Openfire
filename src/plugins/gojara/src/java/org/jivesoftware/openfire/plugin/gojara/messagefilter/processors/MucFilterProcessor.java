package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.List;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * @author axel.frederik.brand
 */
public class MucFilterProcessor extends AbstractRemoteRosterProcessor {

	public MucFilterProcessor() {
		Log.info("Created MucFilterProcessor");
	}

	/**
	 * At this Point we know: MucBlock = true, !incoming, !processed Package is
	 * IQ with Namespace disco#info, from equals the watched subdomain given
	 * through subdomain
	 */
	@Override
	public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
		IQ iqPacket = (IQ) packet;

		if (iqPacket.getType().equals(IQ.Type.result) && !to.isEmpty()) {
			Element root = iqPacket.getChildElement();

			List<Node> nodes = XpathHelper.findNodesInDocument(root.getDocument(), "//disco:feature");
			for (Node node : nodes) {
				String var = node.valueOf("@var");
				if (var.equals("http://jabber.org/protocol/muc"))
					root.remove(node);
			}

		}
	}
}
