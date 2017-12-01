package org.jivesoftware.openfire.plugin.rest.controller;

import java.util.List;

import javax.ws.rs.core.Response;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.xmpp.packet.StreamError;

/**
 * The Class JustMarriedController.
 */
public class JustMarriedController {

    /**
     * Change name.
     *
     * @param currentUserName
     *            the current user name
     * @param newUserName
     *            the new user name
     * @param deleteOldUser
     *            the delete old user
     * @param newEmail
     *            the new email
     * @param newRealName
     *            the new real name
     * @return true, if successful
     * @throws ServiceException
     *             the service exception
     */
    public static boolean changeName(String currentUserName, String newUserName, boolean deleteOldUser,
            String newEmail, String newRealName) throws ServiceException {
        UserManager userManager = UserManager.getInstance();

        try {
            User currentUser = userManager.getUser(currentUserName);
            // Old user found, create new one
            String password = AuthFactory.getPassword(currentUserName);
            String newName = (newRealName == null || newRealName.length() == 0) ? currentUser.getName() : newRealName;
            String newMail = (newEmail == null || newEmail.length() == 0) ? currentUser.getEmail() : newEmail;
            User newUser = userManager.createUser(newUserName, password, currentUser.getName(), newMail);
            newUser.setName(newName);
            newUser.setNameVisible(currentUser.isNameVisible());
            newUser.setEmailVisible(currentUser.isEmailVisible());
            newUser.setCreationDate(currentUser.getCreationDate());

            copyRoster(currentUser, newUser, currentUserName);
            copyProperties(currentUser, newUser);
            copyToGroups(currentUserName, newUserName);
            copyVCard(currentUserName, newUserName);
            if (deleteOldUser) {
                deleteUser(currentUser);
            }

        } catch (UserNotFoundException e) {
            throw new ServiceException("Could not find user", currentUserName, ExceptionType.USER_NOT_FOUND_EXCEPTION,
                    Response.Status.NOT_FOUND, e);
        } catch (UserAlreadyExistsException e) {
            throw new ServiceException("Could not create new user", newUserName,
                    ExceptionType.USER_ALREADY_EXISTS_EXCEPTION, Response.Status.CONFLICT, e);
        }
        return true;

    }

    /**
     * Copy v card.
     *
     * @param currentUserName
     *            the current user name
     * @param newUserName
     *            the new user name
     * @throws ServiceException
     *             the service exception
     */
    private static void copyVCard(String currentUserName, String newUserName) throws ServiceException {
        VCardManager vcardManager = VCardManager.getInstance();
        Element vcard = vcardManager.getVCard(currentUserName);

        if (vcard != null) {
            try {
                vcardManager.setVCard(newUserName, vcard);
            } catch (Exception e) {
                throw new ServiceException("Could not copy vcard to new user", newUserName,
                        ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION, Response.Status.BAD_REQUEST, e);
            }
        }
    }

    /**
     * Copy to groups.
     *
     * @param currentUser
     *            the current user
     * @param newUser
     *            the new user
     */
    private static void copyToGroups(String currentUser, String newUser) {
        GroupManager groupManager = GroupManager.getInstance();
        for (Group group : groupManager.getGroups()) {
            if (group.isUser(currentUser)) {
                group.getMembers().add(XMPPServer.getInstance().createJID(newUser, null));
            }
        }

    }

    /**
     * Delete user.
     *
     * @param oldUser
     *            the old user
     */
    private static void deleteUser(User oldUser) {
        UserManager.getInstance().deleteUser(oldUser);
        LockOutManager.getInstance().enableAccount(oldUser.getUsername());
        final StreamError error = new StreamError(StreamError.Condition.not_authorized);
        for (ClientSession sess : SessionManager.getInstance().getSessions(oldUser.getUsername())) {
            sess.deliverRawText(error.toXML());
            sess.close();
        }
    }

    /**
     * Copy properties.
     *
     * @param currentUser
     *            the current user
     * @param newUser
     *            the new user
     */
    private static void copyProperties(User currentUser, User newUser) {
        newUser.getProperties().putAll( currentUser.getProperties() );
    }

    /**
     * Copy roster.
     *
     * @param currentUser
     *            the current user
     * @param newUser
     *            the new user
     * @param currentUserName
     *            the current user name
     * @throws ServiceException
     *             the service exception
     */
    private static void copyRoster(User currentUser, User newUser, String currentUserName) throws ServiceException {
        Roster newRoster = newUser.getRoster();
        Roster currentRoster = currentUser.getRoster();
        for (RosterItem item : currentRoster.getRosterItems()) {
            try {
                List<String> groups = item.getGroups();

                RosterItem justCreated = newRoster.createRosterItem(item.getJid(), item.getNickname(), groups, true,
                        true);
                justCreated.setAskStatus(item.getAskStatus());
                justCreated.setRecvStatus(item.getRecvStatus());
                justCreated.setSubStatus(item.getSubStatus());

                for (Group gr : item.getSharedGroups()) {
                    justCreated.addSharedGroup(gr);
                }

                for (Group gr : item.getInvisibleSharedGroups()) {
                    justCreated.addInvisibleSharedGroup(gr);
                }
                newRoster.updateRosterItem(justCreated);
                addNewUserToOthersRoster(newUser, item, currentUserName);

            } catch (UserAlreadyExistsException e) {
                throw new ServiceException("Could not create roster item for user ", newUser.getUsername(),
                        ExceptionType.USER_ALREADY_EXISTS_EXCEPTION, Response.Status.CONFLICT, e);
            } catch (SharedGroupException e) {
                throw new ServiceException("Could not create roster item, because it is a contact from a shared group",
                        newUser.getUsername(), ExceptionType.USER_ALREADY_EXISTS_EXCEPTION,
                        Response.Status.BAD_REQUEST, e);
            } catch (UserNotFoundException e) {
                throw new ServiceException("Could not update roster item for user " + newUser.getUsername()
                        + " because it was not properly created.", newUser.getUsername(),
                        ExceptionType.USER_NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND, e);
            }
        }

    }

    /**
     * Adds the new user to others roster.
     *
     * @param newUser
     *            the new user
     * @param otherItem
     *            the other item
     * @param currentUser
     *            the current user
     * @throws ServiceException
     *             the service exception
     */
    private static void addNewUserToOthersRoster(User newUser, RosterItem otherItem, String currentUser)
            throws ServiceException {
        otherItem.getJid();
        UserManager userManager = UserManager.getInstance();

        // Is this user registered with our OF server?
        String username = otherItem.getJid().getNode();
        if (username != null && username.length() > 0 && userManager.isRegisteredUser(username)
                && XMPPServer.getInstance().isLocal(XMPPServer.getInstance().createJID(currentUser, null))) {
            try {
                User otherUser = userManager.getUser(username);
                Roster otherRoster = otherUser.getRoster();
                RosterItem oldUserOnOthersRoster = otherRoster.getRosterItem(XMPPServer.getInstance().createJID(
                        currentUser, null));

                try {
                    if (!oldUserOnOthersRoster.isOnlyShared()) {

                        RosterItem justCreated = otherRoster.createRosterItem(
                                XMPPServer.getInstance().createJID(newUser.getUsername(), null),
                                oldUserOnOthersRoster.getNickname(), oldUserOnOthersRoster.getGroups(), true, true);
                        justCreated.setAskStatus(oldUserOnOthersRoster.getAskStatus());
                        justCreated.setRecvStatus(oldUserOnOthersRoster.getRecvStatus());
                        justCreated.setSubStatus(oldUserOnOthersRoster.getSubStatus());
                        otherRoster.updateRosterItem(justCreated);
                    }
                } catch (UserAlreadyExistsException e) {
                    throw new ServiceException("Could not create roster item for user ", newUser.getUsername(),
                            ExceptionType.USER_ALREADY_EXISTS_EXCEPTION, Response.Status.CONFLICT, e);
                } catch (SharedGroupException e) {
                    throw new ServiceException(
                            "Could not create roster item, because it is a contact from a shared group",
                            newUser.getUsername(), ExceptionType.USER_ALREADY_EXISTS_EXCEPTION,
                            Response.Status.BAD_REQUEST, e);
                }
            } catch (UserNotFoundException e) {
                throw new ServiceException("Could not create roster item for user " + newUser.getUsername()
                        + "  because it is a contact from a shared group.", newUser.getUsername(),
                        ExceptionType.USER_NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND, e);
            }
        }
    }
}
