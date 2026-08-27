/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import com.google.common.annotations.VisibleForTesting;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.DefaultAuthProvider;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the server side of the SCRAM SASL exchange as defined in RFC 5802, including the channel binding (-PLUS)
 * variants defined there and profiled for other hash functions in RFC 7677.
 *
 * The exchange logic in this class is hash-function agnostic. Concrete subclasses bind a specific hash function by
 * implementing the small set of abstract methods: the base mechanism name (which doubles as the credential storage
 * key), the HMAC and message digest algorithm names, the default iteration count, and the server-side secret used to
 * derive indistinguishable fake credentials for non-existent users.
 *
 * Instances are session-specific and must not be reused across sessions or users. The available SASL mechanisms are
 * established when the instance is created and are used, in particular, to correctly process the GS2 header and enforce
 * channel-binding downgrade protection.
 *
 * @author Richard Midwinter, Guus der Kinderen
 */
public abstract class ScramSaslServer implements SaslServer
{
    private static final Logger Log = LoggerFactory.getLogger(ScramSaslServer.class);

    /**
     * The name of the negotiated property through which the channel binding type that was used during authentication
     * is exposed.
     */
    public static final String PROPNAME_CHANNELBINDINGTYPE = "channelbindingtype";

    // RFC 5802 §7 formal syntax, factored into named fragments so a single character-class definition can't silently
    // drift between the patterns that share it.
    private static final String CB_NAME       = "[A-Za-z0-9.-]+";            // cb-name  = 1*(ALPHA / DIGIT / "." / "-")
    private static final String SASLNAME_CHAR = "[^,\\x00]";                 // saslname char: excludes NUL, comma (escaping validated separately by decodeSaslname)
    private static final String NONCE         = "[\\x21-\\x2B\\x2D-\\x7E]*"; // printable* -- %x21-2B / %x2D-7E; '*' permits empty so the existing explicit isEmpty() checks keep their specific messages
    private static final String ATTR_VAL      = "[A-Za-z]=[^,\\x00]+";       // attr-val = ALPHA "=" value; value excludes NUL per value-safe-char
    private static final String BASE64        = "[A-Za-z0-9+/]*={0,2}";      // base64 charset, with '=' padding confined to the string's end (0-2 of them);

    /**
     * SCRAM attribute letters explicitly assigned meaning by RFC 5802 §5.1 (a, c, e, i, m, n, p, r, s, v). Per that
     * section, "[o]ptional extensions use as-yet unassigned attribute names". An extension using one of these
     * letters is never a legitimate extension, regardless of whether that specific attribute happens to appear (in
     * its own, fixed position) in the message being parsed.
     */
    private static final Set<Character> ASSIGNED_ATTRIBUTE_LETTERS = Set.of('a', 'c', 'e', 'i', 'm', 'n', 'p', 'r', 's', 'v');

    /**
     * Matches a single, complete attr-val pair (e.g. "a=1"), anchored at both ends. Used by
     * {@link #rejectReservedMandatoryExtension(String)} to validate each segment in full -- attribute-name
     * character, "=", and a value free of comma/NUL -- rather than re-implementing a subset of the same grammar
     * with manual length and character checks that can drift out of sync with the shared ATTR_VAL fragment.
     */
    private static final Pattern ATTR_VAL_PATTERN = Pattern.compile("^" + ATTR_VAL + "$");

    /**
     * Matches the GS2 header that prefixes a SCRAM client-first-message:
     *
     * <pre>
     * gs2-cbind-flag  = ("p=" cb-name) / "n" / "y"
     * gs2-header      = gs2-cbind-flag "," [ authzid ] ","
     * authzid         = "a=" saslname
     * </pre>
     *
     * Only the "p" flag may carry a value, and per the grammar's {@code 1*(...)} productions neither a cb-name nor
     * an authzid may be empty if present at all -- "n=...", "y=...", and "a=" with nothing following are all
     * malformed and rejected outright rather than silently treated as "no value supplied".
     *
     * Group 1: "p" if the p-flag was used, else null.
     * Group 2: the (non-empty) channel-binding name, only present when group 1 is "p".
     * Group 3: the flag character, "n" or "y", whichever was used; else null (when group 1 is "p" instead).
     * Group 4: the raw (still saslname-escaped), non-empty authzid value, without the "a=" prefix, only present when supplied.
     * Group 5: everything after the GS2 header, i.e. the client-first-message-bare.
     */
    private static final Pattern GS2_HEADER = Pattern.compile("^(?:(p)=(" + CB_NAME + ")|([ny])),(?:a=(" + SASLNAME_CHAR + "+))?,(.*)\\z");

    /**
     * Matches a SCRAM client-first-message-bare:
     *
     * <pre>
     * client-first-message-bare = [reserved-mext ","] username "," nonce ["," extensions]
     * reserved-mext              = "m=" 1*(value-char)
     * username                   = "n=" saslname
     * nonce                      = "r=" c-nonce [s-nonce]
     * extensions                 = attr-val *("," attr-val)
     * attr-val                   = ALPHA "=" value
     * </pre>
     *
     * Group 1: the reserved "m=" extension in the leading position, if the client sent one (used purely to detect and reject it).
     * Group 2: the (still saslname-escaped) username value.
     * Group 3: the nonce value.
     * Group 4: the raw, comma-prefixed extensions following the nonce (e.g. ",a=1,b=2"), or an empty string if none are present.
     *
     * Each segment from group 4 is already constrained to a well-formed, non-empty attr-val pair by this
     * pattern; {@link #rejectReservedMandatoryExtension(String)} must still be called on this value, since the
     * reserved "m" attribute can also appear here rather than only in the leading reserved-mext position.
     */
    private static final Pattern CLIENT_FIRST_MESSAGE_BARE = Pattern.compile("^(?:(m=[^,]*),)?n=(" + SASLNAME_CHAR + "*),r=(" + NONCE + ")((?:," + ATTR_VAL + ")*)\\z");

    /**
     * Matches a SCRAM client-final-message:
     *
     * <pre>
     * client-final-message-without-proof = channel-binding "," nonce ["," extensions]
     * client-final-message               = client-final-message-without-proof "," proof
     * extensions                         = attr-val *("," attr-val); attr-val = ALPHA "=" value
     * </pre>
     *
     * Group 1: the client-final-message-without-proof, verbatim (needed, byte-for-byte, to compute AuthMessage).
     * Group 2: the channel-binding value.
     * Group 3: the nonce value.
     * Group 4: the raw, comma-prefixed extensions between the nonce and the proof (e.g. ",a=1,b=2"), or an empty string if none are present.
     * Group 5: the proof value.
     *
     * Each optional extension between the nonce and the proof must be a well-formed, non-empty attr-val pair;
     * malformed segments (empty, multi-letter attribute names, or missing "=") cause the whole message to be
     * rejected as invalid rather than silently tolerated.
     *
     * As with the equivalent group in {@link #CLIENT_FIRST_MESSAGE_BARE}, each segment from group 4 is already
     * constrained to a well-formed attr-val pair, but {@link #rejectReservedMandatoryExtension(String)}
     * must still be called on this value to catch a reserved "m" attribute.
     */
    private static final Pattern CLIENT_FINAL_MESSAGE = Pattern.compile("^(c=(" + BASE64 + "),r=(" + NONCE + ")((?:,(?!p=)" + ATTR_VAL + ")*)),p=(" + BASE64 + ")\\z");

    /**
     * Octet collation as defined in RFC 4790 section 9.3 ("i;octet").
     */
    private static final Comparator<String> OCTET_ORDER = Comparator.comparing((String s) -> s.getBytes(StandardCharsets.UTF_8), Arrays::compareUnsigned);

    /**
     * The names of SASL mechanisms that are available to this particular session (as opposed to the set of globally
     * available mechanism names). The session-specificality is important to be able to correctly process the GS2 header
     * sent by a client, particularly around channel-binding downgrade protection. It is important to know if the server
     * offered channel-binding, when the client indicates that it supports channel-binding but did not receive the -PLUS
     * mechanism (by sending the 'y' flag).
     */
    private final Set<String> availableMechanismsForSession;

    /**
     * The names of channel binding types that are available to this particular session (as opposed to the set of
     * globally available channel binding types). The session specificality is important to be able to correctly
     * implement XEP-0474 SASL SCRAM Downgrade Protection.
     */
    private final Set<String> availableChannelBindingTypesForSession;

    /**
     * Denotes if this instance supports channel-binding ({@code true}) or not ({@code false}).
     */
    private final boolean isPlusMechanism;

    /**
     * The possibly null set of properties used to select the SASL mechanism and to configure the authentication
     * exchange of the selected mechanism.
     */
    private final Map<String, ?> props;

    private String username;
    private State state = State.INITIAL;
    private String nonce;
    private String serverFirstMessage;
    private String clientFirstMessageBare;
    private final SecureRandom random = new SecureRandom();
    private byte[] expectedChannelBindingPayloadInFinalClientMessage;
    private String gs2CbindName;

    private enum State {
        INITIAL,
        IN_PROGRESS,
        COMPLETE;
    }

    /**
     * Creates a new, client-specific, instance.
     *
     * @param isPlusMechanism                        Denotes if this instance supports channel-binding ({@code true}) or not ({@code false}).
     * @param props                                  The possibly null set of properties used to select the SASL mechanism and to configure the authentication exchange of the selected mechanism.
     * @param availableMechanismsForSession          The names of SASL mechanisms that are available to this particular session (as opposed to the set of globally available mechanism names).
     * @param availableChannelBindingTypesForSession The names of channel binding types that are available to this particular session (as opposed to the set of globally available channel binding types).
     */
    protected ScramSaslServer(final boolean isPlusMechanism, final Map<String, ?> props, @Nonnull final Set<String> availableMechanismsForSession, @Nonnull final Set<String> availableChannelBindingTypesForSession)
    {
        this.isPlusMechanism = isPlusMechanism;
        this.props = props;
        this.availableMechanismsForSession = availableMechanismsForSession;
        this.availableChannelBindingTypesForSession = availableChannelBindingTypesForSession;
    }

    /**
     * The IANA-registered name of the base (non-PLUS) mechanism implemented by this server, for example
     * {@code SCRAM-SHA-1}. This value is also the key under which SCRAM credentials for this mechanism are stored:
     * the -PLUS variant shares the credential of the base mechanism.
     *
     * @return A non-null string representing the IANA-registered (base) mechanism name.
     */
    protected abstract String getMechanismBaseName();

    /**
     * The JCA name of the HMAC algorithm that corresponds to this mechanism's hash function, for example
     * {@code HmacSHA1}.
     *
     * @return the HMAC algorithm name.
     */
    protected abstract String getHmacAlgorithmName();

    /**
     * The JCA name of the message digest that corresponds to this mechanism's hash function, for example
     * {@code SHA-1}. Used to compute {@code H(ClientKey)} when verifying the client proof.
     *
     * @return the message digest algorithm name.
     */
    protected abstract String getDigestAlgorithmName();

    /**
     * The iteration count to advertise when no per-user value is available.
     *
     * @return the default iteration count for this mechanism.
     */
    protected abstract int getDefaultIterationCount();

    /**
     * A server-side secret from which deterministic fake credentials are derived for non-existent users, so that
     * authentication processing for non-existing users is indistinguishable from that of existing users.
     *
     * @return the server secret for this mechanism.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    protected abstract String getNonExistentUserSecret();

    /**
     * Returns the IANA-registered mechanism name of this SASL server, which is the base mechanism name with a
     * {@code -PLUS} suffix for the channel binding variant.
     *
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    @Override
    public String getMechanismName()
    {
        return isPlusMechanism ? getMechanismBaseName() + "-PLUS" : getMechanismBaseName();
    }

    /**
     * Evaluates a single client response and advances the SCRAM exchange one step.
     *
     * Dispatch is driven by {@link #state}: {@link State#INITIAL} treats the response as
     * {@code client-first-message} (see {@link #generateServerFirstMessage(byte[])}); {@link State#IN_PROGRESS}
     * treats it as {@code client-final-message} (see {@link #generateServerFinalMessage(byte[])}); once
     * {@link State#COMPLETE}, an empty response is tolerated but a non-empty one is rejected.
     *
     * Any {@link RuntimeException} thrown while processing is re-wrapped as a {@link SaslException}, so an
     * implementation defect surfaces as a failed authentication attempt rather than an unchecked exception.
     *
     * @param response The non-null (but possibly empty) response sent by the client.
     * @return The possibly null challenge to send to the client; null only once the exchange has concluded.
     * @throws SaslException if the response is invalid for the current state, or arrives after completion.
     */
    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException
    {
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
                    throw new SaslException("Unexpected response after authentication completed");
                default:
                    throw new SaslException("No response expected in state " + state);

            }
            return challenge;
        } catch (RuntimeException ex) {
            throw new SaslException("Unexpected exception while evaluating SASL response.", ex);
        }
    }

    /**
     * Parses the SCRAM {@code client-first-message} and produces the {@code server-first-message}.
     *
     * Validates the GS2 header and authzid (rejecting proxy authorization unless the authzid matches the
     * authentication identity), the reserved "m" mandatory-extension attribute wherever it appears, and channel
     * binding (downgrade detection, and -PLUS channel-binding-data retrieval). On success, populates
     * {@link #username}, {@link #clientFirstMessageBare}, {@link #gs2CbindName},
     * {@link #expectedChannelBindingPayloadInFinalClientMessage}, and {@link #nonce} for use by
     * {@link #generateServerFinalMessage(byte[])}.
     *
     * @param response the raw bytes of the client-first-message
     * @return the server-first-message (combined nonce, salt, iteration count)
     * @throws SaslException if the message is malformed or fails any RFC 5802 validation
     */
    private byte[] generateServerFirstMessage(final byte[] response) throws SaslException
    {
        final String clientFirstMessage = decodeStrictUtf8(response);

        final Matcher gs2Matcher = GS2_HEADER.matcher(clientFirstMessage);
        if (!gs2Matcher.matches()) {
            throw new SaslException("Invalid first client message: unable to parse GS2 header");
        }

        final byte[] gs2_header = extractRawGS2Header(response); // Using raw header to prevent any normalization issues that might pop up when using something like: gs2Header.getBytes(StandardCharsets.UTF_8);

        final String gs2CbindFlag;
        if (gs2Matcher.group(1) != null) {
            gs2CbindFlag = "p";
            gs2CbindName = gs2Matcher.group(2);
        } else {
            gs2CbindFlag = gs2Matcher.group(3); // "n" or "y", whichever matched
            gs2CbindName = null;
        }
        final String rawAuthzid = gs2Matcher.group(4);
        final String authzid = rawAuthzid != null ? decodeSaslname(rawAuthzid) : null;
        clientFirstMessageBare = gs2Matcher.group(5);

        final Matcher bareMatcher = CLIENT_FIRST_MESSAGE_BARE.matcher(clientFirstMessageBare);
        if (!bareMatcher.matches()) {
            throw new SaslException("Invalid first client message: unable to parse client-first-message-bare");
        }

        username = decodeSaslname(bareMatcher.group(2)); // Group 2 comes from a mandatory (non-optional) capturing group, so once bareMatcher.matches() has succeeded, group 2 is always a non-null string.
        String clientNonce = bareMatcher.group(3);

        if (username.isEmpty()) {
            throw new SaslException("Invalid first client message: Username cannot be empty");
        }
        if (clientNonce == null || clientNonce.isEmpty()) {
            throw new SaslException("Invalid first client message: Client nonce cannot be empty");
        }

        // RFC 5802 requires the server to authorize a supplied authzid, or fail authentication if it does not support
        // doing so. Openfire does not support proxy authorization, but an authzid that is identical to the SASL
        // authentication identity is not a request for proxying (the client is simply, redundantly, asking to be
        // authorized as itself, which getAuthorizationID() already guarantees). See OF-3352
        if (authzid != null && !authzid.isEmpty() && !authzid.equals(username)) {
            throw new SaslException("Proxy authorization is not supported by this server. Rejecting authentication for non-empty authzid that differs from the authentication identity.");
        }

        // https://www.rfc-editor.org/rfc/rfc5802.html#section-5: the "m=" attribute is reserved for future
        // extensibility. Its presence indicates a mandatory extension; if the server does not support/understand
        // the extension (which, since none are currently defined, is always the case here), it MUST fail the
        // authentication rather than silently ignore the attribute. See OF-3350
        final String mandatoryExtension = bareMatcher.group(1);
        if (mandatoryExtension != null) {
            throw new SaslException("Client requested an unsupported mandatory extension ('" + mandatoryExtension + "'). Rejecting authentication.");
        }

        // The check above only catches "m=" in the leading reserved-mext position. A client could otherwise bypass
        // it simply by moving the attribute into the (structurally identical) extensions list that follows the nonce.
        rejectReservedMandatoryExtension(bareMatcher.group(4));

        // https://www.rfc-editor.org/rfc/rfc5802.html#section-6: If the flag is set to "y" and the server supports
        // channel binding, the server MUST fail authentication. This is because if the client sets the channel binding
        // flag to "y", then the client must have believed that the server did not support channel binding -- if the
        // server did in fact support channel binding, then this is an indication that there has been a downgrade attack
        // (e.g., an attacker changed the server's mechanism list to exclude the -PLUS suffixed SCRAM mechanism name(s)).
        final boolean clientSupportsChannelBindingButThinksServerDoesNot = "y".equals(gs2CbindFlag);

        // Note that this needs to evaluate support _as offered to this particular client_, not global support (even if
        // those will often be the same set). There may be client-specific reasons to not offer a mechanism. If this
        // code would assume that server-supported mechanisms would always be offered, a client that did not get offered
        // a channel-binding mechanism (and sent 'y' to indicate that it could use it) would cause the authentication to
        // incorrectly be aborted.
        final String plusMechanismName = getMechanismBaseName() + "-PLUS";
        final boolean serverOfferedChannelBinding = availableMechanismsForSession.contains(plusMechanismName);
        if (clientSupportsChannelBindingButThinksServerDoesNot && serverOfferedChannelBinding) {
            throw new SaslException("Client supports channel binding, but thinks the server does not (while it does). Rejecting authentication to prevent downgrade attack.");
        }

        final boolean clientRequiresChannelBinding = "p".equals(gs2CbindFlag);
        if (clientRequiresChannelBinding && !isPlusMechanism) {
            throw new SaslException("Client requires channel binding, but is not using a -PLUS mechanism. Rejecting authentication.");
        }

        if (isPlusMechanism)
        {
            if (!clientRequiresChannelBinding) {
                throw new SaslException("Channel binding required for -PLUS. Rejecting authentication.");
            }

            if (!serverOfferedChannelBinding) {
                // Should be unreachable, but this is cheap defense in depth.
                throw new SaslException("Client requires channel binding, but server could not offer channel binding to client. Rejecting authentication.");
            }

            // https://www.rfc-editor.org/rfc/rfc5802.html#section-6: If the channel binding flag was "p" and the server
            // does not support the indicated channel binding type, then the server MUST fail authentication.
            if (gs2CbindName == null || gs2CbindName.isEmpty() || !availableChannelBindingTypesForSession.contains(gs2CbindName)) {
                throw new SaslException("Client requires channel binding, but server does not support the indicated channel binding type '" + gs2CbindName + "'. Rejecting authentication.");
            }

            // Prepare channel binding data.
            final LocalSession session = (LocalSession) props.get(LocalSession.class.getCanonicalName());
            if (session == null || session.getConnection() == null) {
                throw new SaslException("Local session not found in properties. Rejecting authentication.");
            }
            final Optional<byte[]> channelBindingData = session.getConnection().getChannelBindingData(gs2CbindName);
            if (channelBindingData.isEmpty()) {
                Log.debug("Unable to retrieve channel binding data for '{}'. Rejecting authentication.", gs2CbindName);
                throw new SaslException("Unable to retrieve channel binding data for '" + gs2CbindName + "'. Rejecting authentication.");
            }

            // In the final client message, we expect to find a combination of the gs2 header and channel binding data.
            final byte[] cb_data = channelBindingData.get();
            expectedChannelBindingPayloadInFinalClientMessage = new byte[gs2_header.length + cb_data.length];
            System.arraycopy(gs2_header, 0, expectedChannelBindingPayloadInFinalClientMessage, 0        , gs2_header.length);
            System.arraycopy(cb_data,    0, expectedChannelBindingPayloadInFinalClientMessage, gs2_header.length, cb_data.length);
        } else {
            // If this is _not_ a -PLUS mechanism, we still need to verify the channel binding payload in the final client message.
            // In that case, it should not have trailing channel binding data.
            expectedChannelBindingPayloadInFinalClientMessage = gs2_header;
        }

        nonce = clientNonce + UUID.randomUUID().toString();

        serverFirstMessage = String.format("r=%s,s=%s,i=%d", nonce, DatatypeConverter.printBase64Binary(getOrCreateSalt(username)), getIterations(username));

        // XEP-0474: SASL SCRAM Downgrade Protection
        if (SASLAuthentication.SSDP_ENABLED.getValue()) {
            serverFirstMessage += ",h=" + calculateDowngradeProtectionHash();
        }

        return serverFirstMessage.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses the SCRAM {@code client-final-message}, verifies the client's proof, and produces the
     * {@code server-final-message}.
     *
     * Verifies the nonce and channel-binding value against what {@link #generateServerFirstMessage(byte[])}
     * recorded, rejects the reserved "m" attribute among any extensions, then reconstructs AuthMessage and checks
     * the client's proof against it.
     *
     * @param response the raw bytes of the client-final-message
     * @return the server-final-message (base64-encoded server signature, "v=")
     * @throws SaslException if the message is malformed, the nonce/channel-binding/proof don't verify, or authentication otherwise fails
     */
    private byte[] generateServerFinalMessage(final byte[] response) throws SaslException
    {
        final String clientFinalMessage = decodeStrictUtf8(response);

        final Matcher m = CLIENT_FINAL_MESSAGE.matcher(clientFinalMessage);
        if (!m.matches()) {
            throw new SaslException("Invalid client final message");
        }

        final String clientFinalMessageWithoutProof = m.group(1); // c=BASE64,r=NONCE[,extensions] - verbatim, needed for AuthMessage
        final String channelBinding = m.group(2);                 // c=BASE64
        final String clientNonce = m.group(3);                    // r=NONCE
        final String extensions = m.group(4);                     // raw, comma-prefixed extensions (or ""); still needs rejectReservedMandatoryExtension()
        final String proof = m.group(5);                          // p=BASE64

        // RFC 5802 §5.1: the reserved "m" attribute must cause authentication failure wherever it appears, not only
        // in client-first-message. client-final-message-without-proof has its own optional "extensions" production,
        // so a client could otherwise request an unsupported mandatory extension there and still authenticate
        // successfully with a validly-computed proof.
        rejectReservedMandatoryExtension(extensions);

        if (proof == null || proof.isEmpty()) {
            throw new SaslException("Invalid client final message: missing proof attribute");
        }

        // BASE64 already confines '=' to the string's end, so a valid base64 length is a multiple of 4.
        if (proof.length() % 4 != 0) {
            throw new SaslException("Invalid client final message: proof is not valid base64");
        }

        if (channelBinding == null || channelBinding.isEmpty()) {
            throw new SaslException("Invalid client final message: missing channel binding attribute");
        }

        // BASE64 already confines '=' to the string's end, so a valid base64 length is just a multiple of 4.
        if (channelBinding.length() % 4 != 0) {
            throw new SaslException("Invalid client final message: channel binding is not valid base64");
        }

        if (clientNonce == null || clientNonce.isEmpty()) {
            throw new SaslException("Invalid client final message: missing nonce attribute");
        }

        // Verify nonce: RFC 5802 §5: must equal client_nonce (from initial client response) + server_nonce (from initial server response)
        if (!nonce.equals(clientNonce)) { // Constant-time operation is important for keys, not for public protocol values like nonces.
            // Possible replay or tampering
            throw new SaslException("Invalid client final message: incorrect nonce attribute value");
        }

        // Verify channel binding payload.
        final byte[] decodedChannelBinding = DatatypeConverter.parseBase64Binary(channelBinding);
        if (!Arrays.equals(expectedChannelBindingPayloadInFinalClientMessage, decodedChannelBinding)) {
            throw new SaslException("Invalid client final message: channel binding payload does not match expected payload");
        }

        try {
            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;
            byte[] storedKey = getOrFakeStoredKey(username);
            byte[] serverKey = getOrFakeServerKey(username);

            byte[] clientSignature = ScramUtils.computeHmac(storedKey, authMessage, getHmacAlgorithmName());
            byte[] serverSignature = ScramUtils.computeHmac(serverKey, authMessage, getHmacAlgorithmName());

            byte[] clientKey = clientSignature.clone();
            byte[] decodedProof = DatatypeConverter.parseBase64Binary(proof);
            if (decodedProof.length != clientKey.length) {
                throw new SaslException("Invalid proof length: expected " + clientKey.length + " bytes, got " + decodedProof.length);
            }
            for (int i = 0; i < clientKey.length; i++) {
                clientKey[i] ^= decodedProof[i];
            }

            if (!MessageDigest.isEqual(storedKey, MessageDigest.getInstance(getDigestAlgorithmName()).digest(clientKey))) {
                throw new SaslException("Authentication failed for: '"+username+"'");
            }
            return ("v=" + DatatypeConverter.printBase64Binary(serverSignature))
                .getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new SaslException(e.getMessage(), e);
        }
    }

    /**
     * Determines whether the authentication exchange has completed.
     *
     * This method is typically called after each invocation of {@code evaluateResponse()} to determine whether the
     * authentication has completed successfully or should be continued.
     *
     * @return true if the authentication exchange has completed; false otherwise.
     */
    @Override
    public boolean isComplete()
    {
        return state == State.COMPLETE;
    }

    /**
     * Reports the authorization ID in effect for the client of this session.
     *
     * This method can only be called if isComplete() returns true.
     *
     * @return The authorization ID of the client.
     * @throws IllegalStateException if this authentication session has not completed
     */
    @Override
    public String getAuthorizationID()
    {
        if (isComplete()) {
            return username;
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
        }
    }

    /**
     * Unwraps a byte array received from the client. SCRAM supports no security layer.
     *
     * @return the unwrapped byte array.
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
    {
        if (isComplete()) {
            throw new IllegalStateException(getMechanismName() + " does not support integrity or privacy");
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
        }
    }

    /**
     * Wraps a byte array to be sent to the client. SCRAM supports no security layer.
     *
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException(getMechanismName() + " does not support integrity or privacy");
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
        }
    }

    /**
     * Retrieves the negotiated property.
     *
     * This method can be called only after the authentication exchange has completed (i.e., when {@code isComplete()}
     * returns true); otherwise, an {@code IllegalStateException} is thrown.
     *
     * @param propName the property
     * @return The value of the negotiated property. If null, the property was not negotiated or is not applicable to this mechanism.
     * @throws IllegalStateException if this authentication exchange has not completed
     */
    @Override
    public Object getNegotiatedProperty(String propName) {
        if (isComplete()) {
            if (propName.equals(Sasl.QOP)) {
                return "auth";
            } else if (isPlusMechanism && propName.equals(PROPNAME_CHANNELBINDINGTYPE)) {
                return gs2CbindName;
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
        }
    }

    /**
     * Disposes of any system resources or security-sensitive information the SaslServer might be using. Invoking this
     * method invalidates the SaslServer instance. This method is idempotent.
     *
     * @throws SaslException If a problem was encountered while disposing the resources.
     */
    @Override
    public void dispose() throws SaslException {
        username = null;
        nonce = null;
        serverFirstMessage = null;
        clientFirstMessageBare = null;
        expectedChannelBindingPayloadInFinalClientMessage = null;
        gs2CbindName = null;
        state = State.INITIAL;
    }

    /**
     * Retrieve the salt for a given username.
     *
     * When a salt does not currently exist for an existing user, but a password is set, that value is used to create
     * and persist a new salt for that user.
     *
     * Returns a username-specific salt if the user doesn't exist to mimic an invalid password. This also guards against
     * user enumeration attacks.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    protected byte[] getOrCreateSalt(final String username)
    {
        try
        {
            final String saltBase64 = AuthFactory.getSalt(username, getMechanismBaseName());
            if (saltBase64 == null) {
                return handleMissingSalt(username);
            }

            return decodeSalt(saltBase64);
        }
        catch (UserNotFoundException e)
        {
            Log.debug("User '{}' not found. Returning fake salt.", username, e);
            return generateFakeSalt(username);
        }
        catch (UnsupportedOperationException | ConnectionException | InternalUnauthenticatedException e) {
            Log.warn("Exception in SCRAM.getSalt() for user '{}'", username, e);
            return generateFakeSalt(username);
        }
    }

    /**
     * When no salt is found for the user, but a (plain-text) password is available, we can generate a salt by updating
     * the password to the same value (this should trigger a re-hashing of the password).
     *
     * @param username The user for whom to generate a salt
     * @return A salt
     * @throws UserNotFoundException when the password could not be loaded for this user.
     * @throws InternalUnauthenticatedException when there's an authentication issue with connecting to the user-provider
     * @throws ConnectionException when there's an issue with connecting to the user-provider
     * @throws UnsupportedOperationException when a plain-text password cannot be retrieved for this user.
     */
    private byte[] handleMissingSalt(String username) throws UserNotFoundException, InternalUnauthenticatedException, ConnectionException, UnsupportedOperationException
    {
        Log.debug("No salt found for '{}', regenerating.", username);

        final String password = AuthFactory.getPassword(username);
        if (password == null) {
            // No password available. This is likely an issue with the provider, which should have thrown a
            // UserNotFoundException or UnsupportedOperationException. Both of those will cause the same fallback
            // handling, so this code can generate either to cause that same fallback behavior.
            throw new UserNotFoundException("No password available for user '" + username + "'");
        }
        AuthFactory.setPassword(username, password);

        final String newSalt = AuthFactory.getSalt(username, getMechanismBaseName());
        if (newSalt == null) {
            Log.debug("Salt regeneration failed for '{}'", username);
            return generateFakeSalt(username);
        }
        return decodeSalt(newSalt);
    }

    /**
     * Decode a base64-encoded salt.
     *
     * @param base64Salt The base64-encoded salt to decode
     * @return The decoded salt as a byte array
     */
    private byte[] decodeSalt(@Nonnull final String base64Salt)
    {
        return DatatypeConverter.parseBase64Binary(base64Salt);
    }

    /**
     * Generate a fake salt to guard against user enumeration attacks (see OF-3258).
     *
     * The returned salt is a deterministic but cryptographically unpredictable value derived from the username and a
     * server-side secret. The returned value is always exactly {@link DefaultAuthProvider#SALT_LENGTH} bytes long.
     *
     * @param username The username for which to generate a fake salt
     * @return a fake salt of length {@link DefaultAuthProvider#SALT_LENGTH}.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    private byte[] generateFakeSalt(String username)
    {
        final int length = DefaultAuthProvider.SALT_LENGTH;

        try
        {
            final byte[] key = getNonExistentUserSecret().getBytes(StandardCharsets.UTF_8);
            final byte[] result = new byte[length];

            int offset = 0;
            int counter = 0;

            while (offset < length)
            {
                // Domain separation + counter to expand output deterministically
                final byte[] block = ScramUtils.computeHmac(key, "fake-salt-for-" + username + ":" + counter, getHmacAlgorithmName());
                final int toCopy = Math.min(block.length, length - offset);
                System.arraycopy(block, 0, result, offset, toCopy);

                offset += toCopy;
                counter++;
            }

            return result;
        }
        catch (SaslException e)
        {
            // Give up trying to be deterministic. Return a random salt.
            final byte[] salt = new byte[length];
            random.nextBytes(salt);
            return salt;
        }
    }

    /**
     * Retrieve the iteration count from the database for a given username.
     *
     * @return The iteration count for the given username.
     */
    private int getIterations(final String username)
    {
        try {
            return AuthFactory.getIterations(username, getMechanismBaseName());
        } catch (UserNotFoundException e) {
            return getDefaultIterationCount();
        }
    }

    /**
     * Retrieve the server key from the database for a given username, but returns a fake key if none is found.
     * <p>
     * Returning a fake key helps guard against timing attacks: instead of short-circuiting the operation,
     * a fake key is generated to ensure consistent response times and prevent potential timing attacks.
     *
     * @return The server key for the given username.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3257">OF-3257: Guard against timing attacks in ScramSha1SaslServer</a>
     */
    protected byte[] getOrFakeServerKey(String username)
    {
        try {
            byte[] key = getServerKey(username);
            if (key != null) {
                return key;
            }
        } catch (UserNotFoundException ignored) {
            // fall through
        }
        return generateFakeKey("server-key-" + username);
    }

    /**
     * Retrieve the server key from the database for a given username.
     *
     * @return The server key for the given username.
     */
    private byte[] getServerKey(final String username) throws UserNotFoundException
    {
        final String serverKey = AuthFactory.getServerKey(username, getMechanismBaseName());
        if (serverKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary(serverKey);
        }
    }

    /**
     * Retrieve the stored key from the database for a given username, but returns a fake key if none is found.
     * <p>
     * Returning a fake key helps guard against timing attacks: instead of short-circuiting the operation,
     * a fake key is generated to ensure consistent response times and prevent potential timing attacks.
     *
     * @return The stored key for the given username.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3257">OF-3257: Guard against timing attacks in ScramSha1SaslServer</a>
     */
    protected byte[] getOrFakeStoredKey(final String username)
    {
        try {
            byte[] key = getStoredKey(username);
            if (key != null) {
                return key;
            }
        } catch (UserNotFoundException ignored) {
            // fall through
        }
        return generateFakeKey("stored-key-" + username);
    }

    /**
     * Retrieve the stored key from the database for a given username.
     *
     * @return The stored key for the given username.
     */
    private byte[] getStoredKey(final String username) throws UserNotFoundException
    {
        final String storedKey = AuthFactory.getStoredKey(username, getMechanismBaseName());
        if (storedKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary(storedKey);
        }
    }

    /**
     * Generate a fake key to guard against timing attacks (see OF-3257).
     *
     * The fake key is derived using this mechanism's HMAC algorithm, so that its length matches the length of a real
     * key for this mechanism.
     *
     * @param input a string input for which to generate a fake key
     * @return a fake key
     */
    private byte[] generateFakeKey(String input)
    {
        try {
            return ScramUtils.computeHmac(
                getNonExistentUserSecret().getBytes(StandardCharsets.UTF_8),
                input,
                getHmacAlgorithmName()
            );
        } catch (SaslException e) {
            int fallbackLength;
            try {
                fallbackLength = Mac.getInstance(getHmacAlgorithmName()).getMacLength();
            } catch (NoSuchAlgorithmException ignored) {
                fallbackLength = 24;
            }
            final byte[] fallback = new byte[fallbackLength];
            random.nextBytes(fallback);
            return fallback;
        }
    }

    /**
     * Extracts the raw GS2 header from a SCRAM client-first-message byte array.
     *
     * The GS2 header is defined in RFC 5802 as:
     * <pre>
     * gs2-header = gs2-cbind-flag "," [authzid] ","
     * </pre>
     * and always terminates with a trailing comma.
     *
     * This method performs a byte-level scan of the input and returns a copy of the original byte array from index
     * {@code 0} up to and including the second comma (i.e., the full GS2 header including its trailing comma).
     *
     * No character decoding or normalization is performed. This ensures that the returned GS2 header is byte-for-
     * byte identical to the original input, which is required for correct -PLUS channel binding validation in SCRAM
     * mechanisms.
     *
     * @param data the raw SCRAM client-first-message bytes
     * @return a byte array containing the complete GS2 header including the trailing comma
     * @throws SaslException if the input does not contain a valid GS2 header
     */
    protected static byte[] extractRawGS2Header(final byte[] data) throws SaslException
    {
        // The GS2 header ends at the second comma.
        int commaCount = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ',') {
                commaCount++;
                if (commaCount == 2) {
                    return Arrays.copyOfRange(data, 0, i+1); // +1 to include the comma itself.
                }
            }
        }
        throw new SaslException("Invalid GS2 header format");
    }

    /**
     * Decodes the given bytes as strict UTF-8, rejecting malformed input rather than silently substituting the
     * Unicode replacement character (U+FFFD), which is what {@code new String(bytes, StandardCharsets.UTF_8)} does
     * by default. RFC 5802 explicitly anticipates invalid UTF-8 as a distinct, detectable failure (see the
     * "invalid-encoding" and "invalid-username-encoding" server-error-value tokens in §7); silently normalizing
     * malformed bytes would let two different, invalid byte sequences collapse into the same decoded string, and
     * would let content the client never actually sent reach credential lookup and AuthMessage.
     *
     * @param bytes the raw message bytes received from the client
     * @return the strictly-decoded UTF-8 string
     * @throws SaslException if the bytes are not valid UTF-8
     */
    @VisibleForTesting
    static String decodeStrictUtf8(@Nonnull final byte[] bytes) throws SaslException
    {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new SaslException("Invalid message: not valid UTF-8", e);
        }
    }

    /**
     * Decodes a SCRAM {@code saslname} per RFC 5802 §5.1: a literal comma is sent on the wire as {@code =2C}, and a
     * literal equals sign as {@code =3D}. Any other character following an {@code =} indicates a malformed value.
     *
     * @param saslname the raw, wire-escaped saslname value (username or authzid)
     * @return the decoded value
     * @throws SaslException if the value contains a NUL character or a malformed escape sequence
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @VisibleForTesting
    static String decodeSaslname(@Nonnull final String saslname) throws SaslException
    {
        if (saslname.indexOf('\u0000') >= 0) {
            throw new SaslException("Invalid saslname: NUL character is not permitted");
        }
        if (saslname.indexOf('=') < 0) {
            return saslname; // Fast path: no escape sequences present.
        }

        final StringBuilder result = new StringBuilder(saslname.length());
        for (int i = 0; i < saslname.length(); i++) {
            final char c = saslname.charAt(i);
            if (c != '=') {
                result.append(c);
                continue;
            }

            if (i + 2 >= saslname.length()) {
                throw new SaslException("Invalid saslname: incomplete escape sequence at position " + i);
            }

            final String escape = saslname.substring(i + 1, i + 3);
            switch (escape) {
                case "2C":
                    result.append(',');
                    break;
                case "3D":
                    result.append('=');
                    break;
                default:
                    throw new SaslException("Invalid saslname: unrecognized escape sequence '=" + escape + "'");
            }
            i += 2; // Skip the two characters just consumed (loop increment consumes the third).
        }
        return result.toString();
    }

    /**
     * Scans a raw, comma-prefixed extensions string (e.g. ",a=1,b=2") for invalid extensions: the reserved "m"
     * attribute, an extension reusing an already-assigned SCRAM attribute letter (a, c, e, i, n, p, r, s, v), or an
     * attribute name repeated more than once.
     *
     * "m" is reserved for a future mandatory extension mechanism (RFC 5802 §5.1); since none is currently defined,
     * its presence -- wherever it appears, not just in the leading reserved-mext position -- must fail
     * authentication rather than be silently ignored like a genuinely unrecognized extension. The other two checks
     * follow from the same section describing extensions as using "as-yet unassigned attribute names," and from the
     * protocol's one-value-per-attribute model, even though neither is spelled out as an explicit uniqueness rule.
     *
     * This method assumes its input is already shape-constrained by {@link #CLIENT_FIRST_MESSAGE_BARE}/
     * {@link #CLIENT_FINAL_MESSAGE}; the per-segment shape check here is defense-in-depth for direct callers, since
     * this method is {@code @VisibleForTesting}.
     *
     * @param rawExtensions the raw, comma-prefixed extensions string, or an empty string if none are present
     * @throws SaslException if a reserved "m" attribute is present, a segment is not a well-formed attr-val pair,
     *                       an extension reuses an assigned attribute letter, or an attribute name is repeated
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @VisibleForTesting
    static void rejectReservedMandatoryExtension(@Nonnull final String rawExtensions) throws SaslException
    {
        if (rawExtensions.isEmpty()) {
            return;
        }

        final Set<Character> seen = new HashSet<>();
        for (final String ext : rawExtensions.substring(1).split(",", -1))
        {
            if (!ATTR_VAL_PATTERN.matcher(ext).matches())
            {
                throw new SaslException("Invalid extension: '" + ext + "' is not a well-formed attr-val pair");
            }

            final char name = ext.charAt(0);

            if (name == 'm')
            {
                throw new SaslException("Client requested an unsupported mandatory extension ('" + ext + "'). Rejecting authentication.");
            }

            if (ASSIGNED_ATTRIBUTE_LETTERS.contains(name))
            {
                throw new SaslException("Invalid extension: '" + ext + "' reuses the already-assigned SCRAM attribute name '" + name + "'; extensions must use an as-yet unassigned attribute name.");
            }

            if (!seen.add(name))
            {
                throw new SaslException("Invalid extension: attribute name '" + name + "' appears more than once ('" + ext + "').");
            }
        }
    }

    /**
     * Calculates the downgrade protection hash over the SASL mechanisms and channel-binding types that were advertised
     * to this session.
     *
     * @return the downgrade protection hash
     * @see <a href="https://xmpp.org/extensions/xep-0474.html">XEP-0474: SASL SCRAM Downgrade Protection</a>
     */
    @Nonnull
    @VisibleForTesting
    String calculateDowngradeProtectionHash() throws SaslException
    {
        final StringBuilder s = new StringBuilder(availableMechanismsForSession.stream()
            .sorted(OCTET_ORDER)
            .collect(Collectors.joining("\u001E")));

        if (!availableChannelBindingTypesForSession.isEmpty())
        {
            s.append('\u001F');
            s.append(availableChannelBindingTypesForSession.stream().sorted(OCTET_ORDER).collect(Collectors.joining("\u001E")));
        }

        try
        {
            final MessageDigest digest = MessageDigest.getInstance(getDigestAlgorithmName());
            return Base64.getEncoder().encodeToString(digest.digest(s.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new SaslException("Unable to calculate downgrade protection hash.", e);
        }
    }
}
