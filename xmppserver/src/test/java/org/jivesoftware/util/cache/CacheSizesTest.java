/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.jivesoftware.openfire.OfflineMessage;
import org.jivesoftware.util.SAXReaderUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link CacheSizes} utility class.
 * 
 * This test suite verifies the accuracy and behavior of the size estimation methods for various Java objects,
 * primitives, collections, maps, arrays, and objects implementing {@link Cacheable}.
 */
class CacheSizesTest
{
    /**
     * Minimal {@link Cacheable} implementation for testing.
     */
    static class TestCacheable implements Cacheable {
        private final int size;

        TestCacheable(int size) {
            this.size = size;
        }

        @Override
        public int getCachedSize() {
            return size;
        }
    }

    /**
     * Minimal {@link Packet} implementation for testing.
     */
    static class TestPacket extends Packet
    {
        @Override
        public Element getElement() {
            try {
                return SAXReaderUtil.readRootElement("<message to='john@example.org' from='jane@example.org/resource'><body>test body</body><offline/></message>");
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JID getFrom() {
            return new JID("john", "example.org", null);
        }

        @Override
        public JID getTo() {
            return new JID("jane", "example.org", "resource");
        }

        @Override
        public Packet createCopy()
        {
            throw new UnsupportedOperationException("Should not be used during unit testing.");
        }
    }

    /**
     * A simple serializable class for testing fallback serialization.
     */
    static class SerializableTestObject implements Serializable {
        private static final long serialVersionUID = 1L;
        int a = 10;
        String s = "test";
    }

    /**
     * Tests the sizes of primitive types and basic object overhead.
     */
    @Test
    void testPrimitiveSizes() {
        assertEquals(4, CacheSizes.sizeOfInt());
        assertEquals(2, CacheSizes.sizeOfChar());
        assertEquals(1, CacheSizes.sizeOfBoolean());
        assertEquals(8, CacheSizes.sizeOfLong());
        assertEquals(8, CacheSizes.sizeOfDouble());
        assertEquals(4, CacheSizes.sizeOfObject());
        assertEquals(12, CacheSizes.sizeOfDate());
    }

    /**
     * Tests the size estimation of {@link String} objects.
     */
    @Test
    void testStringSize() {
        String s = "hello";
        int expected = 4 + s.getBytes().length;
        assertEquals(expected, CacheSizes.sizeOfString(s));
        assertEquals(0, CacheSizes.sizeOfString(null));
    }

    /**
     * Tests that {@link Cacheable} objects return their own cached size.
     */
    @Test
    void testCacheableObject() throws CannotCalculateSizeException {
        TestCacheable obj = new TestCacheable(42);
        assertEquals(42, CacheSizes.sizeOfAnything(obj));
    }

    /**
     * Tests size calculation of wrapper types: {@link Integer}, {@link Long},
     * {@link Double}, and {@link Boolean}.
     */
    @Test
    void testIntegerLongDoubleBoolean() throws CannotCalculateSizeException {
        assertEquals(CacheSizes.sizeOfObject() + CacheSizes.sizeOfInt(),
            CacheSizes.sizeOfAnything(Integer.valueOf(10)));
        assertEquals(CacheSizes.sizeOfLong(), CacheSizes.sizeOfAnything(Long.valueOf(10L)));
        assertEquals(CacheSizes.sizeOfObject() + CacheSizes.sizeOfDouble(),
            CacheSizes.sizeOfAnything(Double.valueOf(3.14)));
        assertEquals(CacheSizes.sizeOfObject() + CacheSizes.sizeOfBoolean(),
            CacheSizes.sizeOfAnything(Boolean.TRUE));
    }

    /**
     * Tests size calculation of {@link Map} objects with multiple entries.
     */
    @Test
    void testMapSize() throws CannotCalculateSizeException {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        int size = CacheSizes.sizeOfMap(map);
        assertTrue(size > 0);
    }

    /**
     * Tests size calculation of {@link Collection} objects with mixed elements.
     */
    @Test
    void testCollectionSize() throws CannotCalculateSizeException {
        List<Object> list = new ArrayList<>();
        list.add("test");
        list.add(42);
        list.add(new TestCacheable(10));
        int size = CacheSizes.sizeOfCollection(list);
        assertTrue(size > 0);
    }

    /**
     * Tests size calculation for arrays of {@code long} and {@code byte}.
     */
    @Test
    void testArraySizes() throws CannotCalculateSizeException {
        long[] longArray = {1L, 2L, 3L};
        byte[] byteArray = {1, 2, 3, 4};
        assertEquals(CacheSizes.sizeOfObject() + 3 * CacheSizes.sizeOfLong(),
            CacheSizes.sizeOfAnything(longArray));
        assertEquals(CacheSizes.sizeOfObject() + 4,
            CacheSizes.sizeOfAnything(byteArray));
    }

    /**
     * Tests size calculation for {@link Packet} and {@link OfflineMessage} objects.
     */
    @Test
    void testPacketSize() throws CannotCalculateSizeException, ExecutionException, InterruptedException
    {
        TestPacket packet = new TestPacket();
        int size = CacheSizes.sizeOfPacket(packet);
        assertTrue(size > 0);

        OfflineMessage offlineMessage = new OfflineMessage(new Date(), SAXReaderUtil.readRootElement("<message to='john@example.org' from='jane@example.org/resource'><body>test body</body><offline/></message>"));
        size = CacheSizes.sizeOfPacket(offlineMessage);
        assertTrue(size > 0);
    }

    /**
     * Tests the fallback serialization-based size calculation for unknown objects.
     * Uses a Serializable object to ensure serialization succeeds.
     */
    @Test
    void testFallbackSerialization() throws CannotCalculateSizeException {
        // Do not use an anonymous class. Even if they implement Serializable, they often have issues with default
        // serialization, especially if they capture enclosing state or contain non-serializable fields.
        // ObjectOutputStream can fail on such classes.
        int size = CacheSizes.sizeOfAnything(new SerializableTestObject());
        assertTrue(size > 0, "Serialized object size should be greater than 0");
    }

    /**
     * Tests that null inputs return size 0 for all relevant methods.
     */
    @Test
    void testNullObjects() throws CannotCalculateSizeException {
        assertEquals(0, CacheSizes.sizeOfAnything(null));
        assertEquals(0, CacheSizes.sizeOfMap(null));
        assertEquals(0, CacheSizes.sizeOfCollection(null));
        assertEquals(0, CacheSizes.sizeOfPacket(null));
    }

    /**
     * Tests that sizeOfAnything throws CannotCalculateSizeException
     * for an anonymous non-serializable object.
     */
    @Test
    void testFallbackSerializationFailsAnonymous() {
        Object obj = new Object() {
            int a = 10;
            String s = "test";
        };

        assertThrows(
            CannotCalculateSizeException.class,
            () -> CacheSizes.sizeOfAnything(obj),
            "Expected CannotCalculateSizeException for non-serializable anonymous object"
        );
    }
}
