/**
 * $RCSfile: HistoryRequest.java,v $
 * $Revision: 2899 $
 * $Date: 2005-09-28 15:30:42 -0300 (Wed, 28 Sep 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.muc;

import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import org.dom4j.Element;
import org.jivesoftware.openfire.muc.spi.LocalMUCRole;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger Log = LoggerFactory.getLogger(HistoryRequest.class);
	private static final XMPPDateTimeFormat xmppDateTime = new XMPPDateTimeFormat();

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
                    // parse since String into Date
                    this.since = xmppDateTime.parseString(history.attributeValue("since"));
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
    public void sendHistory(LocalMUCRole joinRole, MUCRoomHistory roomHistory) {
        if (!isConfigured()) {
            Iterator<Message> history = roomHistory.getMessageHistory();
            while (history.hasNext()) {
                joinRole.send(history.next());
            }
        }
        else {
            Message changedSubject = roomHistory.getChangedSubject();
            boolean addChangedSubject = (changedSubject != null) ? true : false;
            if (getMaxChars() == 0) {
                // The user requested to receive no history
                if (addChangedSubject) {
                    joinRole.send(changedSubject);
                }
                return;
            }
            int accumulatedChars = 0;
            int accumulatedStanzas = 0;
            Element delayInformation;
            LinkedList<Message> historyToSend = new LinkedList<Message>();
            ListIterator<Message> iterator = roomHistory.getReverseMessageHistory();
            while (iterator.hasPrevious()) {
                Message message = iterator.previous();
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
                        Date delayedDate = xmppDateTime.parseString(delayInformation.attributeValue("stamp"));
                        if (getSince() != null && delayedDate != null && delayedDate.before(getSince())) {
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

                // Don't add the latest subject change if it's already in the history.
                if (addChangedSubject) {
                    if (changedSubject != null && changedSubject.equals(message)) {
                        addChangedSubject = false;
                    }
                }

                historyToSend.addFirst(message);
            }
            // Check if we should add the latest subject change.
            if (addChangedSubject) {
                historyToSend.addFirst(changedSubject);
            }
            // Send the smallest amount of traffic to the user
            for (Object aHistoryToSend : historyToSend) {
                joinRole.send((Message) aHistoryToSend);
            }
        }
    }
}
