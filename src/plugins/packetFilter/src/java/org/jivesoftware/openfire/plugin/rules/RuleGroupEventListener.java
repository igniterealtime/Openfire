package org.jivesoftware.openfire.plugin.rules;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.plugin.PacketFilterConstants;
import org.jivesoftware.openfire.plugin.rules.Rule.SourceDestType;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleGroupEventListener implements GroupEventListener {

	private static final Logger Log = LoggerFactory.getLogger(RuleGroupEventListener.class);

	private RuleManager rm = new RuleManagerProxy();

	@Override
	public void groupDeleting(Group group, Map params) {
		deleteAllAutoCreatedRules(group);
	}

	public void createPassRule(String source, String destination, String groupName) {
		Pass pass = new Pass();
		pass.setPacketType(Rule.PacketType.Any);
		pass.setDestType(SourceDestType.Group);
		pass.setDestination(destination);
		pass.setSourceType(SourceDestType.Group);
		pass.setSource(source);
		pass.isDisabled(false);
		pass.doLog(false);
		pass.setDescription("Auto created rule for group: " + groupName);
		rm.addRule(pass, 1);
		Log.debug("Created rule for group: " + groupName + " rule: " + pass);
	}

	@Override
	public void groupModified(Group group, Map params) {
		String keyChanged = (String) params.get("propertyKey");
		String originalValue = (String) params.get("originalValue");
		Log.debug("Group: " + group.getName() + " params: " + params + " originalValue:" + originalValue);
		if ("sharedRoster.groupList".equals(keyChanged)) {
			String currentValue = group.getProperties().get("sharedRoster.groupList");
			if (currentValue != null && !"".equals(currentValue)) {
				deleteAllAutoCreatedRules(group);
				Collection<Group> groupList = parseGroups(currentValue);
				createPassRule(group.getName(), group.getName(), group.getName());
				for (Group tmpGroup : groupList) {
					createPassRule(tmpGroup.getName(), group.getName(), group.getName());
					createPassRule(group.getName(), tmpGroup.getName(), group.getName());
				}
			}
		}

		else if ("sharedRoster.showInRoster".equals(keyChanged)) {
			String currentValue = group.getProperties().get("sharedRoster.showInRoster");
			if ("onlyGroup".equals(currentValue)) {
				deleteAllAutoCreatedRules(group);

				createPassRule(group.getName(), group.getName(), group.getName());
			} else if ("everybody".equals(currentValue)) {
				deleteAllAutoCreatedRules(group);

				createPassRule(PacketFilterConstants.ANY_GROUP, group.getName(), group.getName());
				createPassRule(group.getName(), PacketFilterConstants.ANY_GROUP, group.getName());
			} else if ("nobody".equals(currentValue)) {
				deleteAllAutoCreatedRules(group);
			}
		}
	}

	public void deleteAllAutoCreatedRules(Group group) {
		for (Rule rule : rm.getRules()) {
			if (Rule.SourceDestType.Group == rule.getSourceType()
					&& (group.getName().equals(rule.getSource()) || group.getName().equals(rule.getDestination()))) {
				if (rule.getDescription().startsWith("Auto created rule for group: " + group.getName())) {
					rm.deleteRule(Integer.valueOf(rule.getRuleId()));
					Log.debug("Deleted rule for group: " + group.getName() + " rule: " + rule);
				}
			}
		}
	}

	@Override
	public void groupCreated(Group group, Map params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void memberAdded(Group group, Map params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void memberRemoved(Group group, Map params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void adminAdded(Group group, Map params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void adminRemoved(Group group, Map params) {
		// TODO Auto-generated method stub

	}

	/**
	 * Returns a collection of Groups obtained by parsing a comma delimited String with the name of groups.
	 * 
	 * @param groupNames
	 *            a comma delimited string with group names.
	 * @return a collection of Groups obtained by parsing a comma delimited String with the name of groups.
	 */
	private Collection<Group> parseGroups(String groupNames) {
		Collection<Group> answer = new HashSet<Group>();
		for (String groupName : parseGroupNames(groupNames)) {
			try {
				answer.add(GroupManager.getInstance().getGroup(groupName));
			} catch (GroupNotFoundException e) {
				// Do nothing. Silently ignore the invalid reference to the group
			}
		}
		return answer;
	}

	/**
	 * Returns a collection of Groups obtained by parsing a comma delimited String with the name of groups.
	 * 
	 * @param groupNames
	 *            a comma delimited string with group names.
	 * @return a collection of Groups obtained by parsing a comma delimited String with the name of groups.
	 */
	private static Collection<String> parseGroupNames(String groupNames) {
		Collection<String> answer = new HashSet<String>();
		if (groupNames != null) {
			StringTokenizer tokenizer = new StringTokenizer(groupNames, ",");
			while (tokenizer.hasMoreTokens()) {
				answer.add(tokenizer.nextToken());
			}
		}
		return answer;
	}

}
