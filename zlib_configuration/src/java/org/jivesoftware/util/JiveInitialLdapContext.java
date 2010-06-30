/**
 * $RCSfile$
 * $Revision: 2698 $
 * $Date: 2005-08-19 15:28:16 -0300 (Fri, 19 Aug 2005) $
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

package org.jivesoftware.util;

import java.util.Hashtable;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSession;

/**
 * A class that extends the Java's InitialLdapContext class with
 * two properties to store the SSLSession and the StartTlsResponse
 * objects. This is necessary when using the StartTLS extension.
 */
public class JiveInitialLdapContext extends InitialLdapContext {

	private StartTlsResponse tlsResp;
	private SSLSession sslSess;
	
	public JiveInitialLdapContext(Hashtable<?, ?> arg0, Control[] arg1)
			throws NamingException {
		super(arg0, arg1);
	}
	
	public JiveInitialLdapContext() throws NamingException {
		super();
	}

	public StartTlsResponse getTlsResponse() {
		return tlsResp;
	}

	public void setTlsResponse(StartTlsResponse tlsResp) {
		this.tlsResp = tlsResp;
	}

	public SSLSession getSslSession() {
		return sslSess;
	}

	public void setSslSession(SSLSession sslSess) {
		this.sslSess = sslSess;
	}

}
