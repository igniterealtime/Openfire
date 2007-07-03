/**
 * $RCSfile$
 * $Revision: 1765 $
 * $Date: 2005-08-10 22:37:59 -0700 (Wed, 10 Aug 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.*;
import com.cenqua.shaj.Shaj;

import java.net.URL;
import java.io.File;
import java.lang.reflect.Field;

/**
 * Authenticates using the native operating system authentication method. On Windows,
 * this means Win32 authentication; on Unix/Linux, PAM authentication. New user accounts
 * will be created automatically as needed.<p>
 *
 * Authentication is handled using the <a href="http://opensource.cenqua.com/shaj">Shaj</a>
 * library. In order for this provider to work, the appropriate native library must be loaded.
 * The appropriate native library must be manually moved from the resources/nativeAuth
 * directory to the lib directory.<p>
 *
 * To enable this provider, set the following in the XML configuration file:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.auth.NativeAuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 *     &lt;user&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.user.NativeUserProvider&lt;/className&gt;
 *     &lt;/user&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * The properties to configure the provider are as follows:
 *
 * <ul>
 *      <li>nativeAuth.domain -- <i> on Windows, the domain to use for authentication.
 *      If the value is not set, the machine's default domain will be used or standard OS
 *      auth will be used if the machine is not part of a domain. On Unix/Linux, this
 *      value specifies the PAM module to use for authentication. If the value is not set,
 *      the PAM module "other" will be used.
 * </ul>
 *
 * For more information about configuring the domain value and other aspects of Shaj,
 * please see: <a href="http://opensource.cenqua.com/shaj/doc.html">
 * http://opensource.cenqua.com/shaj/doc.html</a>.
 *
 * @author Matt Tucker
 */
public class NativeAuthProvider implements AuthProvider {

    private String domain;

    public NativeAuthProvider() {
        this.domain = JiveGlobals.getXMLProperty("nativeAuth.domain");

        // Configure the library path so that we can load the shaj native library
        // from the Openfire lib directory.
        // Find the root path of this class.
        try {
            String binaryPath = (new URL(Shaj.class.getProtectionDomain()
                    .getCodeSource().getLocation(), ".")).openConnection()
                    .getPermission().getName();
            binaryPath = (new File(binaryPath)).getCanonicalPath();

            // Add the binary path to "java.library.path".
            String newLibPath = binaryPath + File.pathSeparator +
                    System.getProperty("java.library.path");
            System.setProperty("java.library.path", newLibPath);
            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(System.class.getClassLoader(), null);
        }
        catch (Exception e) {
            Log.error(e);
        }

        // Configure Shaj to log output to the Openfire logger.
        com.cenqua.shaj.log.Log.Factory.setInstance(new com.cenqua.shaj.log.Log() {
            public boolean isDebug() {
                return Log.isDebugEnabled();
            }

            public void error(String string) {
                Log.error(string);
            }

            public void error(String string, Throwable throwable) {
                Log.error(string, throwable);
            }

            public void debug(String string) {
                Log.debug(string);
            }
        });
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getName())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }
        try {
            // Some native authentication mechanisms appear to not handle high load
            // very well. Therefore, synchronize access to Shaj to throttle auth checks.
            synchronized (this) {
                if (!Shaj.checkPassword(domain, username, password)) {
                    throw new UnauthorizedException();
                }
            }
        }
        catch (UnauthorizedException ue) {
            throw ue;
        }
        catch (Exception e) {
            throw new UnauthorizedException(e);
        }

        // See if the user exists in the database. If not, automatically create them.
        UserManager userManager = UserManager.getInstance();
        try {
            userManager.getUser(username);
        }
        catch (UserNotFoundException unfe) {
            try {
                Log.debug("Automatically creating new user account for " + username);
                // Create user; use a random password for better safety in the future.
                // Note that we have to go to the user provider directly -- because the
                // provider is read-only, UserManager will usually deny access to createUser.
                UserProvider provider = UserManager.getUserProvider();
                if (!(provider instanceof NativeUserProvider)) {
                    Log.error("Error: not using NativeUserProvider so authentication with " +
                            "NativeAuthProvider will likely fail. Using: " +
                            provider.getClass().getName());
                }
                UserManager.getUserProvider().createUser(username, StringUtils.randomString(8),
                        null, null);
            }
            catch (UserAlreadyExistsException uaee) {
                // Ignore.
            }
        }
    }

    public void authenticate(String username, String token, String digest)
            throws UnauthorizedException
    {
        throw new UnsupportedOperationException();
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
