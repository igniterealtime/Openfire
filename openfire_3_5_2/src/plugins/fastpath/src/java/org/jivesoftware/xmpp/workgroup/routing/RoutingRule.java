/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 * 
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.routing;

/**
 * Defines a routing rule.
 *
 * @author Derek DeMoro
 */
public class RoutingRule {

    private long queueID;
    private String query;
    private int position;

    public RoutingRule(long queueID, int position, String query){
        this.queueID = queueID;
        this.position = position;
        this.query = query;
    }

    public long getQueueID() {
        return queueID;
    }

    public void setQueueID(long queueID) {
        this.queueID = queueID;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

}