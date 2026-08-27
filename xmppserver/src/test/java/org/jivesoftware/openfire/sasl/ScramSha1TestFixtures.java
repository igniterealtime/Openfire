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
 * A single source of truth for unit tests that assert the implementation for SCRAM-SHA-1. This fixture contains:
 *
 * <ul>
 *     <li>stable shared test data</li>
 *     <li>canonical protocol constants</li>
 *     <li>repeated values with semantic meaning</li>
 * </ul>
 *
 * Usage of constants from this class is encouraged to avoid hard-coding values. This makes intent clearer by
 * referencing to data by their name/meaning rather than by using raw value (that could be construed as a magic value).
 */
public final class ScramSha1TestFixtures
{
    public static final String USER = "user";
    public static final String PASSWORD = "pencil";
    public static final String SALT = "QSXCR+Q6sek8bf92";
    public static final int ITERATIONS = 4096;
    public static final String CLIENT_KEY = "Client Key";
    public static final String SERVER_KEY = "Server Key";
    public static final String CLIENT_NONCE = "fyko+d2lbbFgONRv9qkxdawL";
    public static final String STORED_KEY_HEX = "e9d94660c39d65c38fbad91c358f14da0eef2bd6";
    public static final String SERVER_KEY_HEX = "0fe09258b3ac852ba502cc62ba903eaacdbf7d31";
    public static final Set<String> SUPPORTED_MECHANISMS = Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha1SaslServer.MECHANISM_NAME+"-PLUS");
    public static final Set<String> SUPPORTED_CHANNEL_BINDING_TYPES = Set.of("tls-server-end-point", "tls-exporter");


    /**
     * The XEP-0474 downgrade protection hash over {@link AbstractScramSaslServerTest#SSDP_ADVERTISED_MECHANISMS} and
     * {@link AbstractScramSaslServerTest#SSDP_CHANNEL_BINDING_TYPES}, using this mechanism's hash function.
     *
     * The SCRAM-SHA-1 value is the one published in the worked example of XEP-0474 §6.3.
     */
    public static final String SSDP_HASH = "G6k/rBLDqgOhRRaCuuatSDFkJ08=";

    /**
     * As {@link #SSDP_HASH}, but over the mechanism list alone, as produced when no channel-binding types were advertised.
     */
    public static final String SSDP_HASH_WITHOUT_CHANNEL_BINDING_TYPES = "g00gt4Qd0gJ3EvnclTnY0KEYfRg=";

    private ScramSha1TestFixtures() {}
}
