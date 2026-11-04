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
package org.jivesoftware.openfire.server.s2stest;

import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Authentication;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Certificate;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns.Target;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Stream;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tls;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the one-line summary shown above the checklist.
 *
 * @author Matthew Vivian
 */
public class ChecklistSummaryTest
{
    private static final String DOMAIN = "remote.example.org";
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("openfire_i18n", Locale.ROOT);

    /**
     * Translates as the admin console does, from the English resource bundle.
     */
    private static final S2SChecklistBuilder.Localiser ENGLISH = (key, arguments) -> new MessageFormat(BUNDLE.getString(key), Locale.ROOT).format(arguments.toArray());

    private static AttemptMilestones.Builder allSteps()
    {
        return AttemptMilestones.builder()
            .dns(Dns.srvRecords(List.of(Target.plain("xmpp.remote.example.org:5269"))))
            .tcp(Tcp.connected("192.0.2.10:5269"))
            .stream(Stream.opened(DOMAIN))
            .tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"))
            .certificate(Certificate.valid())
            .authentication(Authentication.saslExternal());
    }

    private static S2SChecklistBuilder test(final AttemptMilestones milestones, final PingOutcome ping)
    {
        return new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH)
            .milestones(milestones)
            .sessionRegistered(true)
            .ping(ping)
            .log(new LogSummary(0, 0, 0));
    }

    @Test
    public void testFailureNamesTheFirstFailedCheck()
    {
        // Setup test fixture.
        final S2SChecklist checklist = test(allSteps().certificate(Certificate.untrusted()).authentication(null).build(), PingOutcome.result()).sessionRegistered(false).build();

        // Execute system under test.
        final ChecklistSummary summary = ChecklistSummary.of(checklist);

        // Verify results.
        assertEquals(ChecklistSummary.Kind.FAILED, summary.kind(), "a failed check must make the summary a failure");
        assertEquals(S2SCheck.CERTIFICATE, summary.failedCheck(), "the summary must point at the first failed check");
    }

    @Test
    public void testWarningsAreCounted()
    {
        // Setup test fixture: two warnings.
        final S2SChecklist checklist = test(allSteps().authentication(Authentication.dialback()).build(), PingOutcome.remoteError("service-unavailable")).build();

        // Execute system under test.
        final ChecklistSummary summary = ChecklistSummary.of(checklist);

        // Verify results.
        assertEquals(ChecklistSummary.Kind.WARNINGS, summary.kind(), "warnings without a failure must make the summary a warning");
        assertEquals(2, summary.warnings(), "the summary must count the warnings");
        assertNull(summary.failedCheck(), "with no failure there is no failed check to name");
    }

    @Test
    public void testUnobservedStepsWithoutProblemsAreSaidSo()
    {
        // Setup test fixture: nothing recorded about the connection steps.
        final S2SChecklist checklist = test(AttemptMilestones.none(), PingOutcome.result()).build();

        // Execute system under test.
        final ChecklistSummary summary = ChecklistSummary.of(checklist);

        // Verify results.
        assertEquals(ChecklistSummary.Kind.NOT_OBSERVED, summary.kind(), "a test that skipped steps must not claim that all checks passed");
    }

    @Test
    public void testAllPassed()
    {
        // Execute system under test.
        final ChecklistSummary summary = ChecklistSummary.of(test(allSteps().build(), PingOutcome.result()).build());

        // Verify results.
        assertEquals(ChecklistSummary.Kind.PASSED, summary.kind(), "a test in which every check passed must say so");
        assertEquals(0, summary.warnings(), "a passed test has no warnings");
    }

    @Test
    public void testEveryKindHasAMessage()
    {
        // Verify results.
        for (final ChecklistSummary.Kind kind : ChecklistSummary.Kind.values()) {
            assertTrue(BUNDLE.containsKey(kind.messageKey()), "summary " + kind + " must have a message in openfire_i18n.properties");
        }
    }
}
