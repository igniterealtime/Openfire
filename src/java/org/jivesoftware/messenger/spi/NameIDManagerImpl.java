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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.NameIDManager;
import org.jivesoftware.messenger.chatbot.spi.ChatbotManagerImpl;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;

public class NameIDManagerImpl extends BasicModule implements NameIDManager {
    protected Cache id2userCache;
    protected Cache name2idCache;
    protected ChatbotManagerImpl chatbotManager = new ChatbotManagerImpl();

    /**
     * Create a basic module with a set name.
     */
    public NameIDManagerImpl() {
        super("Name ID Manager");
    }

    public String getUsername(long id) throws UserNotFoundException {
        if (id2userCache == null) {
            id2userCache = CacheManager.getCache("userid2user");
        }
        User user = (User)id2userCache.get(new Long(id));
        if (user == null) {
            return chatbotManager.getChatbotUsername(id);
        }
        else {
            return user.getUsername();
        }
    }

    public long getID(String username) throws UserNotFoundException {
        if (name2idCache == null) {
            name2idCache = CacheManager.getCache("username2userid");
        }
        Long id = (Long)name2idCache.get(username);
        if (id == null) {
            return chatbotManager.getChatbotID(username);
        }
        else {
            return id.longValue();
        }
    }
}
