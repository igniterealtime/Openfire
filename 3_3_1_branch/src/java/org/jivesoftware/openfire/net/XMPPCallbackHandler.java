/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.sasl.AuthorizationManager;
import org.jivesoftware.openfire.user.UserNotFoundException;

import javax.security.auth.callback.*;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import java.io.IOException;

/**
 * Callback handler that may be used when doing SASL authentication. A CallbackHandler
 * may be required depending on the SASL mechanism being used.<p>
 *
 * Mechanisms that use a digest don't include a password so the server needs to use the
 * stored password of the user to compare it (somehow) with the specified digest. This
 * operation requires that the UserProvider being used supports passwords retrival.
 * {@link SASLAuthentication} should not offer these kind of SASL mechanisms if the user
 * provider being in use does not support passwords retrieval.
 *
 * @author Hao Chen
 */
public class XMPPCallbackHandler implements CallbackHandler {

    public XMPPCallbackHandler() {
    }

    public void handle(final Callback[] callbacks)
            throws IOException, UnsupportedCallbackException {

        String realm;
        String name = null;

        for (int i = 0; i < callbacks.length; i++) {
            // Log.info("Callback: " + callbacks[i].getClass().getSimpleName());
            if (callbacks[i] instanceof RealmCallback) {
                realm = ((RealmCallback) callbacks[i]).getText();
                if (realm == null) {
                    realm = ((RealmCallback) callbacks[i]).getDefaultText();
                }
                //Log.info("RealmCallback: " + realm);
            }
            else if (callbacks[i] instanceof NameCallback) {
                name = ((NameCallback) callbacks[i]).getName();
                if (name == null) {
                    name = ((NameCallback) callbacks[i]).getDefaultName();
                }
                //Log.info("NameCallback: " + name);
            }
            else if (callbacks[i] instanceof PasswordCallback) {
                try {
                    // Get the password from the UserProvider. Some UserProviders may not support
                    // this operation
                    ((PasswordCallback) callbacks[i])
                            .setPassword(AuthFactory.getPassword(name).toCharArray());

                    //Log.info("PasswordCallback: "
                    //+ new String(((PasswordCallback) callbacks[i]).getPassword()));
                }
                catch (UserNotFoundException e) {
                    throw new IOException(e.toString());
                }
            }
            else if (callbacks[i] instanceof AuthorizeCallback) {
                AuthorizeCallback authCallback = ((AuthorizeCallback) callbacks[i]);
                String authenId =
                        authCallback.getAuthenticationID(); // Principal that authenticated
                String authorId =
                        authCallback.getAuthorizationID();  // Username requested (not full JID)
                if (AuthorizationManager.authorize(authorId, authenId)) {
                    authCallback.setAuthorized(true);
                    authCallback.setAuthorizedID(authorId);
                    Log.debug(authenId + " authorized to " + authorId);
                }
                else {
                    Log.debug(authenId + " not authorized to " + authorId);
                }
            }
            else {
                throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
            }
        }
    }
}