package org.jivesoftware.openfire.plugin;

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
