/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.xmpp.workgroup.chatbot.Chatbot;
import org.jivesoftware.xmpp.workgroup.chatbot.ChatbotSession;
import org.xmpp.packet.Message;

/**
 * <p>The Workgroup's message handler processes all incoming message packets sent to the workgroup.</p>
 * <p/>
 * <p>Currently the workgroup recognises:</p>
 * <ul>
 * <li>No message packets (all are silently dropped)</li>
 * </ul>
 *
 * @author Derek DeMoro
 */
public class MessageHandler {
    private Workgroup workgroup;


    public MessageHandler(Workgroup workgroup) {
        this.workgroup = workgroup;
    }


    public void process(Message packet) {
        if (packet.getBody() == null) {
            // TODO Handle statistics reported by the agents????
            // ignore this packet
            return;
        }
        // Get the chatbot of the workgroup. It is not mandatory for workgroups to have a chatbot
        // so if no chatbot was defined for the workgroup then do nothing
        Chatbot bot = workgroup.getChatBot();
        if (bot != null) {
            // Get the chatbot session of the user (create one if necessary)
            ChatbotSession session = bot.getSession(packet.getFrom(), true);
            // Let the bot process the received message
            bot.onMessage(session, packet);
        }
    }
}
