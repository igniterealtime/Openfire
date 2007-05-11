/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods to assist in working with the Externalizable interfaces. This class
 * is only used when running inside of a Cluser. When using the open source version
 * this class will use a dummy implementation. Anyway, this class is not used when
 * not using the Enterprise edition.<p>
 *
 * ExternalizableLite is very similar to the standard Externalizable interface, except that
 * it uses DataOutput/DataInput instead of the Object stream equivalents.
 *
 * @author Gaston Dombiak
 */
public class ExternalizableUtil {

    private static ExternalizableUtil instance = new ExternalizableUtil();

    private ExternalizableUtilStrategy strategy = new DummyExternalizableUtil();

    static {
        instance = new ExternalizableUtil();
    }

    public static ExternalizableUtil getInstance() {
        return instance;
    }


    /**
     * Sets the implementation to use for serializing and deserializing
     * objects. 
     *
     * @param strategy the new strategy to use.
     */
    public void setStrategy(ExternalizableUtilStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Returns the implementation to use for serializing and deserializing
     * objects.
     *
     * @return the implementation to use for serializing and deserializing
     * objects.
     */
    public ExternalizableUtilStrategy getStrategy() {
        return strategy;
    }

    /**
     * Hidding constructor. We only want one single instance.
     */
    private ExternalizableUtil() {
        super();
    }

    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param stringMap the Map of String key/value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringMap(DataOutput out, Map<String, String> stringMap) throws IOException {
        strategy.writeStringMap(out, stringMap);
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
        return strategy.readStringMap(in);
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
        strategy.writeLongIntMap(out, map);
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
        return strategy.readLongIntMap(in);
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
        strategy.writeStringList(out, stringList);
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
        return strategy.readStringList(in);
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
        strategy.writeLongArray(out, array);
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
        return strategy.readLongArray(in);
    }

    public void writeLong(DataOutput out, long value) throws IOException {
        strategy.writeLong(out, value);
    }

    public long readLong(DataInput in) throws IOException {
        return strategy.readLong(in);
    }

    public void writeInt(DataOutput out, int value) throws IOException {
        strategy.writeInt(out, value);
    }

    public int readInt(DataInput in) throws IOException {
        return strategy.readInt(in);
    }

    public void writeBoolean(DataOutput out, boolean value) throws IOException {
        strategy.writeBoolean(out, value);
    }

    public boolean readBoolean(DataInput in) throws IOException {
        return strategy.readBoolean(in);
    }

    public void writeByteArray(DataOutput out, byte[] value) throws IOException {
        strategy.writeByteArray(out, value);
    }

    public byte[] readByteArray(DataInput in) throws IOException {
        return strategy.readByteArray(in);
    }

    public void writeSafeUTF(DataOutput out, String value) throws IOException {
        strategy.writeSafeUTF(out, value);
    }

    public String readSafeUTF(DataInput in) throws IOException {
        return strategy.readSafeUTF(in);
    }

    /**
     * Writes a collection of Externalizable objects. The collection passed as a parameter
     * must be a collection and not a <tt>null</null> value.
     *
     * @param out   the output stream.
     * @param value the collection of Externalizable objects. This value must not be null.
     * @throws IOException if an error occurs.
     */
    public void writeExternalizableCollection(DataOutput out, Collection<? extends Externalizable> value) throws IOException {
        strategy.writeExternalizableCollection(out, value);
    }

    /**
     * Reads a collection of Externalizable objects and adds them to the collection passed as a parameter. The
     * collection passed as a parameter must be a collection and not a <tt>null</null> value.
     *
     * @param in the input stream.
     * @param value the collection of Externalizable objects. This value must not be null.
     * @param loader class loader to use to build elements inside of the serialized collection.
     * @throws IOException if an error occurs.
     * @return the number of elements added to the collection.
     */
    public int readExternalizableCollection(DataInput in, Collection<? extends Externalizable> value, ClassLoader loader)
            throws IOException {
        return strategy.readExternalizableCollection(in, value, loader);
    }

    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param map       the Map of String key and Externalizable value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeExternalizableMap(DataOutput out, Map<String, ? extends Externalizable> map) throws IOException {
        strategy.writeExternalizableMap(out, map);
    }

    /**
     * Reads a Map of String key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @param map a Map of String key and Externalizable value pairs.
     * @param loader class loader to use to build elements inside of the serialized collection.
     * @throws IOException if an error occurs.
     * @return the number of elements added to the collection.
     */
    public int readExternalizableMap(DataInput in, Map<String, ? extends Externalizable> map, ClassLoader loader) throws IOException {
        return strategy.readExternalizableMap(in, map, loader);
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
        strategy.writeStringsMap(out, map);
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
        return strategy.readStringsMap(in, map);
    }

    /**
     * Writes content of collection of strings to the output stream.
     *
     * @param out           the output stream.
     * @param collection    the Collection of Strings.
     * @throws IOException if an error occurs.
     */
    public void writeStrings(DataOutput out, Collection<String> collection) throws IOException {
        strategy.writeStrings(out, collection);
    }

    /**
     * Reads the string array from the input stream and adds them to the specified collection. 
     *
     * @param in the input stream.
     * @param collection the collection to add the read strings from the input stream.
     * @return number of elements added to the collection.
     * @throws IOException if an error occurs.
     */
    public int readStrings(DataInput in, Collection<String> collection) throws IOException {
        return strategy.readStrings(in, collection);
    }
}
