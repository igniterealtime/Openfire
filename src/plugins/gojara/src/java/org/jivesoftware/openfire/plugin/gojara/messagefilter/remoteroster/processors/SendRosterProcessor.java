package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors;

import java.util.Collection;

import org.dom4j.Element;
import org.dom4j.tree.DefaultAttribute;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * This class implements the XEP-xxx Remote Roster Management standard
 * "2.3 Server or component requests user's roster". Part of command pattern
 * used in {@link RemoteRosterInterceptor}
 * 
 * Further information: <a
 * href="http://jkaluza.fedorapeople.org/remote-roster.html#sect-id215516"
 * >Here</a>
 * 
 * @author Holger Bergunde
 * 
 */
public class SendRosterProcessor extends AbstractRemoteRosterProcessor {

	private RosterManager _rosterManager;
	private String _componentName;

	public SendRosterProcessor(RosterManager rosterMananger, String componentName) {
		Log.debug("Createt SendRosterProcessor for " + componentName);
		_rosterManager = rosterMananger;
		_componentName = componentName;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException {
		Log.debug("Processing packet in SendRosterProcessor for " + _componentName);
		IQ myPacket = (IQ) packet;

		String from = myPacket.getFrom().toString();
		String username = getUsernameFromJid(from);

		Roster roster;
		try {
			roster = _rosterManager.getRoster(username);
			Collection<RosterItem> items = roster.getRosterItems();
			sendRosterToComponent(packet, items);
		} catch (UserNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void sendRosterToComponent(Packet requestPacket, Collection<RosterItem> items) {
		Log.debug("Sending contacts from user " + requestPacket.getFrom().toString() + " to external Component");
		IQ iq = (IQ) requestPacket;
		IQ response = IQ.createResultIQ(iq);
		response.setTo(_componentName);
		Element query = new DefaultElement("query");
		for (RosterItem i : items) {
			if (i.getJid().toString().contains(_componentName)) {
				Log.debug("Roster exchange for external component " + _componentName + ". Sending user "
						+ i.getJid().toString());
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
		dispatchPacket(response);
	}

}
