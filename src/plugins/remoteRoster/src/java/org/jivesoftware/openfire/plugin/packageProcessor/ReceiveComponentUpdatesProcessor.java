package org.jivesoftware.openfire.plugin.packageProcessor;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Node;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

public class ReceiveComponentUpdatesProcessor extends AbstractRemoteRosterProcessor{

	private RosterManager _rosterManager;

	public ReceiveComponentUpdatesProcessor(RosterManager rosterManager) {
		_rosterManager = rosterManager;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException
	{

		IQ myPacket = (IQ) packet;
		String to = myPacket.getTo().toString();
		String username = getUsernameFromJid(to);

		List<Node> nodes = findNodesInDocument(myPacket.getElement().getDocument(), "//roster:item");
		for (Node n : nodes) {

			Roster roster;
			try {
				roster = _rosterManager.getRoster(username);
				String jid = n.valueOf("@jid");
				String name = n.valueOf("@name");
				if (jid.equals(myPacket.getFrom().toString()))
				{
					//Do not add the component itself to the contact list
					break;
				}
				List<String> grouplist = new ArrayList<String>();
				List<Node> groupnodes = findNodesInDocument(n.getDocument(), "//roster:group");
				for (Node ne : groupnodes) {
					String groupName = ne.getText();
					grouplist.add(groupName);
				}
				boolean rosterPersisten = JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", true);
//				System.out.println("füge item hinzu: "+jid+" mit gruppen: "+grouplist.toString());
				RosterItem item = roster.createRosterItem(new JID(jid), name, grouplist, false, rosterPersisten);
				//RosterItem getThat = roster.getRosterItem(new JID(jid));
				item.setSubStatus(RosterItem.SUB_BOTH);
				roster.updateRosterItem(item);
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Exception beim hinzufuegen eines Konaktes!");
			}
		}
	}
}
