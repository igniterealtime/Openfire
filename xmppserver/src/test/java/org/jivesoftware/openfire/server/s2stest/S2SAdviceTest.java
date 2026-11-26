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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the guidance that the S2S connection test shows below the checklist.
 *
 * @author Matthew Vivian
 */
public class S2SAdviceTest
{
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("openfire_i18n", Locale.ROOT);

    /**
     * The message key of every failure and warning that a check can show. Keys for text that the builder composes
     * (lists, what happened at each address) and the status labels are not results, so they are left out.
     */
    private static final Pattern RESULT_KEY = Pattern.compile("^server2server\\.settings\\.testing\\.check\\.(?!status\\.)[a-z_]+\\.(fail|warning)(\\.[a-z_.]+)?$");

    private static List<String> resultKeys()
    {
        final List<String> keys = new ArrayList<>(Collections.list(BUNDLE.getKeys()));
        keys.removeIf(key -> !RESULT_KEY.matcher(key).matches());
        return keys;
    }

    private static S2SCheck checkOf(final String messageKey)
    {
        final String segment = messageKey.substring(S2SCheck.KEY_PREFIX.length()).split("\\.")[0];
        for (final S2SCheck check : S2SCheck.values()) {
            if (check.messageKey("").startsWith(S2SCheck.KEY_PREFIX + segment + ".")) {
                return check;
            }
        }
        throw new IllegalArgumentException("no check for " + messageKey);
    }

    private static CheckResult result(final S2SCheck check, final CheckStatus status, final String suffix)
    {
        return new CheckResult(check, status, check.messageKey(suffix), List.of());
    }

    private static List<CheckResult> passingResults()
    {
        final List<CheckResult> results = new ArrayList<>();
        for (final S2SCheck check : S2SCheck.values()) {
            results.add(result(check, CheckStatus.PASS, "pass"));
        }
        return results;
    }

    private static S2SChecklist checklistWith(final CheckResult... replacements)
    {
        final List<CheckResult> results = passingResults();
        for (final CheckResult replacement : replacements) {
            results.set(replacement.check().ordinal(), replacement);
        }
        return new S2SChecklist(results);
    }

    @Test
    public void testEveryFailureAndWarningHasAdvice()
    {
        // Setup test fixture.
        final List<String> keys = resultKeys();
        assertFalse(keys.isEmpty(), "the bundle must hold the checklist's result messages");

        for (final String key : keys) {
            // Execute system under test.
            final CheckStatus status = key.contains(".warning") ? CheckStatus.WARNING : CheckStatus.FAIL;
            final CheckResult result = new CheckResult(checkOf(key), status, key, List.of());

            // Verify results.
            assertTrue(S2SAdvice.forResult(result).isPresent(), "the result " + key + " must come with advice for the administrator of this server");
        }
    }

    private static List<String> textKeys(final S2SAdvice.Advice advice)
    {
        final List<String> keys = new ArrayList<>();
        advice.localKey().ifPresent(keys::add);
        advice.remoteKey().ifPresent(keys::add);
        advice.noteKey().ifPresent(keys::add);
        return keys;
    }

    @Test
    public void testEveryAdviceTextExists()
    {
        for (final S2SAdvice.Advice advice : S2SAdvice.all()) {
            assertFalse(textKeys(advice).isEmpty(), "advice " + advice.id() + " must say something");
            for (final String key : textKeys(advice)) {
                assertTrue(BUNDLE.containsKey(key), "advice key " + key + " must exist in openfire_i18n.properties");
            }
        }
    }

    @Test
    public void testAdviceWithPlaceholdersAnswersOneResultWithTheSameValues()
    {
        for (final S2SAdvice.Advice advice : S2SAdvice.all()) {
            for (final String key : textKeys(advice)) {
                final int placeholders = new MessageFormat(BUNDLE.getString(key), Locale.ROOT).getFormatsByArgumentIndex().length;
                if (placeholders == 0) {
                    continue;
                }
                // Advice that fills in values must be for one result message exactly, so that the values are the ones it expects.
                final String resultKey = S2SCheck.KEY_PREFIX + advice.id();
                assertTrue(BUNDLE.containsKey(resultKey), "advice " + key + " fills in values, so it must answer one result message, " + resultKey);
                final int available = new MessageFormat(BUNDLE.getString(resultKey), Locale.ROOT).getFormatsByArgumentIndex().length;
                assertTrue(placeholders <= available, "advice " + key + " must use only the values that " + resultKey + " carries");
            }
        }
    }

    @Test
    public void testEveryCertificateFailureButRevocationMentionsTheServerWideSettings()
    {
        for (final S2SAdvice.Advice advice : S2SAdvice.all()) {
            if (advice.check() == S2SCheck.CERTIFICATE && !advice.id().equals(REVOKED)) {
                assertTrue(advice.noteKey().isPresent(), "certificate advice " + advice.id() + " must point to the settings that let such a connection go ahead, and say that they apply to every remote server");
            }
        }
    }

    @Test
    public void testARevokedCertificateIsNotWorkedAround()
    {
        // Setup test fixture.
        final S2SAdvice.Advice advice = S2SAdvice.all().stream().filter(a -> a.id().equals(REVOKED)).findFirst().orElseThrow();

        // Verify results.
        assertTrue(advice.noteKey().isEmpty(), "its issuer revoked the certificate for a reason, so the advice must not point to settings that let the connection go ahead anyway");
        assertTrue(advice.pages().isEmpty(), "nothing on this server should change, so no admin console page is offered");
    }

    private static final String REVOKED = "certificate.fail.revoked";

    @Test
    public void testADnsOverrideIsMentionedWhenOneWasUsedAndALaterStepFailed()
    {
        // Setup test fixture: a DNS override on this server sent the connection to a host that opened no stream.
        final CheckResult override = new CheckResult(S2SCheck.DNS, CheckStatus.PASS, S2SCheck.DNS.messageKey("pass.override"), List.of("xmpp.example.org:5269"));
        final S2SChecklist checklist = checklistWith(override, result(S2SCheck.STREAM, CheckStatus.FAIL, "fail.closed"));

        // Execute system under test.
        final S2SAdvice.Suggestion suggestion = S2SAdvice.forChecklist(checklist).get(0);

        // Verify results.
        assertEquals(override, suggestion.dnsOverride().orElse(null), "a failure after a DNS override was used must point to that override, as it decided where this server connected");
    }

    @Test
    public void testADnsOverrideIsNotMentionedWhenNoneWasUsed()
    {
        // Setup test fixture: the domain published SRV records, and the host they name opened no stream.
        final S2SChecklist checklist = checklistWith(result(S2SCheck.STREAM, CheckStatus.FAIL, "fail.closed"));

        // Execute system under test.
        final S2SAdvice.Suggestion suggestion = S2SAdvice.forChecklist(checklist).get(0);

        // Verify results.
        assertTrue(suggestion.dnsOverride().isEmpty(), "without a DNS override in use, the advice must not suggest checking one");
    }

    @Test
    public void testAdviceForAReusedStreamIdNamesTheSettingThatDecidesIt()
    {
        // Setup test fixture.
        final CheckResult result = result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.reused_stream_id");

        // Execute system under test.
        final S2SAdvice.Advice advice = S2SAdvice.forResult(result).orElseThrow();

        // Verify results.
        assertTrue(BUNDLE.getString(advice.localKey().orElseThrow()).contains("xmpp.server.outgoing.accept-reused-stream-id"), "the administrator must learn which setting refuses a reused stream ID");
        assertTrue(advice.pages().contains(S2SAdvice.AdminPage.SYSTEM_PROPERTIES), "the advice must link to the page on which that setting is changed");
    }

    @Test
    public void testAClosedConnectionIsNotBlamedOnEitherServer()
    {
        // This server learns that the connection closed, not which side closed it, so the result must not say, and the
        // advice must point at the logs of both servers.
        for (final String check : new String[] { "stream", "tls", "authentication" }) {
            // Setup test fixture.
            final CheckResult result = result(checkOf(S2SCheck.KEY_PREFIX + check + "."), CheckStatus.FAIL, "fail.closed");

            // Execute system under test.
            final S2SAdvice.Advice advice = S2SAdvice.forResult(result).orElseThrow();

            // Verify results.
            final String message = BUNDLE.getString(S2SCheck.KEY_PREFIX + check + ".fail.closed");
            assertFalse(message.contains("remote server closed"), "the result for " + check + " must not say that the remote server closed the connection: " + message);
            assertTrue(advice.localKey().isPresent() && advice.remoteKey().isPresent(), "the advice for " + check + " must cover both servers");
            assertTrue(advice.pages().contains(S2SAdvice.AdminPage.LOGS), "the advice for " + check + " must link to the logs of this server");
        }
    }

    @Test
    public void testResultsWithADifferentCauseGetTheirOwnAdvice()
    {
        // Each of these has a cause that the default advice for its check would misdescribe.
        for (final String[] expected : new String[][] {
            { "tcp", "fail.refused" }, { "tcp", "fail.none_attempted" }, { "tls", "fail.refused" }, { "tls", "fail.timed_out" },
            { "tls", "fail.stalled" }, { "certificate", "fail.revoked" }, { "authentication", "fail.closed_after_external" },
            { "authentication", "fail.reused_stream_id" }, { "authentication", "fail.restarted_stream_no_id" }, { "ping", "fail.local_error" },
            { "stream", "fail.closed" }, { "tls", "fail.closed" }, { "authentication", "fail.closed" } }) {
            final S2SCheck check = checkOf(S2SCheck.KEY_PREFIX + expected[0] + ".");
            final S2SAdvice.Advice advice = S2SAdvice.forResult(result(check, CheckStatus.FAIL, expected[1])).orElseThrow();
            assertEquals(expected[0] + "." + expected[1], advice.id(), "the result " + expected[0] + "." + expected[1] + " needs advice of its own");
        }
    }

    @Test
    public void testACertificateRejectedDuringTheHandshakeGetsTheAdviceForTheCertificate()
    {
        // Setup test fixture: this server stopped the handshake because the remote certificate has expired.
        final S2SChecklist checklist = checklistWith(
            result(S2SCheck.TLS, CheckStatus.FAIL, "fail.certificate"),
            result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.expired"));

        // Execute system under test.
        final List<S2SAdvice.Suggestion> suggestions = S2SAdvice.forChecklist(checklist);

        // Verify results.
        assertEquals(1, suggestions.size(), "a failure must come with one suggestion");
        assertEquals("certificate.fail.expired", suggestions.get(0).advice().id(), "the check that says why the certificate was rejected must give the advice, as it is the more specific");
        assertEquals(S2SCheck.CERTIFICATE, suggestions.get(0).result().check(), "the suggestion must name the check it answers");
    }

    @Test
    public void testNoMessageOfTheTestPageHoldsAnApostrophe()
    {
        // The page formats its messages with MessageFormat, which drops a single apostrophe, so none may hold one.
        for (final String key : Collections.list(BUNDLE.getKeys())) {
            if (key.startsWith("server2server.settings.testing.")) {
                assertFalse(BUNDLE.getString(key).contains("'"), "message " + key + " must not hold an apostrophe, as MessageFormat would drop it");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(S2SAdvice.AdminPage.class)
    public void testEveryAdminPageHasALabelAndARelativeAddress(final S2SAdvice.AdminPage page)
    {
        assertTrue(BUNDLE.containsKey(page.labelKey()), "page label key " + page.labelKey() + " must exist in openfire_i18n.properties");
        assertTrue(page.url().matches("^[a-z0-9-]+\\.jsp(\\?[A-Za-z]+=[A-Z0-9_]+)?$"), "the page address must be a fixed, relative admin console page: " + page.url());
    }

    @Test
    public void testTheMostSpecificAdviceIsChosen()
    {
        // Execute system under test.
        final S2SAdvice.Advice expired = S2SAdvice.forResult(result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.expired")).orElseThrow();
        final S2SAdvice.Advice untrusted = S2SAdvice.forResult(result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.untrusted")).orElseThrow();

        // Verify results.
        assertEquals("certificate.fail.expired", expired.id(), "an expired certificate needs its own advice, as nothing on this server can fix it");
        assertEquals("certificate.fail", untrusted.id(), "a result without advice of its own must get the advice for its check and status");
    }

    @Test
    public void testPassingResultsHaveNoAdvice()
    {
        // Execute system under test.
        final List<S2SAdvice.Suggestion> suggestions = S2SAdvice.forChecklist(new S2SChecklist(passingResults()));

        // Verify results.
        assertTrue(suggestions.isEmpty(), "a test that passed every check needs no advice");
    }

    @Test
    public void testAFailureGetsAdviceForTheFirstFailureOnly()
    {
        // Setup test fixture: the certificate was rejected, Server Dialback was refused, and a warning was logged.
        final S2SChecklist checklist = checklistWith(
            result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.untrusted"),
            result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.dialback_refused"),
            result(S2SCheck.LOG, CheckStatus.WARNING, "warning"));

        // Execute system under test.
        final List<S2SAdvice.Suggestion> suggestions = S2SAdvice.forChecklist(checklist);

        // Verify results.
        assertEquals(List.of("certificate.fail"), suggestions.stream().map(suggestion -> suggestion.advice().id()).toList(), "after a failure, the advice must be about the first failed check, where the administrator should start");
    }

    @Test
    public void testWarningsGetAdviceEachWhenNothingFailed()
    {
        // Setup test fixture: the connection works unencrypted, and authenticated with Server Dialback.
        final S2SChecklist checklist = checklistWith(
            result(S2SCheck.TLS, CheckStatus.WARNING, "warning.unencrypted"),
            result(S2SCheck.AUTHENTICATION, CheckStatus.WARNING, "warning.dialback.not_offered"));

        // Execute system under test.
        final List<S2SAdvice.Suggestion> suggestions = S2SAdvice.forChecklist(checklist);

        // Verify results.
        assertEquals(List.of(S2SCheck.TLS, S2SCheck.AUTHENTICATION), suggestions.stream().map(suggestion -> suggestion.result().check()).toList(), "with nothing failed, each warning must come with its advice, in the order of the checks");
    }
}
