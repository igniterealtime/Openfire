/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
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
