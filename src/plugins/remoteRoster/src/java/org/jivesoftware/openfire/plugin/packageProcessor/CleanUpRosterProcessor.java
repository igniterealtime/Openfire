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
		_rosterManager = rostermananger;
		_subDomain = subdomain;
	}
	
	@Override
	public void process(Packet packet) throws PacketRejectedException
	{
//		System.out.println("hab hier was ne oder was");
		Presence myPacket = (Presence) packet;
		String to = myPacket.getTo().toString();
		String username = getUsernameFromJid(to);
		//<presence id="19nTS-48" to="xmpp.dew08299" type="unavailable"/
		if (myPacket.getType() != null && myPacket.getType().equals(Presence.Type.unavailable))
		{	
			System.out.println("is unavaibale ------------");
			try {
				Roster roster = _rosterManager.getRoster(username);
				
				Collection <RosterItem> items = roster.getRosterItems();
				
				for (RosterItem item: items)
				{
					String itemName = item.getJid().toString();
					System.out.println("NAME: "+itemName+ " sub: "+_subDomain);
					if (itemName.contains(_subDomain) && !itemName.equals(_subDomain))
					{
						System.out.println("entferne "+itemName);
						roster.deleteRosterItem(item.getJid(), false);
					}
				}
				
				
				
				
				
				
				
				
			} catch (Exception e) {
				System.out.println("hier lief was falsch");
				e.printStackTrace();
			}
			
			
		}
	}

}
