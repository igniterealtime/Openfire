package org.jivesoftware.openfire.plugin.packetfilter.test;

import org.jivesoftware.openfire.plugin.PacketFilterUtil;
import org.xmpp.packet.JID;

public class Test {

    public static void main(String... args) {

        String jid = JID.escapeNode("test@domain1.com@otherdomain.com");
        String allDomain  = "*@domain1.com@otherdomain.com";
        System.out.println(matchUser(allDomain,jid));

    }

    public static String getComponent(String jid) {
        if (jid.contains("@")) {
            int atIndex = jid.indexOf("@");
            return (jid.substring(atIndex,jid.length()));
        }
        else {
            return jid;
        }
    }

    public static String getDomain(String jid) {
        return getComponent(jid);
    }

     public static boolean isJID(String jid) {
        if (jid != null &&
                (jid.split("@").length == 1) &&
                jid.length() < 255 &&
                jid.trim().length() > 0) {
            return true;
        } else return false;
    }

    private static boolean matchUser(String ruleToFrom, String packetToFrom) {
        boolean match = false;
        packetToFrom = JID.unescapeNode(packetToFrom);
        if (ruleToFrom.indexOf("*") == 0 && ruleToFrom.indexOf("@") == 1) {
            System.out.println(getDomain(ruleToFrom));
            System.out.println(getDomain(packetToFrom));
            if (getDomain(ruleToFrom).equals(getDomain(packetToFrom))) {
                match = true;
            }
        }
        else {
            if (ruleToFrom.equals(packetToFrom)) {
                match = true;
            }
        }
        return match;
    }
}
