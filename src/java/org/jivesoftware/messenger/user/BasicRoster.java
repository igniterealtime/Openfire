/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.user;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;
import org.xmpp.packet.JID;

/**
 * <p>Implements the basic Roster interface storing all roster items into a simple hash table.</p>
 * <p>This class is intended to be used as a simple based for creating specialized Roster
 * implementations without having to recode the very boring item management.</p>
 *
 * @author Iain Shigeoka
 */
public class BasicRoster implements Roster, Cacheable {

    /**
     * <p>Roster item cache - table: key jabberid string; value roster item.</p>
     */
    protected Hashtable rosterItems = new Hashtable();
    /**
     * <p>Lock for the roster item map.</p>
     */
    private ReadWriteLock itemLock = new ReentrantReadWriteLock();

    /**
     * <p>Create an empty iq roster packet.</p>
     */
    public BasicRoster() {
    }

    public boolean isRosterItem(JID user) {
        itemLock.readLock().lock();
        try {
            return rosterItems.containsKey(user.toBareJID());
        }
        finally {
            itemLock.readLock().unlock();
        }
    }

    public Iterator getRosterItems() throws UnauthorizedException {
        itemLock.readLock().lock();
        try {
            LinkedList itemList = new LinkedList();
            Iterator items = rosterItems.values().iterator();
            while (items.hasNext()) {
                itemList.add(items.next());
            }
            return itemList.iterator();
        }
        finally {
            itemLock.readLock().unlock();
        }
    }

    public int getTotalRosterItemCount() throws UnauthorizedException {
        itemLock.readLock().lock();
        try {
            return rosterItems.size();
        }
        finally {
            itemLock.readLock().unlock();
        }
    }

    public RosterItem getRosterItem(JID user) throws UnauthorizedException, UserNotFoundException {
        itemLock.readLock().lock();
        try {
            RosterItem item = (RosterItem)rosterItems.get(user.toBareJID());
            if (item == null) {
                throw new UserNotFoundException(user.toBareJID());
            }
            return item;
        }
        finally {
            itemLock.readLock().unlock();
        }
    }

    public RosterItem createRosterItem(JID user) throws UnauthorizedException, UserAlreadyExistsException {
        return createRosterItem(user, null, null);
    }

    public RosterItem createRosterItem(JID user, String nickname, List<String> groups)
            throws UnauthorizedException, UserAlreadyExistsException {
        RosterItem item = provideRosterItem(user, nickname, groups);
        itemLock.writeLock().lock();
        try {
            rosterItems.put(item.getJid().toBareJID(), item);
            return item;
        }
        finally {
            itemLock.writeLock().unlock();
        }
    }

    public void createRosterItem(org.xmpp.packet.Roster.Item item)
            throws UnauthorizedException, UserAlreadyExistsException {
        RosterItem rosterItem = provideRosterItem(item);
        itemLock.writeLock().lock();
        try {
            rosterItems.put(item.getJID().toBareJID(), rosterItem);
        }
        finally {
            itemLock.writeLock().unlock();
        }
    }

    /**
     * <p>Generate a new RosterItem for use with createRosterItem.<p>
     * <p/>
     * <p>Overriding classes will want to override this method to produce the roster
     * item implementation to be used by the BasicRoster.createRsterItem() methods.</p>
     *
     * @param user     The roster jid address to create the roster item for
     * @param nickname The nickname to assign the item (or null for none)
     * @param groups   The groups the item belongs to (or null for none)
     * @return The newly created roster items ready to be stored by the BasicRoster item's hash table
     */
    protected RosterItem provideRosterItem(JID user, String nickname, List<String> groups)
            throws UserAlreadyExistsException, UnauthorizedException {
        return new BasicRosterItem(user, nickname, groups);
    }

    /**
     * <p>Generate a new RosterItem for use with createRosterItem.<p>
     * <p/>
     * <p>Overriding classes will want to override this method to produce the roster
     * item implementation to be used by the BasicRoster.createRsterItem() methods.</p>
     *
     * @param item The item to copy settings for the new item in this roster
     * @return The newly created roster items ready to be stored by the BasicRoster item's hash table
     */
    protected RosterItem provideRosterItem(org.xmpp.packet.Roster.Item item)
            throws UserAlreadyExistsException, UnauthorizedException {
        return new BasicRosterItem(item);
    }

    public void updateRosterItem(RosterItem item) throws UnauthorizedException, UserNotFoundException {
        itemLock.writeLock().lock();
        try {
            if (rosterItems.get(item.getJid().toBareJID()) == null) {
                throw new UserNotFoundException(item.getJid().toBareJID());
            }
            rosterItems.put(item.getJid().toBareJID(), item);
        }
        finally {
            itemLock.writeLock().unlock();
        }
    }

    public RosterItem deleteRosterItem(JID user) throws UnauthorizedException {

        itemLock.writeLock().lock();
        try {
            // If removing the user was successful, remove the user from the subscriber list:
            return (RosterItem)rosterItems.remove(user.toBareJID());
        }
        finally {
            itemLock.writeLock().unlock();
        }
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfMap(rosterItems);          // roster item cache
        return size;
    }
}
