/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.sasl.task;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Sasl2TaskResult}, verifying its factory methods produce results of the correct type and that
 * the immutability/defensive-copy guarantees documented on the class hold.
 */
class Sasl2TaskResultTest
{
    @Test
    void taskDataResultHasTypeTaskData()
    {
        // Setup test fixture.
        final Element child = DocumentHelper.createElement("payload");

        // Execute system under test.
        final Sasl2TaskResult result = Sasl2TaskResult.taskData(child);

        // Verify result.
        assertEquals(Sasl2TaskResult.Type.TASK_DATA, result.getType(),"A result built via taskData() must report type TASK_DATA");
    }

    @Test
    void taskDataResultContainsGivenChildrenAsCopies()
    {
        // Setup test fixture.
        final Element child = DocumentHelper.createElement("payload");

        // Execute system under test.
        final Sasl2TaskResult result = Sasl2TaskResult.taskData(child);

        // Verify result.
        assertNotSame(child, result.getTaskData().get(0), "taskData() must store a defensive copy, not the original element instance");
        assertEquals("payload", result.getTaskData().get(0).getName(), "The copied element must have the same name as the original");
    }

    @Test
    void emptyTaskDataResultHasNoChildren()
    {
        // Setup test fixture & Execute system under test.
        final Sasl2TaskResult result = Sasl2TaskResult.taskData();

        // Verify result.
        assertTrue(result.getTaskData().isEmpty(), "Calling taskData() with no children must produce an empty (not null) list");
    }

    @Test
    void completedResultHasTypeCompleted()
    {
        // Execute system under test.
        final Sasl2TaskResult result = Sasl2TaskResult.completed();

        // Verify result.
        assertEquals(Sasl2TaskResult.Type.COMPLETED, result.getType(), "A result built via completed() must report type COMPLETED");
    }

    @Test
    void completedResultWithoutDataHasNullAdditionalData()
    {
        // Execute system under test.
        final Sasl2TaskResult result = Sasl2TaskResult.completed();

        // Verify result.
        assertNull(result.getAdditionalData(), "completed() with no argument must yield null additional data");
    }

    @Test
    void completedResultPreservesAdditionalData()
    {
        // Setup test fixture.
        final byte[] data = {1, 2, 3};

        // Execute system under test.
        final Sasl2TaskResult result = Sasl2TaskResult.completed(data);

        // Verify result.
        assertArrayEquals(data, result.getAdditionalData(), "completed(byte[]) must return the same bytes that were passed in");
    }

    @Test
    void additionalDataIsDefensivelyCopiedOnInput()
    {
        // Setup test fixture.
        final byte[] data = {1, 2, 3};
        final Sasl2TaskResult result = Sasl2TaskResult.completed(data);

        // Execute system under test.
        data[0] = 99;

        // Verify result.
        assertEquals(1, result.getAdditionalData()[0], "Mutating the caller's array after construction must not affect the stored result");
    }

    @Test
    void additionalDataIsDefensivelyCopiedOnOutput()
    {
        // Setup test fixture.
        final Sasl2TaskResult result = Sasl2TaskResult.completed(new byte[]{1, 2, 3});

        // Execute system under test.
        final byte[] returned = result.getAdditionalData();
        returned[0] = 99;

        // Verify result.
        assertEquals(1, result.getAdditionalData()[0], "Mutating a previously-returned array must not affect the stored result on a later call");
    }
}
