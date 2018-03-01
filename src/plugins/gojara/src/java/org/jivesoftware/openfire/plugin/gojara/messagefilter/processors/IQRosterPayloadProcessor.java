package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.dom4j.tree.DefaultAttribute;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * This class implements the XEP-0321 Remote Roster Management standard "2.3 Server or component requests user's roster".
 * Part of command pattern used in {@link RemoteRosterInterceptor}
 *
 * Further information: <a href="http://jkaluza.fedorapeople.org/remote-roster.html#sect-id215516" >Here</a>
 *
 * @author Holger Bergunde
 * @author axel.frederik.brand
 *
 */
public class IQRosterPayloadProcessor extends AbstractRemoteRosterProcessor {

    private RosterManager _rosterManager;

    public IQRosterPayloadProcessor(RosterManager rosterMananger) {
        Log.info("Created IQRosterPayloadProcessor");
        _rosterManager = rosterMananger;
    }

    @Override
    public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
        Log.debug("Processing packet in IQRosterPayloadProcessor for " + subdomain + " : " + packet.toString());

        IQ myPacket = (IQ) packet;
        String username = getUsernameFromJid(to);

        if (myPacket.getType().equals(IQ.Type.get)) {
            handleIQget(myPacket, subdomain, username);
        } else if (myPacket.getType().equals(IQ.Type.set)) {
            handleIQset(myPacket, subdomain, username);
        }

    }

    private void handleIQget(IQ myPacket, String subdomain, String username) {
        if (JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false)) {
            Roster roster;
            try {
                roster = _rosterManager.getRoster(username);
                Collection<RosterItem> items = roster.getRosterItems();
                Log.debug("Sending contacts with subdomain " + subdomain + " from user " + username + " to external Component");
                sendRosterToComponent(myPacket, items, subdomain);
            } catch (UserNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            Log.debug("Sending nonpersistant-RemoteRosterResponse to external Component  for User: " + username);
            sendEmptyRoster(myPacket, subdomain);
        }
    }

    private void sendRosterToComponent(IQ requestPacket, Collection<RosterItem> items, String subdomain) {

        IQ response = IQ.createResultIQ(requestPacket);
        response.setTo(subdomain);
        Element query = new DefaultElement( QName.get("query","jabber:iq:roster"));
        for (RosterItem i : items) {
            String jid = i.getJid().toString();
            if (!jid.equals(subdomain) && jid.contains(subdomain)) {
                Log.debug("Roster exchange for external component " + subdomain + ". Sending user " + i.getJid().toString());
                Element item = new DefaultElement("item", null);
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
        response.setChildElement(query);
        dispatchPacket(response);
    }

    private void sendEmptyRoster(Packet requestPacket, String subdomain) {
        IQ iq = (IQ) requestPacket;
        IQ response = IQ.createResultIQ(iq);
        response.setTo(subdomain);
        Element query = new DefaultElement( QName.get("query","jabber:iq:roster") );
        response.setChildElement(query);
        dispatchPacket(response);
    }

    private void handleIQset(IQ myPacket, final String subdomain, final String username) throws PacketRejectedException {
        IQ response = IQ.createResultIQ(myPacket);

        List<Node> nodes = findNodesInDocument(myPacket.getElement().getDocument(), "//roster:item");
        for (Node n : nodes) {

            Roster roster;
            String jid = n.valueOf("@jid");
            String name = n.valueOf("@name");
            String subvalue = n.valueOf("@subscription");
            // We dont want to add or delete the subdomain itself, so we have to
            // reject that packet, it seems openfire itself
            // can interpret the iq:roster remove stanzas in some way, this was
            // causing trouble on register:remove
            if (JiveGlobals.getBooleanProperty("plugin.remoteroster.ignoreSubdomains", true) && jid.equals(subdomain)
                    && subvalue.equals("both"))
                throw new PacketRejectedException();

            if (subvalue.equals("both")) {
                try {
                    roster = _rosterManager.getRoster(username);
                    List<String> grouplist = new ArrayList<String>();
                    List<Node> groupnodes = findNodesInDocument(n.getDocument(), "//roster:group");
                    for (Node ne : groupnodes) {
                        String groupName = ne.getText();
                        grouplist.add(groupName);
                    }
                    boolean rosterPersistent = JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", true);
                    Log.debug("Adding/Updating Contact " + jid + " to roster of " + username);
                    try {
                        RosterItem item = roster.getRosterItem(new JID(jid));
                        item.setGroups(grouplist);
                        roster.updateRosterItem(item);
                        // dont send iq-result if just updating user
                        continue;
                    } catch (UserNotFoundException exc) {
                        // Then we should add him!
                    }
                    RosterItem item = roster.createRosterItem(new JID(jid), name, grouplist, false, rosterPersistent);
                    item.setSubStatus(RosterItem.SUB_BOTH);
                    roster.updateRosterItem(item);
                } catch (Exception e) {
                    Log.info("Could not add user to Roster although no entry should exist..." + username, e);

                }
                dispatchPacket(response);

            } else if (subvalue.equals("remove")) {
                // check if its the right package to initiate unregister removal of contacts
                // we dont need to do this when persistent = false because they will get deleted as soon as gateway is unavailable
                if (JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false) && jid.equals(subdomain)) {
                    deleteSubdomainItemsFromRoster(username, subdomain);
                }
                // in ANY case, if its a roster:remove, we want to reject packet, as it causes errors if Openfire tries
                // to handle it
                throw new PacketRejectedException();
            }
        }
    }

    /**
     * Searches the users roster for a specific subdomain and deletes all contacts that contain subdomain
     *
     * @param username
     * @param subdomain
     */
    private void deleteSubdomainItemsFromRoster(String username, String subdomain) {
        try {
            Roster roster = _rosterManager.getRoster(username);
            Collection<RosterItem> items = roster.getRosterItems();
            for (RosterItem item : items) {
                String itemName = item.getJid().toString();
                if (itemName.contains(subdomain)) {
                    Log.debug("Removing contact " + item.getJid().toString() + " from contact list because of Unregister.");
                    roster.deleteRosterItem(item.getJid(), false);
                }
            }
        } catch (UserNotFoundException e) {
            Log.debug("Couldnt find User!" + e.toString());
        } catch (SharedGroupException e) {
            e.printStackTrace();
        }

    }

}
