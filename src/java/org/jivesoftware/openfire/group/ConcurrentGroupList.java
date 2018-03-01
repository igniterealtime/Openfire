package org.jivesoftware.openfire.group;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.xmpp.packet.JID;

/**
 * This list specifies additional methods that understand groups among 
 * the items in the list.
 * 
 * @author Tom Evans
 */
public class ConcurrentGroupList<T> extends CopyOnWriteArrayList<T> implements GroupAwareList<T> {

    private static final long serialVersionUID = 7735849143650412115L;

    // This set is used to optimize group operations within this list.
    // We only populate this set when it's needed to dereference the
    // groups in the base list, but once it exists we keep it in sync
    // via the various add/remove operations.
    // NOTE: added volatile keyword for double-check idiom (lazy instantiation)
    private volatile transient Set<String> knownGroupNamesInList;
    
    public ConcurrentGroupList() {
        super();
    }

    public ConcurrentGroupList(Collection<? extends T> c) {
        super(c);
    }

    /**
     * Returns true if the list contains the given JID. If the JID
     * is not found in the list, search the list for groups and look
     * for the JID in each of the corresponding groups.
     * 
     * @param value The target, presumably a JID
     * @return True if the target is in the list, or in any groups in the list
     */
    @Override
    public boolean includes(Object value) {
        boolean found = false;
        if (contains(value)) {
            found = true;
        } else if (value instanceof JID) {
            JID target = (JID) value;
            Iterator<Group> iterator = getGroups().iterator();
            while (!found && iterator.hasNext()) {
                found = iterator.next().isUser(target);
            }
        }
        return found;
    }

    /**
     * Returns the groups that are implied (resolvable) from the items in the list.
     * 
     * @return A Set containing the groups in the list
     */
    @Override
    public Set<Group> getGroups() {
        Set<Group> result = new HashSet<>();
        for (String groupName : getKnownGroupNamesInList()) {
            result.add(Group.resolveFrom(groupName));
        }
        return result;
    }
    
    /**
     * Accessor uses the  "double-check idiom" (j2se 5.0+) for proper lazy instantiation.
     * Additionally, the set is not cached until there is at least one group in the list.
     * 
     * @return the known group names among the items in the list
     */
    private Set<String> getKnownGroupNamesInList() {
        Set<String> result = knownGroupNamesInList;
        if (result == null) {
            synchronized(this) {
                result = knownGroupNamesInList;
                if (result == null) {
                    result = new HashSet<>();
                    // add all the groups into the group set
                    Iterator<T> iterator = iterator();
                    while (iterator.hasNext()) {
                        T listItem = iterator.next();
                        Group group = Group.resolveFrom(listItem);
                        if (group != null) {
                            result.add(group.getName());
                        };
                    }
                    knownGroupNamesInList = result.isEmpty() ? null : result;
                }
            }
        }
        return result;
    }

    /**
     * This method is called from several of the mutators to keep
     * the group set in sync with the full list. 
     * 
     * @param item The item to be added or removed if it is in the group set
     * @param addOrRemove True to add, false to remove
     * @return true if the given item is a group
     */
    private synchronized boolean syncGroups(Object item, boolean addOrRemove) {
        boolean result = false;
        // only sync if the group list has been instantiated
        if (knownGroupNamesInList != null) {
            Group group = Group.resolveFrom(item);
            if (group != null) {
                result = true;
                if (addOrRemove == ADD) {
                    knownGroupNamesInList.add(group.getName());
                } else if (addOrRemove == REMOVE) {
                    knownGroupNamesInList.remove(group.getName());
                    if (knownGroupNamesInList.isEmpty()) {
                        knownGroupNamesInList = null;
                    }
                }
            }
        }
        return result;
    }
    
    // below are overrides for the various mutators
    
    @Override
    public T set(int index, T element) {
        T result = super.set(index, element);
        syncGroups(element, ADD);
        return result;
    }

    @Override
    public boolean add(T e) {
        boolean result = super.add(e);
        syncGroups(e, ADD);
        return result;
    }

    @Override
    public void add(int index, T element) {
        super.add(index, element);
        syncGroups(element, ADD);
    }

    @Override
    public T remove(int index) {
        T result = super.remove(index);
        syncGroups(result, REMOVE);
        return result;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = super.remove(o);
        if (removed) {
            syncGroups(o, REMOVE);
        }
        return removed;
    }

    @Override
    public boolean addIfAbsent(T e) {
        boolean added = super.addIfAbsent(e);
        if (added) {
            syncGroups(e, ADD);
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = super.removeAll(c);
        if (changed) {
            // drop the transient set, will be rebuilt when/if needed
            synchronized(this) {
                knownGroupNamesInList = null;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = super.retainAll(c);
        if (changed) {
            // drop the transient set, will be rebuilt when/if needed
            synchronized(this) {
                knownGroupNamesInList = null;
            }
        }
        return changed;
    }

    @Override
    public int addAllAbsent(Collection<? extends T> c) {
        int added = super.addAllAbsent(c);
        if (added > 0) {
            // drop the transient set, will be rebuilt when/if needed
            synchronized(this) {
                knownGroupNamesInList = null;
            }
        }
        return added;
    }

    @Override
    public void clear() {
        super.clear();
        synchronized(this) {
            knownGroupNamesInList = null;
        }
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean changed = super.addAll(c);
        if (changed) {
            // drop the transient set, will be rebuilt when/if needed
            synchronized(this) {
                knownGroupNamesInList = null;
            }
        }
        return changed;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean changed = super.addAll(index, c);
        if (changed) {
            // drop the transient set, will be rebuilt when/if needed
            synchronized(this) {
                knownGroupNamesInList = null;
            }
        }
        return changed;
    }

    private static final boolean ADD = true;
    private static final boolean REMOVE = false;
}
