package org.jivesoftware.openfire.plugin.rest.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.plugin.rest.dao.PropertyDAO;
import org.jivesoftware.openfire.plugin.rest.entity.GroupEntity;
import org.jivesoftware.openfire.plugin.rest.entity.RosterEntities;
import org.jivesoftware.openfire.plugin.rest.entity.RosterItemEntity;
import org.jivesoftware.openfire.plugin.rest.entity.UserEntities;
import org.jivesoftware.openfire.plugin.rest.entity.UserEntity;
import org.jivesoftware.openfire.plugin.rest.entity.UserGroupsEntity;
import org.jivesoftware.openfire.plugin.rest.entity.UserProperty;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.rest.utils.UserUtils;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

/**
 * The Class UserServiceController.
 */
public class UserServiceController {
    /** The Constant INSTANCE. */
    public static final UserServiceController INSTANCE = new UserServiceController();

    /** The user manager. */
    private UserManager userManager;

    /** The roster manager. */
    private RosterManager rosterManager;

    /** The server. */
    private XMPPServer server;
    
    /** The lock out manager. */
    private LockOutManager lockOutManager;

    /**
     * Gets the single instance of UserServiceController.
     *
     * @return single instance of UserServiceController
     */
    public static UserServiceController getInstance() {
        return INSTANCE;
    }

    /**
     * Instantiates a new user service controller.
     */
    private UserServiceController() {
        server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        rosterManager = server.getRosterManager();
        lockOutManager = server.getLockOutManager();
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
                        ExceptionType.USER_ALREADY_EXISTS_EXCEPTION, Response.Status.CONFLICT);
            }
            addProperties(userEntity.getUsername(), userEntity.getProperties());
        } else {
            throw new ServiceException("Could not create new user",
                    "users", ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION, Response.Status.BAD_REQUEST);
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
            // Payload contains another username than provided over path
            // parameter
            if (userEntity.getUsername() != null) {
                if (!userEntity.getUsername().equals(username)) {
                    JustMarriedController.changeName(username, userEntity.getUsername(), true, userEntity.getEmail(),
                            userEntity.getName());
                    addProperties(userEntity.getUsername(), userEntity.getProperties());
                    return;
                }
            }
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

            addProperties(username, userEntity.getProperties());
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
     * @param propertyValue
     * @param propertyKey
     * @return the user entities
     * @throws ServiceException
     */
    public UserEntities getUserEntities(String userSearch, String propertyKey, String propertyValue)
            throws ServiceException {
        if (propertyKey != null) {
            return getUserEntitiesByProperty(propertyKey, propertyValue);
        }
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
        lockOutManager.enableAccount(username);
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
        lockOutManager.disableAccount(username, null, null);
        
        if (lockOutManager.isAccountDisabled(username)) {
            final StreamError error = new StreamError(StreamError.Condition.not_authorized);
            for (ClientSession sess : SessionManager.getInstance().getSessions(username)) {
                sess.deliverRawText(error.toXML());
                sess.close();
            }
        }
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
     *             the service exception
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
                    group = GroupController.getInstance().createGroup(new GroupEntity(groupName, ""));
                }
                groups.add(group);
            }
            for (Group group : groups) {
                group.getMembers().add(server.createJID(username, null));
            }
        }
    }
    
    /**
     * Adds the user to group.
     *
     * @param username the username
     * @param groupName the group name
     * @throws ServiceException the service exception
     */
    public void addUserToGroup(String username, String groupName) throws ServiceException {
        Group group = null;
        try {
            group = GroupManager.getInstance().getGroup(groupName);
        } catch (GroupNotFoundException e) {
            // Create this group
            group = GroupController.getInstance().createGroup(new GroupEntity(groupName, ""));
        }
        
        group.getMembers().add(server.createJID(username, null));
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
     * Delete user from group.
     *
     * @param username the username
     * @param groupName the group name
     * @throws ServiceException the service exception
     */
    public void deleteUserFromGroup(String username, String groupName) throws ServiceException {
        Group group = null;
        try {
            group = GroupManager.getInstance().getGroup(groupName);
        } catch (GroupNotFoundException e) {
            throw new ServiceException("Could not find group", groupName, ExceptionType.GROUP_NOT_FOUND,
                    Response.Status.NOT_FOUND, e);
        }
        group.getMembers().remove(server.createJID(username, null));
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
    private void addProperties(String username, List<UserProperty> properties) throws ServiceException {
        User user = getAndCheckUser(username);
        user.getProperties().clear();
        if (properties != null) {
            for (UserProperty property : properties) {
                user.getProperties().put(property.getKey(), property.getValue());
            }
        }
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
