/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
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

    public IQvCardHandler() {
        super("XMPP vCard Handler");
        info = new IQHandlerInfo("vcard", "vcard-temp");
    }

    public synchronized IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        IQ result = null;
        try {
            XMPPAddress recipient = packet.getRecipient();
            XMPPPacket.Type type = packet.getType();
            if (type.equals(IQ.SET)) {
                User user = userManager.getUser(packet.getOriginatingSession().getUserID());
                // Proper format
                Element vcard = ((XMPPDOMFragment)packet.getChildFragment()).getRootElement();
                if (vcard != null) {
                    List nameStack = new ArrayList(5);
                    readVCard(vcard, nameStack, user);
                }
                result = packet.createResult();
            }
            else if (type.equals(IQ.GET)) {
                User user = userManager.getUser(recipient.getName());
                result = packet.createResult();

                XMPPDOMFragment frag = new XMPPDOMFragment();
                result.setChildFragment(frag);

                Element vcard = frag.getRootElement().addElement("VCARD", "vcard-temp");
                Iterator names = user.getVCardPropertyNames();
                while (names.hasNext()) {
                    String name = (String)names.next();
                    String path = name.replace(':', '/');
                    Element node = DocumentHelper.makeElement(vcard, path);
                    node.setText(user.getVCardProperty(name));
                }
            }
            else {
                result = packet.createResult();
                result.setError(XMPPError.Code.NOT_ACCEPTABLE);
            }
        }
        catch (UserNotFoundException e) {
            result = packet.createResult();
            result.setError(XMPPError.Code.NOT_FOUND);
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

    public UserManager userManager;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
