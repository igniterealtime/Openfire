/*
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches MUC events. The following events are supported:
 * <ul>
 * <li><b>occupantJoined</b> --&gt; Someone joined a room.</li>
 * <li><b>occupantLeft</b> --&gt; Someone left a room.</li>
 * <li><b>nicknameChanged</b> --&gt; A nickname was changed in a room.</li>
 * <li><b>messageReceived</b> --&gt; A message was received in a room.</li>
 * <li><b>roomCreated</b> --&gt; A room was created.</li>
 * <li><b>roomDestroyed</b> --&gt; A room was destroyed.</li>
 * </ul>
 * Use {@link #addListener(MUCEventListener)} and {@link #removeListener(MUCEventListener)}
 * to add or remove {@link MUCEventListener}.
 *
 * @author Daniel Henninger
 */
public class MUCEventDispatcher {
    private static final Logger Log = LoggerFactory.getLogger(MUCEventDispatcher.class);

    private static Collection<MUCEventListener> listeners =
            new ConcurrentLinkedQueue<>();

    public static void addListener(MUCEventListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(MUCEventListener listener) {
        listeners.remove(listener);
    }

    public static void occupantJoined(JID roomJID, JID user, String nickname) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.occupantJoined(roomJID, user, nickname);  
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'occupantJoined' event!", e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void occupantLeft(JID roomJID, JID user, String nickname) {
        for (MUCEventListener listener : listeners) {
            try {
                // We call both two and three argument methods to support
                // older API clients
                listener.occupantLeft(roomJID, user);
                listener.occupantLeft(roomJID, user, nickname);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'occupantLeft' event!", e);
            } 
        }
    }

    public static void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.nicknameChanged(roomJID, user, oldNickname, newNickname);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'nicknameChanged' event!", e);
            }
        }
    }

    public static void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.messageReceived(roomJID, user, nickname, message); 
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'messageReceived' event!", e);
            }
        }
    }

    public static void privateMessageRecieved(JID toJID, JID fromJID, Message message) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.privateMessageRecieved(toJID, fromJID, message);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'privateMessageRecieved' event!", e);
            }  
        }
    }

    public static void roomCreated(JID roomJID) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.roomCreated(roomJID);   
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'roomCreated' event!", e);
            }
        }
    }

    public static void roomDestroyed(JID roomJID) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.roomDestroyed(roomJID);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'roomDestroyed' event!", e);
            }
        }
    }

    public static void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        for (MUCEventListener listener : listeners) {
            try {
                listener.roomSubjectChanged(roomJID, user, newSubject); 
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'roomSubjectChanged' event!", e);
            }
        }
    }

}
