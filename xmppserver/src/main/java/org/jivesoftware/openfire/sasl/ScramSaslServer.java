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

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.Arrays;

public abstract class ScramSaslServer implements SaslServer
{
    /**
     * Extracts the raw GS2 header from a SCRAM client-first-message byte array.
     *
     * The GS2 header is defined in RFC 5802 as:
     * <pre>
     * gs2-header = gs2-cbind-flag "," [authzid] ","
     * </pre>
     * and always terminates with a trailing comma.
     *
     * This method performs a byte-level scan of the input and returns a copy of the original byte array from index
     * {@code 0} up to and including the second comma (i.e., the full GS2 header including its trailing comma).
     *
     * No character decoding or normalization is performed. This ensures that the returned GS2 header is byte-for-
     * byte identical to the original input, which is required for correct SCRAM-SHA-1-PLUS channel binding validation.
     *
     * @param data the raw SCRAM client-first-message bytes
     * @return a byte array containing the complete GS2 header including the trailing comma
     * @throws SaslException if the input does not contain a valid GS2 header
     */
    protected static byte[] extractRawGS2Header(final byte[] data) throws SaslException
    {
        // The GS2 header ends at the second comma.
        int commaCount = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ',') {
                commaCount++;
                if (commaCount == 2) {
                    return Arrays.copyOfRange(data, 0, i+1); // +1 to include the comma itself.
                }
            }
        }
        throw new SaslException("Invalid GS2 header format");
    }
}
