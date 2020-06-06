package org.jivesoftware.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.cert.SANCertificateIdentityMapping;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.Packet;

import javax.xml.bind.DatatypeConverter;
import java.io.StringWriter;
import java.io.Writer;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs server to server test.
 *
 * Attempts to send an IQ packet to ping a given domain. Captures debug information from logging, certificates and
 * packets.
 */
public class S2STestService {

    private static final org.slf4j.Logger Log = LoggerFactory.getLogger(S2STestService.class);

    private Semaphore waitUntil;

    private String domain;

    /**
     * @param domain The host to test.
     */
    public S2STestService(String domain) {
        this.domain = domain;
    }

    /**
     * Run a test against the domain.
     * @return K-V pairs of debug information.
     * @throws Exception On error.
     */
    public Map<String, String> run() throws Exception {
        waitUntil = new Semaphore(0);
        Map<String, String> results = new HashMap<>();
        final DomainPair pair = new DomainPair(XMPPServer.getInstance().getServerInfo().getXMPPDomain(), domain);

        // Tear down existing routes.
        final SessionManager sessionManager = SessionManager.getInstance();
        for (final Session incomingServerSession : sessionManager.getIncomingServerSessions( domain ) )
        {
            incomingServerSession.close();
        }

        final Session outgoingServerSession = sessionManager.getOutgoingServerSession( pair );
        if ( outgoingServerSession != null )
        {
            outgoingServerSession.close();
        }

        final IQ pingRequest = new IQ( Type.get );
        pingRequest.setChildElement( "ping", IQPingHandler.NAMESPACE );
        pingRequest.setFrom( pair.getLocal() );
        pingRequest.setTo( domain );

        // Intercept logging.
        final Writer logs = new StringWriter();
        final String appenderName = addAppender( logs );

        // Intercept packets.
        final PacketInterceptor interceptor = new S2SInterceptor( pingRequest );
        InterceptorManager.getInstance().addInterceptor(interceptor);

        // Send ping.
        try
        {
            Log.info( "Sending server to server ping request to " + domain );
            XMPPServer.getInstance().getIQRouter().route( pingRequest );

            // Wait for success or exceed socket timeout.
            waitUntil.tryAcquire( RemoteServerManager.getSocketTimeout(), TimeUnit.MILLISECONDS );

            // Check on the connection status.
            logSessionStatus();

            // Prepare response.
            results.put( "certs", getCertificates() );
            results.put( "stanzas", interceptor.toString() );
            results.put( "logs", logs.toString() );

            return results;
        }
        finally
        {
            // Cleanup
            InterceptorManager.getInstance().removeInterceptor( interceptor );
            removeAppender( appenderName );
        }
    }

    String addAppender(final Writer writer) {
        final String name = "openfire-s2s-test-appender-" + StringUtils.randomString( 10 );
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        final PatternLayout layout = PatternLayout.createDefaultLayout(config);
        final Appender appender = WriterAppender.createAppender(layout, null, writer, name, false, true);
        appender.start();
        config.addAppender(appender);

        final Level level = null;
        final Filter filter = null;
        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.addAppender(appender, level, filter);
        }
        config.getRootLogger().addAppender(appender, level, filter);
        return name;
    }

    void removeAppender(final String name) {
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        config.getAppenders().remove( name ).stop();

        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.removeAppender( name );
        }
        config.getRootLogger().removeAppender( name );
    }


    /**
     * Logs the status of the session.
     */
    private void logSessionStatus() {
        final DomainPair pair = new DomainPair(XMPPServer.getInstance().getServerInfo().getXMPPDomain(), domain);
        OutgoingServerSession session = XMPPServer.getInstance().getSessionManager().getOutgoingServerSession(pair);
        if (session != null) {
            int connectionStatus = session.getStatus();
            switch(connectionStatus) {
            case Session.STATUS_CONNECTED:
                Log.info("Session is connected.");
                break;
            case Session.STATUS_CLOSED:
                Log.info("Session is closed.");
                break;
            case Session.STATUS_AUTHENTICATED:
                Log.info("Session is authenticated.");
                break;
            }
        } else {
            Log.info("Failed to establish server to server session.");
        }
    }

    /**
     * @return A String representation of the certificate chain for the connection to the domain under test.
     */
    private String getCertificates() {
        final DomainPair pair = new DomainPair(XMPPServer.getInstance().getServerInfo().getXMPPDomain(), domain);
        Session session = XMPPServer.getInstance().getSessionManager().getOutgoingServerSession(pair);
        StringBuilder certs = new StringBuilder();
        if (session != null) {
            Log.info("Successfully negotiated TLS connection.");
            Certificate[] certificates = session.getPeerCertificates();
            for (Certificate certificate : certificates) {
                X509Certificate x509cert = (X509Certificate) certificate;
                certs.append("--\nSubject: ");
                certs.append(x509cert.getSubjectDN());

                List<String> subjectAltNames = new SANCertificateIdentityMapping().mapIdentity(x509cert);
                if (!subjectAltNames.isEmpty()) {
                    certs.append("\nSubject Alternative Names: ");
                    for (String subjectAltName : subjectAltNames) {
                        certs.append("\n  ");
                        certs.append(subjectAltName);
                    }
                }

                certs.append("\nNot Before: ");
                certs.append(x509cert.getNotBefore());
                certs.append("\nNot After: ");
                certs.append(x509cert.getNotAfter());
                certs.append("\n\n-----BEGIN CERTIFICATE-----\n");
                certs.append(DatatypeConverter.printBase64Binary(
                        certificate.getPublicKey().getEncoded()).replaceAll("(.{64})", "$1\n"));
                certs.append("\n-----END CERTIFICATE-----\n\n");
            }
        }
        return certs.toString();
    }

    /**
     * Packet interceptor for the duration of our S2S test.
     */
    private class S2SInterceptor implements PacketInterceptor {
        private StringBuilder xml = new StringBuilder();

        private final IQ ping;

        /**
         * @param ping The IQ ping request that was used to initiate the test.
         */
        public S2SInterceptor( IQ ping )
        {
            this.ping = ping;
        }

        /**
         * Keeps a log of the XMPP traffic, releasing the wait lock on response received.
         */
        @Override
        public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
                throws PacketRejectedException {

            if (ping.getTo() == null || packet.getFrom() == null || packet.getTo() == null) {
                return;
            }

            if (!processed
                    && (ping.getTo().getDomain().equals(packet.getFrom().getDomain()) || ping.getTo().getDomain().equals(packet.getTo().getDomain()))) {

                // Log all traffic to and from the domain.
                xml.append(packet.toXML());
                xml.append('\n');

                // If we've received our IQ response, stop the test.
                if ( packet instanceof IQ )
                {
                    final IQ iq = (IQ) packet;
                    if ( iq.isResponse() && ping.getID().equals( iq.getID() ) && ping.getTo().equals( iq.getFrom() ) ) {
                        Log.info("Successful server to server response received.");
                        waitUntil.release();
                    }
                }
            }
        }

        /**
         * Returns the received stanzas as a String.
         */
        public String toString() {
            return xml.toString();
        }
    }
}
