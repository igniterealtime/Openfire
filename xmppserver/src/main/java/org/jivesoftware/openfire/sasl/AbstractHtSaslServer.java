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

import org.jivesoftware.openfire.fast.FastToken;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * Abstract base class shared by FAST HT-* and HT2-* SASL server implementations.
 *
 * <p>Provides the common state ({@code complete}, {@code authorizationId}, {@code rotatedToken})
 * and the boilerplate {@link SaslServer} methods that are identical across all HT variants:
 * {@link #isComplete()}, {@link #getAuthorizationID()}, {@link #getRotatedToken()},
 * {@link #unwrap}, {@link #wrap}, {@link #getNegotiatedProperty}, and {@link #dispose}.</p>
 *
 * <p>Concrete subclasses must implement {@link #getMechanismName()} and
 * {@link #evaluateResponse(byte[])}.</p>
 */
abstract class AbstractHtSaslServer implements SaslServer {

    protected boolean complete = false;
    protected String authorizationId = null;
    protected FastToken rotatedToken = null;

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public String getAuthorizationID() {
        if (!complete) {
            throw new IllegalStateException("Authentication not yet complete");
        }
        return authorizationId;
    }

    /**
     * Returns the rotated FAST token produced after successful authentication, or {@code null}
     * if authentication has not completed successfully.
     *
     * @return the rotated {@link FastToken}, or {@code null}
     */
    public FastToken getRotatedToken() {
        return rotatedToken;
    }

    @Override
    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        throw new SaslException(getMechanismName() + " does not support integrity/confidentiality");
    }

    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        throw new SaslException(getMechanismName() + " does not support integrity/confidentiality");
    }

    @Override
    public Object getNegotiatedProperty(final String propName) {
        return null;
    }

    @Override
    public void dispose() throws SaslException {
        complete = false;
        authorizationId = null;
        rotatedToken = null;
    }

    /**
     * Returns the index of the first occurrence of {@code target} in {@code array} starting at
     * {@code fromIndex}, or {@code -1} if not found.
     */
    protected static int indexOf(final byte[] array, final byte target, final int fromIndex) {
        for (int i = fromIndex; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }
}
