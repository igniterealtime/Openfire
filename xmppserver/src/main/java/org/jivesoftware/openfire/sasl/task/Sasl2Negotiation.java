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

import org.dom4j.Element;
import org.jivesoftware.openfire.net.UserAgentInfo;
import org.jivesoftware.openfire.session.LocalSession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * The state of one SASL2 task negotiation on one session.
 *
 * An instance is created when an {@code <authenticate/>} element is processed for a client session, is stored in the
 * session's data under {@link Sasl2TaskManager#NEGOTIATION_KEY}, and is discarded when the negotiation ends. It also
 * carries the deferred outcome of the SASL exchange itself: while tasks are running, the authorization identity and
 * the mechanism name live here rather than being applied to the session, because the session must not become
 * authenticated until every task has completed.
 *
 * Providers do not see this type. They are handed {@link Sasl2TaskContext} views of it, one per provider, which scope
 * attribute storage per provider.
 *
 * All mutation is performed by {@link Sasl2TaskManager} while holding this instance's monitor.
 */
public class Sasl2Negotiation
{
    /**
     * The point that a negotiation has reached. Used to reject protocol elements that arrive out of order.
     */
    public enum State
    {
        /**
         * The {@code <authenticate/>} element has been seen; the SASL exchange is still in progress.
         */
        PRE_AUTHENTICATION,

        /**
         * The SASL exchange has succeeded. No task has been offered (yet).
         */
        AUTHENTICATED,

        /**
         * A {@code <continue/>} element has been sent; the peer is expected to select a task with {@code <next/>}.
         */
        AWAITING_NEXT,

        /**
         * A {@code <task-data/>} element has been sent; the peer is expected to respond in kind.
         */
        AWAITING_TASK_DATA,

        /**
         * The negotiation has ended and has been torn down. Written only by
         * {@link Sasl2TaskManager#endNegotiation(LocalSession, boolean)}, whose compare-and-swap on this value makes
         * teardown idempotent when a connection close races with inbound processing. No other code may set it.
         */
        FINISHED
    }

    private final LocalSession session;
    private String saslMechanismName;

    /**
     * Attribute storage, keyed by provider identifier and then by attribute name.
     */
    private final Map<String, Map<String, Object>> providerAttributes = new HashMap<>();

    /**
     * Names of tasks that completed successfully, in completion order.
     */
    private final List<String> completedTaskNames = new ArrayList<>();

    /**
     * The tasks offered in the current round.
     */
    private final List<Sasl2TaskManager.Offer> currentOffer = new ArrayList<>();

    /**
     * Providers that participated in this negotiation, so that all can be notified when it ends.
     */
    private final Set<Sasl2TaskProvider> participants = new LinkedHashSet<>();

    private volatile State state = State.PRE_AUTHENTICATION;
    private boolean authenticated;
    private String authorizationIdentity;
    private String channelBindingType;
    private byte[] finalAdditionalData;
    private Sasl2Task activeTask;
    private int round;

    Sasl2Negotiation(@Nonnull final LocalSession session, @Nonnull final String saslMechanismName)
    {
        this.session = session;
        this.saslMechanismName = saslMechanismName;
    }

    // -- Accessors used by SASLAuthentication to conclude the negotiation ------------------------------------------

    /**
     * The authorization identity that the SASL exchange yielded, or null for an anonymous authentication.
     *
     * @return the authenticated username, or null.
     */
    @Nullable
    public String getAuthorizationIdentity()
    {
        return authorizationIdentity;
    }

    /**
     * The name of the SASL mechanism that was used.
     *
     * @return a mechanism name (never null).
     */
    @Nonnull
    public String getSaslMechanismName()
    {
        return saslMechanismName;
    }

    /**
     * The data to render as {@code <additional-data/>} in the final {@code <success/>} element.
     *
     * This is <em>not</em> the SASL mechanism's own success data: that was delivered to the peer in the first
     * {@code <continue/>} element, and must not be repeated. It is the data, if any, that the last task contributed.
     *
     * @return additional data for the {@code <success/>} element, or null.
     */
    @Nullable
    public byte[] getFinalAdditionalData()
    {
        return finalAdditionalData == null ? null : finalAdditionalData.clone();
    }

    // -- Package-private mutation, performed by Sasl2TaskManager ---------------------------------------------------

    /**
     * The point that the negotiation has reached in its state machine.
     *
     * @return the current state (never null).
     */
    @Nonnull
    State getState()
    {
        return state;
    }

    /**
     * Records the outcome of the SASL exchange.
     *
     * @param authorizationIdentity the authenticated username, or null for an anonymous authentication.
     * @param saslMechanismName the mechanism name as reported by the SaslServer, which supersedes the provisional
     *                          value taken from the peer's {@code <authenticate/>} element. Ignored when null.
     * @param channelBindingType the negotiated channel binding type, or null.
     */
    void setAuthenticationResult(@Nullable final String authorizationIdentity, @Nullable final String saslMechanismName, @Nullable final String channelBindingType)
    {
        this.authorizationIdentity = authorizationIdentity;
        if (saslMechanismName != null) {
            this.saslMechanismName = saslMechanismName.toUpperCase(Locale.ROOT);
        }
        this.channelBindingType = channelBindingType;
        this.authenticated = true;
        this.state = State.AUTHENTICATED;
    }

    /**
     * Sets the current state of the negotiation.
     *
     * @param state the new state. Cannot be {@link State#FINISHED}; use {@link #markFinished()} instead.
     */
    void setState(@Nonnull final State state)
    {
        if (state == State.FINISHED) {
            throw new IllegalArgumentException("Use markFinished() to end a negotiation, so that teardown runs exactly once.");
        }
        this.state = state;
    }

    /**
     * Marks this negotiation as ended, returning whether this call was the one that ended it.
     *
     * Teardown must run exactly once even when a connection close races with inbound processing, so the caller
     * performs it only when this returns true.
     *
     * @return true if this call transitioned the negotiation to {@link State#FINISHED}.
     */
    boolean markFinished()
    {
        if (state == State.FINISHED) {
            return false;
        }
        state = State.FINISHED;
        return true;
    }

    /**
     * Records the {@code <additional-data/>} contributed by the last task to complete, to be rendered
     * in the final {@code <success/>} element once the negotiation concludes.
     *
     * @param finalAdditionalData the data, or null if the last task contributed none.
     */
    void setFinalAdditionalData(@Nullable final byte[] finalAdditionalData)
    {
        this.finalAdditionalData = finalAdditionalData == null ? null : finalAdditionalData.clone();
    }

    /**
     * Whether the SASL exchange itself has completed successfully. Mirrors {@link Sasl2TaskContext#isAuthenticated()}.
     *
     * @return true once {@link #setAuthenticationResult(String, String, String)} has been called, otherwise false.
     */
    boolean isAuthenticated()
    {
        return authenticated;
    }

    /**
     * Marks the given provider as having participated in this negotiation
     *
     * @param provider the provider that participated (cannot be null).
     */
    synchronized void addParticipant(@Nonnull final Sasl2TaskProvider provider)
    {
        participants.add(provider);
    }

    /**
     * The providers that have participated in this negotiation so far.
     *
     * Returns an immutable snapshot, decoupled from later calls to {@link #addParticipant(Sasl2TaskProvider)}:
     * a provider added after this method returns will not appear in the set already returned. Safe to iterate
     * without holding this negotiation's monitor.
     *
     * @return an immutable snapshot of the participating providers (never null, possibly empty).
     */
    @Nonnull
    synchronized Set<Sasl2TaskProvider> getParticipants()
    {
        return Set.copyOf(participants);
    }

    /**
     * Starts a new round of task offering: records the tasks being offered this round, discards any task left
     * over from a previous round (a task is expected to have already been completed or aborted by the time a new
     * round begins), and moves the negotiation to {@link State#AWAITING_NEXT}.
     *
     * @param offers the tasks offered this round, one entry per eligible provider/task-name pair (cannot be null;
     *               the caller is expected not to invoke this with an empty list).
     */
    void beginRound(@Nonnull final List<Sasl2TaskManager.Offer> offers)
    {
        currentOffer.clear();
        currentOffer.addAll(offers);
        activeTask = null;
        state = State.AWAITING_NEXT;
    }

    /**
     * Advances the round counter by one.
     *
     * Expected to be invoked once per {@code <continue/>} element sent, so that the
     * {@code xmpp.auth.sasl2.tasks.max-rounds} limit can be enforced.
     */
    void nextRound()
    {
        round++;
    }

    /**
     * The number of {@code <continue/>} elements sent so far during this negotiation, including the one currently
     * being assembled.
     *
     * @return the current round number; 0 before the first round begins.
     */
    int getRound()
    {
        return round;
    }

    /**
     * Looks up the task offered in the current round under the given name.
     *
     * Intended to be used to validate a peer's {@code <next task='...'/>} selection against what was actually offered
     * (an OF-3273 style enforcement), so that a peer cannot drive a task it was never presented with.
     *
     * @param taskName the task name to look up (cannot be null).
     * @return the matching offer, or null if no task by this name was offered in the current round.
     */
    @Nullable
    Sasl2TaskManager.Offer findOffer(@Nonnull final String taskName)
    {
        return currentOffer.stream().filter(o -> o.taskName().equals(taskName)).findFirst().orElse(null);
    }

    /**
     * Records the task instance that is now executing.
     *
     * Expected to be invoked after the peer selected the task with a {@code <next/>} element.
     *
     * @param task the task now in progress (cannot be null).
     */
    void setActiveTask(@Nonnull final Sasl2Task task)
    {
        this.activeTask = task;
    }

    /**
     * The task instance that is currently executing, if any.
     *
     * This can return null if no task is in progress (e.g. between rounds, before any task has been selected, or
     * after the active task has completed or been aborted).
     *
     * @return the active task, or null if none is in progress.
     */
    @Nullable
    Sasl2Task getActiveTask()
    {
        return activeTask;
    }

    /**
     * Marks the active task as having completed successfully: appends its name to the completed-task list (in
     * completion order) and clears it as the active task.
     *
     * @throws IllegalStateException if no task is currently active, which indicates a bug in {@link Sasl2TaskManager} rather than a peer-triggerable condition.
     */
    void completeActiveTask()
    {
        if (activeTask == null) {
            throw new IllegalStateException("No SASL2 task is active. This suggests a bug in Openfire.");
        }
        completedTaskNames.add(activeTask.getName());
        activeTask = null;
    }

    /**
     * The names of the tasks that have completed successfully during this negotiation, in the order they
     * completed. Mirrors {@link Sasl2TaskContext#getCompletedTaskNames()}.
     *
     * @return an immutable list of completed task names (never null, possibly empty).
     */
    @Nonnull
    List<String> getCompletedTaskNames()
    {
        return Collections.unmodifiableList(completedTaskNames);
    }

    /**
     * Creates the view on this negotiation that is handed to the given provider and to the tasks it creates.
     *
     * @param provider the provider to create a view for (cannot be null).
     * @return a context (never null).
     */
    @Nonnull
    Sasl2TaskContext contextFor(@Nonnull final Sasl2TaskProvider provider)
    {
        return new ProviderView(provider);
    }

    @Override
    public String toString()
    {
        return "Sasl2Negotiation{session=" + session + ", state=" + state + ", round=" + round + ", completed=" + completedTaskNames + '}';
    }

    /**
     * A per-provider view on the enclosing negotiation. Scopes attribute storage and advertised stream features to
     * the provider that the view was created for, so that providers cannot collide with, or observe, each other.
     */
    private class ProviderView implements Sasl2TaskContext
    {
        private final Sasl2TaskProvider provider;

        private ProviderView(@Nonnull final Sasl2TaskProvider provider)
        {
            this.provider = provider;
        }

        @Override
        @Nonnull
        public LocalSession getSession()
        {
            return session;
        }

        @Override
        public boolean isAuthenticated()
        {
            return Sasl2Negotiation.this.isAuthenticated();
        }

        @Override
        @Nullable
        public String getAuthorizationIdentity()
        {
            return authorizationIdentity;
        }

        @Override
        @Nonnull
        public String getSaslMechanismName()
        {
            return saslMechanismName;
        }

        @Override
        @Nullable
        public String getChannelBindingType()
        {
            return channelBindingType;
        }

        @Override
        @Nullable
        public UserAgentInfo getUserAgentInfo()
        {
            final Object data = session.getSessionData("user-agent-info");
            return data instanceof UserAgentInfo info ? info : null;
        }

        @Override
        @Nonnull
        public List<String> getCompletedTaskNames()
        {
            return Sasl2Negotiation.this.getCompletedTaskNames();
        }

        @Override
        @Nonnull
        public Set<String> getOfferedTaskNames()
        {
            final Set<String> result = new LinkedHashSet<>();
            currentOffer.forEach(offer -> result.add(offer.taskName()));
            return Collections.unmodifiableSet(result);
        }

        @Override
        public int getRound()
        {
            return round;
        }

        @Override
        @Nonnull
        public List<Element> getAdvertisedFeatureElements()
        {
            return Sasl2TaskManager.getAdvertisedFeatureElements(session, provider.getIdentifier());
        }

        @Override
        @Nullable
        public Object getAttribute(@Nonnull final String name)
        {
            synchronized (Sasl2Negotiation.this) {
                final Map<String, Object> attributes = providerAttributes.get(provider.getIdentifier());
                return attributes == null ? null : attributes.get(name);
            }
        }

        @Override
        @Nonnull
        public <T> Optional<T> getAttribute(@Nonnull final String name, @Nonnull final Class<T> type)
        {
            final Object value = getAttribute(name);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        @Override
        public void setAttribute(@Nonnull final String name, @Nullable final Object value)
        {
            synchronized (Sasl2Negotiation.this) {
                if (value == null) {
                    final Map<String, Object> attributes = providerAttributes.get(provider.getIdentifier());
                    if (attributes != null) {
                        attributes.remove(name);
                    }
                } else {
                    providerAttributes.computeIfAbsent(provider.getIdentifier(), k -> new HashMap<>()).put(name, value);
                }
            }
        }
    }
}
