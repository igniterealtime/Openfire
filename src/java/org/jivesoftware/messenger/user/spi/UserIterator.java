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

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.database.DatabaseObjectFactory;
import org.jivesoftware.messenger.container.ServiceLookupFactory;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.database.DatabaseObjectFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An class that defines the logic to iterate through an array of long unique ID's of Jive objects.
 * <p/>
 * <p/>
 * One feature of the class is the ability to recover from underlying modifications to the dataset
 * in some cases. Consider the following sequence of events:
 * <ul>
 * <li> Time 00: An Iterator for users in a group is obtained.
 * <li> Time 01: 3 of the 8 users in the group are deleted.
 * <li> Time 02: Iteration of users begins.
 * </ul>
 * <p/>
 * In the above example, the underlying users in the group were changed after the initial
 * iterator was obtained. The logic in this class will attempt to automatically compensate for
 * these changes by skipping over items that cannot be loaded. In the above example, that would
 * translate to the iterator returning 5 users instead of 8.
 *
 * @author Iain Shigeoka
 */
public class UserIterator implements Iterator {

    private long[] elements;
    private int currentIndex = -1;
    private Object nextElement = null;

    private DatabaseObjectFactory objectFactory;

    /**
     * Creates a new UserIterator.
     */
    public UserIterator(long[] elements)
            throws UnauthorizedException {
        this.elements = elements;

        // Create an objectFactory to load users.
        this.objectFactory = new UserDOFactory();
    }

    private class UserDOFactory implements DatabaseObjectFactory {
        private UserManager userManager;

        public UserDOFactory() throws UnauthorizedException {
            userManager =
                    (UserManager)ServiceLookupFactory.getLookup().lookup(UserManager.class);
        }

        public Object loadObject(long id) {
            try {
                User user = userManager.getUser(id);
                return user;
            }
            catch (UserNotFoundException e) { /* ignore */
            }

            return null;
        }
    }

    /**
     * Returns true if there are more elements in the iteration.
     *
     * @return true if the iterator has more elements.
     */
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

    /**
     * Returns the next element.
     *
     * @return the next element.
     * @throws java.util.NoSuchElementException
     *          if there are no more elements.
     */
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

    /**
     * Not supported for security reasons.
     */
    public void remove() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the next available element, or null if there are no more elements to return.
     *
     * @return the next available element.
     */
    public Object getNextElement() {
        while (currentIndex + 1 < elements.length) {
            currentIndex++;
            Object element = objectFactory.loadObject(elements[currentIndex]);
            if (element != null) {
                return element;
            }
        }
        return null;
    }
}