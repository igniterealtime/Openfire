/**
 * $RCSfile$
 * $Revision: 2654 $
 * $Date: 2005-08-14 14:40:32 -0300 (Sun, 14 Aug 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;

import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Properties;

/**
 * An AuthProvider that authenticates using a POP3 server. It will automatically create
 * local user accounts as needed. To enable this provider, edit the XML config file
 * file and set:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.auth.POP3AuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 *     &lt;user&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.user.POP3UserProvider&lt;/className&gt;
 *     &lt;/user&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * The properties to configure the provider are as follows:
 *
 * <ul>
 *      <li>pop3.host -- <i>(required)</i> the name (or IP) of the POP3 server.
 *      <li>pop.port -- the port of the POP3 server. The default value is 110 for standard
 *              connections and 995 for SSL connections.
 *      <li>pop3.domain -- the mail domain (e.g. gmail.com).
 *      <li>pop3.authRequiresDomain -- set this to true if the POP3 server requires a
 *              full email address for authentication (foo@example.com) rather than just
 *              a username (foo). The default value is false.
 *      <li>pop3.ssl -- true if an SSL connection to the POP3 server should be used. The default
 *              is false.
 *      <li>pop3.debug -- true if debugging output for the POP3 connection should be enabled. The
 *              default is false.
 *      <li>pop3.authCache.enabled -- true if authentication checks should be cached locally.
 *              This will decrease load on the POP3 server if users continually authenticate.
 *              The default value is false.
 *      <li>pop3.authCache.size -- the maximum size of the authentication cache (in bytes). The
 *              default value is 512 K.
 *      <li>pop3.authCache.maxLifetime -- the maximum lifetime of items in the authentication
 *              cache (in milliseconds). The default value is one hour.
 * </ul>
 *
 * @author Sean Meiners
 */
public class POP3AuthProvider implements AuthProvider {

    private Cache authCache = null;
    private String host = null;
    private String domain = null;
    private int port = -1;
    private boolean useSSL = false;
    private boolean authRequiresDomain = false;
    private boolean debugEnabled;

    /**
     * Initialiazes the POP3AuthProvider with values from the global config file.
     */
    public POP3AuthProvider() {
        if (Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.authCache.enabled"))) {
            String cacheName = "POP3 Authentication";
            authCache = CacheFactory.createCache(cacheName);
        }

        useSSL = Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.ssl"));
        authRequiresDomain = Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.authRequiresDomain"));

        host = JiveGlobals.getXMLProperty("pop3.host");
        if (host == null || host.length() < 1) {
            throw new IllegalArgumentException("pop3.host is null or empty");
        }

        debugEnabled = Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.debug"));

        domain = JiveGlobals.getXMLProperty("pop3.domain");

        port = JiveGlobals.getXMLProperty("pop3.port", useSSL ? 995 : 110);

        if (Log.isDebugEnabled()) {
            Log.debug("Created new POP3AuthProvider instance, fields:");
            Log.debug("\t host: " + host);
            Log.debug("\t port: " + port);
            Log.debug("\t domain: " + domain);
            Log.debug("\t useSSL: " + useSSL);
            Log.debug("\t authRequiresDomain: " + authRequiresDomain);
            Log.debug("\t authCacheEnabled: " + (authCache != null));
            if (authCache != null) {
                Log.debug("\t authCacheSize: " + authCache.getCacheSize());
                Log.debug("\t authCacheMaxLifetime: " + authCache.getMaxLifetime());
            }
        }
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }

        Log.debug("POP3AuthProvider.authenticate("+username+", ******)");

            // If cache is enabled, see if the auth is in cache.
            if (authCache != null && authCache.containsKey(username)) {
                String hash = (String)authCache.get(username);
                if (StringUtils.hash(password).equals(hash)) {
                    return;
                }
            }

        Properties mailProps = new Properties();
        mailProps.setProperty("mail.debug", String.valueOf(debugEnabled));
        Session session = Session.getInstance(mailProps, null);
        Store store;
        try {
            store = session.getStore(useSSL ? "pop3s" : "pop3");
        }
        catch(NoSuchProviderException e) {
            Log.error(e);
            throw new UnauthorizedException(e);
        }

        try {
            if (authRequiresDomain) {
                store.connect(host, port, username + "@" + domain, password);
            }
            else {
                store.connect(host, port, username, password);
            }
        }
        catch(Exception e) {
            Log.error(e);
            throw new UnauthorizedException(e);
        }

        if (! store.isConnected()) {
            throw new UnauthorizedException("Could not authenticate user");
        }

        try {
            store.close();
        }
        catch (Exception e) {
            // Ignore.
        }

        // If cache is enabled, add the item to cache.
        if (authCache != null) {
            authCache.put(username, StringUtils.hash(password));
        }

        // See if the user exists in the database. If not, automatically create them.
        UserManager userManager = UserManager.getInstance();
        try {
            userManager.getUser(username);
        }
        catch (UserNotFoundException unfe) {
            String email = username + "@" + (domain!=null?domain:host);
            try {
                Log.debug("Automatically creating new user account for " + username);
                // Create user; use a random password for better safety in the future.
                // Note that we have to go to the user provider directly -- because the
                // provider is read-only, UserManager will usually deny access to createUser.
                UserManager.getUserProvider().createUser(username, StringUtils.randomString(8),
                        null, email);
            }
            catch (UserAlreadyExistsException uaee) {
                // Ignore.
            }
        }
    }

    public void authenticate(String username, String token, String digest)
            throws UnauthorizedException
    {
        throw new UnauthorizedException("Digest authentication not supported.");
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return false;
    }

    public String getPassword(String username)
            throws UserNotFoundException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

     public void setPassword(String username, String password) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}