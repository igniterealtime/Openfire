/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Defines a factory for {@link io.netty.channel.ChannelHandler} instances. The instances created by an implementation
 * are directly added to, or removed from, a pipeline.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public interface NettyChannelHandlerFactory
{
    /**
     * Add a new ChannelHandler to the provided pipeline.
     *
     * It is assumed, but not required, that for each invocation, a new ChannelHandler is created.
     *
     * @param pipeline The pipeline to which a ChannelHandler is to be added.
     * @deprecated Use {@link #addNewHandlerTo(ChannelPipeline, EventExecutorGroup)} instead.
     */
    @Deprecated(forRemoval = true, since = "5.1.0") // Remove in or after Openfire 5.2.0
    void addNewHandlerTo(final ChannelPipeline pipeline);

    /**
     * Add a new ChannelHandler to the provided pipeline using the given executor.
     *
     * It is assumed, but not required, that for each invocation, a new ChannelHandler is created.
     *
     * Implementations should ensure that the handler is executed on the given {@link EventExecutorGroup}.
     * By default, this delegates to {@link #addNewHandlerTo(ChannelPipeline)} for backward compatibility.
     *
     * @param pipeline The pipeline to which a ChannelHandler is to be added.
     * @param executor the EventExecutorGroup which will be used to execute the ChannelHandler methods.
     */
    default void addNewHandlerTo(final ChannelPipeline pipeline, final EventExecutorGroup executor) {
        // Delegate to old method by default
        addNewHandlerTo(pipeline);
    }

    /**
     * Remove a ChannelHandler from the provided pipeline.
     *
     * Implementations should ensure that ChannelHandlers added via {@link #addNewHandlerTo(ChannelPipeline, EventExecutorGroup)} can be
     * removed again by invocation of this method.
     *
     * @param pipeline The pipeline to which a ChannelHandler is to be added.
     */
    void removeHandlerFrom(final ChannelPipeline pipeline);
}
