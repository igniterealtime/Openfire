/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cache;

import com.tangosol.util.ExternalizableHelper;
import org.jivesoftware.util.cache.ExternalizableUtilStrategy;

import java.io.*;
import java.util.*;

/**
 * Serialization strategy that uses Coherence as its underlying mechanism.
 *
 * @author Gaston Dombiak
 */
public class CoherenceExternalizableUtil implements ExternalizableUtilStrategy {

    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param stringMap the Map of String key/value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringMap(DataOutput out, Map<String, String> stringMap) throws IOException {
        // Format for map is an int with the number of values,
        // followed by all String key/value pairs.
        if (stringMap == null) {
            out.writeInt(-1);
        }
        else {
            out.writeInt(stringMap.size());
            for (Map.Entry<String, String> entry : stringMap.entrySet()) {
                ExternalizableHelper.writeSafeUTF(out, entry.getKey());
                ExternalizableHelper.writeSafeUTF(out, entry.getValue());
            }
        }
    }

    /**
     * Reads a Map of String key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of String key/value pairs.
     * @throws IOException if an error occurs.
     */
    public Map<String, String> readStringMap(DataInput in) throws IOException {
        // Format for map is an int with the number of values,
        // followed by all String key/value pairs.
        int propertyCount = in.readInt();
        if (propertyCount == -1) {
            return null;
        }
        else {
            Map<String, String> stringMap = new HashMap<String, String>();
            for (int i = 0; i < propertyCount; i++) {
                stringMap.put(ExternalizableHelper.readSafeUTF(in), ExternalizableHelper.readSafeUTF(in));
            }
            return stringMap;
        }
    }

    /**
     * Writes a Map of String key and Set of Strings value pairs. This method DOES NOT handle the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param map       the Map of String key and Set of Strings value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringsMap(DataOutput out, Map<String, Set<String>> map) throws IOException {
        out.writeInt(map.size());
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            ExternalizableHelper.writeSafeUTF(out, entry.getKey());
            ExternalizableHelper.writeStringArray(out, entry.getValue().toArray(new String[]{}));
        }
    }

    /**
     * Reads a Map of String key and Set of Strings value pairs. 
     *
     * @param in the input stream.
     * @param map a Map of String key and Set of Strings value pairs.
     * @return number of elements added to the collection.
     * @throws IOException if an error occurs.
     */
    public int readStringsMap(DataInput in, Map<String, Set<String>> map) throws IOException {
        // Format for map is an int with the number of values,
        // followed by all String key/value pairs.
        int propertyCount = in.readInt();
        for (int i = 0; i < propertyCount; i++) {
            String key = ExternalizableHelper.readSafeUTF(in);
            Set<String> value = new HashSet<String>();
            Collections.addAll(value, ExternalizableHelper.readStringArray(in));
            map.put(key, value);
        }
        return propertyCount;
    }

    /**
     * Writes a Map of Long key and Integer value pairs. This method handles
     * the case when the Map is <tt>null</tt>.
     *
     * @param out the output stream.
     * @param map the Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public void writeLongIntMap(DataOutput out, Map<Long, Integer> map) throws IOException {
        // Format for map is an int with the number of values,
        // followed by all key/value pairs.
        if (map == null) {
            out.writeInt(-1);
        }
        else {
            out.writeInt(map.size());
            for (Map.Entry<Long, Integer> entry : map.entrySet()) {
                out.writeLong(entry.getKey());
                out.writeInt(entry.getValue());
            }
        }
    }

    /**
     * Reads a Map of Long key and Integer value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public Map readLongIntMap(DataInput in) throws IOException {
        // Format for map is an int with the number of values,
        // followed by all key/value pairs.
        int propertyCount = in.readInt();
        if (propertyCount == -1) {
            return null;
        }
        else {
            Map<Long, Integer> map = new HashMap<Long, Integer>();
            for (int i = 0; i < propertyCount; i++) {
                map.put(in.readLong(), in.readInt());
            }
            return map;
        }
    }

    /**
     * Writes a List of Strings. This method handles the case when the List is
     * <tt>null</tt>.
     *
     * @param out        the output stream.
     * @param stringList the List of Strings.
     * @throws IOException if an error occurs.
     */
    public void writeStringList(DataOutput out, List stringList) throws IOException {
        // Format for list is an int with the number of values,
        // followed by all Strings.
        if (stringList == null) {
            out.writeInt(-1);
        }
        else {
            out.writeInt(stringList.size());
            for (int i = 0, n = stringList.size(); i < n; i++) {
                ExternalizableHelper.writeSafeUTF(out, (String) stringList.get(i));
            }
        }
    }

    /**
     * Reads a List of Strings. This method will return <tt>null</tt> if the List
     * written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a List of Strings.
     * @throws IOException if an error occurs.
     */
    public List<String> readStringList(DataInput in) throws IOException {
        // Format for list is an int with the number of values,
        // followed by all Strings.
        int propertyCount = in.readInt();
        if (propertyCount == -1) {
            return null;
        }
        else {
            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < propertyCount; i++) {
                stringList.add(ExternalizableHelper.readSafeUTF(in));
            }
            return stringList;
        }
    }

    /**
     * Writes an array of long values. This method handles the case when the
     * array is <tt>null</tt>.
     *
     * @param out   the output stream.
     * @param array the array of long values.
     * @throws IOException if an error occurs.
     */
    public void writeLongArray(DataOutput out, long [] array) throws IOException {
        if (array == null) {
            ExternalizableHelper.writeInt(out, -1);
            return;
        }
        ExternalizableHelper.writeInt(out, array.length);
        for (long item : array) {
            ExternalizableHelper.writeLong(out, item);
        }
    }

    /**
     * Reads an array of long values. This method will return <tt>null</tt> if
     * the array written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return an array of long values.
     * @throws IOException if an error occurs.
     */
    public long [] readLongArray(DataInput in) throws IOException {
        int size = ExternalizableHelper.readInt(in);
        if (size == -1) {
            return null;
        }
        long [] array = new long[size];
        for (int i = 0; i < size; i++) {
            array[i] = ExternalizableHelper.readLong(in);
        }
        return array;
    }

    public void writeLong(DataOutput out, long value) throws IOException {
        ExternalizableHelper.writeLong(out, value);
    }

    public long readLong(DataInput in) throws IOException {
        return ExternalizableHelper.readLong(in);
    }

    public void writeByteArray(DataOutput out, byte[] value) throws IOException {
        ExternalizableHelper.writeByteArray(out, value);
    }

    public byte[] readByteArray(DataInput in) throws IOException {
        return ExternalizableHelper.readByteArray(in);
    }

    public void writeInt(DataOutput out, int value) throws IOException {
        ExternalizableHelper.writeInt(out, value);
    }

    public int readInt(DataInput in) throws IOException {
        return ExternalizableHelper.readInt(in);
    }

    public void writeBoolean(DataOutput out, boolean value) throws IOException {
        out.writeBoolean(value);
    }

    public boolean readBoolean(DataInput in) throws IOException {
        return in.readBoolean();
    }

    public void writeSerializable(DataOutput out, Serializable value) throws IOException {
        ExternalizableHelper.writeSerializable(out, value);
    }

    public Serializable readSerializable(DataInput in) throws IOException {
        return ExternalizableHelper.readSerializable(in);
    }

    public void writeSafeUTF(DataOutput out, String value) throws IOException {
        ExternalizableHelper.writeSafeUTF(out, value);
    }

    public String readSafeUTF(DataInput in) throws IOException {
        return ExternalizableHelper.readSafeUTF(in);
    }

    public void writeExternalizableCollection(DataOutput out, Collection<? extends Externalizable> value)
            throws IOException {
        ExternalizableHelper.writeCollection(out, value);
    }

    public void writeSerializableCollection(DataOutput out, Collection<? extends Serializable> value)
    throws IOException {
		ExternalizableHelper.writeCollection(out, value);
    }
    public int readExternalizableCollection(DataInput in, Collection<? extends Externalizable> value,
                                            ClassLoader loader) throws IOException {
        return ExternalizableHelper.readCollection(in, value, loader);
    }

    public int readSerializableCollection(DataInput in, Collection<? extends Serializable> value,
            ClassLoader loader) throws IOException {
    	return ExternalizableHelper.readCollection(in, value, loader);
}

    public void writeExternalizableMap(DataOutput out, Map<String, ? extends Externalizable> map) throws IOException {
        ExternalizableHelper.writeMap(out, map);
    }

    public void writeSerializableMap(DataOutput out, Map<String, ? extends Serializable> map) throws IOException {
        ExternalizableHelper.writeMap(out, map);
    }
    
    public int readExternalizableMap(DataInput in, Map<String, ? extends Externalizable> map, ClassLoader loader) throws IOException {
        return ExternalizableHelper.readMap(in, map, loader);
    }

    public int readSerializableMap(DataInput in, Map<String, ? extends Serializable> map, ClassLoader loader) throws IOException {
        return ExternalizableHelper.readMap(in, map, loader);
    }
    
    public void writeStrings(DataOutput out, Collection<String> collection) throws IOException {
        ExternalizableHelper.writeStringArray(out, collection.toArray(new String[]{}));
    }

    public int readStrings(DataInput in, Collection<String> collection) throws IOException {
        String[] strings = ExternalizableHelper.readStringArray(in);
        Collections.addAll(collection, strings);
        return strings.length;
    }
}
