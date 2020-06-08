/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.NamedThreadFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A manager of tasks that write archives into storage.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ArchiveManager extends BasicModule
{
    /**
     * A thread pool that writes messages to the database.
     */
    private ExecutorService executor;

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
        executor = Executors.newCachedThreadPool( new NamedThreadFactory( "archive-service-worker-", null, null, null ) );
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
