/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import org.jivesoftware.messenger.auth.AuthFactory;
import org.jivesoftware.messenger.user.UserNotFoundException;

import javax.security.auth.callback.*;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import java.io.IOException;

/**
 * Callback handler that may be used when doing SASL authentication. A CallbackHandler may be
 * required depending on the SASL mechanism being used. Currently DIGEST-MD5 and CRAM-MD5 are
 * the only mechanisms that will require a callback handler.<p>
 *
 * Mechanisms that use a digest don't include a password so the server needs to use the stored
 * password of the user to compare it (somehow) with the specified digest. This operation requires
 * that the UserProvider being used supports passwords retrival. {@link SASLAuthentication} should
 * not offer these kind of SASL mechanisms if the user provider being in use does not support
 * passwords retrieval.
 *
 * @author Hao Chen
 */
public class XMPPCallbackHandler implements CallbackHandler {

	public XMPPCallbackHandler() {
	}

	public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {

		String realm = null;
		String name = null;

		for (int i = 0; i < callbacks.length; i++) {
			// Log.info("Callback: " + callbacks[i].getClass().getSimpleName());
			if (callbacks[i] instanceof RealmCallback) {
				realm = ((RealmCallback) callbacks[i]).getText();
				if (realm == null) {
					realm = ((RealmCallback) callbacks[i]).getDefaultText();
				}
				 //Log.info("RealmCallback: " + realm);
			} else if (callbacks[i] instanceof NameCallback) {
				name = ((NameCallback) callbacks[i]).getName();
				if (name == null) {
					name = ((NameCallback) callbacks[i]).getDefaultName();
				}
				 //Log.info("NameCallback: " + name);
			} else if (callbacks[i] instanceof PasswordCallback) {
				try {
                    // Get the password from the UserProvider. Some UserProviders may not support
                    // this operation
                    ((PasswordCallback) callbacks[i])
                            .setPassword(AuthFactory.getPassword(name).toCharArray());

                    //Log.info("PasswordCallback: "
					 //+ new String(((PasswordCallback) callbacks[i]).getPassword()));
				} catch (UserNotFoundException e) {
					throw new IOException(e.toString());
				}
			} else if (callbacks[i] instanceof AuthorizeCallback) {
				AuthorizeCallback authCallback = ((AuthorizeCallback) callbacks[i]);
				String authenId = authCallback.getAuthenticationID();
				String authorId = authCallback.getAuthorizationID();
                if (authenId.equals(authorId)) {
					authCallback.setAuthorized(true);
					authCallback.setAuthorizedID(authorId);
				}
				 //Log.info("AuthorizeCallback: authorId: " + authorId);
			} else {
				throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
			}
		}

	}
}
