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

package org.jivesoftware.xmpp.workgroup.disco;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Iterator;
import java.util.List;

/**
 * This class is responsible for handling all the packets sent to the workgroup service with
 * namespace http://jabber.org/protocol/disco#items. If the disco packet was sent to the workgroup
 * itself then for each hosted workgroup will be returned an item. But if the packet was sent to
 * a workgroup then answer an error since it's not allowed to discover the items of a workgroup.
 *
 * @author Gaston Dombiak
 */
public class IQDiscoItemsHandler {

    private WorkgroupManager workgroupManager;
    private AdHocCommandManager commandManager;

    public IQDiscoItemsHandler(WorkgroupManager workgroupManager, AdHocCommandManager commandManager) {
        this.workgroupManager = workgroupManager;
        this.commandManager = commandManager;
    }

    public IQ handleIQ(IQ packet) {
        if (packet.getType() == IQ.Type.result) {
            List<Element> items = packet.getChildElement().elements("item");
            // Send a disco#info to each discovered item
            for (Element item : items) {
                String jid = item.attributeValue("jid");

                IQ disco = new IQ(IQ.Type.get);
                disco.setTo(jid);
                disco.setFrom(packet.getTo());
                disco.setChildElement("query", "http://jabber.org/protocol/disco#info");
                workgroupManager.send(disco);
            }
            return null;
        }

        // Create a copy of the sent pack that will be used as the reply
        // we only need to add the requested info to the reply if any, otherwise add
        // a not found error
        IQ reply = IQ.createResultIQ(packet);

        if (IQ.Type.set == packet.getType()) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.bad_request);
            return reply;
        }

        // Check if the disco#items was sent to the workgroup service itself
        if (workgroupManager.getAddress().equals(packet.getTo())) {
            Element iq = packet.getChildElement();
            String node = iq.attributeValue("node");
            reply.setChildElement(iq.createCopy());
            Element queryElement = reply.getChildElement();
            if (node == null) {
                // Add the hosted workgroups to the reply
                for (Workgroup workgroup : workgroupManager.getWorkgroups()) {
                    Element item = queryElement.addElement("item");
                    item.addAttribute("jid", workgroup.getJID().toString());
                    item.addAttribute("name", workgroup.getJID().getNode());
                }
            }
            else if ("http://jabber.org/protocol/commands".equals(node)) {
                for (AdHocCommand command : commandManager.getCommands()) {
                    // Only include commands that the sender can invoke (i.e. has enough permissions)
                    if (command.hasPermission(packet.getFrom())) {
                        Element item = queryElement.addElement("item");
                        item.addAttribute("jid", workgroupManager.getAddress().toString());
                        item.addAttribute("node", command.getCode());
                        item.addAttribute("name", command.getLabel());
                    }
                }
            }
            else {
                // Unknown node. Service not available
                reply.setError(PacketError.Condition.service_unavailable);
            }
        }
        else {
            // Answer an error if the user is trying to discover items of a workgroup
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.not_acceptable);
        }
        return reply;
    }
}
