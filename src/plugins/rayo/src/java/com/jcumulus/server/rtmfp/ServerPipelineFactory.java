package com.jcumulus.server.rtmfp;

/**
 * jCumulus is a Java port of Cumulus OpenRTMP
 *
 * Copyright 2011 OpenRTMFP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License received along this program for more
 * details (or else see http://www.gnu.org/licenses/).
 *
 *
 * This file is a part of jCumulus.
 */

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;


public class ServerPipelineFactory implements ChannelPipelineFactory
{
    private OrderedMemoryAwareThreadPoolExecutor orderedmemoryawarethreadpoolexecutor = null;
    private Sessions sessions;


    public ServerPipelineFactory(Sessions sessions, OrderedMemoryAwareThreadPoolExecutor orderedmemoryawarethreadpoolexecutor)
    {
        this.sessions = sessions;
        this.orderedmemoryawarethreadpoolexecutor = orderedmemoryawarethreadpoolexecutor;
    }

    public ChannelPipeline getPipeline() throws Exception
    {
        ChannelPipeline channelpipeline = Channels.pipeline();
        channelpipeline.addLast("pipelineExecutor", new ExecutionHandler(orderedmemoryawarethreadpoolexecutor));
        channelpipeline.addLast("handler", new RtmfpChannelUpstreamHandler(sessions));
        return channelpipeline;
    }

}
