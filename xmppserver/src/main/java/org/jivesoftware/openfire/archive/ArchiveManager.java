/*
 * Copyright (C) 2019-2022 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegate;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegateMBean;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.SystemProperty;

import javax.management.ObjectName;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

/**
 * A manager of tasks that write archives into storage.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ArchiveManager extends BasicModule
{
    /**
     * The number of threads to keep in the thread pool that writes messages to the database, even if they are idle.
     */
    public static final SystemProperty<Integer> EXECUTOR_CORE_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.archivemanager.threadpool.size.core")
        .setMinValue(0)
        .setDefaultValue(0)
        .setDynamic(false)
        .build();

    /**
     * The maximum number of threads to allow in the thread pool that writes messages to the database.
     */
    public static final SystemProperty<Integer> EXECUTOR_MAX_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.archivemanager.threadpool.size.max")
        .setMinValue(1)
        .setDefaultValue(Integer.MAX_VALUE)
        .setDynamic(false)
        .build();

    /**
     * The number of threads in the thread pool that writes messages to the database is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating.
     */
    public static final SystemProperty<Duration> EXECUTOR_POOL_KEEP_ALIVE = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.archivemanager.threadpool.keepalive")
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDefaultValue(Duration.ofSeconds(60))
        .setDynamic(false)
        .build();

    /**
     * A thread pool that writes messages to the database.
     */
    private ThreadPoolExecutor executor;

    /**
     * Object name used to register delegate MBean (JMX) for the thread pool executor.
     */
    private ObjectName objectName;

    /**
     * Currently running tasks.
     */
    private final ConcurrentMap<String, Archiver> tasks = new ConcurrentHashMap<>();

    public ArchiveManager()
    {
        super( "ArchiveManager" );
    }

    /**
     * Initializes the manager, by instantiating a thread pool that is used to process archiving tasks.
     */
    @Override
    public void initialize( final XMPPServer server )
    {
        if ( executor != null )
        {
            throw new IllegalStateException( "Already initialized." );
        }
        executor = new ThreadPoolExecutor(
            EXECUTOR_CORE_POOL_SIZE.getValue(),
            EXECUTOR_MAX_POOL_SIZE.getValue(),
            EXECUTOR_POOL_KEEP_ALIVE.getValue().toSeconds(),
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new NamedThreadFactory( "archive-service-worker-", null, null, null ) );

        if (JMXManager.isEnabled()) {
            final ThreadPoolExecutorDelegateMBean mBean = new ThreadPoolExecutorDelegate(executor);
            objectName = JMXManager.tryRegister(mBean, ThreadPoolExecutorDelegateMBean.BASE_OBJECT_NAME + "archive-manager");
        }
    }

    /**
     * Destroys the module, by shutting down the thread pool that is being used to process archiving tasks.
     *
     * No new archive tasks will be accepted any longer, although previously scheduled tasks can continue
     * to be processed.
     */
    @Override
    public void destroy()
    {
        if (objectName != null) {
            JMXManager.tryUnregister(objectName);
            objectName = null;
        }

        if ( executor != null )
        {
            executor.shutdown();
            executor = null;
        }
    }

    /**
     * Adds a new task, that is started immediately.
     *
     * @param archiver The task to be added. Cannot be null.
     */
    public synchronized void add( final Archiver archiver )
    {
        if (tasks.containsKey( archiver.getId() ) )
        {
            throw new IllegalStateException( "A task with ID " + archiver.getId() + " has already been added." );
        }

        executor.submit( archiver );
        tasks.put( archiver.getId(), archiver );
    }

    /**
     * Stops and removes an exiting task. No-op when the provided task is not managed by this manager.
     *
     * @param archiver The task to be added. Cannot be null.
     */
    public synchronized void remove( final Archiver archiver )
    {
        remove( archiver.getId() );
    }

    /**
     * Stops and removes an existing task. No-op when the provided task is not managed by this manager.
     *
     * @param id The identifier of task to be added. Cannot be null.
     */
    public synchronized void remove( final String id )
    {
        final Archiver task = tasks.remove( id );
        if ( task != null )
        {
            task.stop();
        }
    }

    public Duration availabilityETAOnLocalNode( final String id, final Instant instant )
    {
        final Archiver task = tasks.get( id );
        if (task == null )
        {
            // When a node does not have a task, it has no pending data to write to it.
            return Duration.ZERO;
        }
        else
        {
            return task.availabilityETAOnLocalNode( instant );
        }
    }
}
