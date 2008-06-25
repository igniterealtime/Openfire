/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.clearspace;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.XMPPServer;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;
import static org.jivesoftware.openfire.clearspace.WSUtils.parseStringArray;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupProvider;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * @author Daniel Henninger
 */
public class ClearspaceGroupProvider implements GroupProvider {
    protected static final String URL_PREFIX = "socialGroupService/";

    private static final String TYPE_ID_OWNER = "0";
    private static final String TYPE_ID_MEMBER = "1";

    public ClearspaceGroupProvider() {
    }

    public Group createGroup(String name) throws UnsupportedOperationException, GroupAlreadyExistsException {
        throw new UnsupportedOperationException("Could not create groups.");
    }

    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Could not delete groups.");
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        return translateGroup(getGroupByName(name));
    }

    public void setName(String oldName, String newName) throws UnsupportedOperationException, GroupAlreadyExistsException {
        throw new UnsupportedOperationException("Could not modify groups.");
    }

    public void setDescription(String name, String description) throws GroupNotFoundException {
        throw new UnsupportedOperationException("Could not modify groups.");
    }

    public int getGroupCount() {
        try {
            String path = URL_PREFIX + "socialGroupCount";
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);
            return Integer.valueOf(getReturn(element));
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public Collection<String> getGroupNames() {
        try {
            String path = URL_PREFIX + "socialGroupNames";
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);

            return parseStringArray(element);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public Collection<String> getGroupNames(int startIndex, int numResults) {
        try {
            String path = URL_PREFIX + "socialGroupNamesBounded/" + startIndex + "/" + numResults;
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);

            return parseStringArray(element);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public Collection<String> getGroupNames(JID user) {
        try {
            long userID = ClearspaceManager.getInstance().getUserID(user);
            String path = URL_PREFIX + "userSocialGroupNames/" + userID;
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);

            return parseStringArray(element);
        } catch (UserNotFoundException e) {
            throw new UnsupportedOperationException("User not found", e);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void addMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Could not modify groups.");
    }

    public void updateMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Could not modify groups.");
    }

    public void deleteMember(String groupName, JID user) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Could not modify groups.");
    }

    public boolean isReadOnly() {
        return true;
    }

    public Collection<String> search(String query) {
        throw new UnsupportedOperationException("Group search is not supported");
    }

    public Collection<String> search(String query, int startIndex, int numResults) {
        throw new UnsupportedOperationException("Group search is not supported");
    }

    public boolean isSearchSupported() {
        return false;
    }

    /**
     * Translate a XML respose of a group to a <code>Group</code>.
     *
     * @param responseNode the XML representation of a CS group.
     * @return the group that corresponds to the XML.
     */
    private Group translateGroup(Element responseNode) {

        Node groupNode = responseNode.selectSingleNode("return");

        // Gets the CS DISPLAY NAME that is OF NAME
        String name = groupNode.selectSingleNode("displayName").getText();

        // Gets the CS NAME that is OF DISPLAY NAME
        String displayName = groupNode.selectSingleNode("name").getText();

        // Gets the group ID
        long id = Long.parseLong(groupNode.selectSingleNode("ID").getText());

        // Gets the group type
        int type = Integer.parseInt(groupNode.selectSingleNode("typeID").getText());

        // Gets the group description if it exist
        String description = null;
        Node tmpNode = groupNode.selectSingleNode("description");
        if (tmpNode != null) {
            description = tmpNode.getText();
        }

        // Get the members and administrators
        Collection<JID> members = new ArrayList<JID>();
        Collection<JID> administrators = new ArrayList<JID>();
        try {
            XMPPServer server = XMPPServer.getInstance();

            // Gets the JID from the response
            List<Element> membersElement = (List<Element>) getGroupMembers(id).elements("return");
            for (Element memberElement : membersElement) {

                String username = memberElement.element("user").element("username").getText();
                // Escape username to accept usernames with @ or spaces
                String escapedUsername = JID.escapeNode(username);

                String typeID = memberElement.element("typeID").getText();

                if (TYPE_ID_OWNER.equals(typeID)) {
                    administrators.add(server.createJID(escapedUsername, null));
                } else if (TYPE_ID_MEMBER.equals(typeID)) {
                    members.add(server.createJID(escapedUsername, null));
                } else {
                    // nothing to do, waiting for approval
                }
            }
        } catch (GroupNotFoundException e) {
            // this won't happen, the group exists.
        }

        Map<String, String> properties = new HashMap<String, String>();

        // Type 0 is OPEN
        if (type == 0) {
            properties.put("sharedRoster.showInRoster", "everybody");
        } else {
            // Types 1, 2 or 3 are MEMBER_ONLY, PRIVATE, SECRET
            properties.put("sharedRoster.showInRoster", "onlyGroup");
        }

        properties.put("sharedRoster.displayName", displayName);
        properties.put("sharedRoster.groupList", "");

        // Creates the group
        // There are some interesting things happening here.
        // If this is the first time that this group is loaded from CS, the OF will save this properties.
        // If this is not the first time and these properties haven't changed, then nothing happens
        // If this is not the first time but these properties have changed, then OF will update it's saved data.
        // And this is OK, event if this "getGroup" is to be used in a "change group properties event", the group should
        // always show the last information.
        return new Group(name, description, members, administrators, properties);
    }

    /**
     * Returns a group by its name.
     *
     * @param name the name of the group to retrive.
     * @return the group.                                                   
     * @throws GroupNotFoundException if a group with that name doesn't exist or there is a problem getting it.
     */
    private Element getGroupByName(String name) throws GroupNotFoundException {
        try {
            String path = URL_PREFIX + "socialGroupsByName/" + name;

            return ClearspaceManager.getInstance().executeRequest(GET, path);
        } catch (Exception e) {
            // It is not supported exception, wrap it into a GroupNotFoundException
            throw new GroupNotFoundException("Unexpected error", e);
        }
    }

    /**
     * Returns the all the members of the group. It continas the onwers and the members of the group.
     *
     * @param groupID the group id to return the members of.
     * @return all the members of the group.
     * @throws GroupNotFoundException if the groups doesn't exist or there is a problem getting the members.
     */
    private Element getGroupMembers(long groupID) throws GroupNotFoundException {
        try {
            // Gets the members and administrators
            String path = URL_PREFIX + "members/" + groupID;
            return ClearspaceManager.getInstance().executeRequest(GET, path);
        } catch (Exception e) {
            // It is not supported exception, wrap it into a GroupNotFoundException
            throw new GroupNotFoundException("Unexpected error", e);
        }
    }
}
