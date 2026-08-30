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

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Encodes the failure responder message defined by draft-ietf-kitten-sasl-ht. */
public final class Ht2FailureResponse {
    public static final String UNKNOWN_USER = "unknown-user";
    public static final String INVALID_TOKEN = "invalid-token";
    public static final String OTHER_ERROR = "other-error";
    private static final Set<String> STANDARD_DESCRIPTIONS = Set.of(UNKNOWN_USER, INVALID_TOKEN, OTHER_ERROR);

    private Ht2FailureResponse() {
    }

    /** Unknown descriptions are deliberately represented as the standard catch-all value. */
    public static byte[] encode(@Nonnull final String description) {
        final String safeDescription = STANDARD_DESCRIPTIONS.contains(description) ? description : OTHER_ERROR;
        final byte[] value = safeDescription.getBytes(StandardCharsets.US_ASCII);
        final byte[] result = new byte[value.length + 1];
        result[0] = 0x01;
        System.arraycopy(value, 0, result, 1, value.length);
        return result;
    }
}
