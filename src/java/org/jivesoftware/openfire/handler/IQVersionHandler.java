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

package org.jivesoftware.openfire.handler;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Collections;
import java.util.Iterator;

/**
 * Implements the TYPE_IQ jabber:iq:version protocol (version info). Allows
 * XMPP entities to query each other's application versions.  The server
 * will respond with its current version info.
 *
 * @author Iain Shigeoka
 */
public class IQVersionHandler extends IQHandler implements ServerFeaturesProvider {

    private static Element bodyElement;
    private IQHandlerInfo info;

    public IQVersionHandler() {
        super("XMPP Server Version Handler");
        info = new IQHandlerInfo("query", "jabber:iq:version");
        if (bodyElement == null) {
            bodyElement = DocumentHelper.createElement(QName.get("query", "jabber:iq:version"));
            bodyElement.addElement("name").setText(AdminConsole.getAppName());
            bodyElement.addElement("version").setText(AdminConsole.getVersionString());
        }
    }

    @Override
    public IQ handleIQ(IQ packet) throws PacketException {
        if (IQ.Type.get == packet.getType()) {
            // Could cache this information for every server we see
            Element answerElement = bodyElement.createCopy();
            try {
                // Try to retrieve this for every request - security settings
                // might be changed runtime!
                final String os = System.getProperty("os.name") + ' ' 
                        + System.getProperty("os.version") + " ("
                        + System.getProperty("os.arch") + ')';
                final String java = "Java " + System.getProperty("java.version");
                answerElement.addElement("os").setText(os + " - " + java);
            } catch (SecurityException ex) {
                // Security settings don't allow the OS to be read. We'll honor
                // this and simply not report it.
            }
            IQ result = IQ.createResultIQ(packet);
            result.setChildElement(answerElement);
            return result;
        } else if (IQ.Type.set == packet.getType()) {
            // Answer an not-acceptable error since IQ should be of type GET
            IQ result = IQ.createResultIQ(packet);
            result.setError(PacketError.Condition.not_acceptable);
            return result;
        }
        // Ignore any other type of packet
        return null;
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton("jabber:iq:version").iterator();
    }
}
