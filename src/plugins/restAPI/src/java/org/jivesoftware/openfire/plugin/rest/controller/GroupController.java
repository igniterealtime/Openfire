package org.jivesoftware.openfire.plugin.rest.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.plugin.rest.entity.GroupEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.rest.utils.MUCRoomUtils;

/**
 * The Class GroupController.
 */
public class GroupController {
    /** The Constant INSTANCE. */
    public static final GroupController INSTANCE = new GroupController();

    /**
     * Gets the single instance of GroupController.
     *
     * @return single instance of GroupController
     */
    public static GroupController getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the groups.
     *
     * @return the groups
     * @throws ServiceException
     *             the service exception
     */
    public List<GroupEntity> getGroups() throws ServiceException {
        Collection<Group> groups = GroupManager.getInstance().getGroups();
        List<GroupEntity> groupEntities = new ArrayList<GroupEntity>();
        for (Group group : groups) {
            GroupEntity groupEntity = new GroupEntity(group.getName(), group.getDescription());
            groupEntities.add(groupEntity);
        }

        return groupEntities;
    }

    /**
     * Gets the group.
     *
     * @param groupName
     *            the group name
     * @return the group
     * @throws ServiceException
     *             the service exception
     */
    public GroupEntity getGroup(String groupName) throws ServiceException {
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(groupName);
        } catch (GroupNotFoundException e) {
            throw new ServiceException("Could not find group", groupName, ExceptionType.GROUP_NOT_FOUND,
                    Response.Status.NOT_FOUND, e);
        }

        GroupEntity groupEntity = new GroupEntity(group.getName(), group.getDescription());
        groupEntity.setAdmins(MUCRoomUtils.convertJIDsToStringList(group.getAdmins()));
        groupEntity.setMembers(MUCRoomUtils.convertJIDsToStringList(group.getMembers()));

        return groupEntity;
    }

    /**
     * Creates the group.
     *
     * @param groupEntity
     *            the group entity
     * @return the group
     * @throws ServiceException
     *             the service exception
     */
    public Group createGroup(GroupEntity groupEntity) throws ServiceException {
        Group group;
        if (groupEntity != null && !groupEntity.getName().isEmpty()) {
            try {
                group = GroupManager.getInstance().createGroup(groupEntity.getName());
                group.setDescription(groupEntity.getDescription());

                group.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
                group.getProperties().put("sharedRoster.displayName", groupEntity.getName());
                group.getProperties().put("sharedRoster.groupList", "");
            } catch (GroupAlreadyExistsException e) {
                throw new ServiceException("Could not create a group", groupEntity.getName(),
                        ExceptionType.GROUP_ALREADY_EXISTS, Response.Status.CONFLICT, e);
            }
        } else {
            throw new ServiceException("Could not create new group", "groups",
                    ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION, Response.Status.BAD_REQUEST);
        }
        return group;
    }

    /**
     * Update group.
     *
     * @param groupName the group name
     * @param groupEntity the group entity
     * @return the group
     * @throws ServiceException the service exception
     */
    public Group updateGroup(String groupName, GroupEntity groupEntity) throws ServiceException {
        Group group;
        if (groupEntity != null && !groupEntity.getName().isEmpty()) {
            if (groupName.equals(groupEntity.getName())) {
                try {
                    group = GroupManager.getInstance().getGroup(groupName);
                    group.setDescription(groupEntity.getDescription());
                } catch (GroupNotFoundException e) {
                    throw new ServiceException("Could not find group", groupName, ExceptionType.GROUP_NOT_FOUND,
                            Response.Status.NOT_FOUND, e);
                }
            } else {
                throw new ServiceException(
                        "Could not update the group. The group name is different to the payload group name.", groupName + " != " + groupEntity.getName(),
                        ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION, Response.Status.BAD_REQUEST);
            }
        } else {
            throw new ServiceException("Could not update new group", "groups",
                    ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION, Response.Status.BAD_REQUEST);
        }
        return group;
    }

    /**
     * Delete group.
     *
     * @param groupName
     *            the group name
     * @throws ServiceException
     *             the service exception
     */
    public void deleteGroup(String groupName) throws ServiceException {
        try {
            Group group = GroupManager.getInstance().getGroup(groupName);
            GroupManager.getInstance().deleteGroup(group);
        } catch (GroupNotFoundException e) {
            throw new ServiceException("Could not find group", groupName, ExceptionType.GROUP_NOT_FOUND,
                    Response.Status.NOT_FOUND, e);
        }
    }
}
