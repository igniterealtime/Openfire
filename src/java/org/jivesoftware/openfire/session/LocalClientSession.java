/**
 * $RCSfile$
 * $Revision: 3187 $
 * $Date: 2005-12-11 13:34:34 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.session;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.user.PresenceEventDispatcher;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

/**
 * Represents a session between the server and a client.
 *
 * @author Gaston Dombiak
 */
public class LocalClientSession extends LocalSession implements ClientSession {

	private static final Logger Log = LoggerFactory.getLogger(LocalClientSession.class);

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
    private static Map<String,String> allowedAnonymIPs = new HashMap<String,String>();

    /**
     * The authentication token for this session.
     */
    protected AuthToken authToken;

    /**
     * Flag indicating if this session has been initialized yet (upon first available transition).
     */
    private boolean initialized;

    /**
     * Flag that indicates if the session was available ever.
     */
    private boolean wasAvailable = false;

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

    /**
     * Privacy list that overrides the default privacy list. This list affects only this
     * session and only for the duration of the session.
     */
    private String activeList;
    /**
     * Default privacy list used for the session's user. This list is processed if there
     * is no active list set for the session.
     */
    private String defaultList;

    static {
        // Fill out the allowedIPs with the system property
        String allowed = JiveGlobals.getProperty("xmpp.client.login.allowed", "");
        StringTokenizer tokens = new StringTokenizer(allowed, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            allowedIPs.put(address, "");
        }
        String allowedAnonym = JiveGlobals.getProperty("xmpp.client.login.allowedAnonym", "");
        tokens = new StringTokenizer(allowedAnonym, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            allowedAnonymIPs.put(address, "");

        }
    }

    /**
     * Returns the list of IP address that are allowed to connect to the server. If the list is
     * empty then anyone is allowed to connect to the server except for anonymous users that are
     * subject to {@link #getAllowedAnonymIPs()}. This list is used for both anonymous and
     * non-anonymous users.
     *
     * @return the list of IP address that are allowed to connect to the server.
     */
    public static Map<String, String> getAllowedIPs() {
        return allowedIPs;
    }


    /**
     * Returns the list of IP address that are allowed to connect to the server for anonymous
     * users. If the list is empty then anonymous will be only restricted by {@link #getAllowedIPs()}.
     *
     * @return the list of IP address that are allowed to connect to the server.
     */
    public static Map<String, String> getAllowedAnonymIPs() {
        return allowedAnonymIPs;
    }

    /**
     * Returns a newly created session between the server and a client. The session will
     * be created and returned only if correct name/prefix (i.e. 'stream' or 'flash')
     * and namespace were provided by the client.
     *
     * @param serverName the name of the server where the session is connecting to.
     * @param xpp the parser that is reading the provided XML through the connection.
     * @param connection the connection with the client.
     * @return a newly created session between the server and a client.
     * @throws org.xmlpull.v1.XmlPullParserException if an error occurs while parsing incoming data.
     */
    public static LocalClientSession createSession(String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
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
            String hostAddress = "Unknown";
            // The server is using a whitelist so check that the IP address of the client
            // is authorized to connect to the server
            try {
               hostAddress = connection.getHostAddress();
            } catch (UnknownHostException e) {
                // Do nothing
            }
            if (!isAllowed(connection)) {
                // Client cannot connect from this IP address so end the stream and
                // TCP connection
                Log.debug("LocalClientSession: Closed connection to client attempting to connect from: " + hostAddress);
                // Include the not-authorized error in the response
                StreamError error = new StreamError(StreamError.Condition.not_authorized);
                connection.deliverRawText(error.toXML());
                // Close the underlying connection
                connection.close();
                return null;
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
                    Log.error(e.getMessage(), e);
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

        // Indicate the TLS policy to use for this connection
        if (!connection.isSecure()) {
            boolean hasCertificates = false;
            try {
                hasCertificates = SSLConfig.getKeyStore().size() > 0;
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            Connection.TLSPolicy tlsPolicy = getTLSPolicy();
            if (Connection.TLSPolicy.required == tlsPolicy && !hasCertificates) {
                Log.error("Client session rejected. TLS is required but no certificates " +
                        "were created.");
                return null;
            }
            // Set default TLS policy
            connection.setTlsPolicy(hasCertificates ? tlsPolicy : Connection.TLSPolicy.disabled);
        } else {
            // Set default TLS policy
            connection.setTlsPolicy(Connection.TLSPolicy.disabled);
        }

        // Indicate the compression policy to use for this connection
        connection.setCompressionPolicy(getCompressionPolicy());

        // Create a ClientSession for this user.
        LocalClientSession session = SessionManager.getInstance().createClientSession(connection);

        // Build the start packet response
        StringBuilder sb = new StringBuilder(200);
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
        connection.deliverRawText(sb.toString());

        // If this is a "Jabber" connection, the session is now initialized and we can
        // return to allow normal packet parsing.
        if (majorVersion == 0) {
            return session;
        }
        // Otherwise, this is at least XMPP 1.0 so we need to announce stream features.

        sb = new StringBuilder(490);
        sb.append("<stream:features>");
        if (connection.getTlsPolicy() != Connection.TLSPolicy.disabled) {
            sb.append("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
            if (connection.getTlsPolicy() == Connection.TLSPolicy.required) {
                sb.append("<required/>");
            }
            sb.append("</starttls>");
        }
        // Include available SASL Mechanisms
        sb.append(SASLAuthentication.getSASLMechanisms(session));
        // Include Stream features
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");

        connection.deliverRawText(sb.toString());
        return session;
    }

    public static boolean isAllowed(Connection connection) {
        if (!allowedIPs.isEmpty()) {
            // The server is using a whitelist so check that the IP address of the client
            // is authorized to connect to the server
            boolean forbidAccess = false;
            try {
                if (!allowedIPs.containsKey(connection.getHostAddress())) {
                    byte[] address = connection.getAddress();
                    String range1 = (address[0] & 0xff) + "." + (address[1] & 0xff) + "." +
                            (address[2] & 0xff) +
                            ".*";
                    String range2 = (address[0] & 0xff) + "." + (address[1] & 0xff) + ".*.*";
                    String range3 = (address[0] & 0xff) + ".*.*.*";
                    if (!allowedIPs.containsKey(range1) && !allowedIPs.containsKey(range2) &&
                            !allowedIPs.containsKey(range3)) {
                        forbidAccess = true;
                    }
                }
            } catch (UnknownHostException e) {
                forbidAccess = true;
            }
            return !forbidAccess;
        }
        return true;
    }

    /**
     * Sets the list of IP address that are allowed to connect to the server. If the list is
     * empty then anyone is allowed to connect to the server except for anonymous users that are
     * subject to {@link #getAllowedAnonymIPs()}. This list is used for both anonymous and
     * non-anonymous users.
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
                buf.append(", ").append(iter.next());
            }
            JiveGlobals.setProperty("xmpp.client.login.allowed", buf.toString());
        }
    }

    /**
     * Sets the list of IP address that are allowed to connect to the server for anonymous
     * users. If the list is empty then anonymous will be only restricted by {@link #getAllowedIPs()}.
     *
     * @param allowed the list of IP address that are allowed to connect to the server.
     */
    public static void setAllowedAnonymIPs(Map<String, String> allowed) {
        allowedAnonymIPs = allowed;
        if (allowedAnonymIPs.isEmpty()) {
            JiveGlobals.deleteProperty("xmpp.client.login.allowedAnonym");
        }
        else {
            // Iterate through the elements in the map.
            StringBuilder buf = new StringBuilder();
            Iterator<String> iter = allowedAnonymIPs.keySet().iterator();
            if (iter.hasNext()) {
                buf.append(iter.next());
            }
            while (iter.hasNext()) {
                buf.append(", ").append(iter.next());
            }
            JiveGlobals.setProperty("xmpp.client.login.allowedAnonym", buf.toString());
        }
    }

    /**
     * Returns whether TLS is mandatory, optional or is disabled for clients. When TLS is
     * mandatory clients are required to secure their connections or otherwise their connections
     * will be closed. On the other hand, when TLS is disabled clients are not allowed to secure
     * their connections using TLS. Their connections will be closed if they try to secure the
     * connection. in this last case.
     *
     * @return whether TLS is mandatory, optional or is disabled.
     */
    public static SocketConnection.TLSPolicy getTLSPolicy() {
        // Set the TLS policy stored as a system property
        String policyName = JiveGlobals.getProperty("xmpp.client.tls.policy", Connection.TLSPolicy.optional.toString());
        SocketConnection.TLSPolicy tlsPolicy;
        try {
            tlsPolicy = Connection.TLSPolicy.valueOf(policyName);
        } catch (IllegalArgumentException e) {
            Log.error("Error parsing xmpp.client.tls.policy: " + policyName, e);
            tlsPolicy = Connection.TLSPolicy.optional;
        }
        return tlsPolicy;
    }

    /**
     * Sets whether TLS is mandatory, optional or is disabled for clients. When TLS is
     * mandatory clients are required to secure their connections or otherwise their connections
     * will be closed. On the other hand, when TLS is disabled clients are not allowed to secure
     * their connections using TLS. Their connections will be closed if they try to secure the
     * connection. in this last case.
     *
     * @param policy whether TLS is mandatory, optional or is disabled.
     */
    public static void setTLSPolicy(SocketConnection.TLSPolicy policy) {
        JiveGlobals.setProperty("xmpp.client.tls.policy", policy.toString());
    }

    /**
     * Returns whether compression is optional or is disabled for clients.
     *
     * @return whether compression is optional or is disabled.
     */
    public static SocketConnection.CompressionPolicy getCompressionPolicy() {
        // Set the Compression policy stored as a system property
        String policyName = JiveGlobals
                .getProperty("xmpp.client.compression.policy", Connection.CompressionPolicy.optional.toString());
        SocketConnection.CompressionPolicy compressionPolicy;
        try {
            compressionPolicy = Connection.CompressionPolicy.valueOf(policyName);
        } catch (IllegalArgumentException e) {
            Log.error("Error parsing xmpp.client.compression.policy: " + policyName, e);
            compressionPolicy = Connection.CompressionPolicy.optional;
        }
        return compressionPolicy;
    }

    /**
     * Sets whether compression is optional or is disabled for clients.
     *
     * @param policy whether compression is optional or is disabled.
     */
    public static void setCompressionPolicy(SocketConnection.CompressionPolicy policy) {
        JiveGlobals.setProperty("xmpp.client.compression.policy", policy.toString());
    }

    /**
     * Returns the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @return the Privacy list that overrides the default privacy list.
     */
    public PrivacyList getActiveList() {
        if (activeList != null) {
            try {
                return PrivacyListManager.getInstance().getPrivacyList(getUsername(), activeList);
            } catch (UserNotFoundException e) {
                Log.error(e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Sets the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @param activeList the Privacy list that overrides the default privacy list.
     */
    public void setActiveList(PrivacyList activeList) {
        this.activeList = activeList != null ? activeList.getName() : null;
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
            cache.put(getAddress().toString(), new ClientSessionInfo(this));
        }
    }

    /**
     * Returns the default Privacy list used for the session's user. This list is
     * processed if there is no active list set for the session.
     *
     * @return the default Privacy list used for the session's user.
     */
    public PrivacyList getDefaultList() {
        if (defaultList != null) {
            try {
                return PrivacyListManager.getInstance().getPrivacyList(getUsername(), defaultList);
            } catch (UserNotFoundException e) {
                Log.error(e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Sets the default Privacy list used for the session's user. This list is
     * processed if there is no active list set for the session.
     *
     * @param defaultList the default Privacy list used for the session's user.
     */
    public void setDefaultList(PrivacyList defaultList) {
        // Do nothing if nothing has changed
        if ((this.defaultList == null && defaultList == null) ||
                (defaultList != null && defaultList.getName().equals(this.defaultList))) {
            return;
        }
        this.defaultList = defaultList != null ? defaultList.getName() : null;
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
            cache.put(getAddress().toString(), new ClientSessionInfo(this));
        }
    }

    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param serverName name of the server.
     * @param connection The connection we are proxying.
     * @param streamID unique identifier of this session.
     */
    public LocalClientSession(String serverName, Connection connection, StreamID streamID) {
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
     * @throws org.jivesoftware.openfire.user.UserNotFoundException if a user is not associated with a session
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
     */
    public void setAuthToken(AuthToken auth, String resource) {
        setAddress(new JID(auth.getUsername(), getServerName(), resource));
        authToken = auth;
        setStatus(Session.STATUS_AUTHENTICATED);

        // Set default privacy list for this session
        setDefaultList(PrivacyListManager.getInstance().getDefaultPrivacyList(auth.getUsername()));
        // Add session to the session manager. The session will be added to the routing table as well
        sessionManager.addSession(this);
    }

    /**
     * Initialize the session as an anonymous login. This automatically upgrades the session's
     * status to authenticated and enables many features that are not available until
     * authenticated (obtaining managers for example).<p>
     */
    public void setAnonymousAuth() {
        // Anonymous users have a full JID. Use the random resource as the JID's node
        String resource = getAddress().getResource();
        setAddress(new JID(resource, getServerName(), resource, true));
        setStatus(Session.STATUS_AUTHENTICATED);
        if (authToken == null) {
            authToken = new AuthToken(resource, true);
        }
        // Add session to the session manager. The session will be added to the routing table as well
        sessionManager.addSession(this);
    }

    /**
     * Returns the authentication token associated with this session.
     *
     * @return the authentication token associated with this session (can be null).
     */
    public AuthToken getAuthToken() {
        return authToken;
    }

    public boolean isAnonymousUser() {
        return authToken == null || authToken.isAnonymous();
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
     * Returns true if the session was available ever.
     *
     * @return true if the session was available ever.
     */
    public boolean wasAvailable() {
        return wasAvailable;
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
        if(offlineFloodStopped) {
            return false;
        }
        String username = getAddress().getNode();
        for (ClientSession session : sessionManager.getSessions(username)) {
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
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
            cache.put(getAddress().toString(), new ClientSessionInfo(this));
        }
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
     */
    public void setPresence(Presence presence) {
        Presence oldPresence = this.presence;
        this.presence = presence;
        if (oldPresence.isAvailable() && !this.presence.isAvailable()) {
            // The client is no longer available
            sessionManager.sessionUnavailable(this);
            // Mark that the session is no longer initialized. This means that if the user sends
            // an available presence again the session will be initialized again thus receiving
            // offline messages and offline presence subscription requests
            setInitialized(false);
            // Notify listeners that the session is no longer available
            PresenceEventDispatcher.unavailableSession(this, presence);
        }
        else if (!oldPresence.isAvailable() && this.presence.isAvailable()) {
            // The client is available
            sessionManager.sessionAvailable(this);
            wasAvailable = true;
            // Notify listeners that the session is now available
            PresenceEventDispatcher.availableSession(this, presence);
        }
        else if (this.presence.isAvailable() && oldPresence.getPriority() != this.presence.getPriority())
        {
            // The client has changed the priority of his presence
            sessionManager.changePriority(this, oldPresence.getPriority());
            // Notify listeners that the priority of the session/resource has changed
            PresenceEventDispatcher.presenceChanged(this, presence);
        }
        else if (this.presence.isAvailable()) {
            // Notify listeners that the show or status value of the presence has changed
            PresenceEventDispatcher.presenceChanged(this, presence);
        }
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
            cache.put(getAddress().toString(), new ClientSessionInfo(this));
        }
    }

    public String getAvailableStreamFeatures() {
        // Offer authenticate and registration only if TLS was not required or if required
        // then the connection is already secured
        if (conn.getTlsPolicy() == Connection.TLSPolicy.required && !conn.isSecure()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(200);

        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            sb.append(
                    "<compression xmlns=\"http://jabber.org/features/compress\"><method>zlib</method></compression>");
        }

        if (getAuthToken() == null) {
            // Advertise that the server supports Non-SASL Authentication
            sb.append("<auth xmlns=\"http://jabber.org/features/iq-auth\"/>");
            // Advertise that the server supports In-Band Registration
            if (XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled()) {
                sb.append("<register xmlns=\"http://jabber.org/features/iq-register\"/>");
            }
        }
        else {
            // If the session has been authenticated then offer resource binding
            // and session establishment
            sb.append("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>");
            sb.append("<session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\"/>");
        }
        return sb.toString();
    }

    /**
     * Increments the conflict by one.
     */
    public int incrementConflictCount() {
        conflictCount++;
        return conflictCount;
    }

    /**
     * Returns true if the specified packet must not be blocked based on the active or default
     * privacy list rules. The active list will be tried first. If none was found then the
     * default list is going to be used. If no default list was defined for this user then
     * allow the packet to flow.
     *
     * @param packet the packet to analyze if it must be blocked.
     * @return true if the specified packet must be blocked.
     */
    public boolean canProcess(Packet packet) {
        PrivacyList list = getActiveList();
        if (list != null) {
            // If a privacy list is active then make sure that the packet is not blocked
            return !list.shouldBlockPacket(packet);
        }
        else {
            list = getDefaultList();
            // There is no active list so check if there exists a default list and make
            // sure that the packet is not blocked
            return list == null || !list.shouldBlockPacket(packet);
        }
    }

    void deliver(Packet packet) throws UnauthorizedException {
        if (conn != null && !conn.isClosed()) {
            conn.deliver(packet);
        }
    }

    public String toString() {
        return super.toString() + " presence: " + presence;
    }
}
