/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.fast.FastToken;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Date;

/**
 * Renders and delivers the elements that conclude (or advance) a SASL negotiation: {@code <challenge/>},
 * {@code <success/>} and {@code <failure/>}, for both the RFC 6120 SASL profile and SASL2 (XEP-0388).
 *
 * This is deliberately the only place in the SASL implementation that constructs those elements, so that the exact
 * shape of what a peer receives can be reasoned about, and asserted on in tests, in one place.
 *
 * Note that {@link #authenticationFailed(LocalSession, Failure, boolean, byte[])} does more than render: it also
 * counts consecutive failures on the session and closes the connection once the configured limit is reached. That
 * policy travelled with the rendering when this class was extracted; splitting the two is a separate change.
 */
final class SaslOutcome
{
    private static final Logger Log = LoggerFactory.getLogger(SaslOutcome.class);

    private SaslOutcome() {
    }

    /**
     * Sends a SASL {@code <challenge/>} to the peer.
     *
     * @param session    the session to deliver to (cannot be null).
     * @param challenge  the mechanism-specific challenge data (can be null).
     * @param usingSASL2 {@code true} to use the SASL2 namespace; {@code false} for RFC 6120 SASL.
     */
    static void sendChallenge(final Session session, final byte[] challenge, final boolean usingSASL2)
    {
        sendElement(session, "challenge", challenge, usingSASL2);
    }

    /**
     * Sends an RFC 6120 SASL {@code <success/>} to the peer.
     *
     * SASL2 successes are structured rather than a bare base64 payload; use
     * {@link #buildSasl2SuccessElement(byte[], String, String, FastToken)} for those.
     *
     * @param session     the session to deliver to (cannot be null).
     * @param successData the mechanism-specific success data (can be null).
     */
    static void sendSuccess(final Session session, final byte[] successData)
    {
        sendElement(session, "success", successData, false);
    }

    private static void sendElement(final Session session, final String element, final byte[] data, final boolean usingSASL2)
    {
        final Element reply = DocumentHelper.createElement(QName.get(element, usingSASL2 ? SASLAuthentication.SASL2_NAMESPACE : SASLAuthentication.SASL_NAMESPACE));
        if (data != null) {
            String data_b64 = Base64.getEncoder().encodeToString(data).trim();
            if (data_b64.isEmpty()) {
                // Empty-payload sentinel. Only meaningful for SASL1; unreachable on the SASL2 path, whose sole caller here is <challenge>, which is never sent with empty/missing data.
                data_b64 = "=";
            }
            reply.addText(data_b64);
        }
        session.deliverRawText(reply.asXML());
    }

    /**
     * Builds a SASL2 &lt;success/&gt; element.
     *
     * @param successData optional mechanism-specific success data (can be null).
     * @param authorizationIdentity the bare JID authorization identity (e.g. user@domain or uuid@domain for anonymous).
     * @param resource the bound resource, or null if no resource was bound.
     * @param fastToken optional FAST token to include in the response as per XEP-0484 (can be null).
     * @return the &lt;success/&gt; element.
     */
    static Element buildSasl2SuccessElement(final byte[] successData, final String authorizationIdentity, final String resource, final FastToken fastToken)
    {
        final Element success = DocumentHelper.createElement(new QName("success", new Namespace("", SASLAuthentication.SASL2_NAMESPACE)));
        if (successData != null && successData.length > 0) {
            final String data_b64 = Base64.getEncoder().encodeToString(successData).trim();
            success.addElement("additional-data").setText(data_b64);
        }
        final StringBuilder authId = new StringBuilder(authorizationIdentity != null ? authorizationIdentity : "");
        if (resource != null) {
            authId.append('/').append(resource);
        }
        success.addElement("authorization-identifier").setText(authId.toString());
        // XEP-0484: include <token> if a FAST token was issued.
        if (fastToken != null) {
            final Element tokenEl = success.addElement(new QName("token", new Namespace("", FastTokenManager.NAMESPACE)));
            tokenEl.addAttribute("expiry", XMPPDateTimeFormat.format(Date.from(fastToken.getExpiry())));
            tokenEl.addAttribute("token", fastToken.getTokenString());
        }
        return success;
    }

    /**
     * Delivers a SASL {@code <failure/>} to the peer, and closes the session if it has now failed to authenticate
     * more often than {@code xmpp.auth.retries} permits.
     *
     * @param session    the session that failed to authenticate (cannot be null).
     * @param failure    the SASL error condition (cannot be null).
     * @param usingSASL2 {@code true} to use the SASL2 namespace; {@code false} for RFC 6120 SASL.
     */
    static void authenticationFailed(final LocalSession session, final Failure failure, final boolean usingSASL2)
    {
        final Element reply = DocumentHelper.createElement(QName.get("failure", usingSASL2 ? SASLAuthentication.SASL2_NAMESPACE : SASLAuthentication.SASL_NAMESPACE));
        if (usingSASL2) {
            // SASL2 still uses the original SASL namespace for failure reasons.
            reply.addElement(failure.toString(), SASLAuthentication.SASL_NAMESPACE);
        } else {
            reply.addElement(failure.toString());
        }
        session.deliverRawText(reply.asXML());
        // Give a number of retries before closing the connection
        Integer retries = (Integer) session.getSessionData("authRetries");
        if (retries == null) {
            retries = 1;
        }
        else {
            retries = retries + 1;
        }
        session.setSessionData("authRetries", retries);
        if (retries >= JiveGlobals.getIntProperty("xmpp.auth.retries", 3) ) {
            // Close the connection
            Log.debug( "Closing session that failed to authenticate {} times: {}", retries, session );
            session.markNonResumable();
            session.close();
        }
    }
}
