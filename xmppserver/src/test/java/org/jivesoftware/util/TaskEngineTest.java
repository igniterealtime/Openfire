/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to validate the functionality of @{link {@link TaskEngine}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TaskEngineTest
{
    /**
     * Asserts that the Future instance that is returned when submitting a Runnable through
     * {@link TaskEngine#submit(Runnable)} can be used find out the execution state of the job.
     */
    @Test
    public void testRunnableFuture() throws Exception
    {
        // Setup test fixture.
        final Runnable job = () -> {
            // A very low-effort job.
        };

        // Execute system under test.
        final Future<?> result = TaskEngine.getInstance().submit(job);

        // Verify results.
        assertNull(result.get(1, TimeUnit.MINUTES));
    }

    /**
     * Asserts that the Future instance that is returned when submitting a Runnable through
     * {@link TaskEngine#submit(Runnable)} returns an exception thrown during the execution of the job.
     */
    @Test
    public void testRunnableFutureWithException() throws Exception
    {
        // Setup test fixture.
        final Runnable job = () -> {
            throw new RuntimeException("Thrown as part of a unit test.");
        };

        // Execute system under test.
        final Future<?> result = TaskEngine.getInstance().submit(job);

        // Verify results.
        final ExecutionException ex = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.MINUTES));
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertEquals("Thrown as part of a unit test.", ex.getCause().getMessage());
    }

    /**
     * Asserts that the Future instance that is returned when submitting a Callable through
     * {@link TaskEngine#submit(Callable)} can be used to obtain the value that is computed by the job.
     */
    @Test
    public void testCallableFutureWithResult() throws Exception
    {
        // Setup test fixture.
        final Callable<Integer> job = () -> 42;

        // Execute system under test.
        final Future<Integer> result = TaskEngine.getInstance().submit(job);

        // Verify results.
        assertEquals(42, result.get(1, TimeUnit.MINUTES));
    }

    /**
     * Asserts that the Future instance that is returned when submitting a Callable through
     * {@link TaskEngine#submit(Callable)} returns an exception thrown during the execution of the job.
     */
    @Test
    public void testCallableFutureWithException() throws Exception
    {
        // Setup test fixture.
        final Callable<Integer> job = () -> { throw new RuntimeException("Thrown as part of a unit test."); };

        // Execute system under test.
        final Future<Integer> result = TaskEngine.getInstance().submit(job);

        // Verify results.
        final ExecutionException ex = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.MINUTES));
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertEquals("Thrown as part of a unit test.", ex.getCause().getMessage());
    }
}
