/**
 * $RCSfile$
 * $Revision: 684 $
 * $Date: 2004-12-11 23:30:40 -0300 (Sat, 11 Dec 2004) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
            bodyElement.addElement("os").setText("Java " + System.getProperty("java.version"));
            bodyElement.addElement("version");
        }
    }

    public IQ handleIQ(IQ packet) throws PacketException {
        if (IQ.Type.get == packet.getType()) {
            // Could cache this information for every server we see
            Element answerElement = bodyElement.createCopy();
            answerElement.element("name").setText(AdminConsole.getAppName());
            answerElement.element("version").setText(AdminConsole.getVersionString());
            IQ result = IQ.createResultIQ(packet);
            result.setChildElement(answerElement);
            return result;
        }
        else if (IQ.Type.set == packet.getType()) {
            // Answer an not-acceptable error since IQ should be of type GET
            IQ result = IQ.createResultIQ(packet);
            result.setError(PacketError.Condition.not_acceptable);
            return result;
        }
        // Ignore any other type of packet
        return null;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator<String> getFeatures() {
        List<String> features = new ArrayList<String>();
        features.add("jabber:iq:version");
        return features.iterator();
    }
}