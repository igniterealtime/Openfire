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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A view on one SASL2 negotiation, as seen by one {@link Sasl2TaskProvider} (and by the {@link Sasl2Task} instances
 * that it creates).
 *
 * An instance is scoped to a single SASL2 negotiation attempt on a single session. When a peer restarts the
 * negotiation by sending a new {@code <authenticate/>} element, all state represented by this context is discarded and
 * a new context is created.
 *
 * <h2>Attribute storage</h2>
 * The attribute methods provide scratch space that survives for the duration of the negotiation. Attributes are
 * scoped to the provider that the context was created for: two providers that use the same attribute name will not
 * see each other's values. This is the intended place to record, for example, that the peer opted in to a task in its
 * {@code <authenticate/>} element, so that the decision can be acted on later, when
 * {@link Sasl2TaskProvider#getOfferedTasks(Sasl2TaskContext)} is invoked.
 *
 * <h2>Phases</h2>
 * A context is created when the peer's {@code <authenticate/>} element is processed, which is <em>before</em> SASL
 * authentication has completed. During that phase {@link #isAuthenticated()} returns {@code false} and
 * {@link #getAuthorizationIdentity()} returns {@code null}. Every other callback in this API is invoked only after
 * SASL authentication has succeeded, at which point the authenticated identity is available.
 *
 * <h2>Threading</h2>
 * Instances are not thread-safe. They are intended to be used only from the callbacks that receive them. The manager
 * guarantees that no two callbacks for the same negotiation run concurrently.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
 */
public interface Sasl2TaskContext
{
    /**
     * The session that is performing the SASL2 negotiation.
     *
     * Note that this session is not (yet) authenticated: it does not have an authentication token, and its address is
     * not yet the address of the authenticating user, even after {@link #isAuthenticated()} starts returning
     * {@code true}. Use {@link #getAuthorizationIdentity()} to identify the user.
     *
     * @return the session that is negotiating (never null).
     */
    @Nonnull
    LocalSession getSession();

    /**
     * Indicates whether the SASL exchange itself has completed successfully.
     *
     * This returns {@code false} only during {@link Sasl2TaskProvider#onAuthenticateReceived(Sasl2TaskContext, Element)}.
     *
     * @return true if SASL authentication has succeeded, otherwise false.
     */
    boolean isAuthenticated();

    /**
     * The authorization identity (typically the username, without domain-part) that the peer authenticated as.
     *
     * Returns {@code null} when authentication has not yet completed, and also when the peer authenticated
     * anonymously. Implementations that cannot operate on an anonymous session must check for null.
     *
     * @return the authenticated username, or null.
     */
    @Nullable
    String getAuthorizationIdentity();

    /**
     * The name of the SASL mechanism that the peer selected, in upper case (for example {@code SCRAM-SHA-256-PLUS}).
     *
     * This is relevant to eligibility decisions: a task that requires the peer to know the account password must not
     * be offered after a token-based authentication such as XEP-0484 FAST.
     *
     * <p><strong>Caveat:</strong> while {@link #isAuthenticated()} is still {@code false} (e.g. when read from
     * {@link Sasl2TaskProvider#onAuthenticateReceived(Sasl2TaskContext, Element)}) this is the mechanism name the
     * peer <em>claimed</em> in its {@code <authenticate/>} element, not yet verified in any way. It is overwritten
     * with the SASL layer's confirmed value only once authentication succeeds. Do not base an authorization or
     * eligibility decision on this value before {@link #isAuthenticated()} returns {@code true}; it is safe to use
     * for anything read-only at that stage, such as logging or picking which opt-in parameters to parse.
     *
     * @return the SASL mechanism name (never null).
     */
    @Nonnull
    String getSaslMechanismName();

    /**
     * The channel binding type that was negotiated, if the selected mechanism uses channel binding.
     *
     * @return a channel binding type (such as {@code tls-exporter}), or null if none was negotiated.
     */
    @Nullable
    String getChannelBindingType();

    /**
     * The user agent information (XEP-0388 § 2.2) that the peer supplied in its {@code <authenticate/>} element.
     *
     * This can be used to make a task conditional on a particular client, or to identify the device that a
     * credential is being provisioned for. Note that user agent information is client-provided, and should not be
     * trusted.
     *
     * @return user agent information, or null if the peer did not supply any.
     */
    @Nullable
    UserAgentInfo getUserAgentInfo();

    /**
     * The names of the tasks that have already completed successfully during this negotiation, in the order in which
     * they completed.
     *
     * A provider is expected to consult this when deciding what to offer: the manager will refuse to offer a task
     * name that has already completed, but a provider that offers several alternatives (such as two second-factor
     * methods) is responsible for withdrawing the alternatives once one of them has been performed.
     *
     * @return an immutable list of completed task names (never null, possibly empty).
     */
    @Nonnull
    List<String> getCompletedTaskNames();

    /**
     * The names of the tasks that were offered to the peer in the most recent {@code <continue/>} element.
     *
     * @return an immutable set of task names (never null, possibly empty).
     */
    @Nonnull
    Set<String> getOfferedTaskNames();

    /**
     * The number of {@code <continue/>} elements that have been sent to the peer during this negotiation, including
     * the one currently being assembled.
     *
     * The first invocation of {@link Sasl2TaskProvider#getOfferedTasks(Sasl2TaskContext)} in a negotiation sees a
     * value of 1.
     *
     * @return the current round number.
     */
    int getRound();

    /**
     * The stream feature elements that this provider contributed to the SASL2 {@code <authentication/>} stream
     * feature for this session, as recorded when those features were advertised.
     *
     * This exists so that a provider can verify that a task the peer asks for was in fact offered to it, which
     * prevents a peer from driving a negotiation that was never advertised. It mirrors the check that Openfire
     * performs on SASL mechanism names (see OF-3273).
     *
     * @return detached copies of the elements this provider advertised (never null, possibly empty).
     */
    @Nonnull
    List<Element> getAdvertisedFeatureElements();

    /**
     * Returns a previously stored attribute value.
     *
     * @param name the name of the attribute (cannot be null).
     * @return the stored value, or null if no value is stored under this name.
     */
    @Nullable
    Object getAttribute(@Nonnull String name);

    /**
     * Returns a previously stored attribute value, if it is of the expected type.
     *
     * @param name the name of the attribute (cannot be null).
     * @param type the expected type of the value (cannot be null).
     * @param <T>  the expected type of the value.
     * @return the stored value, or an empty optional if absent or of another type.
     */
    @Nonnull
    <T> Optional<T> getAttribute(@Nonnull String name, @Nonnull Class<T> type);

    /**
     * Stores an attribute value for the duration of this negotiation.
     *
     * @param name  the name of the attribute (cannot be null).
     * @param value the value to store. A null value removes any previously stored value.
     */
    void setAttribute(@Nonnull String name, @Nullable Object value);
}
