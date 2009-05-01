/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.archive;

import org.jivesoftware.util.StringUtils;

/**
 *
 */
public class ConversationInfo {

    private long conversationID;
    private String participant1;
    private String participant2;
    /**
     * For group converstion we need to send a string array with the occupants' JIDs.
     */
    private String[] allParticipants;
    private String date;
    private String lastActivity;
    private String body;
    private int messageCount;
    private long duration;

    public long getConversationID() {
        return conversationID;
    }

    public void setConversationID(long conversationID) {
        this.conversationID = conversationID;
    }

    public String getParticipant1() {
        return participant1;
    }

    public void setParticipant1(String participant1) {
        this.participant1 = participant1;
    }

    public String getParticipant2() {
        return participant2;
    }

    public void setParticipant2(String participant2) {
        this.participant2 = participant2;
    }

    public String[] getAllParticipants() {
        return allParticipants;
    }

    public void setAllParticipants(String[] allParticipants) {
        this.allParticipants = allParticipants;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public String getDuration() {
        return StringUtils.getTimeFromLong(duration);
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(String lastActivity) {
        this.lastActivity = lastActivity;
    }

}
