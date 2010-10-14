/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.privacy.PrivacyListProvider;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * IQPrivacyHandler is responsible for handling privacy lists.
 *
 * @author Gaston Dombiak
 */
public class IQPrivacyHandler extends IQHandler
        implements ServerFeaturesProvider, UserEventListener {

    private IQHandlerInfo info;
    private PrivacyListManager manager = PrivacyListManager.getInstance();
    private PrivacyListProvider provider = new PrivacyListProvider();

    public IQPrivacyHandler() {
        super("Blocking Communication Handler");
        info = new IQHandlerInfo("query", "jabber:iq:privacy");
    }

    @Override
	public IQ handleIQ(IQ packet) throws UnauthorizedException {
        IQ.Type type = packet.getType();
        JID from = packet.getFrom();
        if (from.getNode() == null || !UserManager.getInstance().isRegisteredUser(from.getNode())) {
            // Service is unavailable for anonymous users
            IQ result = IQ.createResultIQ(packet);
            result.setChildElement(packet.getChildElement().createCopy());
            result.setError(PacketError.Condition.service_unavailable);
            return result;
        }
        IQ result = null;
        if (type.equals(IQ.Type.get)) {
            // User wants to retrieve a privacy list or the list of privacy list
            Element child = packet.getChildElement();
            List elements = child.elements();
            if (elements.isEmpty()) {
                // User requested names of privacy lists
                result = getPrivacyListsNames(packet, from);
            }
            else {
                // User requested a privacy list
                result = getPrivacyList(packet, from);
            }
        }
        else if (type.equals(IQ.Type.set)) {
            Element child = packet.getChildElement();
            Element activeList = child.element("active");
            Element defaultList = child.element("default");
            if (activeList != null) {
                // Active list handling
                String listName = activeList.attributeValue("name");
                if (listName != null) {
                    // User wants to set or change the active list currently being applied by
                    // the server to this session
                    result = setActiveList(packet, from, listName);
                }
                else {
                    // User wants to decline the use of any active list for this session
                    result = declineActiveList(packet, from);

                }
            }
            else if (defaultList != null) {
                // Default list handling
                String listName = defaultList.attributeValue("name");
                if (listName != null) {
                    // User wants to set or change its default list (i.e. which applies
                    // to the user as a whole, not only the sending resource)
                    result = setDefaultList(packet, from, listName);
                }
                else {
                    // User wants to decline the use of a default list
                    result = declineDefaultList(packet, from);
                }
            }
            else {
                // Privacy list handling (create/edit/delete)
                Element list = child.element("list");
                String listName = list.attributeValue("name");
                List items = list.elements();
                if (!items.isEmpty()) {
                    // User wants to create or edit a privacy list
                    result = updateOrCreateList(packet, from, list);
                }
                else {
                    // User wants to delete a privacy list
                    result = deleteList(packet, from, listName);

                }
            }
        }
        return result;
    }

    /**
     * Returns the IQ packet containing the active and default lists and the lists
     * defined by the user.
     *
     * @param packet IQ packet requesting the lists.
     * @param from sender of the IQ packet.
     * @return the IQ packet containing the active and default lists and the lists
     *         defined by the user.
     */
    private IQ getPrivacyListsNames(IQ packet, JID from) {
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);
        Map<String, Boolean> privacyLists = provider.getPrivacyLists(from.getNode());
        // Add the default list
        for (String listName : privacyLists.keySet()) {
            if (privacyLists.get(listName)) {
                childElement.addElement("default").addAttribute("name", listName);
            }
        }
        // Add the active list (only if there is an active list for the session)
        ClientSession session = sessionManager.getSession(from);
        if  (session != null && session.getActiveList() != null) {
            childElement.addElement("active")
                    .addAttribute("name", session.getActiveList().getName());
        }

        // Add a list element for each privacy list
        for (String listName : privacyLists.keySet()) {
            childElement.addElement("list").addAttribute("name", listName);
        }
        return result;
    }

    /**
     * Returns the IQ packet containing the details of the specified list. If no list
     * was found or the IQ request contains more than one specified list then an error will
     * be returned.
     *
     * @param packet IQ packet requesting a given list.
     * @param from sender of the IQ packet.
     * @return the IQ packet containing the details of the specified list.
     */
    private IQ getPrivacyList(IQ packet, JID from) {
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);

        // Check that only one list was requested
        List<Element> lists = childElement.elements("list");
        if (lists.size() > 1) {
            result.setError(PacketError.Condition.bad_request);
        }
        else {
            String listName = lists.get(0).attributeValue("name");
            PrivacyList list = null;
            if (listName != null) {
                // A list name was specified so get it
                list = manager.getPrivacyList(from.getNode(), listName);
            }
            if (list != null) {
                // Add the privacy list to the result
                childElement = result.setChildElement("query", "jabber:iq:privacy");
                childElement.add(list.asElement());
            }
            else {
                // List not found
                result.setError(PacketError.Condition.item_not_found);
            }
        }
        return result;
    }

    /**
     * User has specified a new active list that should be used for the current session.
     *
     * @param packet IQ packet setting new active list for the current session.
     * @param from sender of the IQ packet.
     * @param listName name of the new active list for the current session.
     * @return acknowledge of success.
     */
    private IQ setActiveList(IQ packet, JID from, String listName) {
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);

        // Get the list
        PrivacyList list = manager.getPrivacyList(from.getNode(), listName);
        if (list != null) {
            // Get the user session
            ClientSession session = sessionManager.getSession(from);
            if (session != null) {
                // Set the new active list for this session
                session.setActiveList(list);
            }
        }
        else {
            // List not found
            result.setError(PacketError.Condition.item_not_found);
        }
        return result;
    }

    /**
     * User has requested that no active list should be used for the current session. Return
     * acknowledge of success.
     *
     * @param packet IQ packet declining active list for the current session.
     * @param from sender of the IQ packet.
     * @return acknowledge of success.
     */
    private IQ declineActiveList(IQ packet, JID from) {
        // Get the user session
        ClientSession session = sessionManager.getSession(from);
        // Set that there is no active list for this session
        session.setActiveList(null);
        // Return acknowledge of success
        return IQ.createResultIQ(packet);
    }

    /**
     * User has specified a new default list that should be used for all session.
     *
     * @param packet IQ packet setting new default list for all sessions.
     * @param from sender of the IQ packet.
     * @param listName name of the new default list for all sessions.
     * @return acknowledge of success.
     */
    private IQ setDefaultList(IQ packet, JID from, String listName) {
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);

        if (sessionManager.getSessionCount(from.getNode()) > 1) {
            // Current default list is being used by more than one session
            result.setError(PacketError.Condition.conflict);
        }
        else {
            // Get the list
            PrivacyList list = manager.getPrivacyList(from.getNode(), listName);
            if (list != null) {
                // Get the user session
                ClientSession session = sessionManager.getSession(from);
                PrivacyList oldDefaultList = session.getDefaultList();
                manager.changeDefaultList(from.getNode(), list, oldDefaultList);
                // Set the new default list for this session (the only existing session)
                session.setDefaultList(list);
            }
            else {
                // List not found
                result.setError(PacketError.Condition.item_not_found);
            }
        }
        return result;
    }

    /**
     * User has specified that there is no default list that should be used for this user.
     *
     * @param packet IQ packet declining default list for all sessions.
     * @param from sender of the IQ packet.
     * @return acknowledge of success.
     */
    private IQ declineDefaultList(IQ packet, JID from) {
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);

        if (sessionManager.getSessionCount(from.getNode()) > 1) {
            // Current default list is being used by more than one session
            result.setError(PacketError.Condition.conflict);
        }
        else {
            // Get the user session
            ClientSession session = sessionManager.getSession(from);
            // Check if a default list was already defined
            if (session.getDefaultList() != null) {
                // Set the existing default list as non-default
                session.getDefaultList().setDefaultList(false);
                // Update the database with the new list state
                provider.updatePrivacyList(from.getNode(), session.getDefaultList());
                session.setDefaultList(null);
            }
        }
        return result;
    }

    /**
     * Updates an existing privacy list or creates a new one with the specified items list. The
     * new list will not become the active or default list by default. The user will have to
     * send another packet to set the new list as active or default.<p>
     *
     * Once the list was updated or created a "privacy list push" will be sent to all
     * connected resources of the user.
     *
     * @param packet IQ packet updating or creating a new privacy list.
     * @param from sender of the IQ packet.
     * @param listElement the element containing the list and its items.
     * @return acknowledge of success.
     */
    private IQ updateOrCreateList(IQ packet, JID from, Element listElement) {
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);

        String listName = listElement.attributeValue("name");
        PrivacyList list = manager.getPrivacyList(from.getNode(), listName);
        if (list == null) {
            list = manager.createPrivacyList(from.getNode(), listName, listElement);
        }
        else {
            // Update existing list
            list.updateList(listElement);
            provider.updatePrivacyList(from.getNode(), list);
            // Make sure that existing user sessions that are using the updated list are poining
            // to the updated instance. This may happen since PrivacyListManager uses a Cache that
            // may expire so it's possible to have many instances representing the same privacy
            // list. Therefore, if a list is modified then we need to make sure that all
            // instances are replaced with the updated instance. An OR Mapping Tool would have
            // avoided this issue since identity is ensured.
            for (ClientSession session : sessionManager.getSessions(from.getNode())) {
                if (list.equals(session.getDefaultList())) {
                    session.setDefaultList(list);
                }
                if (list.equals(session.getActiveList())) {
                    session.setActiveList(list);
                }
            }
        }
        // Send a "privacy list push" to all connected resources
        IQ pushPacket = new IQ(IQ.Type.set);
        Element child = pushPacket.setChildElement("query", "jabber:iq:privacy");
        child.addElement("list").addAttribute("name", list.getName());
        sessionManager.userBroadcast(from.getNode(), pushPacket);

        return result;
    }

    private IQ deleteList(IQ packet, JID from, String listName) {
        ClientSession currentSession;
        IQ result = IQ.createResultIQ(packet);
        Element childElement = packet.getChildElement().createCopy();
        result.setChildElement(childElement);
        // Get the list to delete
        PrivacyList list = manager.getPrivacyList(from.getNode(), listName);

        if (list == null) {
            // List to delete was not found
            result.setError(PacketError.Condition.item_not_found);
            return result;
        }
        else {
            currentSession = sessionManager.getSession(from);
            // Check if the list is being used by another session
            for (ClientSession session : sessionManager.getSessions(from.getNode())) {
                if (currentSession == session) {
                    // Ignore the active session for this checking
                    continue;
                }
                if (list.equals(session.getDefaultList()) || list.equals(session.getActiveList())) {
                    // List to delete is being used by another session so return a conflict error
                    result.setError(PacketError.Condition.conflict);
                    return result;
                }
            }
        }
        // Remove the list from the active session (if it was being used)
        if (list.equals(currentSession.getDefaultList())) {
            currentSession.setDefaultList(null);
        }
        if (list.equals(currentSession.getActiveList())) {
            currentSession.setActiveList(null);
        }
        manager.deletePrivacyList(from.getNode(), listName);
        return result;
    }

    @Override
	public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator<String> getFeatures() {
        ArrayList<String> features = new ArrayList<String>();
        features.add("jabber:iq:privacy");
        return features.iterator();
    }

    public void userCreated(User user, Map params) {
        //Do nothing
    }

    public void userDeleting(User user, Map params) {
        // Delete privacy lists owned by the user being deleted
        manager.deletePrivacyLists(user.getUsername());
    }

    public void userModified(User user, Map params) {
        //Do nothing
    }

    @Override
	public void initialize(XMPPServer server) {
        super.initialize(server);
    }
}
