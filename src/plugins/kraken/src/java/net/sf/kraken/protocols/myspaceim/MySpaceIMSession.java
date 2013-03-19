/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.myspaceim;

import java.util.ArrayList;

import net.sf.jmyspaceiml.MSIMConnection;
import net.sf.jmyspaceiml.MSIMException;
import net.sf.jmyspaceiml.packet.ActionMessage;
import net.sf.jmyspaceiml.packet.InstantMessage;
import net.sf.jmyspaceiml.packet.StatusMessage;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.ChatStateType;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.xmpp.packet.JID;

/**
 * Represents a MySpaceIM session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with MySpaceIM.
 *
 * @author Daniel Henninger
 */
public class MySpaceIMSession extends TransportSession<MySpaceIMBuddy> {

    static Logger Log = Logger.getLogger(MySpaceIMSession.class);

    /**
     * Create a MySpaceIM Session instance.
     *
     * @param registration Registration information used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public MySpaceIMSession(Registration registration, JID jid, MySpaceIMTransport transport, Integer priority) {
        super(registration, jid, transport, priority);
    }

    // Listener
    private MySpaceIMListener listener;
    
    // Connection instance
    private MSIMConnection connection;

    public MSIMConnection getConnection() {
        return connection;
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
        setPendingPresenceAndStatus(presenceType, verboseStatus); 
        if (!isLoggedIn()) {  
            try {
                connection = new MSIMConnection();
                connection.connect();
                connection.login(getRegistration().getUsername(), getRegistration().getPassword());
                this.setLoginStatus(TransportLoginStatus.LOGGED_IN);
                listener = new MySpaceIMListener(this);
                connection.addMessageListener(listener);
                connection.getContactManager().addContactListener(listener);
                connection.getContactManager().requestContacts();
            }
            catch (MSIMException e) {
                Log.error("MySpace: Failure while logging in:", e);
                setFailureStatus(ConnectionFailureReason.UNKNOWN);
                sessionDisconnected("Failure while logging in.");
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logOut()
     */
    @Override
    public void logOut() {
        if (connection != null) {
            if (listener != null) {
                connection.getContactManager().removeContactListener(listener);
                connection.removeMessageListener(listener);
            }
            connection.disconnect();
        }
        cleanUp();
        sessionDisconnectedNoReconnect(null);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#cleanUp()
     */
    @Override
    public void cleanUp() {
        if (connection != null) {

            connection = null;
        }
        if (listener != null) {
            listener = null;
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(MySpaceIMBuddy contact) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(MySpaceIMBuddy contact) {
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("MySpace: accept-adding is currently not implemented."
                + " Cannot accept-add: " + userID);
        // TODO: Currently unimplemented
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
        InstantMessage msg = new InstantMessage();
        msg.setTo(getTransport().convertJIDToID(jid));
        msg.setFrom(String.valueOf(connection.getUserID()));
        msg.setBody(message);
        connection.sendPacket(msg);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID,net.sf.kraken.type.ChatStateType)
     */
    @Override
    public void sendChatState(JID jid, ChatStateType chatState) {
        ActionMessage msg = new ActionMessage();
        msg.setTo(getTransport().convertJIDToID(jid));
        msg.setFrom(String.valueOf(connection.getUserID()));
        msg.setAction(chatState == ChatStateType.composing ? ActionMessage.ACTION_TYPING : ActionMessage.ACTION_STOPTYPING);
        connection.sendPacket(msg);
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
     * @see net.sf.kraken.session.TransportSession#updateStatus(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        StatusMessage msg = new StatusMessage();
        msg.setTo(getTransport().convertJIDToID(jid));
        msg.setFrom(String.valueOf(connection.getUserID()));
        msg.setStatusCode(((MySpaceIMTransport)getTransport()).convertXMPPStatusToMySpaceIM(presenceType));
        msg.setStatusMessage(verboseStatus);
        connection.sendPacket(msg);
    }

}
