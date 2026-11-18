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
package org.jivesoftware.openfire.server.diagnostics;

import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.CappedList;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Certificate;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the bounds that {@link AttemptMilestones} puts on lists whose length the remote server decides, and the
 * structure in which it keeps the failures of a TCP connection.
 *
 * @author Matthew Vivian
 */
public class AttemptMilestonesTest
{
    @Test
    public void testCappedListKeepsTheFirstValuesAndCountsTheRest()
    {
        // Setup test fixture.
        final int extra = 5;
        final List<String> values = names(AttemptMilestones.MAX_LISTED_VALUES + extra);

        // Execute system under test.
        final CappedList<String> kept = CappedList.of(values);

        // Verify results.
        assertEquals(values.subList(0, AttemptMilestones.MAX_LISTED_VALUES), kept.values(), "only the first values must be kept, in their order");
        assertEquals(extra, kept.leftOut(), "the number of values left out must be kept as a number, for the admin console to word in its own language");
    }

    @Test
    public void testCappedListKeepsValuesUpToTheLimit()
    {
        // Setup test fixture.
        final List<String> values = names(AttemptMilestones.MAX_LISTED_VALUES);

        // Execute system under test.
        final CappedList<String> kept = CappedList.of(values);

        // Verify results.
        assertEquals(values, kept.values(), "values up to the limit must all be kept");
        assertEquals(0, kept.leftOut(), "nothing must be counted as left out");
    }

    @Test
    public void testCappedListRejectsValuesBeyondItsBounds()
    {
        assertThrows(IllegalArgumentException.class, () -> new CappedList<>(names(AttemptMilestones.MAX_LISTED_VALUES + 1), 0), "a capped list must not hold more values than the limit");
        assertThrows(IllegalArgumentException.class, () -> new CappedList<>(names(1), -1), "a capped list cannot leave out a negative number of values");
    }

    @Test
    public void testNameMismatchKeepsOnlyTheFirstNames()
    {
        // Setup test fixture: the remote's certificate, which it controls, can hold any number of names.
        final int extra = 90;
        final List<String> names = names(AttemptMilestones.MAX_LISTED_VALUES + extra);

        // Execute system under test.
        final Certificate certificate = Certificate.nameMismatch(names);

        // Verify results.
        assertEquals(names.subList(0, AttemptMilestones.MAX_LISTED_VALUES), certificate.presentedNames().values(), "only the first names must be kept, as the latest attempts are kept for many domain pairs");
        assertEquals(extra, certificate.presentedNames().leftOut(), "the number of names left out must be kept");
    }

    @Test
    public void testNameMismatchKeepsTheNamesAsRemoteText()
    {
        // Setup test fixture: names in the certificate, which the remote controls, can hold line breaks and be long.
        final String forgedLine = "other.example.org\n2026-11-06 12:00:00 INFO  Connection succeeded";
        final String longName = "x".repeat(AttemptMilestones.MAX_REMOTE_TEXT_LENGTH + 50);

        // Execute system under test.
        final Certificate certificate = Certificate.nameMismatch(List.of(forgedLine, longName));

        // Verify results.
        assertEquals(List.of(AttemptMilestones.remoteText(forgedLine), AttemptMilestones.remoteText(longName)), certificate.presentedNames().values(), "each name must be kept as other text from the remote is, so that it cannot start what looks like a new line of the diagnostic log, nor be kept at any length");
    }

    @Test
    public void testAllFailedClassifiesTheFailures()
    {
        // Setup test fixture.
        final AddressFailure refused = AddressFailure.refused("192.0.2.10:5269");
        final AddressFailure timedOut = AddressFailure.timedOut("192.0.2.11:5269");
        final AddressFailure other = AddressFailure.failed("192.0.2.12:5269", "No route to host");

        // Execute system under test, and verify results.
        assertEquals(Tcp.Outcome.REFUSED, Tcp.allFailed(List.of(refused, refused)).outcome(), "when every address refused, the result is refused");
        assertEquals(Tcp.Outcome.TIMED_OUT, Tcp.allFailed(List.of(timedOut)).outcome(), "when every address timed out, the result is timed out");
        assertEquals(Tcp.Outcome.FAILED, Tcp.allFailed(List.of(refused, timedOut)).outcome(), "different failures at different addresses cannot be summarised as one kind");
        assertEquals(Tcp.Outcome.FAILED, Tcp.allFailed(List.of(other)).outcome(), "another failure must be reported as such");
        assertEquals(List.of(refused, other), Tcp.allFailed(List.of(refused, other)).failures().values(), "each failure must be kept, with its own outcome and reason");
    }

    @Test
    public void testAddressFailureCarriesAReasonOnlyForAnotherFailure()
    {
        assertThrows(IllegalArgumentException.class, () -> new AddressFailure("192.0.2.10:5269", Tcp.Outcome.FAILED, null), "another failure must carry its reason");
        assertThrows(IllegalArgumentException.class, () -> new AddressFailure("192.0.2.10:5269", Tcp.Outcome.REFUSED, "Connection refused"), "a refusal must not carry a reason, as its outcome says what happened");
        assertThrows(IllegalArgumentException.class, () -> new AddressFailure("192.0.2.10:5269", Tcp.Outcome.CONNECTED, null), "an address failure must be a failure");
    }

    private static List<String> names(final int count)
    {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("xmpp" + i + ".remote.example.org");
        }
        return names;
    }
}
