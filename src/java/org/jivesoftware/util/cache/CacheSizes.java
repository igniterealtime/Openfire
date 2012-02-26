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

package org.jivesoftware.util.cache;

import org.jivesoftware.util.cache.Cacheable;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
        return 4 + string.getBytes().length;
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
     * Returns the size in bytes of a Map object. 
     *
     * @param map the Map object to determine the size of.
     * @return the size of the Map object.
     */
    public static int sizeOfMap(Map map)
	    throws CannotCalculateSizeException {
        if (map == null) {
            return 0;
        }
        // Base map object -- should be something around this size.
        int size = 36;
		Set<? extends Map.Entry> set = map.entrySet();
        
        // Add in size of each value
        for (Map.Entry<Object, Object> entry : set) {
			size += sizeOfAnything(entry.getKey());
            size += sizeOfAnything(entry.getValue());
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
    public static int sizeOfCollection(Collection list) 
            throws CannotCalculateSizeException {
        if (list == null) {
            return 0;
        }
        // Base list object (approximate)
        int size = 36;
        // Add in size of each value
        Object[] values = list.toArray();
        for (int i = 0; i < values.length; i++) {
            size += sizeOfAnything(values[i]);
        }
        return size;
    }

    /**
     * Returns the size of an object in bytes. Determining size by serialization
     * is only used as a last resort.
     *
     * @return the size of an object in bytes.
     */
    public static int sizeOfAnything(Object object) 
	    throws CannotCalculateSizeException {
        // If the object is Cacheable, ask it its size.
        if (object == null) {
            return 0;
        }
        if (object instanceof Cacheable) {
            return ((Cacheable)object).getCachedSize();
        }
        // Check for other common types of objects put into cache.
        else if (object instanceof String) {
            return sizeOfString((String)object);
        }
        else if (object instanceof Long) {
            return sizeOfLong();
        }
        else if (object instanceof Integer) {
            return sizeOfObject() + sizeOfInt();
        }
        else if (object instanceof Double) {
            return sizeOfObject() + sizeOfDouble();
        }
        else if (object instanceof Boolean) {
            return sizeOfObject() + sizeOfBoolean();
        }
        else if (object instanceof Map) {
            return sizeOfMap((Map)object);
        }
        else if (object instanceof long[]) {
            long[] array = (long[])object;
            return sizeOfObject() + array.length * sizeOfLong();
        }
        else if (object instanceof Collection) {
            return sizeOfCollection((Collection)object);
        }
        else if (object instanceof byte[]) {
            byte [] array = (byte[])object;
            return sizeOfObject() + array.length;
        }
        // Default behavior -- serialize the object to determine its size.
        else {
            int size = 1;
            try {
                // Default to serializing the object out to determine size.
                CacheSizes.NullOutputStream out = new NullOutputStream();
                ObjectOutputStream outObj = new ObjectOutputStream(out);
                outObj.writeObject(object);
                size = out.size();
            }
            catch (IOException ioe) {
                throw new CannotCalculateSizeException(object);
            }
            return size;
        }
    }

    private static class NullOutputStream extends OutputStream {

        int size = 0;

        @Override
        public void write(int b) throws IOException {
            size++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            size += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            size += len;
        }

        /**
         * Returns the number of bytes written out through the stream.
         *
         * @return the number of bytes written to the stream.
         */
        public int size() {
            return size;
        }
    }
}
