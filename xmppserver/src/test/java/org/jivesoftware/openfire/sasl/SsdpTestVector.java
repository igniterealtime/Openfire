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

import java.util.Set;

/**
 * The XEP-0474 downgrade-protection test vectors: the SASL mechanisms and channel-binding types that were
 * advertised to a session.
 *
 * These are identical for every SCRAM hash function; only the expected hash differs per algorithm, which each
 * {@code ScramShaXTestFixtures} supplies.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0474.html">XEP-0474: SASL SCRAM Downgrade Protection</a>
 */
public enum SsdpTestVector
{
    /**
     * The worked example of XEP-0474 §6.3, and the only vector whose SCRAM-SHA-1 hash is published there.
     */
    SPECIFICATION_EXAMPLE(
        Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"),
        Set.of("tls-server-end-point", "tls-exporter")),

    /**
     * The specification's example without channel binding, where the %x1F section is omitted entirely rather than
     * emitted as a bare separator.
     */
    WITHOUT_CHANNEL_BINDING_TYPES(
        Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"),
        Set.of()),

    /**
     * A full advertisement: exercises sorting beyond two entries, and includes mechanisms outside the SCRAM
     * family, which are hashed too.
     */
    FULL_ADVERTISEMENT(
        Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS", "SCRAM-SHA-256", "SCRAM-SHA-256-PLUS", "PLAIN", "EXTERNAL"),
        Set.of("tls-unique", "tls-exporter", "tls-server-end-point")),

    /**
     * A degenerate case, where a stray trailing separator would still produce something plausible.
     */
    SINGLE_MECHANISM_AND_TYPE(
        Set.of("SCRAM-SHA-256-PLUS"),
        Set.of("tls-exporter")),

    /**
     * A degenerate case with no channel-binding section at all.
     */
    SINGLE_MECHANISM(
        Set.of("SCRAM-SHA-1"),
        Set.of());

    private final Set<String> mechanisms;
    private final Set<String> channelBindingTypes;

    SsdpTestVector(final Set<String> mechanisms, final Set<String> channelBindingTypes)
    {
        this.mechanisms = mechanisms;
        this.channelBindingTypes = channelBindingTypes;
    }

    /**
     * The SASL mechanism names that were advertised to the session.
     */
    public Set<String> mechanisms()
    {
        return mechanisms;
    }

    /**
     * The channel-binding type names that were advertised to the session; empty when XEP-0440 was not used.
     */
    public Set<String> channelBindingTypes()
    {
        return channelBindingTypes;
    }
}
