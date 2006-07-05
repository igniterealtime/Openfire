/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.wildfire.ClientSession;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.auth.AuthFactory;
import org.jivesoftware.wildfire.auth.AuthToken;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.server.IncomingServerSession;
import org.xmpp.packet.JID;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.io.UnsupportedEncodingException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * SASLAuthentication is responsible for returning the available SASL mechanisms to use and for
 * actually performing the SASL authentication.<p>
 *
 * The list of available SASL mechanisms is determined by 1) the type of
 * {@link org.jivesoftware.wildfire.user.UserProvider} being used since some SASL mechanisms
 * require the server to be able to retrieve user passwords; 2) whether anonymous logins are
 * enabled or not and 3) whether the underlying connection has been secured or not.
 *
 * @author Hao Chen
 * @author Gaston Dombiak
 */
public class SASLAuthentication {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";

    private static final String SASL_NAMESPACE = "xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"";

    private static Map<String, ElementType> typeMap = new TreeMap<String, ElementType>();

    private static Set<String> mechanisms = null;

    static {
        initMechanisms();
    }

    public enum ElementType {

        AUTH("auth"), RESPONSE("response"), CHALLENGE("challenge"), FAILURE("failure"), UNDEF("");

        private String name = null;

        public String toString() {
            return name;
        }

        private ElementType(String name) {
            this.name = name;
            typeMap.put(this.name, this);
        }

        public static ElementType valueof(String name) {
            if (name == null) {
                return UNDEF;
            }
            ElementType e = typeMap.get(name);
            return e != null ? e : UNDEF;
        }
    }

    public enum Status {
        /**
         * Entity needs to respond last challenge. Session is still negotiating
         * SASL authentication.
         */
        needResponse,
        /**
         * SASL negotiation has failed. The entity may retry a few times before the connection
         * is closed.
         */
        failed,
        /**
         * SASL negotiation has been successful.
         */
        authenticated
    }

    /**
     * Returns a string with the valid SASL mechanisms available for the specified session. If
     * the session's connection is not secured then only include the SASL mechanisms that don't
     * require TLS.
     *
     * @return a string with the valid SASL mechanisms available for the specified session.
     */
    public static String getSASLMechanisms(Session session) {
        if (!(session instanceof ClientSession) && !(session instanceof IncomingServerSession)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(195);
        sb.append("<mechanisms xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        if (session.getConnection().isSecure() && session instanceof IncomingServerSession) {
            // Server connections dont follow the same rules as clients
            sb.append("<mechanism>EXTERNAL</mechanism>");
        }
        else {
            for (String mech : getSupportedMechanisms()) {
                if (mech.equals("CRAM-MD5") || mech.equals("DIGEST-MD5")) {
                    // Check if the user provider in use supports passwords retrieval. Accessing
                    // to the users passwords will be required by the CallbackHandler
                    if (!AuthFactory.getAuthProvider().supportsPasswordRetrieval()) {
                        continue;
                    }
                }
                else if (mech.equals("ANONYMOUS")) {
                    // Check anonymous is supported
                    if (!XMPPServer.getInstance().getIQAuthHandler().isAnonymousAllowed()) {
                        continue;
                    }
                }
                sb.append("<mechanism>");
                sb.append(mech);
                sb.append("</mechanism>");
            }
        }
        sb.append("</mechanisms>");
        return sb.toString();
    }

    /**
     * Handles the SASL authentication packet. The entity may be sending an initial
     * authentication request or a response to a challenge made by the server. The returned
     * value indicates whether the authentication has finished either successfully or not or
     * if the entity is expected to send a response to a challenge.
     *
     * @param session the session that is authenticating with the server.
     * @param doc the stanza sent by the authenticating entity.
     * @return value that indicates whether the authentication has finished either successfully
     *         or not or if the entity is expected to send a response to a challenge.
     * @throws UnsupportedEncodingException If UTF-8 charset is not supported.
     */
    public static Status handle(Session session, Element doc) throws UnsupportedEncodingException {
        Status status;
        String mechanism;
        if (doc.getNamespace().asXML().equals(SASL_NAMESPACE)) {
            ElementType type = ElementType.valueof(doc.getName());
            switch (type) {
                case AUTH:
                    mechanism = doc.attributeValue("mechanism");
                    // Store the requested SASL mechanism by the client
                    session.setSessionData("SaslMechanism", mechanism);
                    //Log.debug("SASLAuthentication.doHandshake() AUTH entered: "+mechanism);
                    if (mechanism.equalsIgnoreCase("PLAIN") &&
                            getSupportedMechanisms().contains("PLAIN")) {
                        status = doPlainAuthentication(session, doc);
                    }
                    else if (mechanism.equalsIgnoreCase("ANONYMOUS") &&
                            getSupportedMechanisms().contains("ANONYMOUS")) {
                        status = doAnonymousAuthentication(session);
                    }
                    else if (mechanism.equalsIgnoreCase("EXTERNAL")) {
                        status = doExternalAuthentication(session, doc);
                    }
                    else if (getSupportedMechanisms().contains(mechanism)) {
                        // The selected SASL mechanism requires the server to send a challenge
                        // to the client
                        try {
                            Map<String, String> props = new TreeMap<String, String>();
                            props.put(Sasl.QOP, "auth");
                            if (mechanism.equals("GSSAPI")) {
                                props.put(Sasl.SERVER_AUTH, "TRUE");
                            }
                            SaslServer ss = Sasl.createSaslServer(mechanism, "xmpp",
                                    session.getServerName(), props,
                                    new XMPPCallbackHandler());
                            // evaluateResponse doesn't like null parameter
                            byte[] token = new byte[0];
                            if (doc.isTextOnly()) {
                                // If auth request includes a value then validate it
                                token = StringUtils.decodeBase64(doc.getTextTrim());
                                if (token == null) {
                                    token = new byte[0];
                                }
                            }
                            byte[] challenge = ss.evaluateResponse(token);
                            // Send the challenge
                            sendChallenge(session, challenge);

                            session.setSessionData("SaslServer", ss);
                            status = Status.needResponse;
                        }
                        catch (SaslException e) {
                            Log.warn("SaslException", e);
                            authenticationFailed(session);
                            status = Status.failed;
                        }
                    }
                    else {
                        Log.warn("Client wants to do a MECH we don't support: '" +
                                mechanism + "'");
                        authenticationFailed(session);
                        status = Status.failed;
                    }
                    break;
                case RESPONSE:
                    // Store the requested SASL mechanism by the client
                    mechanism = (String) session.getSessionData("SaslMechanism");
                    if (mechanism.equalsIgnoreCase("PLAIN") &&
                            getSupportedMechanisms().contains("PLAIN")) {
                        status = doPlainAuthentication(session, doc);
                    }
                    else if (mechanism.equalsIgnoreCase("EXTERNAL")) {
                        status = doExternalAuthentication(session, doc);
                    }
                    else if (getSupportedMechanisms().contains(mechanism)) {
                        SaslServer ss = (SaslServer) session.getSessionData("SaslServer");
                        if (ss != null) {
                            boolean ssComplete = ss.isComplete();
                            String response = doc.getTextTrim();
                            try {
                                if (ssComplete) {
                                    authenticationSuccessful(session, ss.getAuthorizationID(),
                                            null);
                                    status = Status.authenticated;
                                }
                                else {
                                    byte[] data = StringUtils.decodeBase64(response);
                                    if (data == null) {
                                        data = new byte[0];
                                    }
                                    byte[] challenge = ss.evaluateResponse(data);
                                    if (ss.isComplete()) {
                                        authenticationSuccessful(session, ss.getAuthorizationID(),
                                                challenge);
                                        status = Status.authenticated;
                                    }
                                    else {
                                        // Send the challenge
                                        sendChallenge(session, challenge);
                                        status = Status.needResponse;
                                    }
                                }
                            }
                            catch (SaslException e) {
                                Log.warn("SaslException", e);
                                authenticationFailed(session);
                                status = Status.failed;
                            }
                        }
                        else {
                            Log.fatal("SaslServer is null, should be valid object instead.");
                            authenticationFailed(session);
                            status = Status.failed;
                        }
                    }
                    else {
                        Log.warn(
                                "Client responded to a MECH we don't support: '" + mechanism + "'");
                        authenticationFailed(session);
                        status = Status.failed;
                    }
                    break;
                default:
                    authenticationFailed(session);
                    status = Status.failed;
                    // Ignore
                    break;
            }
        }
        else {
            Log.debug("Unknown namespace sent in auth element: " + doc.asXML());
            authenticationFailed(session);
            status = Status.failed;
        }
        // Check if SASL authentication has finished so we can clean up temp information
        if (status == Status.failed || status == Status.authenticated) {
            // Remove the SaslServer from the Session
            session.removeSessionData("SaslServer");
            // Remove the requested SASL mechanism by the client
            session.removeSessionData("SaslMechanism");
        }
        return status;
    }

    private static Status doAnonymousAuthentication(Session session) {
        if (XMPPServer.getInstance().getIQAuthHandler().isAnonymousAllowed()) {
            // Just accept the authentication :)
            authenticationSuccessful(session, null, null);
            return Status.authenticated;
        }
        else {
            // anonymous login is disabled so close the connection
            authenticationFailed(session);
            return Status.failed;
        }
    }

    private static Status doPlainAuthentication(Session session, Element doc)
            throws UnsupportedEncodingException {
        String username;
        String password;
        String response = doc.getTextTrim();
        if (response == null || response.length() == 0) {
            // No info was provided so send a challenge to get it
            sendChallenge(session, new byte[0]);
            return Status.needResponse;
        }

        // Parse data and obtain username & password
        String data = new String(StringUtils.decodeBase64(response), CHARSET);
        StringTokenizer tokens = new StringTokenizer(data, "\0");
        if (tokens.countTokens() > 2) {
            // Skip the "authorization identity"
            tokens.nextToken();
        }
        username = tokens.nextToken();
        password = tokens.nextToken();
        try {
            AuthToken token = AuthFactory.authenticate(username, password);
            authenticationSuccessful(session, token.getUsername(), null);
            return Status.authenticated;
        }
        catch (UnauthorizedException e) {
            authenticationFailed(session);
            return Status.failed;
        }
    }

    private static Status doExternalAuthentication(Session session, Element doc)
            throws UnsupportedEncodingException {
        // Only accept EXTERNAL SASL for s2s. At this point the connection has already
        // been secured using TLS
        if (!(session instanceof IncomingServerSession)) {
            return Status.failed;
        }
        String hostname = doc.getTextTrim();
        if (hostname == null || hostname.length() == 0) {
            // No hostname was provided so send a challenge to get it
            sendChallenge(session, new byte[0]);
            return Status.needResponse;
        }

        hostname = new String(StringUtils.decodeBase64(hostname), CHARSET);
        // Check if cerificate validation is disabled for s2s
        // Flag that indicates if certificates of the remote server should be validated.
        // Disabling certificate validation is not recommended for production environments.
        boolean verify =
                JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true);
        if (!verify) {
            authenticationSuccessful(session, hostname, null);
            return Status.authenticated;
        }
        // Check that hostname matches the one provided in a certificate
        SocketConnection connection = (SocketConnection) session.getConnection();
        try {
            for (Certificate certificate : connection.getSSLSession().getPeerCertificates()) {
                if (TLSStreamHandler.getPeerIdentities((X509Certificate) certificate)
                        .contains(hostname)) {
                    authenticationSuccessful(session, hostname, null);
                    return Status.authenticated;
                }
            }
        }
        catch (SSLPeerUnverifiedException e) {
            Log.warn("Error retrieving client certificates of: " + session, e);
        }
        authenticationFailed(session);
        return Status.failed;
    }

    private static void sendChallenge(Session session, byte[] challenge) {
        StringBuilder reply = new StringBuilder(250);
        if(challenge == null) {
            challenge = new byte[0];
        }
        String challenge_b64 = StringUtils.encodeBase64(challenge).trim();
        if ("".equals(challenge_b64)) {
            challenge_b64 = "="; // Must be padded if null
        }
        reply.append(
                "<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        reply.append(challenge_b64);
        reply.append("</challenge>");
        session.getConnection().deliverRawText(reply.toString());
    }

    private static void authenticationSuccessful(Session session, String username,
            byte[] successData) {
        StringBuilder reply = new StringBuilder(80);
        reply.append("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"");
        if (successData != null) {
            reply.append(">").append(successData).append("</success>");
        }
        else {
            reply.append("/>");
        }
        session.getConnection().deliverRawText(reply.toString());
        // We only support SASL for c2s
        if (session instanceof ClientSession) {
            ((ClientSession) session).setAuthToken(new AuthToken(username));
        }
        else if (session instanceof IncomingServerSession) {
            String hostname = username;
            // Set the first validated domain as the address of the session
            session.setAddress(new JID(null, hostname, null));
            // Add the validated domain as a valid domain. The remote server can
            // now send packets from this address
            ((IncomingServerSession) session).addValidatedDomain(hostname);
        }
    }

    private static void authenticationFailed(Session session) {
        StringBuilder reply = new StringBuilder(80);
        reply.append("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        reply.append("<not-authorized/></failure>");
        session.getConnection().deliverRawText(reply.toString());
        // Give a number of retries before closing the connection
        Integer retries = (Integer) session.getSessionData("authRetries");
        if (retries == null) {
            retries = 1;
        }
        else {
            retries = retries + 1;
        }
        session.setSessionData("authRetries", retries);
        if (retries >= JiveGlobals.getIntProperty("xmpp.auth.retries", 3) ) {
            // Close the connection
            session.getConnection().close();
        }
    }

    /**
     * Adds a new SASL mechanism to the list of supported SASL mechanisms by the server. The
     * new mechanism will be offered to clients and connection managers as stream features.
     *
     * @param mechanism the new SASL mechanism.
     */
    public static void addSupportedMechanism(String mechanism) {
        mechanisms.add(mechanism);
    }

    /**
     * Removes a SASL mechanism from the list of supported SASL mechanisms by the server.
     *
     * @param mechanism the SASL mechanism to remove.
     */
    public static void removeSupportedMechanism(String mechanism) {
        mechanisms.remove(mechanism);
    }

    /**
     * Returns the list of supported SASL mechanisms by the server. Note that Java may have
     * support for more mechanisms but some of them may not be returned since a special setup
     * is required that might be missing. Use {@link #addSupportedMechanism(String)} to add
     * new SASL mechanisms.
     *
     * @return the list of supported SASL mechanisms by the server.
     */
    public static Set<String> getSupportedMechanisms() {
        return mechanisms;
    }

    private static void initMechanisms() {
        mechanisms = new HashSet<String>();
        String available = JiveGlobals.getXMLProperty("sasl.mechs");
        if (available == null) {
            mechanisms.add("ANONYMOUS");
            mechanisms.add("PLAIN");
            mechanisms.add("DIGEST-MD5");
            mechanisms.add("CRAM-MD5");
        } else {
            StringTokenizer st = new StringTokenizer(available, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                String mech = st.nextToken().toUpperCase();
                // Check that the mech is a supported mechansim. Maybe we shouldnt check this and allow any?
                if (mech.equals("ANONYMOUS") ||
                        mech.equals("PLAIN") ||
                        mech.equals("DIGEST-MD5") ||
                        mech.equals("CRAM-MD5") ||
                        mech.equals("GSSAPI")) {
                    Log.debug("SASLAuthentication: Added " + mech + " to mech list");
                    mechanisms.add(mech);
                }
            }

            if (getSupportedMechanisms().contains("GSSAPI")) {
                if (JiveGlobals.getXMLProperty("sasl.gssapi.config") != null) {
                    System.setProperty("java.security.krb5.debug",
                            JiveGlobals.getXMLProperty("sasl.gssapi.debug", "false"));
                    System.setProperty("java.security.auth.login.config",
                            JiveGlobals.getXMLProperty("sasl.gssapi.config"));
                    System.setProperty("javax.security.auth.useSubjectCredsOnly",
                            JiveGlobals.getXMLProperty("sasl.gssapi.useSubjectCredsOnly", "false"));
                } else {
                    //Not configured, remove the option.
                    Log.debug("SASLAuthentication: Removed GSSAPI from mech list");
                    mechanisms.remove("GSSAPI");
                }
            }
        }
    }
}
