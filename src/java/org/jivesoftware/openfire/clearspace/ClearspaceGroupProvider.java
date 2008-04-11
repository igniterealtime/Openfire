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

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.XMPPServer;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.*;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;
import static org.jivesoftware.openfire.clearspace.WSUtils.parseStringArray;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupProvider;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Daniel Henninger
 */
public class ClearspaceGroupProvider implements GroupProvider {
    protected static final String URL_PREFIX = "groupService/";

    private Boolean readOnly;

    public ClearspaceGroupProvider() {
    }

    public Group createGroup(String name) throws UnsupportedOperationException, GroupAlreadyExistsException {
        // Check if this operation is supported
        if (isReadOnly()) {
            throw new UnsupportedOperationException("Could not create groups.");
        }

        try {

            String path = URL_PREFIX + "groups";

            // Creates the XML with the data
            Document groupDoc = DocumentHelper.createDocument();
            Element rootE = groupDoc.addElement("createGroup");
            Element nameE = rootE.addElement("name");
            nameE.addText(name);
            rootE.addElement("description");

            Element group = ClearspaceManager.getInstance().executeRequest(POST, path, groupDoc.asXML());

            return translateGroup(group);

        } catch (GroupAlreadyExistsException gaee) {
            throw gaee;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void deleteGroup(String name) throws UnsupportedOperationException {
        // Check if this operation is supported
        if (isReadOnly()) {
            throw new UnsupportedOperationException("Could not delete groups.");
        }

        try {
            long groupID = ClearspaceManager.getInstance().getGroupID(name);
            String path = URL_PREFIX + "groups/" + groupID;
            ClearspaceManager.getInstance().executeRequest(DELETE, path);

        } catch (GroupNotFoundException gnfe) {
            Log.error(gnfe);
            // it is ok, the group doesn't exist "anymore"
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        return translateGroup(getGroupByName(name));
    }

    public void setName(String oldName, String newName) throws UnsupportedOperationException, GroupAlreadyExistsException {
        try {
            Element group = getGroupByName(oldName);
            WSUtils.modifyElementText(group, "name", newName);

            String path = URL_PREFIX + "groups";
            ClearspaceManager.getInstance().executeRequest(PUT, path);

        } catch (GroupNotFoundException gnfe) {
            Log.error(gnfe);
            // no further action required
        } catch (Exception e) {
            // It is not supported exception, wrap it into a UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }

    }

    public void setDescription(String name, String description) throws GroupNotFoundException {
        try {
            Element group = getGroupByName(name);
            WSUtils.modifyElementText(group, "description", description);

            String path = URL_PREFIX + "groups";
            ClearspaceManager.getInstance().executeRequest(PUT, path);

        } catch (GroupNotFoundException gnfe) {
            Log.error(gnfe);
            // no further action required
        } catch (Exception e) {
            // It is not supported exception, wrap it into a UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public int getGroupCount() {
        try {
            String path = URL_PREFIX + "groupCount";
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);
            return Integer.valueOf(getReturn(element));
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public Collection<String> getGroupNames() {
        try {
            String path = URL_PREFIX + "groupNames";
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);

            return parseStringArray(element);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public Collection<String> getGroupNames(int startIndex, int numResults) {
        try {
            String path = URL_PREFIX + "groupNamesBounded/" + startIndex + "/" + numResults;
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
            String path = URL_PREFIX + "userGroupNames/" + userID;
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
        try {
            long userID = ClearspaceManager.getInstance().getUserID(user);
            long groupID = ClearspaceManager.getInstance().getGroupID(groupName);

            String path = URL_PREFIX;

            Document groupDoc = DocumentHelper.createDocument();
            Element rootE;
            if (administrator) {
                rootE = groupDoc.addElement("addAdministratorToGroup");
                path += "groupAdmins";
            } else {
                rootE = groupDoc.addElement("addMemberToGroup");
                path += "groupMembers";
            }
            Element nameE = rootE.addElement("userID");
            nameE.addText(String.valueOf(userID));
            rootE.addElement("groupID");
            nameE.addText(String.valueOf(groupID));

            ClearspaceManager.getInstance().executeRequest(POST, path, groupDoc.asXML());


        } catch (GroupNotFoundException e) {
            throw new UnsupportedOperationException("Group not found", e);
        } catch (UserNotFoundException e) {
            throw new UnsupportedOperationException("User not found", e);
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void updateMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException {
        deleteMember(groupName, user);
        addMember(groupName, user, administrator);
    }

    public void deleteMember(String groupName, JID user) throws UnsupportedOperationException {

        long userID;
        long groupID;
        try {
            userID = ClearspaceManager.getInstance().getUserID(user);
            groupID = ClearspaceManager.getInstance().getGroupID(groupName);

        } catch (GroupNotFoundException e) {
            // It's ok, that not existing group doesn't contains that memeber, :)
            return;
        } catch (UserNotFoundException e) {
            // It's ok, that group doesn't contains that not existing memeber, :)
            return;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }

        //Another try catch because it is going to remove it two times, one for admin and one
        //for user. Therefore one of them could throw an exception.
        try {
            String path = URL_PREFIX + "groupAdmins/" + groupID + "/" + userID;
            ClearspaceManager.getInstance().executeRequest(DELETE, path);

            path = URL_PREFIX + "groupMembers/" + groupID + "/" + userID;
            ClearspaceManager.getInstance().executeRequest(DELETE, path);
        } catch (GroupNotFoundException e) {
            //won't happend, the group exist
        } catch (UserNotFoundException e) {
            //won't happend, the user exist
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }

    }

    public boolean isReadOnly() {
        if (readOnly == null) {
            loadReadOnly();
        }
        // If it is null returns the most restrictive anwser.
        return (readOnly == null ? false : readOnly);
    }

    private void loadReadOnly() {
        try {
            // See if the is read only
            String path = URL_PREFIX + "isReadOnly";
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);
            readOnly = Boolean.valueOf(getReturn(element));
        } catch (Exception e) {
            // if there is a problem, keep it null, maybe in the next call succes.
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

    private Group translateGroup(Element responseNode) {

        Node groupNode = responseNode.selectSingleNode("return");

        // Get the name, description and id of the group
        String name = null;
        String description = null;
        long id = -1;

        // Gets the group name
        name = groupNode.selectSingleNode("name").getText();

        // Gets the group ID
        id = Long.parseLong(groupNode.selectSingleNode("ID").getText());

        // Gets the group description if it exist
        Node tmpNode = groupNode.selectSingleNode("description");
        if (tmpNode != null) {
            description = tmpNode.getText();
        }

        // Get the members and administrators
        Collection<JID> members = null;
        Collection<JID> administrators = null;
        try {
            members = getGroupMembers(id, false);
            administrators = getGroupMembers(id, true);
        } catch (GroupNotFoundException e) {
            // this won't happen, the group exists.
        }

        // Creates the group
        return new Group(name, description, members, administrators);
    }

    private Element getGroupByName(String name) throws GroupNotFoundException {
        try {
            String path = URL_PREFIX + "groups/" + URLEncoder.encode(name, "UTF-8");

            return ClearspaceManager.getInstance().executeRequest(GET, path);
        } catch (GroupNotFoundException gnfe) {
            // It is a supported exception, throw it again
            throw gnfe;
        } catch (Exception e) {
            // It is not supported exception, wrap it into a GroupNotFoundException
            throw new GroupNotFoundException("Unexpected error", e);
        }
    }

    private Collection<JID> getGroupMembers(long groupID, boolean admin) throws GroupNotFoundException {
        try {
            XMPPServer server = XMPPServer.getInstance();
            Collection<JID> members = new ArrayList<JID>();

            // Gets the members or administrators
            String path = null;
            if (admin) {
                path = URL_PREFIX + "groupAdmins/" + groupID;
            } else {
                path = URL_PREFIX + "groupMembers/" + groupID;
            }
            Element element = ClearspaceManager.getInstance().executeRequest(GET, path);

            // Gets the JID from the response
            List<Node> users = (List<Node>) element.selectNodes("return");
            for (Node user : users) {
                String username = user.selectSingleNode("username").getText();
                members.add(server.createJID(username, null));

            }
            return members;
        } catch (GroupNotFoundException gnfe) {
            // It is a supported exception, throw it again
            throw gnfe;
        } catch (Exception e) {
            // It is not supported exception, wrap it into a GroupNotFoundException
            throw new GroupNotFoundException("Unexpected error", e);
        }
    }
}
