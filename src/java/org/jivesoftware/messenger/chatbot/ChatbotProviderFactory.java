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

import org.jivesoftware.messenger.chatbot.spi.DbChatbotAccountProvider;
import org.jivesoftware.messenger.chatbot.spi.DbChatbotIDProvider;
import org.jivesoftware.messenger.chatbot.spi.DbChatbotInfoProvider;

public class ChatbotProviderFactory {

    private static ChatbotIDProvider idProvider = new DbChatbotIDProvider();
    private static ChatbotAccountProvider accountProvider = new DbChatbotAccountProvider();
    private static ChatbotInfoProvider infoProvider = new DbChatbotInfoProvider();

    public static ChatbotIDProvider getChatbotIDProvider() {
        return idProvider;
    }

    public static ChatbotAccountProvider getChatbotAccountProvider() {
        return accountProvider;
    }

    public static ChatbotInfoProvider getChatbotInfoProvider() {
        return infoProvider;
    }
}
