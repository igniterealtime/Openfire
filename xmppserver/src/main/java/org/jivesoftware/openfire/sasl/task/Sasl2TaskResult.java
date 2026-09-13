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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of one step of a {@link Sasl2Task}.
 *
 * A step either produces data that is to be sent to the peer in a {@code <task-data/>} element (after which the peer
 * is expected to respond with a {@code <task-data/>} element of its own), or completes the task.
 *
 * A step that must abort the entire SASL2 negotiation does not return a result: it throws a
 * {@link org.jivesoftware.openfire.sasl.SaslFailureException} instead.
 *
 * Instances are immutable.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
 */
public final class Sasl2TaskResult
{
    public enum Type
    {
        /**
         * A {@code <task-data/>} element is to be sent to the peer, which is expected to respond in kind.
         */
        TASK_DATA,

        /**
         * The task has completed successfully. The manager will either offer another round of tasks, or complete the
         * SASL2 negotiation.
         */
        COMPLETED
    }

    private final Type type;
    private final List<Element> taskData;
    private final byte[] additionalData;

    private Sasl2TaskResult(@Nonnull final Type type, @Nonnull final List<Element> taskData, @Nullable final byte[] additionalData)
    {
        this.type = type;
        this.taskData = taskData;
        this.additionalData = additionalData;
    }

    /**
     * A result that sends a {@code <task-data/>} element containing the provided children to the peer.
     *
     * Every child element must carry its own namespace: {@code <task-data/>} is in the SASL2 namespace, which is not
     * an appropriate namespace for third-party payloads.
     *
     * @param children the child elements of the {@code <task-data/>} element that is sent to the peer.
     * @return a result (never null).
     */
    @Nonnull
    public static Sasl2TaskResult taskData(@Nonnull final Element... children)
    {
        return taskData(List.of(children));
    }

    /**
     * A result that sends a {@code <task-data/>} element containing the provided children to the peer.
     *
     * @param children the child elements of the {@code <task-data/>} element that is sent to the peer. Can be empty,
     *                 which sends an empty {@code <task-data/>} element.
     * @return a result (never null).
     */
    @Nonnull
    public static Sasl2TaskResult taskData(@Nonnull final Collection<Element> children)
    {
        final List<Element> copies = new ArrayList<>(children.size());
        for (final Element child : children) {
            copies.add(child.createCopy());
        }
        return new Sasl2TaskResult(Type.TASK_DATA, Collections.unmodifiableList(copies), null);
    }

    /**
     * A result that completes the task without contributing any data to the response that the peer receives.
     *
     * @return a result (never null).
     */
    @Nonnull
    public static Sasl2TaskResult completed()
    {
        return completed(null);
    }

    /**
     * A result that completes the task, contributing mechanism-style {@code <additional-data/>} to the response that
     * the peer receives.
     *
     * The data is placed in the next {@code <continue/>} element if another round of tasks follows, or in the final
     * {@code <success/>} element if this was the last task. Most tasks have no such data and use
     * {@link #completed()}.
     *
     * @param additionalData data to be base64-encoded into an {@code <additional-data/>} element, or null.
     * @return a result (never null).
     */
    @Nonnull
    public static Sasl2TaskResult completed(@Nullable final byte[] additionalData)
    {
        return new Sasl2TaskResult(Type.COMPLETED, Collections.emptyList(), additionalData == null ? null : additionalData.clone());
    }

    @Nonnull
    public Type getType()
    {
        return type;
    }

    /**
     * The children of the {@code <task-data/>} element to send to the peer. Only meaningful for {@link Type#TASK_DATA}.
     *
     * @return an immutable list of elements (never null, possibly empty).
     */
    @Nonnull
    public List<Element> getTaskData()
    {
        return taskData;
    }

    /**
     * Data to be rendered as {@code <additional-data/>}. Only meaningful for {@link Type#COMPLETED}.
     *
     * @return the additional data, or null.
     */
    @Nullable
    public byte[] getAdditionalData()
    {
        return additionalData == null ? null : additionalData.clone();
    }

    @Override
    public String toString()
    {
        return "Sasl2TaskResult{type=" + type + ", taskData=" + taskData.size() + " element(s), additionalData=" + (additionalData == null ? "none" : additionalData.length + " byte(s)") + '}';
    }
}
