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
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface that allows to provide different ways for implementing serialization of objects.
 * The open source version of the server will just provide a dummy implementation that does
 * nothing. The enterprise version will use Coherence as its underlying mechanism.
 *
 * @author Gaston Dombiak
 */
public interface ExternalizableUtilStrategy {
    
    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param stringMap the Map of String key/value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringMap(DataOutput out, Map<String, String> stringMap) throws IOException;

    /**
     * Reads a Map of String key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of String key/value pairs.
     * @throws IOException if an error occurs.
     */
    public Map<String, String> readStringMap(DataInput in) throws IOException;

    /**
     * Writes a Map of Long key and Integer value pairs. This method handles
     * the case when the Map is <tt>null</tt>.
     *
     * @param out the output stream.
     * @param map the Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public void writeLongIntMap(DataOutput out, Map<Long, Integer> map) throws IOException;

    /**
     * Reads a Map of Long key and Integer value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public Map readLongIntMap(DataInput in) throws IOException;

    /**
     * Writes a List of Strings. This method handles the case when the List is
     * <tt>null</tt>.
     *
     * @param out        the output stream.
     * @param stringList the List of Strings.
     * @throws IOException if an error occurs.
     */
    public void writeStringList(DataOutput out, List stringList) throws IOException;

    /**
     * Reads a List of Strings. This method will return <tt>null</tt> if the List
     * written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a List of Strings.
     * @throws IOException if an error occurs.
     */
    public List<String> readStringList(DataInput in) throws IOException;

    /**
     * Writes an array of long values. This method handles the case when the
     * array is <tt>null</tt>.
     *
     * @param out   the output stream.
     * @param array the array of long values.
     * @throws IOException if an error occurs.
     */
    public void writeLongArray(DataOutput out, long [] array) throws IOException;

    /**
     * Reads an array of long values. This method will return <tt>null</tt> if
     * the array written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return an array of long values.
     * @throws IOException if an error occurs.
     */
    public long [] readLongArray(DataInput in) throws IOException;

    public void writeLong(DataOutput out, long value) throws IOException;

    public long readLong(DataInput in) throws IOException;

    public void writeBoolean(DataOutput out, boolean value) throws IOException;

    public boolean readBoolean(DataInput in) throws IOException;

    public void writeSafeUTF(DataOutput out, String value) throws IOException;

    public String readSafeUTF(DataInput in) throws IOException;
}