/*
 * Copyright (C) 2021-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that verify the functionality of {@link CollectionUtils}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CollectionUtilsTest
{
    /**
     * Verifies that {@link CollectionUtils#findDuplicates(Collection[])} does not find duplicates when the provided
     * singular collection does not have any.
     */
    @Test
    public void testSingleCollectionWithoutDuplicates() throws Exception
    {
        // Setup test fixture.
        final List<String> input = Arrays.asList("a", "b", "c");

        // Execute system under test.
        @SuppressWarnings("unchecked")
        final Set<String> result = CollectionUtils.findDuplicates(input);

        // Verify results.
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that {@link CollectionUtils#findDuplicates(Collection[])} does not find duplicates when the provided
     * singular collection does not have any.
     */
    @Test
    public void testSingleCollectionWithDuplicates() throws Exception
    {
        // Setup test fixture.
        final List<String> input = Arrays.asList("a", "DUPLICATE", "c", "DUPLICATE");

        // Execute system under test.
        @SuppressWarnings("unchecked")
        final Set<String> result = CollectionUtils.findDuplicates(input);

        // Verify results.
        assertEquals(1, result.size());
        assertTrue(result.contains("DUPLICATE"));
    }

    /**
     * Verifies that {@link CollectionUtils#findDuplicates(Collection[])} finds duplicates when the provided
     * collections have some.
     */
    @Test
    public void testMultipleCollectionsWithoutDuplicates() throws Exception
    {
        // Setup test fixture.
        final List<String> input1 = Arrays.asList("a", "b");
        final List<String> input2 = Arrays.asList("c", "d");
        final List<String> input3 = Arrays.asList("e", "f", "g");

        // Execute system under test.
        @SuppressWarnings("unchecked")
        final Set<String> result = CollectionUtils.findDuplicates(input1, input2, input3);

        // Verify results.
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that {@link CollectionUtils#findDuplicates(Collection[])} finds duplicates when the provided
     * collections have some.
     */
    @Test
    public void testMultipleCollectionsWithDuplicates() throws Exception
    {
        // Setup test fixture.
        final List<String> input1 = Arrays.asList("a", "b");
        final List<String> input2 = Arrays.asList("DUPLICATE", "d");
        final List<String> input3 = Arrays.asList("e", "DUPLICATE", "g");

        // Execute system under test.
        @SuppressWarnings("unchecked")
        final Set<String> result = CollectionUtils.findDuplicates(input1, input2, input3);

        // Verify results.
        assertEquals(1, result.size());
        assertTrue(result.contains("DUPLICATE"));
    }

}
