package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors;

import java.util.Collection;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * This class is a part of the command pattern used in
 * {@link RemoteRosterInterceptor}. If the remote contacts should not be 
 * saved permanently in the users Roster, this command will delete
 * contacts to the corresponding Transport upon receiving unavailable presence
 * from transport. This way the Users Roster will not get modified by the automated
 * unsubscribe presences triggered by deleting RosterItem in OF-Roster
 * 
 * @author Holger Bergunde
 * 
 */
public class NonPersistantRosterProcessor extends AbstractRemoteRosterProcessor {

	private RosterManager _rosterManager;
//	private String _subDomain;

	public NonPersistantRosterProcessor(RosterManager rostermananger) {
		Log.debug("Created NonPersistantProcessor");
		_rosterManager = rostermananger;
//		_subDomain = subdomain;
	}

	@Override
	public void process(Packet packet, String subdomain) throws PacketRejectedException {
		Log.debug("Processing packet in NonPersistantRosterProcessor for " + subdomain);
		Presence myPacket = (Presence) packet;
		String to = myPacket.getTo().toString();
		String username = getUsernameFromJid(to);
		if (myPacket.getType() != null && myPacket.getType().equals(Presence.Type.unavailable)) {
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
