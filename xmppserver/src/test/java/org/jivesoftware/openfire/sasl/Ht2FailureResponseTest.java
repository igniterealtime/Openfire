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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Ht2FailureResponseTest {

    @Test
    void encodesEveryStandardFailureDescription() {
        assertFrame(Ht2FailureResponse.UNKNOWN_USER, Ht2FailureResponse.UNKNOWN_USER);
        assertFrame(Ht2FailureResponse.INVALID_TOKEN, Ht2FailureResponse.INVALID_TOKEN);
        assertFrame(Ht2FailureResponse.OTHER_ERROR, Ht2FailureResponse.OTHER_ERROR);
    }

    @Test
    void mapsUnknownDescriptionsToOtherError() {
        assertFrame("implementation-specific-detail", Ht2FailureResponse.OTHER_ERROR);
    }

    private static void assertFrame(final String input, final String expectedDescription) {
        final byte[] description = expectedDescription.getBytes(StandardCharsets.US_ASCII);
        final byte[] expected = new byte[description.length + 1];
        expected[0] = 0x01;
        System.arraycopy(description, 0, expected, 1, description.length);
        assertArrayEquals(expected, Ht2FailureResponse.encode(input));
    }
}
