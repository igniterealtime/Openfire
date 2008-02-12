/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.group.GroupProvider;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.xmpp.packet.JID;

import java.util.Collection;

/**
 * @author Daniel Henninger
 */
public class ClearspaceGroupProvider implements GroupProvider {
    public Group createGroup(String name) throws UnsupportedOperationException, GroupAlreadyExistsException {
        throw new UnsupportedOperationException("Test");
    }

    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        return null;
    }

    public void setName(String oldName, String newName) throws UnsupportedOperationException, GroupAlreadyExistsException {
        throw new UnsupportedOperationException("Test");
    }

    public void setDescription(String name, String description) throws GroupNotFoundException {
        throw new UnsupportedOperationException("Test");
    }

    public int getGroupCount() {
        return 0;
    }

    public Collection<String> getGroupNames() {
        return null;
    }

    public Collection<String> getGroupNames(int startIndex, int numResults) {
        return null;
    }

    public Collection<String> getGroupNames(JID user) {
        return null;
    }

    public void addMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public void updateMember(String groupName, JID user, boolean administrator) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public void deleteMember(String groupName, JID user) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Test");
    }

    public boolean isReadOnly() {
        return true;
    }

    public Collection<String> search(String query) {
        return null;
    }

    public Collection<String> search(String query, int startIndex, int numResults) {
        return null;
    }

    public boolean isSearchSupported() {
        return false;
    }
}
