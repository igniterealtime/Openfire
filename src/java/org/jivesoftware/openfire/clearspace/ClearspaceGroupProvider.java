/**
 * $Revision$
 * $Date$
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
package org.jivesoftware.openfire.clearspace;

import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;
import static org.jivesoftware.openfire.clearspace.WSUtils.parseStringArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.AbstractReadOnlyGroupProvider;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupCollection;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;

/**
 * @author Daniel Henninger
 */
public class ClearspaceGroupProvider extends AbstractReadOnlyGroupProvider {
    protected static final String URL_PREFIX = "socialGroupService/";

    private static final String TYPE_ID_OWNER = "0";
    private static final String TYPE_ID_MEMBER = "1";

    public ClearspaceGroupProvider() {
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        return translateGroup(getGroupByName(name));
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

    public boolean isSharingSupported() {
		return true;
	}

    public Collection<String> getSharedGroupNames() {
        // Return all social group names since every social group is a shared group
        return getGroupNames();
    }

	public Collection<String> getSharedGroupNames(JID user) {
		// TODO: is there a better way to get the shared Clearspace groups for a given user?
		Collection<String> result = new ArrayList<String>();
		Iterator<Group> sharedGroups = new GroupCollection(getGroupNames()).iterator();
		while (sharedGroups.hasNext()) {
			Group group = sharedGroups.next();
			if (group.isUser(user)) {
				result.add(group.getName());
			}
		}
		return result;
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
            // Encode potentially non-ASCII characters
            name = URLUTF8Encoder.encode(name);
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
