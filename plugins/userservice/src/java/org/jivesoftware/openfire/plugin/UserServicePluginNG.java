package org.jivesoftware.openfire.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.entity.RosterEntities;
import org.jivesoftware.openfire.entity.RosterItemEntity;
import org.jivesoftware.openfire.entity.UserEntities;
import org.jivesoftware.openfire.entity.UserEntity;
import org.jivesoftware.openfire.entity.UserGroupsEntity;
import org.jivesoftware.openfire.entity.UserProperty;
import org.jivesoftware.openfire.exceptions.ExceptionType;
import org.jivesoftware.openfire.exceptions.ServiceException;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.utils.UserUtils;
import org.xmpp.packet.JID;

/**
 * The Class UserServicePluginNG.
 */
public class UserServicePluginNG {
    /** The Constant INSTANCE. */
    public static final UserServicePluginNG INSTANCE = new UserServicePluginNG();

    /** The user manager. */
    private UserManager userManager;

    /** The roster manager. */
    private RosterManager rosterManager;

    /** The server. */
    private XMPPServer server;

    /**
     * Gets the single instance of UserServicePluginNG.
     *
     * @return single instance of UserServicePluginNG
     */
    public static UserServicePluginNG getInstance() {
        return INSTANCE;
    }

    /**
     * Instantiates a new user service plugin ng.
     */
    private UserServicePluginNG() {
        server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        rosterManager = server.getRosterManager();
    }

    /**
     * Creates the user.
     *
     * @param userEntity
     *            the user entity
     * @throws ServiceException
     *             the service exception
     */
    public void createUser(UserEntity userEntity) throws ServiceException {
        if (userEntity != null && !userEntity.getUsername().isEmpty()) {
            if (userEntity.getPassword() == null) {
                throw new ServiceException("Could not create new user, because password is null",
                        userEntity.getUsername(), "PasswordIsNull", Response.Status.BAD_REQUEST);
            }
            try {
                userManager.createUser(userEntity.getUsername(), userEntity.getPassword(), userEntity.getName(),
                        userEntity.getEmail());
            } catch (UserAlreadyExistsException e) {
                throw new ServiceException("Could not create new user", userEntity.getUsername(),
                        ExceptionType.USER_ALREADY_EXISTS_EXCEPTION, Response.Status.BAD_REQUEST);
            }
            addProperties(userEntity);
        }
    }

    /**
     * Update user.
     *
     * @param username
     *            the username
     * @param userEntity
     *            the user entity
     * @throws ServiceException
     *             the service exception
     */
    public void updateUser(String username, UserEntity userEntity) throws ServiceException {
        if (userEntity != null && !username.isEmpty()) {
            User user = getAndCheckUser(username);
            if (userEntity.getPassword() != null) {
                user.setPassword(userEntity.getPassword());
            }
            if (userEntity.getName() != null) {
                user.setName(userEntity.getName());
            }
            if (userEntity.getEmail() != null) {
                user.setEmail(userEntity.getEmail());
            }

            addProperties(userEntity);
        }
    }

    /**
     * Delete user.
     *
     * @param username
     *            the username
     * @throws ServiceException
     *             the service exception
     */
    public void deleteUser(String username) throws ServiceException {
        User user = getAndCheckUser(username);
        userManager.deleteUser(user);

        rosterManager.deleteRoster(server.createJID(username, null));
    }

    /**
     * Gets the user entities.
     *
     * @param userSearch
     *            the user search
     * @return the user entities
     */
    public UserEntities getUserEntities(String userSearch) {
        UserEntities userEntities = new UserEntities();
        userEntities.setUsers(UserUtils.convertUsersToUserEntities(userManager.getUsers(), userSearch));
        return userEntities;
    }

    /**
     * Gets the user entity.
     *
     * @param username
     *            the username
     * @return the user entity
     * @throws ServiceException
     *             the service exception
     */
    public UserEntity getUserEntity(String username) throws ServiceException {
        return UserUtils.convertUserToUserEntity(getAndCheckUser(username));
    }

    /**
     * Enable user.
     *
     * @param username
     *            the username
     * @throws ServiceException
     *             the service exception
     */
    public void enableUser(String username) throws ServiceException {
        getAndCheckUser(username);
        LockOutManager.getInstance().enableAccount(username);
    }

    /**
     * Disable user.
     *
     * @param username
     *            the username
     * @throws ServiceException
     *             the service exception
     */
    public void disableUser(String username) throws ServiceException {
        getAndCheckUser(username);
        LockOutManager.getInstance().disableAccount(username, null, null);
    }

    /**
     * Gets the roster entities.
     *
     * @param username
     *            the username
     * @return the roster entities
     * @throws ServiceException
     *             the service exception
     */
    public RosterEntities getRosterEntities(String username) throws ServiceException {
        Roster roster = getUserRoster(username);

        List<RosterItemEntity> rosterEntities = new ArrayList<RosterItemEntity>();
        for (RosterItem rosterItem : roster.getRosterItems()) {
            RosterItemEntity rosterItemEntity = new RosterItemEntity(rosterItem.getJid().toBareJID(),
                    rosterItem.getNickname(), rosterItem.getSubStatus().getValue());
            rosterItemEntity.setGroups(rosterItem.getGroups());

            rosterEntities.add(rosterItemEntity);
        }

        return new RosterEntities(rosterEntities);
    }

    /**
     * Adds the roster item.
     *
     * @param username
     *            the username
     * @param rosterItemEntity
     *            the roster item entity
     * @throws ServiceException
     *             the service exception
     * @throws UserAlreadyExistsException
     *             the user already exists exception
     * @throws SharedGroupException
     *             the shared group exception
     * @throws UserNotFoundException
     *             the user not found exception
     */
    public void addRosterItem(String username, RosterItemEntity rosterItemEntity) throws ServiceException,
            UserAlreadyExistsException, SharedGroupException, UserNotFoundException {
        Roster roster = getUserRoster(username);
        if (rosterItemEntity.getJid() == null) {
            throw new ServiceException("JID is null", "JID", "IllegalArgumentException", Response.Status.BAD_REQUEST);
        }
        JID jid = new JID(rosterItemEntity.getJid());

        try {
            roster.getRosterItem(jid);
            throw new UserAlreadyExistsException(jid.toBareJID());
        } catch (UserNotFoundException e) {
            // Roster item does not exist. Try to add it.
        }

        if (roster != null) {
            RosterItem rosterItem = roster.createRosterItem(jid, rosterItemEntity.getNickname(),
                    rosterItemEntity.getGroups(), false, true);
            UserUtils.checkSubType(rosterItemEntity.getSubscriptionType());
            rosterItem.setSubStatus(RosterItem.SubType.getTypeFromInt(rosterItemEntity.getSubscriptionType()));
            roster.updateRosterItem(rosterItem);
        }
    }

    /**
     * Update roster item.
     *
     * @param username
     *            the username
     * @param rosterJid
     *            the roster jid
     * @param rosterItemEntity
     *            the roster item entity
     * @throws ServiceException
     *             the service exception
     * @throws UserNotFoundException
     *             the user not found exception
     * @throws UserAlreadyExistsException
     *             the user already exists exception
     * @throws SharedGroupException
     *             the shared group exception
     */
    public void updateRosterItem(String username, String rosterJid, RosterItemEntity rosterItemEntity)
            throws ServiceException, UserNotFoundException, UserAlreadyExistsException, SharedGroupException {
        getAndCheckUser(username);

        Roster roster = getUserRoster(username);
        JID jid = new JID(rosterJid);
        RosterItem rosterItem = roster.getRosterItem(jid);

        if (rosterItemEntity.getNickname() != null) {
            rosterItem.setNickname(rosterItemEntity.getNickname());
        }
        if (rosterItemEntity.getGroups() != null) {
            rosterItem.setGroups(rosterItemEntity.getGroups());
        }
        UserUtils.checkSubType(rosterItemEntity.getSubscriptionType());

        rosterItem.setSubStatus(RosterItem.SubType.getTypeFromInt(rosterItemEntity.getSubscriptionType()));
        roster.updateRosterItem(rosterItem);
    }

    /**
     * Delete roster item.
     *
     * @param username
     *            the username
     * @param rosterJid
     *            the roster jid
     * @throws SharedGroupException
     *             the shared group exception
     * @throws ServiceException
     *             the service exception
     */
    public void deleteRosterItem(String username, String rosterJid) throws SharedGroupException, ServiceException {
        getAndCheckUser(username);
        Roster roster = getUserRoster(username);
        JID jid = new JID(rosterJid);

        if (roster.deleteRosterItem(jid, true) == null) {
            throw new ServiceException("Roster Item could not deleted", jid.toBareJID(), "RosterItemNotFound",
                    Response.Status.NOT_FOUND);
        }
    }

    /**
     * Gets the user groups.
     *
     * @param username
     *            the username
     * @return the user groups
     * @throws ServiceException
     *             the service exception
     */
    public List<String> getUserGroups(String username) throws ServiceException {
        User user = getAndCheckUser(username);
        Collection<Group> groups = GroupManager.getInstance().getGroups(user);
        List<String> groupNames = new ArrayList<String>();
        for (Group group : groups) {
            groupNames.add(group.getName());
        }

        return groupNames;
    }

    /**
     * Adds the user to group.
     *
     * @param username
     *            the username
     * @param userGroupsEntity
     *            the user groups entity
     * @throws ServiceException
     * @throws GroupAlreadyExistsException
     *             the group already exists exception
     */
    public void addUserToGroups(String username, UserGroupsEntity userGroupsEntity) throws ServiceException {
        if (userGroupsEntity != null) {
            Collection<Group> groups = new ArrayList<Group>();

            for (String groupName : userGroupsEntity.getGroupNames()) {
                Group group = null;
                try {
                    group = GroupManager.getInstance().getGroup(groupName);
                } catch (GroupNotFoundException e) {
                    // Create this group
                    group = createGroup(groupName);
                }
                groups.add(group);
            }
            for (Group group : groups) {
                group.getMembers().add(server.createJID(username, null));
            }
        }
    }

    /**
     * Delete user from groups.
     *
     * @param username
     *            the username
     * @param userGroupsEntity
     *            the user groups entity
     * @throws ServiceException
     *             the service exception
     */
    public void deleteUserFromGroups(String username, UserGroupsEntity userGroupsEntity) throws ServiceException {
        if (userGroupsEntity != null) {
            for (String groupName : userGroupsEntity.getGroupNames()) {
                Group group = null;
                try {
                    group = GroupManager.getInstance().getGroup(groupName);
                } catch (GroupNotFoundException e) {
                    throw new ServiceException("Could not find group", groupName, ExceptionType.GROUP_NOT_FOUND,
                            Response.Status.NOT_FOUND, e);
                }
                group.getMembers().remove(server.createJID(username, null));
            }
        }
    }

    /**
     * Gets the user entities by property key and or value.
     *
     * @param propertyKey
     *            the property key
     * @param propertyValue
     *            the property value (can be null)
     * @return the user entities by property
     * @throws ServiceException
     *             the service exception
     */
    public UserEntities getUserEntitiesByProperty(String propertyKey, String propertyValue) throws ServiceException {
        List<String> usernames = PropertyDAO.getUsernameByProperty(propertyKey, propertyValue);
        List<UserEntity> users = new ArrayList<UserEntity>();
        UserEntities userEntities = new UserEntities();

        for (String username : usernames) {
            users.add(getUserEntity(username));
        }

        userEntities.setUsers(users);
        return userEntities;
    }

    /**
     * Adds the properties.
     *
     * @param userEntity
     *            the user entity
     * @throws ServiceException
     *             the service exception
     */
    private void addProperties(UserEntity userEntity) throws ServiceException {
        User user = getAndCheckUser(userEntity.getUsername());
        user.getProperties().clear();
        if (userEntity.getProperties() != null) {
            for (UserProperty property : userEntity.getProperties()) {
                user.getProperties().put(property.getKey(), property.getValue());
            }
        }
    }

    /**
     * Creates the group.
     *
     * @param groupName
     *            the group name
     * @return the group
     * @throws ServiceException
     * @throws GroupAlreadyExistsException
     *             the group already exists exception
     */
    private Group createGroup(String groupName) throws ServiceException {
        Group group = null;
        try {
            group = GroupManager.getInstance().createGroup(groupName);
            group.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
            group.getProperties().put("sharedRoster.displayName", groupName);
            group.getProperties().put("sharedRoster.groupList", "");
        } catch (GroupAlreadyExistsException e) {
            throw new ServiceException("Could not create group", groupName, ExceptionType.GROUP_ALREADY_EXISTS,
                    Response.Status.BAD_REQUEST, e);
        }
        return group;
    }

    /**
     * Gets the and check user.
     *
     * @param username
     *            the username
     * @return the and check user
     * @throws ServiceException
     *             the service exception
     */
    private User getAndCheckUser(String username) throws ServiceException {
        JID targetJID = server.createJID(username, null);
        if (targetJID.getNode() == null) {
            throw new ServiceException("Could not get user", username, ExceptionType.USER_NOT_FOUND_EXCEPTION,
                    Response.Status.NOT_FOUND);
        }

        try {
            return userManager.getUser(targetJID.getNode());
        } catch (UserNotFoundException e) {
            throw new ServiceException("Could not get user", username, ExceptionType.USER_NOT_FOUND_EXCEPTION,
                    Response.Status.NOT_FOUND, e);
        }
    }

    /**
     * Gets the user roster.
     *
     * @param username
     *            the username
     * @return the user roster
     * @throws ServiceException
     *             the service exception
     */
    private Roster getUserRoster(String username) throws ServiceException {
        try {
            return rosterManager.getRoster(username);
        } catch (UserNotFoundException e) {
            throw new ServiceException("Could not get user roster", username, ExceptionType.USER_NOT_FOUND_EXCEPTION,
                    Response.Status.NOT_FOUND, e);
        }
    }
}
