package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.plugin.rules.Rule;
import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

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
		if (packet.getTo() == null || packet.getFrom() == null)
			return null;
		// TODO Would it be better to keep a local copy of the rules?
		for (Rule rule : ruleManager.getRules()) {
			if (!rule.isDisabled() && typeMatch(rule.getPackeType(), packet)
					&& sourceDestMatch(rule.getDestType(), rule.getDestination(), packet.getTo())
					&& sourceDestMatch(rule.getSourceType(), rule.getSource(), packet.getFrom())) {

				return rule;
			}
		}
		return null;
	}

	private boolean typeMatch(Rule.PacketType rulePacketType, Packet packet) {
		// Simple case. Any.
		if (rulePacketType == Rule.PacketType.Any)
			return true;

		else if (packet instanceof Message) {
			Message message = (Message) packet;
			if (rulePacketType == Rule.PacketType.Message) {
				return true;
			}
			// Try some types.
			else if (rulePacketType == Rule.PacketType.MessageChat && message.getType() == Message.Type.chat) {
				return true;
			} else if (rulePacketType == Rule.PacketType.MessageGroupChat && message.getType() == Message.Type.groupchat) {
				return true;
			}
			return false;
		} else if (packet instanceof Presence) {
			if (rulePacketType == Rule.PacketType.Presence) {
				return true;
			} else
				return false;
		} else if (packet instanceof IQ) {
			if (rulePacketType == Rule.PacketType.Iq) {
				return true;
			} else
				return false;
		}

		return false;
	}

	private boolean sourceDestMatch(Rule.SourceDestType type, String ruleToFrom, JID packetToFrom) {
		if (type == Rule.SourceDestType.Any)
			return true;
		if (type == Rule.SourceDestType.User) {
			if (ruleToFrom.equals(packetToFrom.toBareJID())) {
				return true;
			}
		} else if (type == Rule.SourceDestType.Group) {
			return packetToFromGroup(ruleToFrom, packetToFrom);
		} else if (type == Rule.SourceDestType.Component) {
			if (ruleToFrom.toLowerCase().equals(packetToFrom.getDomain().toLowerCase())) {
				return true;
			}
		} else if (type == Rule.SourceDestType.Other) {
			if (matchUser(ruleToFrom, packetToFrom)) {
				return true;
			}
		}
		return false;
	}

	private boolean matchUser(String ruleToFrom, JID packetToFrom) {
		boolean match = false;
		// Escape the text so I get a rule to packet match.
		// String escapedPacketToFrom = JID.unescapeNode(packetToFrom.toBareJID().toString());
		if (ruleToFrom.indexOf("*") == 0 && ruleToFrom.indexOf("@") == 1) {
			if (PacketFilterUtil.getDomain(ruleToFrom).equals(packetToFrom.getDomain().toString())) {
				match = true;
			}
		} else {
			if (ruleToFrom.equals(packetToFrom.toBareJID())) {
				match = true;
			}
		}
		return match;
	}

	private boolean packetToFromGroup(String rulegroup, JID packetToFrom) {
		Group group = null;
		boolean result = false;
		try {
			group = GroupManager.getInstance().getGroup(rulegroup);
		} catch (GroupNotFoundException e) {
			if (PacketFilterConstants.ANY_GROUP.equals(rulegroup)) {
				if (!GroupManager.getInstance().getGroups(packetToFrom).isEmpty()) {
					result = true;
				}
			} else {
				e.printStackTrace();
			}
		}
		if (group != null) {
			if (group.isUser(packetToFrom)) {
				result = true;
			}
		}
		return result;
	}
}
