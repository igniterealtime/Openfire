/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.msn;

import org.hn.sleek.jmml.Contact;
import org.hn.sleek.jmml.ContactChangeEvent;
import org.hn.sleek.jmml.ContactStatus;
import org.hn.sleek.jmml.IncomingMessageEvent;
import org.hn.sleek.jmml.MessengerClientAdapter;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * MSN Listener Interface.
 *
 * This handles real interaction with MSN, but mostly is a listener for
 * incoming events from MSN.
 *
 * @author Daniel Henninger
 */
public class MSNListener extends MessengerClientAdapter {

    /**
     * Creates the MSN Listener instance.
     *
     * @param session Session this listener is associated with.
     */
    public MSNListener(MSNSession session) {
        this.msnSession = session;
    }

    /**
     * The session this listener is associated with.
     */
    public MSNSession msnSession = null;

    /**
     * Handles incoming messages from MSN.
     */
    public void incomingMessage(IncomingMessageEvent event) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJIDWithHighestPriority());
        m.setFrom(msnSession.getTransport().convertIDToJID(event.getUserName()));
        m.setBody(event.getMessage());
        msnSession.getTransport().sendPacket(m);
    }

    /**
     * Deals with a server disconnection.
     */
    public void serverDisconnected() {
        msnSession.logOut();
    }

    /**
     * Notification of a contact that exists on user's contact list.
     *
     * @param contact Contact from contact list.
     */
    public void contactReceived(Contact contact) {
        Log.debug("Got MSN contact " + contact.toString());
        try {
            msnSession.getTransport().addOrUpdateRosterItem(msnSession.getJID(), msnSession.getTransport().convertIDToJID(contact.getUserName()), contact.getFriendlyName(), "MSN Transport");
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to find session associated with MSN session.");
        }
    }

    /**
     * A property of a contact has changed.
     *
     * This can be a myriad of things that have changed.
     *
     * @param event An event instance for the change.
     */
    public void contactPropertyChanged(ContactChangeEvent event) {
        int propid = event.getProperty();
        if (propid == Contact.STATUS) {
            String newstatus = (String)event.getNewValue();
            Presence p = new Presence();
            p.setTo(msnSession.getJID());
            p.setFrom(msnSession.getTransport().convertIDToJID(event.getUserName()));
            if (newstatus.equals(ContactStatus.ONLINE)) {
                // We're good, send as is..
            }
            else if (newstatus.equals(ContactStatus.AWAY)) {
                p.setShow(Presence.Show.away);
            }
            else if (newstatus.equals(ContactStatus.BE_RIGHT_BACK)) {
                p.setShow(Presence.Show.away);
            }
            else if (newstatus.equals(ContactStatus.BUSY)) {
                p.setShow(Presence.Show.dnd);
            }
            else if (newstatus.equals(ContactStatus.IDLE)) {
                p.setShow(Presence.Show.away);
            }
            else if (newstatus.equals(ContactStatus.OFFLINE)) {
                p.setType(Presence.Type.unavailable);
            }
            else if (newstatus.equals(ContactStatus.ON_THE_PHONE)) {
                p.setShow(Presence.Show.dnd);
            }
            else if (newstatus.equals(ContactStatus.OUT_TO_LUNCH)) {
                p.setShow(Presence.Show.xa);
            }
            msnSession.getTransport().sendPacket(p);
        }
    }

    /**
     * Someone has added user to their contact list
     *
     * @param username Username that added this user.
     */
    public void reverseListChanged(String username) {
        Presence p = new Presence(Presence.Type.subscribed);
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().convertIDToJID(username));
        msnSession.getTransport().sendPacket(p);
    }

    /**
     * The user's login has completed and was accepted.
     */
    public void loginAccepted() {
        Log.debug("MSN login accepted");
        Presence p = new Presence();
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().getJID());
        msnSession.getTransport().sendPacket(p);

        //try {
        //    msnSession.getManager().synchronizeContactList();
        //}
        //catch (MSNException e) {
        //    Log.error("Unable to sync MSN contact list:", e);
        //}

        syncUsers();
    }

    /**
     * The user's login failed.
     */
    public void loginError() {
        // TODO: Handle this nicely
    }

    /**
     * Syncs up the jabber contact list with the MSN one.
     */
    public void syncUsers() {
        //List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        //ContactList contactList = msnSession.getManager().getContactList();
        //for (Contact c : contactList.getContacts()) {
        //    Log.debug("I found a contact for MSN!");
        //    //ArrayList groups = c.getGroups();
        //    //legacyusers.add(new TransportBuddy(c.getUserName(), c.getFriendlyName(), groups.get(0).toString()));
        //    legacyusers.add(new TransportBuddy(c.getUserName(), c.getFriendlyName(), "MSN Transport"));
       // }
       // try {
        //    msnSession.getTransport().syncLegacyRoster(msnSession.getJID(), legacyusers);
        //}
        //catch (UserNotFoundException e) {
         //   Log.error("Unable to sync MSN contact list for " + msnSession.getJID());
        //}
    }

}
