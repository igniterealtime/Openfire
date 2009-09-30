/**
 * $RCSfile$
 * $Revision: 18915 $
 * $Date: 2005-05-17 10:07:18 -0700 (Tue, 17 May 2005) $
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

package org.jivesoftware.xmpp.workgroup.search;

import org.jivesoftware.xmpp.workgroup.Workgroup;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates a transcript search result resulting from a search using the ChatSearch class.
 * Relevance is only stored to 3 significant decimal places (xx.x%).<p>
 *
 * Use {@link #getSessionID()} to get the id of the session that originated the transcript
 * contained in the result.
 *
 * @author Gaston Dombiak
 */
public class QueryResult {

    private Workgroup workgroup;
    private String sessionID;
    private Date startDate;
    private List<String> agentJIDs;
    private float relevance;

    public QueryResult(Workgroup workgroup, String sessionID, Date startDate,
            List<String> agentJIDs, float relevance) {
        this.workgroup = workgroup;
        this.sessionID = sessionID;
        this.startDate = startDate;
        this.agentJIDs = agentJIDs;
        this.relevance = Math.round(relevance * 1000.0F) / 1000.0F;;
    }

    /**
     * Returns the workgroup where the chat support took place. Using the workgroup and the
     * sessionID it's possible for an agent to retrieve the complete chat transcript.
     *
     * @return the workgroup where the chat support took place.
     */
    public Workgroup getWorkgroup() {
        return workgroup;
    }

    /**
     * Returns the id of the session that originated the chat support. Use this id for
     * getting more information about the session. See
     * {@link org.jivesoftware.xmpp.workgroup.WorkgroupIQHandler#handleIQGet(org.xmpp.packet.IQ)}.
     *
     * @return the id of the session that originated the chat support.
     */
    public String getSessionID() {
        return sessionID;
    }

    /**
     * Returns the date when the chat support started.
     *
     * @return the date when the chat support started.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Returns the bare JID of the agents that were involved in the chat support. More than
     * one agent may have been involved in a chat transfer was done to another agent or a chat
     * invitation was sent to another agent.
     *
     * @return the bare JID of the agents that were involved in the chat support.
     */
    public List<String> getAgentJIDs() {
        return agentJIDs;
    }

    /**
     * Get the relevance of this query result as determined by the search engine.
     *
     * @return the relevance of this query result as determined by the search engine.
     */
    public float getRelevance() {
        return relevance;
    }

    /**
     * Returns the relevance as a percentage (no decimal places) formatted according
     * to the locale passed in.
     *
     * @param locale the locale to use to format the percentage.
     * @return the formatted percentage as a String.
     */
    public String getRelevanceAsPercentage(Locale locale) {
        NumberFormat format = DecimalFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(0);
        return format.format(relevance);
    }
}
