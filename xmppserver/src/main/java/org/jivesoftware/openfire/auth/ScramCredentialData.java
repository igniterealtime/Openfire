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
package org.jivesoftware.openfire.auth;

import java.util.Locale;

/**
 * SCRAM credentials for a single user and a single mechanism.
 *
 * The mechanism is stored in its normalized ('base') form: upper-cased and without the optional
 * {@code -PLUS} suffix (channel binding does not affect the stored credential material, only the SASL exchange).
 *
 * This type exists to give the credential storage a stable, mechanism-keyed shape. In the current release only
 * {@code SCRAM-SHA-1} is produced and consumed; the type is intentionally kept minimal.
 */
public class ScramCredentialData
{
    public final String mechanism;
    public final String salt;
    public final int iterations;
    public final String storedKey;
    public final String serverKey;

    public ScramCredentialData(final String mechanism, final String salt, final int iterations, final String storedKey, final String serverKey)
    {
        this.mechanism = normalizeMechanismName(mechanism);
        this.salt = salt;
        this.iterations = iterations;
        this.storedKey = storedKey;
        this.serverKey = serverKey;
    }

    /**
     * Normalizes a SCRAM mechanism name to upper case and strips the optional {@code -PLUS} suffix.
     *
     * @param mechanism The mechanism name (for example: {@code SCRAM-SHA-1-PLUS}).
     * @return The normalized base mechanism name (for example: {@code SCRAM-SHA-1}).
     * @throws IllegalArgumentException if the value is null, empty, or not a SCRAM mechanism name.
     */
    public static String normalizeMechanismName(final String mechanism)
    {
        if (mechanism == null || mechanism.trim().isEmpty()) {
            throw new IllegalArgumentException("SCRAM mechanism cannot be null or empty.");
        }

        final String normalized = mechanism.trim().toUpperCase(Locale.ROOT);
        final String baseName = normalized.endsWith("-PLUS")
            ? normalized.substring(0, normalized.length() - "-PLUS".length())
            : normalized;

        if (!baseName.startsWith("SCRAM-SHA-")) {
            throw new IllegalArgumentException("Unsupported SCRAM mechanism name: " + mechanism);
        }

        return baseName;
    }
}
