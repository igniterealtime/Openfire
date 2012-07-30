package org.jivesoftware.openfire.plugin.married;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.xmpp.packet.StreamError;

public class JustMarriedPlugin implements Plugin {

	private static Logger Log = Logger.getLogger(JustMarriedPlugin.class);

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
	}

	public static boolean changeName(String currentUserName, String newUserName, boolean deleteOldUser) {
		UserManager userManager = UserManager.getInstance();

		try {
			User currentUser = userManager.getUser(currentUserName);
			// Old user found, create new one
			String password = AuthFactory.getPassword(currentUserName);
			User newUser = userManager.createUser(newUserName, password, currentUser.getName(), currentUser.getEmail());
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
			Log.error("Could not find user " + currentUserName, e);
			return false;
		} catch (UserAlreadyExistsException e) {
			Log.error("Could not create user " + newUserName, e);
			return false;
		}
		return true;

	}

	private static void copyVCard(String currentUserName, String newUserName) {
		VCardManager vcardManager = VCardManager.getInstance();
		Element vcard = vcardManager.getVCard(currentUserName);

		if (vcard != null) {
			try {
				vcardManager.setVCard(newUserName, vcard);
			} catch (Exception e) {
				Log.error("Could not copy vcard to " + newUserName, e);
			}
		}
	}

	private static void copyToGroups(String currentUser, String newUser) {
		GroupManager groupManager = GroupManager.getInstance();
		for (Group group : groupManager.getGroups()) {
			if (group.isUser(currentUser)) {
				group.getMembers().add(XMPPServer.getInstance().createJID(newUser, null));
			}
		}

	}

	private static void deleteUser(User oldUser) {
		UserManager.getInstance().deleteUser(oldUser);
		final StreamError error = new StreamError(StreamError.Condition.not_authorized);
		for (ClientSession sess : SessionManager.getInstance().getSessions(oldUser.getUsername())) {
			sess.deliverRawText(error.toXML());
			sess.close();
		}
	}

	private static void copyProperties(User currentUser, User newUser) {
		for (String key : currentUser.getProperties().keySet()) {
			newUser.getProperties().put(key, User.getPropertyValue(currentUser.getUsername(), key));
		}
	}

	private static void copyRoster(User currentUser, User newUser, String currentUserName) {

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

				addNewUserToOthersRoster(newUser, item, currentUserName);

			} catch (UserAlreadyExistsException e) {
				Log.error("Could not create roster item for user " + item.getJid(), e);
			} catch (SharedGroupException e) {
				Log.error("Could not create roster item for user " + item.getJid()
						+ " because it is a contact from a shared group", e);
			}
		}

	}

	private static void addNewUserToOthersRoster(User newUser, RosterItem otherItem, String currentUser) {
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
					}
				} catch (UserAlreadyExistsException e) {
					Log.error("Could not create roster item for user " + newUser.getUsername(), e);
				} catch (SharedGroupException e) {
					Log.error(e);

				}
			} catch (UserNotFoundException e) {
				Log.error("Could not create roster item for user " + newUser.getUsername()
						+ " because it is a contact from a shared group", e);
			}
		}
	}

	@Override
	public void destroyPlugin() {
	}

}
