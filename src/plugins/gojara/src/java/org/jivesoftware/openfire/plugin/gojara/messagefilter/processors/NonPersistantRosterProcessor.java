package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.Collection;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * When this Processor gets called, it deletes all Contacts of a given User that
 * contain a specific subdomain. We use this to clean up all Contacts of a Users
 * Gateway registration as soon as he logs out. In this case the Transport sends
 * a Unavailable Presence without subtext "Connecting" to the user.
 * 
 * @author Holger Bergunde
 * @author axel.frederik.brand
 * 
 */
public class NonPersistantRosterProcessor extends AbstractRemoteRosterProcessor {

	private RosterManager _rosterManager;

	public NonPersistantRosterProcessor(RosterManager rostermananger) {
		Log.info("Created NonPersistantProcessor");
		_rosterManager = rostermananger;
	}

	@Override
	public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
		Presence myPacket = (Presence) packet;
		if (myPacket.getType() != null && myPacket.getType().equals(Presence.Type.unavailable)
				&& !myPacket.getElement().getStringValue().equals("Connecting")) {
			String username = getUsernameFromJid(to);
			Log.debug("Processing packet in NonPersistantRosterProcessor for " + subdomain + "and user " + username + " Packet: "
					+ packet.toString());

			try {
				Roster roster = _rosterManager.getRoster(username);
				Collection<RosterItem> items = roster.getRosterItems();
				for (RosterItem item : items) {
					String itemName = item.getJid().toString();
					if (itemName.contains(subdomain) && !itemName.equals(subdomain)) {
						Log.debug("Removing contact " + item.getJid().toString() + " from contact list.");
						roster.deleteRosterItem(item.getJid(), false);
						
					}
				}

			} catch (Exception e) {
				Log.debug("Execption occured when cleaning up the Roster.", e);
				e.printStackTrace();
			}
		}
	}

}
