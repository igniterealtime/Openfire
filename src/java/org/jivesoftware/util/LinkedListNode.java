/**
 * $RCSfile$
 * $Revision$
 * $Date$
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

package org.jivesoftware.util;

/**
 * Doubly linked node in a LinkedList. Most LinkedList implementations keep the
 * equivalent of this class private. We make it public so that references
 * to each node in the list can be maintained externally.
 * <p/>
 * Exposing this class lets us make remove operations very fast. Remove is
 * built into this class and only requires two reference reassignments. If
 * remove existed in the main LinkedList class, a linear scan would have to
 * be performed to find the correct node to delete.
 * <p/>
 * The linked list implementation was specifically written for the Jive
 * cache system. While it can be used as a general purpose linked list, for
 * most applications, it is more suitable to use the linked list that is part
 * of the Java Collections package.
 *
 * @author Jive Software
 * @see org.jivesoftware.util.LinkedList
 */
public class LinkedListNode<E> {

    public LinkedListNode<E> previous;
    public LinkedListNode<E> next;
    public E object;

    /**
     * This class is further customized for the CoolServlets cache system. It
     * maintains a timestamp of when a Cacheable object was first added to
     * cache. Timestamps are stored as long values and represent the number
     * of milleseconds passed since January 1, 1970 00:00:00.000 GMT.<p>
     * <p/>
     * The creation timestamp is used in the case that the cache has a
     * maximum lifetime set. In that case, when
     * [current time] - [creation time] > [max lifetime], the object will be
     * deleted from cache.
     */
    public long timestamp;

    /**
     * Constructs an self-referencing node. This node acts as a start/end
     * sentinel when traversing nodes in a LinkedList.
     */
    public LinkedListNode() {
    	previous = next = this;
    }

    /**
     * Constructs a new linked list node.
     *
     * @param object   the Object that the node represents.
     * @param next     a reference to the next LinkedListNode in the list.
     * @param previous a reference to the previous LinkedListNode in the list.
     */
    public LinkedListNode(E object, LinkedListNode<E> next, LinkedListNode<E> previous) {
    	if (next != null && previous != null) {
    		this.insert(next, previous);
    	}
        this.object = object;
    }

    /**
     * Removes this node from the linked list that it was a part of.
     * @return This node; next and previous references dropped
     */
    public LinkedListNode<E> remove() {
        previous.next = next;
        next.previous = previous;
        previous = next = null;
        return this;
    }
    
    /**
     * Inserts this node into the linked list that it will be a part of.
     * @return This node, updated to reflect previous/next changes
     */
    public LinkedListNode<E> insert(LinkedListNode<E> next, LinkedListNode<E> previous) {
        this.next = next;
        this.previous = previous;
        this.previous.next = this.next.previous = this;
        return this;
    }

    /**
     * Returns a String representation of the linked list node by calling the
     * toString method of the node's object.
     *
     * @return a String representation of the LinkedListNode.
     */
    @Override
	public String toString() {
        return object == null ? "null" : object.toString();
    }
}
