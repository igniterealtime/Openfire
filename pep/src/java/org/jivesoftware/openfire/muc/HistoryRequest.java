/**
 * $RCSfile: HistoryRequest.java,v $
 * $Revision: 2899 $
 * $Date: 2005-09-28 15:30:42 -0300 (Wed, 28 Sep 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimeZone;

import org.jivesoftware.openfire.muc.spi.MUCRoleImpl;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.FastDateFormat;
import org.dom4j.Element;
import org.xmpp.packet.Message;

/**
 * Represents the amount of history requested by an occupant while joining a room. There are 
 * basically four ways to control the amount of history that a user may receive. Those are: limit
 * by the maximum limit of characters to receive, limit by a maximum number of stanzas to receive,
 * limit to receive only the messages before a given date or of the last X seconds.<p>
 * 
 * A user may combine any of these four methods. The idea is that the user will receive the smallest 
 * amount of traffic so the amount of history to collect will stop as soon as any of the requested 
 * method has reached its limit.
 * 
 * @author Gaston Dombiak
 */
public class HistoryRequest {

    private static final DateFormat formatter = new SimpleDateFormat(JiveConstants.XMPP_DATETIME_FORMAT);
    private static final DateFormat delayedFormatter = new SimpleDateFormat(
            JiveConstants.XMPP_DELAY_DATETIME_FORMAT);
    static {
        delayedFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private int maxChars = -1;
    private int maxStanzas = -1;
    private int seconds = -1;
    private Date since;

    public HistoryRequest(Element userFragment) {
        Element history = userFragment.element("history");
        if (history != null) {
            if (history.attribute("maxchars") != null) {
                this.maxChars = Integer.parseInt(history.attributeValue("maxchars"));
            }
            if (history.attribute("maxstanzas") != null) {
                this.maxStanzas = Integer.parseInt(history.attributeValue("maxstanzas"));
            }
            if (history.attribute("seconds") != null) {
                this.seconds = Integer.parseInt(history.attributeValue("seconds"));
            }
            if (history.attribute("since") != null) {
                try {
                    // parse utc into Date
                    synchronized (formatter) {
                        this.since = formatter.parse(history.attributeValue("since"));
                    }
                }
                catch(ParseException pe) {
                    Log.error("Error parsing date from history management", pe);
                    this.since = null;
                }
            }
        }
    }
    
    /**
     * Returns the total number of characters to receive in the history.
     * 
     * @return total number of characters to receive in the history.
     */
    public int getMaxChars() {
        return maxChars;
    }

    /**
     * Returns the total number of messages to receive in the history.
     * 
     * @return the total number of messages to receive in the history.
     */
    public int getMaxStanzas() {
        return maxStanzas;
    }

    /**
     * Returns the number of seconds to use to filter the messages received during that time. 
     * In other words, only the messages received in the last "X" seconds will be included in 
     * the history.
     * 
     * @return the number of seconds to use to filter the messages received during that time.
     */
    public int getSeconds() {
        return seconds;
    }

    /**
     * Returns the since date to use to filter the messages received during that time. 
     * In other words, only the messages received since the datetime specified will be 
     * included in the history.
     * 
     * @return the since date to use to filter the messages received during that time.
     */
    public Date getSince() {
        return since;
    }

    /**
     * Returns true if the history has been configured with some values.
     * 
     * @return true if the history has been configured with some values.
     */
    private boolean isConfigured() {
        return maxChars > -1 || maxStanzas > -1 || seconds > -1 || since != null;
    }

    /**
     * Sends the smallest amount of traffic that meets any combination of the requested criteria.
     * 
     * @param joinRole the user that will receive the history.
     * @param roomHistory the history of the room.
     */
    public void sendHistory(MUCRoleImpl joinRole, MUCRoomHistory roomHistory) {
        if (!isConfigured()) {
            Iterator history = roomHistory.getMessageHistory();
            while (history.hasNext()) {
                joinRole.send((Message) history.next());
            }
        }
        else {
            if (getMaxChars() == 0) {
                // The user requested to receive no history
                return;
            }
            Message message;
            int accumulatedChars = 0;
            int accumulatedStanzas = 0;
            Element delayInformation;
            LinkedList historyToSend = new LinkedList();
            ListIterator iterator = roomHistory.getReverseMessageHistory();
            while (iterator.hasPrevious()) {
                message = (Message)iterator.previous();
                // Update number of characters to send
                String text = message.getBody() == null ? message.getSubject() : message.getBody();
                if (text == null) {
                    // Skip this message since it has no body and no subject  
                    continue;
                }
                accumulatedChars += text.length();
                if (getMaxChars() > -1 && accumulatedChars > getMaxChars()) {
                    // Stop collecting history since we have exceded a limit
                    break;
                }
                // Update number of messages to send
                accumulatedStanzas ++;
                if (getMaxStanzas() > -1 && accumulatedStanzas > getMaxStanzas()) {
                    // Stop collecting history since we have exceded a limit
                    break;
                }

                if (getSeconds() > -1 || getSince() != null) {
                    delayInformation = message.getChildElement("x", "jabber:x:delay");
                    try {
                        // Get the date when the historic message was sent
                        Date delayedDate = null;
                        synchronized (delayedFormatter) {
                            delayedDate = delayedFormatter
                                    .parse(delayInformation.attributeValue("stamp"));
                        }
                        if (getSince() != null && delayedDate.before(getSince())) {
                            // Stop collecting history since we have exceded a limit
                            break;
                        }
                        if (getSeconds() > -1) {
                            Date current = new Date();
                            long diff = (current.getTime() - delayedDate.getTime()) / 1000;
                            if (getSeconds() <= diff) {
                                // Stop collecting history since we have exceded a limit
                                break;
                            }
                        }
                    }
                    catch (Exception e) {
                        Log.error("Error parsing date from historic message", e);
                    }

                }

                historyToSend.addFirst(message);
            }
            // Send the smallest amount of traffic to the user
            Iterator history = historyToSend.iterator();
            while (history.hasNext()) {
                joinRole.send((Message) history.next());
            }
        }
    }
}
