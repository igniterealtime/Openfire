/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.sasl.VerifyPasswordCallback;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handler that may be used when doing SASL authentication. A CallbackHandler
 * may be required depending on the SASL mechanism being used.
 *
 * Mechanisms that use a digest don't include a password so the server needs to use the
 * stored password of the user to compare it (somehow) with the specified digest. This
 * operation requires that the UserProvider being used supports passwords retrieval.
 * {@link SASLAuthentication} should not offer these kinds of SASL mechanisms if the user
 * provider being in use does not support passwords retrieval.
 *
 * @author Hao Chen
 */
public class XMPPCallbackHandler implements CallbackHandler {

    private static final Logger Log = LoggerFactory.getLogger(XMPPCallbackHandler.class);

    public XMPPCallbackHandler() {
    }

    @Override
    public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException
    {
        String name = null;

        for (Callback callback : callbacks) {
            if (callback instanceof RealmCallback) {
                ((RealmCallback) callback).setText( XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
            }
            else if (callback instanceof NameCallback) {
                name = ((NameCallback) callback).getName();
                if (name == null) {
                    name = ((NameCallback) callback).getDefaultName();
                }
                Log.trace("NameCallback: {}", name);
            }
            else if (callback instanceof PasswordCallback) {
                try {
                    // Get the password from the UserProvider. Some UserProviders may not support this operation
                    ((PasswordCallback) callback).setPassword(AuthFactory.getPassword(name).toCharArray());
                    Log.trace("PasswordCallback");
                }
                catch (UserNotFoundException | UnsupportedOperationException e) {
                    throw new IOException(e.toString());
                }

            }
            else if (callback instanceof VerifyPasswordCallback) {
                Log.trace("VerifyPasswordCallback");
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
                Log.trace("AuthorizeCallback");
                AuthorizeCallback authCallback = ((AuthorizeCallback) callback);

                // Principal that authenticated - identity whose password was used.
                String authcid = authCallback.getAuthenticationID();

                // Username requested (not full JID) - identity to act as.
                String authzid = authCallback.getAuthorizationID();

                // Remove any REALM from the username. This is optional in the specifications, and it may cause
                // a lot of users to fail to log in if their clients is sending an incorrect value.
                if (authzid != null && authzid.contains("@")) {
                    authzid = authzid.substring(0, authzid.lastIndexOf("@"));
                }
                if (authcid.equals(authzid)) {
                    // Client perhaps made no request, get default username.
                    authzid = AuthorizationManager.map(authcid);
                    Log.trace("No username requested, using {}", authzid);
                }
                if (AuthorizationManager.authorize(authzid, authcid)) {
                    Log.trace("{} authorized to {}", authcid, authzid);
                    authCallback.setAuthorized(true);
                    authCallback.setAuthorizedID(authzid);
                }
                else {
                    Log.trace("{} not authorized to {}", authcid, authzid);
                    authCallback.setAuthorized(false);
                }
            }
            else {
                Log.debug("Unsupported callback: {}" + callback.getClass().getSimpleName());
                throw new UnsupportedCallbackException(callback, "Unrecognized Callback");
            }
        }
    }
}
