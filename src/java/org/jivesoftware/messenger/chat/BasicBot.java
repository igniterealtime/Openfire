/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chat;

import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * A basic bot user for easy creation of chatbots. This bot is invisible
 * and has no backing user.
 *
 * @author Iain Shigeoka
 * @author Derek DeMoro
 */
class BasicBot implements ChatRole {

    /**
     * The chatroom this bot belongs to.
     */
    private ChatRoom chatRoom;
    /**
     * The current presence of this bot.
     */
    private Presence presence;

    /**
     * Create a chatbot for the given chat chatRoom.
     *
     * @param chatRoom the chatroom the bot monitors
     * @throws UnauthorizedException if the bot doesn't have permission to enter the chatroom
     */
    public BasicBot(ChatRoom chatRoom) throws UnauthorizedException {
        this.chatRoom = chatRoom;
        presence = this.chatRoom.createPresence(Presence.STATUS_INVISIBLE);
    }

    public Presence getPresence() throws UnauthorizedException {
        return presence;
    }

    public void setPresence(Presence newPresence) throws UnauthorizedException {
        this.presence = newPresence;
    }

    public void setRole(int newRole) throws UnauthorizedException {
        throw new UnauthorizedException("This bot is read-only");
    }

    public int getRole() throws UnauthorizedException {
        return ChatRole.OBSERVER;
    }

    public String getNickname() throws UnauthorizedException {
        return "BasicBot";
    }

    public void kick() throws UnauthorizedException {
        throw new UnauthorizedException("This bot can't be kicked");
    }

    public ChatUser getChatUser() {
        ChatUser user = null;
        try {
            user = chatRoom.getRole().getChatUser();
        }
        catch (UnauthorizedException e) {
            Log.error(e);
        }

        return user;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public XMPPAddress getRoleAddress() {
        XMPPAddress addr = null;
        try {
            addr = chatRoom.getRole().getRoleAddress();
        }
        catch (UnauthorizedException e) {
            Log.error(e);
        }
        return addr;
    }

    public void send(Message packet) throws UnauthorizedException {
    }

    public void send(Presence packet) throws UnauthorizedException {
    }

    public void send(IQ packet) throws UnauthorizedException {
    }

    public String toString() {
        return "Chat Bot in chatRoom " + chatRoom.getName();
    }
}
