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

package org.jivesoftware.messenger.auth;

import org.jivesoftware.util.*;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;

import javax.mail.Store;
import javax.mail.Session;
import javax.mail.NoSuchProviderException;

/**
 * An AuthProvider that authenticates using a POP3 server. It will automatically create
 * local user accounts as needed. The properties to configure the provider are as follows:
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
 *              value is false.
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

    /**
     * Initialiazes the POP3AuthProvider with values from the global config file.
     */
    public POP3AuthProvider() {
        if (Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.authCache.enabled")).booleanValue()) {
            int maxSize = JiveGlobals.getXMLProperty("pop3.authCache.size", 512*1024);
            long maxLifetime = (long)JiveGlobals.getXMLProperty("pop3.authCache.maxLifetime",
								(int)JiveConstants.HOUR * 1);
            authCache = new Cache("POP3 Auth Cache", maxSize, maxLifetime);
        }

        useSSL = Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.ssl")).booleanValue();
        authRequiresDomain = Boolean.valueOf(JiveGlobals.getXMLProperty("pop3.authRequiresDomain")
                ).booleanValue();

        host = JiveGlobals.getXMLProperty("pop3.host");
        if (host == null || host.length() < 1) {
            throw new IllegalArgumentException("pop3.host is null or empty");
        }

        domain = JiveGlobals.getXMLProperty("pop3.domain");

        port = JiveGlobals.getXMLProperty("pop3.port", useSSL ? 995 : 110);
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

        Session session = Session.getDefaultInstance(System.getProperties());
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
        catch(Exception e) {
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
                // Create user; use a random password for better safety in the future.
                userManager.createUser(username, StringUtils.randomString(8), null, email);
            }
            catch (UserAlreadyExistsException uaee) {

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
}