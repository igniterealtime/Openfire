/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import java.io.Serializable;

/**
 * Used to return multiple service matches.
 *
 * @author Iain Shigeoka
 */
public class ServiceMatches implements Serializable {
    /**
     * The items that matched.
     */
    public ServiceItem[] items;
    /**
     * The total number of items that matched
     */
    public int totalMatches;
    /**
     * Serializable id. Increment whenever class signature changes.
     */
    private static final long serialVersionUID = 1;

    /**
     * Create an empty match result.
     */
    public ServiceMatches() {
        items = new ServiceItem[]{
        };
        totalMatches = 0;
    }

    /**
     * Create a match result with predefined settings.
     *
     * @param itemList   the items found.
     * @param matchCount the number of matches found.
     */
    public ServiceMatches(ServiceItem[] itemList, int matchCount) {
        if (itemList == null) {
            items = new ServiceItem[]{};
            totalMatches = 0;
        }
        else {
            items = itemList;
        }
        this.totalMatches = matchCount;
    }
}
