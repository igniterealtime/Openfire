/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util.cache;

import org.jivesoftware.util.cache.Cacheable;

import java.util.Map;
import java.util.Collection;

/**
 * Utility class for determining the sizes in bytes of commonly used objects.
 * Classes implementing the Cacheable interface should use this class to
 * determine their size.
 *
 * @author Matt Tucker
 */
public class CacheSizes {

    /**
     * Returns the size in bytes of a basic Object. This method should only
     * be used for actual Object objects and not classes that extend Object.
     *
     * @return the size of an Object.
     */
    public static int sizeOfObject() {
        return 4;
    }

    /**
     * Returns the size in bytes of a String.
     *
     * @param string the String to determine the size of.
     * @return the size of a String.
     */
    public static int sizeOfString(String string) {
        if (string == null) {
            return 0;
        }
        return 4 + string.length() * 2;
    }

    /**
     * Returns the size in bytes of a primitive int.
     *
     * @return the size of a primitive int.
     */
    public static int sizeOfInt() {
        return 4;
    }

    /**
     * Returns the size in bytes of a primitive char.
     *
     * @return the size of a primitive char.
     */
    public static int sizeOfChar() {
        return 2;
    }

    /**
     * Returns the size in bytes of a primitive boolean.
     *
     * @return the size of a primitive boolean.
     */
    public static int sizeOfBoolean() {
        return 1;
    }

    /**
     * Returns the size in bytes of a primitive long.
     *
     * @return the size of a primitive long.
     */
    public static int sizeOfLong() {
        return 8;
    }

    /**
     * Returns the size in bytes of a primitive double.
     *
     * @return the size of a primitive double.
     */
    public static int sizeOfDouble() {
        return 8;
    }

    /**
     * Returns the size in bytes of a Date.
     *
     * @return the size of a Date.
     */
    public static int sizeOfDate() {
        return 12;
    }

    /**
     * Returns the size in bytes of a Map object. All keys and
     * values <b>must be Strings</b>.
     *
     * @param map the Map object to determine the size of.
     * @return the size of the Map object.
     */
    public static int sizeOfMap(Map map) {
        if (map == null) {
            return 0;
        }
        // Base map object -- should be something around this size.
        int size = 36;
        // Add in size of each value
        Object[] values = map.values().toArray();
        for (int i = 0; i < values.length; i++) {
            size += sizeOfString((String)values[i]);
        }
        Object[] keys = map.keySet().toArray();
        // Add in each key
        for (int i = 0; i < keys.length; i++) {
            size += sizeOfString((String)keys[i]);
        }
        return size;
    }

    /**
     * Returns the size in bytes of a Collection object. Elements are assumed to be
     * <tt>String</tt>s, <tt>Long</tt>s or <tt>Cacheable</tt> objects.
     *
     * @param list the Collection object to determine the size of.
     * @return the size of the Collection object.
     */
    public static int sizeOfCollection(Collection list) {
        if (list == null) {
            return 0;
        }
        // Base list object (approximate)
        int size = 36;
        // Add in size of each value
        Object[] values = list.toArray();
        for (int i = 0; i < values.length; i++) {
            Object obj = values[i];
            if (obj instanceof String) {
                size += sizeOfString((String)obj);
            }
            else if (obj instanceof Long) {
                size += sizeOfLong() + sizeOfObject();
            }
            else {
                size += ((Cacheable)obj).getCachedSize();
            }
        }
        return size;
    }
}