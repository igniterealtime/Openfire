/**
 * $RCSfile$
 * $Revision: 2698 $
 * $Date: 2005-08-19 15:28:16 -0300 (Fri, 19 Aug 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.ldap;

import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.net.URLEncoder;
import java.util.Hashtable;

/**
 * Centralized administration of LDAP connections. The getInstance() method
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
 *      <li>ldap.nameField -- default value is "cn".</li>
 *      <li>ldap.emailField -- default value is "mail".</li>
 *      <li>ldap.searchFilter -- the filter used to load the list of users. The
 *              default value is in the form "([usernameField]={0})" where [usernameField]
 *              is the value of ldap.usernameField.
 *      <li>ldap.groupNameField</li>
 *      <li>ldap.groupMemberField</li>
 *      <li>ldap.groupDescriptionField</li>
 *      <li>ldap.posixMode</li>
 *      <li>ldap.groupSearchFilter</li>
 *      <li>ldap.debugEnabled</li>
 *      <li>ldap.sslEnabled</li>
 *      <li>ldap.autoFollowReferrals</li>
 *      <li>ldap.initialContextFactory --  if this value is not specified,
 *          "com.sun.jndi.ldap.LdapCtxFactory" will be used.</li>
 * </ul>
 *
 * @author Matt Tucker
 */
public class LdapManager {

    private String host;
    private int port = 389;
    private String usernameField = "uid";
    private String nameField = "cn";
    private String emailField = "mail";
    private String baseDN = "";
    private String alternateBaseDN = null;
    private String adminDN = null;
    private String adminPassword;
    private boolean ldapDebugEnabled = false;
    private boolean sslEnabled = false;
    private String initialContextFactory;
    private boolean followReferrals = false;
    private boolean connectionPoolEnabled = true;
    private String searchFilter = null;

    private String groupNameField = "cn";
    private String groupMemberField = "member";
    private String groupDescriptionField = "description";
    private boolean posixMode = false;
    private String groupSearchFilter = null;

    private static LdapManager instance = new LdapManager();

    /**
     * Provides singleton access to an instance of the LdapManager class.
     *
     * @return an LdapManager instance.
     */
    public static LdapManager getInstance() {
        return instance;
    }

    /**
     * Constructs a new LdapManager instance. This class is a singleton so the
     * constructor is private.
     */
    private LdapManager() {
        this.host = JiveGlobals.getXMLProperty("ldap.host");
        String portStr = JiveGlobals.getXMLProperty("ldap.port");
        if (portStr != null) {
            try {
                this.port = Integer.parseInt(portStr);
            }
            catch (NumberFormatException nfe) { }
        }
        if (JiveGlobals.getXMLProperty("ldap.usernameField") != null) {
            this.usernameField = JiveGlobals.getXMLProperty("ldap.usernameField");
        }
        if (JiveGlobals.getXMLProperty("ldap.baseDN") != null) {
            this.baseDN = JiveGlobals.getXMLProperty("ldap.baseDN");
        }
        if (JiveGlobals.getXMLProperty("ldap.alternateBaseDN") != null) {
            this.alternateBaseDN = JiveGlobals.getXMLProperty("ldap.alternateBaseDN");
        }
        if (JiveGlobals.getXMLProperty("ldap.nameField") != null) {
            this.nameField = JiveGlobals.getXMLProperty("ldap.nameField");
        }
        if (JiveGlobals.getXMLProperty("ldap.emailField") != null) {
            this.emailField = JiveGlobals.getXMLProperty("ldap.emailField");
        }
        if (JiveGlobals.getXMLProperty("ldap.connectionPoolEnabled") != null) {
            this.connectionPoolEnabled = Boolean.valueOf(
                    JiveGlobals.getXMLProperty("ldap.connectionPoolEnabled")).booleanValue();
        }
        if (JiveGlobals.getXMLProperty("ldap.searchFilter") != null) {
            this.searchFilter = JiveGlobals.getXMLProperty("ldap.searchFilter");
        }
        else {
            StringBuilder filter = new StringBuilder();
            filter.append("(").append(usernameField).append("={0})");
            this.searchFilter = filter.toString();
        }
        if (JiveGlobals.getXMLProperty("ldap.groupNameField") != null) {
            this.groupNameField = JiveGlobals.getXMLProperty("ldap.groupNameField");
        }
        if (JiveGlobals.getXMLProperty("ldap.groupMemberField") != null) {
            this.groupMemberField = JiveGlobals.getXMLProperty("ldap.groupMemberField");
        }
        if (JiveGlobals.getXMLProperty("ldap.groupDescriptionField") != null) {
            this.groupDescriptionField = JiveGlobals.getXMLProperty("ldap.groupDescriptionField");
        }
        if (JiveGlobals.getXMLProperty("ldap.posixMode") != null) {
            this.posixMode = Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.posixMode"));
        }
        if (JiveGlobals.getXMLProperty("ldap.groupSearchFilter") != null) {
            this.groupSearchFilter = JiveGlobals.getXMLProperty("ldap.groupSearchFilter");
        }
        else {
            this.groupSearchFilter = "("+groupMemberField+"={0})";
        }

        this.adminDN = JiveGlobals.getXMLProperty("ldap.adminDN");
        if (adminDN != null && adminDN.trim().equals("")) {
            adminDN = null;
        }
        this.adminPassword = JiveGlobals.getXMLProperty("ldap.adminPassword");
        this.ldapDebugEnabled = Boolean.valueOf(JiveGlobals.getXMLProperty(
                "ldap.debugEnabled")).booleanValue();
        this.sslEnabled = Boolean.valueOf(JiveGlobals.getXMLProperty(
                "ldap.sslEnabled")).booleanValue();
        this.followReferrals = Boolean.valueOf(JiveGlobals.getXMLProperty(
                "ldap.autoFollowReferrals")).booleanValue();
        this.initialContextFactory = JiveGlobals.getXMLProperty("ldap.initialContextFactory");
        if (initialContextFactory != null) {
            try {
                Class.forName(initialContextFactory);
            }
            catch (ClassNotFoundException cnfe) {
                Log.error("Initial context factory class failed to load: " + initialContextFactory +
                        ".  Using default initial context factory class instead.");
                initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
            }
        }
        // Use default value if none was set.
        else {
            initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
        }

        if (Log.isDebugEnabled()) {
            Log.debug("Created new LdapManager() instance, fields:");
            Log.debug("\t host: " + host);
            Log.debug("\t port: " + port);
            Log.debug("\t usernamefield: " + usernameField);
            Log.debug("\t baseDN: " + baseDN);
            Log.debug("\t alternateBaseDN: " + alternateBaseDN);
            Log.debug("\t nameField: " + nameField);
            Log.debug("\t emailField: " + emailField);
            Log.debug("\t adminDN: " + adminDN);
            Log.debug("\t adminPassword: " + adminPassword);
            Log.debug("\t searchFilter: " + searchFilter);
            Log.debug("\t ldapDebugEnabled: " + ldapDebugEnabled);
            Log.debug("\t sslEnabled: " + sslEnabled);
            Log.debug("\t initialContextFactory: " + initialContextFactory);
            Log.debug("\t connectionPoolEnabled: " + connectionPoolEnabled);
            Log.debug("\t autoFollowReferrals: " + followReferrals);
            Log.debug("\t groupNameField: " + groupNameField);
            Log.debug("\t groupMemberField: " + groupMemberField);
            Log.debug("\t groupDescriptionField: " + groupDescriptionField);
            Log.debug("\t posixMode: " + posixMode);
            Log.debug("\t groupSearchFilter: " + groupSearchFilter);
        }
    }

    /**
     * Returns a DirContext for the LDAP server that can be used to perform
     * lookups and searches using the default base DN. The context uses the
     * admin login that is defined by <tt>adminDN</tt> and <tt>adminPassword</tt>.
     *
     * @return a connection to the LDAP server.
     * @throws NamingException if there is an error making the LDAP connection.
     */
    public LdapContext getContext() throws NamingException {
        return getContext(baseDN);
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
            Log.debug("Creating a DirContext in LdapManager.getContext()...");
        }

         // Set up the environment for creating the initial context
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
        env.put(Context.PROVIDER_URL, getProviderURL(baseDN));
        if (sslEnabled) {
            env.put("java.naming.ldap.factory.socket",
                    "org.jivesoftware.util.SimpleSSLSocketFactory");
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }

        // Use simple authentication to connect as the admin.
        if (adminDN != null) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, adminDN);
            if (adminPassword != null) {
                env.put(Context.SECURITY_CREDENTIALS, adminPassword);
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
            env.put("com.sun.jndi.ldap.connect.pool", "true");
        }
        if (followReferrals) {
            env.put(Context.REFERRAL, "follow");
        }

        if (debug) {
            Log.debug("Created hashtable with context values, attempting to create context...");
        }
        // Create new initial context
        LdapContext context = new InitialLdapContext(env, null);
        if (debug) {
            Log.debug("... context created successfully, returning.");
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
            Log.debug("In LdapManager.checkAuthentication(userDN, password), userDN is: " + userDN + "...");
        }

        DirContext ctx = null;
        try {
            // See if the user authenticates.
            Hashtable<String, Object> env = new Hashtable<String, Object>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
            env.put(Context.PROVIDER_URL, getProviderURL(baseDN));
            if (sslEnabled) {
                env.put("java.naming.ldap.factory.socket",
                        "org.jivesoftware.util.SimpleSSLSocketFactory");
                env.put(Context.SECURITY_PROTOCOL, "ssl");
            }
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, userDN + "," + baseDN);
            env.put(Context.SECURITY_CREDENTIALS, password);
            // Specify timeout to be 10 seconds, only on non SSL since SSL connections
            // break with a teimout.
            if (!sslEnabled) {
                env.put("com.sun.jndi.ldap.connect.timeout", "10000");
            }
            if (ldapDebugEnabled) {
                env.put("com.sun.jndi.ldap.trace.ber", System.err);
            }
            if (debug) {
                Log.debug("Created context values, attempting to create context...");
            }
            ctx = new InitialDirContext(env);
            if (debug) {
                Log.debug("... context created successfully, returning.");
            }
        }
        catch (NamingException ne) {
            // If an alt baseDN is defined, attempt a lookup there.
            if (alternateBaseDN != null) {
                try { ctx.close(); }
                catch (Exception ignored) { }
                try {
                    // See if the user authenticates.
                    Hashtable<String, Object> env = new Hashtable<String, Object>();
                    // Use a custom initial context factory if specified. Otherwise, use the default.
                    env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
                    env.put(Context.PROVIDER_URL, getProviderURL(alternateBaseDN));
                    if (sslEnabled) {
                        env.put("java.naming.ldap.factory.socket", "org.jivesoftware.util.SimpleSSLSocketFactory");
                        env.put(Context.SECURITY_PROTOCOL, "ssl");
                    }
                    env.put(Context.SECURITY_AUTHENTICATION, "simple");
                    env.put(Context.SECURITY_PRINCIPAL, userDN + "," + alternateBaseDN);
                    env.put(Context.SECURITY_CREDENTIALS, password);
                    // Specify timeout to be 10 seconds, only on non SSL since SSL connections
                    // break with a teimout.
                    if (!sslEnabled) {
                        env.put("com.sun.jndi.ldap.connect.timeout", "10000");
                    }
                    if (ldapDebugEnabled) {
                        env.put("com.sun.jndi.ldap.trace.ber", System.err);
                    }
                    if (debug) {
                        Log.debug("Created context values, attempting to create context...");
                    }
                    ctx = new InitialDirContext(env);
                }
                catch (NamingException e) {
                    if (debug) {
                        Log.debug("Caught a naming exception when creating InitialContext", ne);
                    }
                    return false;
                }
            }
            else {
                if (debug) {
                    Log.debug("Caught a naming exception when creating InitialContext", ne);
                }
                return false;
            }
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) { }
        }
        return true;
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
    public String findUserDN(String username) throws Exception {
        try {
            return findUserDN(username, baseDN);
        }
        catch (Exception e) {
            if (alternateBaseDN != null) {
                return findUserDN(username, alternateBaseDN);
            }
            else {
                throw e;
            }
        }
    }

    /**
     * Finds a user's dn using their username in the specified baseDN. Normally, this search
     * will be performed using the field "uid", but this can be changed by setting
     * the <tt>usernameField</tt> property.<p>
     *
     * Searches are performed over all subtrees relative to the <tt>baseDN</tt>.
     * For example, if the <tt>baseDN</tt> is "o=jivesoftware, o=com" and we
     * do a search for "mtucker", then we might find a userDN of
     * "uid=mtucker,ou=People". This kind of searching is a good thing since
     * it doesn't make the assumption that all user records are stored in a flat
     * structure. However, it does add the requirement that "uid" field (or the
     * other field specified) must be unique over the entire subtree from the
     * <tt>baseDN</tt>. For example, it's entirely possible to create two dn's
     * in your LDAP directory with the same uid: "uid=mtucker,ou=People" and
     * "uid=mtucker,ou=Administrators". In such a case, it's not possible to
     * uniquely identify a user, so this method will throw an error.<p>
     *
     * The dn that's returned is relative to the <tt>baseDN</tt>.
     *
     * @param username the username to lookup the dn for.
     * @param baseDN the base DN to use for this search.
     * @return the dn associated with <tt>username</tt>.
     * @throws Exception if the search for the dn fails.
     * @see #findUserDN(String)  to search using the default baseDN and alternateBaseDN.
     */
    public String findUserDN(String username, String baseDN) throws Exception {
        boolean debug = Log.isDebugEnabled();
        if (debug) {
            Log.debug("Trying to find a user's DN based on their username. " + usernameField + ": " + username
                    + ", Base DN: " + baseDN + "...");
        }
        DirContext ctx = null;
        try {
            ctx = getContext(baseDN);
            if (debug) {
                Log.debug("Starting LDAP search...");
            }
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { usernameField });

            NamingEnumeration answer = ctx.search("", searchFilter, new String[] {username},
                    constraints);

            if (debug) {
                Log.debug("... search finished");
            }

            if (answer == null || !answer.hasMoreElements()) {
                if (debug) {
                    Log.debug("User DN based on username '" + username + "' not found.");
                }
                throw new UserNotFoundException("Username " + username + " not found");
            }
            String userDN = ((SearchResult)answer.next()).getName();
            // Make sure there are no more search results. If there are, then
            // the username isn't unique on the LDAP server (a perfectly possible
            // scenario since only fully qualified dn's need to be unqiue).
            // There really isn't a way to handle this, so throw an exception.
            // The baseDN must be set correctly so that this doesn't happen.
            if (answer.hasMoreElements()) {
                if (debug) {
                    Log.debug("Search for userDN based on username '" + username + "' found multiple " +
                            "responses, throwing exception.");
                }
                throw new UserNotFoundException("LDAP username lookup for " + username +
                        " matched multiple entries.");
            }
            return userDN;
        }
        catch (Exception e) {
            if (debug) {
                Log.debug("Exception thrown when searching for userDN based on username '" + username + "'", e);
            }
            throw e;
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) { }
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
        String ldapURL = "";
        try {
            // Create a correctly-encoded ldap URL for the PROVIDER_URL
            ldapURL = "ldap://" + host + ":" + port + "/" +
                    URLEncoder.encode(baseDN, "UTF-8");
            // The java.net.URLEncoder class encodes spaces as +, but they need to be %20
            ldapURL = ldapURL.replaceAll("\\+", "%20");
        }
        catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is not supported, fall back to using raw baseDN
            ldapURL = "ldap://" + host + ":" + port + "/" + baseDN;
        }
        return ldapURL;
    }

    /**
     * Returns the LDAP server host; e.g. <tt>localhost</tt> or
     * <tt>machine.example.com</tt>, etc. This value is stored as the Jive
     * Property <tt>ldap.host</tt>.
     *
     * @return the LDAP server host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the LDAP server host; e.g., <tt>localhost</tt> or
     * <tt>machine.example.com</tt>, etc. This value is store as the Jive
     * Property <tt>ldap.host</tt>
     *
     * @param host the LDAP server host name.
     */
    public void setHost(String host) {
        this.host = host;
        JiveGlobals.setXMLProperty("ldap.host", host);
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
        JiveGlobals.setXMLProperty("ldap.port", ""+port);
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
        JiveGlobals.setXMLProperty("ldap.ldapDebugEnabled", ""+debugEnabled);
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
        JiveGlobals.setXMLProperty("ldap.sslEnabled", ""+sslEnabled);
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
     * Sets the LDAP field name that the username lookup will be performed on.
     * By default this is "uid".
     *
     * @param usernameField the LDAP field that the username lookup will be
     *      performed on.
     */
    public void setUsernameField(String usernameField) {
        this.usernameField = usernameField;
        if (usernameField == null) {
            JiveGlobals.deleteXMLProperty("ldap.usernameField");
        }
        else {
            JiveGlobals.setXMLProperty("ldap.usernameField", usernameField);
        }
    }

    /**
     * Returns the LDAP field name that the user's name is stored in. By default
     * this is "cn". Another common value is "displayName".
     *
     * @return the LDAP field that that correspond's to the user's name.
     */
    public String getNameField() {
        return nameField;
    }

    /**
     * Sets the LDAP field name that the user's name is stored in. By default
     * this is "cn". Another common value is "displayName".
     *
     * @param nameField the LDAP field that that correspond's to the user's name.
     */
    public void setNameField(String nameField) {
        this.nameField = nameField;
        if (nameField == null) {
            JiveGlobals.deleteXMLProperty("ldap.nameField");
        }
        else {
            JiveGlobals.setXMLProperty("ldap.nameField", nameField);
        }
    }

    /**
     * Returns the LDAP field name that the user's email address is stored in.
     * By default this is "mail".
     *
     * @return the LDAP field that that correspond's to the user's email
     *      address.
     */
    public String getEmailField() {
        return emailField;
    }

    /**
     * Sets the LDAP field name that the user's email address is stored in.
     * By default this is "mail".
     *
     * @param emailField the LDAP field that that correspond's to the user's
     *      email address.
     */
    public void setEmailField(String emailField) {
        this.emailField = emailField;
        if (emailField == null) {
            JiveGlobals.deleteXMLProperty("ldap.emailField");
        }
        else {
            JiveGlobals.setXMLProperty("ldap.emailField", emailField);
        }
    }

    /**
     * Returns the starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the base DN.
     *
     * @return the starting DN used for performing searches.
     */
    public String getBaseDN() {
        return baseDN;
    }

    /**
     * Sets the starting DN that searches for users will performed with.
     * Searches will performed on the entire sub-tree under the base DN.
     *
     * @param baseDN the starting DN used for performing searches.
     */
    public void setBaseDN(String baseDN) {
        this.baseDN = baseDN;
        JiveGlobals.setXMLProperty("ldap.baseDN", baseDN);
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
        return alternateBaseDN;
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
            JiveGlobals.deleteXMLProperty("ldap.alternateBaseDN");
        }
        else {
            JiveGlobals.setXMLProperty("ldap.alternateBaseDN", alternateBaseDN);
        }
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
        JiveGlobals.setXMLProperty("ldap.adminDN", adminDN);
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
        JiveGlobals.setXMLProperty("ldap.adminPassword", adminPassword);
    }

    /**
     * Returns the filter used for searching the directory for users.
     *
     * @return the search filter.
     */
    public String getSearchFilter() {
    	return searchFilter;
    }

    /**
     * Sets the filter used for searching the directory for users. The filter should
     * contain a single token "{0}" that will be dynamically replaced with the
     * user's unique ID.
     *
     * @param searchFilter the search filter.
     */
    public void setSearchFilter(String searchFilter) {
    	if (searchFilter == null || "".equals(searchFilter)) {
            StringBuilder filter = new StringBuilder();
            filter.append("(").append(usernameField).append("={0})");
            this.searchFilter = filter.toString();
            JiveGlobals.deleteXMLProperty("ldap.searchFilter");
        }
        else {
            this.searchFilter = searchFilter;
    		JiveGlobals.setXMLProperty("ldap.searchFilter", searchFilter);
    	}
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
        JiveGlobals.setXMLProperty("ldap.groupNameField", groupNameField);
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
    public void setGroupmemberField(String groupMemberField) {
        this.groupMemberField = groupMemberField;
        JiveGlobals.setXMLProperty("ldap.groupMemberField", groupMemberField);
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
        JiveGlobals.setXMLProperty("ldap.groupDescriptionField", groupDescriptionField);
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
    public void setPostfixMode(boolean posixMode) {
        this.posixMode = posixMode;
        JiveGlobals.setXMLProperty("ldap.posixEnabled", String.valueOf(posixMode));
    }

    /**
     * Return the field used as the search filter when searching for groups.
     * Value of groupSearchFilter defaults "(groupMemberField=*)".
     *
     * @return the field used as the search filter when searching for groups.
     */
    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }

    /**
     * Sets the field used as the search filter when searching for groups.
     * Value of groupSearchFilter defaults "(groupMemberField=*)".
     *
     * @param groupSearchFilter the field used as the search filter when searching for groups.
     */
    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
        JiveGlobals.setXMLProperty("ldap.groupSearchFilter", groupSearchFilter);
    }
}