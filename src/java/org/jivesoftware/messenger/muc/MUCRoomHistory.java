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
package org.jivesoftware.messenger.muc;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.TimeZone;

import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.MetaDataFragment;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * Represent the data model for one <code>MUCRoom</code> history. Including chat transcript,
 * joining and leaving times.
 * 
 * @author Gaston Dombiak
 */
public final class MUCRoomHistory {

    private static final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");
    static {
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    private MUCRoom room;

    private HistoryStrategy history;

    private Map userJoinMap = new LinkedHashMap();

    private Map userLeftMap = new LinkedHashMap();

    private long startTime;

    private long endTime;

    private long waitTime;

    private int state;

    private boolean isNonAnonymousRoom;

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

    public MUCRoomHistory(MUCRoom mucRoom, HistoryStrategy historyStrategy) {
        this.room = mucRoom;
        this.isNonAnonymousRoom = mucRoom.canAnyoneDiscoverJID();
        setHistory(historyStrategy);
    }

    public String getRoomname() {
        return room.getName();
    }

    public void setHistory(HistoryStrategy history) {
        this.history = history;
    }

    public HistoryStrategy getHistory() {
        return history;
    }

    public void addMessage(Message packet) {
        // Don't keep messages whose sender is the room itself (thus address without resource)
        if (packet.getSender().getResourcePrep() == null
                || packet.getSender().getResourcePrep().length() == 0) {
            return;
        }
        Message packetToAdd = (Message) packet.createDeepCopy();

        // TODO Analyze concurrency (on the LinkList) when adding many messages simultaneously

        // Check if the room has changed its configuration
        if (isNonAnonymousRoom != room.canAnyoneDiscoverJID()) {
            isNonAnonymousRoom = room.canAnyoneDiscoverJID();
            // Update the "from" attribute of the delay information in the history
            Message message;
            MetaDataFragment frag;
            // TODO Make this update in a separate thread
            for (Iterator it = getMessageHistory(); it.hasNext();) {
                message = (Message) it.next();
                frag = (MetaDataFragment) message.getFragment("x", "jabber:x:delay");
                if (room.canAnyoneDiscoverJID()) {
                    // Set the Full JID as the "from" attribute
                    try {
                        MUCRole role = room.getOccupant(message.getSender().getResourcePrep());
                        frag.setProperty("x:from", role.getChatUser().getAddress().toStringPrep());
                    }
                    catch (UserNotFoundException e) {
                    }
                }
                else {
                    // Set the Room JID as the "from" attribute
                    frag.setProperty("x:from", message.getSender().toStringPrep());
                }
            }

        }

        // Add the delay information to the message
        MetaDataFragment delayInformation = new MetaDataFragment("jabber:x:delay", "x");
        Date current = new Date();
        delayInformation.setProperty("x:stamp", UTC_FORMAT.format(current));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            try {
                MUCRole role = room.getOccupant(packet.getSender().getResourcePrep());
                delayInformation.setProperty("x:from", role.getChatUser().getAddress()
                        .toStringPrep());
            }
            catch (UserNotFoundException e) {
            }
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.setProperty("x:from", packet.getSender().toStringPrep());
        }
        packetToAdd.addFragment(delayInformation);
        history.addMessage(packetToAdd);
    }

    public Iterator getMessageHistory() {
        return history.getMessageHistory();
    }

    /**
     * Obtain the current history to be iterated in reverse mode. This means that the returned list
     * iterator will be positioned at the end of the history so senders of this message must
     * traverse the list in reverse mode.
     * 
     * @return A list iterator of Message objects positioned at the end of the list.
     */
    public ListIterator getReverseMessageHistory() {
        return history.getReverseMessageHistory();
    }

    public void userJoined(MUCUser user, Date timeJoined) {
        userJoinMap.put(user, timeJoined);
    }

    public void userLeft(MUCUser user, Date timeLeft) {
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