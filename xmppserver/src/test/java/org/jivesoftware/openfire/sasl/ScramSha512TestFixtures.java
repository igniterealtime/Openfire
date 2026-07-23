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
 * A single source of truth for unit tests that assert the implementation for SCRAM-SHA-512. This fixture contains:
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
public final class ScramSha512TestFixtures
{
    public static final String USER = "user";
    public static final String PASSWORD = "pencil";
    public static final String SALT = "W22ZaJ0SNY7soEsUEjb6gQ==";
    public static final int ITERATIONS = 4096;
    public static final String CLIENT_KEY = "Client Key";
    public static final String SERVER_KEY = "Server Key";
    public static final String CLIENT_NONCE = "rOprNGfwEbeRWgbNEkqO";
    public static final String STORED_KEY_HEX = "e8002e6f7d3ae446119b216933644dc2a2be7869eb918b8459b5e7d7d2ec12606aceef106825cd735170a675fd3611f684affad1dce3f43a0ee43bd590e1dbbe";
    public static final String SERVER_KEY_HEX = "8d91db6230b5687874fe129bc7206e1858c3ae08e02934f57ac03b6b05a229c459d28ff46f5c9611e6c179256490215ec1ff759cb0df285db89af0f99e613aac";
    public static final Set<String> SUPPORTED_MECHANISMS = Set.of(ScramSha512SaslServer.MECHANISM_NAME, ScramSha512SaslServer.MECHANISM_NAME+"-PLUS");

    private ScramSha512TestFixtures() {}
}
