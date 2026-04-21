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
package org.jivesoftware.util.channelbinding;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLEngine;
import java.util.Optional;

/**
 * Provides a mechanism to extract channel binding data of a specific type from an SSL engine.
 *
 * Implementations of this interface attempt to obtain the channel binding value as defined in relevant RFCs
 * from a given SSL session, for the requested channel binding type (label). The availability and method of extraction
 * may depend on the underlying TLS provider, JDK version, or presence of third-party libraries.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5929">RFC 5929: Channel Bindings for TLS</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5705">RFC 5705: Keying Material Exporters for Transport Layer Security (TLS)</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9266">RFC 9266: Channel Bindings for TLS 1.3</a>
 */
public interface ChannelBindingProvider
{
    /**
     * Returns the RFC-defined unique prefix for the channel binding type this provider supports (e.g., "tls-exporter",
     * "tls-server-end-point").
     *
     * Note that these values are case-sensitive and must match exactly as defined in the respective RFCs.
     *
     * @return the channel binding type unique prefix (never null or empty)
     */
    String getType();

    /**
     * Attempts to extract the channel binding data from the provided SSL session.
     *
     * The returned value, if present, is the channel binding data as specified by the RFC for this provider's type.
     * If the session or provider does not support this operation, an empty Optional is returned.
     *
     * Callers should treat returned arrays as immutable.
     *
     * @param engine the SSL engine from which to extract channel binding data (must not be null)
     * @return an Optional containing the channel binding data, or empty if unavailable or unsupported
     */
    Optional<byte[]> getChannelBinding(@Nonnull final SSLEngine engine);
}
