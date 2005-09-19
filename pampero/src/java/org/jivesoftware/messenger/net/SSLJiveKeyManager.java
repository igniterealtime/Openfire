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

package org.jivesoftware.messenger.net;

import com.sun.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * A skeleton placeholder for developers wishing to implement their own custom
 * key manager. In future revisions we may expand the skeleton code if customers
 * request assistance in creating custom key managers.
 * <p/>
 * The key manager is an essential part of server SSL support. Typically you
 * will implement a custom key manager to retrieve certificates from repositories
 * that are not of standard Java types (e.g. obtaining them from LDAP or a JDBC database).
 *
 * @author Iain Shigeoka
 */
public class SSLJiveKeyManager implements X509KeyManager {
    public String[] getClientAliases(String s, Principal[] principals) {
        return new String[0];
    }

    public String chooseClientAlias(String s, Principal[] principals) {
        return null;
    }

    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        return null;
    }

    public String[] getServerAliases(String s, Principal[] principals) {
        return new String[0];
    }

    public String chooseServerAlias(String s, Principal[] principals) {
        return null;
    }

    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
        return null;
    }

    public X509Certificate[] getCertificateChain(String s) {
        return new X509Certificate[0];
    }

    public PrivateKey getPrivateKey(String s) {
        return null;
    }
}
