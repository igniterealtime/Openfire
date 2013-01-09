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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.util.cache.ExternalizableUtilStrategy;

import com.hazelcast.nio.SerializationHelper;

/**
 * Serialization strategy that uses Hazelcast as its underlying mechanism.
 *
 * @author Tom Evans
 * @author Gaston Dombiak
 */
public class ClusterExternalizableUtil implements ExternalizableUtilStrategy {

    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param stringMap the Map of String key/value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringMap(DataOutput out, Map<String, String> stringMap) throws IOException {
        SerializationHelper.writeObject(out, stringMap);
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
    	return (Map<String, String>) SerializationHelper.readObject(in);
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
        SerializationHelper.writeObject(out, map);
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
    	Map<String, Set<String>> result = (Map<String, Set<String>>) SerializationHelper.readObject(in);
    	if (result == null) return 0;
        map.putAll(result);
        return result.size();
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
        SerializationHelper.writeObject(out, map);
    }

    /**
     * Reads a Map of Long key and Integer value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public Map<Long, Integer> readLongIntMap(DataInput in) throws IOException {
        return (Map<Long, Integer>) SerializationHelper.readObject(in);
    }

    /**
     * Writes a List of Strings. This method handles the case when the List is
     * <tt>null</tt>.
     *
     * @param out        the output stream.
     * @param stringList the List of Strings.
     * @throws IOException if an error occurs.
     */
    public void writeStringList(DataOutput out, List<String> stringList) throws IOException {
        SerializationHelper.writeObject(out, stringList);
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
        return (List<String>) SerializationHelper.readObject(in);
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
        SerializationHelper.writeObject(out, array);
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
        return (long[]) SerializationHelper.readObject(in);
    }

    public void writeLong(DataOutput out, long value) throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public long readLong(DataInput in) throws IOException {
        return (Long) SerializationHelper.readObject(in);
    }

    public void writeByteArray(DataOutput out, byte[] value) throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public byte[] readByteArray(DataInput in) throws IOException {
    	return (byte[]) SerializationHelper.readObject(in);
    }

    public void writeInt(DataOutput out, int value) throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public int readInt(DataInput in) throws IOException {
    	return (Integer) SerializationHelper.readObject(in);
    }

    public void writeBoolean(DataOutput out, boolean value) throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public boolean readBoolean(DataInput in) throws IOException {
    	return (Boolean) SerializationHelper.readObject(in);
    }

    public void writeSerializable(DataOutput out, Serializable value) throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public Serializable readSerializable(DataInput in) throws IOException {
    	return (Serializable) SerializationHelper.readObject(in);
    }

    public void writeSafeUTF(DataOutput out, String value) throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public String readSafeUTF(DataInput in) throws IOException {
    	return (String) SerializationHelper.readObject(in);
    }

    public void writeExternalizableCollection(DataOutput out, Collection<? extends Externalizable> value)
            throws IOException {
        SerializationHelper.writeObject(out, value);
    }

    public void writeSerializableCollection(DataOutput out, Collection<? extends Serializable> value)
    throws IOException {
        SerializationHelper.writeObject(out, value);
    }
    public int readExternalizableCollection(DataInput in, Collection<? extends Externalizable> value,
                                            ClassLoader loader) throws IOException {
    	Collection<Externalizable> result = (Collection<Externalizable>) SerializationHelper.readObject(in);
    	if (result == null) return 0;
    	((Collection<Externalizable>)value).addAll(result);
    	return result.size();
    }

    public int readSerializableCollection(DataInput in, Collection<? extends Serializable> value,
            ClassLoader loader) throws IOException {
    	Collection<Serializable> result = (Collection<Serializable>) SerializationHelper.readObject(in);
    	if (result == null) return 0;
		((Collection<Serializable>)value).addAll(result);
		return result.size();
}

    public void writeExternalizableMap(DataOutput out, Map<String, ? extends Externalizable> map) throws IOException {
        SerializationHelper.writeObject(out, map);
    }

    public void writeSerializableMap(DataOutput out, Map<? extends Serializable, ? extends Serializable> map) throws IOException {
        SerializationHelper.writeObject(out, map);
    }
    
    public int readExternalizableMap(DataInput in, Map<String, ? extends Externalizable> map, ClassLoader loader) throws IOException {
    	Map<String, Externalizable> result = (Map<String, Externalizable>) SerializationHelper.readObject(in);
    	if (result == null) return 0;
		((Map<String, Externalizable>)map).putAll(result);
		return result.size();
    }

    public int readSerializableMap(DataInput in, Map<? extends Serializable, ? extends Serializable> map, ClassLoader loader) throws IOException {
    	Map<String, Serializable> result = (Map<String, Serializable>) SerializationHelper.readObject(in);
    	if (result == null) return 0;
    	((Map<String, Serializable>)map).putAll(result);
    	return result.size();
    }
    
    public void writeStrings(DataOutput out, Collection<String> collection) throws IOException {
        SerializationHelper.writeObject(out, collection);
    }

    public int readStrings(DataInput in, Collection<String> collection) throws IOException {
        Collection<String> result = (Collection<String>) SerializationHelper.readObject(in);
        if (result == null) return 0;
        collection.addAll(result);
        return result.size();
    }
}
