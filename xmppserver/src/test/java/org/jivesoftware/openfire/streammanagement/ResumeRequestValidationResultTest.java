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
package org.jivesoftware.openfire.streammanagement;

import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.PacketError;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Verifies {@link ResumeRequestValidationResult}.
 */
public class ResumeRequestValidationResultTest
{
    /**
     * Verifies that a successful result reports success, exposes the target session, and has no failure condition.
     */
    @Test
    public void testSuccess() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession target = mock(LocalClientSession.class);

        // Execute system under test.
        final ResumeRequestValidationResult result = ResumeRequestValidationResult.success(target);

        // Verify result.
        assertTrue(result.isSuccess(), "Expected a success result to report success.");
        assertEquals(target, result.getTarget(), "Expected a success result to expose the provided target session.");
        assertNull(result.getFailureCondition(), "Expected a success result to have no failure condition.");
    }

    /**
     * Verifies that a failure result reports no success, exposes the failure condition, and has no target session.
     */
    @Test
    public void testFailure() throws Exception
    {
        // Execute system under test.
        final ResumeRequestValidationResult result = ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);

        // Verify result.
        assertFalse(result.isSuccess(), "Expected a failure result to not report success.");
        assertNull(result.getTarget(), "Expected a failure result to expose no target session.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "Expected a failure result to expose the provided failure condition.");
    }
}
