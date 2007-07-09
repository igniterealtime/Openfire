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


import java.util.Map;
import java.util.StringTokenizer;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 * Implements the PLAIN server-side mechanism.
 * (<a href="http://www.ietf.org/rfc/rfc4616.txt">RFC 4616</a>)<br />
 * <p>
 * client ----- {authzid, authcid, password} -----> server
 * </p>
 * Each paramater sent to the server is seperated by a null character
 * The authorization ID (authzid) may be empty.
 *
 * @author Jay Kline
 */

public class SaslServerPlainImpl implements SaslServer {

    private String principal;
    private String username; //requested authorization identity
    private String password;
    private CallbackHandler cbh;
    private boolean completed;
    private boolean aborted;
    private int counter;


    public SaslServerPlainImpl(String protocol, String serverFqdn, Map props, CallbackHandler cbh) throws SaslException {
        this.cbh = cbh;
        this.completed = false;
        this.counter = 0;
    }

    /**
     * Returns the IANA-registered mechanism name of this SASL server.
     * ("PLAIN").
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    public String getMechanismName() {
        return "PLAIN";
    }

    /**
     * Evaluates the response data and generates a challenge.
     *
     * If a response is received from the client during the authentication
     * process, this method is called to prepare an appropriate next
     * challenge to submit to the client. The challenge is null if the
     * authentication has succeeded and no more challenge data is to be sent
     * to the client. It is non-null if the authentication must be continued
     * by sending a challenge to the client, or if the authentication has
     * succeeded but challenge data needs to be processed by the client.
     * <tt>isComplete()</tt> should be called
     * after each call to <tt>evaluateResponse()</tt>,to determine if any further
     * response is needed from the client.
     *
     * @param response The non-null (but possibly empty) response sent
     * by the client.
     *
     * @return The possibly null challenge to send to the client.
     * It is null if the authentication has succeeded and there is
     * no more challenge data to be sent to the client.
     * @exception SaslException If an error occurred while processing
     * the response or generating a challenge.
     */
    public byte[] evaluateResponse(byte[] response)
        throws SaslException {
        if (completed) {
            throw new IllegalStateException("PLAIN authentication already completed");
        }
        if (aborted) {
            throw new IllegalStateException("PLAIN authentication previously aborted due to error");
        }
        try {
            if(response.length != 0) {
                String data = new String(response, "UTF8");
                StringTokenizer tokens = new StringTokenizer(data, "\0");
                if (tokens.countTokens() > 2) {
                    username = tokens.nextToken();
                    principal = tokens.nextToken();
                } else {
                    username = tokens.nextToken();
                    principal = username;
                }
                password = tokens.nextToken();
                NameCallback ncb = new NameCallback("PLAIN authentication ID: ",principal);
                VerifyPasswordCallback vpcb = new VerifyPasswordCallback(password.toCharArray());
                cbh.handle(new Callback[]{ncb,vpcb});

                if (vpcb.getVerified()) {
                    vpcb.clearPassword();
                    AuthorizeCallback acb = new AuthorizeCallback(principal,username);
                    cbh.handle(new Callback[]{acb});
                    username = acb.getAuthorizationID();
                    completed = true;
                } else {
                    throw new SaslException("PLAIN: user not authorized: "+principal);
                }
            } else {
                //Client gave no initial response
                if( counter++ > 1 ) {
                    throw new SaslException("PLAIN expects a response");
                }
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            aborted = true;
            throw new SaslException("UTF8 not available on platform", e);
        } catch (UnsupportedCallbackException e) {
            aborted = true;
            throw new SaslException("PLAIN authentication failed", e);
        } catch (IOException e) {
            aborted = true;
            throw new SaslException("PLAIN authentication failed", e);
        }
        return null;
    }

   /**
      * Determines whether the authentication exchange has completed.
      * This method is typically called after each invocation of
      * <tt>evaluateResponse()</tt> to determine whether the
      * authentication has completed successfully or should be continued.
      * @return true if the authentication exchange has completed; false otherwise.
      */
    public boolean isComplete() {
        return completed;
    }

    /**
     * Reports the authorization ID in effect for the client of this
     * session.
     * This method can only be called if isComplete() returns true.
     * @return The authorization ID of the client.
     * @exception IllegalStateException if this authentication session has not completed
     */
    public String getAuthorizationID() {
        if(completed) {
            return username;
        } else {
            throw new IllegalStateException("PLAIN authentication not completed");
        }
    }


    /**
     * Unwraps a byte array received from the client. PLAIN supports no security layer.
     * 
     * @throws SaslException if attempted to use this method.
     */
    public byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException {
        if(completed) {
            throw new IllegalStateException("PLAIN does not support integrity or privacy");
        } else {
            throw new IllegalStateException("PLAIN authentication not completed");
        }
    }

    /**
     * Wraps a byte array to be sent to the client. PLAIN supports no security layer.
     *
     * @throws SaslException if attempted to use this method.
     */
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException {
        if(completed) {
            throw new IllegalStateException("PLAIN does not support integrity or privacy");
        } else {
            throw new IllegalStateException("PLAIN authentication not completed");
        }
    }

    /**
     * Retrieves the negotiated property.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true); otherwise, an
     * <tt>IllegalStateException</tt> is thrown.
     *
     * @param propName the property
     * @return The value of the negotiated property. If null, the property was
     * not negotiated or is not applicable to this mechanism.
     * @exception IllegalStateException if this authentication exchange has not completed
     */

    public Object getNegotiatedProperty(String propName) {
        if (completed) {
            if (propName.equals(Sasl.QOP)) {
                return "auth";
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException("PLAIN authentication not completed");
        }
    }

     /**
      * Disposes of any system resources or security-sensitive information
      * the SaslServer might be using. Invoking this method invalidates
      * the SaslServer instance. This method is idempotent.
      * @throws SaslException If a problem was encountered while disposing
      * the resources.
      */
    public void dispose() throws SaslException {
        password = null;
        username = null;
        principal = null;
        completed = false;
    }
}
