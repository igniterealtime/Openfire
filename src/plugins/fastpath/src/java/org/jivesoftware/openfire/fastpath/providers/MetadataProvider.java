/*
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.providers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

/**
 * MetadataProvider is a generic data handler to retrieve name-value pairs
 * based from properties files in a given location.  To register your own
 * properties files, you can use the Server Properties feature in the Live Assistant
 * server and register based on the following naming: config.file0 = location_of_file.
 */
public class MetadataProvider implements WorkgroupProvider {

	private static final Logger Log = LoggerFactory.getLogger(MetadataProvider.class);
	
    /**
     * Returns true if the IQ packet name equals "generic-metadata".
     *
     * @param packet the IQ GET packet sent to the server. Get packets are sent the
     *               server when a users wishes to retrieve server information.
     * @return true if it should be handled my MetadataProvider.
     */
    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "generic-metadata".equals(name);
    }

    /**
     * Executed if #handleGet returned true. This is responsible for sending
     * information back to the client.
     *
     * @param packet    the IQ GET packet sent to the server.
     * @param workgroup the Workgroup the packet was sent to.
     */
    public void executeGet(IQ packet, Workgroup workgroup) {

        // Create the generic reply packet.
        IQ reply = IQ.createResultIQ(packet);

        // Check that the sender of this IQ is an agent. If so
        // we throw an item not found exception.
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        try {
            workgroupManager.getAgentManager().getAgent(packet.getFrom());
        }
        catch (AgentNotFoundException e) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        // If the sender of the packet is an agent, send back name-value pairs
        // of all properties files specified.
        final Map<String, String> map = new HashMap<String, String>();
        final List<String> configFiles = JiveGlobals.getProperties("config");
        for (String fileName : configFiles) {
            File file = new File(fileName);
            if (file.exists()) {
                // load properties file.
                Properties props = new Properties();
                try {
                    props.load(new FileInputStream(file));
                    Enumeration<?> properties = props.propertyNames();
                    while (properties.hasMoreElements()) {
                        String key = (String)properties.nextElement();
                        String value = props.getProperty(key);
                        map.put(key, value);
                    }
                }
                catch (IOException e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }

        //  It is VERY IMPORTANT that you use the same name and namespace as the sending packet. Otherwise,
        //  it would never be mapped correctly.
        final Element genericSetting = reply.setChildElement("generic-metadata",
                "http://jivesoftware.com/protocol/workgroup");

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Create a name-value element
            Element element = genericSetting.addElement("entry");
            element.addElement("name").setText(key);
            element.addElement("value").setText(value);
        }

        // Send back new packet with requested information.
        workgroup.send(reply);
    }

    /**
     * Would handle any IQ SET packets if #handleSet returned true.
     *
     * @param packet    the IQ SET Packet.
     * @param workgroup the Workgroup the packet was sent to.
     */
    public void executeSet(IQ packet, Workgroup workgroup) {

    }

    /**
     * Would return true if we wished to handle setting of information from client to server.
     *
     * @param packet the IQ SET Packet.
     * @return true if we wish to handle this packet.
     */
    public boolean handleSet(IQ packet) {
        return false;
    }


}

