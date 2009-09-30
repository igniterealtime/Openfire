/**
 * $RCSfile$
 * $Revision: 18406 $
 * $Date: 2005-02-07 14:32:46 -0800 (Mon, 07 Feb 2005) $
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

package org.jivesoftware.xmpp.workgroup;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * <p>
 * Filters workgroup listings for display in GUIs.
 * </p><p>
 * Currently a pretty just a limited ability to set the start index and number of results
 * for paginating results in GUIs.
 * </p>
 *
 * @author Derek DeMoro
 */
public class WorkgroupResultFilter {

    /**
     * Indicates that no result limit should be enforced.
     */
    public static final int NO_RESULT_LIMIT = -1;

    /**
     * Empty constructor creating a default filter with start index 0, and number of
     * results unlimited.
     */
    public WorkgroupResultFilter() {

    }

    /**
     * Constructor that sets the start index 0, and number of results.
     *
     * @param startIndex The start index for the results
     * @param numResults The maximum number of workgroups to include in the result
     */
    public WorkgroupResultFilter(int startIndex, int numResults) {
        this.startIndex = startIndex;
        this.numResults = numResults;
    }

    /**
     * The starting index for results. Default is 0.
     */
    private int startIndex = 0;

    /**
     * Number of results to return. Default is NO_RESULT_LIMIT
     * which means an unlimited number of results.
     */
    private int numResults = NO_RESULT_LIMIT;

    /**
     * Returns the max number of results that should be returned.
     * The default value for is NO_RESULT_LIMIT, which means there will be no limit
     * on the number of results. This method can be used in combination with
     * setStartIndex(int) to perform pagination of results.
     *
     * @return the max number of results to return or NO_RESULT_LIMIT for no limit.
     * @see #setStartIndex(int)
     */
    public int getNumResults() {
        return numResults;
    }

    /**
     * Sets the limit on the number of results to be returned.
     * User NO_RESULT_LIMIT if you don't want to limit the results returned.
     *
     * @param numResults the number of results to return or NO_RESULT_LIMIT for no limit
     */
    public void setNumResults(int numResults) {
        if (numResults != NO_RESULT_LIMIT && numResults < 0) {
            throw new IllegalArgumentException("numResults cannot be less than 0.");
        }
        this.numResults = numResults;
    }

    /**
     * Returns the index of the first result to return.
     *
     * @return the index of the first result which should be returned.
     */
    public int getStartIndex() {
        return startIndex;
    }

    /**
     * Sets the index of the first result to return. For example, if the start
     * index is set to 20, the Iterator returned will start at the 20th result
     * in the query. This method can be used in combination with
     * setNumResults(int) to perform pagination of results.
     *
     * @param startIndex the index of the first result to return.
     */
    public void setStartIndex(int startIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("A start index less than 0 is not valid.");
        }
        this.startIndex = startIndex;
    }

    /**
     * Filters the raw results according to it's current settings and returns an
     * iterator over the result.
     *
     * @param rawResults Iterator over all support group members
     * @return Iterator over group members fitting the current filter settings
     */
    public synchronized Iterator filter(Iterator rawResults) {
        Iterator result = null;
        if (startIndex == 0 && numResults == NO_RESULT_LIMIT) {
            result = rawResults;
        }
        else {
            LinkedList list = new LinkedList();
            while (rawResults.hasNext() && startIndex-- > 0) {
                rawResults.next(); // skip over first x entries
            }
            if (numResults == NO_RESULT_LIMIT) {
                while (rawResults.hasNext()) {
                    list.add(rawResults.next());
                }
            }
            else {
                for (int i = 0; rawResults.hasNext() && i < numResults; i++) {
                    list.add(rawResults.next());
                }
            }
            result = list.iterator();
        }
        return result;
    }
}
