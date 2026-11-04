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
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link S2SChecklistBuilder} applies the rules of the S2S connection test checklist (OF-3439): for each of
 * the 11 checks, which recorded facts give a pass, a warning or a failure, and when a check shows as not reached or not
 * observed.
 *
 * Every assertion on a check also verifies that its message key exists in the English resource bundle and that the
 * message takes exactly the parameters the result carries, so a rule cannot emit a key that the admin console cannot
 * render. The text that the builder composes from recorded values (a list, what happened at each address) is checked
 * in the same way, as the builder asks for it.
 *
 * @author Matthew Vivian
 */
public class S2SChecklistBuilderTest
{
    private static final String DOMAIN = "remote.example.org";
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("openfire_i18n", Locale.ROOT);
    private static final String KEY_PREFIX = "server2server.settings.testing.check.";

    /**
     * Translates as the admin console does, from the English resource bundle, after verifying the key and the number
     * of arguments.
     */
    private static final S2SChecklistBuilder.Localiser ENGLISH = (key, arguments) -> {
        assertRenderable(key, arguments);
        return new MessageFormat(BUNDLE.getString(key), Locale.ROOT).format(arguments.toArray());
    };

    /**
     * Milestones of an attempt in which every step succeeded.
     */
    private static AttemptMilestones.Builder successfulMilestones()
    {
        return AttemptMilestones.builder()
            .dns(Dns.srvRecords(List.of(Target.plain("xmpp.remote.example.org:5269"))))
            .tcp(Tcp.connected("192.0.2.10:5269"))
            .stream(Stream.opened(DOMAIN))
            .tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"))
            .certificate(Certificate.valid())
            .authentication(Authentication.saslExternal());
    }

    /**
     * A test of a domain that passed validation, in which every step succeeded.
     */
    private static S2SChecklistBuilder successfulTest()
    {
        return successfulTest(ENGLISH);
    }

    /**
     * A test of a domain that passed validation, in which every step succeeded, with the text that the builder composes
     * translated by the given localiser.
     */
    private static S2SChecklistBuilder successfulTest(final S2SChecklistBuilder.Localiser localiser)
    {
        return new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, localiser)
            .milestones(successfulMilestones().build())
            .sessionRegistered(true)
            .ping(PingOutcome.result())
            .log(new LogSummary(0, 0, 0));
    }

    private static CheckResult assertCheck(final S2SChecklist checklist, final S2SCheck check, final CheckStatus expectedStatus, final String expectedKeySuffix)
    {
        final CheckResult result = checklist.get(check);
        assertEquals(expectedStatus, result.status(), "check " + check + " must have status " + expectedStatus);
        assertEquals(KEY_PREFIX + expectedKeySuffix, result.messageKey(), "check " + check + " must explain its status with the matching message");
        assertRenderable(result);
        return result;
    }

    private static void assertRenderable(final CheckResult result)
    {
        assertRenderable(result.messageKey(), result.parameters());
        assertTrue(BUNDLE.containsKey(result.check().titleKey()), "title key " + result.check().titleKey() + " must exist in openfire_i18n.properties");
    }

    private static void assertRenderable(final String key, final List<String> parameters)
    {
        assertTrue(BUNDLE.containsKey(key), "message key " + key + " must exist in openfire_i18n.properties");
        final int placeholders = new MessageFormat(BUNDLE.getString(key), Locale.ROOT).getFormatsByArgumentIndex().length;
        assertEquals(placeholders, parameters.size(), "message " + key + " must receive exactly as many parameters as it has placeholders");
    }

    /**
     * @return the explanation of the result, as the admin console shows it.
     */
    private static String detail(final CheckResult result, final S2SChecklistBuilder.Localiser localiser)
    {
        return localiser.localise(result.messageKey(), result.parameters());
    }

    private static void assertNotReached(final S2SChecklist checklist, final S2SCheck... checks)
    {
        for (final S2SCheck check : checks) {
            assertCheck(checklist, check, CheckStatus.NOT_REACHED, "not_reached");
        }
    }

    // Structure of the checklist

    @Test
    public void testChecklistHoldsElevenChecksInOrder()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        final List<S2SCheck> order = checklist.getResults().stream().map(CheckResult::check).toList();
        assertEquals(List.of(S2SCheck.values()), order, "the checklist must hold one result per check, in the order the steps happen");
        assertEquals(11, order.size(), "the checklist must hold exactly the 11 agreed checks");
    }

    @Test
    public void testFullySuccessfulAttemptPassesEveryCheck()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        for (final CheckResult result : checklist.getResults()) {
            assertEquals(CheckStatus.PASS, result.status(), "check " + result.check() + " must pass when every step succeeded");
            assertRenderable(result);
        }
        assertTrue(checklist.getFirstFailure().isEmpty(), "a fully successful test must have no failed check");
    }

    @Test
    public void testFirstFailureIsTheEarliestFailedCheck()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().tls(Tls.unencrypted()).authentication(Authentication.dialbackRefused()).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertEquals(S2SCheck.TLS, checklist.getFirstFailure().orElseThrow().check(), "the first failure must be the earliest failed check, which is where the administrator should look");
    }

    @Test
    public void testResultsCarryNoMarkup()
    {
        // Setup test fixture: a remote-controlled value containing markup.
        final String hostile = "<script>alert(1)</script>";
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().stream(Stream.streamError(hostile)).build());

        // Execute system under test.
        final CheckResult result = builder.build().get(S2SCheck.STREAM);

        // Verify results.
        assertEquals(List.of(hostile), result.parameters(), "remote-controlled text must be passed through as a plain parameter, for the page to escape when it renders it");
    }

    // Check 1: target XMPP domain valid

    @Test
    public void testValidDomainPasses()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.DOMAIN, CheckStatus.PASS, "domain.pass");
        assertEquals(List.of(DOMAIN), result.parameters(), "the pass message must name the domain that was tested");
    }

    @ParameterizedTest
    @MethodSource("rejectedDomains")
    public void testRejectedDomainFailsAndNothingElseIsReached(final DomainCheck domainCheck, final String expectedKeySuffix)
    {
        // Setup test fixture: no attempt is made for a rejected domain.
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(DOMAIN, domainCheck, ENGLISH);

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.DOMAIN, CheckStatus.FAIL, expectedKeySuffix);
        assertEquals(List.of(DOMAIN), result.parameters(), "the failure message must name the domain that was rejected");
        assertNotReached(checklist, S2SCheck.DNS, S2SCheck.TCP, S2SCheck.STREAM, S2SCheck.TLS, S2SCheck.CERTIFICATE,
            S2SCheck.AUTHENTICATION, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE, S2SCheck.LOG);
    }

    static java.util.stream.Stream<Arguments> rejectedDomains()
    {
        return java.util.stream.Stream.of(
            Arguments.of(DomainCheck.INVALID, "domain.fail.invalid"),
            Arguments.of(DomainCheck.LOCAL, "domain.fail.local"),
            Arguments.of(DomainCheck.COMPONENT, "domain.fail.component"),
            Arguments.of(DomainCheck.BLOCKED_BY_POLICY, "domain.fail.blocked")
        );
    }

    @Test
    public void testRejectedDomainIgnoresOtherInputs()
    {
        // Setup test fixture: inputs that would otherwise pass.
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(DOMAIN, DomainCheck.BLOCKED_BY_POLICY, ENGLISH)
            .milestones(successfulMilestones().build())
            .sessionRegistered(true)
            .ping(PingOutcome.result())
            .log(new LogSummary(0, 0, 0));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertNotReached(checklist, S2SCheck.DNS, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.LOG);
    }

    @ParameterizedTest
    @EnumSource(CheckStatus.class)
    public void testEveryStatusHasALabel(final CheckStatus status)
    {
        assertTrue(BUNDLE.containsKey(status.labelKey()), "status " + status + " must have a label in openfire_i18n.properties, for the Result column");
    }

    // Check 2: DNS resolved to server address

    @Test
    public void testDnsWithSrvRecordsPasses()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.DNS, CheckStatus.PASS, "dns.pass");
        assertEquals(List.of("xmpp.remote.example.org:5269"), result.parameters(), "the pass message must list the addresses found");
    }

    @Test
    public void testDnsWithOnlyFallbackPasses()
    {
        // Setup test fixture: no SRV records, so the domain name itself is used (the RFC 6120 fallback).
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().dns(Dns.fallbackOnly(List.of(Target.plain("remote.example.org:5269"), Target.plain("remote.example.org:5270")))).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.DNS, CheckStatus.PASS, "dns.pass.fallback");
        assertEquals(List.of("remote.example.org:5269, remote.example.org:5270"), result.parameters(), "the message must list the fallback addresses used");
        assertCheck(checklist, S2SCheck.TCP, CheckStatus.PASS, "tcp.pass");
    }

    @Test
    public void testDnsOverridePassesSayingSo()
    {
        // Setup test fixture: an administrator configured a DNS override for the domain.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().dns(Dns.override(List.of(Target.plain("xmpp2:5269")))).build());

        // Execute system under test.
        final CheckResult result = builder.build().get(S2SCheck.DNS);

        // Verify results.
        assertEquals(CheckStatus.PASS, result.status(), "an address from a DNS override is a valid way to reach the domain");
        assertEquals(KEY_PREFIX + "dns.pass.override", result.messageKey(), "the message must say a DNS override was used, not that SRV records were found");
        assertRenderable(result);
        assertEquals(List.of("xmpp2:5269"), result.parameters(), "the message must show the overriding address");
    }

    @Test
    public void testDnsWithNoAddressFailsAndLaterStepsAreNotReached()
    {
        // Setup test fixture: the attempt stops at DNS; the ping is answered by our own server.
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH)
            .milestones(AttemptMilestones.builder().dns(Dns.noAddress()).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"))
            .log(new LogSummary(1, 0, 0));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.DNS, CheckStatus.FAIL, "dns.fail.no_address");
        assertEquals(List.of(DOMAIN), result.parameters(), "the failure must name the domain that could not be resolved");
        assertNotReached(checklist, S2SCheck.TCP, S2SCheck.STREAM, S2SCheck.TLS, S2SCheck.CERTIFICATE,
            S2SCheck.AUTHENTICATION, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
        assertCheck(checklist, S2SCheck.LOG, CheckStatus.WARNING, "log.warning");
    }

    // Check 3: TCP connection established

    @Test
    public void testTcpConnectedPassesNamingTheEndpoint()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TCP, CheckStatus.PASS, "tcp.pass");
        assertEquals(List.of("192.0.2.10:5269"), result.parameters(), "the pass message must show the address and port used");
    }

    @ParameterizedTest
    @MethodSource("tcpFailures")
    public void testTcpFailuresAreDistinguished(final Tcp tcp, final String expectedKeySuffix, final List<String> expectedParameters)
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH)
            .milestones(AttemptMilestones.builder().dns(Dns.srvRecords(List.of(Target.plain("xmpp.remote.example.org:5269")))).tcp(tcp).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TCP, CheckStatus.FAIL, expectedKeySuffix);
        assertEquals(expectedParameters, result.parameters(), "the failure must name the endpoint (and the reason, where there is one)");
        assertNotReached(checklist, S2SCheck.STREAM, S2SCheck.TLS, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION,
            S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    static java.util.stream.Stream<Arguments> tcpFailures()
    {
        return java.util.stream.Stream.of(
            Arguments.of(Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269"))), "tcp.fail.refused", List.of("192.0.2.10:5269")),
            Arguments.of(Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269"), AddressFailure.refused("[2001:db8::10]:5269"))), "tcp.fail.refused", List.of("192.0.2.10:5269, [2001:db8::10]:5269")),
            Arguments.of(Tcp.allFailed(List.of(AddressFailure.timedOut("192.0.2.10:5269"))), "tcp.fail.timed_out", List.of("192.0.2.10:5269")),
            Arguments.of(Tcp.allFailed(List.of(AddressFailure.failed("192.0.2.10:5269", "No route to host"))), "tcp.fail.other", List.of("192.0.2.10:5269", "192.0.2.10:5269 No route to host")),
            Arguments.of(Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269"), AddressFailure.timedOut("192.0.2.11:5269"), AddressFailure.failed("192.0.2.12:5269", "No route to host"))),
                "tcp.fail.other", List.of("192.0.2.10:5269, 192.0.2.11:5269, 192.0.2.12:5269", "192.0.2.10:5269 refused; 192.0.2.11:5269 timed out; 192.0.2.12:5269 No route to host")),
            Arguments.of(Tcp.noneAttempted(), "tcp.fail.none_attempted", List.of())
        );
    }

    @Test
    public void testManyFailedAddressesAreListedWithHowManyWereLeftOut()
    {
        // Setup test fixture: more addresses failed than are kept.
        final List<AddressFailure> failures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            failures.add(i % 2 == 0 ? AddressFailure.refused("192.0.2." + i + ":5269") : AddressFailure.timedOut("192.0.2." + i + ":5269"));
        }
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH)
            .milestones(AttemptMilestones.builder().tcp(Tcp.allFailed(failures)).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TCP, CheckStatus.FAIL, "tcp.fail.other");
        assertEquals("The connection to 192.0.2.0:5269, 192.0.2.1:5269, 192.0.2.2:5269, 192.0.2.3:5269, 192.0.2.4:5269, 192.0.2.5:5269, "
                + "192.0.2.6:5269, 192.0.2.7:5269, 192.0.2.8:5269, 192.0.2.9:5269, and 2 more failed: 192.0.2.0:5269 refused; "
                + "192.0.2.1:5269 timed out; 192.0.2.2:5269 refused; 192.0.2.3:5269 timed out; 192.0.2.4:5269 refused; 192.0.2.5:5269 timed out; "
                + "192.0.2.6:5269 refused; 192.0.2.7:5269 timed out; 192.0.2.8:5269 refused; 192.0.2.9:5269 timed out; and 2 more",
            detail(result, ENGLISH), "the addresses and failures kept must be listed, followed by how many were left out");
    }

    // Translation of the text that the builder composes

    /**
     * The text that the builder composes from recorded values, in a language other than English, to show that each
     * fragment comes from the localiser rather than from text built when the attempt was recorded.
     */
    private static final Map<String, String> TRANSLATED_PARTS = Map.of(
        KEY_PREFIX + "list", "{0} | {1}",
        KEY_PREFIX + "list.more", "und {0} weitere",
        KEY_PREFIX + "dns.target.direct_tls", "{0} (direktes TLS)",
        KEY_PREFIX + "tcp.failures", "{0} || {1}",
        KEY_PREFIX + "tcp.failure.refused", "{0} abgelehnt",
        KEY_PREFIX + "tcp.failure.timed_out", "{0} Zeitueberschreitung",
        KEY_PREFIX + "tcp.failure.other", "{0}: {1}");

    private static final S2SChecklistBuilder.Localiser TRANSLATED = (key, arguments) ->
        new MessageFormat(TRANSLATED_PARTS.getOrDefault(key, BUNDLE.getString(key)), Locale.ROOT).format(arguments.toArray());

    @Test
    public void testComposedTextOfTheDnsCheckIsTranslated()
    {
        // Setup test fixture: more DNS targets than are kept, the first for direct TLS.
        final List<Target> targets = new ArrayList<>();
        targets.add(Target.directTls("xmpps.remote.example.org:5270"));
        for (int i = 1; i < 12; i++) {
            targets.add(Target.plain("xmpp" + i + ".remote.example.org:5269"));
        }
        final AttemptMilestones milestones = successfulMilestones().dns(Dns.srvRecords(targets)).build();
        final String listed = "xmpp1.remote.example.org:5269, xmpp2.remote.example.org:5269, xmpp3.remote.example.org:5269, xmpp4.remote.example.org:5269, "
            + "xmpp5.remote.example.org:5269, xmpp6.remote.example.org:5269, xmpp7.remote.example.org:5269, xmpp8.remote.example.org:5269, "
            + "xmpp9.remote.example.org:5269";

        // Execute system under test.
        final CheckResult english = successfulTest().milestones(milestones).build().get(S2SCheck.DNS);
        final CheckResult translated = successfulTest(TRANSLATED).milestones(milestones).build().get(S2SCheck.DNS);

        // Verify results.
        assertEquals("DNS SRV records found: xmpps.remote.example.org:5270 (direct TLS), " + listed + ", and 2 more", detail(english, ENGLISH),
            "the English text must read as before");
        assertEquals("DNS SRV records found: xmpps.remote.example.org:5270 (direktes TLS) | " + listed.replace(", ", " | ") + " | und 2 weitere", detail(translated, TRANSLATED),
            "the list separator, the direct TLS mark and the count of targets left out must come from the resource bundle");
    }

    @Test
    public void testComposedTextOfTheTcpCheckIsTranslated()
    {
        // Setup test fixture.
        final Tcp tcp = Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269"), AddressFailure.timedOut("192.0.2.11:5269"), AddressFailure.failed("[2001:db8::10]:5269", "Network is unreachable")));
        final AttemptMilestones failedMilestones = AttemptMilestones.builder().tcp(tcp).build();
        final AttemptMilestones warnedMilestones = successfulMilestones().tcp(Tcp.connectedAfterFailures("192.0.2.12:5269", tcp.failures().values())).build();

        // Execute system under test.
        final CheckResult failedInEnglish = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH).milestones(failedMilestones).build().get(S2SCheck.TCP);
        final CheckResult failedTranslated = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, TRANSLATED).milestones(failedMilestones).build().get(S2SCheck.TCP);
        final CheckResult warnedTranslated = successfulTest(TRANSLATED).milestones(warnedMilestones).build().get(S2SCheck.TCP);

        // Verify results.
        assertEquals("The connection to 192.0.2.10:5269, 192.0.2.11:5269, [2001:db8::10]:5269 failed: 192.0.2.10:5269 refused; 192.0.2.11:5269 timed out; [2001:db8::10]:5269 Network is unreachable",
            detail(failedInEnglish, ENGLISH), "the English text must read as before");
        assertEquals("The connection to 192.0.2.10:5269 | 192.0.2.11:5269 | [2001:db8::10]:5269 failed: 192.0.2.10:5269 abgelehnt || 192.0.2.11:5269 Zeitueberschreitung || [2001:db8::10]:5269: Network is unreachable",
            detail(failedTranslated, TRANSLATED), "what happened at each address must come from the localiser; only the operating system's own message is kept as it is");
        assertTrue(detail(warnedTranslated, TRANSLATED).contains(": 192.0.2.10:5269 abgelehnt || 192.0.2.11:5269 Zeitueberschreitung || [2001:db8::10]:5269: Network is unreachable."),
            "the failures before a connection must be translated too: " + detail(warnedTranslated, TRANSLATED));
    }

    @Test
    public void testComposedTextOfTheCertificateCheckIsTranslated()
    {
        // Setup test fixture: a certificate with more names than are kept.
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            names.add("n" + i + ".example.org");
        }
        final AttemptMilestones milestones = successfulMilestones().certificate(Certificate.nameMismatch(names)).build();

        // Execute system under test.
        final String english = detail(successfulTest().milestones(milestones).build().get(S2SCheck.CERTIFICATE), ENGLISH);
        final String translated = detail(successfulTest(TRANSLATED).milestones(milestones).build().get(S2SCheck.CERTIFICATE), TRANSLATED);

        // Verify results.
        assertTrue(english.endsWith("n8.example.org, n9.example.org, and 2 more"), "the English text must read as before: " + english);
        assertTrue(translated.endsWith("n8.example.org | n9.example.org | und 2 weitere"), "the count of names left out must come from the localiser: " + translated);
    }

    @ParameterizedTest
    @MethodSource("tcpWarnings")
    public void testTcpConnectedOnlyAfterAProblemWarns(final Tcp tcp, final String expectedKeySuffix, final List<String> expectedParameters)
    {
        // Setup test fixture: the connection was made, and the rest of the attempt succeeded.
        final S2SChecklistBuilder builder = successfulTest().milestones(successfulMilestones().tcp(tcp).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TCP, CheckStatus.WARNING, expectedKeySuffix);
        assertEquals(expectedParameters, result.parameters(), "the warning must name the endpoint used (and what failed, where something did)");
        assertCheck(checklist, S2SCheck.STREAM, CheckStatus.PASS, "stream.pass");
        assertCheck(checklist, S2SCheck.RESPONSE, CheckStatus.PASS, "response.pass");
        assertEquals(1, ChecklistSummary.of(checklist).warnings(), "the problem on the way to the connection must be the only warning");
    }

    static java.util.stream.Stream<Arguments> tcpWarnings()
    {
        return java.util.stream.Stream.of(
            Arguments.of(Tcp.connectedAfterFailures("192.0.2.11:5269", List.of(AddressFailure.timedOut("192.0.2.10:5269"))), "tcp.warning.earlier_failures", List.of("192.0.2.11:5269", "192.0.2.10:5269 timed out")),
            Arguments.of(Tcp.connectedToFallback("192.0.2.20:5269", List.of(AddressFailure.refused("192.0.2.10:5269"))), "tcp.warning.fallback", List.of("192.0.2.20:5269", "192.0.2.10:5269 refused")),
            Arguments.of(Tcp.connectedToFallback("192.0.2.20:5269", List.of()), "tcp.warning.fallback.unresolved", List.of("192.0.2.20:5269"))
        );
    }

    // Check 4: XMPP server stream opened

    @Test
    public void testStreamOpenedPassesNamingTheRemote()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.STREAM, CheckStatus.PASS, "stream.pass");
        assertEquals(List.of(DOMAIN), result.parameters(), "the pass message must show the name the remote answered as");
    }

    @Test
    public void testStreamOpenedWithoutANamePassesSayingSo()
    {
        // Setup test fixture: RFC 6120 only recommends that the remote states its name on the stream header.
        final S2SChecklistBuilder builder = successfulTest().milestones(successfulMilestones().stream(Stream.opened(null)).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.STREAM, CheckStatus.PASS, "stream.pass.unnamed");
        assertEquals(List.of(), result.parameters(), "a stream opened without a name has no name to show");
    }

    @Test
    public void testStreamErrorRequiresItsText()
    {
        assertThrows(NullPointerException.class, () -> Stream.streamError(null), "a stream error must carry the error the remote sent");
    }

    @Test
    public void testStreamClosedByRemoteFails()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.closedByRemote()).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.STREAM, CheckStatus.FAIL, "stream.fail.closed");
        assertNotReached(checklist, S2SCheck.TLS, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION, S2SCheck.SESSION);
    }

    @Test
    public void testStreamErrorFailsShowingTheErrorText()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.streamError("host-unknown")).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.STREAM, CheckStatus.FAIL, "stream.fail.error");
        assertEquals(List.of("host-unknown"), result.parameters(), "the failure must show the stream error the remote sent");
    }

    @Test
    public void testNoStreamBeforeTheTimeoutFailsShowingTheTimeout()
    {
        // Setup test fixture: the remote accepted the connection and then stayed silent.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.timedOut(Duration.ofSeconds(5))).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.STREAM, CheckStatus.FAIL, "stream.fail.timed_out");
        assertEquals(List.of("5"), result.parameters(), "the failure must say how long this server waited for the stream");
        assertNotReached(checklist, S2SCheck.TLS, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION, S2SCheck.SESSION);
    }

    @Test
    public void testStreamWithoutIdFails()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.missingId()).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.STREAM, CheckStatus.FAIL, "stream.fail.no_id");
        assertNotReached(checklist, S2SCheck.TLS, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION, S2SCheck.SESSION);
    }

    @Test
    public void testStreamTimeoutCannotBeNegative()
    {
        assertThrows(IllegalArgumentException.class, () -> Stream.timedOut(Duration.ofSeconds(-1)), "a stream that timed out must say how long this server waited");
    }

    // Direct TLS: TLS comes before the stream

    @Test
    public void testFailedDirectTlsHandshakeLeavesTheStreamNotReached()
    {
        // Setup test fixture: with direct TLS the handshake comes first, so no stream can open after it fails.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().directTls(true).dns(Dns.srvRecords(List.of(Target.plain("a:5270")))).tcp(Tcp.connected("192.0.2.10:5270"))
                .tls(Tls.handshakeFailed("Received fatal alert: handshake_failure")).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.TLS, CheckStatus.FAIL, "tls.fail.handshake");
        assertNotReached(checklist, S2SCheck.STREAM, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION, S2SCheck.SESSION);
        assertEquals(List.of(S2SCheck.values()), checklist.getResults().stream().map(CheckResult::check).toList(), "the checks must stay in their numbered order whatever order the steps happened in");
    }

    @Test
    public void testFailedStartTlsHandshakeAfterTheStreamKeepsTheStreamPassed()
    {
        // Setup test fixture: with STARTTLS the stream opens first, so it passed before the handshake failed.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.opened(DOMAIN))
                .tls(Tls.handshakeFailed("Received fatal alert: handshake_failure")).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.STREAM, CheckStatus.PASS, "stream.pass");
        assertCheck(checklist, S2SCheck.TLS, CheckStatus.FAIL, "tls.fail.handshake");
    }

    @Test
    public void testSilentRemoteOverDirectTlsFailsTlsShowingTheTimeout()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(AttemptMilestones.builder().directTls(true).dns(Dns.srvRecords(List.of(Target.plain("a:5270")))).tcp(Tcp.connected("192.0.2.10:5270"))
                .tls(Tls.timedOut(Duration.ofSeconds(5))).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TLS, CheckStatus.FAIL, "tls.fail.timed_out");
        assertEquals(List.of("5"), result.parameters(), "the failure must say how long this server waited for the handshake");
        assertNotReached(checklist, S2SCheck.STREAM);
    }

    // Check 5: TLS negotiated

    @Test
    public void testTlsNegotiatedPassesShowingProtocolAndCipher()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TLS, CheckStatus.PASS, "tls.pass");
        assertEquals(List.of("TLSv1.3", "TLS_AES_256_GCM_SHA384"), result.parameters(), "the pass message must show the protocol and cipher suite");
    }

    @ParameterizedTest
    @MethodSource("tlsFailures")
    public void testTlsFailuresAreDistinguished(final Tls tls, final String expectedKeySuffix, final List<String> expectedParameters)
    {
        // Setup test fixture: the attempt stops at TLS.
        final AttemptMilestones milestones = AttemptMilestones.builder()
            .dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.opened(DOMAIN)).tls(tls).build();
        final S2SChecklistBuilder builder = successfulTest().milestones(milestones).sessionRegistered(false).ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.TLS, CheckStatus.FAIL, expectedKeySuffix);
        assertEquals(expectedParameters, result.parameters(), "the failure must carry its reason where there is one");
        assertNotReached(checklist, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    static java.util.stream.Stream<Arguments> tlsFailures()
    {
        return java.util.stream.Stream.of(
            Arguments.of(Tls.notOffered(), "tls.fail.not_offered", List.of()),
            Arguments.of(Tls.refused(), "tls.fail.refused", List.of()),
            Arguments.of(Tls.handshakeFailed("Received fatal alert: handshake_failure"), "tls.fail.handshake", List.of("Received fatal alert: handshake_failure")),
            Arguments.of(Tls.notTls(), "tls.fail.not_tls", List.of()),
            Arguments.of(Tls.unencrypted(), "tls.fail.unencrypted", List.of()),
            Arguments.of(Tls.notSetUpInTime(Duration.ofSeconds(10)), "tls.fail.stalled", List.of("10")),
            Arguments.of(Tls.closedBeforeSetUp(), "tls.fail.closed", List.of())
        );
    }

    @Test
    public void testHandshakeStoppedByCertificateRejectionFailsBothTlsAndCertificate()
    {
        // Setup test fixture: this server's trust manager rejects the expired remote certificate during the handshake,
        // which is where untrusted, expired and not yet valid certificates are caught.
        final Instant notAfter = Instant.parse("2026-10-01T09:30:15Z");
        final AttemptMilestones milestones = AttemptMilestones.builder()
            .dns(Dns.srvRecords(List.of(Target.plain("a:5269")))).tcp(Tcp.connected("192.0.2.10:5269")).stream(Stream.opened(DOMAIN))
            .tls(Tls.certificateRejected()).certificate(Certificate.expired(notAfter)).build();
        final S2SChecklistBuilder builder = successfulTest().milestones(milestones).sessionRegistered(false).ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult tls = assertCheck(checklist, S2SCheck.TLS, CheckStatus.FAIL, "tls.fail.certificate");
        assertEquals(List.of(), tls.parameters(), "the TLS row leaves the reason to the certificate row");
        final CheckResult certificate = assertCheck(checklist, S2SCheck.CERTIFICATE, CheckStatus.FAIL, "certificate.fail.expired");
        assertEquals(List.of("2026-10-01 09:30:15 UTC"), certificate.parameters(), "the certificate row must say which property failed, and when");
        assertNotReached(checklist, S2SCheck.AUTHENTICATION, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    // Check 6: remote certificate received, valid, trusted and matches target

    @Test
    public void testValidCertificatePasses()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        assertCheck(checklist, S2SCheck.CERTIFICATE, CheckStatus.PASS, "certificate.pass");
    }

    @ParameterizedTest
    @MethodSource("certificateFailures")
    public void testCertificateFailuresNameWhichPropertyFailed(final Certificate certificate, final String expectedKeySuffix, final List<String> expectedParameters)
    {
        // Setup test fixture: the attempt stops at the certificate.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().certificate(certificate).authentication(null).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.CERTIFICATE, CheckStatus.FAIL, expectedKeySuffix);
        assertEquals(expectedParameters, result.parameters(), "the failure must carry the detail the administrator needs to relay");
        assertNotReached(checklist, S2SCheck.AUTHENTICATION, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    static java.util.stream.Stream<Arguments> certificateFailures()
    {
        return java.util.stream.Stream.of(
            Arguments.of(Certificate.notReceived(), "certificate.fail.not_received", List.of()),
            Arguments.of(Certificate.expired(Instant.parse("2026-10-26T10:06:31Z")), "certificate.fail.expired", List.of("2026-10-26 10:06:31 UTC")),
            Arguments.of(Certificate.notYetValid(Instant.parse("2027-01-01T00:00:00Z")), "certificate.fail.not_yet_valid", List.of("2027-01-01 00:00:00 UTC")),
            Arguments.of(Certificate.untrusted(), "certificate.fail.untrusted", List.of()),
            Arguments.of(Certificate.revoked(), "certificate.fail.revoked", List.of()),
            Arguments.of(Certificate.nameMismatch(List.of("other.example.org", "*.other.example.org")), "certificate.fail.name_mismatch", List.of(DOMAIN, "other.example.org, *.other.example.org"))
        );
    }

    // Check 7: authentication

    @Test
    public void testSaslExternalPasses()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        assertCheck(checklist, S2SCheck.AUTHENTICATION, CheckStatus.PASS, "authentication.pass");
    }

    @ParameterizedTest
    @MethodSource("dialbackFallbacks")
    public void testDialbackFallbackWarns(final Authentication authentication, final String expectedKeySuffix)
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest().milestones(successfulMilestones().authentication(authentication).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.AUTHENTICATION, CheckStatus.WARNING, expectedKeySuffix);
        assertCheck(checklist, S2SCheck.SESSION, CheckStatus.PASS, "session.pass");
        assertTrue(checklist.getFirstFailure().isEmpty(), "falling back to dialback must not count as a failure");
    }

    static java.util.stream.Stream<Arguments> dialbackFallbacks()
    {
        return java.util.stream.Stream.of(
            Arguments.of(Authentication.dialbackAfterExternalNotOffered(), "authentication.warning.dialback.not_offered"),
            Arguments.of(Authentication.dialbackAfterExternalRefused(), "authentication.warning.dialback.refused"),
            Arguments.of(Authentication.dialback(), "authentication.warning.dialback")
        );
    }

    @ParameterizedTest
    @MethodSource("authenticationFailures")
    public void testAuthenticationFailures(final Authentication authentication, final String expectedKeySuffix, final List<String> expectedParameters)
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().authentication(authentication).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.AUTHENTICATION, CheckStatus.FAIL, expectedKeySuffix);
        assertEquals(expectedParameters, result.parameters(), "the failure must carry its reason where there is one");
        assertNotReached(checklist, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    static java.util.stream.Stream<Arguments> authenticationFailures()
    {
        return java.util.stream.Stream.of(
            Arguments.of(Authentication.externalRefusedDialbackDisabled(), "authentication.fail.external_refused_dialback_disabled", List.of()),
            Arguments.of(Authentication.dialbackRefused(), "authentication.fail.dialback_refused", List.of()),
            Arguments.of(Authentication.noCommonMechanism(), "authentication.fail.no_common_mechanism", List.of()),
            Arguments.of(Authentication.streamError("unsupported-stanza-type"), "authentication.fail.stream_error", List.of("unsupported-stanza-type")),
            Arguments.of(Authentication.dialbackFallbackFailed(), "authentication.fail.dialback_fallback", List.of()),
            Arguments.of(Authentication.timedOut(Duration.ofSeconds(10)), "authentication.fail.timed_out", List.of("10")),
            Arguments.of(Authentication.closed(), "authentication.fail.closed", List.of())
        );
    }

    @Test
    public void testAuthenticationStreamErrorRequiresTheError()
    {
        assertThrows(IllegalArgumentException.class, () -> new Authentication(Authentication.Outcome.STREAM_ERROR, null), "a stream error during authentication must carry the error the remote sent");
    }

    // Check 8: authenticated outgoing session registered

    @Test
    public void testRegisteredSessionPasses()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        assertCheck(checklist, S2SCheck.SESSION, CheckStatus.PASS, "session.pass");
    }

    @Test
    public void testMissingSessionFailsWhenNoEarlierCheckFailed()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest().sessionRegistered(false).ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.SESSION, CheckStatus.FAIL, "session.fail");
        assertNotReached(checklist, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    // Checks 9 and 10: XMPP ping and the response

    @Test
    public void testResultReplyPassesPingAndResponse()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        assertCheck(checklist, S2SCheck.PING, CheckStatus.PASS, "ping.pass");
        assertCheck(checklist, S2SCheck.RESPONSE, CheckStatus.PASS, "response.pass");
    }

    @Test
    public void testErrorReplyFromRemotePassesPingAndWarnsOnResponse()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest().ping(PingOutcome.remoteError("service-unavailable"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.PING, CheckStatus.PASS, "ping.pass");
        final CheckResult result = assertCheck(checklist, S2SCheck.RESPONSE, CheckStatus.WARNING, "response.warning.error");
        assertEquals(List.of("service-unavailable"), result.parameters(), "the warning must name the error condition the remote replied with");
    }

    @Test
    public void testErrorFromOurOwnServerFailsPing()
    {
        // Setup test fixture: the session exists, yet only our own server answered.
        final S2SChecklistBuilder builder = successfulTest().ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.PING, CheckStatus.FAIL, "ping.fail.local_error");
        assertEquals(List.of("remote-server-not-found"), result.parameters(), "the failure must name the error our own server generated");
        assertNotReached(checklist, S2SCheck.RESPONSE);
    }

    @Test
    public void testPingTimeoutFailsWithConnectBackHint()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest().ping(PingOutcome.timedOut());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.PING, CheckStatus.FAIL, "ping.fail.timed_out");
        assertTrue(BUNDLE.getString(KEY_PREFIX + "ping.fail.timed_out").contains("connect back"), "the timeout message must hint that the remote may be unable to connect back to us");
        assertNotReached(checklist, S2SCheck.RESPONSE);
    }

    // Check 11: no errors in the log

    @Test
    public void testNoLoggedWarningsOrErrorsPasses()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().log(new LogSummary(0, 0, 0)).build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.LOG, CheckStatus.PASS, "log.pass");
        assertEquals(List.of(), result.parameters(), "with nothing logged, the message carries no counts");
    }

    @Test
    public void testOtherWarningsAreReportedOnlyWhenThereAreAny()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().log(new LogSummary(0, 0, 3)).build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.LOG, CheckStatus.PASS, "log.pass.other");
        assertEquals(List.of("3"), result.parameters(), "the message must report the other warnings and errors logged during the test, as information");
    }

    @Test
    public void testLoggedWarningsForAttemptWarn()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().log(new LogSummary(2, 0, 0)).build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.LOG, CheckStatus.WARNING, "log.warning");
        assertEquals(List.of("2"), result.parameters(), "the warning must report how many warnings were recorded");
    }

    @Test
    public void testLoggedWarningsWithOtherWarningsReportsBoth()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().log(new LogSummary(2, 0, 4)).build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.LOG, CheckStatus.WARNING, "log.warning.other");
        assertEquals(List.of("2", "4"), result.parameters(), "the warning must report this attempt's warnings and, separately, the other warnings and errors");
    }

    @Test
    public void testLoggedErrorsForAttemptFail()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().log(new LogSummary(2, 1, 0)).build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.LOG, CheckStatus.FAIL, "log.fail");
        assertEquals(List.of("1", "2"), result.parameters(), "the failure must report the errors and the warnings recorded");
    }

    @Test
    public void testLoggedErrorsWithOtherWarningsReportsBoth()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().log(new LogSummary(2, 1, 5)).build();

        // Verify results.
        final CheckResult result = assertCheck(checklist, S2SCheck.LOG, CheckStatus.FAIL, "log.fail.other");
        assertEquals(List.of("1", "2", "5"), result.parameters(), "the failure must report this attempt's counts and, separately, the other warnings and errors");
    }

    @Test
    public void testOtherServerWarningsDoNotAffectTheLogCheck()
    {
        // Setup test fixture: many unrelated warnings and errors, none for this attempt.
        final S2SChecklistBuilder builder = successfulTest().log(new LogSummary(0, 0, 50));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertEquals(CheckStatus.PASS, checklist.get(S2SCheck.LOG).status(), "warnings and errors not recorded for this attempt are information only, and must not change the check's status");
    }

    @Test
    public void testExpiredCertificateRequiresItsDate()
    {
        assertThrows(NullPointerException.class, () -> Certificate.expired(null), "an expired certificate must carry the date it expired, which the message shows");
    }

    @Test
    public void testNegativeLogCountsAreRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new LogSummary(-1, 0, 0), "a negative count is a programming error and must be rejected");
    }

    // Not observed: steps that did not happen in this attempt

    @Test
    public void testReusedSessionShowsStepsAsNotObserved()
    {
        // Setup test fixture: an existing session was reused, so no connection steps were recorded.
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH)
            .milestones(AttemptMilestones.none())
            .sessionRegistered(true)
            .ping(PingOutcome.result());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        for (final S2SCheck check : List.of(S2SCheck.DNS, S2SCheck.TCP, S2SCheck.STREAM, S2SCheck.TLS, S2SCheck.CERTIFICATE, S2SCheck.AUTHENTICATION, S2SCheck.LOG)) {
            assertCheck(checklist, check, CheckStatus.NOT_OBSERVED, "not_observed");
        }
        assertCheck(checklist, S2SCheck.SESSION, CheckStatus.PASS, "session.pass");
        assertCheck(checklist, S2SCheck.PING, CheckStatus.PASS, "ping.pass");
    }

    @Test
    public void testNoInputsAfterValidDomainShowsEverythingNotObserved()
    {
        // Execute system under test.
        final S2SChecklist checklist = new S2SChecklistBuilder(DOMAIN, DomainCheck.VALID, ENGLISH).build();

        // Verify results.
        assertCheck(checklist, S2SCheck.DOMAIN, CheckStatus.PASS, "domain.pass");
        checklist.getResults().stream().skip(1).forEach(result -> {
            assertEquals(CheckStatus.NOT_OBSERVED, result.status(), "check " + result.check() + " must not pass without a recorded fact");
            assertRenderable(result);
        });
    }

    @ParameterizedTest
    @EnumSource(value = S2SCheck.class, names = {"DNS", "TCP", "STREAM", "TLS", "CERTIFICATE", "AUTHENTICATION"})
    public void testMissingMilestoneIsNotObservedWithoutAnEarlierFailure(final S2SCheck missing)
    {
        // Setup test fixture: every milestone except one.
        final AttemptMilestones.Builder milestones = successfulMilestones();
        switch (missing) {
            case DNS -> milestones.dns(null);
            case TCP -> milestones.tcp(null);
            case STREAM -> milestones.stream(null);
            case TLS -> milestones.tls(null);
            case CERTIFICATE -> milestones.certificate(null);
            case AUTHENTICATION -> milestones.authentication(null);
            default -> fail("unexpected check " + missing);
        }

        // Execute system under test.
        final S2SChecklist checklist = successfulTest().milestones(milestones.build()).build();

        // Verify results.
        assertCheck(checklist, missing, CheckStatus.NOT_OBSERVED, "not_observed");
        assertTrue(checklist.getFirstFailure().isEmpty(), "an unobserved step is not a failure");
    }

    // Not reached, when the attempt continues past a failed check

    @Test
    public void testStepsRecordedAfterAFailureShowTheirOwnResult()
    {
        // Setup test fixture: the connection went ahead unencrypted (TLS optional on both sides), so no certificate was
        // exchanged, and the remote was authenticated with dialback.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().tls(Tls.unencrypted()).certificate(null).authentication(Authentication.dialbackAfterExternalNotOffered()).build());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.TLS, CheckStatus.FAIL, "tls.fail.unencrypted");
        assertNotReached(checklist, S2SCheck.CERTIFICATE);
        assertCheck(checklist, S2SCheck.AUTHENTICATION, CheckStatus.WARNING, "authentication.warning.dialback.not_offered");
        assertCheck(checklist, S2SCheck.SESSION, CheckStatus.PASS, "session.pass");
        assertCheck(checklist, S2SCheck.PING, CheckStatus.PASS, "ping.pass");
        assertCheck(checklist, S2SCheck.RESPONSE, CheckStatus.PASS, "response.pass");
    }

    @Test
    public void testRecordedFailureAfterAnEarlierFailureIsShown()
    {
        // Setup test fixture: the certificate was rejected, then dialback was tried and refused.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().certificate(Certificate.untrusted()).authentication(Authentication.dialbackRefused()).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.CERTIFICATE, CheckStatus.FAIL, "certificate.fail.untrusted");
        assertCheck(checklist, S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "authentication.fail.dialback_refused");
        assertNotReached(checklist, S2SCheck.SESSION, S2SCheck.PING, S2SCheck.RESPONSE);
    }

    @Test
    public void testPingFailureAfterTheAttemptResumedIsShown()
    {
        // Setup test fixture: unencrypted but authenticated; the ping then timed out.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().tls(Tls.unencrypted()).certificate(null).authentication(Authentication.dialback()).build())
            .ping(PingOutcome.timedOut());

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.SESSION, CheckStatus.PASS, "session.pass");
        assertCheck(checklist, S2SCheck.PING, CheckStatus.FAIL, "ping.fail.timed_out");
    }

    @Test
    public void testLogCheckIsEvaluatedAfterAFailure()
    {
        // Setup test fixture.
        final S2SChecklistBuilder builder = successfulTest()
            .milestones(successfulMilestones().tcp(Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269")))).stream(null).tls(null).certificate(null).authentication(null).build())
            .sessionRegistered(false)
            .ping(PingOutcome.localError("remote-server-not-found"))
            .log(new LogSummary(0, 1, 0));

        // Execute system under test.
        final S2SChecklist checklist = builder.build();

        // Verify results.
        assertCheck(checklist, S2SCheck.LOG, CheckStatus.FAIL, "log.fail");
    }

    // Value types

    @Test
    public void testChecklistGetReturnsTheResultForEachCheck()
    {
        // Execute system under test.
        final S2SChecklist checklist = successfulTest().build();

        // Verify results.
        for (final S2SCheck check : S2SCheck.values()) {
            assertEquals(check, checklist.get(check).check(), "get(" + check + ") must return that check's result");
        }
    }

    @Test
    public void testResultParametersAreImmutable()
    {
        // Execute system under test.
        final CheckResult result = successfulTest().build().get(S2SCheck.TLS);

        // Verify results.
        assertThrows(UnsupportedOperationException.class, () -> result.parameters().add("x"), "a result must not be modifiable after it is built");
    }
}
