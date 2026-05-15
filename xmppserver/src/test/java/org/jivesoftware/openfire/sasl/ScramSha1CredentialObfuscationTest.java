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

import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;

import javax.annotation.Nonnull;
import java.util.HashMap;

/**
 * Unit tests for fake key generation, salt handling and user enumeration protection in {@link ScramSha1SaslServer}.
 */
public class ScramSha1CredentialObfuscationTest extends AbstractScramCredentialObfuscationTest
{
    /**
     * Returns the server secret property used to derive deterministic fake credentials.
     *
     * @return the non-existent user server secret property.
     */
    @Override
    protected SystemProperty<String> serverSecretProperty()
    {
        return ScramSha1SaslServer.SERVER_SECRET_NONEXISTENT_USERS;
    }

    /**
     * Returns the expected hash output length in bytes.
     *
     * @return the expected hash length.
     */
    @Override
    protected int expectedHashLengthBytes()
    {
        return 20;
    }

    /**
     * Provides the salt value associated with an existing user.
     *
     * @return the test fixture salt value for an existing user.
     */
    @Override
    protected String configuredUserSalt()
    {
        return ScramSha1TestFixtures.SALT;
    }

    /**
     * Creates a SCRAM-SHA-1 SASL server instance for test execution.
     *
     * @return a configured {@link ScramSha1SaslServer} instance.
     */
    @Nonnull
    @Override
    protected ScramSha1SaslServer newServer()
    {
        return new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), ScramSha1TestFixtures.SUPPORTED_MECHANISMS);
    }
}
