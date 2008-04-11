/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.sasl.VerifyPasswordCallback;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;

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
                catch (UnauthorizedException e) {
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