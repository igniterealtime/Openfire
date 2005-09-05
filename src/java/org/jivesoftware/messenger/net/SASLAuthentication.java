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

package org.jivesoftware.messenger.net;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XPPPacketReader;
import org.jivesoftware.messenger.ClientSession;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.auth.AuthFactory;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmlpull.v1.XmlPullParserException;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * SASLAuthentication is responsible for returning the available SASL mechanisms to use and for
 * actually performing the SASL authentication.<p>
 *
 * The list of available SASL mechanisms is determined by 1) the type of
 * {@link org.jivesoftware.messenger.user.UserProvider} being used since some SASL mechanisms
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

    private XPPPacketReader reader;

    /**
     *
     */
    public SASLAuthentication(Session session, XPPPacketReader reader) {
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
        StringBuilder sb = new StringBuilder();
        sb.append("<mechanisms xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        // Check if the user provider in use supports passwords retrieval. Accessing to the users
        // passwords will be required by the CallbackHandler
        if (UserManager.getUserProvider().supportsPasswordRetrieval()) {
            sb.append("<mechanism>CRAM-MD5</mechanism>");
            sb.append("<mechanism>DIGEST-MD5</mechanism>");
        }
        sb.append("<mechanism>PLAIN</mechanism>");
        if (XMPPServer.getInstance().getIQAuthHandler().isAllowAnonymous()) {
            sb.append("<mechanism>ANONYMOUS</mechanism>");
        }
        if (session.getConnection().isSecure()) {
            //sb.append("<mechanism>EXTERNAL</mechanism>");
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
                        if (mechanism.equalsIgnoreCase("PLAIN")) {
                            success = doPlainAuthentication(doc);
                            isComplete = true;
                        }
                        else if (mechanism.equalsIgnoreCase("ANONYMOUS")) {
                            success = doAnonymousAuthentication();
                            isComplete = true;
                        }
                        else {
                            // The selected SASL mechanism requires the server to send a challenge
                            // to the client
                            try {
                                Map<String, String> props = new TreeMap<String, String>();
                                props.put(Sasl.QOP, "auth");
                                SaslServer ss = Sasl.createSaslServer(mechanism, "xmpp",
                                        session.getServerName(), props,
                                        new XMPPCallbackHandler());
                                // evaluateResponse doesn't like null parameter
                                byte[] challenge = ss.evaluateResponse(new byte[0]);
                                // Send the challenge
                                sendChallenge(challenge);

                                session.setSessionData("SaslServer", ss);
                            }
                            catch (SaslException e) {
                                Log.warn("SaslException", e);
                                authenticationFailed();
                            }
                        }
                        break;
                    case RESPONSE:
                        SaslServer ss = (SaslServer) session.getSessionData("SaslServer");
                        // TODO Should we mark complete here? or only if failure or success?
                        // Seems like the challenge-response loop may only have 1 iteration.
                        // ok, so I move the complete mark at the begining of RESPONSE. I don't think
                        // a success mark is enough. In case of a failed SASL handshake, the success
                        // mark is false. But the handshake is done anyway.
                        isComplete = true;
                        if (ss != null) {
                            String response = doc.getTextTrim();
                            try {
                                byte[] data = StringUtils.decodeBase64(response).getBytes(CHARSET);
                                if (data == null) {
                                    data = new byte[0];
                                }
                                byte[] challenge = ss.evaluateResponse(data);
                                if (ss.isComplete()) {
                                    authenticationSuccessful(ss.getAuthorizationID());
                                    success = true;
                                }
                                else {
                                    // Send the challenge
                                    sendChallenge(challenge);
                                }
                            }
                            catch (SaslException e) {
                                Log.warn("SaslException", e);
                                authenticationFailed();
                            }
                        }
                        else {
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
                }
            }
            // Remove the SaslServer from the Session
            session.removeSessionData("SaslServer");
        }
        return success;
    }

    private boolean doAnonymousAuthentication() {
        if (XMPPServer.getInstance().getIQAuthHandler().isAllowAnonymous()) {
            // Just accept the authentication :)
            authenticationSuccessful(null);
            return true;
        }
        else {
            // anonymous login is disabled so close the connection
            authenticationFailed();
            return false;
        }
    }

    private boolean doPlainAuthentication(Element doc) {
        String username = "";
        String password = "";
        String response = doc.getTextTrim();
        if (response != null && response.length() > 0) {
            // Parse data and obtain username & password
            String data = StringUtils.decodeBase64(response);
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
            authenticationSuccessful(token.getUsername());
            return true;
        }
        catch (UnauthorizedException e) {
            authenticationFailed();
            return false;
        }
    }

    private void sendChallenge(byte[] challenge) {
        StringBuilder reply = new StringBuilder();
        reply.append(
                "<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        reply.append(StringUtils.encodeBase64(challenge).trim());
        reply.append("</challenge>");
        connection.deliverRawText(reply.toString());
    }

    private void authenticationSuccessful(String username) {
        StringBuilder reply = new StringBuilder();
        reply.append("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>");
        connection.deliverRawText(reply.toString());
        // We only support SASL for c2s
        if (session instanceof ClientSession) {
            ((ClientSession) session).setAuthToken(new AuthToken(username));
        }
    }

    private void authenticationFailed() {
        StringBuilder reply = new StringBuilder();
        reply.append("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        reply.append("<temporary-auth-failure/></failure>");
        connection.deliverRawText(reply.toString());
        // Close the connection
        connection.close();
    }
}
