/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.irc;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.kraken.muc.MUCTransportRoom;
import net.sf.kraken.pseudoroster.PseudoRoster;
import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.pseudoroster.PseudoRosterManager;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.*;
import net.sf.kraken.util.StringUtils;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.commands.AwayCommand;
import f00f.net.irc.martyr.commands.IsonCommand;
import f00f.net.irc.martyr.commands.ListCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.commands.NamesCommand;
import f00f.net.irc.martyr.commands.QuitCommand;
import f00f.net.irc.martyr.services.AutoReconnect;
import f00f.net.irc.martyr.services.AutoRegister;
import f00f.net.irc.martyr.services.AutoResponder;

/**
 * @author Daniel Henninger
 */
public class IRCSession extends TransportSession<IRCBuddy> {

    static Logger Log = Logger.getLogger(IRCSession.class);

    /**
     * Timer to check for online status.
     */
    public Timer timer = new Timer();

    /**
     * Interval at which status is checked.
     */
    private int timerInterval = 30000; // 30 seconds

    /**
     * Status checker.
     */
    StatusCheck statusCheck;

    public IRCSession(Registration registration, JID jid, IRCTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

        pseudoRoster = PseudoRosterManager.getInstance().getPseudoRoster(registration);
        for (String contact : pseudoRoster.getContacts()) {
            getBuddyManager().storeBuddy(new IRCBuddy(getBuddyManager(), contact, pseudoRoster.getItem(contact)));
        }
    }

    /**
     * Our pseudo roster.
     *
     * No server side buddy list, so we track it all here.
     */
    private PseudoRoster pseudoRoster;

    public IRCConnection connection;
    private AutoResponder autoResponder;
    private AutoRegister autoRegister;
    private AutoReconnect autoReconnect;
    private IRCListener listener;

    public IRCConnection getConnection() {
        return connection;
    }

    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
        connection = new IRCConnection();
        autoResponder = new AutoResponder(connection);
//        autoReconnect = new AutoReconnect(connection);
        autoRegister = new AutoRegister(connection, getRegistration().getNickname(), getRegistration().getNickname(), "IM Gateway User", getRegistration().getPassword());
        listener = new IRCListener(this);
        listener.enable();
        new Thread() {
            @Override
            public void run() {
                try {
                    connection.connect(JiveGlobals.getProperty("plugin.gateway.irc.connecthost", "irc.freenode.net"),
                            JiveGlobals.getIntProperty("plugin.gateway.irc.connectport", 7000));
                    setPresence(PresenceType.available);
                    setLoginStatus(TransportLoginStatus.LOGGED_IN);
                    try {
                        getTransport().syncLegacyRoster(getJID(), getBuddyManager().getBuddies());
                    }
                    catch (UserNotFoundException e) {
                        Log.debug("IRC: Error finding user while syncing legacy roster.");
                    }
                    List<String> buddyList = new ArrayList<String>();
                    for (TransportBuddy buddy : getBuddyManager().getBuddies()) {
                        buddyList.add(buddy.getName());
                    }
                    if (!buddyList.isEmpty()) {
                        connection.sendCommand(new IsonCommand(StringUtils.join(buddyList, " ")));
                    }
                    statusCheck = new StatusCheck();
                    timer.schedule(statusCheck, timerInterval, timerInterval);
                    getBuddyManager().activate();
                }
                catch (UnknownHostException e) {
                    Log.debug("IRC: Unable to connect to host:", e);
                    setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
                    sessionDisconnected("IRC server does not appear to exist.");
                }
                catch (IOException e) {
                    Log.debug("IRC: Connection error while trying to connect ot IRC server:", e);
                    setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
                    sessionDisconnected("Connection failed while trying to contact IRC server..");
                }
            }
        }.start();
    }

    @Override
    public void logOut() {
        connection.sendCommand(new QuitCommand());
        cleanUp();
        sessionDisconnectedNoReconnect(null);
    }

    @Override
    public void cleanUp() {
        if (timer != null) {
            try {
                timer.cancel();
            }
            catch (Exception e) {
                // Ignore
            }
            timer = null;
        }
        if (statusCheck != null) {
            try {
                statusCheck.cancel();
            }
            catch (Exception e) {
                // Ignore
            }
            statusCheck = null;
        }
        if (listener != null) {
            try {
                listener.disable();
            }
            catch (Exception e) {
                // Ignore
            }
            listener = null;
        }
        if (autoResponder != null) {
            try {
                autoResponder.disable();
            }
            catch (Exception e) {
                // Ignore
            }
            autoResponder = null;
        }
        if (autoRegister != null) {
            try {
                autoRegister.disable();
            }
            catch (Exception e) {
                // Ignore
            }
            autoRegister = null;
        }
        if (autoReconnect != null) {
            try {
                autoReconnect.disable();
            }
            catch (Exception e) {
                // Ignore
            }
            autoReconnect = null;
        }
        if (connection != null) {
            try {
                connection.disconnect();
            }
            catch (Exception e) {
                // Ignore
            }
            connection = null;
        }
    }
    
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        String awayMsg = ((IRCTransport)getTransport()).convertJabStatusToIRC(presenceType, verboseStatus);
        if (awayMsg == null) {
            try {
                connection.sendCommand(new AwayCommand());
//                setPresence(PresenceType.available);
            }
            catch (Exception e) {
                // Ignore: is due to lost connection during logout typically
            }
        }
        else {
            try {
                connection.sendCommand(new AwayCommand(awayMsg));
//                setPresence(PresenceType.away);
            }
            catch (Exception e) {
                // Ignore: is due to lost connection during logout typically
            }
        }
    }

    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
        String contact = getTransport().convertJIDToID(jid);
        PseudoRosterItem rosterItem;
        if (pseudoRoster.hasItem(contact)) {
            rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(nickname);
            rosterItem.setGroups(groups);
        }
        else {
            rosterItem = pseudoRoster.createItem(contact, nickname, groups);
        }

        getBuddyManager().storeBuddy(new IRCBuddy(getBuddyManager(), contact, rosterItem));

//        connection.sendCommand(new IsonCommand(contact));
    }

    @Override
    public void removeContact(IRCBuddy contact) {
        String ircContact = getTransport().convertJIDToID(contact.getJID());
        pseudoRoster.removeItem(ircContact);
    }

    @Override
    public void updateContact(IRCBuddy contact) {
        String ircContact = getTransport().convertJIDToID(contact.getJID());
        if (pseudoRoster.hasItem(ircContact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(ircContact);
            rosterItem.setNickname(contact.getNickname());
            rosterItem.setGroups((List<String>)contact.getGroups());
//            connection.sendCommand(new IsonCommand(ircContact));
        }
        else {
            PseudoRosterItem rosterItem = pseudoRoster.createItem(ircContact, contact.getNickname(), (List<String>)contact.getGroups());
            getBuddyManager().storeBuddy(new IRCBuddy(getBuddyManager(), ircContact, rosterItem));
//            connection.sendCommand(new IsonCommand(ircContact));
        }
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("IRC: accept-adding is currently not implemented."
                + " Cannot accept-add: " + userID);
        // TODO: Currently unimplemented
    }

    @Override
    public void sendMessage(JID jid, String message) {
        connection.sendCommand(new MessageCommand(getTransport().convertJIDToID(jid), message));
    }

    @Override
    public void sendChatState(JID jid, ChatStateType chatState) {
        // Nothing to do here
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateLegacyAvatar(String, byte[])
     */
    @Override
    public void updateLegacyAvatar(String type, byte[] data) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#getRooms()
     */
    @Override
    public void getRooms() {
        if (getTransport().getMUCTransport().isRoomCacheOutOfDate()) {
            getTransport().getMUCTransport().clearRoomCache();
            connection.sendCommand(new ListCommand());
            getTransport().getMUCTransport().updateRoomCacheTimestamp();
        }
        else {
            // This will be ignored if no one asked for it.
            getTransport().getMUCTransport().sendRooms(getJID(), getTransport().getMUCTransport().getCachedRooms());
        }
    }


    /**
     * @see net.sf.kraken.session.TransportSession#getRoomInfo(String room)
     */
    @Override
    public void getRoomInfo(String room) {
        if (getTransport().getMUCTransport().isRoomCacheOutOfDate()) {
            connection.sendCommand(new ListCommand(room));
        }
        else {
            // This will be ignored if no one asked for it.
            MUCTransportRoom mucRoom = getTransport().getMUCTransport().getCachedRoom(room);
            if (mucRoom != null) {
                getTransport().getMUCTransport().sendRoomInfo(getJID(), getTransport().getMUCTransport().convertIDToJID(mucRoom.getName(), null), mucRoom);
            }
            else {
                getTransport().getMUCTransport().cancelPendingRequest(getJID(), getTransport().getMUCTransport().convertIDToJID(room, null), NameSpace.DISCO_INFO);
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#getRoomMembers(String room)
     */
    @Override
    public void getRoomMembers(String room) {
//        transport.cancelPendingRequest(getSession().getJID(), transport.convertIDToJID(roomname, nickname), BaseMUCTransport.DISCO_ITEMS);
        connection.sendCommand(new NamesCommand(room));
    }

    private class StatusCheck extends TimerTask {
        /**
         * Send ISON to IRC to check on status of contacts.
         */
        @Override
        public void run() {
            List<String> buddyList = new ArrayList<String>();
            for (TransportBuddy buddy : getBuddyManager().getBuddies()) {
                buddyList.add(buddy.getName());
            }
            if (!buddyList.isEmpty()) {
                connection.sendCommand(new IsonCommand(buddyList));
            }
        }
    }
    
}
