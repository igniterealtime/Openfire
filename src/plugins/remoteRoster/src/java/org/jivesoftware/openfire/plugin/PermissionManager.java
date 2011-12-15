package org.jivesoftware.openfire.plugin;

import java.util.Collection;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

public class PermissionManager {

	GroupManager _groupManager = GroupManager.getInstance();

	public boolean isGatewayLimited(String subdomain)
	{
		return getGroupForGateway(subdomain).length() > 0; 
	}

	public boolean allowedForUser(String gateway, JID jid)
	{
		String groupAllowedFor = getGroupForGateway(gateway);
		if (groupAllowedFor != null) {
			Collection<Group> groups = _groupManager.getGroups(jid);
			for (Group gr : groups) {
				if (gr.getName().equals(groupAllowedFor))
				{
					return true;
				}
			}
		}
		return false;
	}

	public String getGroupForGateway(String gateway)
	{
		return JiveGlobals.getProperty("plugin.remoteroster.permissiongroup."+gateway, "");
	}

	public void setGroupForGateway(String gateway, String group)
	{
		JiveGlobals.setProperty("plugin.remoteroster.permissiongroup."+gateway, group);
	}
	
	public void removeGatewayLimitation(String gateway)
	{
		setGroupForGateway(gateway, "");
	}
	
}
