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
package org.jivesoftware.messenger.chatbot.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LongList;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.*;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;

import java.util.*;

public class ChatbotManagerImpl extends BasicModule implements ChatbotManager {

    private ChatbotAccountProvider accountProvider = ChatbotProviderFactory.getChatbotAccountProvider();
    private ChatbotIDProvider idProvider = ChatbotProviderFactory.getChatbotIDProvider();
    private ChatbotInfoProvider infoProvider = ChatbotProviderFactory.getChatbotInfoProvider();
    private Map id2botname = new HashMap<Integer,Object>();
    private Map id2botinfo = new HashMap<Integer,Object>();
    private Hashtable botname2id = new Hashtable();

    /**
     * Create a chatbot manager.
     */
    public ChatbotManagerImpl() {
        super("Chatbot Manager");
        LongList bots = idProvider.getChatbotIDs();
        for (int i = 0; i < bots.size(); i++) {
            try {
                long botID = bots.get(i);
                id2botname.put(botID, idProvider.getUsername(botID));
                botname2id.put(id2botname.get(botID), new Long(botID));
            }
            catch (UserNotFoundException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    public long createChatbot(String username) throws UserAlreadyExistsException, UnauthorizedException {
        long botID = accountProvider.createChatbot(username);
        id2botname.put(botID, username);
        botname2id.put(username, new Long(botID));
        return botID;
    }

    public String getChatbotUsername(long chatbotID) throws UserNotFoundException {
        return (String)id2botname.get(chatbotID);
    }

    public long getChatbotID(String username) throws UserNotFoundException {
        Long id = (Long)botname2id.get(username);
        if (id == null) {
            throw new UserNotFoundException(username);
        }
        return id.longValue();
    }

    public boolean isChatbot(XMPPAddress address) {
        return botname2id.containsKey(address.getNamePrep());
    }

    public ChatbotInfo getChatbotInfo(long chatbotID) throws UserNotFoundException, UnsupportedOperationException {
        if (!id2botinfo.containsKey(chatbotID)) {
            id2botinfo.put(chatbotID, infoProvider.getInfo(chatbotID));
        }
        return (ChatbotInfo)id2botinfo.get(chatbotID);
    }

    public void setChatbotInfo(long chatbotID, ChatbotInfo info) throws UserNotFoundException, UnsupportedOperationException, UnauthorizedException {
        infoProvider.updateInfo(chatbotID, info);
        id2botinfo.put(chatbotID, info);
    }

    public void deleteChatbot(long chatbotID) throws UnauthorizedException, UserNotFoundException {
        accountProvider.deleteChatbot(chatbotID);
        id2botinfo.remove(chatbotID);
        botname2id.remove(id2botname.get(chatbotID));
        id2botname.remove(chatbotID);
    }

    public int getChatbotCount() {
        return id2botname.size();
    }

    public LongList chatbots() {
        return idProvider.getChatbotIDs();
    }

    public LongList chatbots(int startIndex, int numResults) {
        return idProvider.getChatbotIDs(startIndex, numResults);
    }
}