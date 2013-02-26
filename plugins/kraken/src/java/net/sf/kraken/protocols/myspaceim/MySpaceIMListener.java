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

import java.lang.ref.WeakReference;
import java.util.List;

import net.sf.jmyspaceiml.MessageListener;
import net.sf.jmyspaceiml.contact.Contact;
import net.sf.jmyspaceiml.contact.ContactListener;
import net.sf.jmyspaceiml.packet.ActionMessage;
import net.sf.jmyspaceiml.packet.ErrorMessage;
import net.sf.jmyspaceiml.packet.InstantMessage;
import net.sf.jmyspaceiml.packet.MediaMessage;
import net.sf.jmyspaceiml.packet.ProfileMessage;
import net.sf.jmyspaceiml.packet.StatusMessage;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.util.chatstate.ChatStateEventSource;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * @author Daniel Henninger
 */
public class MySpaceIMListener implements MessageListener, ContactListener {

    static Logger Log = Logger.getLogger(MySpaceIMListener.class);
    
    MySpaceIMListener(MySpaceIMSession session) {
        this.myspaceimSessionRef = new WeakReference<MySpaceIMSession>(session);
    }

    WeakReference<MySpaceIMSession> myspaceimSessionRef;

    public MySpaceIMSession getSession() {
        return myspaceimSessionRef.get();
    }

    public void processIncomingMessage(InstantMessage msgPacket) {
        Log.debug("MySpaceIM: Received instant message packet: "+msgPacket);
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(msgPacket.getFrom()),
                msgPacket.getBody()
        );
    }
    
    public void processIncomingMessage(ActionMessage msgPacket) {
        Log.debug("MySpaceIM: Received action message packet: "+msgPacket);
        final ChatStateEventSource chatStateEventSource = getSession().getTransport().getChatStateEventSource();
        final JID receiver = getSession().getJID();
        final JID sender = getSession().getTransport().convertIDToJID(msgPacket.getFrom());
        
        if (msgPacket.getAction().equals(ActionMessage.ACTION_TYPING)) {
            chatStateEventSource.isComposing(sender, receiver);
        }
        else if (msgPacket.getAction().equals(ActionMessage.ACTION_STOPTYPING)) {
            chatStateEventSource.isPaused(sender, receiver);
        }
    }
    
    public void processIncomingMessage(StatusMessage msgPacket) {
        Log.debug("MySpaceIM: Received status message packet: "+msgPacket);
        if (getSession().getBuddyManager().isActivated()) {
            try {
                MySpaceIMBuddy buddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(msgPacket.getFrom()));
                buddy.setPresenceAndStatus(
                        ((MySpaceIMTransport)getSession().getTransport()).convertMySpaceIMStatusToXMPP(msgPacket.getStatusCode()),
                        msgPacket.getStatusMessage()
                );
            }
            catch (NotFoundException e) {
                // Not in our contact list.  Ignore.
                Log.debug("MySpaceIM: Received presense notification for contact we don't care about: "+msgPacket.getFrom());
            }
        }
        else {
            getSession().getBuddyManager().storePendingStatus(getSession().getTransport().convertIDToJID(msgPacket.getFrom()), ((MySpaceIMTransport)getSession().getTransport()).convertMySpaceIMStatusToXMPP(msgPacket.getStatusCode()), msgPacket.getStatusMessage());
        }
    }
    
    public void processIncomingMessage(MediaMessage msgPacket) {
        Log.debug("MySpaceIM: Received media message packet: "+msgPacket);
    }
    
    public void processIncomingMessage(ProfileMessage msgPacket) {
        Log.debug("MySpaceIM: Received profile message packet: "+msgPacket);
    }
    
    public void processIncomingMessage(ErrorMessage msgPacket) {
        Log.debug("MySpaceIM: Received error message packet: "+msgPacket);
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().getJID(),
                msgPacket.getErrorMessage(),
                Message.Type.error
        );
        if (msgPacket.isFatal()) {
            getSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);            
            getSession().sessionDisconnected(msgPacket.getErrorMessage());
        }
    }

    public void contactListUpdateReceived() {
        Log.debug("MySpaceIM: Got contact list.");
        List<Contact> contacts = getSession().getConnection().getContactManager().getContacts();
        for (Contact contact : contacts) {
            MySpaceIMBuddy buddy;
            try {
                buddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(String.valueOf(contact.getContactID())));
            }
            catch (NotFoundException e) {
                buddy = new MySpaceIMBuddy(getSession().getBuddyManager(), contact.getContactID());
                getSession().getBuddyManager().storeBuddy(buddy); 
            }
        }
        try {
            getSession().getTransport().syncLegacyRoster(getSession().getJID(), getSession().getBuddyManager().getBuddies());
        }
        catch (UserNotFoundException e) {
            Log.debug("Unable to sync MySpaceIM contact list for " + getSession().getJID(), e);
        }
        getSession().getBuddyManager().activate();
    }
    
}
