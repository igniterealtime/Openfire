/**
 * $Revision: 19030 $
 * $Date: 2005-06-13 09:14:13 -0700 (Mon, 13 Jun 2005) $
 *
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
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * This class is responsible for handling all the packets sent to the workgroup service with
 * namespace http://jabber.org/protocol/disco#info. If the disco packet was sent to the workgroup
 * itself then the identity of the service will be returned and its features. On the other hand, if
 * the packet was sent to a workgroup then the status of the workgroup will be returned in a data
 * form.
 *
 * @author Gaston Dombiak
 */
public class IQDiscoInfoHandler {

    private WorkgroupManager workgroupManager;
    private AdHocCommandManager commandManager;

    private Collection<DiscoFeaturesProvider> featuresProviders =
            new java.util.concurrent.CopyOnWriteArrayList<DiscoFeaturesProvider>();

    public IQDiscoInfoHandler(WorkgroupManager workgroupManager, AdHocCommandManager commandManager) {
        this.workgroupManager = workgroupManager;
        this.commandManager = commandManager;
    }

    /**
     * Adds a new features provider to the list of providers. The new features provider will be
     * used whenever a disco for information is made against the workgroup service.
     *
     * @param provider the DiscoFeaturesProvider that provides service features.
     */
    public void addServerFeaturesProvider(DiscoFeaturesProvider provider) {
        featuresProviders.add(provider);
    }

    /**
     * Removes a features provider from the list of providers.
     *
     * @param provider the DiscoFeaturesProvider to remove.
     */
    public void removeServerFeaturesProvider(DiscoFeaturesProvider provider) {
        featuresProviders.remove(provider);
    }

    public IQ handleIQ(IQ packet) {
        if (packet.getType() == IQ.Type.result) {
            List features = packet.getChildElement().elements("feature");
            // Detect if this item is the MUC service
            for (Iterator it=features.iterator(); it.hasNext(); ) {
                Element feature = (Element) it.next();
                String variable = feature.attributeValue("var");

                if ("http://jabber.org/protocol/muc".equals(variable)) {
                    workgroupManager.setMUCServiceName(packet.getFrom().getDomain());
                }
            }
            return null;
        }

        // Create a copy of the sent pack that will be used as the reply
        // we only need to add the requested info to the reply if any, otherwise add
        // a not found error
        IQ reply = IQ.createResultIQ(packet);

        // Check if the disco#info was sent to the workgroup service itself
        if (workgroupManager.getAddress().equals(packet.getTo())) {
            Element iq = packet.getChildElement();
            String node = iq.attributeValue("node");
            reply.setChildElement(iq.createCopy());
            Element queryElement = reply.getChildElement();

            if (node == null) {
                // Create and add a the identity of the workgroup service
                Element identity = queryElement.addElement("identity");
                identity.addAttribute("category", "collaboration");
                // TODO Get the name from a property
                identity.addAttribute("name", "Fastpath");
                identity.addAttribute("type", "workgroup");

                // Create and add a the feature provided by the workgroup service
                Element feature = queryElement.addElement("feature");
                feature.addAttribute("var", "http://jabber.org/protocol/workgroup");
                // Create and add a the disco#info feature
                feature = queryElement.addElement("feature");
                feature.addAttribute("var", "http://jabber.org/protocol/disco#info");
                // Indicate that we can provide information about the software version being used
                feature = queryElement.addElement("feature");
                feature.addAttribute("var", "jabber:iq:version");
                // Indicate that we support ad-hoc commands
                feature = queryElement.addElement("feature");
                feature.addAttribute("var", "http://jabber.org/protocol/commands");

                // Add the features provided by the features providers
                for (DiscoFeaturesProvider provider : featuresProviders) {
                    for (String newFeature : provider.getFeatures()) {
                        feature = queryElement.addElement("feature");
                        feature.addAttribute("var", newFeature);
                    }
                }
            }
            else if ("http://jabber.org/protocol/commands".equals(node)) {
                // Create and add a the identity of the workgroup service
                Element identity = queryElement.addElement("identity");
                identity.addAttribute("category", "collaboration");
                // TODO Get the name from a property
                identity.addAttribute("name", "Fastpath");
                identity.addAttribute("type", "workgroup");

                // Create and add a the disco#info feature
                Element feature = queryElement.addElement("feature");
                feature.addAttribute("var", "http://jabber.org/protocol/disco#info");
                // Indicate that we support ad-hoc commands
                feature = queryElement.addElement("feature");
                feature.addAttribute("var", "http://jabber.org/protocol/commands");
            }
            else {
                // Check if the node matches a supported command
                boolean found = false;
                for (AdHocCommand command : commandManager.getCommands()) {
                    if (node.equals(command.getCode())) {
                        found = true;
                        // Only include commands that the sender can invoke (i.e. has enough permissions)
                        if (command.hasPermission(packet.getFrom())) {
                            // Create and add a the identity of the command
                            Element identity = queryElement.addElement("identity");
                            identity.addAttribute("category", "automation");
                            identity.addAttribute("name", command.getLabel());
                            identity.addAttribute("type", "command-node");

                            // Indicate that we support ad-hoc commands
                            Element feature = queryElement.addElement("feature");
                            feature.addAttribute("var", "http://jabber.org/protocol/commands");
                        }
                        else {
                            // Return Forbidden error
                            reply.setError(PacketError.Condition.forbidden);
                        }
                    }
                }
                if (!found) {
                    // Return item_not_found error
                    reply.setError(PacketError.Condition.item_not_found);
                }
            }

        }
        else {
            // Check if the disco#info was sent to a given workgroup
            try {
                Workgroup workgroup = workgroupManager.getWorkgroup(packet.getTo());
                Element iq = packet.getChildElement();
                reply.setChildElement(iq.createCopy());
                Element queryElement = reply.getChildElement();

                // Create and add a the identity of the workgroup service
                Element identity = queryElement.addElement("identity");
                identity.addAttribute("category", "collaboration");
                identity.addAttribute("name", workgroup.getJID().getNode());
                identity.addAttribute("type", "workgroup");

                // Create and add a the disco#info feature
                Element feature = queryElement.addElement("feature");
                feature.addAttribute("var", "http://jabber.org/protocol/disco#info");

                Element form = queryElement.addElement("x", "jabber:x:data");
                form.addAttribute("type", "result");
                // Add static field
                Element field = form.addElement("field");
                field.addAttribute("var", "FORM_TYPE");
                field.addAttribute("type", "hidden");
                field.addElement("value").setText("http://jabber.org/protocol/workgroup#workgroupinfo");
                // Add workgroup description
                field = form.addElement("field");
                field.addAttribute("var", "workgroup#description");
                field.addAttribute("label", "Description");
                field.addElement("value").setText(workgroup.getDescription() == null ?
                        "" : workgroup.getDescription());
                // Add workgroup online status
                field = form.addElement("field");
                field.addAttribute("var", "workgroup#online");
                field.addAttribute("label", "Status");
                field.addElement("value").setText(workgroup.getStatus().name());
            }
            catch (UserNotFoundException e) {
                // If we didn't find a workgroup then answer a not found error
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.item_not_found);
            }
        }
        return reply;
    }
}
