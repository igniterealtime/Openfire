package org.jivesoftware.openfire.clearspace;

import org.dom4j.Element;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.StringTokenizer;

/**
 * Implements the CLEARSPACE server-side SASL mechanism.
 *
 * @author Armando Jagucki
 */
public class ClearspaceSaslServer implements SaslServer {
    private boolean completed;
    private String jid;

    public ClearspaceSaslServer() {
        this.completed = false;
    }

    /**
     * Returns the mechanism name of this SASL server.
     * (e.g. "CRAM-MD5", "GSSAPI").
     *
     * @return A non-null string representing the mechanism name.
     */
    public String getMechanismName() {
        return "CLEARSPACE";
    }

    /**
     * Evaluates the response data and generates a challenge.
     * <p/>
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
     *                 by the client.
     * @return The possibly null challenge to send to the client.
     *         It is null if the authentication has succeeded and there is
     *         no more challenge data to be sent to the client.
     * @throws javax.security.sasl.SaslException
     *          If an error occurred while processing
     *          the response or generating a challenge.
     */
    public byte[] evaluateResponse(byte[] response) throws SaslException {
        ClearspaceManager csManager = ClearspaceManager.getInstance();
        String responseStr = new String(response);

        // Parse data and obtain jid & random string
        StringTokenizer tokens = new StringTokenizer(responseStr, "\u0000");
        if (tokens.countTokens() != 2) {
            // Info was not provided correctly
            completed = false;
            return null;
        }

        jid = tokens.nextToken();

        int atIndex = jid.lastIndexOf("@");

        String node = jid.substring(0, atIndex);

        jid = JID.escapeNode(node) + "@" + jid.substring(atIndex + 1);

        try {
            responseStr = StringUtils.encodeBase64(responseStr);
            Element resultElement =
                    csManager.executeRequest(GET, "groupChatAuthService/isAuthTokenValid/" + responseStr);
            if ("true".equals(WSUtils.getReturn(resultElement))) {
                completed = true;
            }
            else {
                // Failed to authenticate the user so throw an error so SASL failure is returned
                throw new SaslException("SASL CLEARSPACE: user not authorized: " + jid);
            }
        } catch (SaslException e) {
            // rethrow exception
            throw e;
        } catch (Exception e) {
            Log.error("Failed communicating with Clearspace", e);
            throw new SaslException("SASL CLEARSPACE: user not authorized due to an error: " + jid);
        }

        return null;
    }

    /**
     * Determines whether the authentication exchange has completed.
     * This method is typically called after each invocation of
     * <tt>evaluateResponse()</tt> to determine whether the
     * authentication has completed successfully or should be continued.
     *
     * @return true if the authentication exchange has completed; false otherwise.
     */
    public boolean isComplete() {
        return completed;
    }

    /**
     * Reports the authorization ID in effect for the client of this
     * session.
     * This method can only be called if isComplete() returns true.
     *
     * @return The authorization ID of the client.
     * @throws IllegalStateException if this authentication session has not completed
     */
    public String getAuthorizationID() {
        if (completed) {
            return jid;
        }
        else {
            throw new IllegalStateException("CLEARSPACE authentication not completed");
        }
    }

    /**
     * Unwraps a byte array received from the client.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true) and only if
     * the authentication exchange has negotiated integrity and/or privacy
     * as the quality of protection; otherwise,
     * an <tt>IllegalStateException</tt> is thrown.
     * <p/>
     * <tt>incoming</tt> is the contents of the SASL buffer as defined in RFC 2222
     * without the leading four octet field that represents the length.
     * <tt>offset</tt> and <tt>len</tt> specify the portion of <tt>incoming</tt>
     * to use.
     *
     * @param incoming A non-null byte array containing the encoded bytes
     *                 from the client.
     * @param offset   The starting position at <tt>incoming</tt> of the bytes to use.
     * @param len      The number of bytes from <tt>incoming</tt> to use.
     * @return A non-null byte array containing the decoded bytes.
     * @throws javax.security.sasl.SaslException
     *                               if <tt>incoming</tt> cannot be successfully
     *                               unwrapped.
     * @throws IllegalStateException if the authentication exchange has
     *                               not completed, or if the negotiated quality of protection
     *                               has neither integrity nor privacy
     */
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        return new byte[0];
    }

    /**
     * Wraps a byte array to be sent to the client.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true) and only if
     * the authentication exchange has negotiated integrity and/or privacy
     * as the quality of protection; otherwise, a <tt>SaslException</tt> is thrown.
     * <p/>
     * The result of this method
     * will make up the contents of the SASL buffer as defined in RFC 2222
     * without the leading four octet field that represents the length.
     * <tt>offset</tt> and <tt>len</tt> specify the portion of <tt>outgoing</tt>
     * to use.
     *
     * @param outgoing A non-null byte array containing the bytes to encode.
     * @param offset   The starting position at <tt>outgoing</tt> of the bytes to use.
     * @param len      The number of bytes from <tt>outgoing</tt> to use.
     * @return A non-null byte array containing the encoded bytes.
     * @throws javax.security.sasl.SaslException
     *                               if <tt>outgoing</tt> cannot be successfully
     *                               wrapped.
     * @throws IllegalStateException if the authentication exchange has
     *                               not completed, or if the negotiated quality of protection has
     *                               neither integrity nor privacy.
     */
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        return new byte[0];
    }

    /**
     * Retrieves the negotiated property.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true); otherwise, an
     * <tt>IllegalStateException</tt> is thrown.
     *
     * @param propName the property
     * @return The value of the negotiated property. If null, the property was
     *         not negotiated or is not applicable to this mechanism.
     * @throws IllegalStateException if this authentication exchange has not completed
     */

    public Object getNegotiatedProperty(String propName) {
        return null;
    }

    /**
     * Disposes of any system resources or security-sensitive information
     * the SaslServer might be using. Invoking this method invalidates
     * the SaslServer instance. This method is idempotent.
     *
     * @throws javax.security.sasl.SaslException
     *          If a problem was encountered while disposing
     *          the resources.
     */
   public void dispose() throws SaslException {
        completed = false;
    }
}
