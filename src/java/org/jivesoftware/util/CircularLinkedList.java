/**
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

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * <p>Simple circular LinkedList allowing the marking of a position, reset of position, and pass count.</p>
 * <p>The circular list provides several tools for 'spinning' the circular list.
 * The circular list is best thought of as an iterator on the circular list without
 * a head or tail. Adds are conducted at the current list position and nodes lack
 * indexes. Call mark() to mark the current node as a 'zero' position. When the next
 * list item is called, the pass count is incremented when the current position is equal to
 * the marked position. This allows users of the circular list to mark and spin around the
 * circular list, checking how many times they go around the list.</p>
 *
 * @author Jive Software
 */
public class CircularLinkedList {

    /**
     * <p>The current reference to the list.</p>
     */
    private LinkedListNode current;
    /**
     * <p>The marked position on the list.</p>
     */
    private LinkedListNode mark;
    /**
     * <p>The number of times the iteration has passed the mark.</p>
     */
    private int passCount;
    /**
     * <p>Locks access to the current node.</p>
     */
    private ReadWriteLock currentLock = new ReentrantReadWriteLock();
    /**
     * <p>The number of items in the list.</p>
     */
    private int size = 0;

    /**
     * Creates a new linked list.
     */
    public CircularLinkedList() {
        mark();
    }

    /**
     * Creates a new linked list.
     */
    public CircularLinkedList(Object firstItem) {
        add(firstItem);
        mark();
    }

    /**
     * <p>Obtain the number of times the iteration of the list has passed the mark.</p>
     * <p>The passcount is reset using the mark() method.</p>
     *
     * @return The number of times the list has iterated past the mark
     */
    public int getPassCount() {
        return passCount;
    }

    /**
     * Resets the pass count to zero and sets the mark to the current list position.
     */
    public synchronized void mark() {
        passCount = 0;
        mark = current;
    }

    /**
     * Add the given item so the following call to next() will return the item.
     *
     * @param item The item to be added
     */
    public synchronized void add(Object item) {
        currentLock.writeLock().lock();
        try {
            if (current == null) {
                current = new LinkedListNode(item, null, null);
                current.next = current;
                current.previous = current;
                mark();
            }
            else {
                LinkedListNode node = current.next;
                current.next = new LinkedListNode(item, node, current);
            }
            size++;
        }
        finally {
            currentLock.writeLock().unlock();
        }
    }

    /**
     * Sets the object that will be return by next() to the given value.
     * If no next value exists, nothing happens (no new items will be added.
     *
     * @param item The item to be set
     */
    public synchronized void setNext(Object item) {
        currentLock.writeLock().lock();
        try {
            if (current != null) {
                current.next.object = item;
            }
            size++;
        }
        finally {
            currentLock.writeLock().unlock();
        }
    }

    /**
     * Sets the object that will be return by prev() to the given value.
     * If no prev value exists, nothing happens (no new items will be added.
     *
     * @param item The item to be set
     */
    public synchronized void setPrev(Object item) {
        currentLock.writeLock().lock();
        try {
            if (current != null) {
                current.object = item;
            }
            size++;
        }
        finally {
            currentLock.writeLock().unlock();
        }
    }


    /**
     * Advances the list to the next item and returns the item
     * found or null if no items are in the list.
     *
     * @return The next item in the list or null if none exists
     */
    public Object next() {
        Object item = null;
        if (current != null) {
            currentLock.writeLock().lock();
            try {
                current = current.next;
                item = current.object;
            }
            finally {
                currentLock.writeLock().unlock();
            }
        }
        if (mark == current) {
            passCount++;
        }
        return item;
    }

    /**
     * Moves the list to the previous item and returns the item
     * found or null if no items are in the list.
     *
     * @return The next item in the list or null if none exists
     */
    public Object prev() {
        Object item = null;
        if (current != null) {
            currentLock.writeLock().lock();
            try {
                item = current.object;
                current = current.previous;
            }
            finally {
                currentLock.writeLock().unlock();
            }
        }
        if (mark == current) {
            passCount--;
        }
        return item;
    }

    /**
     * Erases all elements in the list and re-initializes it.
     */
    public void clear() {
        current = null;
        mark();
    }

    /**
     * Removes the first item found that matches equals() on the given item.
     * If this list does not contain the element, it is unchanged.
     * More formally, removes the first element found by calling next() such that
     * (item==null ? get(i)==null : item.equals(get(i))) (if such an element is found before
     * making a complete trip around the circular list).
     *
     * @param item The item to remove
     */
    public void remove(Object item) {
        if (current != null) {
            currentLock.writeLock().lock();
            try {
                if (item == null ? current.object == null : item.equals(current.object)) {
                    current = null;
                    mark();
                }
                else {
                    for (LinkedListNode node = current.next;
                         node != current;
                         node = node.next) {
                        if (item == null ? node.object == null : item.equals(node.object)) {
                            node.previous.next = node.next;
                            node.next.previous = node.previous;
                            if (node == mark) {
                                mark = node.next;
                            }
                            break;
                        }
                    }
                }
                size--;
            }
            finally {
                currentLock.writeLock().lock();
            }
        }
    }

    /**
     * Removes the current item (the item last returned by next() or prev()).
     */
    public void remove() {
        if (current != null) {
            currentLock.writeLock().lock();
            try {
                LinkedListNode node = current.next;
                if (node != null) {
                    node.previous.next = node.next;
                    node.next.previous = node.previous;
                    if (node == mark) {
                        mark = node.next;
                    }
                }
                else {
                    current = null;
                }
                size--;
            }
            finally {
                currentLock.writeLock().unlock();
            }
        }
    }

    /**
     * Returns true if the given object is contained in this list.
     *
     * @param item the item to check for existance
     * @return true if the given object is in the list
     */
    public boolean contains(Object item) {
        boolean in = false;
        if (current != null) {
            currentLock.readLock().lock();
            try {
                if (item == null ? current.object == null : item.equals(current.object)) {
                    in = true;
                }
                else {
                    for (LinkedListNode node = current.next;
                         node != current;
                         node = node.next) {
                        if (item == null ? node.object == null : item.equals(node.object)) {
                            in = true;
                            break;
                        }
                    }
                }
            }
            finally {
                currentLock.readLock().unlock();
            }
        }
        return in;
    }

    /**
     * Returns the number of items in the list.
     *
     * @return the number of items in the list.
     */
    public int size() {
        return size;
    }

    /**
     * Returns a String representation of the linked list with a comma
     * delimited list of all the elements in the list.
     *
     * @return a String representation of the LinkedList.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        if (current == null) {
            buf.append("empty");
        }
        else {
            currentLock.readLock().lock();
            try {
                buf.append(current.toString());
                for (LinkedListNode node = current.next;
                     node != current;
                     node = node.next) {
                    buf.append(", ").append(node.toString());
                }
            }
            finally {
                currentLock.readLock().unlock();
            }
        }
        return buf.toString();
    }

    /**
     * <p>Returns true if the circular linked list is empty.</p>
     *
     * @return true if the list is empty.
     */
    public boolean isEmpty() {
        return current == null;
    }
}