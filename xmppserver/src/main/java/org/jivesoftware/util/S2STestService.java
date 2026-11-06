/*
 * Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.jivesoftware.openfire.server.s2stest.LogSummary;
import org.jivesoftware.openfire.server.s2stest.PingOutcome;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.session.RemoteOutgoingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.cert.SANCertificateIdentityMapping;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import java.io.StringWriter;
import java.io.Writer;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs server to server test.
 *
 * Attempts to send an IQ packet to ping a given domain. Captures debug information from logging, certificates and
 * packets, and the facts the S2S connection test checklist is built from: whether an authenticated session was
 * registered, what its connection attempt recorded, and what came back from the ping.
 */
public class S2STestService {

    private static final org.slf4j.Logger Log = LoggerFactory.getLogger(S2STestService.class);

    /**
     * The layout of captured log lines. The level is included so that warnings and errors can be told apart from
     * informational lines.
     */
    private static final String CAPTURED_LOG_PATTERN = "%d{HH:mm:ss.SSS} %-5level %m%n";

    /**
     * How much longer the test waits, after the wait for the ping reply, for a connection attempt that is still in
     * progress. The two waits use the same timeout, so an attempt that fails by timing out ends at about the moment the
     * test stops waiting; without this, its last step would read as not observed.
     */
    static final Duration ATTEMPT_GRACE = Duration.ofSeconds(10);

    /**
     * How much longer the test waits for the ping reply when the session was set up only after the wait for the reply
     * ran out, for example because an address that did not answer used up most of it. The ping is sent when the session
     * is set up, so its reply has had little or no time to arrive; without this, the ping check would fail with a hint
     * that the remote cannot connect back.
     */
    static final Duration LATE_REPLY_WAIT = Duration.ofSeconds(5);

    private static final String PEM_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_END = "-----END CERTIFICATE-----";
    private static final int PEM_LINE_LENGTH = 64;

    private final JID domain;

    /**
     * The outcome of a test.
     *
     * @param ping what came back after the ping, or null when this node cannot tell (the route is held by another
     *             cluster node).
     * @param sessionRegistered whether an authenticated outgoing session to the domain is registered after the test.
     * @param milestones what the connection attempt made during the test recorded, or null when no attempt was made
     *                   during the test (for example because a session was reused on another cluster node).
     * @param log the warnings and errors logged for that attempt, and others logged during the test; null when the
     *            attempt made during the test did not finish, or none was made.
     * @param stanzas the stanzas exchanged with the domain, as XML.
     * @param certificates the certificates the remote presented, as text with PEM blocks.
     * @param logs the log lines captured during the test.
     */
    public record Result(PingOutcome ping, boolean sessionRegistered, AttemptMilestones milestones, LogSummary log, String stanzas, String certificates, String logs) {}

    /**
     * @param domain The host to test.
     */
    public S2STestService(JID domain) {
        this.domain = domain.asBareJID();
    }

    /**
     * Run a test against the domain.
     *
     * Tests of the same domain run one at a time on this node. Each test reports the latest connection attempt that
     * started after it did, so two tests of one domain at the same time would each report the other's attempt.
     *
     * @return the outcome of the test.
     * @throws Exception On error.
     */
    public Result run() throws Exception {
        try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(S2STestService.class, domain.getDomain()).lockInterruptibly()) {
            return runAlone();
        }
    }

    private Result runAlone() throws Exception {
        final DomainPair pair = new DomainPair(XMPPServer.getInstance().getServerInfo().getXMPPDomain(), domain.getDomain());

        // Taken before the existing routes are torn down: any attempt after that is a fresh one, and belongs to this test.
        final Instant testStart = Instant.now();

        // Tear down existing routes.
        final SessionManager sessionManager = SessionManager.getInstance();
        for (final Session incomingServerSession : sessionManager.getIncomingServerSessions( domain.getDomain() ) )
        {
            incomingServerSession.close();
        }

        final Session outgoingServerSession = sessionManager.getOutgoingServerSession( pair );
        if ( outgoingServerSession != null )
        {
            closeAndAwaitTeardown( outgoingServerSession, OutgoingSessionPromise.CLOSE_WAIT_TIMEOUT.getValue() );
        }

        final IQ pingRequest = new IQ( Type.get );
        pingRequest.setChildElement( "ping", IQPingHandler.NAMESPACE );
        pingRequest.setFrom( pair.getLocal() );
        pingRequest.setTo( domain.getDomain() );

        // Intercept logging and packets. The finally block removes the appender and the interceptor, also when adding
        // either of them fails.
        final Writer logs = new StringWriter();
        final AtomicInteger warningsAndErrorsLogged = new AtomicInteger();
        final PingInterceptor interceptor = new PingInterceptor( pingRequest );
        final String appenderName = newAppenderName();
        try
        {
            addAppender( appenderName, logs, warningsAndErrorsLogged );
            InterceptorManager.getInstance().addInterceptor(interceptor);

            // Send ping, and wait for its reply or exceed socket timeout.
            Log.info( "Sending server to server ping request to " + domain.getDomain() );
            final Duration replyTimeout = Duration.ofMillis( RemoteServerManager.getSocketTimeout() );
            final PingReplyListener replyListener = sendPing( XMPPServer.getInstance().getIQRouter(), pingRequest, replyListeningTime( replyTimeout ) );
            IQ reply = replyListener.awaitReply( replyTimeout );

            // Let an attempt that is still in progress finish, so that its outcome is reported rather than missed. The
            // attempt is read once, so that the one waited for is the one reported.
            final Optional<AttemptRecorder> attemptOfThisTest = OutgoingSessionPromise.getInstance().getLatestAttempt( pair )
                .filter( attempt -> !attempt.getStarted().isBefore( testStart ) );
            if ( attemptOfThisTest.isPresent() ) {
                final boolean finishedDuringReplyWait = attemptOfThisTest.get().awaitFinished( Duration.ZERO );
                attemptOfThisTest.get().awaitFinished( ATTEMPT_GRACE );
                if ( reply == null ) {
                    // Also takes a reply that arrived while the attempt was being waited for.
                    reply = replyListener.awaitReply( lateReplyWait( finishedDuringReplyWait, attemptOfThisTest.get().getOutcome() ) );
                }
            }

            // Check on the connection status.
            final OutgoingServerSession session = sessionManager.getOutgoingServerSession( pair );
            if (session != null) {
                Log.info("Session is {}.", session.getStatus());
            } else {
                Log.info("Failed to establish server to server session.");
            }

            // Build the log output: start with the freeform Log4j-captured log, then append the structured
            // per-step diagnostic log of this test's own attempt, when it has finished.
            final StringBuilder combinedLogs = new StringBuilder( logs.toString() );
            attemptOfThisTest
                .flatMap( AttemptRecorder::getDiagnosticLog )
                .filter( l -> !l.isEmpty() )
                .ifPresent( l -> {
                    combinedLogs.append( "\n\n--- Authentication attempt diagnostic log ---\n" );
                    l.forEach( line -> combinedLogs.append( line ).append( '\n' ) );
                } );

            return new Result(
                pingOutcome( reply, interceptor.isPingSent(), session ).orElse( null ),
                session != null && session.isAuthenticated(),
                milestonesOfThisTest( attemptOfThisTest, testStart ).orElse( null ),
                logOfThisTest( attemptOfThisTest, testStart, warningsAndErrorsLogged.get() ).orElse( null ),
                interceptor.getStanzas(),
                session == null ? "" : formatCertificates( session.getPeerCertificates() ),
                combinedLogs.toString() );
        }
        finally
        {
            // Cleanup
            InterceptorManager.getInstance().removeInterceptor( interceptor );
            removeAppender( appenderName );
        }
    }

    /**
     * Closes a session, and waits for the teardown of its connection to finish. Closing marks the connection closed at
     * once, but its route is removed later, when its close listeners run; until then the router can still hand a
     * stanza to the closing session, which only schedules it for redelivery. The test waits, so that its ping is not
     * handed to the session it has just closed.
     *
     * A session held by another cluster node has no connection on this node, so there is nothing to wait for. When the
     * teardown does not finish within the bound, the test carries on.
     *
     * @param session the session to close.
     * @param bound how long to wait for the teardown to finish.
     * @throws InterruptedException when the thread is interrupted while waiting.
     */
    static void closeAndAwaitTeardown(@Nonnull final Session session, @Nonnull final Duration bound) throws InterruptedException {
        session.close();
        if ( !(session instanceof LocalSession localSession) || localSession.getConnection() == null ) {
            return;
        }
        try {
            localSession.getConnection().getCloseFuture().toCompletableFuture().get( bound.toMillis(), TimeUnit.MILLISECONDS );
        } catch ( final TimeoutException e ) {
            Log.info( "The closed session to {} did not finish its teardown within {}; the test carries on.", session.getAddress(), bound );
        } catch ( final ExecutionException e ) {
            Log.debug( "The teardown of the closed session to {} failed; the test carries on.", session.getAddress(), e );
        }
    }

    /**
     * The milestones of the connection attempt made during this test. An attempt that started before the test is
     * someone else's (or an earlier test's), so its milestones must not be reported as this test's.
     *
     * @param latestAttempt the latest attempt for the tested domain pair, if any.
     * @param testStart when this test started.
     * @return the attempt's milestones so far, if it started during this test.
     */
    static Optional<AttemptMilestones> milestonesOfThisTest(final Optional<AttemptRecorder> latestAttempt, final Instant testStart) {
        return latestAttempt
            .filter( attempt -> !attempt.getStarted().isBefore( testStart ) )
            .map( AttemptRecorder::snapshot );
    }

    /**
     * What came back after the ping, when this node can tell. When the route to the remote is held by another cluster
     * node, that node sends the ping, so this node cannot see whether the ping was sent: an error may be that node's
     * own, and a missing reply may mean that the ping never left. Only a result, which the remote alone sends, is
     * judged then.
     *
     * @param reply the reply to the ping, from whichever cluster node it arrived on, or null when none did.
     * @param pingSent whether this node saw the ping sent over an outgoing server session.
     * @param session the outgoing session to the remote after the test, or null when there is none.
     * @return the outcome, or empty when it cannot be known on this node.
     */
    static Optional<PingOutcome> pingOutcome(final IQ reply, final boolean pingSent, final OutgoingServerSession session) {
        final PingOutcome outcome = PingOutcome.fromReply( reply, pingSent );
        if ( session instanceof RemoteOutgoingServerSession && outcome.kind() != PingOutcome.Kind.RESULT ) {
            return Optional.empty();
        }
        return Optional.of( outcome );
    }

    /**
     * The warnings and errors logged during this test. Those in the diagnostic log of the attempt made during the test
     * decide the log check; the rest of those logged by the server meanwhile may be unrelated, and are reported as
     * other. Lines of the diagnostic log are also written to the server log, so they are taken out of that count.
     *
     * @param latestAttempt the latest attempt for the tested domain pair, if any.
     * @param testStart when this test started.
     * @param warningsAndErrorsLogged the number of warnings and errors the server logged during the test.
     * @return the counts, if an attempt started during this test and has finished.
     */
    static Optional<LogSummary> logOfThisTest(final Optional<AttemptRecorder> latestAttempt, final Instant testStart, final int warningsAndErrorsLogged) {
        return latestAttempt
            .filter( attempt -> !attempt.getStarted().isBefore( testStart ) )
            .flatMap( AttemptRecorder::getLoggedProblems )
            .map( problems -> new LogSummary( problems.warnings(), problems.errors(),
                Math.max( 0, warningsAndErrorsLogged - problems.warnings() - problems.errors() ) ) );
    }

    /**
     * @return a name for a new capturing appender, unique to this test.
     */
    static String newAppenderName() {
        return "openfire-s2s-test-appender-" + StringUtils.randomString( 10 );
    }

    /**
     * Captures everything the server logs into the writer under the given name, and counts the warnings and errors
     * among it. The caller chooses the name, so that it can remove the appender even when adding it fails part way.
     *
     * @param name the name of the appender, for {@link #removeAppender(String)}.
     * @param writer receives the captured log lines.
     * @param warningsAndErrors counts the captured warnings and errors.
     */
    void addAppender(final String name, final Writer writer, final AtomicInteger warningsAndErrors) {
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        final PatternLayout layout = PatternLayout.newBuilder().withConfiguration(config).withPattern(CAPTURED_LOG_PATTERN).build();
        final Filter counter = new AbstractFilter() {
            @Override
            public Filter.Result filter(final LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                    warningsAndErrors.incrementAndGet();
                }
                return Filter.Result.NEUTRAL;
            }
        };
        final Appender appender = WriterAppender.createAppender(layout, counter, writer, name, false, true);
        appender.start();
        config.addAppender(appender);

        // An additive logger passes its events on to the root logger, so adding the appender to it as well would
        // capture, and count, each of its events twice.
        final Level level = null;
        final Filter filter = null;
        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            if (!loggerConfig.isAdditive()) {
                loggerConfig.addAppender(appender, level, filter);
            }
        }
        config.getRootLogger().addAppender(appender, level, filter);
    }

    void removeAppender(final String name) {
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        // The configuration can have been reloaded during the test, which drops the appender from it.
        final Appender appender = config.getAppenders().remove( name );
        if ( appender != null ) {
            appender.stop();
        }

        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.removeAppender( name );
        }
        config.getRootLogger().removeAppender( name );
    }

    /**
     * Describes each certificate (subject, alternative names, validity) followed by the certificate itself in PEM
     * format, so that it can be copied into other tools.
     *
     * @param certificates the certificate chain presented by the remote server; may be null.
     * @return the description, never null.
     * @throws CertificateEncodingException when a certificate cannot be encoded.
     */
    static String formatCertificates(final Certificate[] certificates) throws CertificateEncodingException {
        final StringBuilder certs = new StringBuilder();
        if (certificates == null) {
            return "";
        }
        for (final Certificate certificate : certificates) {
            final X509Certificate x509cert = (X509Certificate) certificate;
            certs.append("--\nSubject: ");
            certs.append(x509cert.getSubjectX500Principal());

            final List<String> subjectAltNames = new SANCertificateIdentityMapping().mapIdentity(x509cert);
            if (!subjectAltNames.isEmpty()) {
                certs.append("\nSubject Alternative Names: ");
                for (final String subjectAltName : subjectAltNames) {
                    certs.append("\n  ");
                    certs.append(subjectAltName);
                }
            }

            certs.append("\nNot Before: ");
            certs.append(x509cert.getNotBefore());
            certs.append("\nNot After: ");
            certs.append(x509cert.getNotAfter());
            certs.append("\n\n").append(PEM_BEGIN).append('\n');
            certs.append(Base64.getMimeEncoder(PEM_LINE_LENGTH, new byte[] {'\n'}).encodeToString(certificate.getEncoded()));
            certs.append('\n').append(PEM_END).append("\n\n");
        }
        return certs.toString();
    }

    /**
     * How long the IQ router keeps listening for the reply: as long as the test can wait for it, which is the reply wait,
     * then the wait for an attempt still in progress, then the wait for a late reply. A reply that arrives after the
     * router stops listening is dropped.
     *
     * @param replyTimeout the first wait for the reply.
     * @return how long the router listens.
     */
    static Duration replyListeningTime(@Nonnull final Duration replyTimeout) {
        return replyTimeout.plus( ATTEMPT_GRACE ).plus( LATE_REPLY_WAIT );
    }

    /**
     * How much longer to wait for the ping reply, once the wait for the reply has run out and the attempt has been
     * waited for. Only a session set up after the reply wait ran out has a reply still on its way: the ping is sent when
     * the session is set up.
     *
     * @param finishedDuringReplyWait whether the attempt had finished when the wait for the reply ran out.
     * @param outcome how the attempt ended, or that it is still in progress.
     * @return {@link #LATE_REPLY_WAIT} for a new session set up after the reply wait ran out; otherwise zero.
     */
    static Duration lateReplyWait(final boolean finishedDuringReplyWait, @Nonnull final AttemptRecorder.Outcome outcome) {
        return !finishedDuringReplyWait && outcome == AttemptRecorder.Outcome.NEW_SESSION ? LATE_REPLY_WAIT : Duration.ZERO;
    }

    /**
     * Sends the ping, having first registered for its reply with the IQ router. The router passes a reply to the
     * listener whichever cluster node it arrives on: a node that receives a reply it has no listener for forwards it
     * to the node that registered one. This server's own error, when it cannot deliver the ping, reaches the listener
     * in the same way.
     *
     * @param router the IQ router.
     * @param ping the ping.
     * @param timeout how long the router keeps listening for the reply.
     * @return the listener, which holds the reply once it arrives.
     */
    static PingReplyListener sendPing(@Nonnull final IQRouter router, @Nonnull final IQ ping, @Nonnull final Duration timeout) {
        final PingReplyListener listener = new PingReplyListener();
        router.addIQResultListener( ping.getID(), listener, timeout.toMillis() );
        router.route( ping );
        return listener;
    }

    /**
     * Receives the reply to the ping from the IQ router, and releases the test's wait when it arrives or when the
     * router stops listening for it.
     */
    static class PingReplyListener implements IQResultListener {
        private final CountDownLatch done = new CountDownLatch( 1 );

        private volatile IQ reply;

        @Override
        public void receivedAnswer( final IQ packet )
        {
            Log.info( "{} server to server response received.", packet.getType() == Type.result ? "Successful" : "Erroneous" );
            reply = packet;
            done.countDown();
        }

        @Override
        public void answerTimeout( final String packetId )
        {
            done.countDown();
        }

        /**
         * Waits for the reply to the ping.
         *
         * @param timeout how long to wait at most.
         * @return the reply, or null when none arrived.
         * @throws InterruptedException when the thread is interrupted while waiting.
         */
        IQ awaitReply( final Duration timeout ) throws InterruptedException
        {
            done.await( timeout.toMillis(), TimeUnit.MILLISECONDS );
            return reply;
        }
    }

    /**
     * Packet interceptor for the duration of our S2S test. Logs the traffic with the tested domain, and records whether
     * the ping was sent to the remote server.
     */
    static class PingInterceptor implements PacketInterceptor {
        private final StringBuilder xml = new StringBuilder();

        private final IQ ping;

        private volatile boolean pingSent;

        /**
         * @param ping The IQ ping request that was used to initiate the test.
         */
        PingInterceptor( IQ ping )
        {
            this.ping = ping;
        }

        @Override
        public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) {

            if (ping.getTo() == null || packet.getFrom() == null || packet.getTo() == null) {
                return;
            }

            // The ping reached the remote server only if it was delivered over an outgoing server session. An error
            // this server generates when it cannot connect is routed like a reply from the remote, so this is what
            // tells the two apart.
            // A session that is closed does not send the ping, but schedules it for redelivery once the session's
            // teardown is complete; it is sent then over whichever session the router finds.
            if (!incoming && processed && session instanceof OutgoingServerSession && !session.isClosed() && isPing(packet)) {
                pingSent = true;
            }

            if (!processed
                    && (ping.getTo().getDomain().equals(packet.getFrom().getDomain()) || ping.getTo().getDomain().equals(packet.getTo().getDomain()))) {

                // Log all traffic to and from the domain.
                synchronized (xml) {
                    xml.append(packet.toXML());
                    xml.append('\n');
                }
            }
        }

        private boolean isPing(final Packet packet) {
            return packet instanceof IQ iq && iq.isRequest() && ping.getID().equals(iq.getID()) && ping.getTo().equals(iq.getTo());
        }

        /**
         * @return whether the ping was delivered over an outgoing server session to the remote.
         */
        boolean isPingSent() {
            return pingSent;
        }

        /**
         * @return the stanzas exchanged with the tested domain, one per line.
         */
        String getStanzas() {
            synchronized (xml) {
                return xml.toString();
            }
        }
    }
}
