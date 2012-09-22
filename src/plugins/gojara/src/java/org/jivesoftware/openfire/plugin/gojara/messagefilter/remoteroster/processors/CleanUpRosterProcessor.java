package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors;

import java.util.Collection;

import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * 
 * This class cleans up a roster from contacts if the user removes/deletes the
 * gateway registration. After deleting a registration there should not be any
 * gateway related contacts left
 * 
 * @author holger.bergunde
 * 
 */

public class CleanUpRosterProcessor extends AbstractRemoteRosterProcessor {

	private String _myDomain;
	private RosterManager _rosterManager;

	public CleanUpRosterProcessor(RosterManager rosterMananger, String mySubdomain) {
		Log.debug("Created CleanUpRosterProcessor for " + mySubdomain);
		_myDomain = mySubdomain;
		_rosterManager = rosterMananger;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException {
		if (packet instanceof IQ) {
			IQ iqPacket = (IQ) packet;

			if (findNodesInDocument(iqPacket.getElement().getDocument(), "//register:remove").size() > 0) {
				String username = getUsernameFromJid(packet.getFrom().toString());

				Roster roster;
				try {
					roster = _rosterManager.getRoster(username);

					Collection<RosterItem> items = roster.getRosterItems();

					for (RosterItem item : items) {
						String itemName = item.getJid().toString();
						if (itemName.contains(_myDomain) && !itemName.equals(_myDomain)) {
							Log.debug("Removing contact " + username + " from contact list.");
							roster.deleteRosterItem(item.getJid(), false);
						}
					}
				} catch (UserNotFoundException e) {
					Log.debug("Could not found user while cleaning up the roster in GoJara for user " + username, e);
				} catch (SharedGroupException e) {
					// We should ignore this. External contacts cannot be in
					// shared groups
				}
			}
		}
	}
}