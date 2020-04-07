/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Verifies functionality as implemented by {@link DefaultExternalizableUtil}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DefaultExternalizableUtilTest
{
    private Bus bus;

    @Before
    public void setupBus() {
        bus = new Bus();
    }

    /**
     * Asserts that the process of writing and reading a map of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadStringMap() throws Exception
    {
        // Setup fixture.
        final Map<String, String> input = new HashMap<>();
        input.put("foo", "\uD834\uDD1E");
        input.put("\uD834\uDD1E", "bar");

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringMap(bus.getDataOutput(), input);
        final Map<String, String> result = new DefaultExternalizableUtil().readStringMap(bus.getDataInput());

        // Verify result.
        assertTrue(areEqualString(input, result ));
    }

    /**
     * Asserts that the process of writing and reading a map of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map contains a null value.
     */
    @Test
    public void testWriteReadStringMapNullValue() throws Exception
    {
        // Setup fixture.
        final Map<String, String> input = new HashMap<>();
        input.put("foo", null);
        input.put("\uD834\uDD1E", "bar");

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringMap(bus.getDataOutput(), input);
        final Map<String, String> result = new DefaultExternalizableUtil().readStringMap(bus.getDataInput());

        // Verify result.
        assertTrue(areEqualString(input, result ));
    }

    /**
     * Asserts that the process of writing and reading a map of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map is empty.
     */
    @Test
    public void testWriteReadStringMapEmpty() throws Exception
    {
        // Setup fixture.
        final Map<String, String> input = new HashMap<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringMap(bus.getDataOutput(), input);
        final Map<String, String> result = new DefaultExternalizableUtil().readStringMap(bus.getDataInput());

        // Verify result.
        assertTrue(areEqualString(input, result ));
    }

    /**
     * Asserts that the process of writing and reading a map of a Set of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadStringsMap() throws Exception
    {
        // Setup fixture.
        final Map<String, Set<String>> input = new HashMap<>();
        final Set<String> firstSet = new HashSet<>();
        firstSet.add("\uD834\uDD1E");
        firstSet.add("foo bar");
        input.put("bar", firstSet);

        final Set<String> secondSet = new HashSet<>();
        secondSet.add("bar");
        secondSet.add("foo");
        input.put("\uD834\uDD1E", secondSet);

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringsMap(bus.getDataOutput(), input);
        final Map<String, Set<String>> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readStringsMap(bus.getDataInput(), result);

        // Verify result.
        assertEquals(2, reportedCount);
        assertTrue(areEqualStrings(input, result));
    }

    /**
     * Asserts that the process of writing and reading a map of a Set of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map contains a null value.
     */
    @Test
    public void testWriteReadStringsMapNullValue() throws Exception
    {
        // Setup fixture.
        final Map<String, Set<String>> input = new HashMap<>();
        final Set<String> firstSet = new HashSet<>();
        firstSet.add("\uD834\uDD1E");
        firstSet.add("foo bar");
        input.put("bar", firstSet);

        input.put("\uD834\uDD1E", null);

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringsMap(bus.getDataOutput(), input);
        final Map<String, Set<String>> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readStringsMap(bus.getDataInput(), result);

        // Verify result.
        assertEquals(2, reportedCount);
        assertTrue(areEqualStrings(input, result));
    }

    /**
     * Asserts that the process of writing and reading a map of a Set of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map contains a set that contains
     * a null value.
     */
    @Test
    public void testWriteReadStringsMapEmptyValueInValue() throws Exception
    {
        // Setup fixture.
        final Map<String, Set<String>> input = new HashMap<>();
        final Set<String> firstSet = new HashSet<>();
        firstSet.add(null);
        firstSet.add("foo bar");
        input.put("bar", firstSet);

        final Set<String> secondSet = new HashSet<>();
        secondSet.add("bar");
        secondSet.add("foo");
        input.put("\uD834\uDD1E", secondSet);

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringsMap(bus.getDataOutput(), input);
        final Map<String, Set<String>> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readStringsMap(bus.getDataInput(), result);

        // Verify result.
        assertEquals(2, reportedCount);
        assertTrue(areEqualStrings(input, result));
    }

    /**
     * Asserts that the process of writing and reading a map of a Set of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map is empty.
     */
    @Test
    public void testWriteReadStringsMapEmpty() throws Exception
    {
        // Setup fixture.
        final Map<String, Set<String>> input = new HashMap<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringsMap(bus.getDataOutput(), input);
        final Map<String, Set<String>> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readStringsMap(bus.getDataInput(), result);

        // Verify result.
        assertEquals(0, reportedCount);
        assertTrue(areEqualStrings(input, result));
    }

    /**
     * Asserts that the process of writing and reading a map of Long-to-Integer through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadLongIntMap() throws Exception
    {
        // Setup fixture.
        final Map<Long, Integer> input = new HashMap<>();
        input.put( Long.MIN_VALUE, Integer.MAX_VALUE );
        input.put( Long.MAX_VALUE, Integer.MIN_VALUE );

        // Execute system under test.
        new DefaultExternalizableUtil().writeLongIntMap(bus.getDataOutput(), input);
        final Map<Long, Integer> result = new DefaultExternalizableUtil().readLongIntMap(bus.getDataInput());

        // Verify result.
        assertTrue(areEqualLongInt(input, result ));
    }

    /**
     * Asserts that the process of writing and reading a map of Long-to-Integer through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map contains a null value.
     */
    @Test
    public void testWriteReadLongToIntMapNullValue() throws Exception
    {
        // Setup fixture.
        final Map<Long, Integer> input = new HashMap<>();
        input.put( Long.MIN_VALUE, Integer.MAX_VALUE );
        input.put( Long.MAX_VALUE, null );

        // Execute system under test.
        new DefaultExternalizableUtil().writeLongIntMap(bus.getDataOutput(), input);
        final Map<Long, Integer> result = new DefaultExternalizableUtil().readLongIntMap(bus.getDataInput());

        // Verify result.
        assertTrue(areEqualLongInt(input, result ));
    }

    /**
     * Asserts that the process of writing and reading a map of Long-to-Integer through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the map is empty.
     */
    @Test
    public void testWriteReadLongToIntMapEmpty() throws Exception
    {
        // Setup fixture.
        final Map<Long, Integer> input = new HashMap<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeLongIntMap(bus.getDataOutput(), input);
        final Map<Long, Integer> result = new DefaultExternalizableUtil().readLongIntMap(bus.getDataInput());

        // Verify result.
        assertTrue(areEqualLongInt(input, result ));
    }

    /**
     * Asserts that the process of writing and reading a List of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadStringList() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();
        input.add("foo");
        input.add("\uD834\uDD1E");

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringList(bus.getDataOutput(), input);
        final List<String> result = new DefaultExternalizableUtil().readStringList(bus.getDataInput());

        // Verify result.
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a List of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the list contains a null value.
     */
    @Test
    public void testWriteReadStringListNullValue() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();
        input.add(null);
        input.add("\uD834\uDD1E");

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringList(bus.getDataOutput(), input);
        final List<String> result = new DefaultExternalizableUtil().readStringList(bus.getDataInput());

        // Verify result.
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a List of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the list is empty.
     */
    @Test
    public void testWriteReadStringListEmpty() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeStringList(bus.getDataOutput(), input);
        final List<String> result = new DefaultExternalizableUtil().readStringList(bus.getDataInput());

        // Verify result.
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Array of Long through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadLongArray() throws Exception
    {
        // Setup fixture.
        final long[] input = new long[] { Long.MAX_VALUE, Long.MIN_VALUE };

        // Execute system under test.
        new DefaultExternalizableUtil().writeLongArray(bus.getDataOutput(), input);
        final long[] result = new DefaultExternalizableUtil().readLongArray(bus.getDataInput());

        // Verify result.
        assertArrayEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a Array of Long through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the array is empty
     */
    @Test
    public void testWriteReadLongArrayEmpty() throws Exception
    {
        // Setup fixture.
        final long[] input = new long[0];

        // Execute system under test.
        new DefaultExternalizableUtil().writeLongArray(bus.getDataOutput(), input);
        final long[] result = new DefaultExternalizableUtil().readLongArray(bus.getDataInput());

        // Verify result.
        assertArrayEquals( input, result );
    }


    /**
     * Asserts that the process of writing and reading a long value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadLong() throws Exception
    {
        // Setup fixture.
        final long input = Long.MIN_VALUE;

        // Execute system under test.
        new DefaultExternalizableUtil().writeLong(bus.getDataOutput(), input);
        final long result = new DefaultExternalizableUtil().readLong(bus.getDataInput());

        // Verify result.
        assertEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a byte array value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadByteArray() throws Exception
    {
        // Setup fixture.
        final byte[] input = new byte[] { (byte)0xe0, 0x4d, (byte)0xd0, 0x20, (byte)0xfa, 0x3d, 0x29, 0x13, (byte)0xa2, (byte)0xd8, 0x08, 0x00, 0x2b, (byte) 0x90, 0x39, (byte)0x9d };

        // Execute system under test.
        new DefaultExternalizableUtil().writeByteArray(bus.getDataOutput(), input);
        final byte[] result = new DefaultExternalizableUtil().readByteArray(bus.getDataInput());

        // Verify result.
        assertArrayEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a byte array value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the array is empty.
     */
    @Test
    public void testWriteReadByteArrayEmpty() throws Exception
    {
        // Setup fixture.
        final byte[] input = new byte[0];

        // Execute system under test.
        new DefaultExternalizableUtil().writeByteArray(bus.getDataOutput(), input);
        final byte[] result = new DefaultExternalizableUtil().readByteArray(bus.getDataInput());

        // Verify result.
        assertArrayEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading an int value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadInt() throws Exception
    {
        // Setup fixture.
        final int input = 42;

        // Execute system under test.
        new DefaultExternalizableUtil().writeInt(bus.getDataOutput(), input);
        final int result = new DefaultExternalizableUtil().readInt(bus.getDataInput());

        // Verify result.
        assertEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a boolean value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadBoolean() throws Exception
    {
        // Setup fixture.
        final boolean input = true;

        // Execute system under test.
        new DefaultExternalizableUtil().writeBoolean(bus.getDataOutput(), input);
        final boolean result = new DefaultExternalizableUtil().readBoolean(bus.getDataInput());

        // Verify result.
        assertEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a Serializable value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadSerializable() throws Exception
    {
        // Setup fixture.
        final Serializable input = UUID.randomUUID();

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializable(bus.getDataOutput(), input);
        final Serializable result = new DefaultExternalizableUtil().readSerializable(bus.getDataInput());

        // Verify result.
        assertEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a UTF value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     *
     * Values taken from https://rosettacode.org/wiki/UTF-8_encode_and_decode#Java
     */
    @Test
    public void testWriteReadSafeUTF() throws Exception
    {
        // Setup fixture.
        final String input = "Test String With Unicode characters: \u0041 \u00f6 \u0416 \u20ac \uD834\uDD1E";

        // Execute system under test.
        new DefaultExternalizableUtil().writeSafeUTF(bus.getDataOutput(), input);
        final String result = new DefaultExternalizableUtil().readSafeUTF(bus.getDataInput());

        // Verify result.
        assertEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a UTF value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the String is empty.
     */
    @Test
    public void testWriteReadSafeUTFEmpty() throws Exception
    {
        // Setup fixture.
        final String input = "";

        // Execute system under test.
        new DefaultExternalizableUtil().writeSafeUTF(bus.getDataOutput(), input);
        final String result = new DefaultExternalizableUtil().readSafeUTF(bus.getDataInput());

        // Verify result.
        assertEquals( input, result );
    }

    /**
     * Asserts that the process of writing and reading a collection of Externalizable objects through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    // TODO @Test
    public void testWriteReadExternalizableCollection() throws Exception
    {
        // TODO implement me (and variants for collections that are empty or contain null values).
    }

    /**
     * Asserts that the process of writing and reading a collection of Serializable objects through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    // TODO @Test
    public void testWriteReadSerializableCollection() throws Exception
    {
        // TODO implement me (and variants for collections that are empty or contain null values).
    }

    /**
     * Asserts that the process of writing and reading a Map of String to Externalizable objects through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    // TODO @Test
    public void testWriteReadExternalizableMap() throws Exception
    {
        // TODO implement me (and variants for collections that are empty or contain null values).
    }

    /**
     * Asserts that the process of writing and reading a Map of Serializable to Serializable objects through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    // TODO @Test
    public void testWriteReadSerializableMap() throws Exception
    {
        // TODO implement me (and variants for collections that are empty or contain null values).
    }

    /**
     * Asserts that the process of writing and reading a Collection of Strings through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadStringCollection() throws Exception
    {
        // TODO implement me (and variants for collections that are empty or contain null values).
    }

    /**
     * Checks if two maps are equal.
     *
     * @param first A map
     * @param second Another map
     * @return if both maps are equal, otherwise false.
     */
    private static boolean areEqualString(Map<String, String> first, Map<String, String> second) {
        if (first.size() != second.size()) {
            return false;
        }

        return first.entrySet().stream()
            .allMatch(e -> (e.getValue() == null && second.get(e.getKey()) == null)
                        || (e.getValue().equals(second.get(e.getKey()))) );
    }

    /**
     * Checks if two maps are equal.
     *
     * @param first A map
     * @param second Another map
     * @return if both maps are equal, otherwise false.
     */
    private static boolean areEqualStrings(Map<String, Set<String>> first, Map<String, Set<String>> second) {
        if (first.size() != second.size()) {
            return false;
        }

        return first.entrySet().stream()
            .allMatch(e -> (e.getValue() == null && second.get(e.getKey()) == null)
                || (e.getValue().equals(second.get(e.getKey()))) );
    }

    /**
     * Checks if two maps are equal.
     *
     * @param first A map
     * @param second Another map
     * @return if both maps are equal, otherwise false.
     */
    private static boolean areEqualLongInt(Map<Long, Integer> first, Map<Long, Integer> second) {
        if (first.size() != second.size()) {
            return false;
        }

        return first.entrySet().stream()
            .allMatch(e -> (e.getValue() == null && second.get(e.getKey()) == null)
                || (e.getValue().equals(second.get(e.getKey()))) );
    }

    /**
     * Utility class that generates a DataOutput and DataInput that share a common resource, meaning that what is
     * written to the DataOutput becomes available for reading on the DataInput.
     *
     * Instances are single-use. DataOutput should be written to before DataInput is obtained (this implementation was
     * designed for unit testing only, not for re-use elsewhere).
     */
    public static class Bus
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public DataOutput getDataOutput() {
            return new DataOutputStream( baos );
        }

        public DataInput getDataInput() {
            return new DataInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
        }
    }
}
