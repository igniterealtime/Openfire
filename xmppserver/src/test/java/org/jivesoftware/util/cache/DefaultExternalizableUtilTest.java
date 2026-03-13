/*
 * Copyright (C) 2020-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies functionality as implemented by {@link DefaultExternalizableUtil}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DefaultExternalizableUtilTest
{
    private Bus bus;

    @BeforeEach
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
    }

    /**
     * Asserts that writing a null map of Strings through the {@link DefaultExternalizableUtil} implementation
     * and reading it back results in null.
     */
    @Test
    public void testWriteReadStringMapNull() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeStringMap(bus.getDataOutput(), null);
        final Map<String, String> result = new DefaultExternalizableUtil().readStringMap(bus.getDataInput());

        // Verify result.
        assertNull(result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
        assertEquals(input, result);
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
     * Asserts that the process of writing and reading an Array of Long through the {@link DefaultExternalizableUtil}
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
     * Asserts that the process of writing and reading an Array of Long through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input, when the array is empty.
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
     * Asserts that writing a null long array through the {@link DefaultExternalizableUtil} implementation
     * and reading it back results in null.
     */
    @Test
    public void testWriteReadLongArrayNull() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeLongArray(bus.getDataOutput(), null);
        final long[] result = new DefaultExternalizableUtil().readLongArray(bus.getDataInput());

        // Verify result.
        assertNull(result);
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
     * Asserts that writing and reading Long.MAX_VALUE through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadLongMaxValue() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeLong(bus.getDataOutput(), Long.MAX_VALUE);
        final long result = new DefaultExternalizableUtil().readLong(bus.getDataInput());

        // Verify result.
        assertEquals(Long.MAX_VALUE, result);
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
     * Asserts that writing a null byte array through the {@link DefaultExternalizableUtil} implementation
     * and reading it back results in null.
     */
    @Test
    public void testWriteReadByteArrayNull() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeByteArray(bus.getDataOutput(), null);
        final byte[] result = new DefaultExternalizableUtil().readByteArray(bus.getDataInput());

        // Verify result.
        assertNull(result);
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
     * Asserts that writing and reading Integer.MIN_VALUE through the {@link DefaultExternalizableUtil}
     * implementation results in a value equal to the original input.
     */
    @Test
    public void testWriteReadIntMinValue() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeInt(bus.getDataOutput(), Integer.MIN_VALUE);
        final int result = new DefaultExternalizableUtil().readInt(bus.getDataInput());

        // Verify result.
        assertEquals(Integer.MIN_VALUE, result);
    }

    /**
     * Asserts that writing and reading Integer.MAX_VALUE through the {@link DefaultExternalizableUtil}
     * implementation results in a value equal to the original input.
     */
    @Test
    public void testWriteReadIntMaxValue() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeInt(bus.getDataOutput(), Integer.MAX_VALUE);
        final int result = new DefaultExternalizableUtil().readInt(bus.getDataInput());

        // Verify result.
        assertEquals(Integer.MAX_VALUE, result);
    }

    /**
     * Asserts that the process of writing and reading a boolean value of {@code true} through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadBooleanTrue() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeBoolean(bus.getDataOutput(), true);
        final boolean result = new DefaultExternalizableUtil().readBoolean(bus.getDataInput());

        // Verify result.
        assertTrue(result);
    }

    /**
     * Asserts that the process of writing and reading a boolean value of {@code false} through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadBooleanFalse() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeBoolean(bus.getDataOutput(), false);
        final boolean result = new DefaultExternalizableUtil().readBoolean(bus.getDataInput());

        // Verify result.
        assertFalse(result);
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
     * Asserts that writing a null Serializable through the {@link DefaultExternalizableUtil} implementation
     * and reading it back results in null.
     */
    @Test
    public void testWriteReadSerializableNull() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializable(bus.getDataOutput(), null);
        final Serializable result = new DefaultExternalizableUtil().readSerializable(bus.getDataInput());

        // Verify result.
        assertNull(result);
    }

    /**
     * Asserts that the process of writing and reading a UTF value through the {@link DefaultExternalizableUtil}
     * implementation results in a value that is equal to the original input.
     *
     * Values taken from <a href="https://rosettacode.org/wiki/UTF-8_encode_and_decode#Java">Rosetta Code</a>
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
     * Asserts that writing a null String through the {@link DefaultExternalizableUtil} implementation
     * and reading it back results in null.
     */
    @Test
    public void testWriteReadSafeUTFNull() throws Exception
    {
        // Execute system under test.
        new DefaultExternalizableUtil().writeSafeUTF(bus.getDataOutput(), null);
        final String result = new DefaultExternalizableUtil().readSafeUTF(bus.getDataInput());

        // Verify result.
        assertNull(result);
    }

    /**
     * Asserts that the process of writing and reading a collection of Externalizable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadExternalizableCollection() throws Exception
    {
        // Setup fixture.
        final List<TestExternalizable> input = new ArrayList<>();
        input.add(new TestExternalizable("alpha"));
        input.add(new TestExternalizable("beta"));

        // Execute system under test.
        new DefaultExternalizableUtil().writeExternalizableCollection(bus.getDataOutput(), input);
        final List<TestExternalizable> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readExternalizableCollection(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a collection of Externalizable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the collection is empty.
     */
    @Test
    public void testWriteReadExternalizableCollectionEmpty() throws Exception
    {
        // Setup fixture.
        final List<TestExternalizable> input = new ArrayList<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeExternalizableCollection(bus.getDataOutput(), input);
        final List<TestExternalizable> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readExternalizableCollection(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(0, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a collection of Externalizable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the collection contains a null value.
     */
    @Test
    public void testWriteReadExternalizableCollectionNullValue() throws Exception
    {
        // Setup fixture.
        final List<TestExternalizable> input = new ArrayList<>();
        input.add(new TestExternalizable("alpha"));
        input.add(null);

        // Execute system under test.
        new DefaultExternalizableUtil().writeExternalizableCollection(bus.getDataOutput(), input);
        final List<TestExternalizable> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readExternalizableCollection(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a collection of Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadSerializableCollection() throws Exception
    {
        // Setup fixture.
        final List<UUID> input = new ArrayList<>();
        input.add(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        input.add(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableCollection(bus.getDataOutput(), input);
        final List<UUID> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableCollection(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a collection of Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the collection is empty.
     */
    @Test
    public void testWriteReadSerializableCollectionEmpty() throws Exception
    {
        // Setup fixture.
        final List<UUID> input = new ArrayList<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableCollection(bus.getDataOutput(), input);
        final List<UUID> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableCollection(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(0, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a collection of Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the collection contains a null value.
     */
    @Test
    public void testWriteReadSerializableCollectionNullValue() throws Exception
    {
        // Setup fixture.
        final List<UUID> input = new ArrayList<>();
        input.add(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        input.add(null);

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableCollection(bus.getDataOutput(), input);
        final List<UUID> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableCollection(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of String to Externalizable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadExternalizableMap() throws Exception
    {
        // Setup fixture.
        final Map<String, TestExternalizable> input = new HashMap<>();
        input.put("first", new TestExternalizable("alpha"));
        input.put("second", new TestExternalizable("beta"));

        // Execute system under test.
        new DefaultExternalizableUtil().writeExternalizableMap(bus.getDataOutput(), input);
        final Map<String, TestExternalizable> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readExternalizableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of String to Externalizable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the map is empty.
     */
    @Test
    public void testWriteReadExternalizableMapEmpty() throws Exception
    {
        // Setup fixture.
        final Map<String, TestExternalizable> input = new HashMap<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeExternalizableMap(bus.getDataOutput(), input);
        final Map<String, TestExternalizable> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readExternalizableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(0, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of String to Externalizable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the map contains a null value.
     */
    @Test
    public void testWriteReadExternalizableMapNullValue() throws Exception
    {
        // Setup fixture.
        final Map<String, TestExternalizable> input = new HashMap<>();
        input.put("first", new TestExternalizable("alpha"));
        input.put("second", null);

        // Execute system under test.
        new DefaultExternalizableUtil().writeExternalizableMap(bus.getDataOutput(), input);
        final Map<String, TestExternalizable> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readExternalizableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of Serializable to Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * using String keys.
     */
    @Test
    public void testWriteReadSerializableMapStringKeys() throws Exception
    {
        // Setup fixture.
        final Map<String, UUID> input = new HashMap<>();
        input.put("first", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        input.put("second", UUID.fromString("00000000-0000-0000-0000-000000000002"));

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableMap(bus.getDataOutput(), input);
        final Map<String, UUID> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of Serializable to Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * using non-String Serializable keys (Integer). This specifically exercises the fix for the bug where the
     * deserialized result was incorrectly narrowed to {@code Map<String, Serializable>}.
     */
    @Test
    public void testWriteReadSerializableMapNonStringKeys() throws Exception
    {
        // Setup fixture.
        final Map<Integer, UUID> input = new HashMap<>();
        input.put(1, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        input.put(2, UUID.fromString("00000000-0000-0000-0000-000000000002"));

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableMap(bus.getDataOutput(), input);
        final Map<Integer, UUID> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of Serializable to Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the map is empty.
     */
    @Test
    public void testWriteReadSerializableMapEmpty() throws Exception
    {
        // Setup fixture.
        final Map<String, UUID> input = new HashMap<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableMap(bus.getDataOutput(), input);
        final Map<String, UUID> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(0, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Map of Serializable to Serializable objects through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the map contains a null value.
     */
    @Test
    public void testWriteReadSerializableMapNullValue() throws Exception
    {
        // Setup fixture.
        final Map<String, UUID> input = new HashMap<>();
        input.put("first", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        input.put("second", null);

        // Execute system under test.
        new DefaultExternalizableUtil().writeSerializableMap(bus.getDataOutput(), input);
        final Map<String, UUID> result = new HashMap<>();
        final int reportedCount = new DefaultExternalizableUtil().readSerializableMap(
            bus.getDataInput(), result, getClass().getClassLoader());

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Collection of Strings through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input.
     */
    @Test
    public void testWriteReadStringCollection() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();
        input.add("foo");
        input.add("\uD834\uDD1E");

        // Execute system under test.
        new DefaultExternalizableUtil().writeStrings(bus.getDataOutput(), input);
        final List<String> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readStrings(bus.getDataInput(), result);

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Collection of Strings through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the collection is empty.
     */
    @Test
    public void testWriteReadStringCollectionEmpty() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();

        // Execute system under test.
        new DefaultExternalizableUtil().writeStrings(bus.getDataOutput(), input);
        final List<String> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readStrings(bus.getDataInput(), result);

        // Verify result.
        assertEquals(0, reportedCount);
        assertEquals(input, result);
    }

    /**
     * Asserts that the process of writing and reading a Collection of Strings through the
     * {@link DefaultExternalizableUtil} implementation results in a value that is equal to the original input,
     * when the collection contains a null value.
     */
    @Test
    public void testWriteReadStringCollectionNullValue() throws Exception
    {
        // Setup fixture.
        final List<String> input = new ArrayList<>();
        input.add("foo");
        input.add(null);

        // Execute system under test.
        new DefaultExternalizableUtil().writeStrings(bus.getDataOutput(), input);
        final List<String> result = new ArrayList<>();
        final int reportedCount = new DefaultExternalizableUtil().readStrings(bus.getDataInput(), result);

        // Verify result.
        assertEquals(2, reportedCount);
        assertEquals(input, result);
    }

    /**
     * A minimal {@link Externalizable} implementation for use in tests. Two instances are considered equal if
     * their {@code value} fields are equal.
     */
    public static class TestExternalizable implements Externalizable
    {
        private String value;

        /** Required no-arg constructor for {@link Externalizable}. */
        public TestExternalizable() {}

        public TestExternalizable(final String value) {
            this.value = value;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            DefaultExternalizableUtil.writeObject((DataOutput) out, value);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            value = (String) DefaultExternalizableUtil.readObject((DataInput) in);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof TestExternalizable)) return false;
            return Objects.equals(value, ((TestExternalizable) o).value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "TestExternalizable{value='" + value + "'}";
        }
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
