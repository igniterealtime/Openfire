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
public class GroupCollection extends AbstractCollection<Group> {

    private String[] elements;

    /**
     * Constructs a new GroupCollection.
     * @param collection the initial groups in the collection
     */
    public GroupCollection(Collection<String> collection) {
        this.elements = collection.toArray(new String[collection.size()]);
    }

    /**
     * Constructs a new GroupCollection.
     * @param elements the initial groups in the collection
     */
    public GroupCollection(String [] elements) {
        this.elements = elements;
    }

    @Override
    public Iterator<Group> iterator() {
        return new GroupIterator();
    }

    @Override
    public int size() {
        return elements.length;
    }

    private class GroupIterator implements Iterator<Group> {

        private int currentIndex = -1;
        private Group nextElement = null;

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
        public Group next() throws java.util.NoSuchElementException {
            Group element;
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
        private Group getNextElement() {
            while (currentIndex + 1 < elements.length) {
                currentIndex++;
                Group element = null;
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
