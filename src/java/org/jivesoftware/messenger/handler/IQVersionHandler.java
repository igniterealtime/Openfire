/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.PacketException;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.admin.AdminConsole;

import java.util.ArrayList;
import java.util.Iterator;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.IQ;

/**
 * Implements the TYPE_IQ jabber:iq:version protocol (version info). Allows
 * XMPP entities to query each other's application versions.  The server
 * will respond with its current version info.
 *
 * @author Iain Shigeoka
 */
public class IQVersionHandler extends IQHandler implements ServerFeaturesProvider {

    private static Element bodyElement;
    private static Element versionElement;
    private IQHandlerInfo info;

    public IQVersionHandler() {
        super("XMPP Server Version Handler");
        info = new IQHandlerInfo("query", "jabber:iq:version");
        if (bodyElement == null) {
            bodyElement = DocumentHelper.createElement(QName.get("query", "jabber:iq:version"));
            bodyElement.addElement("name").setText(AdminConsole.getAppName());
            bodyElement.addElement("os").setText("Java 5");
            versionElement = bodyElement.addElement("version");
        }
    }

    public IQ handleIQ(IQ packet) throws PacketException {
        // Could cache this information for every server we see
        bodyElement.element("name").setText(AdminConsole.getAppName());
        versionElement.setText(AdminConsole.getVersionString());
        IQ result = null;
        result = IQ.createResultIQ(packet);
        bodyElement.setParent(null);
        result.setChildElement(bodyElement.createCopy());
        return result;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:version");
        return features.iterator();
    }
}