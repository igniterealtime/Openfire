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

import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Sasl2Negotiation}, covering its state machine, attribute storage, and the
 * {@link Sasl2TaskContext} view it hands out to providers.
 */
class Sasl2NegotiationTest
{
    private Sasl2Negotiation negotiation;
    private Sasl2TaskProvider provider;

    @BeforeEach
    void setUp()
    {
        final LocalSession session = mock(LocalSession.class);
        negotiation = new Sasl2Negotiation(session, "SCRAM-SHA-256");
        provider = mock(Sasl2TaskProvider.class);
        when(provider.getIdentifier()).thenReturn("provider-id");
    }

    @Test
    void newNegotiationStartsInPreAuthenticationState()
    {
        // Verify result.
        assertEquals(Sasl2Negotiation.State.PRE_AUTHENTICATION, negotiation.getState(), "A freshly constructed negotiation must start in PRE_AUTHENTICATION");
    }

    @Test
    void setAuthenticationResultMarksNegotiationAuthenticated()
    {
        // Execute system under test.
        negotiation.setAuthenticationResult("alice", "SCRAM-SHA-256", null);

        // Verify result.
        assertTrue(negotiation.isAuthenticated(), "isAuthenticated() must return true after setAuthenticationResult() was called");
        assertEquals(Sasl2Negotiation.State.AUTHENTICATED, negotiation.getState(), "State must transition to AUTHENTICATED after setAuthenticationResult()");
    }

    @Test
    void setAuthenticationResultSupersedesProvisionalMechanismName()
    {
        // Execute system under test.
        negotiation.setAuthenticationResult("alice", "scram-sha-512", null);

        // Verify result.
        assertEquals("SCRAM-SHA-512", negotiation.contextFor(provider).getSaslMechanismName(), "The confirmed mechanism name must replace the provisional one, upper-cased");
    }

    @Test
    void setAuthenticationResultIgnoresNullMechanismName()
    {
        // Execute system under test.
        negotiation.setAuthenticationResult("alice", null, null);

        // Verify result.
        assertEquals("SCRAM-SHA-256", negotiation.contextFor(provider).getSaslMechanismName(), "A null mechanism name argument must not overwrite the provisional mechanism name");
    }

    @Test
    void setStateRejectsFinished()
    {
        // Verify result.
        assertThrows(IllegalArgumentException.class, () -> negotiation.setState(Sasl2Negotiation.State.FINISHED), "setState() must reject FINISHED; markFinished() is the only supported way to reach it");
    }

    @Test
    void markFinishedReturnsTrueOnFirstCall()
    {
        // Execute system under test.
        final boolean result = negotiation.markFinished();

        // Verify result.
        assertTrue(result, "The first call to markFinished() must report that it performed the transition");
    }

    @Test
    void markFinishedReturnsFalseOnSecondCall()
    {
        // Setup test fixture.
        negotiation.markFinished();

        // Execute system under test.
        final boolean result = negotiation.markFinished();

        // Verify result.
        assertFalse(result, "A second call to markFinished() must report that teardown already happened");
    }

    @Test
    void completeActiveTaskWithoutActiveTaskThrows()
    {
        // Verify result.
        assertThrows(IllegalStateException.class, negotiation::completeActiveTask, "completeActiveTask() must reject being called when no task is active, as this suggests a bug");
    }

    @Test
    void completeActiveTaskRecordsTaskName()
    {
        // Setup test fixture.
        final Sasl2Task task = mock(Sasl2Task.class);
        when(task.getName()).thenReturn("my-task");
        negotiation.setActiveTask(task);

        // Execute system under test.
        negotiation.completeActiveTask();

        // Verify result.
        assertEquals(List.of("my-task"), negotiation.getCompletedTaskNames(), "completeActiveTask() must append the active task's name to the completed-task list");
        assertNull(negotiation.getActiveTask(), "completeActiveTask() must clear the active task once it has completed");
    }

    @Test
    void beginRoundClearsPreviousActiveTask()
    {
        // Setup test fixture.
        final Sasl2Task task = mock(Sasl2Task.class);
        negotiation.setActiveTask(task);

        // Execute system under test.
        negotiation.beginRound(List.of());

        // Verify result.
        assertNull(negotiation.getActiveTask(), "Starting a new round must discard any task left over from a previous round");
        assertEquals(Sasl2Negotiation.State.AWAITING_NEXT, negotiation.getState(), "Starting a new round must move the state to AWAITING_NEXT");
    }

    @Test
    void findOfferReturnsNullWhenNotOffered()
    {
        // Verify result.
        assertNull(negotiation.findOffer("unknown-task"), "findOffer() must return null for a task name that was never offered");
    }

    @Test
    void contextAttributesAreScopedPerProvider()
    {
        // Setup test fixture.
        final Sasl2TaskProvider otherProvider = mock(Sasl2TaskProvider.class);
        when(otherProvider.getIdentifier()).thenReturn("other-provider-id");

        final Sasl2TaskContext context = negotiation.contextFor(provider);
        final Sasl2TaskContext otherContext = negotiation.contextFor(otherProvider);

        // Execute system under test.
        context.setAttribute("key", "provider-value");
        otherContext.setAttribute("key", "other-value");

        // Verify result.
        assertEquals("provider-value", context.getAttribute("key"), "A provider's own attribute value must be unaffected by another provider using the same key");
    }

    @Test
    void contextGetAttributeTypedReturnsEmptyForWrongType()
    {
        // Setup test fixture.
        final Sasl2TaskContext context = negotiation.contextFor(provider);
        context.setAttribute("key", "a string, not an Integer");

        // Execute system under test.
        final Optional<Integer> result = context.getAttribute("key", Integer.class);

        // Verify result.
        assertTrue(result.isEmpty(), "getAttribute(name, type) must return empty when the stored value is not of the requested type");
    }

    @Test
    void contextSetAttributeToNullRemovesIt()
    {
        // Setup test fixture.
        final Sasl2TaskContext context = negotiation.contextFor(provider);
        context.setAttribute("key", "value");

        // Execute system under test.
        context.setAttribute("key", null);

        // Verify result.
        assertNull(context.getAttribute("key"), "Setting an attribute to null must remove any previously stored value");
    }

    @Test
    void contextIsNotAuthenticatedBeforeAuthenticationResultIsSet()
    {
        // Verify result.
        assertFalse(negotiation.contextFor(provider).isAuthenticated(), "The context must report isAuthenticated()=false before setAuthenticationResult() is called");
    }

    @Test
    void contextAuthorizationIdentityIsNullBeforeAuthentication()
    {
        // Verify result.
        assertNull(negotiation.contextFor(provider).getAuthorizationIdentity(), "getAuthorizationIdentity() must be null before authentication completes");
    }

    @Test
    void getOfferedTaskNamesReflectsCurrentRoundAcrossProviders()
    {
        // Setup test fixture.
        final Sasl2TaskProvider otherProvider = mock(Sasl2TaskProvider.class);
        when(otherProvider.getIdentifier()).thenReturn("other-provider-id");
        final Sasl2TaskManager.Offer offerA = new Sasl2TaskManager.Offer(provider, "task-a");
        final Sasl2TaskManager.Offer offerB = new Sasl2TaskManager.Offer(otherProvider, "task-b");
        negotiation.beginRound(List.of(offerA, offerB));

        // Execute system under test.
        final Set<String> result = negotiation.contextFor(provider).getOfferedTaskNames();

        // Verify result.
        assertEquals(Set.of("task-a", "task-b"), result, "getOfferedTaskNames() must report every task offered this round, regardless of which provider offered it");
    }

    @Test
    void nextRoundIncrementsRoundCounter()
    {
        // Setup test fixture.
        final int before = negotiation.getRound();

        // Execute system under test.
        negotiation.nextRound();

        // Verify result.
        assertEquals(before + 1, negotiation.getRound(), "nextRound() must increment the round counter by exactly one");
    }

    @Test
    void contextGetRoundReflectsNegotiationRound()
    {
        // Setup test fixture.
        negotiation.nextRound();
        negotiation.nextRound();

        // Execute system under test.
        final int result = negotiation.contextFor(provider).getRound();

        // Verify result.
        assertEquals(2, result, "The context's getRound() must mirror the negotiation's own round counter");
    }

    @Test
    void getCompletedTaskNamesIsUnmodifiable()
    {
        // Setup test fixture.
        final Sasl2Task task = mock(Sasl2Task.class);
        when(task.getName()).thenReturn("my-task");
        negotiation.setActiveTask(task);
        negotiation.completeActiveTask();

        // Execute system under test.
        final List<String> result = negotiation.getCompletedTaskNames();

        // Verify result.
        assertThrows(UnsupportedOperationException.class, () -> result.add("other-task"), "getCompletedTaskNames() must return a list callers cannot mutate");
    }

    @Test
    void getParticipantsReturnsSnapshotUnaffectedByLaterAdditions()
    {
        // Setup test fixture.
        negotiation.addParticipant(provider);
        final Set<Sasl2TaskProvider> snapshot = negotiation.getParticipants();

        // Execute system under test.
        final Sasl2TaskProvider laterProvider = mock(Sasl2TaskProvider.class);
        when(laterProvider.getIdentifier()).thenReturn("later-provider-id");

        negotiation.addParticipant(laterProvider);

        // Verify result.
        assertFalse(snapshot.contains(laterProvider), "A previously taken snapshot of participants must not reflect providers added afterwards");
    }

    @Test
    void toStringDoesNotIncludeAttributeValues()
    {
        // Setup test fixture.
        negotiation.contextFor(provider).setAttribute("secret", "super-secret-value");

        // Execute system under test.
        final String result = negotiation.toString();

        // Verify result.
        assertFalse(result.contains("super-secret-value"), "toString() must not leak provider attribute values, which may hold sensitive scratch data, into logs");
    }
}
