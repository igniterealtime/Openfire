/**
 * $Revision: 691 $
 * $Date: 2004-12-13 15:06:54 -0300 (Mon, 13 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.group;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Collection;

/**
 * Provides a view of an array of group names as a Collection of Group objects. If
 * any of the group names cannot be loaded, they are transparently skipped when
 * iterating over the collection.
 *
 * @author Matt Tucker
 */
public class GroupCollection extends AbstractCollection {

    private String[] elements;

    /**
     * Constructs a new GroupCollection.
     */
    public GroupCollection(Collection<String> collection) {
        this.elements = collection.toArray(new String[collection.size()]);
    }

    /**
     * Constructs a new GroupCollection.
     */
    public GroupCollection(String [] elements) {
        this.elements = elements;
    }

    public Iterator iterator() {
        return new UserIterator();
    }

    public int size() {
        return elements.length;
    }

    private class UserIterator implements Iterator {

        private int currentIndex = -1;
        private Object nextElement = null;

        public boolean hasNext() {
            // If we are at the end of the list, there can't be any more elements
            // to iterate through.
            if (currentIndex + 1 >= elements.length && nextElement == null) {
                return false;
            }
            // Otherwise, see if nextElement is null. If so, try to load the next
            // element to make sure it exists.
            if (nextElement == null) {
                nextElement = getNextElement();
                if (nextElement == null) {
                    return false;
                }
            }
            return true;
        }

        public Object next() throws java.util.NoSuchElementException {
            Object element;
            if (nextElement != null) {
                element = nextElement;
                nextElement = null;
            }
            else {
                element = getNextElement();
                if (element == null) {
                    throw new NoSuchElementException();
                }
            }
            return element;
        }

        public void remove() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns the next available element, or null if there are no more elements to return.
         *
         * @return the next available element.
         */
        private Object getNextElement() {
            while (currentIndex + 1 < elements.length) {
                currentIndex++;
                Object element = null;
                try {
                    element = GroupManager.getInstance().getGroup(elements[currentIndex]);
                }
                catch (GroupNotFoundException unfe) {
                    // Ignore.
                }
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
    }
}