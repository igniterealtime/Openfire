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
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.session.LocalSession;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies SASL2 tasks (XEP-0388 § 2.5) and decides when they apply.
 *
 * This is the extension point for third-party code. An implementation is registered with
 * {@link Sasl2TaskManager#register(Sasl2TaskProvider)}, typically from a plugin's
 * {@code initializePlugin(...)}, and unregistered again from {@code destroyPlugin()}.
 *
 * <h2>Lifecycle</h2>
 * For every SASL2 negotiation on a client session, the manager invokes the methods of every registered provider in
 * this order:
 * <ol>
 *     <li>{@link #getStreamFeatureElements(LocalSession)}, when stream features are advertised. This happens before
 *     the negotiation starts, and is repeated whenever features are re-advertised (for example after a stream
 *     restart). It is the hook that lets a peer discover, and opt in to, an optional task. This is the mechanism that
 *     XEP-0480 uses.</li>
 *     <li>{@link #onAuthenticateReceived(Sasl2TaskContext, Element)}, when the peer's {@code <authenticate/>} element
 *     arrives. Authentication has not happened yet. This is where an opt-in that the peer expressed in that element
 *     is recorded, as a context attribute, for later use.</li>
 *     <li>{@link #getOfferedTasks(Sasl2TaskContext)}, once the SASL exchange has succeeded, and again after each task
 *     completes. This is the eligibility check: it decides whether this user, on this session, at this moment, is to
 *     be presented with a task.</li>
 *     <li>{@link #createTask(String, Sasl2TaskContext)}, when the peer selects one of the offered tasks.</li>
 *     <li>{@link #onNegotiationEnded(Sasl2TaskContext, boolean)}, when the negotiation ends, successfully or not.</li>
 * </ol>
 *
 * <h2>Offering semantics</h2>
 * The tasks named in one {@code <continue/>} element are <em>alternatives</em>: the peer picks exactly one of them,
 * or aborts. To require several tasks in sequence, return the next one from {@link #getOfferedTasks(Sasl2TaskContext)}
 * after the previous one has completed; the manager will send a further {@code <continue/>}. To offer a choice
 * between second factors, return them all at once, and return none of them once
 * {@link Sasl2TaskContext#getCompletedTaskNames()} shows that one has been performed.
 *
 * There is no way for a peer to decline a task: a peer faced with a {@code <continue/>} it does not understand can
 * only abort, which ends the negotiation unauthenticated. Offering a task unconditionally therefore locks out every
 * client that has not implemented it. Two patterns avoid that:
 * <ul>
 *     <li><strong>Opt-in</strong> (recommended for optional tasks): advertise the task in stream features, and offer
 *     it only to peers that asked for it in their {@code <authenticate/>} element.</li>
 *     <li><strong>Client-conditional</strong>: use {@link Sasl2TaskContext#getUserAgentInfo()} to restrict the offer
 *     to software known to support the task.</li>
 * </ul>
 * A task that is genuinely mandatory (a second factor on an account that requires one, a forced acceptance of terms)
 * may of course be offered unconditionally to the users it applies to.
 *
 * <h2>Cost</h2>
 * {@link #getStreamFeatureElements(LocalSession)} runs for every session that reaches stream feature advertisement,
 * including unauthenticated ones. Anything it does is reachable by an unauthenticated peer, and anything it varies on
 * is observable by one. Tailoring the advertisement to the user named in the stream's {@code from} attribute leaks
 * whether that user exists, which is the same trade-off that Openfire's
 * {@code xmpp.auth.scram.mechanisms-per-user} property governs for SASL mechanisms. Keep the method cheap and,
 * preferably, independent of the claimed identity.
 *
 * <h2>Threading</h2>
 * Implementations must be thread-safe: a single provider instance serves all sessions concurrently. The per-session
 * state belongs in the {@link Sasl2TaskContext} and in the {@link Sasl2Task} instances that
 * {@link #createTask(String, Sasl2TaskContext)} returns.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
 * @see <a href="https://xmpp.org/extensions/xep-0480.html">XEP-0480: SASL Upgrade Tasks</a>
 */
public interface Sasl2TaskProvider
{
    /**
     * A stable identifier for this provider, unique among all registered providers.
     *
     * It is used in logging, in the {@code xmpp.auth.sasl2.tasks.disabled} system property, and to scope the
     * attributes and advertised features that the provider stores on a session. A reverse-DNS-style or plugin-style
     * name is recommended, for example {@code org.example.totp}.
     *
     * @return an identifier (never null or empty).
     */
    @Nonnull
    String getIdentifier();

    /**
     * Every task name that this provider can ever offer.
     *
     * The manager uses this to detect collisions between providers at registration time, and to route a peer's
     * {@code <next/>} element to the right provider. A name returned from
     * {@link #getOfferedTasks(Sasl2TaskContext)} that is not in this set is ignored.
     *
     * Task names are case-sensitive and are exchanged verbatim on the wire. Follow the conventions of whichever
     * specification defines the task, and use a distinctive prefix for task names of your own devising.
     *
     * @return an immutable set of task names (never null or empty).
     */
    @Nonnull
    Set<String> getTaskNames();

    /**
     * Contributes elements to the SASL2 {@code <authentication/>} stream feature, so that a peer can discover the
     * availability of a task and opt in to it.
     *
     * The returned elements are copied into the {@code <authentication/>} element as-is, and are recorded on the
     * session so that they can later be retrieved through {@link Sasl2TaskContext#getAdvertisedFeatureElements()}.
     * Each element must carry its own namespace.
     *
     * Returning an empty list (the default) advertises nothing, which is appropriate for a task that the server
     * imposes rather than one the peer can request.
     *
     * @param session the session that features are being advertised to (never null). Not authenticated.
     * @return elements to add to the {@code <authentication/>} feature (never null, possibly empty).
     */
    @Nonnull
    default List<Element> getStreamFeatureElements(@Nonnull final LocalSession session)
    {
        return Collections.emptyList();
    }

    /**
     * Inspects the peer's {@code <authenticate/>} element, before authentication is attempted.
     *
     * This is where a request that the peer inlined into that element - an opt-in to an optional task, a parameter
     * for a task - is parsed and recorded on the context with
     * {@link Sasl2TaskContext#setAttribute(String, Object)}, for {@link #getOfferedTasks(Sasl2TaskContext)} to act
     * on later.
     *
     * Nothing about the peer has been verified at this point. An implementation should validate the request against
     * what was actually advertised (see {@link Sasl2TaskContext#getAdvertisedFeatureElements()}) rather than trust
     * it, and should not perform expensive work on its basis.
     *
     * @param context the negotiation context (never null). {@link Sasl2TaskContext#isAuthenticated()} is false here.
     * @param authenticate the peer's {@code <authenticate/>} element (never null).
     * @throws SaslFailureException to reject the authentication attempt outright, for a request that is malformed.
     */
    default void onAuthenticateReceived(@Nonnull final Sasl2TaskContext context, @Nonnull final Element authenticate) throws SaslFailureException
    {
    }

    /**
     * Decides which tasks, if any, are to be offered to this session at this point in the negotiation.
     *
     * This is the eligibility check. Typical conditions are whether the account has the relevant feature enabled,
     * whether the peer opted in, whether the interval since the task was last performed has elapsed, whether the
     * client is known to support it, and whether the SASL mechanism that was used is compatible with it.
     *
     * Invoked once per round, on the thread that processes the peer's connection, with authentication complete. It
     * must return quickly.
     *
     * Names that are not in {@link #getTaskNames()}, that another provider already offered in this round, or that
     * have already completed during this negotiation, are discarded with a warning. The order of the returned list
     * is preserved in the {@code <continue/>} element, which conveys the server's preference.
     *
     * Any provider implementing a mandatory task must fail closed within its own eligibility check, because the
     * manager's own error handling fails open by design (to isolate providers from each other).
     *
     * @param context the negotiation context (never null).
     * @return task names to offer (never null, possibly empty).
     */
    @Nonnull
    List<String> getOfferedTasks(@Nonnull Sasl2TaskContext context);

    /**
     * Creates the task that the peer selected.
     *
     * Only invoked for a name that this provider offered in the current round, so an implementation need not
     * re-verify eligibility.
     *
     * @param taskName the name of the selected task (never null).
     * @param context the negotiation context (never null).
     * @return a new task instance (never null).
     * @throws SaslFailureException to abort the negotiation, if the task cannot be created.
     */
    @Nonnull
    Sasl2Task createTask(@Nonnull String taskName, @Nonnull Sasl2TaskContext context) throws SaslFailureException;

    /**
     * Human-readable text to include in the {@code <continue/>} element, explaining to the user why the negotiation
     * did not complete.
     *
     * Used only when this provider contributed at least one task to the current round. When several providers supply
     * text, the texts are joined.
     *
     * @param context the negotiation context (never null).
     * @return text for the peer's user, or an empty optional (the default).
     */
    @Nonnull
    default Optional<String> getContinueText(@Nonnull final Sasl2TaskContext context)
    {
        return Optional.empty();
    }

    /**
     * Notifies the provider that the negotiation has ended.
     *
     * Invoked exactly once per negotiation that this provider saw an {@code <authenticate/>} element for, whether or
     * not it offered any task. Any {@link Sasl2Task} that was still in progress has already had
     * {@link Sasl2Task#onAborted()} invoked on it. The context must not be used after this call returns, and the
     * session must not be written to.
     *
     * Note that {@code successful} reports the outcome of the <em>task flow</em>, not of the SASL2 negotiation as a
     * whole. It is true once every eligible task has completed (including when no task applied at all), which is
     * before the identity is applied to the session and before any inlined Bind2 resource binding or XEP-0198
     * resumption is attempted. A failure in one of those later steps is not reflected here. A provider that must
     * know whether the peer actually ended up authenticated should observe
     * {@link org.jivesoftware.openfire.event.SessionEventDispatcher} instead.
     *
     * @param context the negotiation context (never null).
     * @param successful true if every eligible task completed, false if the negotiation failed or was aborted.
     */
    default void onNegotiationEnded(@Nonnull final Sasl2TaskContext context, final boolean successful)
    {
    }

    /**
     * The relative order in which this provider's tasks appear in the {@code <continue/>} element. Providers are
     * consulted in descending order of priority.
     *
     * @return a priority. Defaults to zero.
     */
    default int getPriority()
    {
        return 0;
    }
}
