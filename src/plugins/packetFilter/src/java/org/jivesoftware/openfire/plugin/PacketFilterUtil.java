package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.plugin.component.ComponentList;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.util.Collection;
/*
   Static util methods.
*/
public class PacketFilterUtil  {
    static String serverName = XMPPServer.getInstance().getServerInfo().getName();

    public static boolean isGroup(String name) {
        try {
            GroupManager.getInstance().getGroup(name);
            return true;
        } catch (GroupNotFoundException e) {
            return false;
        }
    }


    public static Group getGroup(String name) {
        Group group = null;
        try {
            group = GroupManager.getInstance().getProvider().getGroup(name);
        } catch (GroupNotFoundException e) {
            e.printStackTrace();
        }
        return group;
    }

    //Faster the better. This will break if virtual hosts is ever implemented.
    public static boolean isLocalUser(String jid) {
        Collection<String> users = UserManager.getUserProvider().getUsernames();
        for (String username : users) {
            if (jid.equals(username+"@"+serverName)) {
               return true;
            }
        }
        return false;
    }

    /*
        Method to get the component part from a jid. The packet could
        be from the component itself so just return. 
     */
    public static String getComponent(String jid) {
       if (jid.contains("@")) {
            int atIndex = jid.indexOf("@");
            return (jid.substring(atIndex+1,jid.length()));
        }
        else {
            return jid;
        }
    }

    public static String getDomain(String jid) {
        return getComponent(jid);
    }


    /*
        Figure out if the packet is going to a component
     */
   /* public static boolean isComponent(String jid) {
        ComponentList cList = ComponentList.getInstance();
        if (cList.getComponentName(jid) == null) {
            return false;
        }
        else {
            return true;
        }

    }
     */
    
    /*
        Make reasonably sure that the string is a valid
        JID.
     */
    /*public static boolean isJID(String jid) {
        try {
            JID _jid = new JID(jid);
        }
        catch (IllegalArgumentException e) {
            Log.error(e);
            return false;
        }

        return true;
    } */
}
