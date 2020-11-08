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

    private static final long serialVersionUID = 5479781418678223200L;

    // These sets are used to optimize group operations within this map.
    // We only populate these sets when they are needed to dereference the
    // groups in the base map, but once they exist we keep them in sync
    // via the various put/remove operations.
    // NOTE: added volatile keyword for double-check idiom (lazy instantiation)
    private volatile transient Set<String> knownGroupNamesFromKeys;
    private volatile transient Set<String> knownGroupNamesFromValues;

    /**
     * Returns true if the key list contains the given JID. If the JID is not found in the 
     * key list (exact match), search the key list for groups and look for the JID in 
     * each of the corresponding groups (implied match).
     * 
     * @param key The target, presumably a JID
     * @return True if the target is in the key list, or in any groups in the key list
     */
    @Override
    public boolean includesKey(Object key) {
        boolean found = false;
        if (containsKey(key)) {
            found = true;
        } else if (key instanceof JID) {
            // look for group JIDs in the list of keys and dereference as needed
            JID target = (JID) key;
            Iterator<Group> iterator = getGroupsFromKeys().iterator();
            while (!found && iterator.hasNext()) {
                Group next = iterator.next();
                if(next != null)
                    found = next.isUser(target);
            }
        }
        return found;
    }


    /**
     * Returns true if the map has an entry value matching the given JID. If the JID is not 
     * found in the value set (exact match), search the value set for groups and look for the 
     * JID in each of the corresponding groups (implied match).
     * 
     * @param value The target, presumably a JID
     * @return True if the target is in the value set, or in any groups in the value set
     */
    @Override
    public boolean includesValue(Object value) {
        boolean found = false;
        if (containsValue(value)) {
            found = true;
        } else if (value instanceof JID) {
            // look for group JIDs in the list of values and dereference as needed
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
        Set<Group> result = new HashSet<>();
        for(String groupName : getKnownGroupNamesFromKeys()) {
            Group resolved = Group.resolveFrom(groupName);
            if(resolved != null)
                result.add(resolved);
        }
        return result;
    }

    /**
     * Returns the groups that are implied (resolvable) from the values in the map.
     * 
     * @return A Set containing the groups among the values
     */
    @Override
    public synchronized Set<Group> getGroupsFromValues() {
        Set<Group> result = new HashSet<>();
        for(String groupName : getKnownGroupNamesFromValues()) {
            result.add(Group.resolveFrom(groupName));
        }
        return result;
    }

    
    /**
     * Accessor uses the  "double-check idiom" (j2se 5.0+) for proper lazy instantiation.
     * Additionally, nothing is cached until there is at least one group in the map's keys.
     * 
     * @return the known group names among the items in the list
     */
    private Set<String> getKnownGroupNamesFromKeys() {
        Set<String> result = knownGroupNamesFromKeys;
        if (result == null) {
            synchronized(this) {
                result = knownGroupNamesFromKeys;
                if (result == null) {
                    result = new HashSet<>();
                    // add all the groups into the group set
                    Iterator<K> iterator = keySet().iterator();
                    while (iterator.hasNext()) {
                        K key = iterator.next();
                        Group group = Group.resolveFrom(key);
                        if (group != null) {
                            result.add(group.getName());
                        };
                    }
                    knownGroupNamesFromKeys = result.isEmpty() ? null : result;
                }
            }
        }
        return result;
    }

    
    /**
     * Accessor uses the  "double-check idiom" (j2se 5.0+) for proper lazy instantiation.
     * Additionally, nothing is cached until there is at least one group in the map's value set.
     * 
     * @return the known group names among the items in the list
     */
    private Set<String> getKnownGroupNamesFromValues() {
        Set<String> result = knownGroupNamesFromValues;
        if (result == null) {
            synchronized(this) {
                result = knownGroupNamesFromValues;
                if (result == null) {
                    result = new HashSet<>();
                    // add all the groups into the group set
                    Iterator<V> iterator = values().iterator();
                    while (iterator.hasNext()) {
                        V key = iterator.next();
                        Group group = Group.resolveFrom(key);
                        if (group != null) {
                            result.add(group.getName());
                        };
                    }
                    knownGroupNamesFromValues = result.isEmpty() ? null : result;
                }
            }
        }
        return result;
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
        Set<String> groupSet = (keyOrValue == KEYS) ? knownGroupNamesFromKeys : knownGroupNamesFromValues;
        // only sync if the group list has been instantiated
        if (groupSet != null) {
            Group group = Group.resolveFrom(item);
            if (group != null) {
                result = true;
                if (addOrRemove == ADD) {
                    groupSet.add(group.getName());
                } else if (addOrRemove == REMOVE) {
                    groupSet.remove(group.getName());
                    if (groupSet.isEmpty()) {
                        if (keyOrValue == KEYS) {
                            knownGroupNamesFromKeys = null;
                        } else {
                            knownGroupNamesFromValues = null;
                        }
                    }
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
        clearCache();
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
        clearCache();
    }
    
    /**
     * Certain operations imply that our locally cached group list(s) should
     * be dropped and recreated. For example, when an ad-hoc client command
     * is used to add a member to a group, the underlying group instance is
     * actually dropped from the global Group cache, with the effect that our
     * cache would be referring to an orphaned Group instance.
     */
    private synchronized void clearCache() {
        knownGroupNamesFromKeys = null;
        knownGroupNamesFromValues = null;
    }

    private static final boolean KEYS = true;
    private static final boolean VALUES = false;
    private static final boolean ADD = true;
    private static final boolean REMOVE = false;

}
