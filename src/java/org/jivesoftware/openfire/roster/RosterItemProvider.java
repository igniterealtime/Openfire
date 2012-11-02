package org.jivesoftware.openfire.roster;

import java.util.Iterator;

import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;

public interface RosterItemProvider {

    /**
    * Creates a new roster item for the given user (optional operation).<p>
    *
    * <b>Important!</b> The item passed as a parameter to this method is strictly a convenience
    * for passing all of the data needed for a new roster item. The roster item returned from the
    * method will be cached by Openfire. In some cases, the roster item passed in will be passed
    * back out. However, if an implementation may return RosterItems as a separate class
    * (for example, a RosterItem that directly accesses the backend storage, or one that is an
    * object in an object database).<p>
    *
    * @param username the username of the user/chatbot that owns the roster item.
    * @param item the settings for the roster item to create.
    * @return the new roster item.
    * @throws UserAlreadyExistsException if a roster item with the username already exists.
    */
    RosterItem createItem(String username, RosterItem item)
            throws UserAlreadyExistsException;

    /**
     * Update the roster item in storage with the information contained in the given item
     * (optional operation).<p>
     *
     * If you don't want roster items edited through openfire, throw UnsupportedOperationException.
     *
     * @param username the username of the user/chatbot that owns the roster item
     * @param item   The roster item to update
     * @throws UserNotFoundException If no entry could be found to update
     */
    void updateItem(String username, RosterItem item)
            throws UserNotFoundException;

    /**
    * Delete the roster item with the given itemJID for the user (optional operation).<p>
    *
    * If you don't want roster items deleted through openfire, throw
    * UnsupportedOperationException.
    *
    * @param username the long ID of the user/chatbot that owns the roster item
    * @param rosterItemID The roster item to delete
    */
    void deleteItem(String username, long rosterItemID);

    /**
    * Returns an iterator on the usernames whose roster includes the specified JID.
    *
    * @param jid the jid that the rosters should include.
    * @return an iterator on the usernames whose roster includes the specified JID.
    */
    Iterator<String> getUsernames(String jid);

    /**
    * Obtain a count of the number of roster items available for the given user.
    *
    * @param username the username of the user/chatbot that owns the roster items
    * @return The number of roster items available for the user
    */
    int getItemCount(String username);

    /**
    * Retrieve an iterator of RosterItems for the given user.<p>
    *
    * This method will commonly be called when a user logs in. The data will be cached
    * in memory when possible. However, some rosters may be very large so items may need
    * to be retrieved from the provider more frequently than usual for provider data.
    *
    * @param username the username of the user/chatbot that owns the roster items
    * @return An iterator of all RosterItems owned by the user
	*/
	Iterator<RosterItem> getItems(String username);

}