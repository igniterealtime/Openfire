/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.muc;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dispatches MUC events. The following events are supported:
 * <ul>
 * <li><b>occupantJoined</b> --> Someone joined a room.</li>
 * <li><b>occupantLeft</b> --> Someone left a room.</li>
 * <li><b>nicknameChanged</b> --> A nickname was changed in a room.</li>
 * <li><b>messageReceived</b> --> A message was received in a room.</li>
 * <li><b>roomCreated</b> --> A room was created.</li>
 * <li><b>roomDestryod</b> --> A room was destroyed.</li>
 * </ul>
 * Use {@link #addListener(MUCEventListener)} and {@link #removeListener(MUCEventListener)}
 * to add or remove {@link MUCEventListener}.
 *
 * @author Daniel Henninger
 */
public class MUCEventDispatcher {

    private static Collection<MUCEventListener> listeners =
            new ConcurrentLinkedQueue<MUCEventListener>();

    public static void addListener(MUCEventListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(MUCEventListener listener) {
        listeners.remove(listener);
    }

    public static void occupantJoined(JID roomJID, JID user, String nickname) {
        for (MUCEventListener listener : listeners) {
            listener.occupantJoined(roomJID, user, nickname);
        }
    }

    public static void occupantLeft(JID roomJID, JID user) {
        for (MUCEventListener listener : listeners) {
            listener.occupantLeft(roomJID, user);
        }
    }

    public static void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        for (MUCEventListener listener : listeners) {
            listener.nicknameChanged(roomJID, user, oldNickname, newNickname);
        }
    }

    public static void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        for (MUCEventListener listener : listeners) {
            listener.messageReceived(roomJID, user, nickname, message);
        }
    }

    public static void roomCreated(JID roomJID) {
        for (MUCEventListener listener : listeners) {
            listener.roomCreated(roomJID);
        }
    }

    public static void roomDestroyed(JID roomJID) {
        for (MUCEventListener listener : listeners) {
            listener.roomDestroyed(roomJID);
        }
    }

}
