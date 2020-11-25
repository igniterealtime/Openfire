/*
 * Copyright (C) 2004 Jive Software. All rights reserved.
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
    public long totalTime;

    public ProfiledConnectionEntry(String sql) {
        this.sql = sql;
        count = 0;
        totalTime = 0;
    }
}
