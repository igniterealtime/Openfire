/*
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

package org.jivesoftware.openfire.user;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.AbstractCollection;

/**
 * Provides a view of an array of usernames as a Collection of User objects. If
 * any of the usernames cannot be loaded, they are transparently skipped when
 * iterating over the collection.
 *
 * @author Matt Tucker
 */
public class UserCollection extends AbstractCollection<User> {

    private String[] elements;

    /**
     * Constructs a new UserCollection.
     * @param elements the initial set of users
     */
    public UserCollection(String [] elements) {
        this.elements = elements;
    }

    @Override
    public Iterator<User> iterator() {
        return new UserIterator();
    }

    @Override
    public int size() {
        return elements.length;
    }

    private class UserIterator implements Iterator<User> {

        private int currentIndex = -1;
        private User nextElement = null;

        @Override
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

        @Override
        public User next() throws java.util.NoSuchElementException {
            User element;
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

        @Override
        public void remove() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns the next available element, or null if there are no more elements to return.
         *
         * @return the next available element.
         */
        private User getNextElement() {
            while (currentIndex + 1 < elements.length) {
                currentIndex++;
                User element = null;
                try {
                    element = UserManager.getInstance().getUser(elements[currentIndex]);
                }
                catch (UserNotFoundException unfe) {
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
