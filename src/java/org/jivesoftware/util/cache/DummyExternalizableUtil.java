/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.io.*;
import java.util.*;

/**
 * Dummy implementation that does nothing. The open source version of the server uses this
 * strategy.
 *
 * @author Gaston Dombiak
 */
public class DummyExternalizableUtil implements ExternalizableUtilStrategy {
    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param stringMap the Map of String key/value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    @Override
    public void writeStringMap(DataOutput out, Map<String, String> stringMap) throws IOException {
        // Do nothing
    }

    /**
     * Reads a Map of String key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of String key/value pairs.
     * @throws IOException if an error occurs.
     */
    @Override
    public Map<String, String> readStringMap(DataInput in) throws IOException {
        // Do nothing
        return Collections.emptyMap();
    }

    /**
     * Writes a Map of Long key and Integer value pairs. This method handles
     * the case when the Map is <tt>null</tt>.
     *
     * @param out the output stream.
     * @param map the Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    @Override
    public void writeLongIntMap(DataOutput out, Map<Long, Integer> map) throws IOException {
        // Do nothing
    }

    /**
     * Reads a Map of Long key and Integer value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    @Override
    public Map<Long, Integer> readLongIntMap(DataInput in) throws IOException {
        // Do nothing
        return Collections.emptyMap();
    }

    /**
     * Writes a List of Strings. This method handles the case when the List is
     * <tt>null</tt>.
     *
     * @param out        the output stream.
     * @param stringList the List of Strings.
     * @throws IOException if an error occurs.
     */
    @Override
    public void writeStringList(DataOutput out, List<String> stringList) throws IOException {
        // Do nothing
    }

    /**
     * Reads a List of Strings. This method will return <tt>null</tt> if the List
     * written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a List of Strings.
     * @throws IOException if an error occurs.
     */
    @Override
    public List<String> readStringList(DataInput in) throws IOException {
        // Do nothing
        return Collections.emptyList();
    }

    /**
     * Writes an array of long values. This method handles the case when the
     * array is <tt>null</tt>.
     *
     * @param out   the output stream.
     * @param array the array of long values.
     * @throws IOException if an error occurs.
     */
    @Override
    public void writeLongArray(DataOutput out, long[] array) throws IOException {
        // Do nothing
    }

    /**
     * Reads an array of long values. This method will return <tt>null</tt> if
     * the array written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return an array of long values.
     * @throws IOException if an error occurs.
     */
    @Override
    public long[] readLongArray(DataInput in) throws IOException {
        // Do nothing
        return new long[]{};
    }

    @Override
    public void writeLong(DataOutput out, long value) {
        // Do nothing
    }

    @Override
    public long readLong(DataInput in) {
        // Do nothing
        return 0;
    }

    @Override
    public void writeBoolean(DataOutput out, boolean value) {
        // Do nothing
    }

    @Override
    public boolean readBoolean(DataInput in) {
        // Do nothing
        return false;
    }

    @Override
    public void writeByteArray(DataOutput out, byte[] value) throws IOException {
        // Do nothing
    }

    @Override
    public byte[] readByteArray(DataInput in) throws IOException {
        // Do nothing
        return new byte[0];
    }

    @Override
    public void writeSerializable(DataOutput out, Serializable value) throws IOException {
        // Do nothing
    }

    @Override
    public Serializable readSerializable(DataInput in) throws IOException {
        // Do nothing
        return null;
    }

    @Override
    public void writeSafeUTF(DataOutput out, String value) {
        // Do nothing
    }

    @Override
    public String readSafeUTF(DataInput in) {
        // Do nothing
        return "";
    }

    @Override
    public void writeExternalizableCollection(DataOutput out, Collection<? extends Externalizable> value)
            throws IOException {
        // Do nothing
    }

    @Override
    public int readExternalizableCollection(DataInput in, Collection<? extends Externalizable> value,
                                            ClassLoader loader) throws IOException {
        // Do nothing
        return 0;
    }

    @Override
    public void writeSerializableCollection(DataOutput out, Collection<? extends Serializable> value)
        throws IOException {
        // Do nothing
    }
    
    @Override
    public int readSerializableCollection(DataInput in, Collection<? extends Serializable> value,
                                        ClassLoader loader) throws IOException {
        // Do nothing
        return 0;
    }
    
    @Override
    public void writeExternalizableMap(DataOutput out, Map<String, ? extends Externalizable> map) throws IOException {
        // Do nothing
    }

    @Override
    public int readExternalizableMap(DataInput in, Map<String, ? extends Externalizable> map, ClassLoader loader)
            throws IOException {
        // Do nothing
        return 0;
    }

    @Override
    public void writeSerializableMap(DataOutput out, Map<? extends Serializable, ? extends Serializable> map) throws IOException {
        // Do nothing
    }

    @Override
    public int readSerializableMap(DataInput in, Map<? extends Serializable, ? extends Serializable> map, ClassLoader loader)
            throws IOException {
        // Do nothing
        return 0;
    }
    @Override
    public void writeStringsMap(DataOutput out, Map<String, Set<String>> map) throws IOException {
        // Do nothing
    }

    @Override
    public int readStringsMap(DataInput in, Map<String, Set<String>> map) throws IOException {
        // Do nothing
        return 0;
    }

    @Override
    public void writeStrings(DataOutput out, Collection<String> collection) throws IOException {
        // Do nothing
    }

    @Override
    public int readStrings(DataInput in, Collection<String> collection) throws IOException {
        // Do nothing
        return 0;
    }

    @Override
    public void writeInt(DataOutput out, int value) {
        // Do nothing
    }

    @Override
    public int readInt(DataInput in) {
        // Do nothing
        return 0;
    }
}
