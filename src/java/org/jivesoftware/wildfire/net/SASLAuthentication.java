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

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
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
import org.jivesoftware.wildfire.user.UserManager;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.io.IOException;
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

    private static Collection<String> mechanisms = null;

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

    private SocketConnection connection;
    private Session session;

    private XMPPPacketReader reader;

    /**
     *
     */
    public SASLAuthentication(Session session, XMPPPacketReader reader) {
        this.session = session;
        this.connection = (SocketConnection) session.getConnection();
        this.reader = reader;
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
                    if (!UserManager.getUserProvider().supportsPasswordRetrieval()) {
                        continue;
                    }
                }
                else if (mech.equals("ANONYMOUS")) {
                    // Check anonymous is supported
                    if (!XMPPServer.getInstance().getIQAuthHandler().isAllowAnonymous()) {
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

    // Do the SASL handshake
    public boolean doHandshake(Element doc)
            throws IOException, DocumentException, XmlPullParserException {
        boolean isComplete = false;
        boolean success = false;

        while (!isComplete) {
            if (doc.getNamespace().asXML().equals(SASL_NAMESPACE)) {
                ElementType type = ElementType.valueof(doc.getName());
                switch (type) {
                    case AUTH:
                        String mechanism = doc.attributeValue("mechanism");
                        //Log.debug("SASLAuthentication.doHandshake() AUTH entered: "+mechanism);
                        if (mechanism.equalsIgnoreCase("PLAIN")) {
                            if (getSupportedMechanisms().contains("PLAIN")) {
                                success = doPlainAuthentication(doc);
                            }
                            else {
                                // TODO Send auth failed before closing connection
                                success = false;
                            }
                            isComplete = true;
                        }
                        else if (mechanism.equalsIgnoreCase("ANONYMOUS")) {
                            if (getSupportedMechanisms().contains("ANONYMOUS")) {
                                success = doAnonymousAuthentication();
                            }
                            else {
                                // TODO Send auth failed before closing connection
                                success = false;
                            }
                            isComplete = true;
                        }
                        else if (mechanism.equalsIgnoreCase("EXTERNAL")) {
                            success = doExternalAuthentication(doc);
                            isComplete = true;
                        }
                        else {
                            // The selected SASL mechanism requires the server to send a challenge
                            // to the client
                            if (getSupportedMechanisms().contains(mechanism)) {
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
                                    sendChallenge(challenge);

                                    session.setSessionData("SaslServer", ss);
                                }
                                catch (SaslException e) {
                                    isComplete = true;
                                    Log.warn("SaslException", e);
                                    authenticationFailed();
                                }
                            }
                            else {
                                // TODO Send auth failed before closing connection
                                Log.warn("Client wants to do a MECH we don't support: '" +
                                        mechanism + "'");
                                isComplete = true;
                                success = false;
                            }
                        }
                        break;
                    case RESPONSE:
                        SaslServer ss = (SaslServer) session.getSessionData("SaslServer");
                        if (ss != null) {
                            boolean ssComplete = ss.isComplete();
                            String response = doc.getTextTrim();
                            try {
                                if (ssComplete) {
                                    authenticationSuccessful(ss.getAuthorizationID(), null);
                                    success = true;
                                    isComplete = true;
                                }
                                else {
                                    byte[] data = StringUtils.decodeBase64(response);
                                    if (data == null) {
                                        data = new byte[0];
                                    }
                                    byte[] challenge = ss.evaluateResponse(data);
                                    if (ss.isComplete()) {
                                        authenticationSuccessful(ss.getAuthorizationID(),
                                                challenge);
                                        success = true;
                                        isComplete = true;
                                    }
                                    else {
                                        // Send the challenge
                                        sendChallenge(challenge);
                                    }
                                }
                            }
                            catch (SaslException e) {
                                isComplete = true;
                                Log.warn("SaslException", e);
                                authenticationFailed();
                            }
                        }
                        else {
                            isComplete = true;
                            Log.fatal("SaslServer is null, should be valid object instead.");
                            authenticationFailed();
                        }
                        break;
                    default:
                        // Ignore
                        break;
                }
                if (!isComplete) {
                    // Get the next answer since we are not done yet
                    doc = reader.parseDocument().getRootElement();
                    if (doc == null) {
                        // Nothing was read because the connection was closed or dropped
                        isComplete = true;
                    }
                }
            }
            else {
                isComplete = true;
                Log.debug("Unknown namespace sent in auth element: " + doc.asXML());
                authenticationFailed();
            }
        }
        // Remove the SaslServer from the Session
        session.removeSessionData("SaslServer");
        return success;
    }

    private boolean doAnonymousAuthentication() {
        if (XMPPServer.getInstance().getIQAuthHandler().isAllowAnonymous()) {
            // Just accept the authentication :)
            authenticationSuccessful(null, null);
            return true;
        }
        else {
            // anonymous login is disabled so close the connection
            authenticationFailed();
            return false;
        }
    }

    private boolean doPlainAuthentication(Element doc)
            throws DocumentException, IOException, XmlPullParserException {
        String username = "";
        String password = "";
        String response = doc.getTextTrim();
        if (response == null || response.length() == 0) {
            // No info was provided so send a challenge to get it
            sendChallenge(new byte[0]);
            // Get the next answer since we are not done yet
            doc = reader.parseDocument().getRootElement();
            if (doc != null && doc.getTextTrim().length() > 0) {
                response = doc.getTextTrim();
            }
        }

        if (response != null && response.length() > 0) {
            // Parse data and obtain username & password
            String data = new String(StringUtils.decodeBase64(response), CHARSET);
            StringTokenizer tokens = new StringTokenizer(data, "\0");
            if (tokens.countTokens() > 2) {
                // Skip the "authorization identity"
                tokens.nextToken();
            }
            username = tokens.nextToken();
            password = tokens.nextToken();
        }
        try {
            AuthToken token = AuthFactory.authenticate(username, password);
            authenticationSuccessful(token.getUsername(), null);
            return true;
        }
        catch (UnauthorizedException e) {
            authenticationFailed();
            return false;
        }
    }

    private boolean doExternalAuthentication(Element doc) throws DocumentException, IOException,
            XmlPullParserException {
        // Only accept EXTERNAL SASL for s2s. At this point the connection has already
        // been secured using TLS
        if (!(session instanceof IncomingServerSession)) {
            return false;
        }
        String hostname = doc.getTextTrim();
        if (hostname == null || hostname.length() == 0) {
            // No hostname was provided so send a challenge to get it
            sendChallenge(new byte[0]);
            // Get the next answer since we are not done yet
            doc = reader.parseDocument().getRootElement();
            if (doc != null && doc.getTextTrim().length() > 0) {
                hostname = doc.getTextTrim();
            }
        }

        if (hostname != null  && hostname.length() > 0) {
            hostname = new String(StringUtils.decodeBase64(hostname), CHARSET);
            // Check if cerificate validation is disabled for s2s
            if (session instanceof IncomingServerSession) {
                // Flag that indicates if certificates of the remote server should be validated.
                // Disabling certificate validation is not recommended for production environments.
                boolean verify =
                        JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true);
                if (!verify) {
                    authenticationSuccessful(hostname, null);
                    return true;
                }
            }
            // Check that hostname matches the one provided in a certificate
            for (Certificate certificate : connection.getSSLSession().getPeerCertificates()) {
                if (TLSStreamHandler.getPeerIdentities((X509Certificate) certificate)
                        .contains(hostname)) {
                    authenticationSuccessful(hostname, null);
                    return true;
                }
            }
        }
        authenticationFailed();
        return false;
    }

    private void sendChallenge(byte[] challenge) {
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
        connection.deliverRawText(reply.toString());
    }

    private void authenticationSuccessful(String username, byte[] successData) {
        StringBuilder reply = new StringBuilder(80);
        reply.append("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"");
        if (successData != null) {
            reply.append(">").append(successData).append("</success>");
        }
        else {
            reply.append("/>");
        }
        connection.deliverRawText(reply.toString());
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

    private void authenticationFailed() {
        StringBuilder reply = new StringBuilder(80);
        reply.append("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        reply.append("<not-authorized/></failure>");
        connection.deliverRawText(reply.toString());
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
            connection.close();
        }
    }
    
    public static Collection<String> getSupportedMechanisms() {
        if (mechanisms == null) {
            mechanisms = new ArrayList<String>();
            String available = JiveGlobals.getXMLProperty("sasl.mechs");
            if (available == null) {
                mechanisms.add("ANONYMOUS");
                mechanisms.add("PLAIN");
                mechanisms.add("DIGEST-MD5");
                mechanisms.add("CRAM-MD5");
                return mechanisms;
            }
            StringTokenizer st = new StringTokenizer(available, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                String mech = st.nextToken().toUpperCase();
                // Check that the mech is a supported mechansim. Maybe we shouldnt check this and allow any?
                if(mech.equals("ANONYMOUS") ||
                  mech.equals("PLAIN") ||
                  mech.equals("DIGEST-MD5") ||
                  mech.equals("CRAM-MD5") ||
                  mech.equals("GSSAPI") ) { 
                    Log.debug("SASLAuthentication: Added "+mech+" to mech list");
                    mechanisms.add(mech);
                }
            }
            
            if(getSupportedMechanisms().contains("GSSAPI")) {
                if(JiveGlobals.getXMLProperty("sasl.gssapi.config") != null) {
                    System.setProperty("java.security.krb5.debug", JiveGlobals.getXMLProperty("sasl.gssapi.debug","false"));
                    System.setProperty("java.security.auth.login.config",JiveGlobals.getXMLProperty("sasl.gssapi.config"));
                    System.setProperty("javax.security.auth.useSubjectCredsOnly",JiveGlobals.getXMLProperty("sasl.gssapi.useSubjectCredsOnly","false"));
                } else {
                    //Not configured, remove the option.
                    Log.debug("SASLAuthentication: Removed GSSAPI from mech list");
                    mechanisms.remove("GSSAPI");
                }
            }
            
        }
        return mechanisms;
    }
}
