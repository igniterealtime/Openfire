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

package org.jivesoftware.messenger.chatbot;

import org.jivesoftware.util.LongList;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * <p>Manages chatbots on the server.</p>
 * <p/>
 * <p>Chatbots are now first class user entities without some/all of the standard user resoucres.
 * The chatbot manager acts like the user manager to organize chatbot activities. Note that
 * Chatbot management only pertains to a chatbot's persistent resources such as private storage,
 * username reservation (ensuring a unique username and userID on the server), etc. Creating a
 * chatbot does not automatically make them available for routing (does not register entries in
 * the routing table), does not automatically store offline messages, etc. It is up to the chatbot
 * service to act as the active entity in the server (by registering with the routing table and
 * correctly handling packets).</p>
 *
 * @author Iain Shigeoka
 */
public interface ChatbotManager {
    /**
     * <p>Factory method for creating a new Chatbot by reserving a username and ID for the chatbot (optional operation).</p>
     * <p/>
     * <p>Although specific chatbot services MUST reserve chatbot names through this method,
     * it is also useful for admins to reserve certain usernames as chatbots without
     * creating a chatbot to act on behalf of that name. This prevents
     * anyone from creating user accounts with the given username. (Creating 'dummy' accounts
     * for this purpose creates the potential potential that the dummy account can be logged
     * into and used. In addition usernames reserved for chatbots will not show up in user
     * reports and statistics).
     * For example, swear/racist/slang words, company trademark names, names that imply
     * associations with the company, etc. should all be reserved as unused chatbot names.</p>
     *
     * @param username the new and unique username to reserve for a chatbot.
     * @return the chatbot ID reserved for that name.
     * @throws UserAlreadyExistsException    if the username already exists in the system.
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public long createChatbot(String username)
            throws UserAlreadyExistsException, UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Deletes a Chatbot username reservation (optional operation).</p>
     * <p>Note: it is dangerous to delete username reservations for chatbots you did not create
     * through this interface. E.g. if you created chatbots strictly to reserve their names,
     * then it's ok to delete them, but if a chatbot name was reserved for a Live Assistant
     * workgroup chatbot by the Live Assistante WorkgroupManager and it is deleted outside of
     * the WorkgroupManager, another user or chatbot could 'steal' its username.</p>
     *
     * @param chatbotID the ID of the Chatbot to delete.
     * @throws UnauthorizedException
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void deleteChatbot(long chatbotID) throws UnauthorizedException, UserNotFoundException, UnsupportedOperationException;

    /**
     * Returns a Chatbot specified by their ID.
     *
     * @param chatbotID the id of the Chatbot to lookup.
     * @return the Chatbot's username.
     * @throws UserNotFoundException if the Chatbot does not exist.
     */
    public String getChatbotUsername(long chatbotID) throws UserNotFoundException;

    /**
     * Returns the ChatbotID specified by the username.
     *
     * @param username the username of the user.
     * @return the ChatbotID that matches username.
     *         <p/>
     *
     * @throws UserNotFoundException if the Chatbot does not exist.
     */
    public long getChatbotID(String username) throws UserNotFoundException;


    /**
     * <p>Determines if the given address is a chatbot on the server.</p>
     *
     * @param address The address to check for chatbot-ness
     * @return True if the address corresponds to a chatbot on the server
     */
    boolean isChatbot(XMPPAddress address);

    /**
     * <p>Obtain supplemental information about a chatbot (optional operation).</p>
     * <p/>
     * <p>Primarily for use in admin interfaces.</p>
     *
     * @param chatbotID The id of the chatbot to obtain information
     * @return An information object about the chatbot
     * @throws UserNotFoundException
     * @throws UnsupportedOperationException
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     *
     */
    public ChatbotInfo getChatbotInfo(long chatbotID) throws UserNotFoundException, UnsupportedOperationException;

    /**
     * <p>Sets supplemental information about a chatbot (optional operation).</p>
     * <p/>
     * <p>Primarily for use in admin interfaces.</p>
     *
     * @param chatbotID The id of the chatbot to obtain information
     * @param info      An information object about the chatbot
     * @throws UserNotFoundException
     * @throws UnsupportedOperationException
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void setChatbotInfo(long chatbotID, ChatbotInfo info) throws UserNotFoundException, UnsupportedOperationException, UnauthorizedException;

    /**
     * Returns the number of Chatbots in the system.
     *
     * @return the total number of Chatbots.
     */
    public int getChatbotCount();

    /**
     * Returns a list of the IDs of all Chatbots in the system.
     *
     * @return a list of all chat IDs (longs) in the system
     */
    public LongList chatbots();

    /**
     * Returns an list of all the IDs of all Chatbots starting at <tt>startIndex</tt> with the given number of
     * results. This is useful to support pagination in a GUI where you may only want to display a
     * certain number of results per page. It is possible that the number of results returned will
     * be less than that specified by numResults if numResults is greater than the number of records
     * left in the system to display.
     *
     * @param startIndex the beginning index to start the results at.
     * @param numResults the total number of results to return.
     * @return a list of all chat IDs (longs) in the system
     */
    public LongList chatbots(int startIndex, int numResults);
}
