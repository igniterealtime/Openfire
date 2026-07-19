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
package org.jivesoftware.openfire.fast;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Value object representing a FAST (XEP-0484) authentication token.
 *
 * Holds the raw token bytes, the username and mechanism it belongs to, and its expiry time.
 */
public class FastToken {

    private final String username;
    private final String mechanism;
    private final byte[] token;
    private final Instant expiry;

    /**
     * Constructs a FastToken.
     *
     * @param username  the local username this token belongs to (cannot be null)
     * @param mechanism the FAST SASL mechanism name (cannot be null)
     * @param token     the raw token bytes (cannot be null)
     * @param expiry    the instant at which this token expires (cannot be null)
     */
    public FastToken(@Nonnull final String username, @Nonnull final String mechanism,
                     @Nonnull final byte[] token, @Nonnull final Instant expiry) {
        this.username = Objects.requireNonNull(username, "username");
        this.mechanism = Objects.requireNonNull(mechanism, "mechanism");
        this.token = Objects.requireNonNull(token, "token").clone();
        this.expiry = Objects.requireNonNull(expiry, "expiry");
    }

    /**
     * Returns the username this token belongs to.
     *
     * @return the username (never null)
     */
    @Nonnull
    public String getUsername() {
        return username;
    }

    /**
     * Returns the FAST SASL mechanism name associated with this token.
     *
     * @return the mechanism name (never null)
     */
    @Nonnull
    public String getMechanism() {
        return mechanism;
    }

    /**
     * Returns a copy of the raw token bytes.
     *
     * @return the raw token bytes (never null)
     */
    @Nonnull
    public byte[] getToken() {
        return token.clone();
    }

    /**
     * Returns the instant at which this token expires.
     *
     * @return the expiry instant (never null)
     */
    @Nonnull
    public Instant getExpiry() {
        return expiry;
    }

    /**
     * Returns {@code true} if this token has expired relative to the current time.
     *
     * @return {@code true} if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof FastToken)) return false;
        final FastToken that = (FastToken) o;
        return username.equals(that.username)
            && mechanism.equals(that.mechanism)
            && Arrays.equals(token, that.token)
            && expiry.equals(that.expiry);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(username, mechanism, expiry);
        result = 31 * result + Arrays.hashCode(token);
        return result;
    }

    @Override
    public String toString() {
        return "FastToken{username='" + username + "', mechanism='" + mechanism + "', expiry=" + expiry + '}';
    }
}
