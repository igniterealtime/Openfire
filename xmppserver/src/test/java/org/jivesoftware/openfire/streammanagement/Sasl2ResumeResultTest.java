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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Verifies {@link Sasl2ResumeResult}.
 */
public class Sasl2ResumeResultTest
{
    /**
     * Verifies that a successful result reports success, and exposes the resumed session and result element.
     */
    @Test
    public void testSuccess() throws Exception
    {
        // Setup test fixture.
        final Element resumedElement = DocumentHelper.createElement("resumed");
        final LocalClientSession resumedSession = mock(LocalClientSession.class);

        // Execute system under test.
        final Sasl2ResumeResult result = Sasl2ResumeResult.success(resumedElement, resumedSession);

        // Verify result.
        assertTrue(result.isSuccess(), "Expected a success result to report success.");
        assertEquals(resumedElement, result.getResultElement(), "Expected a success result to expose the provided result element.");
        assertEquals(resumedSession, result.getResumedSession(), "Expected a success result to expose the provided resumed session.");
    }

    /**
     * Verifies that a failure result reports no success, exposes the failure element, and has no resumed session.
     */
    @Test
    public void testFailure() throws Exception
    {
        // Setup test fixture.
        final Element failedElement = DocumentHelper.createElement("failed");

        // Execute system under test.
        final Sasl2ResumeResult result = Sasl2ResumeResult.failure(failedElement);

        // Verify result.
        assertFalse(result.isSuccess(), "Expected a failure result to not report success.");
        assertEquals(failedElement, result.getResultElement(), "Expected a failure result to expose the provided result element.");
        assertNull(result.getResumedSession(), "Expected a failure result to expose no resumed session.");
    }
}
