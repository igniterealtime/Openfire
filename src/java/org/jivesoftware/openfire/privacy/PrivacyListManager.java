package org.jivesoftware.openfire.privacy;

import org.dom4j.Element;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Privacy list manager creates, gets, updates and removes privacy lists. Loaded lists
 * are kept in memory using a cache that will keep them at most for 6 hours.
 *
 * @author Gaston Dombiak
 */
public class PrivacyListManager {

    private static final PrivacyListManager instance = new PrivacyListManager();
    private static Cache<String, PrivacyList> listsCache;

    private PrivacyListProvider provider = new PrivacyListProvider();

    private List<PrivacyListEventListener> listeners = new CopyOnWriteArrayList<PrivacyListEventListener>();

    static {
        PrivacyListEventListener eventListener = new PrivacyListEventListener() {
            public void privacyListCreated(PrivacyList list) {
                // Do nothing
            }

            public void privacyListDeleting(String listName) {
                // Do nothing
            }

            public void privacyListModified(PrivacyList list) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                listsCache.put(getCacheKey(list.getUserJID().getNode(), list.getName()), list);
            }
        };
        instance.addListener(eventListener);
    }

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     */
    public static PrivacyListManager getInstance() {
        return instance;
    }

    private PrivacyListManager() {
        // Create the cache of privacy lists
        String cacheName = "Privacy Lists";
        CacheManager.initializeCache(cacheName, "listsCache",512 * 1024);
        listsCache = CacheManager.getCache(cacheName);
    }

    /**
     * Creates a new privacy list for the specified user.
     *
     * @param username the username of the list owner.
     * @param listName the name of the new privacy list.
     * @param listElement the XML that specifies the list and its items.
     * @return the newly created PrivacyList.
     */
    public PrivacyList createPrivacyList(String username, String listName, Element listElement) {
        // Create new list
        PrivacyList list = new PrivacyList(username, listName, false, listElement);
        // Add new list to cache
        listsCache.put(getCacheKey(username, listName), list);
        // Save new  list to database
        provider.createPrivacyList(username, list);
        // Trigger event that a new privacy list has been created
        for (PrivacyListEventListener listener : listeners) {
            listener.privacyListCreated(list);
        }
        return list;
    }

    /**
     * Deletes an existing privacy list of a user. If the privacy list being deleted was
     * the default list then the user will end up with no default list. Therefore, the user
     * will have to set a new default list.
     *
     * @param username the username of the list owner.
     * @param listName the name of the list being deleted.
     */
    public void deletePrivacyList(String username, String listName) {
        // Trigger event that a privacy list is being deleted
        for (PrivacyListEventListener listener : listeners) {
            listener.privacyListDeleting(listName);
        }
        // Remove the list from the cache
        listsCache.remove(getCacheKey(username, listName));
        // Delete the privacy list from the DB
        provider.deletePrivacyList(username, listName);
        // Check if deleted list was the default list
        PrivacyList defaultList = listsCache.get(getDefaultCacheKey(username));
        if (defaultList != null && listName.equals(defaultList.getName())) {
            listsCache.remove(getDefaultCacheKey(username));
        }
    }

    /**
     * Deletes all privacy lists of a user. This may be necessary when a user is being
     * deleted from the system.
     *
     * @param username the username of the list owner.
     */
    public void deletePrivacyLists(String username) {
        for (String listName : provider.getPrivacyLists(username).keySet()) {
            // Remove the list from the cache
            listsCache.remove(getCacheKey(username, listName));
            // Trigger event that a privacy list is being deleted
            for (PrivacyListEventListener listener : listeners) {
                listener.privacyListDeleting(listName);
            }
        }
        // Delete user privacy lists from the DB
        provider.deletePrivacyLists(username);
    }

    /**
     * Returns the default privacy list of the specified user or <tt>null</tt> if
     * none was found.
     *
     * @param username the name of the user to get his default list.
     * @return the default privacy list of the specified user or <tt>null</tt> if
     *         none was found.
     */
    public PrivacyList getDefaultPrivacyList(String username) {
        // Check if we have the default list in the cache
        String cacheKey = getDefaultCacheKey(username);
        PrivacyList list = listsCache.get(cacheKey);
        if (list == null) {
            synchronized (username.intern()) {
                list = listsCache.get(cacheKey);
                if (list == null) {
                    // Load default list from the database
                    list = provider.loadDefaultPrivacyList(username);
                    if (list != null) {
                        listsCache.put(cacheKey, list);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Returns a specific privacy list of the specified user or <tt>null</tt> if
     * none was found.
     *
     * @param username the name of the user to get his privacy list.
     * @param listName the name of the list to get.
     * @return a privacy list of the specified user or <tt>null</tt> if
     *         none was found.
     */
    public PrivacyList getPrivacyList(String username, String listName) {
        // Check if we have a list in the cache
        String cacheKey = getCacheKey(username, listName);
        PrivacyList list = listsCache.get(cacheKey);
        if (list == null) {
            // Load the list from the database
            list = provider.loadPrivacyList(username, listName);
            if (list != null) {
                listsCache.put(cacheKey, list);
            }
        }
        return list;
    }

    /**
     * Sets a given privacy list as the new default list of the user.
     *
     * @param username the name of the user that is setting a new default list.
     * @param newDefault the new default privacy list.
     * @param oldDefault the previous privacy list or <tt>null</tt> if no default list existed.
     */
    public void changeDefaultList(String username, PrivacyList newDefault, PrivacyList oldDefault) {
        // TODO Analyze concurrency issues when other resource may log in while doing this change
        if (oldDefault != null) {
            // Update old default list to become just another list
            oldDefault.setDefaultList(false);
            provider.updatePrivacyList(username, oldDefault);
        }
        // Update new list to become the default
        newDefault.setDefaultList(true);
        // Set new default list in the cache
        listsCache.put(getDefaultCacheKey(username), newDefault);
        // Update both lists in the database
        provider.updatePrivacyList(username, newDefault);
    }

    /**
     * Registers a listener to receive events when a privacy list is created, updated or deleted.
     *
     * @param listener the listener.
     */
    public void addListener(PrivacyListEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public void removeListener(PrivacyListEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the key to use to locate a privacy list in the cache.
     */
    private static String getCacheKey(String username, String listName) {
        return username + listName;
    }

    /**
     * Returns the key to use to locate default privacy lists in the cache.
     */
    private static String getDefaultCacheKey(String username) {
        return getCacheKey(username, "__d_e_f_a_u_l_t__");
    }

    void dispatchModifiedEvent(PrivacyList privacyList) {
        // Trigger event that a privacy list has been modified
        for (PrivacyListEventListener listener : listeners) {
            listener.privacyListModified(privacyList);
        }
    }
}
