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

	private static final long serialVersionUID = -8884698048047935327L;
	
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
	public synchronized Set<Group> getGroups() {
		Set<Group> groupsInList = new HashSet<Group>();
		// add all the groups into the group set
		Iterator<T> iterator = iterator();
		while (iterator.hasNext()) {
			T listItem = iterator.next();
			Group group = Group.resolveFrom(listItem);
			if (group != null) {
				groupsInList.add(group);
			};
		}
		return groupsInList;
	}
}
