/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.ldap;

import com.sun.jndi.ldap.LdapCtxFactory;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.JiveInitialLdapContext;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized administration of LDAP connections. The {@link #getInstance()} method
 * should be used to get an instace. The following properties configure this manager:
 *
 * <ul>
 *      <li>ldap.host</li>
 *      <li>ldap.port</li>
 *      <li>ldap.baseDN</li>
 *      <li>ldap.alternateBaseDN</li>
 *      <li>ldap.adminDN</li>
 *      <li>ldap.adminPassword</li>
 *      <li>ldap.encloseDNs</li>
 *      <li>ldap.usernameField -- default value is "uid".</li>
 *      <li>ldap.usernameSuffix -- default value is "".</li>
 *      <li>ldap.nameField -- default value is "cn".</li>
 *      <li>ldap.emailField -- default value is "mail".</li>
 *      <li>ldap.searchFilter -- the filter used to load the list of users. When defined, it
 *              will be used with the default filter, which is "([usernameField]={0})" where
 *              [usernameField] is the value of ldap.usernameField.
 *      <li>ldap.groupNameField</li>
 *      <li>ldap.groupMemberField</li>
 *      <li>ldap.groupDescriptionField</li>
 *      <li>ldap.posixMode</li>
 *      <li>ldap.groupSearchFilter</li>
 *      <li>ldap.debugEnabled</li>
 *      <li>ldap.sslEnabled</li>
 *      <li>ldap.startTlsEnabled</li>
 *      <li>ldap.autoFollowReferrals</li>
 *      <li>ldap.autoFollowAliasReferrals</li>
 *      <li>ldap.initialContextFactory --  if this value is not specified,
 *          "com.sun.jndi.ldap.LdapCtxFactory" will be used.</li>
 *      <li>ldap.connectionPoolEnabled -- true if an LDAP connection pool should be used.
 *          False if not set.</li>
 * </ul>
 *
 * @author Matt Tucker
 */
public class LdapManager {

    private static final Logger Log = LoggerFactory.getLogger(LdapManager.class);
    // Determine the name of the default LDAP context factory.
    // NOTE: Extracting the name from the class rather than hard coding it allows the compiler to detect the use of this
    // internal class and emit an appropriate warning ("warning: LdapCtxFactory is internal proprietary API and may be removed in a future release")
    // This is deliberate, to highlight use of classes that may be removed in the future.
    private static final String DEFAULT_LDAP_CONTEXT_FACTORY = LdapCtxFactory.class.getName();

    private static LdapManager instance;
    static {
        // Create a special Map implementation to wrap XMLProperties. We only implement
        // the get, put, and remove operations, since those are the only ones used. Using a Map
        // makes it easier to perform LdapManager testing.
        Map<String, String> properties = new Map<String, String>() {

            @Override
            public String get(Object key) {
                return JiveGlobals.getProperty((String)key);
            }

            @Override
            public String put(String key, String value) {
                JiveGlobals.setProperty(key, value);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }

            @Override
            public String remove(Object key) {
                JiveGlobals.deleteProperty((String)key);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }


            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean containsKey(Object key) {
                return false;
            }

            @Override
            public boolean containsValue(Object value) {
                return false;
            }

            @Override
            public void putAll(Map<? extends String, ? extends String> t) {
            }

            @Override
            public void clear() {
            }

            @Override
            public Set<String> keySet() {
                return null;
            }

            @Override
            public Collection<String> values() {
                return null;
            }

            @Override
            public Set<Entry<String, String>> entrySet() {
                return null;
            }
        };
        instance = new LdapManager(properties);
    }


    private Collection<String> hosts = new ArrayList<>();
    private int port;
    private int connTimeout = -1;
    private int readTimeout = -1;
    private String usernameField;
    private String usernameSuffix;
    private String nameField;
    private String emailField;
    private String baseDN;
    private String alternateBaseDN = null;
    private String adminDN = null;
    private String adminPassword;
    private boolean encloseDNs;
    private boolean ldapDebugEnabled = false;
    private boolean sslEnabled = false;
    private String initialContextFactory;
    private boolean followReferrals = false;
    private boolean followAliasReferrals = true;
    private boolean connectionPoolEnabled = true;
    private String searchFilter = null;
    private boolean subTreeSearch;
    private boolean encloseUserDN;
    private boolean encloseGroupDN;
    private boolean startTlsEnabled = false;

    private String groupNameField;
    private String groupMemberField;
    private String groupDescriptionField;
    private boolean posixMode = false;
    private String groupSearchFilter = null;

    private final Map<String, String> properties;

    private Cache<String, DNCacheEntry> userDNCache = null;

    /**
     * Provides singleton access to an instance of the LdapManager class.
     *
     * @return an LdapManager instance.
     */
    public static LdapManager getInstance() {
        return instance;
    }

    /**
     * Constructs a new LdapManager instance. Typically, {@link #getInstance()} should be
     * called instead of this method. LdapManager instances should only be created directly
     * for testing purposes.
     *
     * @param properties the Map that contains properties used by the LDAP manager, such as
     *      LDAP host and base DN.
     */
    public LdapManager(Map<String, String> properties) {
        this.properties = properties;

        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("ldap.host");
        JiveGlobals.migrateProperty("ldap.port");
        JiveGlobals.migrateProperty("ldap.readTimeout");
        JiveGlobals.migrateProperty("ldap.usernameField");
        JiveGlobals.migrateProperty("ldap.usernameSuffix");
        JiveGlobals.migrateProperty("ldap.baseDN");
        JiveGlobals.migrateProperty("ldap.alternateBaseDN");
        JiveGlobals.migrateProperty("ldap.nameField");
        JiveGlobals.migrateProperty("ldap.emailField");
        JiveGlobals.migrateProperty("ldap.connectionPoolEnabled");
        JiveGlobals.migrateProperty("ldap.searchFilter");
        JiveGlobals.migrateProperty("ldap.subTreeSearch");
        JiveGlobals.migrateProperty("ldap.groupNameField");
        JiveGlobals.migrateProperty("ldap.groupMemberField");
        JiveGlobals.migrateProperty("ldap.groupDescriptionField");
        JiveGlobals.migrateProperty("ldap.posixMode");
        JiveGlobals.migrateProperty("ldap.groupSearchFilter");
        JiveGlobals.migrateProperty("ldap.adminDN");
        JiveGlobals.migrateProperty("ldap.adminPassword");
        JiveGlobals.migrateProperty("ldap.debugEnabled");
        JiveGlobals.migrateProperty("ldap.sslEnabled");
        JiveGlobals.migrateProperty("ldap.startTlsEnabled");
        JiveGlobals.migrateProperty("ldap.autoFollowReferrals");
        JiveGlobals.migrateProperty("ldap.autoFollowAliasReferrals");
        JiveGlobals.migrateProperty("ldap.encloseUserDN");
        JiveGlobals.migrateProperty("ldap.encloseGroupDN");
        JiveGlobals.migrateProperty("ldap.encloseDNs");
        JiveGlobals.migrateProperty("ldap.initialContextFactory");
        JiveGlobals.migrateProperty("ldap.pagedResultsSize");
        JiveGlobals.migrateProperty("ldap.clientSideSorting");
        JiveGlobals.migrateProperty("ldap.ldapDebugEnabled");
        JiveGlobals.migrateProperty("ldap.encodeMultibyteCharacters");

        if (JiveGlobals.getBooleanProperty("ldap.userDNCache.enabled", true)) {
            String cacheName = "LDAP UserDN";
            userDNCache = CacheFactory.createCache( cacheName );
        }

        String host = properties.get("ldap.host");
        if (host != null) {
            // Parse the property and check if many hosts were defined. Hosts can be separated
            // by commas or white spaces
            StringTokenizer st = new StringTokenizer(host, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                hosts.add(st.nextToken());
            }
        }
        String portStr = properties.get("ldap.port");
        port = 389;
        if (portStr != null) {
            try {
                this.port = Integer.parseInt(portStr);
            }
            catch (NumberFormatException nfe) {
                Log.error(nfe.getMessage(), nfe);
            }
        }
        String cTimeout = properties.get("ldap.connectionTimeout");
        if (cTimeout != null) {
            try {
                this.connTimeout = Integer.parseInt(cTimeout);
            }
            catch (NumberFormatException nfe) {
                Log.error(nfe.getMessage(), nfe);
            }
        }
        String timeout = properties.get("ldap.readTimeout");
        if (timeout != null) {
            try {
                this.readTimeout = Integer.parseInt(timeout);
            }
            catch (NumberFormatException nfe) {
                Log.error(nfe.getMessage(), nfe);
            }
        }

        usernameField = properties.get("ldap.usernameField");
        if (usernameField == null) {
            usernameField = "uid";
        }
        usernameSuffix = properties.get("ldap.usernameSuffix");
        if (usernameSuffix == null) {
            usernameSuffix = "";
        }

        // are we going to enclose DN values with quotes? (needed when DNs contain non-delimiting commas)
        encloseDNs = true;
        String encloseStr = properties.get("ldap.encloseDNs");
        if (encloseStr != null) {
            encloseDNs = Boolean.valueOf(encloseStr);
        }

        baseDN = properties.get("ldap.baseDN");
        if (baseDN == null) {
            baseDN = "";
        }
        if (encloseDNs) {
           baseDN = getEnclosedDN(baseDN);
        }

        alternateBaseDN = properties.get("ldap.alternateBaseDN");
        if (encloseDNs && alternateBaseDN != null) {
           alternateBaseDN = getEnclosedDN(alternateBaseDN);
        }

        nameField = properties.get("ldap.nameField");
        if (nameField == null) {
            nameField = "cn";
        }
        emailField = properties.get("ldap.emailField");
        if (emailField == null) {
            emailField = "mail";
        }
        connectionPoolEnabled = true;
        String connectionPoolStr = properties.get("ldap.connectionPoolEnabled");
        if (connectionPoolStr != null) {
            connectionPoolEnabled = Boolean.valueOf(connectionPoolStr);
        }
        searchFilter = properties.get("ldap.searchFilter");
        subTreeSearch = true;
        String subTreeStr = properties.get("ldap.subTreeSearch");
        if (subTreeStr != null) {
            subTreeSearch = Boolean.valueOf(subTreeStr);
        }
        groupNameField = properties.get("ldap.groupNameField");
        if (groupNameField == null) {
            groupNameField = "cn";
        }
        groupMemberField = properties.get("ldap.groupMemberField");
        if (groupMemberField ==null) {
            groupMemberField = "member";
        }
        groupDescriptionField = properties.get("ldap.groupDescriptionField");
        if (groupDescriptionField == null) {
            groupDescriptionField = "description";
        }
        posixMode = false;
        String posixStr = properties.get("ldap.posixMode");
        if (posixStr != null) {
            posixMode = Boolean.valueOf(posixStr);
        }
        groupSearchFilter = properties.get("ldap.groupSearchFilter");

        adminDN = properties.get("ldap.adminDN");
        if (adminDN != null && adminDN.trim().equals("")) {
            adminDN = null;
        }
        if (encloseDNs && adminDN != null) {
           adminDN = getEnclosedDN(adminDN);
        }

        adminPassword = properties.get("ldap.adminPassword");
        ldapDebugEnabled = false;
        String ldapDebugStr = properties.get("ldap.debugEnabled");
        if (ldapDebugStr != null) {
            ldapDebugEnabled = Boolean.valueOf(ldapDebugStr);
        }
        sslEnabled = false;
        String sslEnabledStr = properties.get("ldap.sslEnabled");
        if (sslEnabledStr != null) {
            sslEnabled = Boolean.valueOf(sslEnabledStr);
        }
        startTlsEnabled = false;
        String startTlsEnabledStr = properties.get("ldap.startTlsEnabled");
        if (startTlsEnabledStr != null) {
            startTlsEnabled = Boolean.valueOf(startTlsEnabledStr);
        }
        followReferrals = false;
        String followReferralsStr = properties.get("ldap.autoFollowReferrals");
        if (followReferralsStr != null) {
            followReferrals = Boolean.valueOf(followReferralsStr);
        }
        followAliasReferrals = true;
        String followAliasReferralsStr = properties.get("ldap.autoFollowAliasReferrals");
        if (followAliasReferralsStr != null) {
            followAliasReferrals = Boolean.valueOf(followAliasReferralsStr);
        }
        // the following two properties have been deprecated by ldap.encloseDNs.  keeping around for backwards compatibility
        encloseUserDN = true;
        String encloseUserStr = properties.get("ldap.encloseUserDN");
        if (encloseUserStr != null) {
            encloseUserDN = Boolean.valueOf(encloseUserStr) || encloseDNs;
        }
        encloseGroupDN = true;
        String encloseGroupStr = properties.get("ldap.encloseGroupDN");
        if (encloseGroupStr != null) {
            encloseGroupDN = Boolean.valueOf(encloseGroupStr) || encloseDNs;
        }
        this.initialContextFactory = properties.get("ldap.initialContextFactory");
        if (initialContextFactory != null) {
            try {
                Class.forName(initialContextFactory);
            }
            catch (ClassNotFoundException cnfe) {
                Log.error("Initial context factory class failed to load: " + initialContextFactory +
                        ".  Using default initial context factory class instead.");
                initialContextFactory = DEFAULT_LDAP_CONTEXT_FACTORY;
            }
        }
        // Use default value if none was set.
        else {
            initialContextFactory = DEFAULT_LDAP_CONTEXT_FACTORY;
        }

        StringBuilder buf = new StringBuilder();
        buf.append("Created new LdapManager() instance, fields:\n");
        buf.append("\t host: ").append(hosts).append("\n");
        buf.append("\t port: ").append(port).append("\n");
        buf.append("\t usernamefield: ").append(usernameField).append("\n");
        buf.append("\t usernameSuffix: ").append(usernameSuffix).append("\n");
        buf.append("\t baseDN: ").append(baseDN).append("\n");
        buf.append("\t alternateBaseDN: ").append(alternateBaseDN).append("\n");
        buf.append("\t nameField: ").append(nameField).append("\n");
        buf.append("\t emailField: ").append(emailField).append("\n");
        buf.append("\t adminDN: ").append(adminDN).append("\n");
        buf.append("\t adminPassword: ").append(adminPassword).append("\n");
        buf.append("\t searchFilter: ").append(searchFilter).append("\n");
        buf.append("\t subTreeSearch:").append(subTreeSearch).append("\n");
        buf.append("\t ldapDebugEnabled: ").append(ldapDebugEnabled).append("\n");
        buf.append("\t sslEnabled: ").append(sslEnabled).append("\n");
        buf.append("\t startTlsEnabled: ").append(startTlsEnabled).append("\n");
        buf.append("\t initialContextFactory: ").append(initialContextFactory).append("\n");
        buf.append("\t connectionPoolEnabled: ").append(connectionPoolEnabled).append("\n");
        buf.append("\t autoFollowReferrals: ").append(followReferrals).append("\n");
        buf.append("\t autoFollowAliasReferrals: ").append(followAliasReferrals).append("\n");
        buf.append("\t groupNameField: ").append(groupNameField).append("\n");
        buf.append("\t groupMemberField: ").append(groupMemberField).append("\n");
        buf.append("\t groupDescriptionField: ").append(groupDescriptionField).append("\n");
        buf.append("\t posixMode: ").append(posixMode).append("\n");
        buf.append("\t groupSearchFilter: ").append(groupSearchFilter).append("\n");

        if (Log.isDebugEnabled()) {
            Log.debug("LdapManager: "+buf.toString());
        }
        if (ldapDebugEnabled) {
            System.err.println(buf.toString());
        }
    }

    /**
     * Returns a DirContext for the LDAP server that can be used to perform
     * lookups and searches using the default base DN. The alternate DN will be used
     * in case there is a {@link NamingException} using base DN. The context uses the
     * admin login that is defined by <tt>adminDN</tt> and <tt>adminPassword</tt>.
     *
     * @return a connection to the LDAP server.
     * @throws NamingException if there is an error making the LDAP connection.
     */
    public LdapContext getContext() throws NamingException {
        try {
            return getContext(baseDN);
        }
        catch (NamingException e) {
            if (alternateBaseDN != null) {
                return getContext(alternateBaseDN);
            } else {
                throw(e);
            }
        }
    }

    /**
     * Returns a DirContext for the LDAP server that can be used to perform
     * lookups and searches using the specified base DN. The context uses the
     * admin login that is defined by <tt>adminDN</tt> and <tt>adminPassword</tt>.
     *
     * @param baseDN the base DN to use for the context.
     * @return a connection to the LDAP server.
     * @throws NamingException if there is an error making the LDAP connection.
     */
    public LdapContext getContext(String baseDN) throws NamingException {
        boolean debug = Log.isDebugEnabled();
        if (debug) {
            Log.debug("LdapManager: Creating a DirContext in LdapManager.getContext()...");
            if (!sslEnabled && !startTlsEnabled) {
                Log.debug("LdapManager: Warning: Using unencrypted connection to LDAP service!");
            }
        }

        // Set up the environment for creating the initial context
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
        env.put(Context.PROVIDER_URL, getProviderURL(baseDN));

        // SSL
        if (sslEnabled) {
            env.put("java.naming.ldap.factory.socket", "org.jivesoftware.util.SimpleSSLSocketFactory");
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }

        // Use simple authentication to connect as the admin.
        if (adminDN != null) {
            /* If startTLS is requested we MUST NOT bind() before
             * the secure connection has been established. */
            if (!(startTlsEnabled && !sslEnabled)) {
                env.put(Context.SECURITY_AUTHENTICATION, "simple");
                env.put(Context.SECURITY_PRINCIPAL, adminDN);
                if (adminPassword != null) {
                    env.put(Context.SECURITY_CREDENTIALS, adminPassword);
                }
            }
        }
        // No login information so attempt to use anonymous login.
        else {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        }

        if (ldapDebugEnabled) {
            env.put("com.sun.jndi.ldap.trace.ber", System.err);
        }
        if (connectionPoolEnabled) {
            if (!startTlsEnabled) {
                env.put("com.sun.jndi.ldap.connect.pool", "true");
                System.setProperty("com.sun.jndi.ldap.connect.pool.protocol", "plain ssl");
            } else {
                if (debug) {
                    // See http://java.sun.com/products/jndi/tutorial/ldap/connect/pool.html
                    // "When Not to Use Pooling"
                    Log.debug("LdapManager: connection pooling was requested but has been disabled because of StartTLS.");
                }
                env.put("com.sun.jndi.ldap.connect.pool", "false");
            }
        } else {
            env.put("com.sun.jndi.ldap.connect.pool", "false");
        }
        if (connTimeout > 0) {
            env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connTimeout));
        } else {
            env.put("com.sun.jndi.ldap.connect.timeout", "10000");
        }

        if (readTimeout > 0) {
            env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(readTimeout));
        }
        if (followReferrals) {
            env.put(Context.REFERRAL, "follow");
        }
        if (!followAliasReferrals) {
            env.put("java.naming.ldap.derefAliases", "never");
        }

        if (debug) {
            Log.debug("LdapManager: Created hashtable with context values, attempting to create context...");
        }
        // Create new initial context
        JiveInitialLdapContext context = new JiveInitialLdapContext(env, null);

        // TLS http://www.ietf.org/rfc/rfc2830.txt ("1.3.6.1.4.1.1466.20037")
        if (startTlsEnabled && !sslEnabled) {
            if (debug) {
                Log.debug("LdapManager: ... StartTlsRequest");
            }
            if (followReferrals) {
                Log.warn("\tConnections to referrals are unencrypted! If you do not want this, please turn off ldap.autoFollowReferrals");
            }

            // Perform a StartTLS extended operation
            StartTlsResponse tls = (StartTlsResponse)
                context.extendedOperation(new StartTlsRequest());


            /* Open a TLS connection (over the existing LDAP association) and
               get details of the negotiated TLS session: cipher suite,
               peer certificate, etc. */
            try {
                SSLSession session = tls.negotiate(new org.jivesoftware.util.SimpleSSLSocketFactory());

                context.setTlsResponse(tls);
                context.setSslSession(session);

                if (debug) {
                    Log.debug("LdapManager: ... peer host: "
                            + session.getPeerHost()
                            + ", CipherSuite: " + session.getCipherSuite());
                }

                /* Set login credentials only if SSL session has been
                 * negotiated successfully - otherwise user/password
                 * could be transmitted in clear text. */
                if (adminDN != null) {
                    context.addToEnvironment(
                            Context.SECURITY_AUTHENTICATION,
                            "simple");
                    context.addToEnvironment(
                            Context.SECURITY_PRINCIPAL,
                            adminDN);
                    if (adminPassword != null) {
                        context.addToEnvironment(
                                Context.SECURITY_CREDENTIALS,
                                adminPassword);
                    }
                }
            } catch (java.io.IOException ex) {
                Log.error(ex.getMessage(), ex);
            }
        }

        if (debug) {
            Log.debug("LdapManager: ... context created successfully, returning.");
        }

        return context;
    }

    /**
     * Returns true if the user is able to successfully authenticate against
     * the LDAP server. The "simple" authentication protocol is used.
     *
     * @param userDN the user's dn to authenticate (relative to <tt>baseDN</tt>).
     * @param password the user's password.
     * @return true if the user successfully authenticates.
     */
    public boolean checkAuthentication(String userDN, String password) {
        boolean debug = Log.isDebugEnabled();
        if (debug) {
            Log.debug("LdapManager: In LdapManager.checkAuthentication(userDN, password), userDN is: " + userDN + "...");

            if (!sslEnabled && !startTlsEnabled) {
                Log.debug("LdapManager: Warning: Using unencrypted connection to LDAP service!");
            }
        }

        JiveInitialLdapContext ctx = null;
        try {
            // See if the user authenticates.
            Hashtable<String, Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
            env.put(Context.PROVIDER_URL, getProviderURL(baseDN));
            if (sslEnabled) {
                env.put("java.naming.ldap.factory.socket", "org.jivesoftware.util.SimpleSSLSocketFactory");
                env.put(Context.SECURITY_PROTOCOL, "ssl");
            }

            /* If startTLS is requested we MUST NOT bind() before
             * the secure connection has been established. */
            if (!(startTlsEnabled && !sslEnabled)) {
                env.put(Context.SECURITY_AUTHENTICATION, "simple");
                env.put(Context.SECURITY_PRINCIPAL, userDN + "," + baseDN);
                env.put(Context.SECURITY_CREDENTIALS, password);
            } else {
                if (followReferrals) {
                    Log.warn("\tConnections to referrals are unencrypted! If you do not want this, please turn off ldap.autoFollowReferrals");
                }
            }



            if (connTimeout > 0) {
                    env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connTimeout));
                } else {
                    env.put("com.sun.jndi.ldap.connect.timeout", "10000");
                }

            if (readTimeout > 0) {
                env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(readTimeout));
            }
            if (ldapDebugEnabled) {
                env.put("com.sun.jndi.ldap.trace.ber", System.err);
            }
            if (followReferrals) {
                env.put(Context.REFERRAL, "follow");
            }
            if (!followAliasReferrals) {
                env.put("java.naming.ldap.derefAliases", "never");
            }

            if (debug) {
                Log.debug("LdapManager: Created context values, attempting to create context...");
            }
            ctx = new JiveInitialLdapContext(env, null);

            if (startTlsEnabled && !sslEnabled) {

                if (debug) {
                    Log.debug("LdapManager: ... StartTlsRequest");
                }

                // Perform a StartTLS extended operation
                StartTlsResponse tls = (StartTlsResponse)
                    ctx.extendedOperation(new StartTlsRequest());

                /* Open a TLS connection (over the existing LDAP association) and
                   get details of the negotiated TLS session: cipher suite,
                   peer certificate, etc. */
                try {
                    SSLSession session = tls.negotiate(new org.jivesoftware.util.SimpleSSLSocketFactory());

                    ctx.setTlsResponse(tls);
                    ctx.setSslSession(session);

                    if (debug) {
                        Log.debug("LdapManager: ... peer host: "
                                + session.getPeerHost()
                                + ", CipherSuite: " + session.getCipherSuite());
                    }

                    ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                    ctx.addToEnvironment(Context.SECURITY_PRINCIPAL,
                            userDN + "," + baseDN);
                    ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);

                } catch (java.io.IOException ex) {
                    Log.error(ex.getMessage(), ex);
                }

                // make at least one lookup to check authorization
                lookupExistence(
                        ctx,
                        userDN + "," + baseDN,
                        new String[] {usernameField});
            }

            if (debug) {
                Log.debug("LdapManager: ... context created successfully, returning.");
            }
        }
        catch (NamingException ne) {
            // If an alt baseDN is defined, attempt a lookup there.
            if (alternateBaseDN != null) {
                try {
                    if (ctx != null) {
                        ctx.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
                try {
                    // See if the user authenticates.
                    Hashtable<String, Object> env = new Hashtable<>();
                    // Use a custom initial context factory if specified. Otherwise, use the default.
                    env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
                    env.put(Context.PROVIDER_URL, getProviderURL(alternateBaseDN));
                    if (sslEnabled) {
                        env.put("java.naming.ldap.factory.socket", "org.jivesoftware.util.SimpleSSLSocketFactory");
                        env.put(Context.SECURITY_PROTOCOL, "ssl");
                    }

                    /* If startTLS is requested we MUST NOT bind() before
                     * the secure connection has been established. */
                    if (!(startTlsEnabled && !sslEnabled)) {
                        env.put(Context.SECURITY_AUTHENTICATION, "simple");
                        env.put(Context.SECURITY_PRINCIPAL, userDN + "," + alternateBaseDN);
                        env.put(Context.SECURITY_CREDENTIALS, password);
                    }

                        env.put("com.sun.jndi.ldap.connect.timeout", "10000");

                    if (ldapDebugEnabled) {
                        env.put("com.sun.jndi.ldap.trace.ber", System.err);
                    }
                    if (followReferrals) {
                        env.put(Context.REFERRAL, "follow");
                    }
                    if (!followAliasReferrals) {
                        env.put("java.naming.ldap.derefAliases", "never");
                    }
                    if (debug) {
                        Log.debug("LdapManager: Created context values, attempting to create context...");
                    }
                    ctx = new JiveInitialLdapContext(env, null);

                    if (startTlsEnabled && !sslEnabled) {

                        if (debug) {
                            Log.debug("LdapManager: ... StartTlsRequest");
                        }

                        // Perform a StartTLS extended operation
                        StartTlsResponse tls = (StartTlsResponse)
                            ctx.extendedOperation(new StartTlsRequest());

                        /* Open a TLS connection (over the existing LDAP association) and
                           get details of the negotiated TLS session: cipher suite,
                           peer certificate, etc. */
                        try {
                            SSLSession session = tls.negotiate(new org.jivesoftware.util.SimpleSSLSocketFactory());

                            ctx.setTlsResponse(tls);
                            ctx.setSslSession(session);

                            if (debug) {
                                Log.debug("LdapManager: ... peer host: "
                                        + session.getPeerHost()
                                        + ", CipherSuite: " + session.getCipherSuite());
                            }

                            ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                            ctx.addToEnvironment(Context.SECURITY_PRINCIPAL,
                                    userDN + "," + alternateBaseDN);
                            ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);

                        } catch (java.io.IOException ex) {
                            Log.error(ex.getMessage(), ex);
                        }

                        // make at least one lookup to check user authorization
                        lookupExistence(
                                ctx,
                                userDN + "," + alternateBaseDN,
                                new String[] {usernameField});
                    }
                }
                catch (NamingException e) {
                    if (debug) {
                        Log.debug("LdapManager: Caught a naming exception when creating InitialContext", ne);
                    }
                    return false;
                }
            }
            else {
                if (debug) {
                    Log.debug("LdapManager: Caught a naming exception when creating InitialContext", ne);
                }
                return false;
            }
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
        return true;
    }

    /**
     * Looks up an LDAP object by its DN and returns <tt>true</tt> if
     * the search was successful.
     *
     * @param ctx the Context to use for the lookup.
     * @param dn the object's dn to lookup.
     * @return true if the lookup was successful.
     * @throws NamingException if login credentials were wrong.
     */
    private Boolean lookupExistence(InitialDirContext ctx, String dn, String[] returnattrs) throws NamingException {
        boolean debug = Log.isDebugEnabled();

        if (debug) {
            Log.debug("LdapManager: In lookupExistence(ctx, dn, returnattrs), searchdn is: " + dn);
        }

        // Bind to the object's DN
        ctx.addToEnvironment(Context.PROVIDER_URL, getProviderURL(dn));

        String filter = "(&(objectClass=*))";
        SearchControls srcnt = new SearchControls();
        srcnt.setSearchScope(SearchControls.OBJECT_SCOPE);
        srcnt.setReturningAttributes(returnattrs);

        NamingEnumeration<SearchResult> answer = null;

        try {
            answer = ctx.search(
                    "",
                    filter,
                    srcnt);
        } catch (javax.naming.NameNotFoundException nex) {
            // DN not found
        } catch (NamingException ex){
            throw ex;
        }

        if (answer == null || !answer.hasMoreElements())
        {
            Log.debug("LdapManager: .... lookupExistence: DN not found.");
            return false;
        }
        else
        {
            Log.debug("LdapManager: .... lookupExistence: DN found.");
            return true;
        }
    }

    /**
     * Finds a user's dn using their username. Normally, this search will
     * be performed using the field "uid", but this can be changed by setting
     * the <tt>usernameField</tt> property.<p>
     *
     * Searches are performed over all subtrees relative to the <tt>baseDN</tt>.
     * If the search fails in the <tt>baseDN</tt> then another search will be
     * performed in the <tt>alternateBaseDN</tt>. For example, if the <tt>baseDN</tt>
     * is "o=jivesoftware, o=com" and we do a search for "mtucker", then we might
     * find a userDN of "uid=mtucker,ou=People". This kind of searching is a good
     * thing since it doesn't make the assumption that all user records are stored
     * in a flat structure. However, it does add the requirement that "uid" field
     * (or the other field specified) must be unique over the entire subtree from
     * the <tt>baseDN</tt>. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same uid: "uid=mtucker,ou=People" and
     * "uid=mtucker,ou=Administrators". In such a case, it's not possible to
     * uniquely identify a user, so this method will throw an error.<p>
     *
     * The dn that's returned is relative to the default <tt>baseDN</tt>.
     *
     * @param username the username to lookup the dn for.
     * @return the dn associated with <tt>username</tt>.
     * @throws Exception if the search for the dn fails.
     */
    public String findUserDN( String username ) throws Exception
    {
        if ( userDNCache != null )
        {
            // Return a cache entry if one exists.
            final DNCacheEntry dnCacheEntry = userDNCache.get( username );
            if ( dnCacheEntry != null )
            {
                return dnCacheEntry.getUserDN();
            }
        }

        // No cache entry. Query for the value, and add that to the cache.
        try
        {
            final String userDN = findUserDN( username, baseDN );
            if ( userDNCache != null )
            {
                userDNCache.put( username, new DNCacheEntry( userDN, baseDN ) );
            }
            return userDN;
        }
        catch ( Exception e )
        {
            if ( alternateBaseDN != null )
            {
                final String userDN = findUserDN( username, alternateBaseDN );
                if ( userDNCache != null )
                {
                    userDNCache.put( username, new DNCacheEntry( userDN, alternateBaseDN ) );
                }
                return userDN;
            }
            else
            {
                throw e;
            }
        }
    }

    /**
     * Finds a user's dn using their username in the specified baseDN. Normally, this search
     * will be performed using the field "uid", but this can be changed by setting
     * the <tt>usernameField</tt> property.<p>
     *
     * Searches are performed over all sub-trees relative to the <tt>baseDN</tt> unless
     * sub-tree searching has been disabled. For example, if the <tt>baseDN</tt> is
     * "o=jivesoftware, o=com" and we do a search for "mtucker", then we might find a userDN of
     * "uid=mtucker,ou=People". This kind of searching is a good thing since
     * it doesn't make the assumption that all user records are stored in a flat
     * structure. However, it does add the requirement that "uid" field (or the
     * other field specified) must be unique over the entire subtree from the
     * <tt>baseDN</tt>. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same uid: "uid=mtucker,ou=People" and
     * "uid=mtucker,ou=Administrators". In such a case, it's not possible to
     * uniquely identify a user, so this method will throw an error.<p>
     *
     * The DN that's returned is relative to the <tt>baseDN</tt>.
     *
     * @param username the username to lookup the dn for.
     * @param baseDN the base DN to use for this search.
     * @return the dn associated with <tt>username</tt>.
     * @throws Exception if the search for the dn fails.
     * @see #findUserDN(String) to search using the default baseDN and alternateBaseDN.
     */
    public String findUserDN(String username, String baseDN) throws Exception {
        boolean debug = Log.isDebugEnabled();
        //Support for usernameSuffix
        username = username + usernameSuffix;
        if (debug) {
            Log.debug("LdapManager: Trying to find a user's DN based on their username. " + usernameField + ": " + username
                    + ", Base DN: " + baseDN + "...");
        }
        DirContext ctx = null;
        try {
            ctx = getContext(baseDN);
            if (debug) {
                Log.debug("LdapManager: Starting LDAP search...");
            }
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            // If sub-tree searching is enabled (default is true) then search the entire tree.
            if (subTreeSearch) {
                constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            // Otherwise, only search a single level.
            else {
                constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            constraints.setReturningAttributes(new String[] { usernameField });

            // NOTE: this assumes that the username has already been JID-unescaped
            NamingEnumeration<SearchResult> answer = ctx.search("", getSearchFilter(), 
                    new String[] {sanitizeSearchFilter(username)},
                    constraints);

            if (debug) {
                Log.debug("LdapManager: ... search finished");
            }

            if (answer == null || !answer.hasMoreElements()) {
                if (debug) {
                    Log.debug("LdapManager: User DN based on username '" + username + "' not found.");
                }
                throw new UserNotFoundException("Username " + username + " not found");
            }
            String userDN = answer.next().getName();
            // Make sure there are no more search results. If there are, then
            // the username isn't unique on the LDAP server (a perfectly possible
            // scenario since only fully qualified dn's need to be unqiue).
            // There really isn't a way to handle this, so throw an exception.
            // The baseDN must be set correctly so that this doesn't happen.
            if (answer.hasMoreElements()) {
                if (debug) {
                    Log.debug("LdapManager: Search for userDN based on username '" + username + "' found multiple " +
                            "responses, throwing exception.");
                }
                throw new UserNotFoundException("LDAP username lookup for " + username +
                        " matched multiple entries.");
            }
            // Close the enumeration.
            answer.close();
            // All other methods assume that userDN is not a full LDAP string.
            // However if a referal was followed this is not the case.  The
            // following code converts a referral back to a "partial" LDAP string.
            if (userDN.startsWith("ldap://")) {
                userDN = userDN.replace("," + baseDN, "");
                userDN = userDN.substring(userDN.lastIndexOf("/") + 1);
                userDN = java.net.URLDecoder.decode(userDN, "UTF-8");
            }
            if (encloseUserDN) {
                userDN = getEnclosedDN(userDN);
            }
            return userDN;
        } catch (final UserNotFoundException e) {
            Log.trace("LdapManager: UserNotFoundException thrown", e);
            throw e;
        } catch (final Exception e) {
            Log.debug("LdapManager: Exception thrown when searching for userDN based on username '" + username + "'", e);
            throw e;
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) {
                // Ignore.
            }
        }
    }

    /**
     * Finds a groups's dn using it's group name. Normally, this search will
     * be performed using the field "cn", but this can be changed by setting
     * the <tt>groupNameField</tt> property.<p>
     *
     * Searches are performed over all subtrees relative to the <tt>baseDN</tt>.
     * If the search fails in the <tt>baseDN</tt> then another search will be
     * performed in the <tt>alternateBaseDN</tt>. For example, if the <tt>baseDN</tt>
     * is "o=jivesoftware, o=com" and we do a search for "managers", then we might
     * find a groupDN of "uid=managers,ou=Groups". This kind of searching is a good
     * thing since it doesn't make the assumption that all user records are stored
     * in a flat structure. However, it does add the requirement that "cn" field
     * (or the other field specified) must be unique over the entire subtree from
     * the <tt>baseDN</tt>. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same cn: "cn=managers,ou=Financial" and
     * "cn=managers,ou=Engineers". In such a case, it's not possible to
     * uniquely identify a group, so this method will throw an error.<p>
     *
     * The dn that's returned is relative to the default <tt>baseDN</tt>.
     *
     * @param groupname the groupname to lookup the dn for.
     * @return the dn associated with <tt>groupname</tt>.
     * @throws Exception if the search for the dn fails.
     */
    public String findGroupDN(String groupname) throws Exception {
        try {
            return findGroupDN(groupname, baseDN);
        }
        catch (Exception e) {
            if (alternateBaseDN != null) {
                return findGroupDN(groupname, alternateBaseDN);
            }
            else {
                throw e;
            }
        }
    }

    /**
     * Finds a groups's dn using it's group name. Normally, this search will
     * be performed using the field "cn", but this can be changed by setting
     * the <tt>groupNameField</tt> property.<p>
     *
     * Searches are performed over all subtrees relative to the <tt>baseDN</tt>.
     * If the search fails in the <tt>baseDN</tt> then another search will be
     * performed in the <tt>alternateBaseDN</tt>. For example, if the <tt>baseDN</tt>
     * is "o=jivesoftware, o=com" and we do a search for "managers", then we might
     * find a groupDN of "uid=managers,ou=Groups". This kind of searching is a good
     * thing since it doesn't make the assumption that all user records are stored
     * in a flat structure. However, it does add the requirement that "cn" field
     * (or the other field specified) must be unique over the entire subtree from
     * the <tt>baseDN</tt>. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same cn: "cn=managers,ou=Financial" and
     * "cn=managers,ou=Engineers". In such a case, it's not possible to
     * uniquely identify a group, so this method will throw an error.<p>
     *
     * The dn that's returned is relative to the default <tt>baseDN</tt>.
     *
     * @param groupname the groupname to lookup the dn for.
     * @param baseDN the base DN to use for this search.
     * @return the dn associated with <tt>groupname</tt>.
     * @throws Exception if the search for the dn fails.
     * @see #findGroupDN(String) to search using the default baseDN and alternateBaseDN.
     */
    public String findGroupDN(String groupname, String baseDN) throws Exception {
        boolean debug = Log.isDebugEnabled();
        if (debug) {
            Log.debug("LdapManager: Trying to find a groups's DN based on it's groupname. " + groupNameField + ": " + groupname
                    + ", Base DN: " + baseDN + "...");
        }
        DirContext ctx = null;
        try {
            ctx = getContext(baseDN);
            if (debug) {
                Log.debug("LdapManager: Starting LDAP search...");
            }
            // Search for the dn based on the groupname.
            SearchControls constraints = new SearchControls();
            // If sub-tree searching is enabled (default is true) then search the entire tree.
            if (subTreeSearch) {
                constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            // Otherwise, only search a single level.
            else {
                constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            constraints.setReturningAttributes(new String[] { groupNameField });

            String filter = MessageFormat.format(getGroupSearchFilter(), sanitizeSearchFilter(groupname));
            NamingEnumeration<SearchResult> answer = ctx.search("", filter, constraints);

            if (debug) {
                Log.debug("LdapManager: ... search finished");
            }

            if (answer == null || !answer.hasMoreElements()) {
                if (debug) {
                    Log.debug("LdapManager: Group DN based on groupname '" + groupname + "' not found.");
                }
                throw new GroupNotFoundException("Groupname " + groupname + " not found");
            }
            String groupDN = answer.next().getName();
            // Make sure there are no more search results. If there are, then
            // the groupname isn't unique on the LDAP server (a perfectly possible
            // scenario since only fully qualified dn's need to be unqiue).
            // There really isn't a way to handle this, so throw an exception.
            // The baseDN must be set correctly so that this doesn't happen.
            if (answer.hasMoreElements()) {
                if (debug) {
                    Log.debug("LdapManager: Search for groupDN based on groupname '" + groupname + "' found multiple " +
                            "responses, throwing exception.");
                }
                throw new GroupNotFoundException("LDAP groupname lookup for " + groupname +
                        " matched multiple entries.");
            }
            // Close the enumeration.
            answer.close();
            // All other methods assume that groupDN is not a full LDAP string.
            // However if a referal was followed this is not the case.  The
            // following code converts a referral back to a "partial" LDAP string.
            if (groupDN.startsWith("ldap://")) {
                groupDN = groupDN.replace("," + baseDN, "");
                groupDN = groupDN.substring(groupDN.lastIndexOf("/") + 1);
                groupDN = java.net.URLDecoder.decode(groupDN, "UTF-8");
            }
            if (encloseGroupDN) {
                groupDN = getEnclosedDN(groupDN);
            }
            return groupDN;
        } catch (final GroupNotFoundException e) {
            Log.trace("LdapManager: GroupNotFoundException thrown", e);
            throw e;
        } catch (final Exception e) {
            Log.debug("LdapManager: Exception thrown when searching for groupDN based on groupname '" + groupname + "'", e);
            throw e;
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) {
                // Ignore.
            }
        }
    }

    /**
     * Returns a properly encoded URL for use as the PROVIDER_URL.
     * If the encoding fails then the URL will contain the raw base dn.
     *
     * @param baseDN the base dn to use in the URL.
     * @return the properly encoded URL for use in as PROVIDER_URL.
     */
    private String getProviderURL(String baseDN) {
        StringBuffer ldapURL = new StringBuffer();
        try {
            baseDN = URLEncoder.encode(baseDN, "UTF-8");
            // The java.net.URLEncoder class encodes spaces as +, but they need to be %20
            baseDN = baseDN.replaceAll("\\+", "%20");
        }
        catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is not supported, fall back to using raw baseDN
        }
        for (String host : hosts) {
            // Create a correctly-encoded ldap URL for the PROVIDER_URL
            ldapURL.append("ldap://");
            ldapURL.append(host);
            ldapURL.append(':');
            ldapURL.append(port);
            ldapURL.append('/');
            ldapURL.append(baseDN);
            ldapURL.append(' ');
        }
        return ldapURL.toString();
    }

    /**
     * Returns the LDAP servers hosts; e.g. <tt>localhost</tt> or
     * <tt>machine.example.com</tt>, etc. This value is stored as the Jive
     * Property <tt>ldap.host</tt>.
     *
     * @return the LDAP server host name.
     */
    public Collection<String> getHosts() {
        return hosts;
    }

    /**
     * Sets the list of LDAP servers host; e.g., <tt>localhost</tt> or
     * <tt>machine.example.com</tt>, etc. This value is store as the Jive
     * Property <tt>ldap.host</tt> using a comma as a delimiter for each host.<p>
     *
     * Note that all LDAP servers have to share the same configuration.
     *
     * @param hosts the LDAP servers host names.
     */
    public void setHosts(Collection<String> hosts) {
        this.hosts = hosts;
        StringBuilder hostProperty = new StringBuilder();
        for (String host : hosts) {
            hostProperty.append(host).append(',');
        }
        if (!hosts.isEmpty()) {
            // Remove the last comma
            hostProperty.setLength(hostProperty.length()-1);
        }
        properties.put("ldap.host", hostProperty.toString());
    }

    /**
     * Returns the LDAP server port number. The default is 389. This value is
     * stored as the Jive Property <tt>ldap.port</tt>.
     *
     * @return the LDAP server port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the LDAP server port number. The default is 389. This value is
     * stored as the Jive property <tt>ldap.port</tt>.
     *
     * @param port the LDAP server port number.
     */
    public void setPort(int port) {
        this.port = port;
        properties.put("ldap.port", Integer.toString(port));
    }

    /**
     * Returns true if LDAP connection debugging is turned on. When on, trace
     * information about BER buffers sent and received by the LDAP provider is
     * written to System.out. Debugging is turned off by default.
     *
     * @return true if LDAP debugging is turned on.
     */
    public boolean isDebugEnabled() {
        return ldapDebugEnabled;
    }

    /**
     * Sets whether LDAP connection debugging is turned on. When on, trace
     * information about BER buffers sent and received by the LDAP provider is
     * written to System.out. Debugging is turned off by default.
     *
     * @param debugEnabled true if debugging should be turned on.
     */
    public void setDebugEnabled(boolean debugEnabled) {
        this.ldapDebugEnabled = debugEnabled;
        properties.put("ldap.ldapDebugEnabled", Boolean.toString(debugEnabled));
    }

    /**
     * Returns true if LDAP connection is via SSL or not. SSL is turned off by default.
     *
     * @return true if SSL connections are enabled or not.
     */
    public boolean isSslEnabled() {
        return sslEnabled;
    }

    /**
     * Sets whether the connection to the LDAP server should be made via ssl or not.
     *
     * @param sslEnabled true if ssl should be enabled, false otherwise.
     */
    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
        properties.put("ldap.sslEnabled", Boolean.toString(sslEnabled));
    }

    /**
     * Returns true if LDAP connection is via START or not. TLS is turned off by default.
     *
     * @return true if StartTLS connections are enabled or not.
     */
    public boolean isStartTlsEnabled() {
        return startTlsEnabled;
    }

    /**
     * Sets whether the connection to the LDAP server should be made via StartTLS or not.
     *
     * @param startTlsEnabled true if StartTLS should be used, false otherwise.
     */
    public void setStartTlsEnabled(boolean startTlsEnabled) {
        this.startTlsEnabled = startTlsEnabled;
        properties.put("ldap.startTlsEnabled", Boolean.toString(startTlsEnabled));
    }


    /**
     * Returns the LDAP field name that the username lookup will be performed
     * on. By default this is "uid".
     *
     * @return the LDAP field that the username lookup will be performed on.
     */
    public String getUsernameField() {
        return usernameField;
    }

    /**
     * Returns the suffix appended to the username when LDAP lookups are performed.
     * By default this is "".
     *
     * @return the suffix appened to usernames when LDAP lookups are performed.
     */
    public String getUsernameSuffix() {
        return usernameSuffix;
    }

    /**
     * Sets the LDAP field name that the username lookup will be performed on.
     * By default this is "uid".
     *
     * @param usernameField the LDAP field that the username lookup will be
     *      performed on.
     */
    public void setUsernameField(String usernameField) {
        this.usernameField = usernameField;
        if (usernameField == null) {
            properties.remove("ldap.usernameField");
            this.usernameField = "uid";
        }
        else {
            properties.put("ldap.usernameField", usernameField);
        }
    }

    /**
     * Set the suffix appended to the username whenever LDAP lookups are performed.
     *
     * @param usernameSuffix the String to append to usernames for lookups
     */
    public void setUsernameSuffix(String usernameSuffix) {
        this.usernameSuffix = usernameSuffix;
        if (usernameSuffix == null) {
            properties.remove("ldap.usernameSuffix");
            this.usernameSuffix = "";
        }
        else {
            properties.put("ldap.usernameSuffix", usernameSuffix);
        }
    }

    /**
     * Returns the LDAP field name that the user's name is stored in. By default
     * this is "cn". Another common value is "displayName".
     *
     * @return the LDAP field that that corresponds to the user's name.
     */
    public String getNameField() {
        return nameField;
    }

    /**
     * Sets the LDAP field name that the user's name is stored in. By default
     * this is "cn". Another common value is "displayName".
     *
     * @param nameField the LDAP field that that corresponds to the user's name.
     */
    public void setNameField(String nameField) {
        this.nameField = nameField;
        if (nameField == null) {
            properties.remove("ldap.nameField");
        }
        else {
            properties.put("ldap.nameField", nameField);
        }
    }

    /**
     * Returns the LDAP field name that the user's email address is stored in.
     * By default this is "mail".
     *
     * @return the LDAP field that that corresponds to the user's email
     *      address.
     */
    public String getEmailField() {
        return emailField;
    }

    /**
     * Sets the LDAP field name that the user's email address is stored in.
     * By default this is "mail".
     *
     * @param emailField the LDAP field that that corresponds to the user's
     *      email address.
     */
    public void setEmailField(String emailField) {
        this.emailField = emailField;
        if (emailField == null) {
            properties.remove("ldap.emailField");
        }
        else {
            properties.put("ldap.emailField", emailField);
        }
    }

    /**
     * Returns the starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the base DN.
     *
     * @return the starting DN used for performing searches.
     */
    public String getBaseDN() {
        if (encloseDNs) {
            return getEnclosedDN(baseDN);
        } else {
            return baseDN;
        }
    }

    /**
     * Sets the starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the base DN.
     *
     * @param baseDN the starting DN used for performing searches.
     */
    public void setBaseDN(String baseDN) {
        this.baseDN = baseDN;
        properties.put("ldap.baseDN", baseDN);
    }

    /**
     * Returns the alternate starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the alternate base DN after
     * they are performed on the main base DN.
     *
     * @return the alternate starting DN used for performing searches. If no alternate
     *      DN is set, this method will return <tt>null</tt>.
     */
    public String getAlternateBaseDN() {
        return getEnclosedDN(alternateBaseDN);
    }

    /**
     * Sets the alternate starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the alternate base DN after
     * they are performed on the main base dn.
     *
     * @param alternateBaseDN the alternate starting DN used for performing searches.
     */
    public void setAlternateBaseDN(String alternateBaseDN) {
        this.alternateBaseDN = alternateBaseDN;
        if (alternateBaseDN == null) {
            properties.remove("ldap.alternateBaseDN");
        }
        else {
            properties.put("ldap.alternateBaseDN", alternateBaseDN);
        }
    }

    /**
     * Returns the BaseDN for the given username.
     *
     * @param username username to return its base DN.
     * @return the BaseDN for the given username. If no baseDN is found,
     *         this method will return <tt>null</tt>.
     */
    public String getUsersBaseDN( String username )
    {
        if ( userDNCache != null )
        {
            // Return a cache entry if one exists.
            final DNCacheEntry dnCacheEntry = userDNCache.get( username );
            if ( dnCacheEntry != null )
            {
                return dnCacheEntry.getBaseDN();
            }
        }

        // No cache entry. Query for the value, and add that to the cache.
        try
        {
            final String userDN = findUserDN( username, baseDN );
            if ( userDNCache != null )
            {
                userDNCache.put( username, new DNCacheEntry( userDN, baseDN ) );
            }
            return baseDN;
        }
        catch ( Exception e )
        {
            try
            {
                if ( alternateBaseDN != null )
                {
                    final String userDN = findUserDN( username, alternateBaseDN );
                    if ( userDNCache != null )
                    {
                        userDNCache.put( username, new DNCacheEntry( userDN, alternateBaseDN ) );
                    }
                    return alternateBaseDN;
                }
            }
            catch ( Exception ex )
            {
                Log.debug( ex.getMessage(), ex );
            }
        }

        return null;
    }

    /**
     * Returns the BaseDN for the given groupname.
     *
     * @param groupname groupname to return its base DN.
     * @return the BaseDN for the given groupname. If no baseDN is found,
     *         this method will return <tt>null</tt>.
     */
    public String getGroupsBaseDN(String groupname) {
        try {
            findGroupDN(groupname, baseDN);
            return baseDN;
        }
        catch (Exception e) {
            try {
                if (alternateBaseDN != null) {
                    findGroupDN(groupname, alternateBaseDN);
                    return alternateBaseDN;
                }
            }
            catch (Exception ex) {
                Log.debug(ex.getMessage(), ex);
            }
        }
        return null;
    }

    /**
     * Returns the starting admin DN that searches for admins will performed with.
     * Searches will performed on the entire sub-tree under the admin DN.
     *
     * @return the starting DN used for performing searches.
     */
    public String getAdminDN() {
        if (encloseDNs) {
            return getEnclosedDN(adminDN);
        } else {
            return adminDN;
        }
    }

    /**
     * Sets the starting admin DN that searches for admins will performed with.
     * Searches will performed on the entire sub-tree under the admins DN.
     *
     * @param adminDN the starting DN used for performing admin searches.
     */
    public void setAdminDN(String adminDN) {
        this.adminDN = adminDN;
        properties.put("ldap.adminDN", adminDN);
    }

    /**
     * Returns the starting admin DN that searches for admins will performed with.
     * Searches will performed on the entire sub-tree under the admin DN.
     *
     * @return the starting DN used for performing searches.
     */
    public String getAdminPassword() {
        return adminPassword;
    }

    /**
     * Sets the admin password for the LDAP server we're connecting to.
     *
     * @param adminPassword the admin password for the LDAP server we're
     * connecting to.
     */
    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
        properties.put("ldap.adminPassword", adminPassword);
    }

    /**
     * Sets whether an LDAP connection pool should be used or not.
     *
     * @param connectionPoolEnabled true if an LDAP connection pool should be used.
     */
    public void setConnectionPoolEnabled(boolean connectionPoolEnabled) {
        this.connectionPoolEnabled = connectionPoolEnabled;
        properties.put("ldap.connectionPoolEnabled", Boolean.toString(connectionPoolEnabled));
    }

    /**
     * Returns whether an LDAP connection pool should be used or not.
     *
     * @return true if an LDAP connection pool should be used.
     */
    public boolean isConnectionPoolEnabled() {
        return connectionPoolEnabled;
    }

    /**
     * Returns the filter used for searching the directory for users, which includes
     * the default filter (username field search) plus any custom-defined search filter.
     *
     * @return the search filter.
     */
    public String getSearchFilter() {
        StringBuilder filter = new StringBuilder();
        if (searchFilter == null) {
            filter.append('(').append(usernameField).append("={0})");
        }
        else {
            filter.append("(&(").append(usernameField).append("={0})");
            filter.append(searchFilter).append(')');
        }
        return filter.toString();
    }

    /**
     * Sets the search filter appended to the default filter when searching for users.
     *
     * @param searchFilter the search filter appended to the default filter
     *      when searching for users.
     */
    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter;
        properties.put("ldap.searchFilter", searchFilter);
    }

    /**
     * Returns true if the entire tree under the base DN will be searched (recursive search)
     * when doing LDAP queries (finding users, groups, etc). When false, only a single level
     * under the base DN will be searched. The default is <tt>true</tt> which is the best
     * option for most LDAP setups. In only a few cases will the directory be setup in such
     * a way that it's better to do single level searching.
     *
     * @return true if the entire tree under the base DN will be searched.
     */
    public boolean isSubTreeSearch() {
        return subTreeSearch;
    }

    /**
     * Sets whether the entire tree under the base DN will be searched (recursive search)
     * when doing LDAP queries (finding users, groups, etc). When false, only a single level
     * under the base DN will be searched. The default is <tt>true</tt> which is the best
     * option for most LDAP setups. In only a few cases will the directory be setup in such
     * a way that it's better to do single level searching.
     *
     * @param subTreeSearch true if the entire tree under the base DN will be searched.
     */
    public void setSubTreeSearch(boolean subTreeSearch) {
        this.subTreeSearch = subTreeSearch;
        properties.put("ldap.subTreeSearch", String.valueOf(subTreeSearch));
    }

    /**
     * Returns true if LDAP referrals will automatically be followed when found.
     *
     * @return true if LDAP referrals are automatically followed.
     */
    public boolean isFollowReferralsEnabled() {
        return followReferrals;
    }

    /**
     * Sets whether LDAP referrals should be automatically followed.
     *
     * @param followReferrals true if LDAP referrals should be automatically followed.
     */
    public void setFollowReferralsEnabled(boolean followReferrals) {
        this.followReferrals = followReferrals;
        properties.put("ldap.autoFollowReferrals", String.valueOf(followReferrals));
    }

    /**
     * Returns true if LDAP alias referrals will automatically be followed when found.
     *
     * @return true if LDAP alias referrals are automatically followed.
     */
    public boolean isFollowAliasReferralsEnabled() {
        return followAliasReferrals;
    }

    /**
     * Sets whether LDAP alias referrals should be automatically followed.
     *
     * @param followAliasReferrals true if LDAP alias referrals should be automatically followed.
     */
    public void setFollowAliasReferralsEnabled(boolean followAliasReferrals) {
        this.followAliasReferrals = followAliasReferrals;
        properties.put("ldap.autoFollowAliasReferrals", String.valueOf(followAliasReferrals));
    }

    /**
     * Returns the field name used for groups.
     * Value of groupNameField defaults to "cn".
     *
     * @return the field used for groups.
     */
    public String getGroupNameField() {
        return groupNameField;
    }

    /**
     * Sets the field name used for groups.
     *
     * @param groupNameField the field used for groups.
     */
    public void setGroupNameField(String groupNameField) {
        this.groupNameField = groupNameField;
        properties.put("ldap.groupNameField", groupNameField);
    }

    /**
     * Return the field used to list members within a group.
     * Value of groupMemberField defaults to "member".
     *
     * @return the field used to list members within a group.
     */
    public String getGroupMemberField() {
        return groupMemberField;
    }

    /**
     * Sets the field used to list members within a group.
     * Value of groupMemberField defaults to "member".
     *
     * @param groupMemberField the field used to list members within a group.
     */
    public void setGroupMemberField(String groupMemberField) {
        this.groupMemberField = groupMemberField;
        properties.put("ldap.groupMemberField", groupMemberField);
    }

    /**
     * Return the field used to describe a group.
     * Value of groupDescriptionField defaults to "description".
     *
     * @return the field used to describe a group.
     */
    public String getGroupDescriptionField() {
        return groupDescriptionField;
    }

    /**
     * Sets the field used to describe a group.
     * Value of groupDescriptionField defaults to "description".
     *
     * @param groupDescriptionField the field used to describe a group.
     */
    public void setGroupDescriptionField(String groupDescriptionField) {
        this.groupDescriptionField = groupDescriptionField;
        properties.put("ldap.groupDescriptionField", groupDescriptionField);
    }

    /**
     * Return true if the LDAP server is operating in Posix mode. By default
     * false is returned. When in Posix mode, users are stored within a group
     * by their username alone. When not enabled, users are stored in a group using
     * their entire DN.
     *
     * @return true if posix mode is being used by the LDAP server.
     */
    public boolean isPosixMode() {
        return posixMode;
    }

    /**
     * Sets whether the LDAP server is operating in Posix mode. When in Posix mode,
     * users are stored within a group by their username alone. When not enabled,
     * users are stored in a group using their entire DN.
     *
     * @param posixMode true if posix mode is being used by the LDAP server.
     */
    public void setPosixMode(boolean posixMode) {
        this.posixMode = posixMode;
        properties.put("ldap.posixMode", String.valueOf(posixMode));
    }

    /**
     * Returns the filter used for searching the directory for groups, which includes
     * the default filter plus any custom-defined search filter.
     *
     * @return the search filter when searching for groups.
     */
    public String getGroupSearchFilter() {
        StringBuilder groupFilter = new StringBuilder();
        if (groupSearchFilter == null) {
            groupFilter.append('(').append(groupNameField).append("={0})");
        }
        else {
            groupFilter.append("(&(").append(groupNameField).append("={0})");
            groupFilter.append(groupSearchFilter).append(')');
        }
        return groupFilter.toString();
    }

    /**
     * Sets the search filter appended to the default filter when searching for groups.
     *
     * @param groupSearchFilter the search filter appended to the default filter
     *      when searching for groups.
     */
    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
        properties.put("ldap.groupSearchFilter", groupSearchFilter);
    }

    public boolean isEnclosingDNs() {
        String encloseStr = properties.get("ldap.encloseDNs");
        if (encloseStr != null) {
            encloseDNs = Boolean.valueOf(encloseStr);
        } else {
            encloseDNs = true;
        }

        return encloseDNs;
    }

    public void setIsEnclosingDNs(boolean enable) {
        this.encloseDNs = enable;
        properties.put("ldap.encloseDNs", Boolean.toString(enable));
    }

    /**
     * Generic routine for retrieving a list of results from the LDAP server.  It's meant to be very
     * flexible so that just about any query for a list of results can make use of it without having
     * to reimplement their own calls to LDAP.  This routine also accounts for sorting settings,
     * paging settings, any other global settings, and alternate DNs.
     *
     * The passed in filter string needs to be pre-prepared!  In other words, nothing will be changed
     * in the string before it is used as a string.
     *
     * @param attribute LDAP attribute to be pulled from each result and placed in the return results.
     *     Typically pulled from this manager.
     * @param searchFilter Filter to use to perform the search.  Typically pulled from this manager.
     * @param startIndex Number/index of first result to include in results.  (-1 for no limit)
     * @param numResults Number of results to include.  (-1 for no limit)
     * @param suffixToTrim An arbitrary string to trim from the end of every attribute returned.  null to disable.
     * @return A simple list of strings (that should be sorted) of the results.
     */
    public List<String> retrieveList(String attribute, String searchFilter, int startIndex, int numResults, String suffixToTrim) {
        return retrieveList(attribute, searchFilter, startIndex, numResults, suffixToTrim, false);
    }

    /**
     * Generic routine for retrieving a list of results from the LDAP server.  It's meant to be very
     * flexible so that just about any query for a list of results can make use of it without having
     * to reimplement their own calls to LDAP.  This routine also accounts for sorting settings,
     * paging settings, any other global settings, and alternate DNs.
     *
     * The passed in filter string needs to be pre-prepared!  In other words, nothing will be changed
     * in the string before it is used as a string.
     *
     * @param attribute LDAP attribute to be pulled from each result and placed in the return results.
     *     Typically pulled from this manager.
     * @param searchFilter Filter to use to perform the search.  Typically pulled from this manager.
     * @param startIndex Number/index of first result to include in results.  (-1 for no limit)
     * @param numResults Number of results to include.  (-1 for no limit)
     * @param suffixToTrim An arbitrary string to trim from the end of every attribute returned.  null to disable.
     * @param escapeJIDs Use JID-escaping for returned results (e.g. usernames)
     * @return A simple list of strings (that should be sorted) of the results.
     */
    public List<String> retrieveList(String attribute, String searchFilter, int startIndex, int numResults, String suffixToTrim, boolean escapeJIDs) {
        List<String> results = new ArrayList<>();
        int pageSize = -1;
        String pageSizeStr = properties.get("ldap.pagedResultsSize");
        if (pageSizeStr != null)
        {
            try {
                 pageSize = Integer.parseInt(pageSizeStr); /* radix -1 is invalid */
            }
            catch (NumberFormatException e) {
                // poorly formatted number, ignoring
            }
        }
        Boolean clientSideSort = false;
        String clientSideSortStr = properties.get("ldap.clientSideSorting");
        if (clientSideSortStr != null) {
            clientSideSort = Boolean.valueOf(clientSideSortStr);
        }
        LdapContext ctx = null;
        LdapContext ctx2 = null;
        try {
            ctx = getContext(baseDN);

            // Set up request controls, if appropriate.
            List<Control> baseTmpRequestControls = new ArrayList<>();
            if (!clientSideSort) {
                // Server side sort on username field.
                baseTmpRequestControls.add(new SortControl(new String[]{attribute}, Control.NONCRITICAL));
            }
            if (pageSize > 0) {
                // Server side paging.
                baseTmpRequestControls.add(new PagedResultsControl(pageSize, Control.NONCRITICAL));
            }
            Control[] baseRequestControls = baseTmpRequestControls.toArray(new Control[baseTmpRequestControls.size()]);
            ctx.setRequestControls(baseRequestControls);

            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { attribute });
            // If server side sort, we'll skip the initial ones we don't want, and stop when we've hit
            // the amount we do want.
            int skip = -1;
            int lastRes = -1;
            if (!clientSideSort) {
                if (startIndex != -1) {
                    skip = startIndex;
                }
                if (numResults != -1) {
                    lastRes = startIndex + numResults;
                }
            }
            byte[] cookie;
            int count = 0;
            // Run through all pages of results (one page is also possible  ;)  )
            do {
                cookie = null;
                NamingEnumeration<SearchResult> answer = ctx.search("", searchFilter, searchControls);

                // Examine all of the results on this page
                while (answer.hasMoreElements()) {
                    count++;
                    if (skip > 0 && count <= skip) {
                        answer.next();
                        continue;
                    }
                    if (lastRes != -1 && count > lastRes) {
                        answer.next();
                        break;
                    }

                    // Get the next result.
                    String result = (String)answer.next().getAttributes().get(attribute).get();
                    // Remove suffixToTrim if set
                    if (suffixToTrim != null && suffixToTrim.length() > 0 && result.endsWith(suffixToTrim)) {
                        result = result.substring(0,result.length()-suffixToTrim.length());
                    }
                    // Add this to the result.
                    results.add(escapeJIDs ? JID.escapeNode(result) : result);
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();
                        }
                    }
                }
                // Close the enumeration.
                answer.close();
                // Re-activate paged results; affects nothing if no paging support
                List<Control> tmpRequestControls = new ArrayList<>();
                if (!clientSideSort) {
                    // Server side sort on username field.
                    tmpRequestControls.add(new SortControl(new String[]{attribute}, Control.NONCRITICAL));
                }
                if (pageSize > 0) {
                    // Server side paging.
                    tmpRequestControls.add(new PagedResultsControl(pageSize, cookie, Control.CRITICAL));
                }
                Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
                ctx.setRequestControls(requestControls);
            } while (cookie != null && (lastRes == -1 || count <= lastRes));

            // Add groups found in alternate DN
            if (alternateBaseDN != null && (lastRes == -1 || count <= lastRes)) {
                ctx2 = getContext(alternateBaseDN);
                ctx2.setRequestControls(baseRequestControls);

                // Run through all pages of results (one page is also possible  ;)  )
                do {
                    cookie = null;
                    NamingEnumeration<SearchResult> answer = ctx2.search("", searchFilter, searchControls);

                    // Examine all of the results on this page
                    while (answer.hasMoreElements()) {
                        count++;
                        if (skip > 0 && count <= skip) {
                            answer.next();
                            continue;
                        }
                        if (lastRes != -1 && count > lastRes) {
                            answer.next();
                            break;
                        }

                        // Get the next result.
                        String result = (String)answer.next().getAttributes().get(attribute).get();
                        // Remove suffixToTrim if set
                        if (suffixToTrim != null && suffixToTrim.length() > 0 && result.endsWith(suffixToTrim)) {
                            result = result.substring(0,result.length()-suffixToTrim.length());
                        }
                        // Add this to the result.
                        results.add(escapeJIDs ? JID.escapeNode(result) : result);
                    }
                    // Examine the paged results control response
                    Control[] controls = ctx2.getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            if (control instanceof PagedResultsResponseControl) {
                                PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                                cookie = prrc.getCookie();
                            }
                        }
                    }
                    // Close the enumeration.
                    answer.close();
                    // Re-activate paged results; affects nothing if no paging support
                    List<Control> tmpRequestControls = new ArrayList<>();
                    if (!clientSideSort) {
                        // Server side sort on username field.
                        tmpRequestControls.add(new SortControl(new String[]{attribute}, Control.NONCRITICAL));
                    }
                    if (pageSize > 0) {
                        // Server side paging.
                        tmpRequestControls.add(new PagedResultsControl(pageSize, cookie, Control.CRITICAL));
                    }
                    Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
                    ctx2.setRequestControls(requestControls);
                } while (cookie != null && (lastRes == -1 || count <= lastRes));
            }

            // If client-side sorting is enabled, sort and trim.
            if (clientSideSort) {
                Collections.sort(results);
                if (startIndex != -1 || numResults != -1) {
                    if (startIndex == -1) {
                        startIndex = 0;
                    }
                    if (numResults == -1) {
                        numResults = results.size();
                    }
                    int endIndex = Math.min(startIndex + numResults, results.size()-1);
                    results = results.subList(startIndex, endIndex);
                }
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
                if (ctx2 != null) {
                    ctx2.setRequestControls(null);
                    ctx2.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return results;
    }

    /**
     * Generic routine for retrieving the number of available results from the LDAP server that
     * match the passed search filter.  This routine also accounts for paging settings and
     * alternate DNs.
     *
     * The passed in filter string needs to be pre-prepared!  In other words, nothing will be changed
     * in the string before it is used as a string.
     *
     * @param attribute LDAP attribute to be pulled from each result and used in the query.
     *     Typically pulled from this manager.
     * @param searchFilter Filter to use to perform the search.  Typically pulled from this manager.
     * @return The number of entries that match the filter.
     */
    public Integer retrieveListCount(String attribute, String searchFilter) {
        int pageSize = -1;
        String pageSizeStr = properties.get("ldap.pagedResultsSize");
        if (pageSizeStr != null) {
            try {
                pageSize = Integer.parseInt(pageSizeStr); /* radix -1 is invalid */
           }
           catch (NumberFormatException e) {
               // poorly formatted number, ignoring
           }
        }
        LdapContext ctx = null;
        LdapContext ctx2 = null;
        Integer count = 0;
        try {
            ctx = getContext(baseDN);

            // Set up request controls, if appropriate.
            List<Control> baseTmpRequestControls = new ArrayList<>();
            if (pageSize > 0) {
                // Server side paging.
                baseTmpRequestControls.add(new PagedResultsControl(pageSize, Control.NONCRITICAL));
            }
            Control[] baseRequestControls = baseTmpRequestControls.toArray(new Control[baseTmpRequestControls.size()]);
            ctx.setRequestControls(baseRequestControls);

            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { attribute });
            byte[] cookie;
            // Run through all pages of results (one page is also possible  ;)  )
            do {
                cookie = null;
                NamingEnumeration<SearchResult> answer = ctx.search("", searchFilter, searchControls);

                // Examine all of the results on this page
                while (answer.hasMoreElements()) {
                    answer.next();
                    count++;
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();
                        }
                    }
                }
                // Close the enumeration.
                answer.close();
                // Re-activate paged results; affects nothing if no paging support
                List<Control> tmpRequestControls = new ArrayList<>();
                if (pageSize > 0) {
                    // Server side paging.
                    tmpRequestControls.add(new PagedResultsControl(pageSize, cookie, Control.CRITICAL));
                }
                Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
                ctx.setRequestControls(requestControls);
            } while (cookie != null);

            // Add groups found in alternate DN
            if (alternateBaseDN != null) {
                ctx2 = getContext(alternateBaseDN);
                ctx2.setRequestControls(baseRequestControls);

                // Run through all pages of results (one page is also possible  ;)  )
                do {
                    cookie = null;
                    NamingEnumeration<SearchResult> answer = ctx2.search("", searchFilter, searchControls);

                    // Examine all of the results on this page
                    while (answer.hasMoreElements()) {
                        answer.next();
                        count++;
                    }
                    // Examine the paged results control response
                    Control[] controls = ctx2.getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            if (control instanceof PagedResultsResponseControl) {
                                PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                                cookie = prrc.getCookie();
                            }
                        }
                    }
                    // Close the enumeration.
                    answer.close();
                    // Re-activate paged results; affects nothing if no paging support
                    List<Control> tmpRequestControls = new ArrayList<>();
                    if (pageSize > 0) {
                        // Server side paging.
                        tmpRequestControls.add(new PagedResultsControl(pageSize, cookie, Control.CRITICAL));
                    }
                    Control[] requestControls = tmpRequestControls.toArray(new Control[tmpRequestControls.size()]);
                    ctx2.setRequestControls(requestControls);
                } while (cookie != null);
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
                if (ctx2 != null) {
                    ctx2.setRequestControls(null);
                    ctx2.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return count;
    }
    
    /**
     * Escapes any special chars (RFC 4515) from a string representing
     * a search filter assertion value.
     *
     * @param value The input string.
     *
     * @return A assertion value string ready for insertion into a 
     *         search filter string.
     */
    public static String sanitizeSearchFilter(final String value) {
      return sanitizeSearchFilter(value, false);
      
    }
      
    /**
     * Escapes any special chars (RFC 4515) from a string representing
     * a search filter assertion value, with the exception of the '*' wildcard sign
     *
     * @param value The input string.
     *
     * @return A assertion value string ready for insertion into a 
     *         search filter string.
     */
    public static String sanitizeSearchFilter(final String value, boolean acceptWildcard ) {


            StringBuilder result = new StringBuilder();

            for (int i=0; i< value.length(); i++) {

                char c = value.charAt(i);

                switch(c) {
                    case '!':		result.append("\\21");	break;
                    case '&':		result.append("\\26");	break;
                    case '(':		result.append("\\28");	break;
                    case ')':		result.append("\\29");	break;
                    case '*':		result.append(acceptWildcard ? "*" : "\\2a");	break;
                    case ':':		result.append("\\3a");	break;
                    case '\\':		result.append("\\5c");	break;
                    case '|':		result.append("\\7c");	break;
                    case '~':		result.append("\\7e");	break;
                    case '\u0000':	result.append("\\00");	break;
                default:
                    if (c <= 0x7f) {
                        // regular 1-byte UTF-8 char
                        result.append(String.valueOf(c));
                    }
                    else if (c >= 0x080) {
                        // higher-order 2, 3 and 4-byte UTF-8 chars
                        if ( JiveGlobals.getBooleanProperty( "ldap.encodeMultibyteCharacters", false ) )
                        {
                            byte[] utf8bytes = String.valueOf( c ).getBytes( StandardCharsets.UTF_8 );
                            for ( byte b : utf8bytes )
                            {
                                result.append( String.format( "\\%02x", b ) );
                            }
                        }
                        else
                        {
                            result.append(String.valueOf(c));
                        }
                    }
                }
            }
            return result.toString();
    }
    

    /**
     * Encloses DN values with "
     *
     * @param dnValue the unenclosed value of a DN (e.g. ou=Jive Software\, Inc,dc=support,dc=jive,dc=com)
     * @return String the enclosed value of the DN (e.g. ou="Jive Software\, Inc",dc="support",dc="jive",dc="com")
     */
    public static String getEnclosedDN(String dnValue) {
        if (dnValue == null || dnValue.equals("")) {
            return dnValue;
        }

        if (dnPattern == null) {
            dnPattern = Pattern.compile("([^\\\\]=)([^\"]*?[^\\\\])(,|$)");
        }

        Matcher matcher = dnPattern.matcher(dnValue);
        dnValue = matcher.replaceAll("$1\"$2\"$3");
        dnValue = dnValue.replace("\\,", ",");

        return dnValue;
    }

    // Set the pattern to use to wrap DN values with "
    private static Pattern dnPattern;

    private static class DNCacheEntry
    {
        private final String userDN;
        private final String baseDN;

        public DNCacheEntry( String userDN, String baseDN )
        {
            this.userDN = userDN;
            this.baseDN = baseDN;
        }

        public String getUserDN()
        {
            return userDN;
        }

        public String getBaseDN()
        {
            return baseDN;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }

            DNCacheEntry that = (DNCacheEntry) o;

            if ( userDN != null ? !userDN.equals( that.userDN ) : that.userDN != null )
            {
                return false;
            }
            return baseDN != null ? baseDN.equals( that.baseDN ) : that.baseDN == null;
        }

        @Override
        public int hashCode()
        {
            int result = userDN != null ? userDN.hashCode() : 0;
            result = 31 * result + ( baseDN != null ? baseDN.hashCode() : 0 );
            return result;
        }
    }
}
