/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
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

package org.jivesoftware.openfire.net;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.ssl.X509KeyManager;

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
	
	private static final Logger Log = LoggerFactory.getLogger(SSLJiveKeyManager.class);

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
