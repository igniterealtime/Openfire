package org.jivesoftware.openfire.plugin.packageProcessor;

import java.util.Collection;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

public class CleanUpRosterProcessor extends AbstractRemoteRosterProcessor {

	private RosterManager _rosterManager;
	private String _subDomain;

	public CleanUpRosterProcessor(RosterManager rostermananger, String subdomain) {
		Log.debug("Createt CleanUpRosterProcessor for "+subdomain);
		_rosterManager = rostermananger;
		_subDomain = subdomain;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException
	{
		Log.debug("Processing packet in CleanUpRosterProcessor for "+_subDomain);
		Presence myPacket = (Presence) packet;
		String to = myPacket.getTo().toString();
		String username = getUsernameFromJid(to);
		if (myPacket.getType() != null && myPacket.getType().equals(Presence.Type.unavailable)) {
			try {
				Roster roster = _rosterManager.getRoster(username);

				Collection<RosterItem> items = roster.getRosterItems();

				for (RosterItem item : items) {
					String itemName = item.getJid().toString();
					if (itemName.contains(_subDomain) && !itemName.equals(_subDomain)) {
						Log.debug("Removing contact "+item.getJid().toString()+" from contact list.");
						roster.deleteRosterItem(item.getJid(), false);
					}
				}
			} catch (Exception e) {
				Log.debug("Execption occured when cleaning up the Roster.",e);
				e.printStackTrace();
			}

		}
	}

}
