/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.dom4j.io.XPPPacketReader;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.net.SocketConnection;
import org.jivesoftware.messenger.net.SASLAuthentication;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Represents a session between the server and a client.
 *
 * @author Gaston Dombiak
 */
public class ClientSession extends Session {

    private static final String ETHERX_NAMESPACE = "http://etherx.jabber.org/streams";
    private static final String FLASH_NAMESPACE = "http://www.jabber.com/streams/flash";

    /**
     * Keep the list of IP address that are allowed to connect to the server. If the list is
     * empty then anyone is allowed to connect to the server.<p>
     *
     * Note: Key = IP address or IP range; Value = empty string. A hash map is being used for
     * performance reasons.
     */
    private static Map<String,String> allowedIPs = new HashMap<String,String>();

    /**
     * The authentication token for this session.
     */
    protected AuthToken authToken;

    /**
     * Flag indicating if this session has been initialized yet (upon first available transition).
     */
    private boolean initialized;

    /**
     * Flag indicating if the user requested to not receive offline messages when sending
     * an available presence. The user may send a disco request with node
     * "http://jabber.org/protocol/offline" so that no offline messages are sent to the
     * user when he becomes online. If the user is connected from many resources then
     * if one of the sessions stopped the flooding then no session should flood the user.
     */
    private boolean offlineFloodStopped = false;

    private Presence presence = null;

    private int conflictCount = 0;

    static {
        // Fill out the allowedIPs with the system property
        String allowed = JiveGlobals.getProperty("xmpp.client.login.allowed", "");
        StringTokenizer tokens = new StringTokenizer(allowed, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            allowedIPs.put(address, "");
        }
    }

    /**
     * Returns a newly created session between the server and a client. The session will
     * be created and returned only if correct name/prefix (i.e. 'stream' or 'flash')
     * and namespace were provided by the client.
     *
     * @param serverName the name of the server where the session is connecting to.
     * @param reader the reader that is reading the provided XML through the connection.
     * @param connection the connection with the client.
     * @return a newly created session between the server and a client.
     */
    public static Session createSession(String serverName, XPPPacketReader reader,
            SocketConnection connection) throws XmlPullParserException, UnauthorizedException,
            IOException
    {
        XmlPullParser xpp = reader.getXPPParser();

        boolean isFlashClient = xpp.getPrefix().equals("flash");
        connection.setFlashClient(isFlashClient);

        // Conduct error checking, the opening tag should be 'stream'
        // in the 'etherx' namespace
        if (!xpp.getName().equals("stream") && !isFlashClient) {
            throw new XmlPullParserException(
                    LocaleUtils.getLocalizedString("admin.error.bad-stream"));
        }

        if (!xpp.getNamespace(xpp.getPrefix()).equals(ETHERX_NAMESPACE) &&
                !(isFlashClient && xpp.getNamespace(xpp.getPrefix()).equals(FLASH_NAMESPACE)))
        {
            throw new XmlPullParserException(LocaleUtils.getLocalizedString(
                    "admin.error.bad-namespace"));
        }

        if (!allowedIPs.isEmpty()) {
            // The server is using a whitelist so check that the IP address of the client
            // is authorized to connect to the server
            if (!allowedIPs.containsKey(connection.getInetAddress().getHostAddress())) {
                byte[] address = connection.getInetAddress().getAddress();
                String range1 = (address[0] & 0xff) + "." + (address[1] & 0xff) + "." +
                        (address[2] & 0xff) +
                        ".*";
                String range2 = (address[0] & 0xff) + "." + (address[1] & 0xff) + ".*.*";
                String range3 = (address[0] & 0xff) + ".*.*.*";
                if (!allowedIPs.containsKey(range1) && !allowedIPs.containsKey(range2) &&
                        !allowedIPs.containsKey(range3)) {
                    // Client cannot connect from this IP address so end the stream and
                    // TCP connection
                    Log.debug("Closed connection to client attempting to connect from: " +
                            connection.getInetAddress().getHostAddress());
                    // Include the not-authorized error in the response
                    StreamError error = new StreamError(StreamError.Condition.not_authorized);
                    StringBuilder sb = new StringBuilder();
                    sb.append(error.toXML());
                    connection.deliverRawText(sb.toString());
                    // Close the underlying connection
                    connection.close();
                    return null;
                }
            }
        }

        // Default language is English ("en").
        String language = "en";
        // Default to a version of "0.0". Clients written before the XMPP 1.0 spec may
        // not report a version in which case "0.0" should be assumed (per rfc3920
        // section 4.4.1).
        int majorVersion = 0;
        int minorVersion = 0;
        for (int i = 0; i < xpp.getAttributeCount(); i++) {
            if ("lang".equals(xpp.getAttributeName(i))) {
                language = xpp.getAttributeValue(i);
            }
            if ("version".equals(xpp.getAttributeName(i))) {
                try {
                    int[] version = decodeVersion(xpp.getAttributeValue(i));
                    majorVersion = version[0];
                    minorVersion = version[1];
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }

        // If the client supports a greater major version than the server,
        // set the version to the highest one the server supports.
        if (majorVersion > MAJOR_VERSION) {
            majorVersion = MAJOR_VERSION;
            minorVersion = MINOR_VERSION;
        }
        else if (majorVersion == MAJOR_VERSION) {
            // If the client supports a greater minor version than the
            // server, set the version to the highest one that the server
            // supports.
            if (minorVersion > MINOR_VERSION) {
                minorVersion = MINOR_VERSION;
            }
        }

        // Store language and version information in the connection.
        connection.setLanaguage(language);
        connection.setXMPPVersion(majorVersion, minorVersion);

        // Create a ClientSession for this user.
        Session session = SessionManager.getInstance().createClientSession(connection);

        Writer writer = connection.getWriter();
        // Build the start packet response
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        if (isFlashClient) {
            sb.append("<flash:stream xmlns:flash=\"http://www.jabber.com/streams/flash\" ");
        }
        else {
            sb.append("<stream:stream ");
        }
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"jabber:client\" from=\"");
        sb.append(serverName);
        sb.append("\" id=\"");
        sb.append(session.getStreamID().toString());
        sb.append("\" xml:lang=\"");
        sb.append(language);
        // Don't include version info if the version is 0.0.
        if (majorVersion != 0) {
            sb.append("\" version=\"");
            sb.append(majorVersion).append(".").append(minorVersion);
        }
        sb.append("\">");
        writer.write(sb.toString());

        // If this is a "Jabber" connection, the session is now initialized and we can
        // return to allow normal packet parsing.
        if (majorVersion == 0) {
            // If this is a flash client append a special caracter to the response.
            if (isFlashClient) {
                writer.write('\0');
            }
            writer.flush();

            return session;
        }
        // Otherwise, this is at least XMPP 1.0 so we need to announce stream features.

        sb = new StringBuilder();
        sb.append("<stream:features>");
        sb.append("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
        // TODO Consider that STARTTLS may be optional (add TLS options to the AC - disabled, optional, required)
        // sb.append("<required/>");
        sb.append("</starttls>");
        // Include available SASL Mechanisms
        sb.append(SASLAuthentication.getSASLMechanisms(session));
        sb.append("</stream:features>");

        writer.write(sb.toString());

        if (isFlashClient) {
            writer.write('\0');
        }
        writer.flush();

        return session;
    }

    /**
     * Returns the list of IP address that are allowed to connect to the server. If the list is
     * empty then anyone is allowed to connect to the server.
     *
     * @return the list of IP address that are allowed to connect to the server.
     */
    public static Map<String, String> getAllowedIPs() {
        return allowedIPs;
    }

    /**
     * Sets the list of IP address that are allowed to connect to the server. If the list is
     * empty then anyone is allowed to connect to the server.
     *
     * @param allowed the list of IP address that are allowed to connect to the server.
     */
    public static void setAllowedIPs(Map<String, String> allowed) {
        allowedIPs = allowed;
        if (allowedIPs.isEmpty()) {
            JiveGlobals.deleteProperty("xmpp.client.login.allowed");
        }
        else {
            // Iterate through the elements in the map.
            StringBuilder buf = new StringBuilder();
            Iterator<String> iter = allowedIPs.keySet().iterator();
            if (iter.hasNext()) {
                buf.append(iter.next());
            }
            while (iter.hasNext()) {
                buf.append(", ").append((String)iter.next());
            }
            JiveGlobals.setProperty("xmpp.client.login.allowed", buf.toString());
        }
    }

    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param connection The connection we are proxying
     */
    public ClientSession(String serverName, Connection connection, StreamID streamID) {
        super(serverName, connection, streamID);
        // Set an unavailable initial presence
        presence = new Presence();
        presence.setType(Presence.Type.unavailable);
    }

    /**
     * Returns the username associated with this session. Use this information
     * with the user manager to obtain the user based on username.
     *
     * @return the username associated with this session
     * @throws UserNotFoundException if a user is not associated with a session
     *      (the session has not authenticated yet)
     */
    public String getUsername() throws UserNotFoundException {
        if (authToken == null) {
            throw new UserNotFoundException();
        }
        return getAddress().getNode();
    }

    /**
     * Sets the new Authorization Token for this session. The session is not yet considered fully
     * authenticated (i.e. active) since a resource has not been binded at this point. This
     * message will be sent after SASL authentication was successful but yet resource binding
     * is required.
     *
     * @param auth the authentication token obtained from SASL authentication.
     */
    public void setAuthToken(AuthToken auth) {
        authToken = auth;
    }

    /**
     * Initialize the session with a valid authentication token and
     * resource name. This automatically upgrades the session's
     * status to authenticated and enables many features that are not
     * available until authenticated (obtaining managers for example).
     *
     * @param auth the authentication token obtained from the AuthFactory.
     * @param resource the resource this session authenticated under.
     * @param userManager the user manager this authentication occured under.
     */
    public void setAuthToken(AuthToken auth, UserManager userManager, String resource)
            throws UserNotFoundException
    {
        User user = userManager.getUser(auth.getUsername());
        setAddress(new JID(user.getUsername(), getServerName(), resource));
        authToken = auth;

        sessionManager.addSession(this);
        setStatus(Session.STATUS_AUTHENTICATED);
    }

    /**
     * <p>Initialize the session as an anonymous login.</p>
     * <p>This automatically upgrades the session's
     * status to authenticated and enables many features that are not
     * available until authenticated (obtaining managers for example).</p>
     */
    public void setAnonymousAuth() {
        sessionManager.addAnonymousSession(this);
        setStatus(Session.STATUS_AUTHENTICATED);
    }

    /**
     * Returns the authentication token associated with this session.
     *
     * @return the authentication token associated with this session (can be null).
     */
    public AuthToken getAuthToken() {
        return authToken;
    }

    /**
     * Flag indicating if this session has been initialized once coming
     * online. Session initialization occurs after the session receives
     * the first "available" presence update from the client. Initialization
     * actions include pushing offline messages, presence subscription requests,
     * and presence statuses to the client. Initialization occurs only once
     * following the first available presence transition.
     *
     * @return True if the session has already been initializsed
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Sets the initialization state of the session.
     *
     * @param isInit True if the session has been initialized
     * @see #isInitialized
     */
    public void setInitialized(boolean isInit) {
        initialized = isInit;
    }

    /**
     * Returns true if the offline messages of the user should be sent to the user when
     * the user becomes online. If the user sent a disco request with node
     * "http://jabber.org/protocol/offline" before the available presence then do not
     * flood the user with the offline messages. If the user is connected from many resources
     * then if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @return true if the offline messages of the user should be sent to the user when the user
     *         becomes online.
     */
    public boolean canFloodOfflineMessages() {
        for (ClientSession session : sessionManager.getSessions()) {
            if (session.isOfflineFloodStopped()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the user requested to not receive offline messages when sending
     * an available presence. The user may send a disco request with node
     * "http://jabber.org/protocol/offline" so that no offline messages are sent to the
     * user when he becomes online. If the user is connected from many resources then
     * if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @return true if the user requested to not receive offline messages when sending
     *         an available presence.
     */
    public boolean isOfflineFloodStopped() {
        return offlineFloodStopped;
    }

    /**
     * Sets if the user requested to not receive offline messages when sending
     * an available presence. The user may send a disco request with node
     * "http://jabber.org/protocol/offline" so that no offline messages are sent to the
     * user when he becomes online. If the user is connected from many resources then
     * if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @param offlineFloodStopped if the user requested to not receive offline messages when
     *        sending an available presence.
     */
    public void setOfflineFloodStopped(boolean offlineFloodStopped) {
        this.offlineFloodStopped = offlineFloodStopped;
    }

    /**
     * Obtain the presence of this session.
     *
     * @return The presence of this session or null if not authenticated
     */
    public Presence getPresence() {
        return presence;
    }

    /**
     * Set the presence of this session
     *
     * @param presence The presence for the session
     * @return The old priority of the session or null if not authenticated
     */
    public Presence setPresence(Presence presence) {
        Presence oldPresence = this.presence;
        this.presence = presence;
        if (oldPresence.isAvailable() && !this.presence.isAvailable()) {
            // The client is no longer available
            sessionManager.sessionUnavailable(this);
            // Mark that the session is no longer initialized. This means that if the user sends
            // an available presence again the session will be initialized again thus receiving
            // offline messages and offline presence subscription requests
            setInitialized(false);
        }
        else if (!oldPresence.isAvailable() && this.presence.isAvailable()) {
            // The client is available
            sessionManager.sessionAvailable(this);
        }
        else if (oldPresence.getPriority() != this.presence.getPriority()) {
            // The client has changed the priority of his presence
            sessionManager.changePriority(getAddress(), this.presence.getPriority());
        }
        return oldPresence;
    }

    /**
     * Returns the number of conflicts detected on this session.
     * Conflicts typically occur when another session authenticates properly
     * to the user account and requests to use a resource matching the one
     * in use by this session. Administrators may configure the server to automatically
     * kick off existing sessions when their conflict count exceeds some limit including
     * 0 (old sessions are kicked off immediately to accommodate new sessions). Conflicts
     * typically signify the existing (old) session is broken/hung.
     *
     * @return The number of conflicts detected for this session
     */
    public int getConflictCount() {
        return conflictCount;
    }

    /**
     * Increments the conflict by one.
     */
    public void incrementConflictCount() {
        conflictCount++;
    }

    public void process(Packet packet) {
        deliver(packet);
    }

    private void deliver(Packet packet) {
        if (conn != null && !conn.isClosed()) {
            try {
                conn.deliver(packet);
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    public String toString() {
        return super.toString() + " presence: " + presence;
    }
}
