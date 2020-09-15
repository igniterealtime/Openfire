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

import org.jivesoftware.admin.LdapUserTester;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.JiveInitialLdapContext;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.Rdn;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSession;

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
 *      <li>ldap.flattenNestedGroups</li>
 *      <li>ldap.debugEnabled</li>
 *      <li>ldap.sslEnabled</li>
 *      <li>ldap.startTlsEnabled</li>
 *      <li>ldap.autoFollowReferrals</li>
 *      <li>ldap.autoFollowAliasReferrals</li>
 *      <li>ldap.initialContextFactory --  if this value is not specified,
 *          "com.sun.jndi.ldap.LdapCtxFactory" will be used.</li>
 *      <li>ldap.connectionPoolEnabled -- true if an LDAP connection pool should be used.
 *          False if not set.</li>
 *      <li>ldap.findUsersFromGroupsEnabled</li> -- If true then Openfire users will be identified from the members
 *      of Openfire groups instead of from the list of all users in LDAP. This option is only useful if you wish to
 *      restrict the users of Openfire to those in certain groups. Normally this is done by applying an appropriate
 *      ldap.searchFilter, but there are a number of reasons why you may wish to enable this option instead:
 *      <ul>
 *          <li>If group members cannot be identified by the attributes of the user in LDAP (typically the memberOf
 *          attribute) then users cannot be filtered using ldap.searchFilter</li>
 *          <li>If the number of Openfire users is small compared to the total number of users in LDAP
 *          then it may be more performant to identify these users from the groups to which they belong instead
 *          of applying an ldap.searchFilter. Note that if this is not the case, enabling this option may significantly
 *          decrease performance.</li>
 *      </ul>
 *      In any case, an appropriate ldap.groupSearchFilter should be applied to prevent LDAP users belonging to
 *      <i>any</i> group being selected as Openfire users.
 *      (default value: false)
 * </ul>
 *
 * @author Matt Tucker
 */
public class LdapManager {

    private static final Logger Log = LoggerFactory.getLogger(LdapManager.class);
    private static final String DEFAULT_LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    public static final SystemProperty<Integer> LDAP_PAGE_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("ldap.pagedResultsSize")
        .setDefaultValue(-1)
        .setDynamic(true)
        .build();

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

    /** Exposed for test use only */
    public static void setInstance(LdapManager instance) {
        LdapManager.instance = instance;
    }

    private Collection<String> hosts = new ArrayList<>();
    private int port;
    private int connTimeout = -1;
    private int readTimeout = -1;
    private String usernameField;
    private String usernameSuffix;
    private LdapUserTester.PropertyMapping nameField;
    private String emailField;
    private LdapName baseDN;
    private LdapName alternateBaseDN;
    private String adminDN;
    private String adminPassword;
    private boolean ldapDebugEnabled;
    private boolean sslEnabled;
    private String initialContextFactory;
    private boolean followReferrals;
    private boolean followAliasReferrals;
    private boolean connectionPoolEnabled;
    private String searchFilter;
    private boolean subTreeSearch;
    private boolean startTlsEnabled;
    private final boolean findUsersFromGroupsEnabled;

    private String groupNameField;
    private String groupMemberField;
    private String groupDescriptionField;
    private boolean posixMode;
    private String groupSearchFilter;
    private boolean flattenNestedGroups;

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
        JiveGlobals.migrateProperty("ldap.flattenNestedGroups");
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
                Log.error("Unable to parse 'ldap.port' value as a number.", nfe);
            }
        }
        String cTimeout = properties.get("ldap.connectionTimeout");
        if (cTimeout != null) {
            try {
                this.connTimeout = Integer.parseInt(cTimeout);
            }
            catch (NumberFormatException nfe) {
                Log.error("Unable to parse 'ldap.connectionTimeout' value as a number.", nfe);
            }
        }
        String timeout = properties.get("ldap.readTimeout");
        if (timeout != null) {
            try {
                this.readTimeout = Integer.parseInt(timeout);
            }
            catch (NumberFormatException nfe) {
                Log.error("Unable to parse 'ldap.readTimeout' value as a number.", nfe);
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

        baseDN = parseAsLdapNameOrLog( properties.get("ldap.baseDN") );

        alternateBaseDN = parseAsLdapNameOrLog( properties.get("ldap.alternateBaseDN") );

        String nameFieldValue = properties.get("ldap.nameField");
        if (nameFieldValue == null) {
            nameFieldValue = "cn";
        }

        nameField = new LdapUserTester.PropertyMapping(nameFieldValue);

        emailField = properties.get("ldap.emailField");
        if (emailField == null) {
            emailField = "mail";
        }
        connectionPoolEnabled = StringUtils.parseBoolean(properties.get("ldap.connectionPoolEnabled"))
            .orElse(Boolean.TRUE);
        searchFilter = properties.get("ldap.searchFilter");
        subTreeSearch = StringUtils.parseBoolean(properties.get("ldap.subTreeSearch"))
            .orElse(Boolean.TRUE);
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
        posixMode = StringUtils.parseBoolean(properties.get("ldap.posixMode"))
            .orElse(Boolean.FALSE);
        groupSearchFilter = properties.get("ldap.groupSearchFilter");

        flattenNestedGroups = false;
        String flattenNestedGroupsStr = properties.get("ldap.flattenNestedGroups");
        if (flattenNestedGroupsStr != null) {
            flattenNestedGroups = Boolean.parseBoolean(flattenNestedGroupsStr);
        }

        adminDN = properties.get("ldap.adminDN");
        if (adminDN != null && adminDN.trim().equals("")) {
            adminDN = null;
        }

        adminPassword = properties.get("ldap.adminPassword");
        ldapDebugEnabled = StringUtils.parseBoolean(properties.get("ldap.debugEnabled"))
            .orElse(Boolean.FALSE);
        sslEnabled = StringUtils.parseBoolean(properties.get("ldap.sslEnabled"))
            .orElse(Boolean.TRUE);
        startTlsEnabled = StringUtils.parseBoolean(properties.get("ldap.startTlsEnabled"))
            .orElse(Boolean.FALSE);
        followReferrals = StringUtils.parseBoolean(properties.get("ldap.autoFollowReferrals"))
            .orElse(Boolean.FALSE);
        followAliasReferrals = StringUtils.parseBoolean(properties.get("ldap.autoFollowAliasReferrals"))
            .orElse(Boolean.TRUE);

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
        this.findUsersFromGroupsEnabled = Boolean.parseBoolean(properties.get("ldap.findUsersFromGroupsEnabled"));

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
        buf.append("\t adminPassword: ").append("************").append("\n");
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
        buf.append("\t flattenNestedGroups: ").append(flattenNestedGroups).append("\n");
        buf.append("\t findUsersFromGroupsEnabled: ").append(findUsersFromGroupsEnabled).append("\n");

        if (Log.isDebugEnabled()) {
            Log.debug(""+buf.toString());
        }
        if (ldapDebugEnabled) {
            System.err.println(buf.toString());
        }
    }

    /**
     * Splits a string formatted as an LDAP filter, such as <code>(&(part-a)(part-b)(part-c))</code>, in separate parts.
     * When the provided input cannot be parsed as an LDAP filter, the returned collection contains one element: the
     * original input.
     *
     * @param input The value to be split.
     * @return The splitted value.
     */
    public static List<String> splitFilter( final String input )
    {
        final List<String> result = new ArrayList<>();
        if ( input.length() >= 5 && input.startsWith("(") && input.endsWith("))") && (input.charAt(1) == '&' || input.charAt(1) == '|') && input.charAt(2) == '(' )
        {
            // Strip off the outer parenthesis and search operator.
            String stripped = input.substring(2, input.length() - 1);

            // The remainder should consist only of ()-surrounded parts.
            // We'll remove the leading '(' and trailing ')' character, then split on ")(" to get all parts.
            stripped = stripped.substring(1, stripped.length() - 1);

            final String[] split = stripped.split("\\)\\(");
            result.addAll(Arrays.asList(split));
        }
        else
        {
            result.add(input);
        }

        return result;
    }

    /**
     * Joins individual strings into one, formatted as an LDAP filter, such as <code>(&(part-a)(part-b)(part-c))</code>.
     *
     * @param operator the second character of the resulting string.
     * @param parts    The parts to be joined into one string.
     * @return The joined string value.
     */
    public static String joinFilter( char operator, List<String> parts )
    {
        final StringBuilder result = new StringBuilder();
        result.append('(').append(operator);
        for ( final String part : parts )
        {
            result.append('(').append(part).append(')');
        }
        result.append(')');
        return result.toString();
    }

    /**
     * Attempts to parse a string value as an LdapName.
     *
     * This method returns null (and logs an error) if the value was non-null
     * and non-empty and the parsing fails.
     *
     * This method returns null if the provided value was null or empty.
     *
     * @param value The value to be parsed (can be null or empty).
     * @return The parsed value, possibly null.
     */
    public LdapName parseAsLdapNameOrLog( String value )
    {
        LdapName result = null;
        if ( value != null && !value.isEmpty() )
        {
            try
            {
                return new LdapName( value );
            }
            catch ( InvalidNameException ex )
            {
                Log.error( "Unable to parse LDAPvalue '{}'.", value, ex );
            }
        }
        return result;
    }

    /**
     * Returns a RDN from the search result answer. The return value consists of
     * one or more Rdn instances, that, when combined, are relative to the base
     * DN that was used for the search.
     *
     * @param answer The result of the search (cannot be null).
     * @return A relative distinguished name from the answer.
     * @throws NamingException When the search result value cannot be used to form a valid RDN value.
     */
    public static Rdn[] getRelativeDNFromResult( SearchResult answer ) throws NamingException
    {
        // All other methods assume that UserDN is a relative distinguished name,
        // not a (full) distinguished name. However if a referral was followed,
        // a DN instead of a RDN value is returned. The following code converts
        // a referral back to a RDN (previously referred to as a "partial" LDAP
        // string).
        if (!answer.isRelative()) {
            final LdapName dn = new LdapName(( answer.getName() ));
            final List<Rdn> rdns = dn.getRdns();
            return new Rdn[] { rdns.get( rdns.size() -1) };
        }

        // Occasionally, the name is returned as: "cn=ship crew/cooks" (a string
        // that starts with a quote). This likely occurs when the value contains
        // characters that are invalid when not escaped.
        //
        // Quotes around the entire name cannot be parsed (unlike, for example:
        // cn="ship crew/cooks", where the value component, rather than the
        // entire name, is put in quotes).
        //
        // When quotes are detected, this method will strip them, and apply
        // instead escaping according to the rules specified in
        // <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>. This is
        // designed to prevent quotes, or otherwise escaped values from showing
        // in Openfire user and group names.
        Log.debug("Processing relative LDAP SearchResult: '{}'", answer);
        String name = answer.getName();
        boolean needsEscaping = false;
        if ( name.startsWith("\"") && name.endsWith("\"") )
        {
            // Strip off the leading and trailing quote.
            Log.debug("SearchResult was quote-wrapped: '{}'", name);
            name = name.substring(1, name.length()-1 );
            needsEscaping = true;
        }

        if (!needsEscaping)
        {
            // The search result contains a JNDI composite name. From this, a DN needs to be extracted before it can be processed further.
            // See https://bugs.openjdk.java.net/browse/JDK-6201615?focusedCommentId=12344311&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-12344311
            final Name cname = new CompositeName(name);
            name = cname.get(0);
        }

        // Split the search result, which is a relative distinguished name, into separate Rdn values, using a regular expression.
        //
        // This regex is designed to:
        // - split the input string on a comma character,
        // - except for when the comma is escaped (preceded by a \). The regex uses a negative look-behind to detect this case.
        // - The comma needs to be followed by one or more letters, which themselves need to be followed by an equal sign,
        //   which must be followed by at least one other character.
        final String[] rdns = name.split("(?<![\\\\]),(?=[a-zA-z]+=.+)");

        final List<Rdn> result = new ArrayList<>();
        for (String rdn : rdns)
        {
            // If the entire value was in quotes, each individual RDN needs escaping.
            if ( needsEscaping )
            {
                // Escape the value (but first split off the type, to prevent the '=' character from being escaped).
                final String[] typeValue = rdn.split("=",2);
                if (typeValue.length != 2) {
                    Log.warn("Unexpected value while parsing a RDN: '{}'.", name);
                } else {
                    name = typeValue[0] + "=" + Rdn.escapeValue(typeValue[1]);
                    result.add( new Rdn(name) );
                }
            }
            else
            {
                result.add(new Rdn(rdn));
            }
        }

        return result.toArray(new Rdn[0]);
    }

    /**
     * Returns a DirContext for the LDAP server that can be used to perform
     * lookups and searches using the default base DN. The alternate DN will be used
     * in case there is a {@link NamingException} using base DN. The context uses the
     * admin login that is defined by {@code adminDN} and {@code adminPassword}.
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
     * admin login that is defined by {@code adminDN} and {@code adminPassword}.
     *
     * @param baseDN the base DN to use for the context.
     * @return a connection to the LDAP server.
     * @throws NamingException if there is an error making the LDAP connection.
     */
    public LdapContext getContext(LdapName baseDN) throws NamingException {
        Log.debug("Creating a DirContext in LdapManager.getContext() for baseDN '{}'...", baseDN);
        if (!sslEnabled && !startTlsEnabled) {
            Log.warn("Using unencrypted connection to LDAP service!");
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
                // See http://java.sun.com/products/jndi/tutorial/ldap/connect/pool.html "When Not to Use Pooling"
                Log.debug("connection pooling was requested but has been disabled because of StartTLS.");
                env.put("com.sun.jndi.ldap.connect.pool", "false");
            }
        } else {
            env.put("com.sun.jndi.ldap.connect.pool", "false");
        }
        if (connTimeout > 0) {
            env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connTimeout));
        } else {
            env.put("com.sun.jndi.ldap.connect.timeout", "4000");
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

        Log.debug("Created hashtable with context values, attempting to create context...");

        // Create new initial context
        JiveInitialLdapContext context = new JiveInitialLdapContext(env, null);

        // TLS http://www.ietf.org/rfc/rfc2830.txt ("1.3.6.1.4.1.1466.20037")
        if (startTlsEnabled && !sslEnabled) {
            Log.debug("... StartTlsRequest");
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

                Log.debug("... peer host: {}, CipherSuite: {}", session.getPeerHost(), session.getCipherSuite());

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
                Log.error("An exception occurred while trying to create a context for baseDN {}", baseDN, ex);
            }
        }

        Log.debug("... context created successfully, returning.");

        return context;
    }

    /**
     * Returns true if the user is able to successfully authenticate against
     * the LDAP server. The "simple" authentication protocol is used.
     *
     * @param userRDN the user's rdn to authenticate (relative to {@code baseDN}).
     * @param password the user's password.
     * @return true if the user successfully authenticates.
     */
    public boolean checkAuthentication(Rdn[] userRDN, String password) {
        Log.debug("In LdapManager.checkAuthentication(userDN, password), userRDN is: " + Arrays.toString(userRDN) + "...");

        if (!sslEnabled && !startTlsEnabled) {
            Log.warn("Using unencrypted connection to LDAP service!");
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
                env.put(Context.SECURITY_PRINCIPAL, createNewAbsolute( baseDN, userRDN ).toString() );
                env.put(Context.SECURITY_CREDENTIALS, password);
            } else {
                if (followReferrals) {
                    Log.warn("\tConnections to referrals are unencrypted! If you do not want this, please turn off ldap.autoFollowReferrals");
                }
            }

            if (connTimeout > 0) {
                    env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connTimeout));
                } else {
                    env.put("com.sun.jndi.ldap.connect.timeout", "4000");
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

            Log.debug("Created context values, attempting to create context...");
            ctx = new JiveInitialLdapContext(env, null);

            if (startTlsEnabled && !sslEnabled) {

                Log.debug("... StartTlsRequest");

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

                    Log.debug("... peer host: {}, CipherSuite: {}", session.getPeerHost(), session.getCipherSuite());

                    ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                    ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, createNewAbsolute(baseDN, userRDN).toString());
                    ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);

                } catch (java.io.IOException ex) {
                    Log.error("Unable to upgrade connection to TLS; failing user authentication", ex);
                    return false;
                }

                // make at least one lookup to check authorization
                lookupExistence(
                        ctx,
                        createNewAbsolute( baseDN, userRDN ),
                        new String[] {usernameField});
            }

            Log.debug("... context created successfully, returning.");
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
                    Log.error("An exception occurred while trying to authenticate against the alternate baseDN.", e);
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
                        env.put(Context.SECURITY_PRINCIPAL, createNewAbsolute(alternateBaseDN, userRDN).toString());
                        env.put(Context.SECURITY_CREDENTIALS, password);
                    }

                    if (connTimeout > 0) {
                        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connTimeout));
                    } else {
                        env.put("com.sun.jndi.ldap.connect.timeout", "4000");
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
                    Log.debug("Created context values, attempting to create context...");
                    ctx = new JiveInitialLdapContext(env, null);

                    if (startTlsEnabled && !sslEnabled) {
                        Log.debug("... StartTlsRequest");

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

                            Log.debug("... peer host: {}, CipherSuite: {}", session.getPeerHost(), session.getCipherSuite());

                            ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                            ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, createNewAbsolute(alternateBaseDN, userRDN).toString());
                            ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);

                        } catch (java.io.IOException ex) {
                            Log.error("Unable to upgrade connection to TLS; failing user authentication", ex);
                            return false;
                        }

                        // make at least one lookup to check user authorization
                        lookupExistence(
                                ctx,
                                createNewAbsolute(alternateBaseDN, userRDN),
                                new String[] {usernameField});
                    }
                }
                catch (NamingException e) {
                    Log.debug("Caught a naming exception when creating InitialContext", ne);
                    return false;
                }
            }
            else {
                Log.debug("Caught a naming exception when creating InitialContext", ne);
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
                Log.error("An exception occurred while trying to close the context after an authentication attempt.");
            }
        }
        return true;
    }

    public boolean isFindUsersFromGroupsEnabled() {
        return findUsersFromGroupsEnabled;
    }

    public static LdapName createNewAbsolute( LdapName base, Rdn[] relative )
    {
        final LdapName result = (LdapName) base.clone();
        for (int i = relative.length - 1; i >= 0; i--) {
            result.add(relative[i]);
        }
        return result;
    }

    /**
     * Looks up an LDAP object by its DN and returns {@code true} if
     * the search was successful.
     *
     * @param ctx the Context to use for the lookup.
     * @param dn the object's dn to lookup.
     * @return true if the lookup was successful.
     * @throws NamingException if login credentials were wrong.
     */
    private Boolean lookupExistence(InitialDirContext ctx, LdapName dn, String[] returnattrs) throws NamingException {
        Log.debug("In lookupExistence(ctx, dn, returnattrs), searchdn is: {}", dn);

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
            Log.debug("Unable to find ldap object {}.", dn);
        }

        if (answer == null || !answer.hasMoreElements())
        {
            Log.debug(".... lookupExistence: DN not found.");
            return false;
        }
        else
        {
            Log.debug(".... lookupExistence: DN found.");
            return true;
        }
    }

    /**
     * Finds a user's RDN using their username. Normally, this search will
     * be performed using the field "uid", but this can be changed by setting
     * the {@code usernameField} property.<p>
     *
     * Searches are performed over all subtrees relative to the {@code baseDN}.
     * If the search fails in the {@code baseDN} then another search will be
     * performed in the {@code alternateBaseDN}. For example, if the {@code baseDN}
     * is "o=jivesoftware, o=com" and we do a search for "mtucker", then we might
     * find a userDN of "uid=mtucker,ou=People". This kind of searching is a good
     * thing since it doesn't make the assumption that all user records are stored
     * in a flat structure. However, it does add the requirement that "uid" field
     * (or the other field specified) must be unique over the entire subtree from
     * the {@code baseDN}. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same uid: "uid=mtucker,ou=People" and
     * "uid=mtucker,ou=Administrators". In such a case, it's not possible to
     * uniquely identify a user, so this method will throw an error.<p>
     *
     * The dn that's returned is relative to the default {@code baseDN}.
     *
     * @param username the username to lookup the dn for.
     * @return the dn associated with {@code username}.
     * @throws Exception if the search for the dn fails.
     */
    public Rdn[] findUserRDN( String username ) throws Exception
    {
        if ( userDNCache != null )
        {
            // Return a cache entry if one exists.
            final DNCacheEntry dnCacheEntry = userDNCache.get( username );
            if ( dnCacheEntry != null )
            {
                return dnCacheEntry.getUserRDN();
            }
        }

        // No cache entry. Query for the value, and add that to the cache.
        try
        {
            final Rdn[] userRDN = findUserRDN( username, baseDN );
            if ( userDNCache != null )
            {
                userDNCache.put( username, new DNCacheEntry( userRDN, baseDN ) );
            }
            return userRDN;
        }
        catch ( Exception e )
        {
            if ( alternateBaseDN != null )
            {
                final Rdn[] userRDN = findUserRDN( username, alternateBaseDN );
                if ( userDNCache != null )
                {
                    userDNCache.put( username, new DNCacheEntry( userRDN, alternateBaseDN ) );
                }
                return userRDN;
            }
            else
            {
                throw e;
            }
        }
    }

    /**
     * Finds a user's RDN using their username in the specified baseDN. Normally, this search
     * will be performed using the field "uid", but this can be changed by setting
     * the {@code usernameField} property.
     *
     * Searches are performed over all sub-trees relative to the {@code baseDN} unless
     * sub-tree searching has been disabled. For example, if the {@code baseDN} is
     * "o=jivesoftware, o=com" and we do a search for "mtucker", then we might find a userDN of
     * "uid=mtucker,ou=People". This kind of searching is a good thing since
     * it doesn't make the assumption that all user records are stored in a flat
     * structure. However, it does add the requirement that "uid" field (or the
     * other field specified) must be unique over the entire subtree from the
     * {@code baseDN}. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same uid: "uid=mtucker,ou=People" and
     * "uid=mtucker,ou=Administrators". In such a case, it's not possible to
     * uniquely identify a user, so this method will throw an error.<p>
     *
     * The RDN that's returned is relative to the {@code baseDN}.
     *
     * @param username the username to lookup the dn for.
     * @param baseDN the base DN to use for this search.
     * @return the RDN associated with {@code username}.
     * @throws Exception if the search for the RDN fails.
     * @see #findUserRDN(String) to search using the default baseDN and alternateBaseDN.
     */
    public Rdn[] findUserRDN(String username, LdapName baseDN) throws Exception {
        //Support for usernameSuffix
        username = username + usernameSuffix;
        Log.debug("Trying to find a user's RDN based on their username: '{}'. Field: '{}', Base DN: '{}' ...", username, usernameField, baseDN);
        DirContext ctx = null;
        try {
            ctx = getContext(baseDN);
            Log.debug("Starting LDAP search for username '{}'...", username);
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

            Log.debug("... search finished for username '{}'.", username);

            if (answer == null || !answer.hasMoreElements()) {
                Log.debug("User DN based on username '{}' not found.", username);
                throw new UserNotFoundException("Username " + username + " not found");
            }

            final SearchResult result = answer.next();
            final Rdn[] userRDN = getRelativeDNFromResult(result);

            // Make sure there are no more search results. If there are, then
            // the username isn't unique on the LDAP server (a perfectly possible
            // scenario since only fully qualified dn's need to be unqiue).
            // There really isn't a way to handle this, so throw an exception.
            // The baseDN must be set correctly so that this doesn't happen.
            if (answer.hasMoreElements()) {
                Log.debug("Search for userDN based on username '{}' found multiple responses, throwing exception.", username);
                throw new UserNotFoundException("LDAP username lookup for " + username + " matched multiple entries.");
            }
            // Close the enumeration.
            answer.close();

            return userRDN;
        } catch (final UserNotFoundException e) {
            Log.trace("UserNotFoundException thrown when searching for username '{}'", username, e);
            throw e;
        } catch (final Exception e) {
            Log.debug("Exception thrown when searching for userDN based on username '{}'", username, e);
            throw e;
        }
        finally {
            try { if ( ctx != null ) { ctx.close(); } }
            catch (Exception e) {
                Log.debug("An unexpected exception occurred while closing the LDAP context after searching for user '{}'.", username, e);
            }
        }
    }

    /**
     * Finds a groups's RDN using it's group name. Normally, this search will
     * be performed using the field "cn", but this can be changed by setting
     * the {@code groupNameField} property.<p>
     *
     * Searches are performed over all subtrees relative to the {@code baseDN}.
     * If the search fails in the {@code baseDN} then another search will be
     * performed in the {@code alternateBaseDN}. For example, if the {@code baseDN}
     * is "o=jivesoftware, o=com" and we do a search for "managers", then we might
     * find a groupDN of "uid=managers,ou=Groups". This kind of searching is a good
     * thing since it doesn't make the assumption that all user records are stored
     * in a flat structure. However, it does add the requirement that "cn" field
     * (or the other field specified) must be unique over the entire subtree from
     * the {@code baseDN}. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same cn: "cn=managers,ou=Financial" and
     * "cn=managers,ou=Engineers". In such a case, it's not possible to
     * uniquely identify a group, so this method will throw an error.<p>
     *
     * The RDN that's returned is relative to the default {@code baseDN}.
     *
     * @param groupname the groupname to lookup the RDN for.
     * @return the RDN associated with {@code groupname}.
     * @throws Exception if the search for the RDN fails.
     */
    public Rdn[] findGroupRDN(String groupname) throws Exception {
        try {
            return findGroupRDN(groupname, baseDN);
        }
        catch (Exception e) {
            if (alternateBaseDN == null) {
                throw e;
            }
            return findGroupRDN(groupname, alternateBaseDN);
        }
    }

    /**
     * Like {@link #findGroupRDN(String)} but returns the absolute DN of a group
     */
    public LdapName findGroupAbsoluteDN(String groupname) throws Exception {
        try {
            LdapName r = LdapManager.escapeForJNDI(findGroupRDN(groupname, baseDN));
            r.addAll(0, baseDN);
            return r;
        }
        catch (Exception e) {
            if (alternateBaseDN == null) {
                throw e;
            }
            LdapName r = LdapManager.escapeForJNDI(findGroupRDN(groupname, alternateBaseDN));
            r.addAll(0, alternateBaseDN);
            return r;
        }
    }

    /**
     * Finds a groups's dn using it's group name. Normally, this search will
     * be performed using the field "cn", but this can be changed by setting
     * the {@code groupNameField} property.<p>
     *
     * Searches are performed over all subtrees relative to the {@code baseDN}.
     * If the search fails in the {@code baseDN} then another search will be
     * performed in the {@code alternateBaseDN}. For example, if the {@code baseDN}
     * is "o=jivesoftware, o=com" and we do a search for "managers", then we might
     * find a groupDN of "uid=managers,ou=Groups". This kind of searching is a good
     * thing since it doesn't make the assumption that all user records are stored
     * in a flat structure. However, it does add the requirement that "cn" field
     * (or the other field specified) must be unique over the entire subtree from
     * the {@code baseDN}. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same cn: "cn=managers,ou=Financial" and
     * "cn=managers,ou=Engineers". In such a case, it's not possible to
     * uniquely identify a group, so this method will throw an error.<p>
     *
     * The dn that's returned is relative to the default {@code baseDN}.
     *
     * @param groupname the groupname to lookup the dn for.
     * @param baseDN the base DN to use for this search.
     * @return the dn associated with {@code groupname}.
     * @throws Exception if the search for the dn fails.
     * @see #findGroupRDN(String) to search using the default baseDN and alternateBaseDN.
     */
    public Rdn[] findGroupRDN(String groupname, LdapName baseDN) throws Exception {
        Log.debug("Trying to find a groups's RDN based on their group name: '{}'. Field: '{}', Base DN: '{}' ...", usernameField, groupname, baseDN);

        DirContext ctx = null;
        try {
            ctx = getContext(baseDN);
            Log.debug("Starting LDAP search for group '{}'...", groupname);

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

            Log.debug("... search finished for group '{}'.", groupname);

            if (answer == null || !answer.hasMoreElements()) {
                Log.debug("Group DN based on groupname '{}' not found.", groupname);
                throw new GroupNotFoundException("Groupname " + groupname + " not found");
            }

            final SearchResult result = answer.next();
            final Rdn[] groupRDN = getRelativeDNFromResult(result );

            // Make sure there are no more search results. If there are, then
            // the groupname isn't unique on the LDAP server (a perfectly possible
            // scenario since only fully qualified dn's need to be unqiue).
            // There really isn't a way to handle this, so throw an exception.
            // The baseDN must be set correctly so that this doesn't happen.
            if (answer.hasMoreElements()) {
                Log.debug("Search for groupDN based on groupname '{}' found multiple responses, throwing exception.", groupname);
                throw new GroupNotFoundException("LDAP groupname lookup for " + groupname + " matched multiple entries.");
            }
            // Close the enumeration.
            answer.close();

            return groupRDN;
        } catch (final GroupNotFoundException e) {
            Log.trace("Group not found: '{}'", groupname, e);
            throw e;
        } catch (final Exception e) {
            Log.debug("Exception thrown when searching for groupDN based on groupname '{}'", groupname, e);
            throw e;
        }
        finally {
            try { if ( ctx != null ) { ctx.close(); } }
            catch (Exception e) {
                Log.debug("An unexpected exception occurred while closing the LDAP context after searching for group '{}'.", groupname, e);
            }
        }
    }

    /**
     * Check if the given DN matches the group search filter
     *
     * @param dn the absolute DN of the node to check
     * @return true if the given DN is matching the group filter. false oterwise.
     * @throws NamingException if the search for the dn fails.
     */
    public boolean isGroupDN(LdapName dn) {
        Log.debug("LdapManager: Trying to check if DN is a group. DN: {}, Base DN: {} ...", dn, baseDN);

        // is it a sub DN of the base DN?
        if (!dn.startsWith(baseDN)
            && (alternateBaseDN == null || !dn.startsWith(alternateBaseDN))) {
            if (Log.isDebugEnabled()) {
                Log.debug("LdapManager: DN ({}) does not fit to baseDN ({},{})", dn, baseDN, alternateBaseDN);
            }
            return false;
        }

        DirContext ctx = null;
        try {
            Log.debug("LdapManager: Starting LDAP search to check group DN: {}", dn);
            // Search for the group in the node with the given DN.
            // should return the group object itself if is matches the group filter
            ctx = getContext(dn);
            // only search the object itself.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.OBJECT_SCOPE);
            constraints.setReturningAttributes(new String[]{});
            String filter = MessageFormat.format(getGroupSearchFilter(), "*");
            NamingEnumeration<SearchResult> answer = ctx.search("", filter, constraints);

            Log.debug("LdapManager: ... group check search finished for DN: {}", dn);

            boolean result = (answer != null && answer.hasMoreElements());

            if (answer != null) {
                answer.close();
            }
            Log.debug("LdapManager: DN is group: {}? {}!", dn, result);
            return result;
        }
        catch (final NameNotFoundException e) {
            Log.info("LdapManager: Given DN not found (while checking if DN is a group)! {}", dn);
            return false;
        }
        catch (final NamingException e) {
            Log.error("LdapManager: Exception thrown while checking if DN is a group {}", dn, e);
            return false;
        }
        finally {
            try {
                if (ctx != null)
                    ctx.close();
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to verify that DN '{}' is a group.", dn, ex);
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
    String getProviderURL(LdapName baseDN) throws NamingException
    {
        StringBuffer ldapURL = new StringBuffer();

        try
        {
            for ( String host : hosts )
            {
                // Create a correctly-encoded ldap URL for the PROVIDER_URL
                final URI uri = new URI(sslEnabled ? "ldaps" : "ldap", null, host, port, "/" + baseDN.toString(), null, null);
                ldapURL.append(uri.toASCIIString());
                ldapURL.append(" ");
            }
            return ldapURL.toString().trim();
        }
        catch ( Exception e )
        {
            Log.error( "Unable to generate provider URL for baseDN: '{}'.", baseDN, e );
            throw new NamingException( "Unable to generate provider URL for baseDN: '"+baseDN+"': " + e.getMessage() );
        }
    }

    /**
     * Returns the LDAP servers hosts; e.g. {@code localhost} or
     * {@code machine.example.com}, etc. This value is stored as the Jive
     * Property {@code ldap.host}.
     *
     * @return the LDAP server host name.
     */
    public Collection<String> getHosts() {
        return hosts;
    }

    /**
     * Sets the list of LDAP servers host; e.g., {@code localhost} or
     * {@code machine.example.com}, etc. This value is store as the Jive
     * Property {@code ldap.host} using a comma as a delimiter for each host.<p>
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
     * stored as the Jive Property {@code ldap.port}.
     *
     * @return the LDAP server port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the LDAP server port number. The default is 389. This value is
     * stored as the Jive property {@code ldap.port}.
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
    public LdapUserTester.PropertyMapping getNameField() {
        return nameField;
    }

    /**
     * Sets the LDAP field name that the user's name is stored in. By default
     * this is "cn". Another common value is "displayName".
     *
     * @param nameField the LDAP field that that corresponds to the user's name.
     */
    public void setNameField(LdapUserTester.PropertyMapping nameField) {
        this.nameField = nameField;
        if (nameField == null || nameField.getDisplayFormat() == null || nameField.getDisplayFormat().isEmpty() ) {
            properties.remove("ldap.nameField");
        } else {
            properties.put("ldap.nameField", nameField.getDisplayFormat());
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
    public LdapName getBaseDN() {
        return baseDN;
    }

    /**
     * Sets the starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the base DN.
     *
     * @param baseDN the starting DN used for performing searches.
     */
    public void setBaseDN(LdapName baseDN) {
        this.baseDN = baseDN;
        properties.put("ldap.baseDN", baseDN.toString());
    }

    /**
     * Returns the alternate starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the alternate base DN after
     * they are performed on the main base DN.
     *
     * @return the alternate starting DN used for performing searches. If no alternate
     *      DN is set, this method will return {@code null}.
     */
    public LdapName getAlternateBaseDN() {
        return alternateBaseDN;
    }

    /**
     * Sets the alternate starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the alternate base DN after
     * they are performed on the main base dn.
     *
     * @param alternateBaseDN the alternate starting DN used for performing searches.
     */
    public void setAlternateBaseDN(LdapName alternateBaseDN) {
        this.alternateBaseDN = alternateBaseDN;
        if (alternateBaseDN == null) {
            properties.remove("ldap.alternateBaseDN");
        }
        else {
            properties.put("ldap.alternateBaseDN", alternateBaseDN.toString());
        }
    }

    /**
     * Returns the BaseDN for the given username.
     *
     * @param username username to return its base DN.
     * @return the BaseDN for the given username. If no baseDN is found,
     *         this method will return {@code null}.
     */
    public LdapName getUsersBaseDN( String username )
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
            final Rdn[] userRDN = findUserRDN( username, baseDN );
            if ( userDNCache != null )
            {
                userDNCache.put( username, new DNCacheEntry( userRDN, baseDN ) );
            }
            return baseDN;
        }
        catch ( Exception e )
        {
            try
            {
                if ( alternateBaseDN != null )
                {
                    final Rdn[] userRDN = findUserRDN( username, alternateBaseDN );
                    if ( userDNCache != null )
                    {
                        userDNCache.put( username, new DNCacheEntry( userRDN, alternateBaseDN ) );
                    }
                    return alternateBaseDN;
                }
            }
            catch ( Exception ex )
            {
                Log.debug( "An exception occurred while tyring to get the user baseDn for {}", username, ex );
            }
        }

        return null;
    }

    /**
     * Returns the BaseDN for the given groupname.
     *
     * @param groupname groupname to return its base DN.
     * @return the BaseDN for the given groupname. If no baseDN is found,
     *         this method will return {@code null}.
     */
    public LdapName getGroupsBaseDN(String groupname) {
        try {
            findGroupRDN(groupname, baseDN);
            return baseDN;
        }
        catch (Exception e) {
            try {
                if (alternateBaseDN != null) {
                    findGroupRDN(groupname, alternateBaseDN);
                    return alternateBaseDN;
                }
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to find the base dn for group: {}", groupname, ex);
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
        return adminDN;
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
     * under the base DN will be searched. The default is {@code true} which is the best
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
     * under the base DN will be searched. The default is {@code true} which is the best
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
     * Returns true if nested / complex / hierarchic groups should be should be flattened.
     * <p>
     * This means: if group A is member of group B, the members of group A will also be members of
     * group B
     */
    public boolean isFlattenNestedGroups() {
        return flattenNestedGroups;
    }

    /**
     * Set whether nested / complex / hierarchic groups should be should be flattened.
     *
     * @see #isFlattenNestedGroups()
     */
    public void setFlattenNestedGroups(boolean flattenNestedGroups) {
        this.flattenNestedGroups = flattenNestedGroups;
        properties.put("ldap.flattenNestedGroups", String.valueOf(posixMode));
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
        final int pageSize = LDAP_PAGE_SIZE.getValue();
        boolean clientSideSort = Boolean.parseBoolean(properties.get("ldap.clientSideSorting"));
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
            Control[] baseRequestControls = baseTmpRequestControls.toArray(new Control[0]);
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
                Control[] requestControls = tmpRequestControls.toArray(new Control[0]);
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
                    Control[] requestControls = tmpRequestControls.toArray(new Control[0]);
                    ctx2.setRequestControls(requestControls);
                } while (cookie != null && (lastRes == -1 || count <= lastRes));
            }

            // If client-side sorting is enabled, sort and trim.
            if (clientSideSort) {
                results = sortAndPaginate(results, startIndex, numResults);
            }
        }
        catch (Exception e) {
            Log.error("An exception occurred while trying to retrieve a list of results from the LDAP server", e);
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
            catch (Exception e) {
                Log.debug("An exception occurred while trying to close contexts after retrieving a list of results from the LDAP server.", e);
            }
        }
        return results;
    }

    static List<String> sortAndPaginate(Collection<String> unpagedCollection, int startIndex, int numResults) {
        final List<String> results = new ArrayList<>(unpagedCollection);
        Collections.sort(results);
        // If the startIndex is negative, start at the beginning
        final int fromIndex = Math.max(startIndex, 0);
        // If the numResults is negative, take all the results
        final int resultCount = Math.max(numResults, results.size());
        // Make sure we're not returning more results than there actually are
        final int toIndex = Math.min(results.size(), resultCount);
        return results.subList(fromIndex, toIndex);
    }

    /**
     * Generic routine for retrieving a single element from the LDAP server.  It's meant to be very
     * flexible so that just about any query for a single results can make use of it without having
     * to reimplement their own calls to LDAP.
     * <p>
     * The passed in filter string needs to be pre-prepared! In other words, nothing will be changed
     * in the string before it is used as a string.
     *
     * @param attribute             LDAP attribute to be pulled from each result and placed in the return results.
     *                              Typically pulled from this manager. Null means the the absolute DN is returned.
     * @param searchFilter          Filter to use to perform the search.  Typically pulled from this manager.
     * @param failOnMultipleResults It true, an {@link IllegalStateException} will be thrown, if the
     *                              search result is not unique. If false, just the first result will be returned.
     * @return A single string.
     */
    public String retrieveSingle(String attribute, String searchFilter, boolean failOnMultipleResults) {
        try {
            return retrieveSingle(attribute, searchFilter, failOnMultipleResults, baseDN);
        }
        catch (Exception e) {
            if (alternateBaseDN != null) {
                return retrieveSingle(attribute, searchFilter, failOnMultipleResults, alternateBaseDN);
            }
            else {
                throw e;
            }
        }
    }

    /**
     * Generic routine for retrieving a single element from the LDAP server.  It's meant to be very
     * flexible so that just about any query for a single results can make use of it without having
     * to reimplement their own calls to LDAP.
     * <p>
     * The passed in filter string needs to be pre-prepared!  In other words, nothing will be changed
     * in the string before it is used as a string.
     *
     * @param attribute             LDAP attribute to be pulled from each result and placed in the return results.
     *                              Typically pulled from this manager. Null means the the absolute DN is returned.
     * @param searchFilter          Filter to use to perform the search.  Typically pulled from this manager.
     * @param failOnMultipleResults It true, an {@link IllegalStateException} will be thrown, if the
     *                              search result is not unique. If false, just the first result will be returned.
     * @param baseDN                DN where to start the search. Typically {@link #getBaseDN()} or {@link #getAlternateBaseDN()}.
     * @return A single string.
     */
    public String retrieveSingle(String attribute, String searchFilter, boolean failOnMultipleResults, LdapName baseDN) {
        LdapContext ctx = null;
        try {
            ctx = getContext(baseDN);

            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(attribute == null ? new String[0] : new String[]{attribute});

            NamingEnumeration<SearchResult> answer = ctx.search("", searchFilter, searchControls);
            if (answer == null || !answer.hasMoreElements()) {
                return null;
            }
            SearchResult searchResult = answer.next();
            String result = attribute == null
                ? new LdapName(searchResult.getName()).addAll(0, baseDN).toString() :
                (String) searchResult.getAttributes().get(attribute).get();
            if (answer.hasMoreElements()) {
                Log.debug("Search result for '{}' is not unique.", searchFilter);
                if (failOnMultipleResults)
                    throw new IllegalStateException("Search result for " + searchFilter + " is not unique.");
            }
            answer.close();
            return result;
        }
        catch (Exception e) {
            Log.error("Error while searching for single result of: {}", searchFilter, e);
            return null;
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            } catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to retrieve a single attribute element for {}.", attribute, ex);
            }
        }
    }

    /**
     * Reads the attribute values of an entry with the given DN.
     *
     * @param attributeName LDAP attribute to be read.
     * @param dn            DN of the entry.
     * @return A list with the values of the attribute.
     */
    public List<String> retrieveAttributeOf(String attributeName, LdapName dn) throws NamingException {
        Log.debug("LdapManager: Reading attribute '{}' of DN '{}' ...", attributeName, dn);

        DirContext ctx = null;
        NamingEnumeration<?> values = null;
        try {

            ctx = getContext(dn);
            Attributes attributes = ctx.getAttributes("", new String[]{attributeName});
            Attribute attribute = attributes.get(attributeName);
            if (attribute == null)
                return Collections.emptyList();
            List<String> result = new ArrayList<>(attribute.size());
            values = attribute.getAll();
            while (values.hasMoreElements()) {
                result.add(values.next().toString());
            }
            return result;
        }
        catch (final Exception e) {
            Log.debug("LdapManager: Exception thrown when reading attribute '{}' of DN '{}' ...", attributeName, dn, e);
            throw e;
        }
        finally {
            try {
                if (values != null)
                    values.close();
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close values after trying to read attribute {}.", attributeName, ex);
            }
            try {
                if (ctx != null)
                    ctx.close();
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to read attribute {}.", attributeName, ex);
            }
        }
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
        final int pageSize = LDAP_PAGE_SIZE.getValue();
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
            Control[] baseRequestControls = baseTmpRequestControls.toArray(new Control[0]);
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
                Control[] requestControls = tmpRequestControls.toArray(new Control[0]);
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
                    Control[] requestControls = tmpRequestControls.toArray(new Control[0]);
                    ctx2.setRequestControls(requestControls);
                } while (cookie != null);
            }
        }
        catch (Exception e) {
            Log.error("An exception occurred while trying to retrieve a list count for attribute: {}", attribute, e);
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
            catch (Exception e) {
                Log.debug("An exception occurred while trying to close contexts after retrieving a list count for attribute: {}", attribute, e);
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
     * Returns a JNDI Name for an array of RDNs that is suitable to use to
     * access LDAP through JNDI.
     *
     * This method applies JDNI-suitable escaping to each RDN
     *
     * <blockquote>When using the JNDI to access an LDAP service, you should be
     * aware that the forward slash character ("/") in a string name has special
     * meaning to the JNDI. If the LDAP entry's name contains this character,
     * then you need to escape it (using the backslash character ("\")).
     * For example, an LDAP entry with the name "cn=O/R" must be presented as
     * the string "cn=O\\/R" to the JNDI context methods.</blockquote>
     *
     * @param rdn The names to escape (cannot be null).
     * @return A JNDI name representation of the values (never null).
     * @see <a href="https://docs.oracle.com/javase/tutorial/jndi/ldap/jndi.html">JNDI</a>
     * @see <a href="https://docs.oracle.com/javase/jndi/tutorial/beyond/names/syntax.html">Handling Special Characters</a>
     */
    public static LdapName escapeForJNDI(Rdn... rdn)
    {
        // Create a clone, to prevent changes to ordering of elements to affect the original array.
        final Rdn[] copy = Arrays.copyOf(rdn, rdn.length);
        final List<Rdn> rdns = Arrays.asList(copy);
        Collections.reverse(rdns);

        // LdapName is a JNDI name that will apply all relevant escaping.
        return new LdapName(rdns);
    }

    /**
     * Escapes any special chars (RFC 4514/4515) from a string representing
     * a search filter assertion value, with the exception of the '*' wildcard sign
     *
     * @param value The input string.
     * @param acceptWildcard {@code true} to accept wildcards, otherwise {@code false}
     *
     * @return A assertion value string ready for insertion into a 
     *         search filter string.
     */
    public static String sanitizeSearchFilter(final String value, boolean acceptWildcard ) {


            StringBuilder result = new StringBuilder();

            for (int i=0; i< value.length(); i++) {

                char c = value.charAt(i);

                switch(c) {
                    case ' ':		result.append( i == 0 || i == (value.length()-1)? "\\20" : c );	break; // RFC 4514 (only at the beginning or end of the string)
                    case '!':		result.append("\\21");	break; // RFC 4515
                    case '"':		result.append("\\22");	break; // RFC 4514
                    case '#':		result.append( i == 0 ? "\\23" : c );	break; // RFC 4514 (only at the beginning of the string)
                    case '&':		result.append("\\26");	break; // RFC 4515
                    case '(':		result.append("\\28");	break; // RFC 4515
                    case ')':		result.append("\\29");	break; // RFC 4515
                    case '*':		result.append(acceptWildcard ? "*" : "\\2a");	break;  // RFC 4515
                    case '+':		result.append("\\2b");	break; // RFC 4514
                    case ',':		result.append("\\2c");	break; // RFC 4514
                    case '/':		result.append("\\2f");	break; // forward-slash  has special meaning to the JDNI. Escape it!
                    case ':':		result.append("\\3a");	break; // RFC 4515
                    case ';':		result.append("\\3b");	break; // RFC 4514
                    case '<':		result.append("\\3c");	break; // RFC 4514
                    case '>':		result.append("\\3e");	break; // RFC 4514
                    case '\\':		result.append("\\5c");	break; // RFC 4515
                    case '|':		result.append("\\7c");	break; // RFC 4515
                    case '~':		result.append("\\7e");	break; // RFC 4515
                    case '\u0000':	result.append("\\00");	break; // RFC 4515
                default:
                    if (c <= 0x7f) {
                        // regular 1-byte UTF-8 char
                        result.append(c);
                    }
                    else {
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
                            result.append(c);
                        }
                    }
                }
            }
            return result.toString();
    }

    private static class DNCacheEntry implements Serializable
    {
        private final Rdn[] userRDN; // relative to baseDN!
        private final LdapName baseDN;

        public DNCacheEntry( Rdn[] userRDN, LdapName baseDN )
        {
            if ( userRDN == null ) {
                throw new IllegalArgumentException("Argument 'userRDN' cannot be null.");
            }

            if ( baseDN == null ) {
                throw new IllegalArgumentException("Argument 'baseDN' cannot be null.");
            }
            this.userRDN = userRDN;
            this.baseDN = baseDN;
        }

        public Rdn[] getUserRDN()
        {
            return userRDN;
        }

        public LdapName getBaseDN()
        {
            return baseDN;
        }

        @Override
        public boolean equals( final Object o )
        {
            if ( this == o ) { return true; }
            if ( o == null || getClass() != o.getClass() ) { return false; }
            final DNCacheEntry that = (DNCacheEntry) o;
            return Arrays.equals(userRDN, that.userRDN) &&
                baseDN.equals(that.baseDN);
        }

        @Override
        public int hashCode()
        {
            int result = Objects.hash(baseDN);
            result = 31 * result + Arrays.hashCode(userRDN);
            return result;
        }
    }
}
