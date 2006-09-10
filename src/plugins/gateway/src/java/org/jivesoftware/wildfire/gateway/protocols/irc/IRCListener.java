/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.irc;

import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.IRCModeParser;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import java.util.Date;

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
     * Session this listener is associated with.
     */
    IRCSession session;

    /**
     * Retrieves the session this listener is associated with.
     */
    public IRCSession getSession() {
        return session;
    }

    public void onRegistered() {
        getSession().getRegistration().setLastLogin(new Date());
        Presence p = new Presence();
        p.setFrom(getSession().getTransport().getJID());
        p.setTo(getSession().getJID());
        getSession().getTransport().sendPacket(p);
    }

    public void onDisconnected() {
        getSession().logOut();
    }

    public void onError(String string) {
    }

    public void onError(int i, String string) {
    }

    public void onInvite(String string, IRCUser ircUser, String string1) {
    }

    public void onJoin(String string, IRCUser ircUser) {
    }

    public void onKick(String string, IRCUser ircUser, String string1, String string2) {
    }

    public void onMode(String string, IRCUser ircUser, IRCModeParser ircModeParser) {
    }

    public void onMode(IRCUser ircUser, String string, String string1) {
    }

    public void onNick(IRCUser ircUser, String string) {
    }

    public void onNotice(String string, IRCUser ircUser, String string1) {
    }

    public void onPart(String string, IRCUser ircUser, String string1) {
    }

    public void onPing(String string) {
    }

    public void onPrivmsg(String chan, IRCUser ircUser, String msg) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setFrom(getSession().getTransport().convertIDToJID(ircUser.getNick()));
        m.setTo(getSession().getJIDWithHighestPriority());
        m.setBody(msg);
        getSession().getTransport().sendPacket(m);
    }

    public void onQuit(IRCUser ircUser, String string) {
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().getJID());
        getSession().getTransport().sendPacket(p);        
    }

    public void onReply(int i, String string, String string1) {
    }

    public void onTopic(String string, IRCUser ircUser, String string1) {
    }

    public void unknown(String string, String string1, String string2, String string3) {
    }

}
