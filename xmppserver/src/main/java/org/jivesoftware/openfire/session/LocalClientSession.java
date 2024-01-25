/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.*;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.csi.CsiManager;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.nio.XMLLightweightParser;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.openfire.user.PresenceEventDispatcher;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.time.Duration;
import java.util.*;

/**
 * Represents a session between the server and a client.
 *
 * @author Gaston Dombiak
 */
public class LocalClientSession extends LocalSession implements ClientSession {

    private static final Logger Log = LoggerFactory.getLogger(LocalClientSession.class);

    private static final String ETHERX_NAMESPACE = "http://etherx.jabber.org/streams";

    /**
     * Keep the list of IP address that are allowed to connect to the server.
     *
     * If the list is  empty then anyone is allowed to connect to the server, unless the IP is on the blacklist (which
     * always takes precedence over the whitelist).
     *
     * Note: the values in this list can be hostnames, IP addresses or IP ranges (with wildcards).
     */
    private static Set<String> allowedIPs = new HashSet<>();
    private static Set<String> allowedAnonymIPs = new HashSet<>();

    /**
     * Similar to {@link #allowedIPs}, but used for blacklisting rather than whitelisting.
     */
    private static Set<String> blockedIPs = new HashSet<>();

    private boolean messageCarbonsEnabled;

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

    private Presence presence;

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

    /**
     * Defines whether a XEP-0191 blocklist was requested by the client of this session.
     */
    private boolean hasRequestedBlocklist = false;

    /**
     * XEP-0352 Client State Indication Manager
     */
    protected final CsiManager csiManager;

    static {
        // Fill out the allowedIPs with the system property
        String allowed = JiveGlobals.getProperty(ConnectionSettings.Client.LOGIN_ALLOWED, "");
        StringTokenizer tokens = new StringTokenizer(allowed, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            allowedIPs.add( address );
        }
        String allowedAnonym = JiveGlobals.getProperty(ConnectionSettings.Client.LOGIN_ANONYM_ALLOWED, "");
        tokens = new StringTokenizer(allowedAnonym, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            allowedAnonymIPs.add(address);
        }
        String blocked = JiveGlobals.getProperty(ConnectionSettings.Client.LOGIN_BLOCKED, "");
        tokens = new StringTokenizer(blocked, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            blockedIPs.add( address );
        }
    }

    /**
     * Returns the list of IP address that are allowed to connect to the server. If the list is empty then anyone is
     * allowed to connect to the server except for anonymous users that are subject to
     * {@link #getWhitelistedAnonymousIPs()}. This list is used for both anonymous and non-anonymous users.
     *
     * Note that the blacklist in {@link #getBlacklistedIPs()} should take precedence!
     *
     * @return the collection of IP address that are allowed to connect to the server. Never null, possibly empty.
     */
    public static Set<String> getWhitelistedIPs() { return allowedIPs; }

    /**
     * Returns the list of IP address that are allowed to connect to the server for anonymous users. If the list is
     * empty then anonymous will be only restricted by {@link #getWhitelistedIPs()}.
     *
     * Note that the blacklist in {@link #getBlacklistedIPs()} should take precedence!
     *
     * @return the collection of IP address that are allowed to connect to the server. Never null, possibly empty.
     */
    public static Set<String> getWhitelistedAnonymousIPs() {
        return allowedAnonymIPs;
    }

    /**
     * Returns the list of IP address that are disallowed to connect to the server. If the list is empty then anyone is
     * allowed to connect to the server, subject to whitelisting. This list is used for both anonymous and
     * non-anonymous users.
     *
     * @return the collection of IP address that are not allowed to connect to the server. Never null, possibly empty.
     */
    public static Set<String> getBlacklistedIPs() { return blockedIPs; }

    /**
     * Returns a newly created session between the server and a client. The session will
     * be created and returned only if correct name/prefix ('stream')
     * and namespace were provided by the client.
     *
     * @param serverName the name of the server where the session is connecting to.
     * @param xpp the parser that is reading the provided XML through the connection.
     * @param connection the connection with the client.
     * @return a newly created session between the server and a client.
     * @throws org.xmlpull.v1.XmlPullParserException if an error occurs while parsing incoming data.
     */
    public static LocalClientSession createSession(String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException
    {
        // Conduct error checking, the opening tag should be 'stream' in the 'etherx' namespace
        if (!xpp.getName().equals("stream")) {
            throw new XmlPullParserException(LocaleUtils.getLocalizedString("admin.error.bad-stream"));
        }

        if (!xpp.getNamespace(xpp.getPrefix()).equals(ETHERX_NAMESPACE)) {
            throw new XmlPullParserException(LocaleUtils.getLocalizedString("admin.error.bad-namespace"));
        }

        if (!isAllowed(connection))
        {
            // Client cannot connect from this IP address so end the stream and TCP connection.
            String hostAddress = "Unknown";
            try {
                hostAddress = connection.getHostAddress();
            } catch (UnknownHostException e) {
                // Do nothing
            }

            Log.debug("LocalClientSession: Closed connection to client attempting to connect from: {}", hostAddress);
            // Include the not-authorized error in the response and close the underlying connection.
            connection.close(new StreamError(StreamError.Condition.not_authorized));
            return null;
        }

        // Retrieve list of namespaces declared in current element (OF-2556)
        connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(xpp));

        final Locale language = Session.detectLanguage(xpp);
        final int[] version = Session.detectVersion(xpp);
        int majorVersion = version[0];
        int minorVersion = version[1];

        connection.setXMPPVersion(majorVersion, minorVersion);

        boolean hasCertificates = false;
        try {
            hasCertificates = !connection.getConfiguration().getIdentityStore().getAllCertificates().isEmpty();
        }
        catch (Exception e) {
            Log.error("Unable to load find any content in the identity store. This connection won't be able to support TLS.", e);
        }

        if (!hasCertificates && connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required) {
            Log.error("Client session rejected. TLS is required but no certificates were created.");
            return null;
        }

        // Create a ClientSession for this user.
        LocalClientSession session = SessionManager.getInstance().createClientSession(connection, language);

        // Build the start packet response
        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(Namespace.get("jabber:client"));
        stream.addAttribute("from", serverName);
        stream.addAttribute("id", session.getStreamID().toString());
        stream.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), language.toLanguageTag());
        // Don't include version info if the version is 0.0.
        if (majorVersion != 0) {
            stream.addAttribute("version", majorVersion + "." + minorVersion);
        }

        // If this is a "Jabber" connection, the session is now initialized - do not include features but return to allow normal packet parsing.
        if (majorVersion != 0)
        {
            // Otherwise, this is at least XMPP 1.0 so we need to announce stream features.
            final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
            document.getRootElement().add(features);

            try {
                if (!connection.isEncrypted() && connection.getConfiguration().getTlsPolicy() != Connection.TLSPolicy.disabled && !connection.getConfiguration().getIdentityStore().getAllCertificates().isEmpty()) {
                    final Element starttls = DocumentHelper.createElement(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
                    if (connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required) {
                        starttls.addElement("required");
                    }
                    features.add(starttls);
                }
                if (!ConnectionSettings.Client.STREAM_LIMITS_ADVERTISEMENT_DISABLED.getValue()) {
                    final Element limits = DocumentHelper.createElement(QName.get("limits", "urn:xmpp:stream-limits:0"));
                    limits.addElement("max-bytes").addText(String.valueOf(XMLLightweightParser.XMPP_PARSER_BUFFER_SIZE.getValue()));
                    final Duration timeout = ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getValue();
                    if (!timeout.isNegative() && !timeout.isZero()) {
                        limits.addElement("idle-seconds").addText(String.valueOf(timeout.toSeconds()));
                    }
                    features.add(limits);
                }
            } catch (KeyStoreException e) {
                Log.warn("Unable to access the identity store for client connections. StartTLS is not being offered as a feature for this session.", e);
            }
            // Include available SASL Mechanisms
            features.add(SASLAuthentication.getSASLMechanisms(session));
            // Include Stream features
            final List<Element> specificFeatures = session.getAvailableStreamFeatures();
            if (specificFeatures != null) {
                for (final Element feature : specificFeatures) {
                    features.add(feature);
                }
            }
        }

        connection.deliverRawText(StringUtils.asUnclosedStream(document));
        return session;
    }

    public static boolean isAllowed( Connection connection )
    {
        try
        {
            final String hostAddress = connection.getHostAddress();
            final byte[] address = connection.getAddress();

            // Blacklist takes precedence over whitelist.
            if ( blockedIPs.contains( hostAddress ) || isAddressInRange( address, blockedIPs ) ) {
                return false;
            }

            // When there's a whitelist (not empty), you must be on it to be allowed.
            return allowedIPs.isEmpty() || allowedIPs.contains( hostAddress ) || isAddressInRange( address, allowedIPs );
        }
        catch ( UnknownHostException e )
        {
            return false;
        }
    }

    public static boolean isAllowedAnonymous( Connection connection )
    {
        try
        {
            final String hostAddress = connection.getHostAddress();
            final byte[] address = connection.getAddress();

            // Blacklist takes precedence over whitelist.
            if ( blockedIPs.contains( hostAddress ) || isAddressInRange( address, blockedIPs ) ) {
                return false;
            }

            // When there's a whitelist (not empty), you must be on it to be allowed.
            return allowedAnonymIPs.isEmpty() || allowedAnonymIPs.contains( hostAddress ) || isAddressInRange( address, allowedAnonymIPs );
        }
        catch ( UnknownHostException e )
        {
            return false;
        }
    }

    // TODO Add IPv6 support (OF-2785)
    public static boolean isAddressInRange( byte[] address, Set<String> ranges ) {
        final String range0 = (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff);
        final String range1 = (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + ".*";
        final String range2 = (address[0] & 0xff) + "." + (address[1] & 0xff) + ".*.*";
        final String range3 = (address[0] & 0xff) + ".*.*.*";
        return ranges.contains(range0) || ranges.contains(range1) || ranges.contains(range2) || ranges.contains(range3);
    }

    /**
     * Sets the list of IP address that are allowed to connect to the server. If the list is empty then anyone not on
     * {@link #getBlacklistedIPs()} is  allowed to connect to the server except for anonymous users that are subject to
     * {@link #getWhitelistedAnonymousIPs()}. This list is used for both anonymous and non-anonymous users.
     *
     * Note that blacklisting takes precedence over whitelisting: if an address is matched by both, access is denied.
     *
     * @param allowed the list of IP address that are allowed to connect to the server. Can be empty, but not null.
     */
    public static void setWhitelistedIPs(Set<String> allowed) {
        if (allowed == null) {
            throw new NullPointerException();
        }
        allowedIPs = allowed;
        if (allowedIPs.isEmpty()) {
            JiveGlobals.deleteProperty(ConnectionSettings.Client.LOGIN_ALLOWED);
        }
        else {
            // Iterate through the elements in the map.
            StringBuilder buf = new StringBuilder();
            Iterator<String> iter = allowedIPs.iterator();
            if (iter.hasNext()) {
                buf.append(iter.next());
            }
            while (iter.hasNext()) {
                buf.append(", ").append(iter.next());
            }
            JiveGlobals.setProperty(ConnectionSettings.Client.LOGIN_ALLOWED, buf.toString());
        }
    }

    /**
     * Sets the list of IP address that are allowed to connect to the server for anonymous users. If the list is empty
     * then anonymous will be only restricted by {@link #getBlacklistedIPs()} and {@link #getWhitelistedIPs()}.
     *
     * @param allowed the list of IP address that are allowed to connect to the server. Can be empty, but not null.
     */
    public static void setWhitelistedAnonymousIPs(Set<String> allowed) {
        if (allowed == null) {
            throw new NullPointerException();
        }
        allowedAnonymIPs = allowed;
        if (allowedAnonymIPs.isEmpty()) {
            JiveGlobals.deleteProperty(ConnectionSettings.Client.LOGIN_ANONYM_ALLOWED);
        }
        else {
            // Iterate through the elements in the map.
            StringBuilder buf = new StringBuilder();
            Iterator<String> iter = allowedAnonymIPs.iterator();
            if (iter.hasNext()) {
                buf.append(iter.next());
            }
            while (iter.hasNext()) {
                buf.append(", ").append(iter.next());
            }
            JiveGlobals.setProperty(ConnectionSettings.Client.LOGIN_ANONYM_ALLOWED, buf.toString());
        }
    }

    /**
     * Sets the list of IP address that are not allowed to connect to the server. This list is used for both anonymous
     * and non-anonymous users, and always takes precedence over a whitelist.
     *
     * @param blocked the list of IP address that are not allowed to connect to the server. Can be empty, but not null.
     */
    public static void setBlacklistedIPs(Set<String> blocked) {
        if (blocked == null) {
            throw new NullPointerException();
        }
        blockedIPs = blocked;
        if (blockedIPs.isEmpty()) {
            JiveGlobals.deleteProperty(ConnectionSettings.Client.LOGIN_BLOCKED);
        }
        else {
            // Iterate through the elements in the map.
            StringBuilder buf = new StringBuilder();
            Iterator<String> iter = blocked.iterator();
            if (iter.hasNext()) {
                buf.append(iter.next());
            }
            while (iter.hasNext()) {
                buf.append(", ").append(iter.next());
            }
            JiveGlobals.setProperty(ConnectionSettings.Client.LOGIN_BLOCKED, buf.toString());
        }
    }

    /**
     * Returns the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @return the Privacy list that overrides the default privacy list.
     */
    @Override
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
    @Override
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
    @Override
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
    @Override
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
     * @param language the language
     */
    public LocalClientSession(String serverName, Connection connection, StreamID streamID, Locale language) {
        super(serverName, connection, streamID, language);
        csiManager = new CsiManager(this);
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
    @Override
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
        final JID jid;
        if (auth.isAnonymous()) {
            jid = new JID(resource, getServerName(), resource);
        } else {
            jid = new JID(auth.getUsername(), getServerName(), resource);
        }
        setAddress(jid);
        authToken = auth;
        setStatus(Session.Status.AUTHENTICATED);

        // Set default privacy list for this session
        if (!auth.isAnonymous()) {
            setDefaultList( PrivacyListManager.getInstance().getDefaultPrivacyList( auth.getUsername() ) );
        }
        // Add session to the session manager. The session will be added to the routing table as well
        sessionManager.addSession(this);
    }

    /**
     * Initialize the session as an anonymous login. This automatically upgrades the session's
     * status to authenticated and enables many features that are not available until
     * authenticated (obtaining managers for example).
     */
    public void setAnonymousAuth() {
        // Anonymous users have a full JID. Use the random resource as the JID's node
        String resource = getAddress().getResource();
        setAddress(new JID(resource, getServerName(), resource, true));
        setStatus(Session.Status.AUTHENTICATED);
        authToken = AuthToken.generateAnonymousToken();
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

    @Override
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
    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Sets the initialization state of the session.
     *
     * @param isInit True if the session has been initialized
     * @see #isInitialized
     */
    @Override
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
     * @see <a href="http://www.xmpp.org/extensions/xep-0160.html">XEP-0160: Best Practices for Handling Offline Messages</a>
     */
    @Override
    public boolean canFloodOfflineMessages() {
        // XEP-0160: When the recipient next sends non-negative available presence to the server, the server delivers the message to the resource that has sent that presence.
        if(offlineFloodStopped || presence.getPriority() < 0) {
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
    @Override
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

    public void reattach(LocalSession connectionProvider, long h)
    {
        super.reattach(connectionProvider, h);

        // XEP-0352: "After a previous stream was resumed using mechanisms like Stream Management (XEP-0198), the CSI
        // state is not restored. That is, stream resumption does not affect the current CSI state, which always
        // defaults to 'active' for new and resumed streams. Clients wishing to immediately go to the inactive state
        // should do so after stream resumption."
        csiManager.activate();
    }

    /**
     * Returns the Client State Indication manager for this session.
     *
     * @return A Client State Indication manager
     */
    public CsiManager getCsiManager() {
        return csiManager;
    }

    /**
     * Obtain the presence of this session.
     *
     * @return The presence of this session or null if not authenticated
     */
    @Override
    public Presence getPresence() {
        return presence;
    }

    /**
     * Set the presence of this session
     *
     * @param presence The presence for the session
     */
    @Override
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
            sessionManager.sessionAvailable(this, presence);
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

    @Override
    public List<Element> getAvailableStreamFeatures() {
        // Offer authenticate and registration only if TLS was not required or if required
        // then the connection is already encrypted
        if (conn.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required && !conn.isEncrypted()) {
            return Collections.emptyList();
        }

        final List<Element> result = new LinkedList<>();

        // Include Stream Compression Mechanism
        if (conn.getConfiguration().getCompressionPolicy() != Connection.CompressionPolicy.disabled && !conn.isCompressed()) {
            final Element compression = DocumentHelper.createElement(QName.get("compression", "http://jabber.org/features/compress"));
            compression.addElement("method").addText("zlib");
            result.add(compression);
        }

        // If a server supports roster versioning, 
        // then it MUST advertise the following stream feature during stream negotiation.
        if (RosterManager.isRosterVersioningEnabled()) {
            result.add(DocumentHelper.createElement(QName.get("ver", "urn:xmpp:features:rosterver")));
        }

        if (getAuthToken() == null) {
            // Advertise that the server supports Non-SASL Authentication
            if ( XMPPServer.getInstance().getIQRouter().supports( "jabber:iq:auth" ) ) {
                result.add(DocumentHelper.createElement(QName.get("auth", "http://jabber.org/features/iq-auth")));
            }
            // Advertise that the server supports In-Band Registration
            if (XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled()) {
                result.add(DocumentHelper.createElement(QName.get("register", "http://jabber.org/features/iq-register")));
            }
        }
        else {
            // If the session has been authenticated then offer resource binding,
            // and session establishment
            result.add(DocumentHelper.createElement(QName.get("bind", "urn:ietf:params:xml:ns:xmpp-bind")));
            final Element session = DocumentHelper.createElement(QName.get("session", "urn:ietf:params:xml:ns:xmpp-session"));
            session.addElement("optional");
            result.add(session);

            // Offer XEP-0198 stream management capabilities if enabled.
            if(StreamManager.isStreamManagementActive()) {
                result.add(DocumentHelper.createElement(QName.get("sm", StreamManager.NAMESPACE_V2)));
                result.add(DocumentHelper.createElement(QName.get("sm", StreamManager.NAMESPACE_V3)));
            }

            // Offer XEP-0352 Client State Indication capabilities if enabled
            if (CsiManager.ENABLED.getValue()) {
                result.add(DocumentHelper.createElement(QName.get("csi", CsiManager.NAMESPACE)));
            }
         }

        // Add XEP-0115 entity capabilities for the server, so that peer can skip service discovery.
        final String ver = EntityCapabilitiesManager.getLocalDomainVerHash();
        if ( ver != null ) {
            final Element c = DocumentHelper.createElement(QName.get("c", "http://jabber.org/protocol/caps"));
            c.addAttribute("hash", "sha-1");
            c.addAttribute("node", EntityCapabilitiesManager.OPENFIRE_IDENTIFIER_NODE);
            c.addAttribute("ver", ver);
            result.add(c);
        }

        if (!ConnectionSettings.Client.STREAM_LIMITS_ADVERTISEMENT_DISABLED.getValue()) {
            final Element limits = DocumentHelper.createElement(QName.get("limits", "urn:xmpp:stream-limits:0"));
            limits.addElement("max-bytes").addText(String.valueOf(XMLLightweightParser.XMPP_PARSER_BUFFER_SIZE.getValue()));
            final Duration timeout = ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getValue();
            if (!timeout.isNegative() && !timeout.isZero()) {
                limits.addElement("idle-seconds").addText(String.valueOf(timeout.toSeconds()));
            }
            result.add(limits);
        }

        return result;
    }

    /**
     * Increments the conflict by one.
     */
    @Override
    public int incrementConflictCount() {
        conflictCount++;
        return conflictCount;
    }

    @Override
    public boolean isMessageCarbonsEnabled() {
        return messageCarbonsEnabled;
    }

    @Override
    public void setMessageCarbonsEnabled(boolean enabled) {
        messageCarbonsEnabled = enabled;
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
            cache.put(getAddress().toString(), new ClientSessionInfo(this));
        }
    }

    @Override
    public boolean hasRequestedBlocklist() {
        return hasRequestedBlocklist;
    }

    @Override
    public void setHasRequestedBlocklist(boolean hasRequestedBlocklist) {
        this.hasRequestedBlocklist = hasRequestedBlocklist;
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
            cache.put(getAddress().toString(), new ClientSessionInfo(this));
        }
    }

    /**
     * Returns true if the specified packet must not be blocked based on the active or default
     * privacy list rules. The active list will be tried first. If none was found then the
     * default list is going to be used. If no default list was defined for this user then
     * allow the packet to flow.
     *
     * @param packet the packet to analyze if it must be blocked.
     * @return true if the specified packet must *not* be blocked.
     */
    @Override
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

    @Override
    public void deliver(Packet queueOrPushStanza) throws UnauthorizedException {
        // Queue this stanza, possibly returning it immediately in line with any previously queued stanzas if this
        // stanza needs to be pushed to the client immediately.
        final List<Packet> stanzasToPush = csiManager.queueOrPush(queueOrPushStanza);

        if (stanzasToPush.isEmpty()) {
            return;
        }
        synchronized (streamManager)
        {
            // Push stanzas to the client.
            for (final Packet stanzaToPush : stanzasToPush) {
                if (conn != null) {
                    conn.deliver(stanzaToPush);
                }
                streamManager.sentStanza(stanzaToPush);
            }
        }
    }

    @Override
    public String toString()
    {
        String peerAddress = "(not available)";
        if (getConnection() != null) {
            try {
                peerAddress = getConnection().getHostAddress();
            } catch (UnknownHostException e) {
                Log.debug("Unable to determine address for peer of local client session.", e);
            }
        }
        return this.getClass().getSimpleName() +"{" +
            "address=" + address +
            ", streamID=" + streamID +
            ", status=" + status +
            ", isEncrypted=" + isEncrypted() +
            ", isDetached=" + isDetached() +
            ", serverName='" + serverName + '\'' +
            ", isInitialized=" + initialized +
            ", hasAuthToken=" + (authToken != null) +
            ", peer address='" + peerAddress +'\'' +
            ", presence='" + presence.toString() + '\'' +
            '}';
    }
}
