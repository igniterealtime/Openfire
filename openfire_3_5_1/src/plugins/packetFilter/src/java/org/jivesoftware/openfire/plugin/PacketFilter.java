package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.plugin.rules.Rule;
import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.jivesoftware.util.Log;
import org.xmpp.packet.*;

public class PacketFilter {

    private static PacketFilter packetFilter = new PacketFilter();
    RuleManager ruleManager;

    private PacketFilter() {

    }

    public static PacketFilter getInstance() {
        return packetFilter;
    }

    public void setRuleManager(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    public Rule findMatch(Packet packet) {
        if (packet.getTo() == null || packet.getFrom() == null) return null;

        String to = packet.getTo().toBareJID();
        String from = packet.getFrom().toBareJID();

        //TODO Would it be better to keep a local copy of the rules?
        for (Rule rule : ruleManager.getRules()) {

            if (!rule.isDisabled() &&
                    typeMatch(rule.getPackeType().toString(), packet) &&
                    sourceDestMatch(rule.getDestType(), rule.getDestination(), to) &&
                    sourceDestMatch(rule.getSourceType(), rule.getSource(), from)) {

                return rule;
            }
        }
        return null;
    }

    private boolean typeMatch(String rulePacketType, Packet packet) {
        //Simple case. Any.
        if (rulePacketType.equals(Rule.PacketType.Any.toString())) return true;

        else if (packet instanceof Message) {
            Message message = (Message) packet;
            if (rulePacketType.equals(Rule.PacketType.Message.toString())) {
                return true;
            }
            //Try some types.
            else if (rulePacketType.equals(Rule.PacketType.MessageChat.toString())
                    && message.getType().toString().equals("chat")) {
                return true;
            } else if (rulePacketType.equals(Rule.PacketType.MessageGroupChat.toString())
                    && message.getType().toString().equals("groupchat")) {
                return true;
            }
            return false;
        } else if (packet instanceof Presence) {
            if (rulePacketType.equals(Rule.PacketType.Presence.toString())) {
                return true;
            } else return false;
        } else if (packet instanceof IQ) {
            if (rulePacketType.equals(Rule.PacketType.Iq.toString())) {
                return true;
            } else return false;
        }

        return false;
    }


    private boolean sourceDestMatch(String type, String ruleToFrom, String packetToFrom) {
        if (type.equals(Rule.SourceDestType.Any.toString())) return true;
        if (type.equals(Rule.SourceDestType.User.toString())) {
            if (ruleToFrom.equals(packetToFrom)) {
                return true;
            }
        } else if (type.equals(Rule.SourceDestType.Group.toString())) {
            return packetToFromGroup(ruleToFrom, packetToFrom);
        } else if (type.equals(Rule.SourceDestType.Component.toString())) {
            if (ruleToFrom.toLowerCase().equals(PacketFilterUtil.getComponent(packetToFrom).toLowerCase())) {
                return true;
            }
        } else if (type.equals(Rule.SourceDestType.Other.toString())) {
            if (matchUser(ruleToFrom, packetToFrom)) {
                return true;
            }


        }
        return false;
    }

    private boolean matchUser(String ruleToFrom, String packetToFrom) {
        boolean match = false;
        //Escape the text so I get a rule to packet match. 
        packetToFrom = JID.unescapeNode(packetToFrom);
        if (ruleToFrom.indexOf("*") == 0 && ruleToFrom.indexOf("@") == 1) {
            if (PacketFilterUtil.getDomain(ruleToFrom).equals(PacketFilterUtil.getDomain(packetToFrom))) {
                match = true;
            }
        } else {     
            if (ruleToFrom.equals(packetToFrom)) {
                match = true;
            }
        }
        return match;
    }


    private boolean packetToFromGroup(String rulegroup, String packetToFrom) {
        Group group = PacketFilterUtil.getGroup(rulegroup);
        if (group == null) {
            return false;
        } else {
            for (JID jid : group.getMembers()) {
                if (jid.toBareJID().equals(packetToFrom)) {
                    return true;
                }
            }
        }
        return false;
    }
}
