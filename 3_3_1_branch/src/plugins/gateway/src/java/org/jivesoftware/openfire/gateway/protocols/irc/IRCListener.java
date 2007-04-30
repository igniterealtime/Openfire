/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.irc;

import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUtil;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.JID;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.PresenceType;
import org.jivesoftware.openfire.gateway.TransportLoginStatus;

import java.util.*;

/**
 * Handles listening for IRC events.
 *
 * @author Daniel Henninger
 */
public class IRCListener implements IRCEventListener {

    public IRCListener(IRCSession session) {
        this.session = session;
    }

    /**
     * Timer to check for online status.
     */
    private Timer timer = new Timer();

    /**
     * Interval at which status is checked.
     */
    private int timerInterval = 60000; // 1 minute

    /**
     * Session this listener is associated with.
     */
    IRCSession session;

    /**
     * Effectively enables or disables the listener functionality, without burying it yet.
     */
    Boolean silenced = false;

    /**
     * Status checker.
     */
    StatusCheck statusCheck;

    /**
     * Retrieves the session this listener is associated with.
     *
     * @return The session the listener is associated with.
     */
    public IRCSession getSession() {
        return session;
    }

    public void onRegistered() {
        Log.debug("IRC registered");
        getSession().getRegistration().setLastLogin(new Date());
        Presence p = new Presence();
        p.setFrom(getSession().getTransport().getJID());
        p.setTo(getSession().getJID());
        getSession().getTransport().sendPacket(p);
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
        String buddyList = "";
        for (String buddy : getSession().getBuddyStatuses().keySet()) {
            buddyList = buddyList + " " + buddy;
        }
        if (!buddyList.equals("")) {
            getSession().getConnection().doIson(buddyList);
        }
        statusCheck = new StatusCheck();
        timer.schedule(statusCheck, timerInterval, timerInterval);
    }

    public void onDisconnected() {
        Log.debug("IRC disconnected");
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().getJID());
        getSession().getTransport().sendPacket(p);
        getSession().getConnection().close();
        timer.cancel();
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    public void onError(String string) {
        Log.debug("IRC error: "+string);
        if (silenced) { return; }
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().getJID(),
                LocaleUtils.getLocalizedString("gateway.irc.errorreceived", "gateway")+" "+string,
                Message.Type.error
        );
    }

    public void onError(int i, String string) {
        Log.debug("IRC error: "+i+", "+string);
        if (silenced) { return; }
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().getJID(),
                LocaleUtils.getLocalizedString("gateway.irc.errorreceivedwithcode", "gateway", Arrays.asList(Integer.toString(i)))+" "+string,
                Message.Type.error
        );
    }

    public void onInvite(String string, IRCUser ircUser, String string1) {
        Log.debug("IRC invite: "+string+", "+ircUser+", "+string1);
    }

    public void onJoin(String string, IRCUser ircUser) {
        Log.debug("IRC join: "+string+", "+ircUser);
    }

    public void onKick(String string, IRCUser ircUser, String string1, String string2) {
        Log.debug("IRC kick: "+string+", "+ircUser+", "+string1+", "+string2);
    }

    public void onMode(String string, IRCUser ircUser, IRCModeParser ircModeParser) {
        Log.debug("IRC mode: "+string+", "+ircUser+", "+ircModeParser);
    }

    public void onMode(IRCUser ircUser, String string, String string1) {
        Log.debug("IRC mode: "+ircUser+", "+string+", "+string1);
    }

    public void onNick(IRCUser ircUser, String string) {
        Log.debug("IRC nick: "+ircUser+", "+string);
    }

    public void onNotice(String string, IRCUser ircUser, String string1) {
        Log.debug("IRC notice: "+string+", "+ircUser+", "+string1);
        if (silenced) { return; }
        String username = ircUser.getNick();
        if (username == null) {
            username = ircUser.getUsername();
        }
        JID from;
        if (username == null) {
            from = getSession().getTransport().getJID();
        }
        else {
            from = getSession().getTransport().convertIDToJID(username);
        }
        getSession().getTransport().sendMessage(
                getSession().getJIDWithHighestPriority(),
                from,
                string1
        );
    }

    public void onPart(String string, IRCUser ircUser, String string1) {
        Log.debug("IRC part: "+string+", "+ircUser+", "+string1);
    }

    public void onPing(String string) {
        // Nothing to do, handled automatically.
    }

    public void onPrivmsg(String chan, IRCUser ircUser, String msg) {
        Log.debug("IRC privmsg: "+chan+", "+ircUser+", "+msg);
        if (silenced) { return; }
        if (msg.equals("VERSION")) {
            // This is actually a CTCP VERSION request.  Why is it showing as a Privmsg?
            // TODO: Should figure out a proper way to handle this.
            //getSession().getConnection().send("CTCP REPLY "+ircUser.getNick()+" VERSION IM Gateway Plugin for Openfire");
            return;
        }
        getSession().getTransport().sendMessage(
                getSession().getJIDWithHighestPriority(),
                getSession().getTransport().convertIDToJID(ircUser.getNick()),
                msg
        );
    }

    public void onQuit(IRCUser ircUser, String string) {
        Log.debug("IRC quit: "+ircUser+", "+string);
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().getJID());
        getSession().getTransport().sendPacket(p);
        getSession().getConnection().close();
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    public void onReply(int i, String string, String string1) {
        Log.debug("IRC reply: "+i+", "+string+", "+string1);
        if (silenced) { return; }
        if (i == IRCUtil.RPL_ISON) {
            String[] onlineContacts = string1.split(" ");
            ArrayList<String> onlineContactList = new ArrayList<String>();
            // Lets see who all is on
            for (String contact : onlineContacts) {
                onlineContactList.add(contact);
                getSession().setBuddyStatus(contact, PresenceType.available);
            }
            // Now lets compare with who all is not on
            for (String contact : getSession().getBuddyStatuses().keySet()) {
                if (!onlineContactList.contains(contact)) {
                    getSession().setBuddyStatus(contact, PresenceType.unavailable);
                }
            }
        }
        else {
            getSession().getTransport().sendMessage(
                    getSession().getJIDWithHighestPriority(),
                    getSession().getTransport().getJID(),
                    string1
            );
        }
    }

    public void onTopic(String string, IRCUser ircUser, String string1) {
        Log.debug("IRC topic: "+string+", "+ircUser+", "+string1);
    }

    public void unknown(String string, String string1, String string2, String string3) {
        Log.debug("Unknown IRC message: "+string+", "+string1+", "+string2+", "+string3);
    }

    public void setSilenced(Boolean setting) {
        silenced = setting;
    }

    private class StatusCheck extends TimerTask {
        /**
         * Send ISON to IRC to check on status of contacts.
         */
        public void run() {
            String buddyList = "";
            for (String buddy : getSession().getBuddyStatuses().keySet()) {
                buddyList = buddyList + " " + buddy;
            }
            if (!buddyList.equals("")) {
                getSession().getConnection().doIson(buddyList);
            }
        }
    }

}
