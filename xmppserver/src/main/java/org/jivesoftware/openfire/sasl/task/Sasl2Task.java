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
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;

import javax.annotation.Nonnull;

/**
 * One execution of a SASL2 task, as defined by the {@code <continue/>} flow of XEP-0388.
 *
 * An instance is created by a {@link Sasl2TaskProvider} when the peer selects the task by sending
 * {@code <next task='...'/>}, and is discarded when the negotiation ends. Instances are therefore stateful and must
 * not be shared between sessions.
 *
 * <h2>Protocol</h2>
 * <ol>
 *     <li>{@link #begin(Element)} is invoked with the {@code <next/>} element that selected this task. That element
 *     may carry a payload, allowing a task to complete in a single round trip.</li>
 *     <li>Whenever {@link Sasl2TaskResult#taskData(Element...)} is returned, a {@code <task-data/>} element is sent to
 *     the peer and the peer's reply is delivered to {@link #onTaskData(Element)}. This repeats for as long as the
 *     implementation wishes.</li>
 *     <li>When {@link Sasl2TaskResult#completed()} is returned, the task is done. The server then either offers
 *     another {@code <continue/>} (if any further task is eligible) or completes the SASL2 negotiation with
 *     {@code <success/>}.</li>
 *     <li>Throwing a {@link SaslFailureException} at any point aborts the entire SASL2 negotiation: the peer receives
 *     a {@code <failure/>} and the session remains unauthenticated. No FAST token is issued, no resource is bound,
 *     and no inline stream resumption takes place.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 * Methods are invoked on the thread that processes inbound data for the peer's connection, and never concurrently for
 * the same instance. They must not block: a database round trip is acceptable, a call out to a third-party service is
 * not. Asynchronous evaluation of a task step is not currently supported.
 *
 * <h2>Security</h2>
 * A task runs after the SASL exchange has succeeded but before the session is authenticated. Openfire holds back the
 * authentication token, resource binding, FAST token issuance and inline stream resumption until every task has
 * completed, so a task can be used as a genuine additional authentication factor. It cannot, however, be used to
 * protect against an attacker that already holds valid credentials <em>and</em> can abandon the connection: the
 * password was verified before the task ran, so a failed task tells the attacker that the password was correct.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
 */
public interface Sasl2Task
{
    /**
     * The name of this task, as it was advertised in the {@code <continue/>} element and selected by the peer.
     *
     * @return a task name (never null or empty).
     */
    @Nonnull
    String getName();

    /**
     * Performs the first step of the task, in response to the peer selecting it.
     *
     * @param next the {@code <next/>} element that selected this task. Never null. Its {@code task} attribute holds
     *             this task's name; any child elements are a task-defined payload.
     * @return the outcome of this step (never null).
     * @throws SaslFailureException to abort the SASL2 negotiation.
     */
    @Nonnull
    Sasl2TaskResult begin(@Nonnull Element next) throws SaslFailureException;

    /**
     * Processes a {@code <task-data/>} element received from the peer.
     *
     * Only invoked after a previous step returned {@link Sasl2TaskResult#taskData(Element...)}. The default
     * implementation aborts the negotiation, which is the correct behaviour for a task that never asks the peer for
     * data.
     *
     * @param taskData the {@code <task-data/>} element received from the peer (never null). Its children are a
     *                 task-defined payload.
     * @return the outcome of this step (never null).
     * @throws SaslFailureException to abort the SASL2 negotiation.
     */
    @Nonnull
    default Sasl2TaskResult onTaskData(@Nonnull final Element taskData) throws SaslFailureException
    {
        throw new SaslFailureException(Failure.MALFORMED_REQUEST, "Task '" + getName() + "' does not expect task data from the peer.");
    }

    /**
     * Invoked when the negotiation ends before this task completed: the peer aborted, another part of the
     * negotiation failed, or the connection was closed.
     *
     * Implementations should release any resources that they hold (a pending challenge, a reserved one-time code)
     * and must not attempt to write to the session. The default implementation does nothing.
     *
     * Note: this may be called even when the negotiation ended because this very task's begin(Element) or
     * onTaskData(Element) threw a SaslFailureException. Implementations must tolerate being asked to release resources
     * they may have already released or never acquired (cleanup should be idempotent).
     */
    default void onAborted()
    {
    }
}
