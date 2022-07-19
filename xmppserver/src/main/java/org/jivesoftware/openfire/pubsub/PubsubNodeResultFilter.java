/*
 * Copyright (C) 2022 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Filters and sorts lists of pubsub nodes.
 * <p>
 * The class also supports pagination of results with the setStartIndex(int)
 * and setNumResults(int) methods. If the start index is not set, it will
 * begin at index 0 (the start of results). If the number of results is not set,
 * it will be unbounded and return as many results as available.</p>
 * <p>
 * Factory methods to create common queries are provided for convenience.</p>
 *
 * @author Guus der Kinderen
 * @see org.jivesoftware.openfire.SessionResultFilter
 */
public class PubsubNodeResultFilter
{
    private static final Logger Log = LoggerFactory.getLogger(PubsubNodeResultFilter.class);

    // ############################################################
    // Search order criteria
    // ############################################################
    /**
     * Descending sort (ie 3, 2, 1...).
     */
    public static final int DESCENDING = 0;

    /**
     * Ascending sort (ie 3, 4, 5...).
     */
    public static final int ASCENDING = 1;

    // ############################################################
    // Result limit search criteria
    // ############################################################
    /**
     * Represents no result limit (infinite results).
     */
    public static final int NO_RESULT_LIMIT = -1;

    // ############################################################
    // Sort fields
    // ############################################################
    public static final int SORT_NODE_IDENTIFIER = 0;
    public static final int SORT_NODE_NAME = 1;
    public static final int SORT_NODE_DESCRIPTION = 2;
    public static final int SORT_ITEM_COUNT = 3;
    public static final int SORT_AFFILIATE_COUNT = 4;
    public static final int SORT_SUBSCRIBER_COUNT = 5;

    // ############################################################
    // Filter fields
    // ############################################################
    public static final String FILTER_NODE_IDENTIFIER = "searchNodeId";
    public static final String FILTER_NODE_NAME = "searchNodeName";
    public static final String FILTER_NODE_DESCRIPTION = "searchNodeDescription";
    public static final String FILTER_ITEM_COUNT = "searchItemCount";
    public static final String FILTER_AFFILIATE_COUNT = "searchAffiliateCount";
    public static final String FILTER_SUBSCRIBER_COUNT = "searchSubscriberCount";

    /**
     * Creates a default PubsubNodeResultFilter: no filtering with results sorted
     * by node ID (ascending).
     *
     * @return default PubsubNodeResultFilter.
     */
    public static PubsubNodeResultFilter createDefaultSessionFilter() {
        PubsubNodeResultFilter resultFilter = new PubsubNodeResultFilter();
        resultFilter.setSortColumnNumber(SORT_NODE_IDENTIFIER);
        resultFilter.setSortOrder(ASCENDING);
        return resultFilter;
    }

    private int sortColumnNumber = SORT_NODE_IDENTIFIER;
    private int sortOrder = ASCENDING;

    /**
     * The starting index for results. Default is 0.
     */
    private int startIndex = 0;

    /**
     * Number of results to return. Default is {@link #NO_RESULT_LIMIT} which means an unlimited number of results.
     */
    private int numResults = NO_RESULT_LIMIT;

    /**
     * The default filter applied to limit the results that are returned. By default, all results are returned.
     */
    private Predicate<Node> filter = node -> true;

    /**
     * Returns the currently selected sort field. The default value is {@link #SORT_NODE_IDENTIFIER}.
     *
     * @return current sort field.
     */
    public int getSortColumnNumber() {
        return sortColumnNumber;
    }

    /**
     * Sets the sort field to use.
     *
     * @param sortColumnNumber the field that will be used for sorting.
     */
    public void setSortColumnNumber(int sortColumnNumber) {
        this.sortColumnNumber = sortColumnNumber;
    }

    /**
     * Returns the sort order, which will be {@link #ASCENDING} for ascending sorting, or {@link #DESCENDING} for
     * descending sorting. The default value is {@link #ASCENDING}.
     *
     * Descending sorting is: 3, 2, 1, etc. Ascending sorting is 1, 2, 3, etc.
     *
     * @return the sort order.
     */
    public int getSortOrder() {
        return this.sortOrder;
    }

    /**
     * Sets the sort order. Valid arguments are {@link #ASCENDING} for ascending sorting, and {@link #DESCENDING} for
     * descending sorting.
     *
     * Descending sorting is: 3, 2, 1, etc. Ascending sorting is 1, 2, 3, etc.
     *
     * @param sortOrder the order that results will be sorted in.
     */
    public void setSortOrder(int sortOrder) {
        if (!(sortOrder == PubsubNodeResultFilter.ASCENDING || sortOrder == PubsubNodeResultFilter.DESCENDING)) {
            throw new IllegalArgumentException();
        }
        this.sortOrder = sortOrder;
    }

    /**
     * Returns the max number of results that should be returned.
     *
     * The default value for is {@link #NO_RESULT_LIMIT}, which means there will be no limit on the number of results.
     * This method can be used in combination with {@link #setStartIndex(int)} to perform pagination of results.
     *
     * @return the max number of results to return or NO_RESULT_LIMIT for no limit
     * @see #setStartIndex(int)
     */
    public int getNumResults() {
        return numResults;
    }

    /**
     * Sets the limit on the number of results to be returned.
     *
     * Use {@link #NO_RESULT_LIMIT} if you don't want to limit the results returned.
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
     * Sets the index of the first result to return. For example, if the start index is set to 20, the Iterator returned
     * will start at the 20th result in the query. This method can be used in combination with
     * {@link #setNumResults(int)} to perform pagination of results.
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
     * Returns a comparator that will sort a standard sorted set according to this filter's sort order.
     *
     * @return a comparator that sorts Sessions matching the sort order for this filter.
     */
    public Comparator<Node> getSortComparator() {
        final Comparator<Node> result;
        switch(sortColumnNumber) {
            default:
                Log.warn("Unrecognized console.orderBy value: '{}'. Defaulting to 'NODE_IDENTIFIER'.", sortColumnNumber);
                // intended fall-through;
            case SORT_NODE_IDENTIFIER:
                result = Comparator.comparing(node -> node.getUniqueIdentifier().getNodeId().toLowerCase());
                break;
            case SORT_NODE_NAME:
                result = Comparator.comparing(node -> (node.getName() == null ? "" : node.getName()).toLowerCase());
                break;
            case SORT_NODE_DESCRIPTION:
                result = Comparator.comparing(node -> (node.getDescription() == null ? "" : node.getDescription()).toLowerCase());
                break;
            case SORT_ITEM_COUNT:
                result = Comparator.comparing(node -> node.getPublishedItems().size());
                break;
            case SORT_AFFILIATE_COUNT:
                result = Comparator.comparing(node -> node.getAllAffiliates().size());
                break;
            case SORT_SUBSCRIBER_COUNT:
                result = Comparator.comparing(node -> node.getAllSubscriptions().size());
                break;
        }
        if (sortOrder == DESCENDING) {
            // Natural ordering needs to be reversed.
            return result.reversed();
        } else {
            return result;
        }
    }

    /**
     * Generates a collection of all parameter names that can be used for filtering.
     *
     * @return all filter parameter names.
     */
    public String[] getFilterParams()
    {
        return new String[] {
            FILTER_NODE_IDENTIFIER,
            FILTER_NODE_NAME,
            FILTER_NODE_DESCRIPTION,
            FILTER_ITEM_COUNT,
            FILTER_AFFILIATE_COUNT,
            FILTER_SUBSCRIBER_COUNT
        };
    }

    /**
     * Adds a new filter to limit the results that are returned.
     *
     * The existing filter conditions are augmented with a new condition. Only results for which the value of a
     * particular parameter matches the provided filter conditions will be result.
     *
     * @param filterParam The filter parameter name.
     * @param filterValue The filter paramter value.
     */
    public void addFilter(String filterParam, String filterValue)
    {
        if (filterParam.isEmpty() || filterValue.isEmpty()) {
            return;
        }

        switch (filterParam) {
            case FILTER_NODE_IDENTIFIER:
                filter = filter.and(node -> node.getUniqueIdentifier().getNodeId().toLowerCase().contains(filterValue.trim().toLowerCase()));
                break;

            case FILTER_NODE_NAME:
                filter = filter.and(node -> node.getName().toLowerCase().contains(filterValue.trim().toLowerCase()));
                break;

            case FILTER_NODE_DESCRIPTION:
                filter = filter.and(node -> node.getDescription().toLowerCase().contains(filterValue.trim().toLowerCase()));
                break;

            case FILTER_ITEM_COUNT:
                filter = filter.and(node -> node.getPublishedItems().size() == Integer.parseInt(filterValue.trim()));
                break;

            case FILTER_AFFILIATE_COUNT:
                filter = filter.and(node -> node.getAllAffiliates().size() == Integer.parseInt(filterValue.trim()));
                break;

            case FILTER_SUBSCRIBER_COUNT:
                filter = filter.and(node -> node.getAllSubscriptions().size() == Integer.parseInt(filterValue.trim()));
                break;

            default:
                Log.warn("Unrecognized filter: '{}' with value: '{}'", filterParam, filterValue);
        }
    }

    /**
     * Returns the filter that limits the results that are returned. By default, all results are returned.
     *
     * @return a predicate used for filtering results.
     */
    public Predicate<Node> getFilter()
    {
        return filter;
    }
}
