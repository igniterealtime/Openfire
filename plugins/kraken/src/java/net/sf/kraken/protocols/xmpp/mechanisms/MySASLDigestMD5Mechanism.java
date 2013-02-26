/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.xmpp.mechanisms;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;

import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.util.Base64;

/**
 * My Implementation of the SASL DIGEST-MD5 mechanism
 *
 */
public class MySASLDigestMD5Mechanism extends SASLMechanism {

	public MySASLDigestMD5Mechanism(SASLAuthentication saslAuthentication) {
		super(saslAuthentication);
	}

	protected void authenticate() throws IOException, XMPPException {
		String[] mechanisms = { getName() };
		Map<String, String> props = new HashMap<String, String>();
		sc = Sasl.createSaslClient(mechanisms, null, "xmpp", hostname, props, this);

		super.authenticate();
	}

    public void authenticate(String username, String host, String password) throws IOException, XMPPException {
        this.authenticationId = username;
        this.password = password;
        this.hostname = host;

        String[] mechanisms = { getName() };
        Map<String,String> props = new HashMap<String,String>();
        sc = Sasl.createSaslClient(mechanisms, null, "xmpp", host, props, this);
        super.authenticate();
    }

    public void authenticate(String username, String host, CallbackHandler cbh) throws IOException, XMPPException {
        String[] mechanisms = { getName() };
        Map<String,String> props = new HashMap<String,String>();
        sc = Sasl.createSaslClient(mechanisms, null, "xmpp", host, props, cbh);
        super.authenticate();
    }

	protected String getName() {
		return "DIGEST-MD5";
	}

	public void challengeReceived(String challenge) throws IOException {
		// Build the challenge response stanza encoding the response text
		StringBuilder stanza = new StringBuilder();

		byte response[];
		if (challenge != null) {
			response = sc.evaluateChallenge(Base64.decode(challenge));
		} else {
			response = sc.evaluateChallenge(null);
		}

		String authenticationText="";

		if (response != null) { // fix from 3.1.1
			authenticationText = Base64.encodeBytes(response, Base64.DONT_BREAK_LINES);
			if (authenticationText.equals("")) {
				authenticationText = "=";
			}
		}

		stanza.append("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
		stanza.append(authenticationText);
		stanza.append("</response>");
        

		// Send the authentication to the server
		getSASLAuthentication().send(new Response(authenticationText));
	}
}
