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
package org.jivesoftware.openfire.sasl.task;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An example {@link Sasl2TaskProvider} modelled on XEP-0480: SASL Upgrade Tasks, in which a client that authenticated
 * with a password is asked to derive and hand over credentials for a stronger SCRAM mechanism, so that the server can
 * offer that mechanism on subsequent connections.
 *
 * This illustrates the advertise-and-opt-in pattern. The task is optional, and a client that does not implement it
 * must not be confronted with it, so:
 * <ol>
 *     <li>{@link #getStreamFeatureElements(LocalSession)} advertises the mechanisms that can be upgraded to, inside
 *     the SASL2 {@code <authentication/>} stream feature;</li>
 *     <li>{@link #onAuthenticateReceived(Sasl2TaskContext, Element)} records the peer's request, validating it
 *     against what was actually advertised;</li>
 *     <li>{@link #getOfferedTasks(Sasl2TaskContext)} offers the task only to a peer that asked for it, and only when
 *     the authentication that just succeeded actually proved knowledge of the password.</li>
 * </ol>
 *
 * The exchange is:
 * <pre>{@code
 * S: <features xmlns='http://etherx.jabber.org/streams'>
 *      <authentication xmlns='urn:xmpp:sasl:2'>
 *        <mechanism>SCRAM-SHA-1</mechanism>
 *        <upgrade xmlns='urn:xmpp:sasl:upgrade:0'>SCRAM-SHA-512</upgrade>
 *      </authentication>
 *    </features>
 * C: <authenticate xmlns='urn:xmpp:sasl:2' mechanism='SCRAM-SHA-1'>
 *      <initial-response>...</initial-response>
 *      <upgrade xmlns='urn:xmpp:sasl:upgrade:0'>SCRAM-SHA-512</upgrade>
 *    </authenticate>
 * ...
 * S: <continue xmlns='urn:xmpp:sasl:2'>
 *      <additional-data>...</additional-data>
 *      <tasks><task>UPGR-SCRAM-SHA-512</task></tasks>
 *    </continue>
 * C: <next xmlns='urn:xmpp:sasl:2' task='UPGR-SCRAM-SHA-512'/>
 * S: <task-data xmlns='urn:xmpp:sasl:2'>
 *      <salt xmlns='urn:xmpp:sasl:upgrade:0' iterations='4096'>...</salt>
 *    </task-data>
 * C: <task-data xmlns='urn:xmpp:sasl:2'>
 *      <hash xmlns='urn:xmpp:sasl:upgrade:0'>...</hash>
 *    </task-data>
 * S: <success xmlns='urn:xmpp:sasl:2'>...</success>
 * }</pre>
 *
 * <strong>Note:</strong> the namespace and the shape of the payload elements above are reproduced from memory and
 * must be checked against the current revision of XEP-0480 before this is used against real clients. The task API
 * itself is deliberately unaware of them: everything protocol-specific lives in this class.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0480.html">XEP-0480: SASL Upgrade Tasks</a>
 */
public class ScramUpgradeTaskProvider implements Sasl2TaskProvider
{
    private static final Logger Log = LoggerFactory.getLogger(ScramUpgradeTaskProvider.class);

    /** Verify against the current revision of XEP-0480 before use. */
    public static final String NAMESPACE = "urn:xmpp:sasl:upgrade:0";

    private static final String TASK_PREFIX = "UPGR-";
    private static final String ATTRIBUTE_REQUESTED = "requested-mechanism";

    private static final int SALT_LENGTH = 16;
    private static final int ITERATION_COUNT = 4096;

    private final SecureRandom random = new SecureRandom();
    private final ScramCredentialStore store;

    /**
     * The storage that a deployment plugs in. Kept as an interface because how (and whether) SCRAM credentials can be
     * written depends entirely on the {@code AuthProvider} and {@code UserProvider} that a deployment uses: a server
     * backed by an external directory typically cannot write them at all.
     */
    public interface ScramCredentialStore
    {
        /**
         * The SCRAM mechanisms for which this store can persist credentials, in descending order of preference (for
         * example {@code SCRAM-SHA-512}, {@code SCRAM-SHA-256}).
         *
         * @return an ordered set of mechanism names (never null, possibly empty).
         */
        @Nonnull
        Set<String> getWritableMechanisms();

        /**
         * Reports whether credentials for the given mechanism are already held for the user, so that a peer is not
         * asked to perform an upgrade that has already happened.
         *
         * @param username the user (never null).
         * @param mechanismName the SCRAM mechanism (never null).
         * @return true if credentials are already stored.
         */
        boolean hasCredentials(@Nonnull String username, @Nonnull String mechanismName);

        /**
         * Persists the credentials that the peer derived.
         *
         * @param username the user (never null).
         * @param mechanismName the SCRAM mechanism (never null).
         * @param salt the salt that the server generated and the peer used (never null).
         * @param iterations the iteration count that the server specified and the peer used.
         * @param saltedPassword the value that the peer derived (never null).
         * @throws Exception if the credentials could not be stored.
         */
        void store(@Nonnull String username, @Nonnull String mechanismName, @Nonnull byte[] salt, int iterations, @Nonnull byte[] saltedPassword) throws Exception;
    }

    public ScramUpgradeTaskProvider(@Nonnull final ScramCredentialStore store)
    {
        this.store = store;
    }

    @Override
    @Nonnull
    public String getIdentifier()
    {
        return "org.example.scram-upgrade";
    }

    @Override
    @Nonnull
    public Set<String> getTaskNames()
    {
        return store.getWritableMechanisms().stream().map(m -> TASK_PREFIX + m).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public int getPriority()
    {
        // A credential upgrade is bookkeeping. Anything that gates access (a second factor, an acceptance) should be
        // presented to the user first.
        return -100;
    }

    @Override
    @Nonnull
    public List<Element> getStreamFeatureElements(@Nonnull final LocalSession session)
    {
        // Advertised to an unauthenticated peer, so this must not depend on the identity that the peer claims, and
        // must not perform a per-user lookup: doing so would tell an unauthenticated peer which accounts exist and
        // what credentials they hold. Whether this particular user still needs the upgrade is decided later, in
        // getOfferedTasks(...).
        if (!session.isEncrypted()) {
            // The peer is about to derive a credential from its password. Do not invite that over a connection that
            // an observer can read.
            return List.of();
        }
        final List<Element> result = new ArrayList<>();
        for (final String mechanismName : store.getWritableMechanisms()) {
            final Element upgrade = DocumentHelper.createElement(QName.get("upgrade", NAMESPACE));
            upgrade.setText(mechanismName);
            result.add(upgrade);
        }
        return result;
    }

    @Override
    public void onAuthenticateReceived(@Nonnull final Sasl2TaskContext context, @Nonnull final Element authenticate) throws SaslFailureException
    {
        final Element requested = authenticate.element(QName.get("upgrade", NAMESPACE));
        if (requested == null) {
            return;
        }
        final String mechanismName = requested.getTextTrim().toUpperCase(Locale.ROOT);

        // Only honour a request for something that was actually advertised to this session. Without this check, a
        // peer could drive the task on a connection on which it was never offered.
        final boolean advertised = context.getAdvertisedFeatureElements().stream()
            .anyMatch(element -> mechanismName.equalsIgnoreCase(element.getTextTrim()));
        if (!advertised) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "An upgrade to '" + mechanismName + "' was requested, which was not offered to this session.");
        }
        context.setAttribute(ATTRIBUTE_REQUESTED, mechanismName);
    }

    @Override
    @Nonnull
    public List<String> getOfferedTasks(@Nonnull final Sasl2TaskContext context)
    {
        final String mechanismName = context.getAttribute(ATTRIBUTE_REQUESTED, String.class).orElse(null);
        if (mechanismName == null) {
            // The peer did not opt in.
            return List.of();
        }
        final String username = context.getAuthorizationIdentity();
        if (username == null) {
            return List.of();
        }
        if (!isPasswordBased(context.getSaslMechanismName())) {
            // The peer authenticated with a token or a certificate, so it does not hold the password from which the
            // new credential would have to be derived.
            return List.of();
        }
        final String taskName = TASK_PREFIX + mechanismName;
        if (context.getCompletedTaskNames().contains(taskName)) {
            return List.of();
        }
        if (!store.getWritableMechanisms().contains(mechanismName)) {
            return List.of();
        }
        if (store.hasCredentials(username, mechanismName)) {
            return List.of();
        }
        return List.of(taskName);
    }

    @Override
    @Nonnull
    public Sasl2Task createTask(@Nonnull final String taskName, @Nonnull final Sasl2TaskContext context)
    {
        return new ScramUpgradeTask(taskName, taskName.substring(TASK_PREFIX.length()), context);
    }

    /**
     * Whether a mechanism proves that the peer knows the account password, and can therefore derive new credentials
     * from it.
     */
    private static boolean isPasswordBased(@Nonnull final String mechanismName)
    {
        return mechanismName.equals("PLAIN") || mechanismName.startsWith("SCRAM-") || mechanismName.equals("DIGEST-MD5");
    }

    private class ScramUpgradeTask implements Sasl2Task
    {
        private final String taskName;
        private final String mechanismName;
        private final Sasl2TaskContext context;
        private byte[] salt;

        private ScramUpgradeTask(@Nonnull final String taskName, @Nonnull final String mechanismName, @Nonnull final Sasl2TaskContext context)
        {
            this.taskName = taskName;
            this.mechanismName = mechanismName;
            this.context = context;
        }

        @Override
        @Nonnull
        public String getName()
        {
            return taskName;
        }

        @Override
        @Nonnull
        public Sasl2TaskResult begin(@Nonnull final Element next)
        {
            salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            final Element element = DocumentHelper.createElement(QName.get("salt", NAMESPACE));
            element.addAttribute("iterations", String.valueOf(ITERATION_COUNT));
            element.setText(Base64.getEncoder().encodeToString(salt));
            return Sasl2TaskResult.taskData(element);
        }

        @Override
        @Nonnull
        public Sasl2TaskResult onTaskData(@Nonnull final Element taskData) throws SaslFailureException
        {
            final Element hash = taskData.element(QName.get("hash", NAMESPACE));
            if (hash == null) {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST, "The upgrade task data does not contain a hash.");
            }
            final byte[] saltedPassword;
            try {
                saltedPassword = Base64.getDecoder().decode(hash.getTextTrim());
            } catch (final IllegalArgumentException e) {
                throw new SaslFailureException(Failure.INCORRECT_ENCODING, "The upgrade hash is not valid base64.");
            }

            final String username = context.getAuthorizationIdentity();
            if (username == null) {
                throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "There is no account to store credentials for.");
            }
            try {
                store.store(username, mechanismName, salt, ITERATION_COUNT, saltedPassword);
            } catch (final Exception e) {
                Log.warn("Unable to store upgraded '{}' credentials for user '{}'.", mechanismName, username, e);
                throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "Unable to store the upgraded credentials.");
            }
            Log.debug("Stored upgraded '{}' credentials for user '{}'.", mechanismName, username);
            return Sasl2TaskResult.completed();
        }

        @Override
        public void onAborted()
        {
            salt = null;
        }
    }
}
