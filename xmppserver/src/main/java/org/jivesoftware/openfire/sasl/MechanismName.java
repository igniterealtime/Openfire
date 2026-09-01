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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Classification of SASL mechanism names by the conventions encoded in the names themselves.
 *
 * Every method here is a pure function of the mechanism name. Nothing in this class consults
 * configuration, session state or the set of mechanisms that this server happens to support, which
 * makes each of them exhaustively testable against a fixed list of names.
 *
 * Callers are expected to pass an upper-cased name, which is the form that
 * {@code SASLAuthentication} normalises inbound mechanism names to.
 */
public final class MechanismName
{
    private MechanismName() {
    }

    /**
     * Returns {@code true} if the given SASL mechanism name is a member of the SCRAM family.
     *
     * SCRAM mechanism names are, per RFC 5802 § 4, the string {@code SCRAM-} followed by the name of the underlying
     * hash function (optionally suffixed with {@code -PLUS} for the channel binding variant).
     *
     * @param mechanismName the SASL mechanism name to check (cannot be null)
     * @return {@code true} if the mechanism is a SCRAM mechanism; {@code false} otherwise
     */
    public static boolean isScram(@Nonnull final String mechanismName)
    {
        return mechanismName.startsWith("SCRAM-");
    }

    /**
     * Returns {@code true} if the given SASL mechanism name is a FAST mechanism (HT-* or HT2-*).
     *
     * FAST mechanisms are not registered in the {@code sasl.mechs} configuration property, so they must be recognised
     * independently of the standard mechanism list when FAST is enabled.
     *
     * @param mechanismName the SASL mechanism name to check (cannot be null)
     * @return {@code true} if the mechanism is a FAST HT-family mechanism; {@code false} otherwise
     */
    public static boolean isFast(@Nonnull final String mechanismName)
    {
        return FastTokenManager.isMechanism(mechanismName);
    }

    /**
     * Returns {@code true} if the given SASL mechanism name requires channel binding.
     *
     * <p>Two naming conventions are recognised:</p>
     * <ul>
     *   <li>The {@code -PLUS} suffix used by SCRAM mechanisms (e.g. {@code SCRAM-SHA-1-PLUS}).</li>
     *   <li>The {@code -UNIQ}, {@code -ENDP}, and {@code -EXPR} suffixes used by HT-* and HT2-*
     *       mechanisms, mapping to {@code tls-unique}, {@code tls-server-end-point}, and
     *       {@code tls-exporter} channel-binding types respectively (per the HT draft, Table 1).</li>
     * </ul>
     *
     * @param mechanismName the SASL mechanism name to check (cannot be null)
     * @return {@code true} if the mechanism requires channel binding; {@code false} otherwise
     */
    public static boolean requiresChannelBinding(@Nonnull final String mechanismName)
    {
        return mechanismName.endsWith("-PLUS")
            || mechanismName.endsWith("-UNIQ")
            || mechanismName.endsWith("-ENDP")
            || mechanismName.endsWith("-EXPR");
    }

    /**
     * Returns the specific TLS channel-binding type name required by the given SASL mechanism, or
     * {@code null} if the mechanism does not require a specific one.
     *
     * <p>Two naming conventions are recognised:</p>
     * <ul>
     *   <li>The {@code -PLUS} suffix used by SCRAM mechanisms (e.g. {@code SCRAM-SHA-1-PLUS}) —
     *       these mechanisms negotiate the exact CB type at runtime, so {@code null} is returned
     *       and availability is checked elsewhere (any CB type is sufficient).</li>
     *   <li>The {@code -UNIQ}, {@code -ENDP}, and {@code -EXPR} suffixes used by HT-* and HT2-*
     *       mechanisms — these encode a specific CB type in the mechanism name, so the exact type
     *       is returned ({@code "tls-unique"}, {@code "tls-server-end-point"}, or
     *       {@code "tls-exporter"} per the HT draft, Table 1).</li>
     * </ul>
     *
     * Note that a {@code null} return does not imply that the mechanism needs no channel binding; use
     * {@link #requiresChannelBinding(String)} for that. A {@code -PLUS} mechanism requires channel binding but has no
     * single required type.
     *
     * @param mechanismName the SASL mechanism name to check (cannot be null)
     * @return the required TLS channel-binding type name (e.g. {@code "tls-unique"}), or
     *         {@code null} if no specific type is required (includes NONE and PLUS mechanisms)
     */
    @Nullable
    public static String requiredChannelBindingType(@Nonnull final String mechanismName)
    {
        if (mechanismName.endsWith("-UNIQ")) return "tls-unique";
        if (mechanismName.endsWith("-ENDP")) return "tls-server-end-point";
        if (mechanismName.endsWith("-EXPR")) return "tls-exporter";
        return null; // NONE, PLUS (negotiated at runtime), or any non-CB mechanism
    }
}
