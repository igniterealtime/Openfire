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
 * A single source of truth for unit tests that assert the implementation for SCRAM-SHA-256. This fixture contains:
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
public final class ScramSha256TestFixtures
{
    public static final String USER = "user";
    public static final String PASSWORD = "pencil";
    public static final String SALT = "W22ZaJ0SNY7soEsUEjb6gQ==";
    public static final int ITERATIONS = 4096;
    public static final String CLIENT_KEY = "Client Key";
    public static final String SERVER_KEY = "Server Key";
    public static final String CLIENT_NONCE = "rOprNGfwEbeRWgbNEkqO";
    public static final String STORED_KEY_HEX = "586e5df283e6dceb5c3e791d8b8528ec191e664045ce971792e2e6b5bb13e2a6";
    public static final String SERVER_KEY_HEX = "c1f3cbc1c13a9d35a14c0990eed97629ea225863e566a4314ab99f3f00e5d9d5";
    public static final Set<String> SUPPORTED_MECHANISMS = Set.of(ScramSha256SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME+"-PLUS");

    private ScramSha256TestFixtures() {}
}
