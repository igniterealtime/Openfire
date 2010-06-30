/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    public static void privateMessageRecieved(JID toJID, JID fromJID, Message message) {
        for (MUCEventListener listener : listeners) {
            listener.privateMessageRecieved(toJID, fromJID, message);
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

    public static void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        for (MUCEventListener listener : listeners) {
            listener.roomSubjectChanged(roomJID, user, newSubject);
        }
    }

}
