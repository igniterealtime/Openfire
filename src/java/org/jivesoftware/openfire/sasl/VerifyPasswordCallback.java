/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sasl;

import javax.security.auth.callback.Callback;

import java.io.Serializable;


/**
 * This callback isused by SaslServer to determine if a password supplied by a 
 * client is verified.
 * Under most circumstances the PasswordCallback should be used instead, but its
 * use requires the underlying sercurity services have access to the stored password
 * to perform a comparison.
 * The security service provider instantiate and pass a VerifyPasswordCallback to the
 * handle method of a CallbackHandler to verify password information.
 *
 * @see javax.security.auth.callback.PasswordCallback
 * @see javax.security.auth.callback.CallbackHandler
 * @author Jay Kline
 */

public class VerifyPasswordCallback implements Callback, Serializable {

    private static final long serialVersionUID = -6393402725550707836L;

    private char[] password;

    private boolean verified;

    /**
     * Construct a <code>VerifyPasswordCallback</code>.
     * @param password the password to verify.
     */
    public VerifyPasswordCallback(char[] password) {
        this.password = (password == null ? null : (char[])password.clone());
        this.verified = false;
    }

    /**
     * Get the retrieved password.
     * @return the retrieved password, which may be null.
     */
    public char[] getPassword() {
        return (password == null ? null : (char[])password.clone());
    }

    /**
     * Clear the retrieved password.
     */
    public void clearPassword() {
        if (password != null) {
            for (int i = 0; i < password.length; i++) {
                password[i] = ' ';
            }
            password = null;
        }
    }

    /**
     * Indicate if this password is verified.
     * @param verified true if the password is verified; false otherwise
     */
    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    /**
     * Determines wether the password is verified.
     * @return true if the password is verified; false otherwise
     */
    public boolean getVerified() {
        return verified;
    }

}