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
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.CappedList;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Certificate;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Stream;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tls;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the checklist of a server-to-server connection test from what was recorded: the verdict on the domain, the
 * milestones of the connection attempt, whether a session was registered, the outcome of the ping, and the warnings
 * and errors logged.
 *
 * Each check is judged only on its own recorded fact. A check with nothing recorded shows "not observed", never a pass.
 *
 * A connection that goes ahead unencrypted, as this server's policy allows, is a warning on the TLS check, and the
 * certificate check shows "not reached", as no certificate is exchanged.
 *
 * After a check fails, a later check shows "not reached" unless its step still ran. Openfire can carry on past some
 * failures (Server Dialback after this server rejects the remote certificate), and a step that ran shows its own
 * result. A failed session, ping or response that follows an earlier
 * failure is a consequence of it, so it also shows "not reached". When the domain itself is rejected no attempt is made,
 * and every other check shows "not reached". Otherwise the log check is judged whenever the log was captured, even
 * after a failure, because it covers the whole attempt.
 *
 * @author Matthew Vivian
 */
public final class S2SChecklistBuilder
{
    /**
     * The i18n keys of the text that this builder composes from recorded values (a list, what happened at each address),
     * so that a translated admin console shows it in its own language.
     */
    private static final String LIST_KEY = S2SCheck.KEY_PREFIX + "list";
    private static final String MORE_KEY = S2SCheck.KEY_PREFIX + "list.more";
    private static final String DIRECT_TLS_TARGET_KEY = S2SCheck.DNS.messageKey("target.direct_tls");
    private static final String FAILURES_KEY = S2SCheck.TCP.messageKey("failures");
    private static final String FAILURE_REFUSED_KEY = S2SCheck.TCP.messageKey("failure.refused");
    private static final String FAILURE_TIMED_OUT_KEY = S2SCheck.TCP.messageKey("failure.timed_out");
    private static final String FAILURE_OTHER_KEY = S2SCheck.TCP.messageKey("failure.other");

    /**
     * Certificate validity limits are shown to the second and always in UTC, so that a limit later on the same day is
     * not mistaken for one that has passed, whatever the time zone of the server or the reader.
     */
    private static final DateTimeFormatter VALIDITY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    /**
     * Looks up an i18n key and fills in its placeholders, in the reader's language. The admin console passes
     * {@code LocaleUtils::getLocalizedString}.
     */
    @FunctionalInterface
    public interface Localiser
    {
        /**
         * @param key the i18n key.
         * @param arguments the values for its placeholders, in order.
         * @return the text in the reader's language.
         */
        String localise(String key, List<String> arguments);
    }

    private final String domain;
    private final DomainCheck domainCheck;
    private final Localiser localiser;
    private AttemptMilestones milestones = AttemptMilestones.none();
    private Boolean sessionRegistered;
    private PingOutcome ping;
    private LogSummary log;

    /**
     * @param domain the XMPP domain that was tested, as entered.
     * @param domainCheck the verdict on that domain, made before any connection was attempted.
     * @param localiser translates the text that this builder composes from recorded values.
     */
    public S2SChecklistBuilder(final String domain, final DomainCheck domainCheck, final Localiser localiser)
    {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.domainCheck = Objects.requireNonNull(domainCheck, "domainCheck");
        this.localiser = Objects.requireNonNull(localiser, "localiser");
    }

    /**
     * @param milestones what was recorded during the connection attempt; null when nothing was recorded.
     * @return this builder.
     */
    public S2SChecklistBuilder milestones(final AttemptMilestones milestones)
    {
        this.milestones = milestones == null ? AttemptMilestones.none() : milestones;
        return this;
    }

    /**
     * @param registered whether an authenticated outgoing session to the domain was registered after the attempt.
     * @return this builder.
     */
    public S2SChecklistBuilder sessionRegistered(final boolean registered)
    {
        this.sessionRegistered = registered;
        return this;
    }

    /**
     * @param ping what came back after the ping; null when no ping was sent.
     * @return this builder.
     */
    public S2SChecklistBuilder ping(final PingOutcome ping)
    {
        this.ping = ping;
        return this;
    }

    /**
     * @param log the warnings and errors logged during the test; null when the log was not captured.
     * @return this builder.
     */
    public S2SChecklistBuilder log(final LogSummary log)
    {
        this.log = log;
        return this;
    }

    public S2SChecklist build()
    {
        final List<CheckResult> results = new ArrayList<>();
        final CheckResult domainResult = judgeDomain();
        results.add(domainResult);

        if (domainResult.status() == CheckStatus.FAIL) {
            for (final S2SCheck check : S2SCheck.values()) {
                if (check != S2SCheck.DOMAIN) {
                    results.add(notReached(check));
                }
            }
            return new S2SChecklist(results);
        }

        final Sequence sequence = new Sequence(results);
        sequence.addStep(S2SCheck.DNS, milestones.getDns().map(this::judgeDns));
        sequence.addStep(S2SCheck.TCP, milestones.getTcp().map(this::judgeTcp));
        // The steps are judged in the order they happen, which decides what an earlier failure leaves not reached. Over
        // direct TLS the handshake, and with it the certificate, comes before the stream; over STARTTLS, after it.
        if (milestones.isDirectTls()) {
            sequence.addStep(S2SCheck.TLS, milestones.getTls().map(S2SChecklistBuilder::judgeTls));
            sequence.addStep(S2SCheck.CERTIFICATE, certificateResult());
            sequence.addStep(S2SCheck.STREAM, milestones.getStream().map(S2SChecklistBuilder::judgeStream));
        } else {
            sequence.addStep(S2SCheck.STREAM, milestones.getStream().map(S2SChecklistBuilder::judgeStream));
            sequence.addStep(S2SCheck.TLS, milestones.getTls().map(S2SChecklistBuilder::judgeTls));
            sequence.addStep(S2SCheck.CERTIFICATE, certificateResult());
        }
        sequence.addStep(S2SCheck.AUTHENTICATION, milestones.getAuthentication().map(S2SChecklistBuilder::judgeAuthentication));
        sequence.addConsequence(S2SCheck.SESSION, Optional.ofNullable(sessionRegistered).map(S2SChecklistBuilder::judgeSession));
        sequence.addConsequence(S2SCheck.PING, Optional.ofNullable(ping).map(S2SChecklistBuilder::judgePing));
        sequence.addConsequence(S2SCheck.RESPONSE, Optional.ofNullable(ping).flatMap(S2SChecklistBuilder::judgeResponse));
        results.add(Optional.ofNullable(log).map(S2SChecklistBuilder::judgeLog).orElseGet(() -> notObserved(S2SCheck.LOG)));
        results.sort(Comparator.comparing(CheckResult::check));
        return new S2SChecklist(results);
    }

    /**
     * Applies the "not reached" rule to checks 2 to 10, in the order their steps happen.
     */
    private static final class Sequence
    {
        private final List<CheckResult> results;
        private boolean stopped;

        private Sequence(final List<CheckResult> results)
        {
            this.results = results;
        }

        /**
         * A step whose milestone is recorded only when the step ran, so a recorded failure is shown even after an
         * earlier failure.
         */
        void addStep(final S2SCheck check, final Optional<CheckResult> recorded)
        {
            add(check, recorded, true);
        }

        /**
         * A fact that is known whether or not the attempt got that far (the session, the ping), so a failure after an
         * earlier failure is its consequence and shows as not reached.
         */
        void addConsequence(final S2SCheck check, final Optional<CheckResult> recorded)
        {
            add(check, recorded, false);
        }

        private void add(final S2SCheck check, final Optional<CheckResult> recorded, final boolean failureShownAfterStop)
        {
            if (recorded.isEmpty()) {
                results.add(stopped ? notReached(check) : notObserved(check));
                return;
            }
            final CheckResult result = recorded.get();
            if (result.status() == CheckStatus.FAIL) {
                results.add(stopped && !failureShownAfterStop ? notReached(check) : result);
                stopped = true;
            } else {
                results.add(result);
                stopped = false;
            }
        }
    }

    private CheckResult judgeDomain()
    {
        final String suffix = switch (domainCheck) {
            case VALID -> "pass";
            case INVALID -> "fail.invalid";
            case LOCAL -> "fail.local";
            case COMPONENT -> "fail.component";
            case BLOCKED_BY_POLICY -> "fail.blocked";
        };
        final CheckStatus status = domainCheck == DomainCheck.VALID ? CheckStatus.PASS : CheckStatus.FAIL;
        return result(S2SCheck.DOMAIN, status, suffix, domain);
    }

    private CheckResult judgeDns(final Dns dns)
    {
        return switch (dns.outcome()) {
            case SRV_RECORDS -> result(S2SCheck.DNS, CheckStatus.PASS, "pass", targets(dns));
            // RFC 6120 allows the domain name itself when no SRV records are published, so this is not a fault.
            case FALLBACK_ONLY -> result(S2SCheck.DNS, CheckStatus.PASS, "pass.fallback", targets(dns));
            case OVERRIDE -> result(S2SCheck.DNS, CheckStatus.PASS, "pass.override", targets(dns));
            case NO_ADDRESS -> result(S2SCheck.DNS, CheckStatus.FAIL, "fail.no_address", domain);
        };
    }

    private CheckResult judgeTcp(final Tcp tcp)
    {
        return switch (tcp.outcome()) {
            case CONNECTED -> result(S2SCheck.TCP, CheckStatus.PASS, "pass", tcp.endpoint());
            // The connection works, but an address that failed can be tried before a working one on later connections,
            // which costs time whenever it times out; a pass would hide it.
            case CONNECTED_AFTER_FAILURES -> result(S2SCheck.TCP, CheckStatus.WARNING, "warning.earlier_failures", tcp.endpoint(), failures(tcp));
            case CONNECTED_TO_FALLBACK -> tcp.failures().isEmpty()
                ? result(S2SCheck.TCP, CheckStatus.WARNING, "warning.fallback.unresolved", tcp.endpoint())
                : result(S2SCheck.TCP, CheckStatus.WARNING, "warning.fallback", tcp.endpoint(), failures(tcp));
            case REFUSED -> result(S2SCheck.TCP, CheckStatus.FAIL, "fail.refused", failedEndpoints(tcp));
            case TIMED_OUT -> result(S2SCheck.TCP, CheckStatus.FAIL, "fail.timed_out", failedEndpoints(tcp));
            case FAILED -> result(S2SCheck.TCP, CheckStatus.FAIL, "fail.other", failedEndpoints(tcp), failures(tcp));
            case NONE_ATTEMPTED -> result(S2SCheck.TCP, CheckStatus.FAIL, "fail.none_attempted");
        };
    }

    private static CheckResult judgeStream(final Stream stream)
    {
        return switch (stream.outcome()) {
            case OPENED -> stream.detail() == null
                ? result(S2SCheck.STREAM, CheckStatus.PASS, "pass.unnamed")
                : result(S2SCheck.STREAM, CheckStatus.PASS, "pass", stream.detail());
            case CLOSED_BY_REMOTE -> result(S2SCheck.STREAM, CheckStatus.FAIL, "fail.closed");
            case STREAM_ERROR -> result(S2SCheck.STREAM, CheckStatus.FAIL, "fail.error", stream.detail());
            case TIMED_OUT -> result(S2SCheck.STREAM, CheckStatus.FAIL, "fail.timed_out", stream.detail());
            case MISSING_ID -> result(S2SCheck.STREAM, CheckStatus.FAIL, "fail.no_id");
        };
    }

    private static CheckResult judgeTls(final Tls tls)
    {
        return switch (tls.outcome()) {
            case NEGOTIATED -> result(S2SCheck.TLS, CheckStatus.PASS, "pass", tls.protocol(), tls.cipherSuite());
            case NOT_OFFERED -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.not_offered");
            case REFUSED -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.refused");
            case HANDSHAKE_FAILED -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.handshake", tls.reason());
            case NOT_TLS -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.not_tls");
            // The certificate check, which follows, says which property of the certificate failed.
            case CERTIFICATE_REJECTED -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.certificate");
            // This server's policy allows the connection to go ahead without TLS, and it did.
            case UNENCRYPTED -> result(S2SCheck.TLS, CheckStatus.WARNING, "warning.unencrypted");
            case REQUIRED_BUT_DISABLED -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.disabled");
            case TIMED_OUT -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.timed_out", tls.reason());
            case NOT_SET_UP_IN_TIME -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.stalled", tls.reason());
            case CLOSED_BEFORE_SET_UP -> result(S2SCheck.TLS, CheckStatus.FAIL, "fail.closed");
        };
    }

    /**
     * @return the verdict on the remote certificate; on a connection that went ahead unencrypted, where no certificate is
     *         exchanged, "not reached" with that reason, rather than "not observed".
     */
    private Optional<CheckResult> certificateResult()
    {
        final Optional<CheckResult> recorded = milestones.getCertificate().map(this::judgeCertificate);
        if (recorded.isEmpty() && milestones.getTls().map(tls -> tls.outcome() == Tls.Outcome.UNENCRYPTED).orElse(false)) {
            return Optional.of(result(S2SCheck.CERTIFICATE, CheckStatus.NOT_REACHED, "not_reached.unencrypted"));
        }
        return recorded;
    }

    private CheckResult judgeCertificate(final Certificate certificate)
    {
        return switch (certificate.outcome()) {
            case VALID -> result(S2SCheck.CERTIFICATE, CheckStatus.PASS, "pass");
            case NOT_RECEIVED -> result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.not_received");
            case EXPIRED -> result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.expired", VALIDITY_FORMAT.format(certificate.validityLimit()));
            case NOT_YET_VALID -> result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.not_yet_valid", VALIDITY_FORMAT.format(certificate.validityLimit()));
            case UNTRUSTED -> result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.untrusted");
            case REVOKED -> result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.revoked");
            case NAME_MISMATCH -> result(S2SCheck.CERTIFICATE, CheckStatus.FAIL, "fail.name_mismatch", domain, list(LIST_KEY, certificate.presentedNames(), name -> name));
        };
    }

    private static CheckResult judgeAuthentication(final Authentication authentication)
    {
        return switch (authentication.outcome()) {
            case SASL_EXTERNAL -> result(S2SCheck.AUTHENTICATION, CheckStatus.PASS, "pass");
            case DIALBACK -> result(S2SCheck.AUTHENTICATION, CheckStatus.WARNING, "warning.dialback");
            case DIALBACK_AFTER_EXTERNAL_NOT_OFFERED -> result(S2SCheck.AUTHENTICATION, CheckStatus.WARNING, "warning.dialback.not_offered");
            case DIALBACK_AFTER_EXTERNAL_REFUSED -> result(S2SCheck.AUTHENTICATION, CheckStatus.WARNING, "warning.dialback.refused");
            case EXTERNAL_REFUSED_DIALBACK_DISABLED -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.external_refused_dialback_disabled");
            case DIALBACK_REFUSED -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.dialback_refused");
            case NO_COMMON_MECHANISM -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.no_common_mechanism");
            case STREAM_ERROR -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.stream_error", authentication.reason());
            case DIALBACK_FALLBACK_FAILED -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.dialback_fallback");
            case TIMED_OUT -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.timed_out", authentication.reason());
            case CLOSED -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.closed");
            case CLOSED_AFTER_EXTERNAL_ACCEPTED -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.closed_after_external");
            case REUSED_STREAM_ID -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.reused_stream_id");
            case RESTARTED_STREAM_MISSING_ID -> result(S2SCheck.AUTHENTICATION, CheckStatus.FAIL, "fail.restarted_stream_no_id");
        };
    }

    private static CheckResult judgeSession(final boolean registered)
    {
        return registered
            ? result(S2SCheck.SESSION, CheckStatus.PASS, "pass")
            : result(S2SCheck.SESSION, CheckStatus.FAIL, "fail");
    }

    private static CheckResult judgePing(final PingOutcome ping)
    {
        return switch (ping.kind()) {
            case RESULT, REMOTE_ERROR -> result(S2SCheck.PING, CheckStatus.PASS, "pass");
            case LOCAL_ERROR -> result(S2SCheck.PING, CheckStatus.FAIL, "fail.local_error", ping.errorCondition());
            case TIMED_OUT -> result(S2SCheck.PING, CheckStatus.FAIL, "fail.timed_out");
        };
    }

    /**
     * Judges the reply from the remote. Without one, the ping check has failed, and this check is not reached.
     */
    private static Optional<CheckResult> judgeResponse(final PingOutcome ping)
    {
        return switch (ping.kind()) {
            case RESULT -> Optional.of(result(S2SCheck.RESPONSE, CheckStatus.PASS, "pass"));
            case REMOTE_ERROR -> Optional.of(result(S2SCheck.RESPONSE, CheckStatus.WARNING, "warning.error", ping.errorCondition()));
            case LOCAL_ERROR, TIMED_OUT -> Optional.empty();
        };
    }

    private static CheckResult judgeLog(final LogSummary log)
    {
        // Warnings and errors not recorded for this attempt are information only, mentioned when there are any.
        final List<String> parameters = new ArrayList<>();
        final CheckStatus status;
        String suffix;
        if (log.attemptErrors() > 0) {
            status = CheckStatus.FAIL;
            suffix = "fail";
            parameters.add(String.valueOf(log.attemptErrors()));
            parameters.add(String.valueOf(log.attemptWarnings()));
        } else if (log.attemptWarnings() > 0) {
            status = CheckStatus.WARNING;
            suffix = "warning";
            parameters.add(String.valueOf(log.attemptWarnings()));
        } else {
            status = CheckStatus.PASS;
            suffix = "pass";
        }
        if (log.otherWarningsAndErrors() > 0) {
            suffix += ".other";
            parameters.add(String.valueOf(log.otherWarningsAndErrors()));
        }
        return result(S2SCheck.LOG, status, suffix, parameters.toArray(new String[0]));
    }

    private static CheckResult notReached(final S2SCheck check)
    {
        return new CheckResult(check, CheckStatus.NOT_REACHED, S2SCheck.KEY_PREFIX + "not_reached", List.of());
    }

    private static CheckResult notObserved(final S2SCheck check)
    {
        return new CheckResult(check, CheckStatus.NOT_OBSERVED, S2SCheck.KEY_PREFIX + "not_observed", List.of());
    }

    private static CheckResult result(final S2SCheck check, final CheckStatus status, final String suffix, final String... parameters)
    {
        return new CheckResult(check, status, check.messageKey(suffix), List.of(parameters));
    }

    /**
     * @return the DNS targets, a direct TLS target marked as such.
     */
    private String targets(final Dns dns)
    {
        return list(LIST_KEY, dns.targets(), target -> target.directTls() ? localise(DIRECT_TLS_TARGET_KEY, target.hostAndPort()) : target.hostAndPort());
    }

    /**
     * @return every address tried, when none connected.
     */
    private String failedEndpoints(final Tcp tcp)
    {
        return list(LIST_KEY, tcp.failures(), AddressFailure::endpoint);
    }

    /**
     * @return what happened at each address that failed. The text of a failure other than a refusal or a timeout is
     *         the operating system's or the JVM's own, which cannot be translated.
     */
    private String failures(final Tcp tcp)
    {
        return list(FAILURES_KEY, tcp.failures(), failure -> switch (failure.outcome()) {
            case REFUSED -> localise(FAILURE_REFUSED_KEY, failure.endpoint());
            case TIMED_OUT -> localise(FAILURE_TIMED_OUT_KEY, failure.endpoint());
            default -> localise(FAILURE_OTHER_KEY, failure.endpoint(), failure.reason());
        });
    }

    /**
     * Lists values in the reader's language, followed by how many were left out. Each pair is joined by a message, so
     * that a language can choose its own separator; a single value is returned as it is.
     *
     * @param separatorKey the i18n key that joins two values.
     * @param list the values, with how many were left out.
     * @param describe turns a value into text.
     * @return the list as text.
     */
    private <T> String list(final String separatorKey, final CappedList<T> list, final Function<T, String> describe)
    {
        final List<String> values = new ArrayList<>();
        list.values().forEach(value -> values.add(describe.apply(value)));
        if (list.leftOut() > 0) {
            values.add(localise(MORE_KEY, String.valueOf(list.leftOut())));
        }
        if (values.isEmpty()) {
            return "";
        }
        String joined = values.get(0);
        for (final String value : values.subList(1, values.size())) {
            joined = localise(separatorKey, joined, value);
        }
        return joined;
    }

    private String localise(final String key, final String... arguments)
    {
        return localiser.localise(key, List.of(arguments));
    }
}
