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

	// These sets are used to optimize group operations within this map.
	// We only populate these sets when they are needed to dereference the
	// groups in the base map, but once they exist we keep them in sync
	// via the various put/remove operations.
	private transient Set<Group> groupsFromKeys;
	private transient Set<Group> groupsFromValues;
	

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
		if (groupsFromKeys == null) {
			groupsFromKeys = new HashSet<Group>();
			// add all the groups into the group set
			Iterator<K> iterator = keySet().iterator();
			while (iterator.hasNext()) {
				K key = iterator.next();
				Group group = Group.resolveFrom(key);
				if (group != null) {
					groupsFromKeys.add(group);
				};
			}
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
		if (groupsFromValues == null) {
			groupsFromValues = new HashSet<Group>();
			// add all the groups into the group set
			Iterator<V> iterator = values().iterator();
			while (iterator.hasNext()) {
				V value = iterator.next();
				Group group = Group.resolveFrom(value);
				if (group != null) {
					groupsFromValues.add(group);
				};
			}
		}
		return groupsFromValues;
	}

	/**
	 * This method is called from several of the mutators to keep
	 * the group set in sync with the keys in the map. 
	 * 
	 * @param item The item to be added or removed if it is in the group set
	 * @param keyOrValue True for keys, false for values
	 * @param addOrRemove True to add, false to remove
	 * @return true if the given item is a group
	 */
	private synchronized boolean syncGroups(Object item, boolean keyOrValue, boolean addOrRemove) {
		boolean result = false;
		Set<Group> groupSet = (keyOrValue == KEYS) ? groupsFromKeys : groupsFromValues;
		// only sync if the group list has been instantiated
		if (groupSet != null) {
			Group group = Group.resolveFrom(item);
			if (group != null) {
				result = true;
				if (addOrRemove == ADD) {
					groupSet.add(group);
				} else if (addOrRemove == REMOVE) {
					groupSet.remove(group);
				}
			}
		}
		return result;
	}
	
	// below are overrides for the various mutators
	
	@Override
	public V put(K key, V value) {
		V priorValue = super.put(key, value);
		syncGroups(value, VALUES, ADD);
		if (priorValue == null) {
			syncGroups(key, KEYS, ADD);
		} else {
			syncGroups(priorValue, VALUES, REMOVE);
		}
		return priorValue;
	}


	@Override
	public V putIfAbsent(K key, V value) {
		V priorValue = super.putIfAbsent(key, value);
		// if the map already contains the key, there was no change
		if (!value.equals(priorValue)) {
			syncGroups(value, VALUES, ADD);
			if (priorValue == null) {
				syncGroups(key, KEYS, ADD);
			} else {
				syncGroups(priorValue, VALUES, REMOVE);
			}
		}
		return priorValue;
	}


	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		super.putAll(m);
		// drop the transient sets; will be rebuilt when/if needed
		synchronized(this) {
			groupsFromKeys = null;
			groupsFromValues = null;
		}
	}


	@Override
	public V remove(Object key) {
		V priorValue = super.remove(key);
		if (priorValue != null) {
			syncGroups(key, KEYS, REMOVE);
			syncGroups(priorValue, VALUES, REMOVE);
		}
		return priorValue;
	}


	@Override
	public boolean remove(Object key, Object value) {
		boolean removed = super.remove(key, value);
		if (removed) {
			syncGroups(key, KEYS, REMOVE);
			syncGroups(value, VALUES, REMOVE);
		}
		return removed;
	}


	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		boolean replaced = super.replace(key, oldValue, newValue);
		if (replaced) {
			syncGroups(oldValue, VALUES, REMOVE);
			syncGroups(newValue, VALUES, ADD);
		}
		return replaced;
	}


	@Override
	public V replace(K key, V value) {
		V priorValue = super.replace(key, value);
		if (priorValue != null) {
			syncGroups(value, VALUES, ADD);
			syncGroups(priorValue, VALUES, REMOVE);
		}
		return priorValue;
	}


	@Override
	public void clear() {
		super.clear();
		synchronized(this) {
			groupsFromKeys = null;
			groupsFromValues = null;
		}
	}

	private static final boolean KEYS = true;
	private static final boolean VALUES = false;
	private static final boolean ADD = true;
	private static final boolean REMOVE = false;
}
