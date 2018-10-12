/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A channel provides a mechanism to queue work units for processing. Each work unit is
 * encapsulated as a ChannelMessage, and processing of each message is performed by a
 * ChannelHandler.<p>
 *
 * As a request is handled by the system, it will travel through a sequence of channels.
 * This architecture has a number of advantages:
 * <ul>
 *      <li> Each request doesn't need to correspond to a thread. Instead, a thread pool
 *          in each channel processes requests from a queue.
 *      <li> Due to the queue at each channel, the system is much better able to respond
 *          to load spikes.
 * </ul><p>
 *
 * Channels are modeled after SEDA stages. For much much more in-depth architecture information,
 * refer to the <a href="http://www.cs.berkeley.edu/~mdw/proj/sandstorm/">SEDA website</a>.
 *
 * @author Matt Tucker
 */
public class Channel<T extends Packet> {

    private static final Logger Log = LoggerFactory.getLogger(Channel.class);

    private String name;
    private ChannelHandler<T> channelHandler;

    ThreadPoolExecutor executor;

    /**
     * Creates a new channel. The channel should be registered after it's created.
     *
     * @param name the name of the channel.
     * @param channelHandler the handler for this channel.
     */
    public Channel(String name, ChannelHandler<T> channelHandler) {
        this.name = name;
        this.channelHandler = channelHandler;

        executor = new ThreadPoolExecutor(1, 8, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Returns the name of the channel.
     *
     * @return the name of the channel.
     */
    public String getName() {
        return name;
    }

    /**
     * Enqueus a message to be handled by this channel. After the ChannelHandler is done
     * processing the message, it will be sent to the next channel. Messages with a higher
     * priority will be handled first.
     *
     * @param packet an XMPP packet to add to the channel for processing.
     */
    public void add( final T packet )
    {
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    channelHandler.process( packet );
                }
                catch ( Exception e )
                {
                    Log.error( LocaleUtils.getLocalizedString( "admin.error" ), e );

                    try
                    {
                        Session session = SessionManager.getInstance().getSession( packet.getFrom() );
                        if ( session != null )
                        {
                            Log.debug( "Closing session of '{}': {}", packet.getFrom(), session );
                            session.close();
                        }
                    }
                    catch ( Exception e1 )
                    {
                        Log.error( "Unexpected exception while trying to close session of '{}'.", packet.getFrom(), e1 );
                    }
                }
            }
        };
        executor.execute(r);
    }

    /**
     * Returns true if the channel is currently running. The channel can be started and
     * stopped by calling the start() and stop() methods.
     *
     * @return true if the channel is running.
     */
    public boolean isRunning() {
        return !executor.isShutdown();
    }

    /**
     * Starts the channel, which means that worker threads will start processing messages
     * from the queue. If the server isn't running, messages can still be enqueued.
     */
    public void start() {

    }

    /**
     * Stops the channel, which means that worker threads will stop processing messages from
     * the queue. If the server isn't running, messages can still be enqueued.
     */
    public synchronized void stop() {
        executor.shutdown();
    }

    /**
     * Returns the number of currently active worker threads in the channel. This value
     * will always fall in between the min a max thread count.
     *
     * @return the current number of worker threads.
     */
    public int getThreadCount() {
        return executor.getPoolSize();
    }

    /**
     * Returns the min number of threads the channel will use for processing messages.
     * The channel will automatically de-allocate worker threads as the queue load shrinks,
     * down to the defined minimum. This lets the channel consume fewer resources when load
     * is low.
     *
     * @return the min number of threads that can be used by the channel.
     */
    public int getMinThreadCount() {
        return executor.getCorePoolSize();
    }

    /**
     * Sets the min number of threads the channel will use for processing messages.
     * The channel will automatically de-allocate worker threads as the queue load shrinks,
     * down to the defined minimum. This lets the channel consume fewer resources when load
     * is low.
     *
     * @param minThreadCount the min number of threads that can be used by the channel.
     */
    public void setMinThreadCount(int minThreadCount) {
        executor.setCorePoolSize(minThreadCount);
    }

    /**
     * Returns the max number of threads the channel will use for processing messages. The
     * channel will automatically allocate new worker threads as the queue load grows, up to the
     * defined maximum. This lets the channel meet higher concurrency needs, but prevents too
     * many threads from being allocated, which decreases overall system performance.
     *
     * @return the max number of threads that can be used by the channel.
     */
    public int getMaxThreadCount() {
        return executor.getMaximumPoolSize();
    }

    /**
     * Sets the max number of threads the channel will use for processing messages. The channel
     * will automatically allocate new worker threads as the queue size grows, up to the defined
     * maximum. This lets the channel meet higher concurrency needs, but prevents too many threads
     * from being allocated, which decreases overall system performance.
     *
     * @param maxThreadCount the max number of threads that can be used by the channel.
     */
    public void setMaxThreadCount(int maxThreadCount) {
        executor.setMaximumPoolSize(maxThreadCount);
    }

    /**
     * Returns the current number of ChannelMessage objects waiting to be processed by
     * the channel.
     *
     * @return the current number of elements in the processing queue.
     */
    public int getQueueSize() {
        return executor.getQueue().size();
    }
}
