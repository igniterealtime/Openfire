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

package org.jivesoftware.openfire.auth;

import java.util.Properties;

import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An AuthProvider that authenticates using a POP3 server. It will automatically create
 * local user accounts as needed. To enable this provider, set system properties as follows:
 *
 * <ul>
 * <li><tt>provider.auth.className = org.jivesoftware.openfire.auth.POP3AuthProvider</tt></li>
 * <li><tt>provider.user.className = org.jivesoftware.openfire.user.POP3UserProvider</tt></li>
 * </ul>
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

    private static final Logger Log = LoggerFactory.getLogger(POP3AuthProvider.class);

    private Cache<String, String> authCache = null;
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
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("pop3.authCache.enabled");
        JiveGlobals.migrateProperty("pop3.ssl");
        JiveGlobals.migrateProperty("pop3.authRequiresDomain");
        JiveGlobals.migrateProperty("pop3.host");
        JiveGlobals.migrateProperty("pop3.debug");
        JiveGlobals.migrateProperty("pop3.domain");
        JiveGlobals.migrateProperty("pop3.port");

        if (Boolean.valueOf(JiveGlobals.getProperty("pop3.authCache.enabled"))) {
            String cacheName = "POP3 Authentication";
            authCache = CacheFactory.createCache(cacheName);
        }

        useSSL = Boolean.valueOf(JiveGlobals.getProperty("pop3.ssl"));
        authRequiresDomain = Boolean.valueOf(JiveGlobals.getProperty("pop3.authRequiresDomain"));

        host = JiveGlobals.getProperty("pop3.host");
        if (host == null || host.length() < 1) {
            throw new IllegalArgumentException("pop3.host is null or empty");
        }

        debugEnabled = Boolean.valueOf(JiveGlobals.getProperty("pop3.debug"));

        domain = JiveGlobals.getProperty("pop3.domain");

        port = JiveGlobals.getIntProperty("pop3.port", useSSL ? 995 : 110);

        if (Log.isDebugEnabled()) {
            Log.debug("POP3AuthProvider: Created new POP3AuthProvider instance, fields:");
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

    @Override
    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            }
        } else {
            // Unknown domain. Return authentication failed.
            throw new UnauthorizedException();
        }

        Log.debug("POP3AuthProvider.authenticate("+username+", ******)");

            // If cache is enabled, see if the auth is in cache.
            if (authCache != null && authCache.containsKey(username)) {
                String hash = authCache.get(username);
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
            Log.error(e.getMessage(), e);
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
            Log.error(e.getMessage(), e);
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
                Log.debug("POP3AuthProvider: Automatically creating new user account for " + username);
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

    @Override
    public String getPassword(String username)
            throws UserNotFoundException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

     @Override
     public void setPassword(String username, String password) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsPasswordRetrieval() {
        return false;
    }
    
    @Override
    public boolean isScramSupported() {
        return false;
    }

    @Override
    public String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }
}
