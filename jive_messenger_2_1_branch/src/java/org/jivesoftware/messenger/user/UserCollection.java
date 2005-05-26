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

package org.jivesoftware.messenger.user;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.AbstractCollection;

/**
 * Provides a view of an array of usernames as a Collection of User objects. If
 * any of the usernames cannot be loaded, they are transparently skipped when
 * iteratating over the collection.
 *
 * @author Matt Tucker
 */
public class UserCollection extends AbstractCollection {

    private String[] elements;
    private int currentIndex = -1;
    private Object nextElement = null;

    /**
     * Constructs a new UserIterator.
     */
    public UserCollection(String [] elements) {
        this.elements = elements;
    }

    public Iterator iterator() {
        return new UserIterator();
    }

    public int size() {
        return elements.length;
    }

    private class UserIterator implements Iterator {

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
            Object element = null;
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
                    element = UserManager.getInstance().getUser(elements[currentIndex]);
                }
                catch (UserNotFoundException unfe) { }
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
    }
}