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
import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the default method implementations on {@link Sasl2TaskProvider}. A minimal implementation that
 * overrides only the interface's abstract methods is used, so that the defaults are exercised unmodified.
 */
class Sasl2TaskProviderTest
{
    /**
     * A minimal provider that overrides nothing but the interface's abstract methods.
     */
    private final Sasl2TaskProvider provider = new Sasl2TaskProvider()
    {
        @Override
        @Nonnull
        public String getIdentifier()
        {
            return "test-provider";
        }

        @Override
        @Nonnull
        public Set<String> getTaskNames()
        {
            return Set.of("test-task");
        }

        @Override
        @Nonnull
        public List<String> getOfferedTasks(@Nonnull Sasl2TaskContext context)
        {
            return Collections.emptyList();
        }

        @Override
        @Nonnull
        public Sasl2Task createTask(@Nonnull String taskName, @Nonnull Sasl2TaskContext context)
        {
            throw new UnsupportedOperationException("Not used by this test.");
        }
    };

    @Test
    void getStreamFeatureElementsDefaultsToEmptyList()
    {
        // Setup test fixture. (The default implementation does not touch its argument.)
        final LocalSession session = null;

        // Execute system under test.
        final List<Element> result = provider.getStreamFeatureElements(session);

        // Verify result.
        assertTrue(result.isEmpty(), "The default getStreamFeatureElements() must advertise nothing unless overridden");
    }

    @Test
    void onAuthenticateReceivedDefaultDoesNotThrow()
    {
        // Setup test fixture. (The default implementation does not touch the context argument.)
        final Sasl2TaskContext context = null;
        final Element authenticate = DocumentHelper.createElement("authenticate");

        // Execute system under test & Verify result.
        assertDoesNotThrow(() -> provider.onAuthenticateReceived(context, authenticate), "The default onAuthenticateReceived() must be a no-op that never rejects the request");
    }

    @Test
    void getContinueTextDefaultsToEmptyOptional()
    {
        // Setup test fixture. (The default implementation does not touch its argument.)
        final Sasl2TaskContext context = null;

        // Execute system under test.
        final var result = provider.getContinueText(context);

        // Verify result.
        assertTrue(result.isEmpty(), "The default getContinueText() must contribute no text unless overridden");
    }

    @Test
    void onNegotiationEndedDefaultDoesNotThrow()
    {
        // Setup test fixture. (The default implementation does not touch its arguments.)
        final Sasl2TaskContext context = null;

        // Execute system under test & Verify result.
        assertDoesNotThrow(() -> provider.onNegotiationEnded(context, true), "The default onNegotiationEnded() must be a no-op regardless of the outcome flag");
    }

    @Test
    void getPriorityDefaultsToZero()
    {
        // Execute system under test.
        final int result = provider.getPriority();

        // Verify result.
        assertEquals(0, result, "The default priority must be zero unless overridden");
    }
}
