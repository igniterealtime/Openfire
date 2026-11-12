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

import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Authentication;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.CappedList;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Certificate;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Stream;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Collects the milestones of one outbound server-to-server connection attempt, as the attempt happens.
 *
 * Every outbound attempt has a recorder, not only those started by the connection test: the test only routes a ping,
 * and the attempt runs on another thread. The code that makes the connection writes milestones from the threads it
 * runs on, while the test reads them from its own thread and may do so before the attempt has finished, so every
 * method is thread-safe. Readers take an immutable {@link #snapshot()}.
 *
 * @author Matthew Vivian
 */
public final class AttemptRecorder
{
    /**
     * How the attempt ended.
     */
    public enum Outcome
    {
        /**
         * The attempt has not finished.
         */
        IN_PROGRESS,

        /**
         * A new connection was made and a session established.
         */
        NEW_SESSION,

        /**
         * An existing session was used, so no new connection was made.
         */
        REUSED_SESSION,

        /**
         * No session was established.
         */
        FAILED
    }

    /**
     * The warnings and errors logged for an attempt.
     *
     * @param warnings the number of warnings.
     * @param errors the number of errors.
     */
    public record LoggedProblems(int warnings, int errors) {}

    private static final Logger Log = LoggerFactory.getLogger(AttemptRecorder.class);

    private static final String WARNING_PREFIX = "WARN:";
    private static final String ERROR_PREFIX = "ERROR:";

    /**
     * The number of characters of diagnostic log kept for an attempt, counting each line with its line break.
     * Recorders are kept for many domain pairs, so a long log must not grow without limit; the warning and error counts
     * still cover every line. A longer log keeps whole lines from its start (up to {@link #DIAGNOSTIC_LOG_HEAD_LENGTH})
     * and from its end (the rest), with one line in between that says how many were left out. Most is kept from the
     * end, where the lines that describe each step and the failure that ended the attempt are.
     */
    public static final int MAX_DIAGNOSTIC_LOG_LENGTH = 16_000;

    /**
     * The part of {@link #MAX_DIAGNOSTIC_LOG_LENGTH} that a long diagnostic log keeps from its start.
     */
    static final int DIAGNOSTIC_LOG_HEAD_LENGTH = 4_000;

    /**
     * Marks where text was cut. ASCII, so that a string holding it keeps Java's compact one-byte-per-character form.
     */
    public static final String CUT_MARKER = "...";

    /**
     * The length, in characters, to which a diagnostic log line kept for an attempt is cut. A line can carry text that
     * the remote server chose, such as the names its DNS answer lists, and recorders are kept for many domain pairs.
     */
    public static final int MAX_DIAGNOSTIC_LOG_LINE_LENGTH = 500;

    private final String localDomain;
    private final String remoteDomain;
    private final Instant started;

    // Guarded by this.
    private final AttemptMilestones.Builder milestones = AttemptMilestones.builder();
    private Outcome outcome = Outcome.IN_PROGRESS;
    private LoggedProblems diagnosticLogProblems;
    private List<String> diagnosticLogLines;
    private int warningsLoggedElsewhere;
    private int errorsLoggedElsewhere;
    private boolean gaveUp;
    private boolean authenticationStarted;
    private boolean externalAccepted;

    /**
     * Starts recording an attempt, now.
     *
     * @param localDomain the domain the connection is made from.
     * @param remoteDomain the domain the connection is made to.
     */
    public AttemptRecorder(final String localDomain, final String remoteDomain)
    {
        this.localDomain = Objects.requireNonNull(localDomain, "localDomain");
        this.remoteDomain = Objects.requireNonNull(remoteDomain, "remoteDomain");
        this.started = Instant.now();
    }

    /**
     * Runs a recording step from the connection code. Recording only describes what happened, so a step that fails is
     * logged and never allowed to change how the connection proceeds.
     *
     * @param recording the recording step.
     */
    public static void safely(final Runnable recording)
    {
        try {
            recording.run();
        } catch (final RuntimeException e) {
            Log.debug("Unable to record a milestone of a connection attempt.", e);
        }
    }

    /**
     * Reads from a recorder on behalf of the connection code, as {@link #safely(Runnable)}: a read that fails is logged,
     * and the fallback is used in its place.
     *
     * @param reading the read.
     * @param fallback the value to use when the read fails.
     * @param <T> the type of the value read.
     * @return the value read, or the fallback.
     */
    public static <T> T safely(final Supplier<T> reading, final T fallback)
    {
        try {
            return reading.get();
        } catch (final RuntimeException e) {
            Log.debug("Unable to read the milestones of a connection attempt.", e);
            return fallback;
        }
    }

    public String getLocalDomain()
    {
        return localDomain;
    }

    public String getRemoteDomain()
    {
        return remoteDomain;
    }

    public Instant getStarted()
    {
        return started;
    }

    public synchronized Outcome getOutcome()
    {
        return outcome;
    }

    /**
     * Records how the attempt ended. A later call replaces an earlier one: a session can be created and the attempt
     * still fail afterwards.
     *
     * @param outcome how the attempt ended; not {@link Outcome#IN_PROGRESS}.
     */
    public synchronized void finish(final Outcome outcome)
    {
        if (Objects.requireNonNull(outcome, "outcome") == Outcome.IN_PROGRESS) {
            throw new IllegalArgumentException("An attempt cannot finish as in progress.");
        }
        this.outcome = outcome;
        notifyAll();
    }

    /**
     * Waits for the attempt to finish. An attempt has finished once its diagnostic log is recorded, which
     * {@code LocalOutgoingServerSession.authenticateDomain} does last, after the outcome: until then, the log check
     * could not be judged.
     *
     * @param timeout how long to wait at most.
     * @return true when the attempt has finished, false when it is still in progress after the timeout.
     * @throws InterruptedException when the waiting thread is interrupted.
     */
    public synchronized boolean awaitFinished(final Duration timeout) throws InterruptedException
    {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (diagnosticLogProblems == null) {
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        return true;
    }

    /*
     * Once this server has given up waiting for the session (see gaveUpWaiting), the record is final: the connection
     * code can still report milestones while the connection is being stopped, but they come too late to be part of the
     * attempt, and would otherwise overwrite the timeout that ended it.
     */

    public synchronized void dns(final Dns dns)
    {
        if (!gaveUp) {
            milestones.dns(dns);
        }
    }

    public synchronized void tcp(final Tcp tcp)
    {
        if (!gaveUp) {
            milestones.tcp(tcp);
        }
    }

    public synchronized void stream(final Stream stream)
    {
        if (!gaveUp) {
            milestones.stream(stream);
        }
    }

    /**
     * Records a stream error sent by the remote server against the step it interrupted. Over STARTTLS, before TLS or the
     * certificate has been recorded, it is the stream failing; after that, it is authentication failing. Over direct
     * TLS, the handshake comes before the stream, so it is the stream failing until this server has started to
     * authenticate. When authentication is failing, the stream is left as opened. Once authentication has been
     * recorded, the connection was set up, and the error is not part of this attempt's checks. A failure inferred from
     * the connection closing gives way to the error, which explains it: the close can reach this recorder first. An
     * error that arrives after this server gave up waiting changes nothing, as the attempt had already ended.
     *
     * @param errorText the stream error condition, with its text when the remote sent one.
     */
    public synchronized void remoteStreamError(final String errorText)
    {
        if (gaveUp) {
            return;
        }
        AttemptMilestones recorded = milestones.build();
        if (recorded.getAuthentication().map(authentication -> authentication.outcome() == Authentication.Outcome.CLOSED || authentication.outcome() == Authentication.Outcome.CLOSED_AFTER_EXTERNAL_ACCEPTED).orElse(false)) {
            milestones.authentication(null);
        }
        if (recorded.getTls().map(tls -> tls.outcome() == Tls.Outcome.CLOSED_BEFORE_SET_UP).orElse(false)) {
            milestones.tls(null);
        }
        recorded = milestones.build();
        if (recorded.getAuthentication().isPresent()) {
            return;
        }
        final boolean streamUnderWay = recorded.isDirectTls()
            ? !authenticationStarted
            : recorded.getTls().isEmpty() && recorded.getCertificate().isEmpty();
        if (streamUnderWay) {
            milestones.stream(Stream.streamError(errorText));
        } else {
            milestones.authentication(Authentication.streamError(errorText));
        }
    }

    /**
     * Records that this server has started to authenticate (with SASL EXTERNAL or Server Dialback), so that a stream
     * error after this point is held against authentication rather than the stream.
     */
    public synchronized void authenticationStarted()
    {
        authenticationStarted = true;
    }

    /**
     * Records that the remote server accepted SASL EXTERNAL. Authentication succeeds only once the remote has restarted
     * the stream, so a close before that is recorded as a close after the remote accepted the certificate.
     */
    public synchronized void externalAccepted()
    {
        externalAccepted = true;
    }

    /**
     * Records whether the connection uses direct TLS, in which the TLS handshake comes before the stream.
     *
     * @param directTls true for direct TLS, false for STARTTLS.
     */
    public synchronized void directTls(final boolean directTls)
    {
        milestones.directTls(directTls);
    }

    /**
     * The steps that a close or a wait can be recorded against. The certificate is recorded with TLS.
     */
    private enum Step { STREAM, TLS, AUTHENTICATION }

    /**
     * @return the step that was under way: the first that has no record, in the order the connection takes them. Over
     *         STARTTLS that is the stream, then TLS, then authentication; over direct TLS, the TLS handshake comes first.
     *         This is the one place that knows that order, so a change to it in the connection code is made here.
     */
    private static Step stepUnderWay(final AttemptMilestones recorded)
    {
        if (recorded.isDirectTls() && recorded.getTls().isEmpty()) {
            return Step.TLS;
        }
        if (recorded.getStream().isEmpty()) {
            return Step.STREAM;
        }
        if (recorded.getTls().isEmpty()) {
            return Step.TLS;
        }
        return Step.AUTHENTICATION;
    }

    /**
     * Records that the connection closed, against the step that was under way (see {@link #stepUnderWay}). The record
     * says only that the connection closed: this server cannot tell which side closed it. A close after a step that
     * recorded a failure is explained by that step, as this server closes the connection itself then, and nothing is
     * added; so is a close once authentication was recorded, and a close after this server gave up waiting, which is
     * this server stopping the connection. Over direct TLS, a close before the handshake finished is recorded by the
     * handshake itself. A close after the remote accepted SASL EXTERNAL, before it restarted the stream, says so.
     */
    public synchronized void connectionClosed()
    {
        final AttemptMilestones recorded = milestones.build();
        if (gaveUp || recorded.getAuthentication().isPresent() || !nothingFailedSoFar(recorded)) {
            return;
        }
        switch (stepUnderWay(recorded)) {
            case STREAM -> milestones.stream(Stream.closed());
            case TLS -> {
                if (!recorded.isDirectTls()) {
                    milestones.tls(Tls.closedBeforeSetUp());
                }
            }
            case AUTHENTICATION -> milestones.authentication(externalAccepted ? Authentication.closedAfterExternalAccepted() : Authentication.closed());
        }
    }

    /**
     * Records that this server stopped waiting for the session, against the step that was under way (see
     * {@link #stepUnderWay}). A failed stream explains the wait, and nothing is added. After a failed TLS handshake, no
     * stream can follow on that connection, so the wait is recorded against authentication, as only the Server Dialback
     * fallback over a new connection can still be under way. Once authentication was recorded, nothing is added. A
     * remote that closed the connection first is not reported as silent, as the close was recorded against the same
     * step. From then on, the record is final: milestones reported later are ignored.
     *
     * @param waited how long this server waited.
     */
    public synchronized void gaveUpWaiting(final Duration waited)
    {
        if (gaveUp) {
            return;
        }
        gaveUp = true;
        final AttemptMilestones recorded = milestones.build();
        if (recorded.getAuthentication().isPresent() || recorded.getStream().map(stream -> stream.outcome() != Stream.Outcome.OPENED).orElse(false)) {
            return;
        }
        if (recorded.getTls().map(tls -> tls.outcome() != Tls.Outcome.NEGOTIATED && tls.outcome() != Tls.Outcome.UNENCRYPTED).orElse(false)) {
            milestones.authentication(Authentication.timedOut(waited));
            return;
        }
        switch (stepUnderWay(recorded)) {
            case STREAM -> milestones.stream(Stream.timedOut(waited));
            case TLS -> milestones.tls(recorded.getStream().isEmpty() ? Tls.timedOut(waited) : Tls.notSetUpInTime(waited));
            case AUTHENTICATION -> milestones.authentication(Authentication.timedOut(waited));
        }
    }

    /**
     * @return whether every step recorded so far succeeded, so that a close cannot be this server ending the attempt
     *         after a failure. An unencrypted connection that this server's policy allows did not fail; one that its policy
     *         refused, as the remote requires the encryption the policy disables, did.
     */
    private static boolean nothingFailedSoFar(final AttemptMilestones recorded)
    {
        return recorded.getStream().map(stream -> stream.outcome() == Stream.Outcome.OPENED).orElse(true)
            && recorded.getTls().map(tls -> tls.outcome() == Tls.Outcome.NEGOTIATED || tls.outcome() == Tls.Outcome.UNENCRYPTED).orElse(true)
            && recorded.getCertificate().map(certificate -> certificate.outcome() == Certificate.Outcome.VALID).orElse(true);
    }

    public synchronized void tls(final Tls tls)
    {
        if (!gaveUp) {
            milestones.tls(tls);
        }
    }

    public synchronized void certificate(final Certificate certificate)
    {
        if (!gaveUp) {
            milestones.certificate(certificate);
        }
    }

    public synchronized void authentication(final Authentication authentication)
    {
        if (!gaveUp) {
            milestones.authentication(authentication);
        }
    }

    /**
     * Records the diagnostic log of the attempt, whose level-prefixed lines say which warnings and errors belong to
     * this attempt rather than to anything else the server was doing at the time. This is the attempt's last step: it
     * ends any {@link #awaitFinished(Duration)}.
     *
     * @param lines the diagnostic log lines of the attempt, each starting with its level.
     */
    public synchronized void diagnosticLog(final List<String> lines)
    {
        int warnings = 0;
        int errors = 0;
        for (final String line : lines) {
            if (line.startsWith(WARNING_PREFIX)) {
                warnings++;
            } else if (line.startsWith(ERROR_PREFIX)) {
                errors++;
            }
        }
        diagnosticLogLines = limitTotalLength(lines.stream().map(AttemptRecorder::limitLength).toList());
        diagnosticLogProblems = new LoggedProblems(warnings, errors);
        notifyAll();
    }

    /**
     * Keeps a log within {@link #MAX_DIAGNOSTIC_LOG_LENGTH}: whole lines from its start, up to
     * {@link #DIAGNOSTIC_LOG_HEAD_LENGTH}, and from its end, up to the rest of the limit, with one line in between that
     * says how many were left out.
     *
     * @param lines the lines, each already cut to {@link #MAX_DIAGNOSTIC_LOG_LINE_LENGTH}.
     * @return the lines, unchanged when they fit the limit.
     */
    private static List<String> limitTotalLength(final List<String> lines)
    {
        int total = 0;
        for (final String line : lines) {
            total += line.length() + 1;
        }
        if (total <= MAX_DIAGNOSTIC_LOG_LENGTH) {
            return lines;
        }
        int headEnd = 0;
        int headLength = 0;
        while (headLength + lines.get(headEnd).length() + 1 <= DIAGNOSTIC_LOG_HEAD_LENGTH) {
            headLength += lines.get(headEnd).length() + 1;
            headEnd++;
        }
        int tailStart = lines.size();
        int keptLength = headLength;
        while (tailStart > headEnd && keptLength + lines.get(tailStart - 1).length() + 1 <= MAX_DIAGNOSTIC_LOG_LENGTH) {
            keptLength += lines.get(tailStart - 1).length() + 1;
            tailStart--;
        }
        final List<String> kept = new ArrayList<>(lines.subList(0, headEnd));
        kept.add("INFO: (" + (tailStart - headEnd) + " lines left out)");
        kept.addAll(lines.subList(tailStart, lines.size()));
        return List.copyOf(kept);
    }

    /**
     * @return the attempt's diagnostic log lines, each cut to {@link #MAX_DIAGNOSTIC_LOG_LINE_LENGTH}, and together
     *         kept within {@link #MAX_DIAGNOSTIC_LOG_LENGTH} (see there); empty until the attempt has finished.
     */
    public synchronized Optional<List<String>> getDiagnosticLog()
    {
        return Optional.ofNullable(diagnosticLogLines);
    }

    /**
     * Counts a warning that the connection code logged for this attempt outside its diagnostic log. The code that sets
     * up the connection (TLS, the certificate, the stream) logs only to the server log, so without this its warnings
     * could not be told from those about anything else the server was doing at the time.
     */
    public synchronized void loggedWarning()
    {
        warningsLoggedElsewhere++;
    }

    /**
     * Counts an error that the connection code logged for this attempt outside its diagnostic log.
     *
     * @see #loggedWarning()
     */
    public synchronized void loggedError()
    {
        errorsLoggedElsewhere++;
    }

    /**
     * @return the warnings and errors logged for this attempt, in its diagnostic log and elsewhere; empty until the
     *         diagnostic log is recorded, which happens when the attempt ends.
     */
    public synchronized Optional<LoggedProblems> getLoggedProblems()
    {
        if (diagnosticLogProblems == null) {
            return Optional.empty();
        }
        return Optional.of(new LoggedProblems(diagnosticLogProblems.warnings() + warningsLoggedElsewhere, diagnosticLogProblems.errors() + errorsLoggedElsewhere));
    }

    /**
     * @return the milestones recorded so far; later milestones do not change it.
     */
    public synchronized AttemptMilestones snapshot()
    {
        return milestones.build();
    }

    /**
     * Describes the recorded milestones as diagnostic log lines, each with a level prefix like the other lines of an
     * attempt's diagnostic log, for the failed-session details page.
     *
     * @return one line per recorded milestone, in the order the steps happen; empty when nothing was recorded.
     */
    public List<String> describe()
    {
        final AttemptMilestones recorded = snapshot();
        final List<String> lines = new ArrayList<>();
        recorded.getDns().ifPresent(dns -> lines.add(line("DNS", dns.outcome(),
            join(", ", dns.targets(), target -> target.hostAndPort() + (target.directTls() ? " (direct TLS)" : "")))));
        recorded.getTcp().ifPresent(tcp -> lines.add(line("TCP", tcp.outcome(), describeEndpoint(tcp), describeFailures(tcp))));
        recorded.getStream().ifPresent(stream -> lines.add(line("Stream", stream.outcome(), stream.detail())));
        recorded.getTls().ifPresent(tls -> lines.add(line("TLS", tls.outcome(), tls.protocol(), tls.cipherSuite(), tls.reason())));
        recorded.getCertificate().ifPresent(certificate -> lines.add(line("Certificate", certificate.outcome(),
            join(", ", certificate.presentedNames(), name -> name),
            certificate.validityLimit() == null ? null : certificate.validityLimit().toString())));
        recorded.getAuthentication().ifPresent(authentication -> lines.add(line("Authentication", authentication.outcome(), authentication.reason())));
        return lines;
    }

    /**
     * @return the address connected to; when none connected, every address tried.
     */
    private static String describeEndpoint(final Tcp tcp)
    {
        return tcp.endpoint() != null ? tcp.endpoint() : join(", ", tcp.failures(), AddressFailure::endpoint);
    }

    /**
     * @return what happened at each address that failed, unless the outcome already says it for all of them.
     */
    private static String describeFailures(final Tcp tcp)
    {
        if (tcp.outcome() == Tcp.Outcome.REFUSED || tcp.outcome() == Tcp.Outcome.TIMED_OUT) {
            return null;
        }
        return join("; ", tcp.failures(), failure -> failure.endpoint() + ' ' + switch (failure.outcome()) {
            case REFUSED -> "refused";
            case TIMED_OUT -> "timed out";
            default -> failure.reason();
        });
    }

    /**
     * Joins the values of a capped list, in English like the rest of the diagnostic log, followed by how many were left
     * out.
     */
    private static <T> String join(final String separator, final CappedList<T> list, final Function<T, String> describe)
    {
        final List<String> values = new ArrayList<>(list.values().stream().map(describe).toList());
        if (list.leftOut() > 0) {
            values.add("and " + list.leftOut() + " more");
        }
        return String.join(separator, values);
    }

    private static String line(final String step, final Enum<?> outcome, final String... details)
    {
        final StringBuilder line = new StringBuilder("INFO: Connection step ").append(step).append(": ").append(outcome);
        for (final String detail : details) {
            if (detail != null && !detail.isEmpty()) {
                line.append(' ').append(detail);
            }
        }
        return limitLength(line.toString());
    }

    /**
     * Cuts a diagnostic log line to {@link #MAX_DIAGNOSTIC_LOG_LINE_LENGTH}, marking the cut.
     *
     * @param line the line.
     * @return the line, cut when it is longer than the limit.
     */
    private static String limitLength(final String line)
    {
        return line.length() > MAX_DIAGNOSTIC_LOG_LINE_LENGTH ? line.substring(0, MAX_DIAGNOSTIC_LOG_LINE_LENGTH) + CUT_MARKER : line;
    }
}
