package org.jivesoftware.openfire.plugin.packageProcessor;

import java.util.Collection;

import org.dom4j.Element;
import org.dom4j.tree.DefaultAttribute;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class SendRosterProcessor extends AbstractRemoteRosterProcessor {

	private RosterManager _rosterManager;
	private String _componentName;

	public SendRosterProcessor(RosterManager rosterMananger, String componentName) {
		_rosterManager = rosterMananger;
		_componentName = componentName;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException
	{
		IQ myPacket = (IQ) packet;

		String to = myPacket.getFrom().toString();
		String username = getUsernameFromJid(to);

		Roster roster;
		try {
			roster = _rosterManager.getRoster(username);
			Collection<RosterItem> items = roster.getRosterItems();
			sendRosterToComponent(packet, items);
		} catch (UserNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void sendRosterToComponent(Packet requestPacket, Collection<RosterItem> items)
	{
		IQ iq = (IQ) requestPacket;
		IQ response = IQ.createResultIQ(iq);
		response.setTo(_componentName);
		Element query = new DefaultElement("query");
		for (RosterItem i : items) {
			if (i.getJid().toString().contains(_componentName)) {
				System.out.println("roster exchange: habe: "+i.getJid().toString());
				Element item = new DefaultElement("item");
				item.add(new DefaultAttribute("jid", i.getJid().toString()));
				item.add(new DefaultAttribute("name", i.getNickname()));
				item.add(new DefaultAttribute("subscription", "both"));
				for (String s : i.getGroups()) {
					Element group = new DefaultElement("group");
					group.setText(s);
					item.add(group);
				}
				query.add(item);
			}
		}
		query.addNamespace("", "jabber:iq:roster");
		response.setChildElement(query);
		System.out.println("sende response: "+response.toString());
		dispatchPacket(response);
	}

}
