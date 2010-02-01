/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.sasl.VerifyPasswordCallback;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger Log = LoggerFactory.getLogger(XMPPCallbackHandler.class);

    public XMPPCallbackHandler() {
    }

    public void handle(final Callback[] callbacks)
            throws IOException, UnsupportedCallbackException {


        String realm;
        String name = null;

        for (Callback callback : callbacks) {
            if (callback instanceof RealmCallback) {
                realm = ((RealmCallback) callback).getText();
                if (realm == null) {
                    realm = ((RealmCallback) callback).getDefaultText();
                }
                //Log.debug("XMPPCallbackHandler: RealmCallback: " + realm);
            }
            else if (callback instanceof NameCallback) {
                name = ((NameCallback) callback).getName();
                if (name == null) {
                    name = ((NameCallback) callback).getDefaultName();
                }
                //Log.debug("XMPPCallbackHandler: NameCallback: " + name);
            }
            else if (callback instanceof PasswordCallback) {
                try {
                    // Get the password from the UserProvider. Some UserProviders may not support
                    // this operation
                    ((PasswordCallback) callback)
                            .setPassword(AuthFactory.getPassword(name).toCharArray());

                    //Log.debug("XMPPCallbackHandler: PasswordCallback");
                }
                catch (UserNotFoundException e) {
                    throw new IOException(e.toString());
                }
                catch (UnsupportedOperationException uoe) {
                    throw new IOException(uoe.toString());
                }

            }
            else if (callback instanceof VerifyPasswordCallback) {
                //Log.debug("XMPPCallbackHandler: VerifyPasswordCallback");
                VerifyPasswordCallback vpcb = (VerifyPasswordCallback) callback;
                try {
                    AuthToken at = AuthFactory.authenticate(name, new String(vpcb.getPassword()));
                    vpcb.setVerified((at != null));
                }
                catch (Exception e) {
                    vpcb.setVerified(false);
                }
            }
            else if (callback instanceof AuthorizeCallback) {
                //Log.debug("XMPPCallbackHandler: AuthorizeCallback");
                AuthorizeCallback authCallback = ((AuthorizeCallback) callback);
                // Principal that authenticated
                String principal = authCallback.getAuthenticationID();
                // Username requested (not full JID)
                String username = authCallback.getAuthorizationID();
                // Remove any REALM from the username. This is optional in the spec and it may cause
                // a lot of users to fail to log in if their clients is sending an incorrect value
                if (username != null && username.contains("@")) {
                    username = username.substring(0, username.lastIndexOf("@"));
                }
                if (principal.equals(username)) {
                    //client perhaps made no request, get default username
                    username = AuthorizationManager.map(principal);
                    if (Log.isDebugEnabled()) {
                        //Log.debug("XMPPCallbackHandler: no username requested, using " + username);
                    }
                }
                if (AuthorizationManager.authorize(username, principal)) {
                    if (Log.isDebugEnabled()) {
                        //Log.debug("XMPPCallbackHandler: " + principal + " authorized to " + username);
                    }
                    authCallback.setAuthorized(true);
                    authCallback.setAuthorizedID(username);
                }
                else {
                    if (Log.isDebugEnabled()) {
                        //Log.debug("XMPPCallbackHandler: " + principal + " not authorized to " + username);
                    }
                    authCallback.setAuthorized(false);
                }
            }
            else {
                if (Log.isDebugEnabled()) {
                    //Log.debug("XMPPCallbackHandler: Callback: " + callback.getClass().getSimpleName());
                }
                throw new UnsupportedCallbackException(callback, "Unrecognized Callback");
            }
        }
    }
}