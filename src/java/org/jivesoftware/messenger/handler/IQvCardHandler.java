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

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Implements the TYPE_IQ vcard-temp protocol. Clients
 * use this protocol to set and retrieve the vCard information
 * associated with someone's account.
 * <p/>
 * A 'get' query retrieves the vcard for the addressee.
 * A 'set' query sets the vcard information for the sender's account.
 * <p/>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 * <p/>
 * <h2>Warning</h2>
 * I have noticed incompatibility between vCard XML used by Exodus and Psi.
 * There is a new vCard standard going through the JSF JEP process. We might
 * want to start either standardizing on clients (probably the most practical),
 * sending notices for non-conformance (useful),
 * or attempting to translate between client versions (not likely).
 *
 * @author Iain Shigeoka
 */
public class IQvCardHandler extends IQHandler {

    private IQHandlerInfo info;
    private UserManager userManager;

    public IQvCardHandler() {
        super("XMPP vCard Handler");
        info = new IQHandlerInfo("vCard", "vcard-temp");
    }

    public synchronized IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        IQ result = null;
        try {
            JID recipient = packet.getTo();
            IQ.Type type = packet.getType();
            if (type.equals(IQ.Type.set)) {
                User user = userManager.getUser(packet.getFrom().getNode());
                // Proper format
                Element vcard = packet.getChildElement();
                if (vcard != null) {
                    List nameStack = new ArrayList(5);
                    readVCard(vcard, nameStack, user);
                }
                result = IQ.createResultIQ(packet);
            }
            else if (type.equals(IQ.Type.get)) {
                User user = userManager.getUser(recipient.getNode());
                result = IQ.createResultIQ(packet);

                Element vcard = DocumentHelper.createElement(QName.get("vCard", "vcard-temp"));
                result.setChildElement(vcard);

                Iterator names = user.getVCardPropertyNames();
                while (names.hasNext()) {
                    String name = (String)names.next();
                    String path = name.replace(':', '/');
                    Element node = DocumentHelper.makeElement(vcard, path);
                    node.setText(user.getVCardProperty(name));
                }
            }
            else {
                result = IQ.createResultIQ(packet);
                result.setError(PacketError.Condition.not_acceptable);
            }
        }
        catch (UserNotFoundException e) {
            result = IQ.createResultIQ(packet);
                result.setError(PacketError.Condition.item_not_found);
        }
        return result;
    }

    /**
     * We need to build names from heirarchical position in the DOM tree.
     *
     * @param element   The element to interrogate for text nodes
     * @param nameStack The current name of the vcard property (list as a stack)
     * @param user      the user getting their vcard set
     */
    private void readVCard(Element element, List nameStack, User user) throws UnauthorizedException {
        Iterator children = element.elementIterator();
        while (children.hasNext()) {
            Element child = (Element)children.next();
            nameStack.add(child.getName());
            String value = child.getTextTrim();
            if (value != null) {
                if (!"".equals(value)) {
                    user.setVCardProperty(createName(nameStack), value);
                }
            }
            readVCard(child, nameStack, user);
            nameStack.remove(nameStack.size() - 1);
        }
    }

    /**
     * Generate a name for the given name stack values
     *
     * @param nameStack
     * @return The name concatenating the values with the ':' character
     */
    private String createName(List nameStack) {
        StringBuffer buf = new StringBuffer();
        Iterator iter = nameStack.iterator();
        while (iter.hasNext()) {
            if (buf.length() > 0) {
                buf.append(':');
            }
            buf.append(iter.next());
        }
        return buf.toString();
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        userManager = server.getUserManager();
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
