/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chat;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represent the data model for one <code>ChatRoom</code> history.
 * Including chat transcript, joining and leaving times.
 */
public final class ChatRoomHistory {
    private String roomname;
    private HistoryStrategy history;
    private Map userJoinMap = new LinkedHashMap();
    private Map userLeftMap = new LinkedHashMap();
    private long startTime;
    private long endTime;
    private long waitTime;
    private int state;

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    private long sessionID;

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    private String userID;

    public ChatRoomHistory(String name) {
        setRoomname(name);
    }


    public void setRoomname(String roomname) {
        this.roomname = roomname;
    }


    public String getRoomname() {
        return roomname;
    }

    public void setHistory(HistoryStrategy history) {
        this.history = history;
    }


    public HistoryStrategy getHistory() {
        return history;
    }

    public void userJoined(ChatUser user, Date timeJoined) {
        userJoinMap.put(user, timeJoined);
    }

    public void userLeft(ChatUser user, Date timeLeft) {
        userLeftMap.put(user, timeLeft);
    }


    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }


    public long getStartTime() {
        return startTime;
    }


    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }


    public long getEndTime() {
        return endTime;
    }

    public long getSessionID() {
        return sessionID;
    }

    public void setSessionID(long sessionID) {
        this.sessionID = sessionID;
    }

    public Map getJoinedMap() {
        return userJoinMap;
    }

    public Map getLeftMap() {
        return userLeftMap;
    }


    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }


}