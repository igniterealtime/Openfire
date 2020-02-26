/*
 * Copyright 2015 Surevine Ltd
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

package org.jivesoftware.openfire.sasl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the SCRAM-SHA-1 server-side mechanism.
 *
 * @author Richard Midwinter
 */
public class ScramSha1SaslServer implements SaslServer {
    public static final SystemProperty<Integer> ITERATION_COUNT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("sasl.scram-sha-1.iteration-count")
        .setDefaultValue(ScramUtils.DEFAULT_ITERATION_COUNT)
        .setDynamic(Boolean.TRUE)
        .build();
    private static final Logger Log = LoggerFactory.getLogger(ScramSha1SaslServer.class);
    private static final Pattern
            CLIENT_FIRST_MESSAGE = Pattern.compile("^(([pny])=?([^,]*),([^,]*),)(m?=?[^,]*,?n=([^,]*),r=([^,]*),?.*)$"),
            CLIENT_FINAL_MESSAGE = Pattern.compile("(c=([^,]*),r=([^,]*)),p=(.*)$");

    private String username;
    private State state = State.INITIAL;
    private String nonce;
    private String serverFirstMessage;
    private String clientFirstMessageBare;
    private SecureRandom random = new SecureRandom();

    private enum State {
        INITIAL,
        IN_PROGRESS,
        COMPLETE;
    }
    
    public ScramSha1SaslServer() {
    }

    /**
     * Returns the IANA-registered mechanism name of this SASL server.
     * ("SCRAM-SHA-1").
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    @Override
    public String getMechanismName() {
        return "SCRAM-SHA-1";
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
     * {@code isComplete()} should be called
     * after each call to {@code evaluateResponse()},to determine if any further
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
    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        try {
            byte[] challenge;
            switch (state)
            {
                case INITIAL:
                    challenge = generateServerFirstMessage(response);
                    state = State.IN_PROGRESS;
                    break;
                case IN_PROGRESS:
                    challenge = generateServerFinalMessage(response);
                    state = State.COMPLETE;
                    break;
                case COMPLETE:
                    if (response == null || response.length == 0)
                    {
                        challenge = new byte[0];
                        break;
                    }
                default:
                    throw new SaslException("No response expected in state " + state);

            }
            return challenge;
        } catch (RuntimeException ex) {
           throw new SaslException("Unexpected exception while evaluating SASL response.", ex);
        }
    }

    /**
     * First response returns:
     *   - the nonce (client nonce appended with our own random UUID)
     *   - the salt
     *   - the number of iterations
     */
    private byte[] generateServerFirstMessage(final byte[] response) throws SaslException {
        String clientFirstMessage = new String(response, StandardCharsets.UTF_8);
        Matcher m = CLIENT_FIRST_MESSAGE.matcher(clientFirstMessage);
        if (!m.matches()) {
            throw new SaslException("Invalid first client message");
        }
//        String gs2Header = m.group(1);
//        String gs2CbindFlag = m.group(2);
//        String gs2CbindName = m.group(3);
//        String authzId = m.group(4);
        clientFirstMessageBare = m.group(5);
        username = m.group(6);
        String clientNonce = m.group(7);
        nonce = clientNonce + UUID.randomUUID().toString();

        serverFirstMessage = String.format("r=%s,s=%s,i=%d", nonce, DatatypeConverter.printBase64Binary(getSalt(username)),
                getIterations(username));
        return serverFirstMessage.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Final response returns the server signature.
     */
    private byte[] generateServerFinalMessage(final byte[] response) throws SaslException {
        String clientFinalMessage = new String(response, StandardCharsets.UTF_8);
        Matcher m = CLIENT_FINAL_MESSAGE.matcher(clientFinalMessage);
        if (!m.matches()) {
            throw new SaslException("Invalid client final message");
        }

        String clientFinalMessageWithoutProof = m.group(1);
//        String channelBinding = m.group(2);
        String clientNonce = m.group(3);
        String proof = m.group(4);
        
        if (!nonce.equals(clientNonce)) {
            throw new SaslException("Client final message has incorrect nonce value");
        }

        try {
            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;
            byte[] storedKey = getStoredKey( username );
            if (storedKey == null) {
                throw new SaslException("No stored key for user '"+username+"'");
            }
            byte[] serverKey = getServerKey(username);
            if (serverKey == null) {
                throw new SaslException("No server key for user '"+username+"'");
            }

            byte[] clientSignature = ScramUtils.computeHmac(storedKey, authMessage);
            byte[] serverSignature = ScramUtils.computeHmac(serverKey, authMessage);
            
            byte[] clientKey = clientSignature.clone();
            byte[] decodedProof = DatatypeConverter.parseBase64Binary(proof);
            for (int i = 0; i < clientKey.length; i++) {
                clientKey[i] ^= decodedProof[i];
            }

            if (!Arrays.equals(storedKey, MessageDigest.getInstance("SHA-1").digest(clientKey))) {
                throw new SaslException("Authentication failed for: '"+username+"'");
            }
            return ("v=" + DatatypeConverter.printBase64Binary(serverSignature))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (UserNotFoundException | NoSuchAlgorithmException e) {
            throw new SaslException(e.getMessage(), e);
        }
    }

   /**
      * Determines whether the authentication exchange has completed.
      * This method is typically called after each invocation of
      * {@code evaluateResponse()} to determine whether the
      * authentication has completed successfully or should be continued.
      * @return true if the authentication exchange has completed; false otherwise.
      */
    @Override
    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    /**
     * Reports the authorization ID in effect for the client of this
     * session.
     * This method can only be called if isComplete() returns true.
     * @return The authorization ID of the client.
     * @exception IllegalStateException if this authentication session has not completed
     */
    @Override
    public String getAuthorizationID() {
        if (isComplete()) {
            return username;
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

    /**
     * Unwraps a byte array received from the client. SCRAM-SHA-1 supports no security layer.
     * 
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException("SCRAM-SHA-1 does not support integrity or privacy");
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

    /**
     * Wraps a byte array to be sent to the client. SCRAM-SHA-1 supports no security layer.
     *
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException("SCRAM-SHA-1 does not support integrity or privacy");
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

    /**
     * Retrieves the negotiated property.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when {@code isComplete()} returns true); otherwise, an
     * {@code IllegalStateException} is thrown.
     *
     * @param propName the property
     * @return The value of the negotiated property. If null, the property was
     * not negotiated or is not applicable to this mechanism.
     * @exception IllegalStateException if this authentication exchange has not completed
     */
    @Override
    public Object getNegotiatedProperty(String propName) {
        if (isComplete()) {
            if (propName.equals(Sasl.QOP)) {
                return "auth";
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

     /**
      * Disposes of any system resources or security-sensitive information
      * the SaslServer might be using. Invoking this method invalidates
      * the SaslServer instance. This method is idempotent.
      * @throws SaslException If a problem was encountered while disposing
      * the resources.
      */
    @Override
    public void dispose() throws SaslException {
        username = null;
        state = State.INITIAL;
    }
    
    /**
     * Retrieve the salt from the database for a given username.
     * 
     * Returns a random salt if the user doesn't exist to mimic an invalid password. 
     */
    private byte[] getSalt(final String username) {
        try {
            String saltshaker = AuthFactory.getSalt(username);
            byte[] salt;
            if (saltshaker == null) {
                Log.debug("No salt found, so resetting password.");
                String password = AuthFactory.getPassword(username);
                AuthFactory.setPassword(username, password);
                salt = DatatypeConverter.parseBase64Binary(AuthFactory.getSalt(username));
            } else {
                salt = DatatypeConverter.parseBase64Binary(saltshaker);
            }
            return salt;
        } catch (UserNotFoundException | UnsupportedOperationException | ConnectionException | InternalUnauthenticatedException e) {
            Log.warn("Exception in SCRAM.getSalt():", e);
            byte[] salt = new byte[24];
            random.nextBytes(salt);
            return salt;
        }
    }
    
    /**
     * Retrieve the iteration count from the database for a given username.
     */
    private int getIterations(final String username) {
        try {
            return AuthFactory.getIterations(username);
        } catch (UserNotFoundException e) {
            return ITERATION_COUNT.getValue();
        }
    }
    
    /**
     * Retrieve the server key from the database for a given username.
     */
    private byte[] getServerKey(final String username) throws UserNotFoundException {
        final String serverKey = AuthFactory.getServerKey(username);
        if (serverKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary( serverKey );
        }
    }
    
    /**
     * Retrieve the stored key from the database for a given username.
     */
    private byte[] getStoredKey(final String username) throws UserNotFoundException {
        final String storedKey = AuthFactory.getStoredKey(username);
        if (storedKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary( storedKey );
        }
    }
}
