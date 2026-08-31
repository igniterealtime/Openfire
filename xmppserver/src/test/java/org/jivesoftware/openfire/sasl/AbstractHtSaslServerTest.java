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

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import javax.security.sasl.SaslException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies how {@link AbstractHtSaslServer#decodeAuthcId(String)} normalizes the authcid taken from an HT initiator
 * message.
 *
 * The returned value is compared against the username claimed in the stream's 'from' attribute, so both must be
 * normalized the same way: to a stringprep'ed node. A bare username is the expected form, but a domain-qualified one is
 * also accepted when it names this server.
 *
 * Any value that cannot be reduced to a username must fail as a SASL error rather than as an unchecked exception
 * escaping the mechanism, since the value is attacker-controlled.
 *
 * The behaviour is shared by both HT families, so it is exercised here through {@link HtSaslServer} rather than
 * separately in each subclass.
 */
class AbstractHtSaslServerTest
{
    /**
     * A bare username must be returned as its stringprep'ed form, so that a client using different casing than the
     * stream's 'from' still matches.
     */
    @Test
    void aBareUsernameIsStringprepped() throws Exception
    {
        assertEquals("user", server().decodeAuthcId("user"),
            "A bare authcid was altered unexpectedly.");
        assertEquals("user", server().decodeAuthcId("USER"),
            "A bare authcid was not case-normalized, so it will not match the expected username.");
    }

    /**
     * A domain-qualified authcid naming this server must be reduced to its node, matching how the rest of Openfire
     * treats an authzid that carries a domain.
     */
    @Test
    void aQualifiedUsernameForThisDomainIsReducedToItsNode() throws Exception
    {
        assertEquals("user", server().decodeAuthcId("user@example.org"),
            "A domain-qualified authcid for this server was not reduced to its node.");
    }

    /**
     * An authcid naming a domain other than this server's must be rejected rather than silently reduced to its node.
     */
    @Test
    void aQualifiedUsernameForAnotherDomainIsRejected()
    {
        assertThrows(SaslException.class, () -> server().decodeAuthcId("user@elsewhere.example"),
            "An authcid naming another domain was accepted.");
    }

    /**
     * An authcid with an empty node must be rejected. The JID parser refuses to construct it, and the mechanism must
     * surface that as a SASL error rather than let it escape unchecked.
     */
    @Test
    void aQualifiedUsernameWithoutANodeIsRejected()
    {
        assertThrows(SaslException.class, () -> server().decodeAuthcId("@example.org"),
            "An authcid with no node was accepted, which would leave no username to compare against the stream's 'from'.");
    }

    /**
     * An authcid with an empty domain must be rejected, and must not fail on a null domain while it is being compared
     * against this server's.
     */
    @Test
    void aQualifiedUsernameWithoutADomainIsRejected()
    {
        assertThrows(SaslException.class, () -> server().decodeAuthcId("user@"),
            "An authcid with no domain was accepted, or failed with something other than a SASL error.");
    }

    /**
     * Without a session there is no domain to validate a qualified authcid against, so it must be rejected rather than
     * accepted unchecked.
     */
    @Test
    void aQualifiedUsernameIsRejectedWithoutASessionToResolveTheDomainAgainst()
    {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());

        assertThrows(SaslException.class, () -> server.decodeAuthcId("user@example.org"),
            "A domain-qualified authcid was accepted with no session available to validate the domain.");
    }

    /**
     * An authcid containing code points that stringprep prohibits must fail as a SASL error, not as an unchecked
     * exception escaping the mechanism.
     */
    @Test
    void anUnpreppableUsernameIsRejected()
    {
        assertThrows(SaslException.class, () -> server().decodeAuthcId("us er"),
            "An authcid containing characters that cannot be stringprepped was accepted.");
    }

    /**
     * Returns an HT SASL server whose properties carry a session for the {@code example.org} domain.
     *
     * @return a server usable for authcid decoding
     */
    private static HtSaslServer server()
    {
        final LocalSession session = mock(LocalSession.class);
        when(session.getServerName()).thenReturn("example.org");
        return new HtSaslServer(FastTokenManager.HT_SHA_256_NONE,
            Map.of(LocalSession.class.getCanonicalName(), session));
    }
}
