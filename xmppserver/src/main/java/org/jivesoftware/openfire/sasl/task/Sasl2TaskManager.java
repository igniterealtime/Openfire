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
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link Sasl2TaskProvider} implementations, and driver of the SASL2 {@code <continue/>} flow that is
 * defined in XEP-0388 § 2.5.
 *
 * <h2>Registration</h2>
 * Third-party code registers a provider once, typically from a plugin:
 * <pre>{@code
 * public void initializePlugin(PluginManager manager, File pluginDirectory) {
 *     provider = new MyTaskProvider();
 *     Sasl2TaskManager.getInstance().register(provider);
 * }
 *
 * public void destroyPlugin() {
 *     Sasl2TaskManager.getInstance().unregister(provider);
 * }
 * }</pre>
 * Unregistering a provider does not disturb negotiations that are already in progress: they hold direct references to
 * the provider and to its tasks.
 *
 * <h2>Flow</h2>
 * <ol>
 *     <li>{@link #addStreamFeatures(LocalSession, Element)} lets providers advertise opt-in features.</li>
 *     <li>{@link #onAuthenticateElement(LocalSession, Element, String)} starts a negotiation and lets providers read
 *     the peer's {@code <authenticate/>} element.</li>
 *     <li>{@link #offerTasks(LocalSession, String, String, byte[])} is invoked once the SASL exchange has succeeded. If
 *     any task is eligible, it sends {@code <continue/>} and returns true, and the SASL2 negotiation is suspended.</li>
 *     <li>{@link #handleTaskElement(LocalSession, Element)} processes the peer's {@code <next/>} and
 *     {@code <task-data/>} elements until every eligible task has completed.</li>
 *     <li>{@link #endNegotiation(LocalSession, boolean)} tears the state down.</li>
 * </ol>
 *
 * Tasks are offered only to {@link LocalClientSession}s: the flow has no meaning for server-to-server or component
 * connections.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
 */
public class Sasl2TaskManager
{
    private static final Logger Log = LoggerFactory.getLogger(Sasl2TaskManager.class);

    /**
     * Controls whether SASL2 tasks are processed at all. When disabled, no task is advertised or offered, and a peer
     * that sends {@code <next/>} or {@code <task-data/>} has its negotiation failed. This is the kill switch for
     * deployments that run into trouble with a third-party provider.
     */
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.sasl2.tasks.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * Identifiers (see {@link Sasl2TaskProvider#getIdentifier()}) of providers that are registered but must not be
     * consulted. Lets an administrator disable one provider without unloading the plugin that supplies it.
     */
    public static final SystemProperty<List<String>> DISABLED_PROVIDERS = SystemProperty.Builder.ofType(List.class)
        .setKey("xmpp.auth.sasl2.tasks.disabled")
        .setDefaultValue(Collections.emptyList())
        .setDynamic(true)
        .buildList(String.class);

    /**
     * The maximum number of {@code <continue/>} elements that are sent during one negotiation. This bounds the effect
     * of a provider that keeps offering a task that never becomes ineligible, which would otherwise let a peer loop
     * indefinitely.
     */
    public static final SystemProperty<Integer> MAX_ROUNDS = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.auth.sasl2.tasks.max-rounds")
        .setDynamic(true)
        .setDefaultValue(5)
        .build();

    /**
     * Session data key under which the {@link Sasl2Negotiation} of the session's current SASL2 negotiation is stored.
     */
    public static final String NEGOTIATION_KEY = "Sasl2.task-negotiation";

    /**
     * Session data key under which the stream feature elements that each provider advertised are stored, as a Map of
     * provider identifier to a List of Elements.
     */
    public static final String ADVERTISED_KEY = "Sasl2.task-features-advertised";

    private static final Sasl2TaskManager INSTANCE = new Sasl2TaskManager();

    /**
     * The outcome of processing one protocol element in the task flow.
     */
    public enum Outcome
    {
        /**
         * Something has been sent to the peer. The SASL2 negotiation is still in progress and the caller must not
         * conclude it.
         */
        AWAITING_PEER,

        /**
         * Every eligible task has completed. The caller must now conclude the SASL2 negotiation, which delivers
         * {@code <success/>}.
         */
        NEGOTIATION_COMPLETE
    }

    /**
     * One task offered to a peer in a {@code <continue/>} element, and the provider that offered it.
     */
    record Offer(@Nonnull Sasl2TaskProvider provider, @Nonnull String taskName) {}

    private final Map<String, Sasl2TaskProvider> providers = new ConcurrentHashMap<>();

    /**
     * Ends a task negotiation whose connection is lost before the negotiation concludes, so that a task that
     * reserved something (a pending challenge, a one-time code) is given the chance to release it.
     *
     * A single shared instance is used, and a connection stores its close listeners keyed by listener instance, so
     * repeated registration for successive authentication attempts on the same connection is idempotent.
     */
    private final ConnectionCloseListener closeListener = handback -> {
        if (handback instanceof LocalSession closedSession && getNegotiation(closedSession) != null) {
            Log.debug("Connection of session '{}' closed while a SASL2 task negotiation was in progress. Ending it.", closedSession);
            endNegotiation(closedSession, false);
        }
        return CompletableFuture.completedFuture(null);
    };

    protected Sasl2TaskManager()
    {
    }

    @Nonnull
    public static Sasl2TaskManager getInstance()
    {
        return INSTANCE;
    }

    // -- Registration ----------------------------------------------------------------------------------------------

    /**
     * Registers a provider of SASL2 tasks.
     *
     * @param provider the provider to register (cannot be null).
     * @throws IllegalArgumentException if the provider is invalid, or if its identifier or one of its task names is
     *                                  already taken by another registered provider.
     */
    public synchronized void register(@Nonnull final Sasl2TaskProvider provider)
    {
        final String identifier = provider.getIdentifier();
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("A SASL2 task provider must have a non-empty identifier.");
        }
        final Set<String> taskNames = provider.getTaskNames();
        if (taskNames == null || taskNames.isEmpty()) {
            throw new IllegalArgumentException("SASL2 task provider '" + identifier + "' does not define any task name.");
        }
        for (final Map.Entry<String, Sasl2TaskProvider> entry : providers.entrySet()) {
            if (entry.getKey().equals(identifier)) {
                throw new IllegalArgumentException("A SASL2 task provider with identifier '" + identifier + "' is already registered.");
            }
            for (final String taskName : taskNames) {
                if (entry.getValue().getTaskNames().contains(taskName)) {
                    throw new IllegalArgumentException("SASL2 task name '" + taskName + "' is already provided by '" + entry.getKey() + "'.");
                }
            }
        }
        providers.put(identifier, provider);
        Log.info("Registered SASL2 task provider '{}', providing task(s): {}", identifier, taskNames);
    }

    /**
     * Removes a previously registered provider. Negotiations that are already in progress are unaffected.
     *
     * @param provider the provider to remove (cannot be null).
     * @return true if the provider was registered.
     */
    public synchronized boolean unregister(@Nonnull final Sasl2TaskProvider provider)
    {
        return unregister(provider.getIdentifier());
    }

    /**
     * Removes a previously registered provider. Negotiations that are already in progress are unaffected.
     *
     * @param identifier the identifier of the provider to remove (cannot be null).
     * @return true if a provider was registered under this identifier.
     */
    public synchronized boolean unregister(@Nonnull final String identifier)
    {
        final boolean removed = providers.remove(identifier) != null;
        if (removed) {
            Log.info("Removed SASL2 task provider '{}'.", identifier);
        }
        return removed;
    }

    /**
     * All registered providers, including those that are disabled by configuration.
     *
     * @return an immutable collection of providers (never null).
     */
    @Nonnull
    public Collection<Sasl2TaskProvider> getProviders()
    {
        return Collections.unmodifiableCollection(providers.values());
    }

    /**
     * The providers that are eligible to be consulted, in the order in which they are to be consulted.
     *
     * @return an ordered list of providers (never null, possibly empty).
     */
    @Nonnull
    protected List<Sasl2TaskProvider> getEnabledProviders()
    {
        if (!ENABLED.getValue()) {
            return Collections.emptyList();
        }
        final List<String> disabled = DISABLED_PROVIDERS.getValue();
        final List<Sasl2TaskProvider> result = new ArrayList<>();
        for (final Sasl2TaskProvider provider : providers.values()) {
            if (!disabled.contains(provider.getIdentifier())) {
                result.add(provider);
            }
        }
        result.sort(Comparator.comparingInt(Sasl2TaskProvider::getPriority).reversed().thenComparing(Sasl2TaskProvider::getIdentifier));
        return result;
    }

    // -- Stream features -------------------------------------------------------------------------------------------

    /**
     * Adds every registered provider's opt-in elements to the SASL2 {@code <authentication/>} stream feature, and
     * records on the session what was advertised.
     *
     * Invoke this while assembling the {@code <authentication xmlns='urn:xmpp:sasl:2'/>} feature, after the
     * {@code <mechanism/>} elements have been added.
     *
     * @param session the session that features are being advertised to (cannot be null).
     * @param authenticationFeature the {@code <authentication/>} element being assembled (cannot be null).
     */
    public void addStreamFeatures(@Nonnull final LocalSession session, @Nonnull final Element authenticationFeature)
    {
        if (!(session instanceof LocalClientSession)) {
            return;
        }
        final Map<String, List<Element>> advertised = new HashMap<>();
        for (final Sasl2TaskProvider provider : getEnabledProviders()) {
            try {
                final List<Element> elements = provider.getStreamFeatureElements(session);
                if (elements == null || elements.isEmpty()) {
                    continue;
                }
                final List<Element> copies = new ArrayList<>(elements.size());
                for (final Element element : elements) {
                    authenticationFeature.add(element.createCopy());
                    copies.add(element.createCopy());
                }
                advertised.put(provider.getIdentifier(), Collections.unmodifiableList(copies));
            } catch (final Exception e) {
                Log.warn("An exception occurred while obtaining SASL2 task stream features from provider '{}' for session '{}'. Its features are not advertised.", provider.getIdentifier(), session, e);
            }
        }
        session.setSessionData(ADVERTISED_KEY, Collections.unmodifiableMap(advertised));
    }

    /**
     * The stream feature elements that the identified provider advertised on the given session.
     *
     * @param session the session (cannot be null).
     * @param providerIdentifier the identifier of the provider (cannot be null).
     * @return detached copies of the advertised elements (never null, possibly empty).
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    static List<Element> getAdvertisedFeatureElements(@Nonnull final LocalSession session, @Nonnull final String providerIdentifier)
    {
        final Object data = session.getSessionData(ADVERTISED_KEY);
        if (!(data instanceof Map)) {
            return Collections.emptyList();
        }
        final List<Element> elements = ((Map<String, List<Element>>) data).get(providerIdentifier);
        if (elements == null) {
            return Collections.emptyList();
        }
        final List<Element> copies = new ArrayList<>(elements.size());
        elements.forEach(element -> copies.add(element.createCopy()));
        return Collections.unmodifiableList(copies);
    }

    // -- Negotiation lifecycle -------------------------------------------------------------------------------------

    /**
     * Starts a task negotiation for a SASL2 authentication attempt, and lets every provider inspect the peer's
     * {@code <authenticate/>} element.
     *
     * Any state left over from an earlier attempt on the same session is discarded first.
     *
     * @param session the session that is authenticating (cannot be null).
     * @param authenticate the peer's {@code <authenticate/>} element (cannot be null).
     * @param mechanismName the name of the SASL mechanism that the peer selected (cannot be null).
     * @throws SaslFailureException if a provider rejects the request.
     */
    public void onAuthenticateElement(@Nonnull final LocalSession session, @Nonnull final Element authenticate, @Nonnull final String mechanismName) throws SaslFailureException
    {
        reset(session);
        if (!(session instanceof LocalClientSession)) {
            return;
        }
        final List<Sasl2TaskProvider> enabled = getEnabledProviders();
        if (enabled.isEmpty()) {
            return;
        }

        final Sasl2Negotiation negotiation = new Sasl2Negotiation(session, mechanismName.toUpperCase(Locale.ROOT));
        session.setSessionData(NEGOTIATION_KEY, negotiation);

        try {
            final Connection connection = session.getConnection();
            if (connection != null) {
                connection.registerCloseListener(closeListener, session);
            }
        } catch (final Exception e) {
            // Not fatal: without the listener, a task that is abandoned mid-negotiation is not notified, but the negotiation itself still proceeds normally.
            Log.warn("Unable to register a connection close listener for the SASL2 task negotiation of session '{}'.", session, e);
        }

        for (final Sasl2TaskProvider provider : enabled) {
            negotiation.addParticipant(provider);
            try {
                provider.onAuthenticateReceived(negotiation.contextFor(provider), authenticate);
            } catch (final SaslFailureException e) {
                Log.debug("SASL2 task provider '{}' rejected the <authenticate/> element of session '{}'.", provider.getIdentifier(), session, e);
                throw e;
            } catch (final Exception e) {
                Log.warn("An exception occurred while SASL2 task provider '{}' inspected the <authenticate/> element of session '{}'. Continuing without it.", provider.getIdentifier(), session, e);
            }
        }
    }

    /**
     * Determines whether any task is to be performed before the SASL2 negotiation can be concluded, and if so, sends
     * a {@code <continue/>} element to the peer.
     *
     * Invoke this once the SASL exchange has succeeded and before the session is authenticated. When this returns
     * true, the caller must suspend the negotiation and wait for the peer's next element; when it returns false,
     * nothing has been sent and the caller proceeds as it would without tasks.
     *
     * @param session the session that is authenticating (cannot be null).
     * @param authorizationIdentity the authenticated username, or null for an anonymous authentication.
     * @param saslMechanismName the mechanism name as reported by the SaslServer (cannot be null).
     * @param saslSuccessData the success data produced by the SASL mechanism, or null. When a {@code <continue/>} is
     *                        sent, this data is delivered in that element (XEP-0388 § 2.5), which is why the caller
     *                        must not also place it in a {@code <success/>} element.
     * @return true if a {@code <continue/>} was sent, false if no task applies.
     * @throws SaslFailureException if a provider failed unexpectedly while determining eligible tasks, or if the
     *                               maximum number of {@code <continue/>} rounds ({@link #MAX_ROUNDS}) was exceeded.
     *                               The negotiation is torn down (as if by {@link #endNegotiation(LocalSession, boolean)}
     *                               with {@code successful=false}) before this exception propagates.
     */
    public boolean offerTasks(@Nonnull final LocalSession session, @Nullable final String authorizationIdentity, @Nonnull final String saslMechanismName, @Nullable final byte[] saslSuccessData) throws SaslFailureException
    {
        final Sasl2Negotiation negotiation = getNegotiation(session);
        if (negotiation == null) {
            return false;
        }
        final boolean offered;
        try {
            synchronized (negotiation) {
                final Object channelBindingType = session.getSessionData("ChannelBindingType");
                negotiation.setAuthenticationResult(authorizationIdentity, saslMechanismName, channelBindingType instanceof String s ? s : null);
                offered = startRound(session, negotiation, saslSuccessData);
            }
        } catch (final SaslFailureException e) {
            endNegotiation(session, false);
            throw e;
        } catch (final Exception e) {
            Log.warn("An unexpected exception occurred while determining SASL2 tasks for session '{}'. Failing the negotiation.", session, e);
            endNegotiation(session, false);
            throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "Unable to determine SASL2 tasks.");
        }
        if (!offered) {
            endNegotiation(session, true);
        }
        return offered;
    }

    /**
     * Processes a {@code <next/>} or {@code <task-data/>} element received from the peer.
     *
     * @param session the session that is authenticating (cannot be null).
     * @param element the received element (cannot be null).
     * @return whether the negotiation can now be concluded, or more is expected from the peer.
     * @throws SaslFailureException if the element is unexpected or malformed, or if a task failed.
     */
    @Nonnull
    public Outcome handleTaskElement(@Nonnull final LocalSession session, @Nonnull final Element element) throws SaslFailureException
    {
        final Sasl2Negotiation negotiation = getNegotiation(session);
        if (negotiation == null) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "No SASL2 task negotiation is in progress for this session.");
        }

        synchronized (negotiation) {
            if (!negotiation.isAuthenticated() || negotiation.getState() == Sasl2Negotiation.State.FINISHED) {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST, "No SASL2 task negotiation is in progress for this session.");
            }
            final Sasl2TaskResult result;
            final String name = element.getName();
            if ("next".equals(name)) {
                result = begin(session, negotiation, element);
            } else if ("task-data".equals(name)) {
                result = advance(negotiation, element);
            } else {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST, "Unexpected element in the SASL2 task flow: " + name);
            }
            return applyResult(session, negotiation, result);
        }
    }

    /**
     * Ends the negotiation, if one is in progress: aborts any task that is still running, notifies every provider
     * that participated, and removes all state from the session.
     *
     * @param session the session (cannot be null).
     * @param successful true if the SASL2 negotiation completed successfully.
     * @return the negotiation that was ended, or null if none was in progress.
     */
    @Nullable
    public Sasl2Negotiation endNegotiation(@Nonnull final LocalSession session, final boolean successful)
    {
        final Sasl2Negotiation negotiation = getNegotiation(session);
        if (negotiation == null) {
            return null;
        }

        synchronized (negotiation) {
            if (!negotiation.markFinished()) {
                // Another thread (typically a connection close racing with inbound processing) already tore this negotiation down.
                return null;
            }
            session.removeSessionData(NEGOTIATION_KEY);

            final Sasl2Task activeTask = negotiation.getActiveTask();
            if (activeTask != null) {
                try {
                    activeTask.onAborted();
                } catch (final Exception e) {
                    Log.warn("An exception occurred while aborting SASL2 task '{}' of session '{}'.", activeTask.getName(), session, e);
                }
            }
            for (final Sasl2TaskProvider provider : negotiation.getParticipants()) {
                try {
                    provider.onNegotiationEnded(negotiation.contextFor(provider), successful);
                } catch (final Exception e) {
                    Log.warn("An exception occurred while notifying SASL2 task provider '{}' of the end of the negotiation of session '{}'.", provider.getIdentifier(), session, e);
                }
            }
        }
        if (successful) {
            // The advertised elements are only consulted during a negotiation. They are deliberately retained when
            // the negotiation failed: the peer can retry on the same stream, and stream features are not
            // re-advertised between attempts.
            session.removeSessionData(ADVERTISED_KEY);
        }
        return negotiation;
    }

    /**
     * Discards any task negotiation state on the session, without notifying providers of a completed negotiation.
     * Invoked when a new authentication attempt starts.
     *
     * @param session the session (cannot be null).
     */
    public void reset(@Nonnull final LocalSession session)
    {
        endNegotiation(session, false);
    }

    /**
     * The negotiation that is in progress for the given session, if any.
     *
     * @param session the session (cannot be null).
     * @return a negotiation, or null.
     */
    @Nullable
    public Sasl2Negotiation getNegotiation(@Nonnull final LocalSession session)
    {
        final Object data = session.getSessionData(NEGOTIATION_KEY);
        if (data != null && !(data instanceof Sasl2Negotiation)) {
            Log.warn("Unexpected object found in session data under key '{}' of session '{}': {}", NEGOTIATION_KEY, session, data);
            return null;
        }
        return (Sasl2Negotiation) data;
    }

    // -- Internals -------------------------------------------------------------------------------------------------

    /**
     * Collects the tasks that are eligible now, and sends a {@code <continue/>} element if there are any.
     *
     * @return true if a {@code <continue/>} was sent.
     * @throws SaslFailureException if the maximum number of {@code <continue/>} rounds ({@link #MAX_ROUNDS}) was exceeded.
     */
    private boolean startRound(@Nonnull final LocalSession session, @Nonnull final Sasl2Negotiation negotiation, @Nullable final byte[] additionalData) throws SaslFailureException
    {
        final int maxRounds = Math.max(1, MAX_ROUNDS.getValue());
        if (negotiation.getRound() >= maxRounds) {
            throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "SASL2 task negotiation of session '" + session + "' exceeded the maximum of " + maxRounds + " rounds.");
        }
        negotiation.nextRound();

        final List<Offer> offers = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        final Set<String> claimed = new LinkedHashSet<>();
        for (final Sasl2TaskProvider provider : getEnabledProviders()) {
            if (!negotiation.getParticipants().contains(provider)) {
                // Registered after this negotiation started. It never saw the <authenticate/> element, so it is not consulted for this negotiation.
                continue;
            }
            final Sasl2TaskContext context = negotiation.contextFor(provider);
            final List<String> proposed;
            try {
                proposed = provider.getOfferedTasks(context);
            } catch (final Exception e) {
                Log.warn("An exception occurred while SASL2 task provider '{}' determined eligible tasks for session '{}'. Skipping it.", provider.getIdentifier(), session, e);
                continue;
            }
            if (proposed == null || proposed.isEmpty()) {
                continue;
            }
            boolean contributed = false;
            for (final String taskName : proposed) {
                if (taskName == null || taskName.isEmpty()) {
                    continue;
                }
                if (!provider.getTaskNames().contains(taskName)) {
                    Log.warn("SASL2 task provider '{}' offered task '{}', which it did not declare in getTaskNames(). Ignoring it.", provider.getIdentifier(), taskName);
                    continue;
                }
                if (negotiation.getCompletedTaskNames().contains(taskName)) {
                    Log.warn("SASL2 task provider '{}' offered task '{}', which already completed during this negotiation of session '{}'. Ignoring it.", provider.getIdentifier(), taskName, session);
                    continue;
                }
                if (!claimed.add(taskName)) {
                    Log.warn("SASL2 task '{}' was offered more than once for session '{}'. Ignoring the duplicate.", taskName, session);
                    continue;
                }
                offers.add(new Offer(provider, taskName));
                contributed = true;
            }
            if (contributed) {
                try {
                    provider.getContinueText(context).filter(text -> !text.isBlank()).ifPresent(texts::add);
                } catch (final Exception e) {
                    Log.warn("An exception occurred while obtaining <continue/> text from SASL2 task provider '{}'.", provider.getIdentifier(), e);
                }
            }
        }

        if (offers.isEmpty()) {
            return false;
        }

        negotiation.beginRound(offers);
        Log.debug("Offering SASL2 task(s) {} to session '{}' (round {}).", claimed, session, negotiation.getRound());
        session.deliverRawText(buildContinueElement(additionalData, offers, texts).asXML());
        return true;
    }

    /**
     * Handles a {@code <next/>} element: validates the selection and starts the task.
     */
    @Nonnull
    private Sasl2TaskResult begin(@Nonnull final LocalSession session, @Nonnull final Sasl2Negotiation negotiation, @Nonnull final Element next) throws SaslFailureException
    {
        if (negotiation.getState() != Sasl2Negotiation.State.AWAITING_NEXT) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "A <next/> element was received while the server was not awaiting a task selection.");
        }
        final String taskName = next.attributeValue("task");
        if (taskName == null || taskName.isEmpty()) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "The <next/> element does not identify a task.");
        }
        // Enforce that the peer selects from what was offered, mirroring the check that Openfire performs on SASL
        // mechanism names (OF-3273): a peer must not be able to drive a task that was never offered to it.
        final Offer offer = negotiation.findOffer(taskName);
        if (offer == null) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "Task '" + taskName + "' was not offered to this session.");
        }

        final Sasl2Task task = offer.provider().createTask(taskName, negotiation.contextFor(offer.provider()));
        if (task == null) {
            throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "Provider '" + offer.provider().getIdentifier() + "' did not create task '" + taskName + "'.");
        }
        negotiation.setActiveTask(task);
        Log.debug("Session '{}' selected SASL2 task '{}'.", session, taskName);
        return task.begin(next);
    }

    /**
     * Handles a {@code <task-data/>} element: passes it to the task that is in progress.
     */
    @Nonnull
    private Sasl2TaskResult advance(@Nonnull final Sasl2Negotiation negotiation, @Nonnull final Element taskData) throws SaslFailureException
    {
        if (negotiation.getState() != Sasl2Negotiation.State.AWAITING_TASK_DATA) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "A <task-data/> element was received while the server was not awaiting one.");
        }
        final Sasl2Task task = negotiation.getActiveTask();
        if (task == null) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST, "A <task-data/> element was received while no task is in progress.");
        }
        return task.onTaskData(taskData);
    }

    /**
     * Acts on the result of a task step: writes to the peer, moves to the next round, or reports that the
     * negotiation can be concluded.
     */
    @Nonnull
    private Outcome applyResult(@Nonnull final LocalSession session, @Nonnull final Sasl2Negotiation negotiation, @Nullable final Sasl2TaskResult result) throws SaslFailureException
    {
        if (result == null) {
            throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "A SASL2 task did not produce a result.");
        }
        switch (result.getType()) {
            case TASK_DATA -> {
                negotiation.setState(Sasl2Negotiation.State.AWAITING_TASK_DATA);
                session.deliverRawText(buildTaskDataElement(result.getTaskData()).asXML());
                return Outcome.AWAITING_PEER;
            }
            case COMPLETED -> {
                final byte[] additionalData = result.getAdditionalData();
                negotiation.completeActiveTask();
                if (startRound(session, negotiation, additionalData)) {
                    return Outcome.AWAITING_PEER;
                }
                negotiation.setFinalAdditionalData(additionalData);
                return Outcome.NEGOTIATION_COMPLETE;
            }
            default -> throw new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "Unrecognized SASL2 task result: " + result.getType());
        }
    }

    // -- Element construction --------------------------------------------------------------------------------------
    // These belong in SaslOutcome, alongside the other SASL2 response elements, and are kept here only to limit the
    // size of the change to existing classes.

    /**
     * Builds the {@code <continue/>} element (XEP-0388 § 2.5) that offers the given tasks to the peer, optionally
     * carrying the SASL mechanism's (or the previous task's) {@code <additional-data/>} and any human-readable
     * {@code <text/>} contributed by the offering providers.
     *
     * @param additionalData mechanism or task success data to embed as base64-encoded {@code <additional-data/>}, or null/empty to omit the element.
     * @param offers the tasks offered in this round (cannot be null, must not be empty).
     * @param texts human-readable text contributed by the offering providers; joined with a single space when there is more than one (cannot be null; empty omits the {@code <text/>} element).
     * @return the assembled {@code <continue/>} element (never null).
     */
    @Nonnull
    static Element buildContinueElement(@Nullable final byte[] additionalData, @Nonnull final List<Offer> offers, @Nonnull final List<String> texts)
    {
        final Element result = DocumentHelper.createElement(QName.get("continue", SASLAuthentication.SASL2_NAMESPACE));
        if (additionalData != null && additionalData.length > 0) {
            result.addElement("additional-data").setText(Base64.getEncoder().encodeToString(additionalData));
        }
        final Element tasks = result.addElement("tasks");
        offers.forEach(offer -> tasks.addElement("task").setText(offer.taskName()));
        if (!texts.isEmpty()) {
            result.addElement("text").setText(String.join(" ", texts));
        }
        return result;
    }

    /**
     * Builds the {@code <task-data/>} element (XEP-0388 § 2.5) that carries one round trip's worth of task-defined
     * payload to the peer.
     *
     * @param children the task-defined child elements to embed, each carrying its own namespace (cannot be null, may be empty, which sends an empty {@code <task-data/>} element).
     * @return the assembled {@code <task-data/>} element (never null).
     */
    @Nonnull
    static Element buildTaskDataElement(@Nonnull final List<Element> children)
    {
        final Element result = DocumentHelper.createElement(QName.get("task-data", SASLAuthentication.SASL2_NAMESPACE));
        children.forEach(child -> result.add(child.createCopy()));
        return result;
    }
}
