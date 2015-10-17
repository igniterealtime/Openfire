package org.jivesoftware.openfire.group;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.xmpp.packet.JID;

/**
 * This extension class provides additional methods that understand groups among 
 * the entries in the map.
 * 
 * @author Tom Evans
 */

public class ConcurrentGroupMap<K, V> extends ConcurrentHashMap<K, V>  implements GroupAwareMap<K, V> {

	private static final long serialVersionUID = -2068242013524715293L;

	/**
	 * Returns true if the key list contains the given JID. If the JID
	 * is not found in the key list, search the key list for groups and 
	 * look for the JID in each of the corresponding groups.
	 * 
	 * @param key The target, presumably a JID
	 * @return True if the target is in the key list, or in any groups in the key list
	 */
	public boolean includesKey(Object key) {
		boolean found = false;
		if (containsKey(key)) {
			found = true;
		} else if (key instanceof JID) {
			// look for group JIDs in the list of keys and dereference as needed
			JID target = (JID) key;
			Iterator<Group> iterator = getGroupsFromKeys().iterator();
			while (!found && iterator.hasNext()) {
				found = iterator.next().isUser(target);
			}
		}
		return found;
	}


	/**
	 * Returns true if the map has an entry value matching the given JID. If the JID
	 * is not found explicitly, search the values for groups and search 
	 * for the JID in each of the corresponding groups.
	 * 
	 * @param value The target, presumably a JID
	 * @return True if the target is in the value set, or in any groups in the value set
	 */
	public boolean includesValue(Object value) {
		boolean found = false;
		if (containsValue(value)) {
			found = true;
		} else if (value instanceof JID) {
			// look for group JIDs in the list of keys and dereference as needed
			JID target = (JID) value;
			Iterator<Group> iterator = getGroupsFromValues().iterator();
			while (!found && iterator.hasNext()) {
				found = iterator.next().isUser(target);
			}
		}
		return found;
	}

	/**
	 * Returns the groups that are implied (resolvable) from the keys in the map.
	 * 
	 * @return A Set containing the groups among the keys
	 */
	@Override
	public synchronized Set<Group> getGroupsFromKeys() {
		Set<Group> groupsFromKeys = new HashSet<Group>();
		// add all the groups into the group set
		Iterator<K> iterator = keySet().iterator();
		while (iterator.hasNext()) {
			K key = iterator.next();
			Group group = Group.resolveFrom(key);
			if (group != null) {
				groupsFromKeys.add(group);
			};
		}
		return groupsFromKeys;
	}

	/**
	 * Returns the groups that are implied (resolvable) from the values in the map.
	 * 
	 * @return A Set containing the groups among the values
	 */
	@Override
	public synchronized Set<Group> getGroupsFromValues() {
		Set<Group> groupsFromValues = new HashSet<Group>();
		// add all the groups into the group set
		Iterator<V> iterator = values().iterator();
		while (iterator.hasNext()) {
			V value = iterator.next();
			Group group = Group.resolveFrom(value);
			if (group != null) {
				groupsFromValues.add(group);
			};
		}
		return groupsFromValues;
	}
}
