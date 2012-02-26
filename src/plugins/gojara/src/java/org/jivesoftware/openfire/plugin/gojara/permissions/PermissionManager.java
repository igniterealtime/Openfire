package org.jivesoftware.openfire.plugin.gojara.permissions;

import java.util.Collection;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

/**
 * 
 * Gateways can be limited to a special user group. This manager helps you to
 * check if the gateways is limited and if a user is in this group
 * 
 * @author Holger Bergunde
 * 
 */
public class PermissionManager {

	GroupManager _groupManager = GroupManager.getInstance();

	public boolean isGatewayLimited(String subdomain) {
		return getGroupForGateway(subdomain).length() > 0;
	}

	public boolean allowedForUser(String gateway, JID jid) {
		String groupAllowedFor = getGroupForGateway(gateway);
		if (groupAllowedFor != null) {
			Collection<Group> groups = _groupManager.getGroups(jid);
			for (Group gr : groups) {
				if (gr.getName().equals(groupAllowedFor)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns the name of the group the usage is limited for the given gateway
	 * 
	 * @param gateway
	 *            the subdomain of the gateway
	 * @return name of the group, or "" if there is no group
	 */
	public String getGroupForGateway(String gateway) {
		return JiveGlobals.getProperty("plugin.remoteroster.permissiongroup." + gateway, "");
	}

	/**
	 * Set the group name for the limitation
	 * 
	 * @param gateway
	 *            subdomain of the component
	 * @param group
	 *            groupname that exists in openfire
	 */
	public void setGroupForGateway(String gateway, String group) {
		JiveGlobals.setProperty("plugin.remoteroster.permissiongroup." + gateway, group);
	}

	/**
	 * Remove the limitaion from the specified gateway
	 * 
	 * @param gateway
	 *            subdomain of the component
	 */
	public void removeGatewayLimitation(String gateway) {
		setGroupForGateway(gateway, "");
	}

}
