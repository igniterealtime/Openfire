/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved
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

import com.google.common.annotations.VisibleForTesting;
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;

import java.util.Map;
import java.util.Set;

/**
 * Implements the SCRAM-SHA-512 (and its channel binding -PLUS variant) server-side mechanism.
 *
 * The SCRAM exchange itself is implemented by the hash-agnostic {@link ScramSaslServer} superclass. This class binds
 * that exchange to the SHA-512 hash function.
 *
 * @author Guus der Kinderen
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-melnikov-scram-sha-512">draft-melnikov-scram-sha-512</a>
 */
public class ScramSha512SaslServer extends ScramSaslServer {

    /**
     * The name of the base (non-PLUS) mechanism implemented by this server. Note that at the time of writing, this
     * name isn't IANA-registered, but follows the convention for SCRAM mechanism names and the definition in
     * draft-melnikov-scram-sha-512
     */
    public static final String MECHANISM_NAME = "SCRAM-SHA-512";

    /**
     * The JCA name of the HMAC algorithm that corresponds to this mechanism's hash function
     */
    public static final String HMAC_ALGORITHM_NAME = "HmacSHA512";

    /**
     * The JCA name of the message digest that corresponds to this mechanism's hash function.
     */
    public static final String DIGEST_ALGORITHM_NAME = "SHA-512";

    /**
     * Stores a server-side secret used when handling authentication attempts for non-existing users in SCRAM-SHA-512 (-PLUS).
     *
     * Prefer to use #getServerSecretForNonExistentUsers() instead of accessing this property directly, as
     * the method will make sure that the one-time initialization that's required for usage will occur.
     *
     * @see #getServerSecretForNonExistentUsers()
     */
    @VisibleForTesting
    static final SystemProperty<String> SERVER_SECRET_NONEXISTENT_USERS = SystemProperty.Builder.ofType(String.class)
        .setKey("sasl.scram-sha-512.server-secret.nonexistent-users")
        .setEncrypted(true)
        .setDynamic(Boolean.TRUE)
        .build();

    /**
     * Retrieves a server-side secret used when handling authentication attempts for non-existing users in
     * SCRAM-SHA-512 (-PLUS).
     *
     * This method ensures that the one-time initialization that is required for usage will occur.
     *
     * Instead of failing immediately, the server derives deterministic, fake SCRAM credentials (such as stored keys,
     * server keys, and where applicable salt values) based on this secret. This ensures that authentication processing
     * for non-existing users is indistinguishable from that of existing users.
     *
     * This mechanism helps protect against user enumeration attacks by preventing observable differences in behavior
     * between existing and non-existing accounts.
     *
     * Changing (rotating) this value will cause different derived values to be generated for non-existing users.
     * This does not affect authentication of existing users but can invalidate consistency of ongoing or repeated
     * authentication attempts for non-existing users.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    public synchronized static String getServerSecretForNonExistentUsers()
    {
        // OF-3258: Ensure a consistent but unpredictable server secret is available.
        final String serverSecret = SERVER_SECRET_NONEXISTENT_USERS.getValue();
        if (serverSecret == null || serverSecret.trim().isEmpty()) {
            SERVER_SECRET_NONEXISTENT_USERS.setValue(StringUtils.randomString(29));
        }
        return SERVER_SECRET_NONEXISTENT_USERS.getValue();
    }

    public static final SystemProperty<Integer> ITERATION_COUNT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("sasl.scram-sha-512.iteration-count")
        .setDefaultValue(ScramUtils.DEFAULT_ITERATION_COUNT)
        .setDynamic(Boolean.TRUE)
        .build();

    public ScramSha512SaslServer(final boolean isPlusMechanism, final Map<String, ?> props)
    {
        super(isPlusMechanism, props, ChannelBindingProviderManager.getInstance(), SASLAuthentication.getSupportedMechanisms());
    }

    /**
     * Constructor for testing purposes.
     */
    @VisibleForTesting
    ScramSha512SaslServer(final boolean isPlusMechanism, final Map<String, ?> props, final ChannelBindingProviderManager channelBindingProviderManager, final Set<String> serverSupportedSaslMechanismNames)
    {
        super(isPlusMechanism, props, channelBindingProviderManager, serverSupportedSaslMechanismNames);
    }

    @Override
    protected String getMechanismBaseName() {
        return MECHANISM_NAME;
    }

    @Override
    protected String getHmacAlgorithmName() {
        return HMAC_ALGORITHM_NAME;
    }

    @Override
    protected String getDigestAlgorithmName() {
        return DIGEST_ALGORITHM_NAME;
    }

    @Override
    protected int getDefaultIterationCount() {
        return ITERATION_COUNT.getValue();
    }

    @Override
    protected String getNonExistentUserSecret() {
        return getServerSecretForNonExistentUsers();
    }
}
