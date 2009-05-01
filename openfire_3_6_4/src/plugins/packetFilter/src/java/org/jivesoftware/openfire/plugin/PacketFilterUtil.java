package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserManager;
import org.xmpp.packet.JID;

import java.util.Collection;
/*
   Static util methods.
*/
public class PacketFilterUtil  {
    
    /*
        Method to get the component part from a jid. The packet could
        be from the component itself so just return.
     */
    public static String getDomain(String jid) {
       if (jid.contains("@")) {
            int atIndex = jid.indexOf("@");
            return (jid.substring(atIndex+1,jid.length()));
        }
        else {
            return jid;
        }
    }
}
