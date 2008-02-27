package org.jivesoftware.openfire.plugin;

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: gato
 * Date: Dec 8, 2006
 * Time: 10:09:20 AM
 * To change this template use File | Settings | File Templates.
 */
public class UserCreationPlugin implements Plugin {

    private static Hashtable<RosterItem.SubType, Map<String, Map<Presence.Type, Change>>> stateTable =
            new Hashtable<RosterItem.SubType, Map<String, Map<Presence.Type, Change>>>();
    private Element vCard;

    static {
        Hashtable<Presence.Type, Change> subrTable;
        Hashtable<Presence.Type, Change> subsTable;
        Hashtable<String, Map<Presence.Type, Change>> sr;

        sr = new Hashtable<String, Map<Presence.Type, Change>>();
        subrTable = new Hashtable<Presence.Type, Change>();
        subsTable = new Hashtable<Presence.Type, Change>();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_NONE, sr);
        // Item wishes to subscribe from owner
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a roster subscription negotiation.
        subrTable.put(Presence.Type.subscribe, new Change(RosterItem.RECV_SUBSCRIBE, null, null)); // no transition
        // Item granted subscription to owner
        // The item's state immediately goes from NONE to TO and ask is reset
        subrTable.put(Presence.Type.subscribed, new Change(null, RosterItem.SUB_TO, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // This makes no sense, there is no subscription to remove
        subrTable.put(Presence.Type.unsubscribe, new Change(null, null, null));
        // Owner has subscription to item revoked
        // Valid response if item requested subscription and we're denying request
        subrTable.put(Presence.Type.unsubscribed, new Change(null, null, RosterItem.ASK_NONE));
        // Owner asking to subscribe to item this is the normal way to begin
        // a roster subscription negotiation.
        subsTable.put(Presence.Type.subscribe, new Change(null, null, RosterItem.ASK_SUBSCRIBE));
        // Item granted a subscription from owner
        subsTable.put(Presence.Type.subscribed, new Change(RosterItem.RECV_NONE, RosterItem.SUB_FROM, null));
        // Owner asking to unsubscribe to item
        // This makes no sense (there is no subscription to unsubscribe from)
        subsTable.put(Presence.Type.unsubscribe, new Change(null, null, null));
        // Item has subscription from owner revoked
        // Valid response if item requested subscription and we're denying request
        subsTable.put(Presence.Type.unsubscribed, new Change(RosterItem.RECV_NONE, null, null));

        sr = new Hashtable<String, Map<Presence.Type, Change>>();
        subrTable = new Hashtable<Presence.Type, Change>();
        subsTable = new Hashtable<Presence.Type, Change>();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_FROM, sr);
        // Owner asking to subscribe to item
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a mutual roster subscription negotiation.
        subsTable.put(Presence.Type.subscribe, new Change(null, null, RosterItem.ASK_SUBSCRIBE));
        // Item granted a subscription from owner
        // This may be necessary if the recipient didn't get an earlier subscribed grant
        // or as a denial of an unsubscribe request
        subsTable.put(Presence.Type.subscribed, new Change(RosterItem.RECV_NONE, null, null));
        // Owner asking to unsubscribe to item
        // This makes no sense (there is no subscription to unsubscribe from)
        subsTable.put(Presence.Type.unsubscribe, new Change(null, RosterItem.SUB_NONE, null));
        // Item has subscription from owner revoked
        // Immediately transition to NONE state
        subsTable.put(Presence.Type.unsubscribed, new Change(RosterItem.RECV_NONE, RosterItem.SUB_NONE, null));
        // Item wishes to subscribe from owner
        // Item already has a subscription so only interesting if item had requested unsubscribe
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a mutual roster subscription negotiation.
        subrTable.put(Presence.Type.subscribe, new Change(RosterItem.RECV_NONE, null, null));
        // Item granted subscription to owner
        // The item's state immediately goes from FROM to BOTH and ask is reset
        subrTable.put(Presence.Type.subscribed, new Change(null, RosterItem.SUB_BOTH, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // This is the normal mechanism of removing subscription
        subrTable.put(Presence.Type.unsubscribe, new Change(RosterItem.RECV_UNSUBSCRIBE, RosterItem.SUB_NONE, null));
        // Owner has subscription to item revoked
        // Valid response if owner requested subscription and item is denying request
        subrTable.put(Presence.Type.unsubscribed, new Change(null, null, RosterItem.ASK_NONE));

        sr = new Hashtable<String, Map<Presence.Type, Change>>();
        subrTable = new Hashtable<Presence.Type, Change>();
        subsTable = new Hashtable<Presence.Type, Change>();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_TO, sr);
        // Owner asking to subscribe to item
        // We're already subscribed, may be trying to unset a unsub request
        subsTable.put(Presence.Type.subscribe, new Change(null, null, RosterItem.ASK_NONE));
        // Item granted a subscription from owner
        // Sets mutual subscription
        subsTable.put(Presence.Type.subscribed, new Change(RosterItem.RECV_NONE, RosterItem.SUB_BOTH, null));
        // Owner asking to unsubscribe to item
        // Normal method of removing subscription
        subsTable.put(Presence.Type.unsubscribe, new Change(null, RosterItem.SUB_NONE, RosterItem.ASK_UNSUBSCRIBE));
        // Item has subscription from owner revoked
        // No subscription to unsub, makes sense if denying subscription request or for
        // situations where the original unsubscribed got lost
        subsTable.put(Presence.Type.unsubscribed, new Change(RosterItem.RECV_NONE, null, null));
        // Item wishes to subscribe from owner
        // This is the normal way to negotiate a mutual subscription
        subrTable.put(Presence.Type.subscribe, new Change(RosterItem.RECV_SUBSCRIBE, null, null));
        // Item granted subscription to owner
        // Owner already subscribed to item, could be a unsub denial or a lost packet
        subrTable.put(Presence.Type.subscribed, new Change(null, null, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // There is no subscription. May be trying to cancel earlier subscribe request.
        subrTable.put(Presence.Type.unsubscribe, new Change(RosterItem.RECV_NONE, RosterItem.SUB_NONE, null));
        // Owner has subscription to item revoked
        // Setting subscription to none
        subrTable.put(Presence.Type.unsubscribed, new Change(null, RosterItem.SUB_NONE, RosterItem.ASK_NONE));

        sr = new Hashtable<String, Map<Presence.Type, Change>>();
        subrTable = new Hashtable<Presence.Type, Change>();
        subsTable = new Hashtable<Presence.Type, Change>();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_BOTH, sr);
        // Owner asking to subscribe to item
        // Makes sense if trying to cancel previous unsub request
        subsTable.put(Presence.Type.subscribe, new Change(null, null, RosterItem.ASK_NONE));
        // Item granted a subscription from owner
        // This may be necessary if the recipient didn't get an earlier subscribed grant
        // or as a denial of an unsubscribe request
        subsTable.put(Presence.Type.subscribed, new Change(RosterItem.RECV_NONE, null, null));
        // Owner asking to unsubscribe to item
        // Set flags
        subsTable.put(Presence.Type.unsubscribe, new Change(null, RosterItem.SUB_FROM, RosterItem.ASK_UNSUBSCRIBE));
        // Item has subscription from owner revoked
        // Immediately transition them to TO state
        subsTable.put(Presence.Type.unsubscribed, new Change(RosterItem.RECV_NONE, RosterItem.SUB_TO, null));
        // Item wishes to subscribe to owner
        // Item already has a subscription so only interesting if item had requested unsubscribe
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a mutual roster subscription negotiation.
        subrTable.put(Presence.Type.subscribe, new Change(RosterItem.RECV_NONE, null, null));
        // Item granted subscription to owner
        // Redundant unless denying unsub request
        subrTable.put(Presence.Type.subscribed, new Change(null, null, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // This is the normal mechanism of removing subscription
        subrTable.put(Presence.Type.unsubscribe, new Change(RosterItem.RECV_UNSUBSCRIBE, RosterItem.SUB_TO, null));
        // Owner has subscription to item revoked
        // Immediately downgrade state to FROM
        subrTable.put(Presence.Type.unsubscribed,
                new Change(RosterItem.RECV_NONE, RosterItem.SUB_FROM, RosterItem.ASK_NONE));
    }

    /**
     * <p>Indicate a state change.</p>
     * <p>Use nulls to indicate fields that should not be changed.</p>
     */
    private static class Change {
        public Change(RosterItem.RecvType recv, RosterItem.SubType sub, RosterItem.AskType ask) {
            newRecv = recv;
            newSub = sub;
            newAsk = ask;
        }

        public RosterItem.RecvType newRecv;
        public RosterItem.SubType newSub;
        public RosterItem.AskType newAsk;
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        // Do nothing
    }

    public void destroyPlugin() {
        // Do nothing
    }

    public void createUsers(String userPrefix, int from, int total) {
        // Create users
        UserManager userManager = XMPPServer.getInstance().getUserManager();
        System.out.println("Creating users accounts: " + total);
        int created = 0;
        for (int i = from; i < from + total; i++) {
            try {
                String username = userPrefix + i;
                userManager.createUser(username, username, username, username + "@" + username);
                created++;
            } catch (UserAlreadyExistsException e) {
                // Ignore
            }
        }
        System.out.println("Accounts created successfully: " + created);
    }

    public void populateRosters(String userPrefix, int from, int total, int usersPerRoster) {
        XMPPServer server = XMPPServer.getInstance();
        RosterManager rosterManager = server.getRosterManager();


        int batchTotal = total / usersPerRoster;
        System.out.println("Total batches of users: " + batchTotal);
        for (int batchNumber = 0; batchNumber < batchTotal; batchNumber++) {

            System.out.println("Current batch: " + batchNumber + ". Users: " + batchNumber*usersPerRoster + " - " + ((batchNumber*usersPerRoster)+usersPerRoster));
            // Add rosters items between connected users
            for (int i = (batchNumber * usersPerRoster) + from;
                 i < (batchNumber * usersPerRoster) + usersPerRoster + from; i++) {
                String username = userPrefix + i;
                Roster roster;
                try {
                    roster = rosterManager.getRoster(username);
                } catch (UserNotFoundException e) {
                    continue;
                }
                if (roster.getRosterItems().size() >= usersPerRoster) {
                    // Roster already populated. Skip it.
                    continue;
                }
                for (int j = (batchNumber * usersPerRoster) + from;
                     j < (batchNumber * usersPerRoster) + usersPerRoster + from; j++) {
                    if (i == j) {
                        continue;
                    }

                    try {
                        Roster recipientRoster = rosterManager.getRoster(userPrefix + j);

                        manageSub(server.createJID(userPrefix + j, null), true, Presence.Type.subscribe, roster);
                        manageSub(server.createJID(username, null), false, Presence.Type.subscribe, recipientRoster);

                        manageSub(server.createJID(userPrefix + j, null), true, Presence.Type.subscribed, roster);
                        manageSub(server.createJID(username, null), false, Presence.Type.subscribed, recipientRoster);

                    } catch (UserNotFoundException e) {
                        // Ignore
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Rosters populated with " + usersPerRoster + " contacts.");
    }

    public void createVCards(String userPrefix, int from, int total) {
        // Create users
        System.out.println("Creating users vCards: " + total);
        int created = 0;
        for (int i = from; i < from + total; i++) {
            try {
                String username = userPrefix + i;
                VCardManager.getInstance().setVCard(username, getDefaultVCard());
                created++;
            } catch (Exception e) {
                // Ignore
            }
        }
        System.out.println("VCards created successfully: " + created);
    }

    private Element getDefaultVCard() {
        if (vCard != null) {
            return vCard;
        }
        try {
            String xml = "<vCard xmlns=\"vcard-temp\"><N><FAMILY>Dombiak</FAMILY><GIVEN>Gaston</GIVEN><MIDDLE>Maximiliano</MIDDLE></N><ORG><ORGNAME>Jive Software</ORGNAME><ORGUNIT/></ORG><FN>Gaston Maximiliano Dombiak</FN><ROLE/><DESC/><JABBERID>gato@jivesoftware.com</JABBERID><userName>12011349</userName><server>ss.ctbc.com.br</server><URL/><NICKNAME>Gato</NICKNAME><TITLE/><PHOTO><TYPE>image/jpeg</TYPE><BINVAL>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</BINVAL></PHOTO><EMAIL><WORK/><INTERNET/><PREF/><USERID>gaston@jivesoftware.com</USERID></EMAIL><EMAIL><HOME/><INTERNET/><PREF/><USERID>gaston@jivesoftware.com</USERID></EMAIL><TEL><PAGER/><WORK/><NUMBER/></TEL><TEL><CELL/><WORK/><NUMBER/></TEL><TEL><VOICE/><WORK/><NUMBER/></TEL><TEL><FAX/><WORK/><NUMBER/></TEL><TEL><PAGER/><HOME/><NUMBER/></TEL><TEL><CELL/><HOME/><NUMBER/></TEL><TEL><VOICE/><HOME/><NUMBER/></TEL><TEL><FAX/><HOME/><NUMBER/></TEL><ADR><WORK/><EXTADD/><PCODE>97204</PCODE><REGION>Oregon</REGION><STREET>317 SW Alder St Ste 500</STREET><CTRY>USA</CTRY><LOCALITY>Portland</LOCALITY></ADR><ADR><HOME/><EXTADD/><PCODE/><REGION/><STREET/><CTRY/><LOCALITY/></ADR></vCard>";
            vCard = DocumentHelper.parseText(xml).getRootElement();
            return vCard;
        } catch (DocumentException e) {
            return null;
        }
    }


    private boolean manageSub(JID target, boolean isSending, Presence.Type type, Roster roster)
            throws UserAlreadyExistsException, SharedGroupException {
        RosterItem item = null;
        RosterItem.AskType oldAsk;
        RosterItem.SubType oldSub = null;
        RosterItem.RecvType oldRecv;
        boolean newItem = false;
        try {
            if (roster.isRosterItem(target)) {
                item = roster.getRosterItem(target);
            } else {
                if (Presence.Type.unsubscribed == type || Presence.Type.unsubscribe == type ||
                        Presence.Type.subscribed == type) {
                    // Do not create a roster item when processing a confirmation of
                    // an unsubscription or receiving an unsubscription request or a
                    // subscription approval from an unknown user
                    return false;
                }
                item = roster.createRosterItem(target, false, true);
                item.setGroups(Arrays.asList("Friends"));
                roster.updateRosterItem(item);
                newItem = true;
            }
            // Get a snapshot of the item state
            oldAsk = item.getAskStatus();
            oldSub = item.getSubStatus();
            oldRecv = item.getRecvStatus();
            // Update the item state based in the received presence type
            updateState(item, type, isSending);
            // Update the roster IF the item state has changed
            if (oldAsk != item.getAskStatus() || oldSub != item.getSubStatus() ||
                    oldRecv != item.getRecvStatus()) {
                roster.updateRosterItem(item);
            } else if (newItem) {
                // Do not push items with a state of "None + Pending In"
                if (item.getSubStatus() != RosterItem.SUB_NONE ||
                        item.getRecvStatus() != RosterItem.RECV_SUBSCRIBE) {
                    roster.broadcast(item, false);
                }
            }
        }
        catch (UserNotFoundException e) {
            // Should be there because we just checked that it's an item
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        return oldSub != item.getSubStatus();
    }

    private static void updateState(RosterItem item, Presence.Type action, boolean isSending) {
        Map<String, Map<Presence.Type, Change>> srTable = stateTable.get(item.getSubStatus());
        Map<Presence.Type, Change> changeTable = srTable.get(isSending ? "send" : "recv");
        Change change = changeTable.get(action);
        if (change.newAsk != null && change.newAsk != item.getAskStatus()) {
            item.setAskStatus(change.newAsk);
        }
        if (change.newSub != null && change.newSub != item.getSubStatus()) {
            item.setSubStatus(change.newSub);
        }
        if (change.newRecv != null && change.newRecv != item.getRecvStatus()) {
            item.setRecvStatus(change.newRecv);
        }
    }

}


