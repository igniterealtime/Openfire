/**
 * $RCSfile: OutgoingServerSession.java,v $
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.server;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XPPPacketReader;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.net.DNSUtil;
import org.jivesoftware.messenger.net.SocketConnection;
import org.jivesoftware.messenger.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The <tt>OutgoingServerSession</tt> represents the connection to a remote server that will only
 * be used for sending packets.<p>
 *
 * Currently only the Server Dialback method is being used for authenticating with the remote
 * server. Use {@link #authenticateDomain(String, String)} to create a new connection to a remote
 * server that will be used for sending packets to the remote server from the specified domain.
 * Only the authenticated domains with the remote server will be able to effectively send packets
 * to the remote server. The remote server will reject and close the connection if a
 * non-authenticated domain tries to send a packet through this connection.<p>
 *
 * Once the connection has been established with the remote server and at least a domain has been
 * authenticated then a new route will be added to the routing table for this connection. For
 * optimization reasons the same outgoing connection will be used even if the remote server has
 * several hostnames. However, different routes will be created in the routing table for each
 * hostname of the remote server.
 *
 * @author Gaston Dombiak
 */
public class OutgoingServerSession extends Session {

    /**
     * Regular expression to ensure that the hostname contains letters.
     */
    private static Pattern pattern = Pattern.compile("[a-zA-Z]");

    private Collection<String> authenticatedDomains = new ArrayList<String>();
    private Collection<String> hostnames = new ArrayList<String>();
    private OutgoingServerSocketReader socketReader;

    /**
     * Creates a new outgoing connection to the specified hostname if no one exists. The port of
     * the remote server could be configured by setting the <b>xmpp.server.socket.remotePort</b> 
     * property or otherwise the standard port 5269 will be used. Either a new connection was
     * created or already existed the specified hostname will be authenticated with the remote
     * server. Once authenticated the remote server will start accepting packets from the specified
     * domain.<p>
     *
     * The Server Dialback method is currently the only implemented method for server-to-server
     * authentication. This implies that the remote server will ask the Authoritative Server
     * to verify the domain to authenticate. Most probably this server will act as the
     * Authoritative Server. See {@link IncomingServerSession} for more information.
     *
     * @param domain the local domain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @return True if the domain was authenticated by the remote server.
     */
    public static boolean authenticateDomain(String domain, String hostname) {
        if (hostname == null || hostname.length() == 0 || hostname.trim().indexOf(' ') > -1) {
            // Do nothing if the target hostname is empty, null or contains whitespaces
            return false;
        }
        try {
            // Check if the remote hostname is in the blacklist
            if (!RemoteServerManager.canAccess(hostname)) {
                return false;
            }

            // Check if a session already exists to the desired hostname (i.e. remote server). If
            // no one exists then create a new session. The same session will be used for the same
            // hostname for all the domains to authenticate
            SessionManager sessionManager = SessionManager.getInstance();
            OutgoingServerSession session = sessionManager.getOutgoingServerSession(hostname);
            if (session == null) {
                // Try locating if the remote server has previously authenticated with this server
                for (IncomingServerSession incomingSession : sessionManager
                        .getIncomingServerSessions(hostname)) {
                    for (String otherHostname : incomingSession.getValidatedDomains()) {
                        session = sessionManager.getOutgoingServerSession(otherHostname);
                        if (session != null) {
                            // A session to the same remote server but with different hostname
                            // was found. Use this session and add the new hostname to the session
                            session.addHostname(hostname);
                            break;
                        }
                    }
                }
            }
            if (session == null) {
                int port = RemoteServerManager.getPortForServer(hostname);
                // No session was found to the remote server so make sure that only one is created
                synchronized (hostname.intern()) {
                    session = sessionManager.getOutgoingServerSession(hostname);
                    if (session == null) {
                        session = createOutgoingSession(domain, hostname, port);
                        if (session != null) {
                            // Add the new hostname to the list of names that the server may have
                            session.addHostname(hostname);
                            // Add the validated domain as an authenticated domain
                            session.addAuthenticatedDomain(domain);
                            // Notify the SessionManager that a new session has been created
                            sessionManager.outgoingServerSessionCreated(session);
                            return true;
                        }
                        else {
                            // Ensure that the hostname is not an IP address (i.e. contains chars)
                            if (!pattern.matcher(hostname).find()) {
                                return false;
                            }
                            // Check if hostname is a subdomain of an existing outgoing session
                            for (String otherHost : sessionManager.getOutgoingServers()) {
                                if (hostname.contains(otherHost)) {
                                    session = sessionManager.getOutgoingServerSession(otherHost);
                                    // Add the new hostname to the found session
                                    session.addHostname(hostname);
                                    return true;
                                }
                            }
                            // Try to establish a connection to candidate hostnames. Iterate on the
                            // substring after the . and try to establish a connection. If a
                            // connection is established then the same session will be used for
                            // sending packets to the "candidate hostname" as well as for the
                            // requested hostname (i.e. the subdomain of the candidate hostname)
                            // This trick is useful when remote servers haven't registered in their
                            // DNSs an entry for their subdomains
                            int index = hostname.indexOf('.');
                            while (index > -1 && index < hostname.length()) {
                                String newHostname = hostname.substring(index + 1);
                                String serverName = XMPPServer.getInstance().getServerInfo()
                                        .getName();
                                if ("com".equals(newHostname) || "net".equals(newHostname) ||
                                        "org".equals(newHostname) ||
                                        "gov".equals(newHostname) ||
                                        "edu".equals(newHostname) ||
                                        serverName.equals(newHostname)) {
                                    return false;
                                }
                                session = createOutgoingSession(domain, newHostname, port);
                                if (session != null) {
                                    // Add the new hostname to the list of names that the server may have
                                    session.addHostname(hostname);
                                    // Add the validated domain as an authenticated domain
                                    session.addAuthenticatedDomain(domain);
                                    // Notify the SessionManager that a new session has been created
                                    sessionManager.outgoingServerSessionCreated(session);
                                    // Add the new hostname to the found session
                                    session.addHostname(newHostname);
                                    return true;
                                }
                                else {
                                    index = hostname.indexOf('.', index + 1);
                                }
                            }
                            return false;
                        }
                    }
                }
            }
            if (session.getAuthenticatedDomains().contains(domain)) {
                // Do nothing since the domain has already been authenticated
                return true;
            }
            // A session already exists so authenticate the domain using that session
            ServerDialback method = new ServerDialback(session.getConnection(), domain);
            if (method.authenticateDomain(session.socketReader, domain, hostname,
                    session.getStreamID().getID())) {
                // Add the validated domain as an authenticated domain
                session.addAuthenticatedDomain(domain);
                return true;
            }
        }
        catch (Exception e) {
            Log.error("Error authenticating domain with remote server: " + hostname, e);
        }
        return false;
    }

    /**
     * Establishes a new outgoing session to a remote server. If the remote server supports TLS
     * and SASL then the new outgoing connection will be secured with TLS and authenticated
     * using SASL. However, if TLS or SASL is not supported by the remote server or if an
     * error occured while securing or authenticating the connection using SASL then server
     * dialback method will be used.
     *
     * @param domain the local domain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @param port default port to use to establish the connection.
     * @return new outgoing session to a remote server.
     */
    private static OutgoingServerSession createOutgoingSession(String domain, String hostname,
            int port) {
        boolean useTLS = JiveGlobals.getBooleanProperty("xmpp.server.tls.enabled", false);
        RemoteServerConfiguration configuration = RemoteServerManager.getConfiguration(hostname);
        if (configuration != null) {
            // TODO Use the specific TLS configuration for this remote server
            //useTLS = configuration.isTLSEnabled();
        }

        if (useTLS) {
            // Connect to remote server using TLS + SASL
            SocketConnection connection = null;
            String realHostname = null;
            int realPort = port;
            Socket socket = new Socket();
            try {
                Log.debug("OS - Trying to connect to " + hostname + ":" + port);

                // Get the real hostname to connect to using DNS lookup of the specified hostname
                DNSUtil.HostAddress address = DNSUtil.resolveXMPPServerDomain(hostname, port);
                realHostname = address.getHost();
                realPort = address.getPort();
                // Establish a TCP connection to the Receiving Server
                socket.connect(new InetSocketAddress(realHostname, realPort),
                        RemoteServerManager.getSocketTimeout());
                Log.debug("OS - Plain connection to " + hostname + ":" + port + " successful");
            }
            catch (Exception e) {
                Log.error("Error trying to connect to remote server: " + hostname +
                        "(DNS lookup: " + realHostname + ":" + realPort + ")", e);
                return null;
            }

            try {
                connection =
                        new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket,
                                false);

                // Send the stream header
                StringBuilder openingStream = new StringBuilder();
                openingStream.append("<stream:stream");
                openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
                openingStream.append(" xmlns=\"jabber:server\"");
                openingStream.append(" to=\"").append(hostname).append("\"");
                openingStream.append(" version=\"1.0\">");
                connection.deliverRawText(openingStream.toString());

                XPPPacketReader reader = new XPPPacketReader();
                reader.setXPPFactory(XmlPullParserFactory.newInstance());
                reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                        CHARSET));
                // Get the answer from the Receiving Server
                XmlPullParser xpp = reader.getXPPParser();
                for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                    eventType = xpp.next();
                }

                String serverVersion = xpp.getAttributeValue("", "version");

                // Check if the remote server is XMPP 1.0 compliant
                if (serverVersion != null && decodeVersion(serverVersion)[0] >= 1) {
                    // Get the stream features
                    Element features = reader.parseDocument().getRootElement();
                    // Check if TLS is enabled
                    if (features != null && features.element("starttls") != null) {
                        // Secure the connection with TLS and authenticate using SASL
                        OutgoingServerSession answer;
                        answer = secureAndAuthenticate(hostname, connection, reader, openingStream,
                                xpp, domain);
                        if (answer != null) {
                            // Everything went fine so return the secured and
                            // authenticated connection
                            return answer;
                        }
                    }
                    else {
                        Log.debug("OS - Error, <starttls> was not received");
                    }
                }
                // Something went wrong so close the connection and try server dialback over
                // a plain connection
                if (connection != null) {
                    connection.close();
                }
            }
            catch (Exception e) {
                Log.error("Error creating secured outgoing session to remote server: " + hostname +
                        "(DNS lookup: " + realHostname + ":" + realPort + ")", e);
                // Close the connection
                if (connection != null) {
                    connection.close();
                }
            }
        }
        Log.debug("OS - Going to try connecting using server dialback");
        // Use server dialback over a plain connection
        return new ServerDialback().createOutgoingSession(domain, hostname, port);
    }

    private static OutgoingServerSession secureAndAuthenticate(String hostname,
            SocketConnection connection, XPPPacketReader reader, StringBuilder openingStream,
            XmlPullParser xpp, String domain) throws Exception {
        Element features;
        Log.debug("OS - Indicating we want TLS to " + hostname);
        connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        // Wait for the <proceed> response
        Element proceed = reader.parseDocument().getRootElement();
        if (proceed != null && proceed.getName().equals("proceed")) {
            Log.debug("OS - Negotiating TLS with " + hostname);
            connection.startTLS(true);
            Log.debug("OS - TLS negotiation with " + hostname + " was successful");

            // TLS negotiation was successful so initiate a new stream
            connection.deliverRawText(openingStream.toString());

            // Reset the parser to use the new secured reader
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(),
                    CHARSET));
            // Skip new stream element
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            // Get new stream features
            features = reader.parseDocument().getRootElement();
            if (features != null && features.element("mechanisms") != null) {
                Iterator it = features.element("mechanisms").elementIterator();
                while (it.hasNext()) {
                    Element mechanism = (Element) it.next();
                    if ("EXTERNAL".equals(mechanism.getTextTrim())) {
                        Log.debug("OS - Starting EXTERNAL SASL with " + hostname);
                        if (doExternalAuthentication(domain, connection, reader)) {
                            Log.debug("OS - EXTERNAL SASL with " + hostname + " was successful");
                            // SASL was successful so initiate a new stream
                            connection.deliverRawText(openingStream.toString());

                            // Reset the parser to use the new secured reader
                            xpp.setInput(new InputStreamReader(
                                    connection.getTLSStreamHandler().getInputStream(), CHARSET));
                            // Skip the opening stream sent by the server
                            for (int eventType = xpp.getEventType();
                                 eventType != XmlPullParser.START_TAG;) {
                                eventType = xpp.next();
                            }

                            // SASL authentication was successful so create new
                            // OutgoingServerSession
                            String id = xpp.getAttributeValue("", "id");
                            StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                            OutgoingServerSession session = new OutgoingServerSession(domain,
                                    connection, new OutgoingServerSocketReader(reader), streamID);
                            connection.init(session);
                            // Set the hostname as the address of the session
                            session.setAddress(new JID(null, hostname, null));
                            return session;
                        }
                        else {
                            Log.debug("OS - Error, EXTERNAL SASL authentication with " + hostname +
                                    " failed");
                        }
                    }
                }
                Log.debug("OS - Error, EXTERNAL SASL was not offered by " + hostname);
            }
            else {
                Log.debug("OS - Error, no SASL mechanisms were offered by " + hostname);
            }
        }
        else {
            Log.debug("OS - Error, <proceed> was not received");
        }
        return null;
    }

    private static boolean doExternalAuthentication(String domain, SocketConnection connection,
            XPPPacketReader reader) throws DocumentException, IOException, XmlPullParserException {

        StringBuilder sb = new StringBuilder();
        sb.append("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"EXTERNAL\">");
        sb.append(StringUtils.encodeBase64(domain));
        sb.append("</auth>");
        connection.deliverRawText(sb.toString());

        Element response = reader.parseDocument().getRootElement();
        if (response != null && "success".equals(response.getName())) {
            return true;
        }
        return false;
    }

    OutgoingServerSession(String serverName, Connection connection,
            OutgoingServerSocketReader socketReader, StreamID streamID) {
        super(serverName, connection, streamID);
        this.socketReader = socketReader;
        socketReader.setSession(this);
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        if (conn != null && !conn.isClosed()) {
            try {
                conn.deliver(packet);
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Returns a collection with all the domains, subdomains and virtual hosts that where
     * authenticated. The remote server will accept packets sent from any of these domains,
     * subdomains and virtual hosts.
     *
     * @return domains, subdomains and virtual hosts that where validated.
     */
    public Collection<String> getAuthenticatedDomains() {
        return Collections.unmodifiableCollection(authenticatedDomains);
    }

    /**
     * Adds a new authenticated domain, subdomain or virtual host to the list of
     * authenticated domains for the remote server. The remote server will accept packets
     * sent from this new authenticated domain.
     *
     * @param domain the new authenticated domain, subdomain or virtual host to add.
     */
    public void addAuthenticatedDomain(String domain) {
        authenticatedDomains.add(domain);
    }

    /**
     * Removes an authenticated domain from the list of authenticated domains. The remote
     * server will no longer be able to accept packets sent from the removed domain, subdomain or
     * virtual host.
     *
     * @param domain the domain, subdomain or virtual host to remove from the list of
     *               authenticated domains.
     */
    public void removeAuthenticatedDomain(String domain) {
        authenticatedDomains.remove(domain);
    }

    /**
     * Returns the list of hostnames related to the remote server. This tracking is useful for
     * reusing the same session for the same remote server even if the server has many names.
     *
     * @return the list of hostnames related to the remote server.
     */
    public Collection<String> getHostnames() {
        return Collections.unmodifiableCollection(hostnames);
    }

    /**
     * Adds a new hostname to the list of known hostnames of the remote server. This tracking is
     * useful for reusing the same session for the same remote server even if the server has
     * many names.
     *
     * @param hostname the new known name of the remote server
     */
    private void addHostname(String hostname) {
        if (hostnames.add(hostname)) {
            // Register the outgoing session in the SessionManager. If the session
            // was already registered nothing happens
            sessionManager.registerOutgoingServerSession(hostname, this);
            // Add a new route for this new session
            XMPPServer.getInstance().getRoutingTable().addRoute(new JID(hostname), this);
        }
    }

}
