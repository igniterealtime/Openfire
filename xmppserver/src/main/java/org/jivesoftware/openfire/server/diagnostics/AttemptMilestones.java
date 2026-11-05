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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What was recorded at each step of one outbound server-to-server connection attempt: an immutable snapshot.
 *
 * A step with no record did not take place in the attempt (or was not observed), and is reported as such; it is never
 * assumed to have succeeded.
 *
 * @author Matthew Vivian
 */
public final class AttemptMilestones
{
    /**
     * The number of values a milestone keeps from a list whose length the remote decides: DNS targets, addresses tried
     * and the names in a certificate. The latest attempts are kept for many domain pairs, so such a list must not grow
     * without limit; the rest are counted instead.
     */
    public static final int MAX_LISTED_VALUES = 10;

    /**
     * The length to which text the remote server chose (a stream error text, the name it gives itself, the reason a
     * TLS handshake failed) is cut when it is recorded. The latest attempts are kept for many domain pairs, so such
     * text must not grow without limit.
     */
    public static final int MAX_REMOTE_TEXT_LENGTH = 200;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private static final AttemptMilestones NONE = builder().build();

    /**
     * Makes text the remote server chose safe to record: control characters (which could, for example, start what looks
     * like a new line of the diagnostic log) become spaces, and the text is cut to {@link #MAX_REMOTE_TEXT_LENGTH},
     * marking the cut.
     *
     * @param text the text the remote chose.
     * @return the text without control characters, cut when it is longer than the limit.
     */
    public static String remoteText(final String text)
    {
        final String printable = CONTROL_CHARACTERS.matcher(text).replaceAll(" ");
        return printable.length() > MAX_REMOTE_TEXT_LENGTH ? printable.substring(0, MAX_REMOTE_TEXT_LENGTH) + AttemptRecorder.CUT_MARKER : printable;
    }

    private final Dns dns;
    private final Tcp tcp;
    private final Stream stream;
    private final Tls tls;
    private final Certificate certificate;
    private final Authentication authentication;
    private final boolean directTls;

    private AttemptMilestones(final Builder builder)
    {
        this.directTls = builder.directTls;
        this.dns = builder.dns;
        this.tcp = builder.tcp;
        this.stream = builder.stream;
        this.tls = builder.tls;
        this.certificate = builder.certificate;
        this.authentication = builder.authentication;
    }

    /**
     * An attempt for which nothing was recorded, for example because an existing session was reused.
     *
     * @return milestones with no step recorded.
     */
    public static AttemptMilestones none()
    {
        return NONE;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * @return whether the connection used direct TLS, in which the TLS handshake comes before the stream, rather than
     *         STARTTLS, in which it comes after.
     */
    public boolean isDirectTls()
    {
        return directTls;
    }

    public Optional<Dns> getDns()
    {
        return Optional.ofNullable(dns);
    }

    public Optional<Tcp> getTcp()
    {
        return Optional.ofNullable(tcp);
    }

    public Optional<Stream> getStream()
    {
        return Optional.ofNullable(stream);
    }

    public Optional<Tls> getTls()
    {
        return Optional.ofNullable(tls);
    }

    public Optional<Certificate> getCertificate()
    {
        return Optional.ofNullable(certificate);
    }

    public Optional<Authentication> getAuthentication()
    {
        return Optional.ofNullable(authentication);
    }

    /**
     * Collects milestones. A null value clears the step, so that it is reported as not recorded.
     */
    public static final class Builder
    {
        private Dns dns;
        private Tcp tcp;
        private Stream stream;
        private Tls tls;
        private Certificate certificate;
        private Authentication authentication;
        private boolean directTls;

        private Builder()
        {
        }

        public Builder directTls(final boolean directTls)
        {
            this.directTls = directTls;
            return this;
        }

        public Builder dns(final Dns dns)
        {
            this.dns = dns;
            return this;
        }

        public Builder tcp(final Tcp tcp)
        {
            this.tcp = tcp;
            return this;
        }

        public Builder stream(final Stream stream)
        {
            this.stream = stream;
            return this;
        }

        public Builder tls(final Tls tls)
        {
            this.tls = tls;
            return this;
        }

        public Builder certificate(final Certificate certificate)
        {
            this.certificate = certificate;
            return this;
        }

        public Builder authentication(final Authentication authentication)
        {
            this.authentication = authentication;
            return this;
        }

        public AttemptMilestones build()
        {
            return new AttemptMilestones(this);
        }
    }

    /**
     * The first {@link #MAX_LISTED_VALUES} values of a list whose length the remote decides, and how many values were
     * left out after them.
     *
     * The values are kept as data, and the count as a number, so that whatever shows them words the list in the
     * reader's language.
     *
     * @param values the values kept, in their order.
     * @param leftOut how many values followed them and were left out.
     * @param <T> the type of the values.
     */
    public record CappedList<T>(List<T> values, int leftOut)
    {
        public CappedList
        {
            values = List.copyOf(values);
            if (values.size() > MAX_LISTED_VALUES) {
                throw new IllegalArgumentException("A capped list keeps at most " + MAX_LISTED_VALUES + " values, not " + values.size());
            }
            if (leftOut < 0) {
                throw new IllegalArgumentException("The number of values left out cannot be negative: " + leftOut);
            }
        }

        /**
         * @param all the values, in the order they are reported.
         * @param <T> the type of the values.
         * @return the first values, with the number of the rest.
         */
        public static <T> CappedList<T> of(final List<? extends T> all)
        {
            if (all.size() <= MAX_LISTED_VALUES) {
                return new CappedList<>(List.copyOf(all), 0);
            }
            return new CappedList<>(List.copyOf(all.subList(0, MAX_LISTED_VALUES)), all.size() - MAX_LISTED_VALUES);
        }

        public boolean isEmpty()
        {
            return values.isEmpty();
        }
    }

    /**
     * What DNS returned for the domain.
     *
     * @param outcome whether SRV records were found, only the fallback was available, or nothing resolved.
     * @param targets the host and port pairs found, in the order they were tried, capped at {@link #MAX_LISTED_VALUES}
     *                as the remote decides how many there are.
     */
    public record Dns(Outcome outcome, CappedList<Target> targets)
    {
        public enum Outcome { SRV_RECORDS, FALLBACK_ONLY, OVERRIDE, NO_ADDRESS }

        /**
         * One host and port that DNS returned.
         *
         * @param hostAndPort the host name and port.
         * @param directTls whether the target is for direct TLS rather than STARTTLS.
         */
        public record Target(String hostAndPort, boolean directTls)
        {
            public Target
            {
                Objects.requireNonNull(hostAndPort, "hostAndPort");
            }

            public static Target plain(final String hostAndPort)
            {
                return new Target(hostAndPort, false);
            }

            public static Target directTls(final String hostAndPort)
            {
                return new Target(hostAndPort, true);
            }
        }

        public Dns
        {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(targets, "targets");
        }

        /**
         * @param targets every target from the SRV records, of which the first {@link #MAX_LISTED_VALUES} are kept.
         * @return the milestone.
         */
        public static Dns srvRecords(final List<Target> targets)
        {
            return new Dns(Outcome.SRV_RECORDS, CappedList.of(targets));
        }

        /**
         * No SRV records were published, so the domain name itself was used on the default port.
         *
         * @param targets the fallback host and port pairs, of which the first {@link #MAX_LISTED_VALUES} are kept.
         * @return the milestone.
         */
        public static Dns fallbackOnly(final List<Target> targets)
        {
            return new Dns(Outcome.FALLBACK_ONLY, CappedList.of(targets));
        }

        /**
         * The address came from a DNS override configured on this server, not from DNS.
         *
         * @param targets the overriding host and port, of which the first {@link #MAX_LISTED_VALUES} are kept.
         * @return the milestone.
         */
        public static Dns override(final List<Target> targets)
        {
            return new Dns(Outcome.OVERRIDE, CappedList.of(targets));
        }

        public static Dns noAddress()
        {
            return new Dns(Outcome.NO_ADDRESS, CappedList.of(List.of()));
        }
    }

    /**
     * The outcome of the TCP connection.
     *
     * @param outcome connected, or how the connection failed.
     * @param endpoint the address and port connected to; null when no connection was made.
     * @param failures what happened at each address that failed: every address tried, when no connection was made; the
     *                 addresses that failed before it, when one was made. Capped at {@link #MAX_LISTED_VALUES}, as the
     *                 remote decides how many addresses there are.
     */
    public record Tcp(Outcome outcome, String endpoint, CappedList<AddressFailure> failures)
    {
        public enum Outcome
        {
            /**
             * Connected, with no address failing before.
             */
            CONNECTED,

            /**
             * Connected, but only after the connection to one or more other addresses failed.
             */
            CONNECTED_AFTER_FAILURES,

            /**
             * Connected to the domain name itself, which is tried last, although the domain publishes SRV records: no
             * server that they name could be reached.
             */
            CONNECTED_TO_FALLBACK,

            REFUSED,
            TIMED_OUT,
            FAILED,

            /**
             * Addresses were found, but no connection to any of them was attempted, for example because each uses a
             * connection type (direct TLS or not) that this server does not accept for server-to-server connections.
             */
            NONE_ATTEMPTED
        }

        /**
         * What happened at one address that this server could not connect to.
         *
         * @param endpoint the address and port.
         * @param outcome refused, timed out, or failed otherwise.
         * @param reason the error text of another failure, as the operating system or the JVM gave it; otherwise null.
         */
        public record AddressFailure(String endpoint, Outcome outcome, String reason)
        {
            public AddressFailure
            {
                Objects.requireNonNull(endpoint, "endpoint");
                Objects.requireNonNull(outcome, "outcome");
                if (outcome != Outcome.REFUSED && outcome != Outcome.TIMED_OUT && outcome != Outcome.FAILED) {
                    throw new IllegalArgumentException("An address failure is refused, timed out or failed, not " + outcome);
                }
                if ((outcome == Outcome.FAILED) != (reason != null)) {
                    throw new IllegalArgumentException("A reason is required for another failure, and only for one.");
                }
            }

            public static AddressFailure refused(final String endpoint)
            {
                return new AddressFailure(endpoint, Outcome.REFUSED, null);
            }

            public static AddressFailure timedOut(final String endpoint)
            {
                return new AddressFailure(endpoint, Outcome.TIMED_OUT, null);
            }

            public static AddressFailure failed(final String endpoint, final String reason)
            {
                return new AddressFailure(endpoint, Outcome.FAILED, Objects.requireNonNull(reason, "reason"));
            }
        }

        public Tcp
        {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(failures, "failures");
            final boolean connected = outcome == Outcome.CONNECTED || outcome == Outcome.CONNECTED_AFTER_FAILURES || outcome == Outcome.CONNECTED_TO_FALLBACK;
            if (connected != (endpoint != null)) {
                throw new IllegalArgumentException("An endpoint is required for a connection, and only for one.");
            }
            final boolean failuresRequired = outcome == Outcome.CONNECTED_AFTER_FAILURES || outcome == Outcome.REFUSED || outcome == Outcome.TIMED_OUT || outcome == Outcome.FAILED;
            final boolean failuresAllowed = failuresRequired || outcome == Outcome.CONNECTED_TO_FALLBACK;
            if ((failuresRequired && failures.isEmpty()) || (!failuresAllowed && !failures.isEmpty())) {
                throw new IllegalArgumentException("Failures are required for a failed connection or a connection after failures, may come before a connection to the fallback, and are not allowed otherwise.");
            }
        }

        public static Tcp connected(final String endpoint)
        {
            return new Tcp(Outcome.CONNECTED, Objects.requireNonNull(endpoint, "endpoint"), CappedList.of(List.of()));
        }

        /**
         * @param endpoint the address and port connected to.
         * @param failures what happened at each address that failed before, of which the first
         *                 {@link #MAX_LISTED_VALUES} are kept.
         * @return the milestone.
         */
        public static Tcp connectedAfterFailures(final String endpoint, final List<AddressFailure> failures)
        {
            return new Tcp(Outcome.CONNECTED_AFTER_FAILURES, Objects.requireNonNull(endpoint, "endpoint"), CappedList.of(failures));
        }

        /**
         * @param endpoint the address and port of the domain name itself, connected to.
         * @param failures what happened at each address that failed before, of which the first
         *                 {@link #MAX_LISTED_VALUES} are kept; empty when none failed, because no server that the SRV
         *                 records name had an address that this server could try.
         * @return the milestone.
         */
        public static Tcp connectedToFallback(final String endpoint, final List<AddressFailure> failures)
        {
            return new Tcp(Outcome.CONNECTED_TO_FALLBACK, Objects.requireNonNull(endpoint, "endpoint"), CappedList.of(failures));
        }

        /**
         * Summarises the failures at every address tried, when none connected: refused or timed out when every address
         * failed that way, otherwise failed.
         *
         * @param failures what happened at each address, in the order they were tried, of which the first
         *                 {@link #MAX_LISTED_VALUES} are kept; the outcome covers them all.
         * @return the milestone.
         */
        public static Tcp allFailed(final List<AddressFailure> failures)
        {
            if (failures.isEmpty()) {
                throw new IllegalArgumentException("A failed connection needs at least one failed address.");
            }
            final Outcome first = failures.get(0).outcome();
            final boolean alike = first != Outcome.FAILED && failures.stream().allMatch(failure -> failure.outcome() == first);
            return new Tcp(alike ? first : Outcome.FAILED, null, CappedList.of(failures));
        }

        /**
         * @return the milestone, with no endpoint, as none was tried.
         */
        public static Tcp noneAttempted()
        {
            return new Tcp(Outcome.NONE_ATTEMPTED, null, CappedList.of(List.of()));
        }
    }

    /**
     * Whether the remote server opened an XMPP stream.
     *
     * @param outcome opened, or why no stream was opened.
     * @param detail the name the remote answered as when opened (null when it gave none), the stream error when
     *               refused, the number of seconds this server waited when it timed out; otherwise null.
     */
    public record Stream(Outcome outcome, String detail)
    {
        public enum Outcome
        {
            OPENED,

            /**
             * The remote closed the connection before it opened a stream.
             */
            CLOSED_BY_REMOTE,

            /**
             * The remote refused the stream with a stream error.
             */
            STREAM_ERROR,

            /**
             * The remote kept the connection open, but did not open a stream before this server stopped waiting.
             */
            TIMED_OUT,

            /**
             * The remote answered with a stream header without a stream ID, so this server closed the connection.
             */
            MISSING_ID
        }

        public Stream
        {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == Outcome.STREAM_ERROR || outcome == Outcome.TIMED_OUT) {
                Objects.requireNonNull(detail, "detail");
            } else if ((outcome == Outcome.CLOSED_BY_REMOTE || outcome == Outcome.MISSING_ID) && detail != null) {
                throw new IllegalArgumentException("This outcome has no detail: " + outcome);
            }
        }

        /**
         * @param remoteName the name the remote stated on its stream header ('from'); null when it stated none, which
         *                   RFC 6120 allows.
         * @return the milestone.
         */
        public static Stream opened(final String remoteName)
        {
            return new Stream(Outcome.OPENED, remoteName);
        }

        public static Stream closedByRemote()
        {
            return new Stream(Outcome.CLOSED_BY_REMOTE, null);
        }

        public static Stream streamError(final String errorText)
        {
            return new Stream(Outcome.STREAM_ERROR, errorText);
        }

        /**
         * @param waited how long this server waited for the stream.
         * @return the milestone.
         */
        public static Stream timedOut(final Duration waited)
        {
            if (waited.isNegative()) {
                throw new IllegalArgumentException("A wait cannot be negative: " + waited);
            }
            return new Stream(Outcome.TIMED_OUT, String.valueOf(waited.toSeconds()));
        }

        public static Stream missingId()
        {
            return new Stream(Outcome.MISSING_ID, null);
        }
    }

    /**
     * Whether TLS was set up.
     *
     * @param outcome negotiated, or why the connection is not encrypted.
     * @param protocol the negotiated protocol, when negotiated; otherwise null.
     * @param cipherSuite the negotiated cipher suite, when negotiated; otherwise null.
     * @param reason the handshake error text, when the handshake failed; the number of seconds waited, when it timed
     *               out; otherwise null.
     */
    public record Tls(Outcome outcome, String protocol, String cipherSuite, String reason)
    {
        public enum Outcome
        {
            NEGOTIATED,

            /**
             * The remote did not offer STARTTLS, and this server requires encryption.
             */
            NOT_OFFERED,

            /**
             * The remote offered STARTTLS, but refused it when asked.
             */
            REFUSED,

            /**
             * The handshake failed for a reason other than this server rejecting the remote certificate.
             */
            HANDSHAKE_FAILED,

            /**
             * Over direct TLS, the remote answered with something other than TLS, for example because the port expects
             * an unencrypted connection.
             */
            NOT_TLS,

            /**
             * This server stopped the handshake because it rejected the remote certificate; the certificate milestone
             * says why.
             */
            CERTIFICATE_REJECTED,

            /**
             * The connection went ahead without encryption, as this server's STARTTLS policy allows: TLS is optional and
             * the remote did not offer it, or this server's policy disables TLS and the remote does not require it.
             */
            UNENCRYPTED,

            /**
             * The remote requires TLS, but this server's STARTTLS policy disables it, so this server closed the connection.
             */
            REQUIRED_BUT_DISABLED,

            /**
             * Over direct TLS, the remote did not complete the handshake before this server stopped waiting.
             */
            TIMED_OUT,

            /**
             * Over STARTTLS, the stream opened, but TLS was not set up before this server stopped waiting.
             */
            NOT_SET_UP_IN_TIME,

            /**
             * Over STARTTLS, the remote closed the connection after the stream opened and before TLS was set up.
             */
            CLOSED_BEFORE_SET_UP
        }

        public Tls
        {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == Outcome.NEGOTIATED) {
                Objects.requireNonNull(protocol, "protocol");
                Objects.requireNonNull(cipherSuite, "cipherSuite");
            }
            if (outcome == Outcome.HANDSHAKE_FAILED || outcome == Outcome.TIMED_OUT || outcome == Outcome.NOT_SET_UP_IN_TIME) {
                Objects.requireNonNull(reason, "reason");
            }
        }

        public static Tls negotiated(final String protocol, final String cipherSuite)
        {
            return new Tls(Outcome.NEGOTIATED, protocol, cipherSuite, null);
        }

        public static Tls notOffered()
        {
            return new Tls(Outcome.NOT_OFFERED, null, null, null);
        }

        public static Tls refused()
        {
            return new Tls(Outcome.REFUSED, null, null, null);
        }

        /**
         * @param reason why the handshake failed, which can hold text the remote chose; it is recorded as remote text.
         * @return the milestone.
         */
        public static Tls handshakeFailed(final String reason)
        {
            return new Tls(Outcome.HANDSHAKE_FAILED, null, null, remoteText(reason));
        }

        public static Tls notTls()
        {
            return new Tls(Outcome.NOT_TLS, null, null, null);
        }

        /**
         * @param waited how long this server waited for the handshake.
         * @return the milestone, with the number of seconds waited as its reason.
         */
        public static Tls timedOut(final Duration waited)
        {
            if (waited.isNegative()) {
                throw new IllegalArgumentException("A wait cannot be negative: " + waited);
            }
            return new Tls(Outcome.TIMED_OUT, null, null, String.valueOf(waited.toSeconds()));
        }

        /**
         * @param waited how long this server waited for the session.
         * @return the milestone, with the number of seconds waited as its reason.
         */
        public static Tls notSetUpInTime(final Duration waited)
        {
            if (waited.isNegative()) {
                throw new IllegalArgumentException("A wait cannot be negative: " + waited);
            }
            return new Tls(Outcome.NOT_SET_UP_IN_TIME, null, null, String.valueOf(waited.toSeconds()));
        }

        public static Tls closedBeforeSetUp()
        {
            return new Tls(Outcome.CLOSED_BEFORE_SET_UP, null, null, null);
        }

        public static Tls certificateRejected()
        {
            return new Tls(Outcome.CERTIFICATE_REJECTED, null, null, null);
        }

        /**
         * The connection went ahead without encryption, as this server's STARTTLS policy allows.
         *
         * @return the milestone.
         */
        public static Tls unencrypted()
        {
            return new Tls(Outcome.UNENCRYPTED, null, null, null);
        }

        /**
         * The remote requires TLS, which this server's STARTTLS policy disables, so this server closed the connection.
         *
         * @return the milestone.
         */
        public static Tls requiredButDisabled()
        {
            return new Tls(Outcome.REQUIRED_BUT_DISABLED, null, null, null);
        }
    }

    /**
     * The verdict on the remote server's certificate.
     *
     * @param outcome valid, or which property failed.
     * @param presentedNames the names in the certificate, for a name mismatch, capped at {@link #MAX_LISTED_VALUES}.
     * @param validityLimit the end of the validity period of an expired certificate, or the start of the validity period
     *                      of a certificate that is not valid yet; otherwise null.
     */
    public record Certificate(Outcome outcome, CappedList<String> presentedNames, Instant validityLimit)
    {
        public enum Outcome { VALID, NOT_RECEIVED, EXPIRED, NOT_YET_VALID, UNTRUSTED, REVOKED, NAME_MISMATCH }

        public Certificate
        {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(presentedNames, "presentedNames");
            if (outcome == Outcome.EXPIRED || outcome == Outcome.NOT_YET_VALID) {
                Objects.requireNonNull(validityLimit, "validityLimit");
            } else if (validityLimit != null) {
                throw new IllegalArgumentException("A validity limit applies only to an expired or not yet valid certificate.");
            }
        }

        private Certificate(final Outcome outcome)
        {
            this(outcome, CappedList.of(List.of()), null);
        }

        public static Certificate valid()
        {
            return new Certificate(Outcome.VALID);
        }

        public static Certificate notReceived()
        {
            return new Certificate(Outcome.NOT_RECEIVED);
        }

        /**
         * @param notAfter the end of the certificate's validity period.
         * @return the milestone.
         */
        public static Certificate expired(final Instant notAfter)
        {
            return new Certificate(Outcome.EXPIRED, CappedList.of(List.of()), notAfter);
        }

        /**
         * @param notBefore the start of the certificate's validity period.
         * @return the milestone.
         */
        public static Certificate notYetValid(final Instant notBefore)
        {
            return new Certificate(Outcome.NOT_YET_VALID, CappedList.of(List.of()), notBefore);
        }

        public static Certificate untrusted()
        {
            return new Certificate(Outcome.UNTRUSTED);
        }

        public static Certificate revoked()
        {
            return new Certificate(Outcome.REVOKED);
        }

        /**
         * @param presentedNames the names in the certificate, of which the first {@link #MAX_LISTED_VALUES} are kept,
         *                       with the number of the rest: the remote decides how many names its certificate holds.
         * @return the milestone.
         */
        public static Certificate nameMismatch(final List<String> presentedNames)
        {
            return new Certificate(Outcome.NAME_MISMATCH, CappedList.of(presentedNames), null);
        }
    }

    /**
     * How the remote server was authenticated, or why it was not.
     *
     * @param outcome the method used, or the kind of failure.
     * @param reason the error the remote sent, for a stream error; the number of seconds waited, for a timeout; otherwise
     *               null.
     */
    public record Authentication(Outcome outcome, String reason)
    {
        public enum Outcome
        {
            /**
             * Authenticated with the server certificate.
             */
            SASL_EXTERNAL,

            /**
             * Authenticated with Server Dialback; why certificate login was not used was not recorded.
             */
            DIALBACK,

            /**
             * Authenticated with Server Dialback, because the remote did not offer SASL EXTERNAL.
             */
            DIALBACK_AFTER_EXTERNAL_NOT_OFFERED,

            /**
             * Authenticated with Server Dialback, because the remote refused SASL EXTERNAL.
             */
            DIALBACK_AFTER_EXTERNAL_REFUSED,

            /**
             * The remote refused SASL EXTERNAL and Server Dialback is disabled on this server.
             */
            EXTERNAL_REFUSED_DIALBACK_DISABLED,

            /**
             * The remote refused Server Dialback.
             */
            DIALBACK_REFUSED,

            /**
             * The remote offered no authentication method that this server can use.
             */
            NO_COMMON_MECHANISM,

            /**
             * The remote ended the stream with a stream error while authentication was under way.
             */
            STREAM_ERROR,

            /**
             * After the TLS handshake failed, Server Dialback over a new, unencrypted connection failed too.
             */
            DIALBACK_FALLBACK_FAILED,

            /**
             * Authentication did not complete before this server stopped waiting.
             */
            TIMED_OUT,

            /**
             * The remote closed the connection while authentication was under way.
             */
            CLOSED
        }

        public Authentication
        {
            Objects.requireNonNull(outcome, "outcome");
            if ((outcome == Outcome.STREAM_ERROR || outcome == Outcome.TIMED_OUT) != (reason != null)) {
                throw new IllegalArgumentException("A reason is required for a stream error or a timeout, and only for those.");
            }
        }

        /**
         * @param waited how long this server waited for the session.
         * @return the milestone, with the number of seconds waited as its reason.
         */
        public static Authentication timedOut(final Duration waited)
        {
            if (waited.isNegative()) {
                throw new IllegalArgumentException("A wait cannot be negative: " + waited);
            }
            return new Authentication(Outcome.TIMED_OUT, String.valueOf(waited.toSeconds()));
        }

        public static Authentication closed()
        {
            return new Authentication(Outcome.CLOSED, null);
        }

        public static Authentication saslExternal()
        {
            return new Authentication(Outcome.SASL_EXTERNAL, null);
        }

        public static Authentication dialback()
        {
            return new Authentication(Outcome.DIALBACK, null);
        }

        public static Authentication dialbackAfterExternalNotOffered()
        {
            return new Authentication(Outcome.DIALBACK_AFTER_EXTERNAL_NOT_OFFERED, null);
        }

        public static Authentication dialbackAfterExternalRefused()
        {
            return new Authentication(Outcome.DIALBACK_AFTER_EXTERNAL_REFUSED, null);
        }

        public static Authentication externalRefusedDialbackDisabled()
        {
            return new Authentication(Outcome.EXTERNAL_REFUSED_DIALBACK_DISABLED, null);
        }

        public static Authentication dialbackRefused()
        {
            return new Authentication(Outcome.DIALBACK_REFUSED, null);
        }

        public static Authentication noCommonMechanism()
        {
            return new Authentication(Outcome.NO_COMMON_MECHANISM, null);
        }

        /**
         * @param errorText the stream error condition, with its text when the remote sent one.
         * @return the milestone.
         */
        public static Authentication streamError(final String errorText)
        {
            return new Authentication(Outcome.STREAM_ERROR, Objects.requireNonNull(errorText, "errorText"));
        }

        public static Authentication dialbackFallbackFailed()
        {
            return new Authentication(Outcome.DIALBACK_FALLBACK_FAILED, null);
        }
    }
}
