/**
 * $Revision: 1116 $
 * $Date: 2005-03-10 15:18:08 -0800 (Thu, 10 Mar 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.user.UserNotFoundException;

import java.util.Set;
import java.util.HashSet;

/**
 * The hybrid auth provider allows up to three AuthProvider implementations to
 * be strung together to do chained authentication checking. The algorithm is
 * as follows:
 * <ol>
 *      <li>Attempt authentication using the primary provider. If that fails:
 *      <li>If the secondary provider is defined, attempt authentication (otherwise return).
 *          If that fails:
 *      <li>If the tertiary provider is defined, attempt authentication.
 * </ol>
 *
 * To enable this provider, set the following in the XML configuration file:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.auth.HybridAuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * The primary, secondary, and tertiary providers are configured as in the following example:
 *
 * <pre>
 * &lt;hybridAuthProvider&gt;
 *      &lt;primaryProvider&gt;
 *          &lt;className&gt;org.jivesoftware.openfire.auth.DefaultAuthProvider&lt;className&gt;
 *      &lt;/primaryProvider&gt;
 *      &lt;secondaryProvider&gt;
 *          &lt;className&gt;org.jivesoftware.openfire.auth.NativeAuthProvider&lt;/className&gt;
 *      &lt;/secondaryProvider&gt;
 * &lt;/hybridAuthProvider&gt;
 * </pre>
 *
 * Each of the chained providers can have a list of override users. If a user is in
 * an override list, authentication will only be attempted with the associated provider
 * (bypassing the chaining logic).<p>
 *
 * The full list of properties:
 * <ul>
 *      <li><tt>hybridAuthProvider.primaryProvider.className</tt> (required) -- the class name
 *          of the auth provider.
 *      <li><tt>hybridAuthProvider.primaryProvider.overrideList</tt> -- a comma-delimitted list
 *          of usernames for which authentication will only be tried with this provider.
 *      <li><tt>hybridAuthProvider.secondaryProvider.className</tt> -- the class name
 *          of the auth provider.
 *      <li><tt>hybridAuthProvider.secondaryProvider.overrideList</tt> -- a comma-delimitted list
 *          of usernames for which authentication will only be tried with this provider.
 *      <li><tt>hybridAuthProvider.tertiaryProvider.className</tt> -- the class name
 *          of the auth provider.
 *      <li><tt>hybridAuthProvider.tertiaryProvider.overrideList</tt> -- a comma-delimitted list
 *          of usernames for which authentication will only be tried with this provider.
 * </ul>
 *
 * The primary provider is required, but all other properties are optional. Each provider
 * should be configured as it is normally, using whatever XML configuration options it specifies.
 *
 * @author Matt Tucker
 */
public class HybridAuthProvider implements AuthProvider {

    private AuthProvider primaryProvider;
    private AuthProvider secondaryProvider;
    private AuthProvider tertiaryProvider;

    private Set<String> primaryOverrides = new HashSet<String>();
    private Set<String> secondaryOverrides = new HashSet<String>();
    private Set<String> tertiaryOverrides = new HashSet<String>();

    public HybridAuthProvider() {
        // Load primary, secondary, and tertiary auth providers.
        String primaryClass = JiveGlobals.getXMLProperty(
                "hybridAuthProvider.primaryProvider.className");
        if (primaryClass == null) {
            Log.error("A primary AuthProvider must be specified. Authentication will be disabled.");
            return;
        }
        try {
            Class c = ClassUtils.forName(primaryClass);
            primaryProvider = (AuthProvider)c.newInstance();
            // All providers must support plain auth.
            if (!primaryProvider.isPlainSupported()) {
                Log.error("Provider " + primaryClass + " must support plain authentication. " +
                        "Authentication disabled.");
                primaryProvider = null;
                return;
            }
            Log.debug("Primary auth provider: " + primaryClass);
        }
        catch (Exception e) {
            Log.error("Unable to load primary auth provider: " + primaryClass +
                    ". Authentication will be disabled.", e);
            return;
        }

        String secondaryClass = JiveGlobals.getXMLProperty(
                "hybridAuthProvider.secondaryProvider.className");
        if (secondaryClass != null) {
            try {
                Class c = ClassUtils.forName(secondaryClass);
                secondaryProvider = (AuthProvider)c.newInstance();
                // All providers must support plain auth.
                if (!secondaryProvider.isPlainSupported()) {
                    Log.error("Provider " + secondaryClass + " must support plain authentication. " +
                            "Authentication disabled.");
                    primaryProvider = null;
                    secondaryProvider = null;
                    return;
                }
                Log.debug("Secondary auth provider: " + secondaryClass);
            }
            catch (Exception e) {
                Log.error("Unable to load secondary auth provider: " + secondaryClass, e);
            }
        }

        String tertiaryClass = JiveGlobals.getXMLProperty(
                "hybridAuthProvider.tertiaryProvider.className");
        if (tertiaryClass != null) {
            try {
                Class c = ClassUtils.forName(tertiaryClass);
                tertiaryProvider = (AuthProvider)c.newInstance();
                // All providers must support plain auth.
                if (!tertiaryProvider.isPlainSupported()) {
                    Log.error("Provider " + tertiaryClass + " must support plain authentication. " +
                            "Authentication disabled.");
                    primaryProvider = null;
                    secondaryProvider = null;
                    tertiaryProvider = null;
                    return;
                }
                Log.debug("Tertiary auth provider: " + tertiaryClass);
            }
            catch (Exception e) {
                Log.error("Unable to load tertiary auth provider: " + tertiaryClass, e);
            }
        }

        // Now, load any overrides.
        String overrideList = JiveGlobals.getXMLProperty(
                "hybridAuthProvider.primaryProvider.overrideList", "");
        for (String user: overrideList.split(",")) {
            primaryOverrides.add(user.trim().toLowerCase());
        }

        if (secondaryProvider != null) {
            overrideList = JiveGlobals.getXMLProperty(
                    "hybridAuthProvider.secondaryProvider.overrideList", "");
            for (String user: overrideList.split(",")) {
                secondaryOverrides.add(user.trim().toLowerCase());
            }
        }

        if (tertiaryProvider != null) {
            overrideList = JiveGlobals.getXMLProperty(
                    "hybridAuthProvider.tertiaryProvider.overrideList", "");
            for (String user: overrideList.split(",")) {
                tertiaryOverrides.add(user.trim().toLowerCase());
            }
        }
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return false;
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        // Check overrides first.
        if (primaryOverrides.contains(username.toLowerCase())) {
            primaryProvider.authenticate(username, password);
            return;
        }
        else if (secondaryOverrides.contains(username.toLowerCase())) {
            secondaryProvider.authenticate(username, password);
            return;
        }
        else if (tertiaryOverrides.contains(username.toLowerCase())) {
            tertiaryProvider.authenticate(username, password);
            return;
        }

        // Now perform normal
        try {
            primaryProvider.authenticate(username, password);
        }
        catch (UnauthorizedException ue) {
            if (secondaryProvider != null) {
                try {
                    secondaryProvider.authenticate(username, password);
                }
                catch (UnauthorizedException ue2) {
                    if (tertiaryProvider != null) {
                        tertiaryProvider.authenticate(username, password);
                    }
                    else {
                        throw ue2;
                    }
                }
            }
            else {
                throw ue;
            }
        }
    }

    public void authenticate(String username, String token, String digest)
            throws UnauthorizedException
    {
        throw new UnauthorizedException("Digest authentication not supported.");
    }

    public String getPassword(String username)
            throws UserNotFoundException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    public void setPassword(String username, String password)
            throws UserNotFoundException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}