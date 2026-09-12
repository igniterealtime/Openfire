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
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the default method implementations on {@link Sasl2Task}. A minimal implementation that overrides only
 * the interface's abstract methods is used, so that the defaults are exercised unmodified.
 */
class Sasl2TaskTest
{
    /**
     * A minimal task that overrides nothing but the interface's abstract methods.
     */
    private final Sasl2Task task = new Sasl2Task()
    {
        @Override
        public String getName()
        {
            return "test-task";
        }

        @Override
        public Sasl2TaskResult begin(Element next)
        {
            throw new UnsupportedOperationException("Not used by this test.");
        }
    };

    @Test
    void onTaskDataDefaultThrowsSaslFailureException()
    {
        // Setup test fixture.
        final Element taskData = DocumentHelper.createElement("task-data");

        // Execute system under test & Verify result.
        assertThrows(SaslFailureException.class, () -> task.onTaskData(taskData), "The default onTaskData() must abort the negotiation for a task that never expects data back");
    }

    @Test
    void onTaskDataDefaultUsesMalformedRequestFailure()
    {
        // Setup test fixture.
        final Element taskData = DocumentHelper.createElement("task-data");

        // Execute system under test.
        final SaslFailureException exception = assertThrows(SaslFailureException.class, () -> task.onTaskData(taskData), "The default onTaskData() must throw so that the negotiation is not left hanging");

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, exception.getFailure(), "The default onTaskData() must report MALFORMED_REQUEST, since receiving data was not expected");
    }

    @Test
    void onTaskDataDefaultExceptionMentionsTaskName()
    {
        // Setup test fixture.
        final Element taskData = DocumentHelper.createElement("task-data");

        // Execute system under test.
        final SaslFailureException exception = assertThrows(SaslFailureException.class, () -> task.onTaskData(taskData), "The default onTaskData() must throw so its message can be inspected");

        // Verify result.
        assertTrue(exception.getMessage().contains("test-task"), "The exception message should name the task, to help diagnose which task rejected the data");
    }

    @Test
    void onAbortedDefaultDoesNotThrow()
    {
        // Execute system under test & Verify result.
        assertDoesNotThrow(task::onAborted, "The default onAborted() must be a no-op that never throws, since it runs during teardown");
    }
}
