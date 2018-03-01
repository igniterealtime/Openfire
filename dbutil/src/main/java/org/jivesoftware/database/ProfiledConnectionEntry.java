/**
 * $RCSfile$
 * $Revision: 37 $
 * $Date: 2004-10-20 23:08:43 -0700 (Wed, 20 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

/**
 * Simple class for tracking profiling stats for individual SQL queries.
 *
 * @author Jive Software
 */
public class ProfiledConnectionEntry {
    /**
     * The SQL query.
     */
    public String sql;

    /**
     * Number of times the query has been executed.
     */
    public int count;

    /**
     * The total time spent executing the query (in milliseconds).
     */
    public int totalTime;

    public ProfiledConnectionEntry(String sql) {
        this.sql = sql;
        count = 0;
        totalTime = 0;
    }
}
