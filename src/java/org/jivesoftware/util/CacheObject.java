/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.util;

import org.jivesoftware.util.LinkedListNode;

/**
 * Wrapper for all objects put into cache. It's primary purpose is to maintain
 * references to the linked lists that maintain the creation time of the object
 * and the ordering of the most used objects.
 * <p/>
 * This class is optimized for speed rather than strictly correct encapsulation.
 *
 * @author Jive Software
 */
public final class CacheObject {

    /**
     * Underlying object wrapped by the CacheObject.
     */
    public Object object;

    /**
     * The size of the Cacheable object. The size of the Cacheable
     * object is only computed once when it is added to the cache. This makes
     * the assumption that once objects are added to cache, they are mostly
     * read-only and that their size does not change significantly over time.
     */
    public int size;

    /**
     * A reference to the node in the cache order list. We keep the reference
     * here to avoid linear scans of the list. Every time the object is
     * accessed, the node is removed from its current spot in the list and
     * moved to the front.
     */
    public LinkedListNode lastAccessedListNode;

    /**
     * A reference to the node in the age order list. We keep the reference
     * here to avoid linear scans of the list. The reference is used if the
     * object has to be deleted from the list.
     */
    public LinkedListNode ageListNode;

    /**
     * A count of the number of times the object has been read from cache.
     */
    public int readCount = 0;

    /**
     * Creates a new cache object wrapper. The size of the Cacheable object
     * must be passed in in order to prevent another possibly expensive
     * lookup by querying the object itself for its size.<p>
     *
     * @param object the underlying Object to wrap.
     * @param size   the size of the Cachable object in bytes.
     */
    public CacheObject(Object object, int size) {
        this.object = object;
        this.size = size;
    }
}