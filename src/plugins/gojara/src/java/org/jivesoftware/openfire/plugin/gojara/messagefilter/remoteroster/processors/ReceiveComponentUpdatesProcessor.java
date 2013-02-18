package org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.remoteroster.RemoteRosterInterceptor;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * 
 * This class implements the XEP-xxx Remote Roster Management standard
 * "2.5 Component sends roster update". Part of command pattern used in
 * {@link RemoteRosterInterceptor}
 * 
 * Further information: <a
 * href="http://jkaluza.fedorapeople.org/remote-roster.html#sect-id215516"
 * >Here</a>
 * 
 * @author Holger Bergunde
 * 
 */
public class ReceiveComponentUpdatesProcessor extends AbstractRemoteRosterProcessor {

	private RosterManager _rosterManager;
	private String _mySubdomain;

	public ReceiveComponentUpdatesProcessor(RosterManager rosterManager, String subdomain) {
		_mySubdomain = subdomain;
		Log.debug("Created ReceiveComponentUpdatesProcessor for " + _mySubdomain);
		_rosterManager = rosterManager;
	}

	@Override
	public void process(Packet packet) throws PacketRejectedException {
		Log.debug("Processing packet in ClientToComponentUpdateProcessor for " + _mySubdomain);
		IQ myPacket = (IQ) packet;
		IQ response = IQ.createResultIQ(myPacket);
		
		String to = myPacket.getTo().toString();
		String username = getUsernameFromJid(to);

		List<Node> nodes = findNodesInDocument(myPacket.getElement().getDocument(), "//roster:item");
		for (Node n : nodes) {
			
			Roster roster;
			String jid = n.valueOf("@jid");
			String name = n.valueOf("@name");
			String subvalue = n.valueOf("@subscription");
			
			if(subvalue.equals("both")){
				try {
					if (jid.equals(myPacket.getFrom().toString())) {
						// Do not add the component itself to the contact list
						break;
					}
					roster = _rosterManager.getRoster(username);
					List<String> grouplist = new ArrayList<String>();
					List<Node> groupnodes = findNodesInDocument(n.getDocument(), "//roster:group");
					for (Node ne : groupnodes) {
						String groupName = ne.getText();
						grouplist.add(groupName);
					}
					boolean rosterPersisten = JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", true);
					Log.debug("Adding/Updating User " + jid + " to roster " + to);
					try {
						RosterItem item = roster.getRosterItem(new JID(jid));
						item.setGroups(grouplist);
						roster.updateRosterItem(item);
						//dont send iq-result if just updating user
						break;
					} catch (UserNotFoundException exc) {
						//Then we should add him! 
					}
					RosterItem item = roster.createRosterItem(new JID(jid), name, grouplist, false, rosterPersisten);
					item.setSubStatus(RosterItem.SUB_BOTH);
					roster.updateRosterItem(item);
				} catch (Exception e) {
					Log.debug("Could not add user to Roster although no entry should exist..." + username, e);
					e.printStackTrace();
				}
			} else if (subvalue.equals("remove")){
				try {
					roster = _rosterManager.getRoster(username);
					Log.debug("Removing contact " + username + " from contact list.");
					//If the contact didnt exist in contact list it is likely the transport itself in which case
					//we do not want to forward this msg to server...
					RosterItem item = roster.deleteRosterItem(new JID(jid), false);
					if (item == null) {
						throw new PacketRejectedException(); 
					}
				} catch (UserNotFoundException e) {
					Log.debug("Could not find user while cleaning up the roster in GoJara for user " + username, e);
					response.setType(IQ.Type.error);
				} catch (SharedGroupException e) {
					// We should ignore this. External contacts cannot be in
					// shared groups
				}
			}
			dispatchPacket(response);
		}
	}
}
