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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.naming.CommunicationException;
import javax.naming.ldap.Rdn;

/**
 * Implementation of auth provider interface for LDAP authentication service plug-in.
 * Only plaintext authentication is currently supported.<p>
 *
 * Optionally, an authentication cache can be enabled. When enabled, a hashed version
 * of the user's password is cached for a variable length of time (2 hours by default).
 * This can decrease load on the directory and preserve some level of service even
 * when the directory becomes unavailable for a period of time.<ul>
 *
 *  <li>{@code ldap.authCache.enabled} -- true to enable the auth cache.</li>
 *  <li>{@code ldap.authCache.size} -- size in bytes of the auth cache. If property is
 *      not set, the default value is 524288 (512 K).</li>
 *  <li>{@code ldap.authCache.maxLifetime} -- maximum amount of time a hashed password
 *      can be cached in milleseconds. If property is not set, the default value is
 *      7200000 (2 hours).</li>
 * </ul>
 *
 * @author Matt Tucker
 */
public class LdapAuthProvider implements AuthProvider {

    private static final Logger Log = LoggerFactory.getLogger(LdapAuthProvider.class);

    private LdapManager manager;
    private Cache<String, String> authCache = null;

    public LdapAuthProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("ldap.authCache.enabled");

        manager = LdapManager.getInstance();
        if (JiveGlobals.getBooleanProperty("ldap.authCache.enabled", false)) {
            String cacheName = "LDAP Authentication";
            authCache = CacheFactory.createCache(cacheName);
        }
    }

    @Override
    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null || "".equals(password.trim())) {
            throw new UnauthorizedException();
        }

        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }

        // Un-escape username.
        username = JID.unescapeNode(username);

        // If cache is enabled, see if the auth is in cache.
        if (authCache != null && authCache.containsKey(username)) {
            String hash = authCache.get(username);
            if (StringUtils.hash(password).equals(hash)) {
                return;
            }
        }

        Rdn[] userRDN;
        try {
            // The username by itself won't help us much with LDAP since we
            // need a fully qualified dn. We could make the assumption that
            // the baseDN would always be the location of user profiles. For
            // example if the baseDN was set to "ou=People, o=jivesoftare, o=com"
            // then we would be able to directly load users from that node
            // of the LDAP tree. However, it's a poor assumption that only a
            // flat structure will be used. Therefore, we search all sub-trees
            // of the baseDN for the username (assuming the user has not disabled
            // sub-tree searching). So, if the baseDN is set to
            // "o=jivesoftware, o=com" then a search will include the "People"
            // node as well all the others under the base.
            userRDN = manager.findUserRDN(username);

            // See if the user authenticates.
            if (!manager.checkAuthentication(userRDN, password)) {
                throw new UnauthorizedException("Username and password don't match");
            }
        }
        catch (CommunicationException e) {
            // Log error here since it will be wrapped with an UnauthorizedException that
            // is never logged
            Log.error("Error connecting to LDAP server", e);
            throw new UnauthorizedException(e);
        }
        catch (Exception e) {
            throw new UnauthorizedException(e);
        }

        // If cache is enabled, add the item to cache.
        if (authCache != null) {
            authCache.put(username, StringUtils.hash(password));
        }
    }

    @Override
    public String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException
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
