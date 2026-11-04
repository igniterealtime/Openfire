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
package org.jivesoftware.openfire.server.s2stest;

/**
 * The 11 checks of the server-to-server connection test, in the order the steps of a connection happen.
 *
 * @author Matthew Vivian
 */
public enum S2SCheck
{
    /**
     * 1. The target XMPP domain is valid, not served locally, and allowed by the server-to-server policy.
     */
    DOMAIN("domain"),

    /**
     * 2. DNS resolved the domain to one or more server addresses.
     */
    DNS("dns"),

    /**
     * 3. A TCP connection was established to a server-to-server endpoint.
     */
    TCP("tcp"),

    /**
     * 4. The remote server opened an XMPP stream.
     */
    STREAM("stream"),

    /**
     * 5. TLS was negotiated.
     */
    TLS("tls"),

    /**
     * 6. The remote certificate was received, is valid and trusted, and matches the target domain.
     */
    CERTIFICATE("certificate"),

    /**
     * 7. Authentication succeeded, preferably with SASL EXTERNAL.
     */
    AUTHENTICATION("authentication"),

    /**
     * 8. An authenticated outgoing server session was registered.
     */
    SESSION("session"),

    /**
     * 9. The remote server itself replied to an XMPP ping.
     */
    PING("ping"),

    /**
     * 10. The reply to the ping was the expected result.
     */
    RESPONSE("response"),

    /**
     * 11. No warnings or errors were logged for this attempt.
     */
    LOG("log");

    /**
     * The prefix shared by every i18n key of the checklist.
     */
    static final String KEY_PREFIX = "server2server.settings.testing.check.";

    private final String keySegment;

    S2SCheck(final String keySegment)
    {
        this.keySegment = keySegment;
    }

    /**
     * The i18n key of this check's title, as shown in the checklist.
     *
     * @return the resource bundle key, never null.
     */
    public String titleKey()
    {
        return messageKey("title");
    }

    /**
     * The i18n key of one of this check's messages.
     *
     * @param suffix the message within this check, for example "fail.refused".
     * @return the resource bundle key, never null.
     */
    String messageKey(final String suffix)
    {
        return KEY_PREFIX + keySegment + "." + suffix;
    }
}
