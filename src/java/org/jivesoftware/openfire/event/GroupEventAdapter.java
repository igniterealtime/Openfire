package org.jivesoftware.openfire.event;

import java.util.Map;

import org.jivesoftware.openfire.group.Group;

/**
 * An abstract adapter class for receiving group events. 
 * The methods in this class are empty. This class exists as convenience for creating listener objects.
 */
public class GroupEventAdapter implements GroupEventListener {

    @Override
    public void groupCreated(Group group, Map params) {
    }

    @Override
    public void groupDeleting(Group group, Map params) {
    }

    @Override
    public void groupModified(Group group, Map params) {
    }

    @Override
    public void memberAdded(Group group, Map params) {
    }

    @Override
    public void memberRemoved(Group group, Map params) {
    }

    @Override
    public void adminAdded(Group group, Map params) {
    }

    @Override
    public void adminRemoved(Group group, Map params) {
    }

}
