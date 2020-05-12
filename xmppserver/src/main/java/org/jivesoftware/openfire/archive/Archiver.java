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

import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * An abstract runnable that adds to-be-archived data to the database.
 *
 * This implementation is designed to reduce the work load on the database, by batching work where possible, without
 * severely delaying database writes.
 *
 * This implementation acts as a consumer (in context of the producer-consumer design pattern), where the queue that
 * is used to relay work from both processes is passed as an argument to the constructor of this class.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public abstract class Archiver<E> implements Runnable
{
    private static final Logger Log = LoggerFactory.getLogger( Archiver.class );

    private final String id;

    // Do not add more than this amount of queries in a batch.
    private int maxWorkQueueSize;

    // Do not delay longer than this amount of milliseconds before storing data in the database.
    private Duration maxPurgeInterval;

    // Maximum time to wait for 'more' work to arrive, before committing the batch.
    private Duration gracePeriod;

    // Reference to the queue in which work is produced.
    final PriorityBlockingQueue<ArchiveCandidate<E>> queue = new PriorityBlockingQueue<>();

    private boolean running = true;

    private Instant lastProcessed = null;

    final List<ArchiveCandidate<E>> workQueue = Collections.synchronizedList(new ArrayList<>());

    /**
     * Instantiates a new archiver.
     *
     * @param id A unique identifier for this archiver.
     * @param maxWorkQueueSize Do not add more than this amount of queries in a batch.
     * @param maxPurgeInterval Do not delay longer than this amount before storing data in the database.
     * @param gracePeriod Maximum amount of milliseconds to wait for 'more' work to arrive, before committing the batch.
     */
    protected Archiver( String id, int maxWorkQueueSize, Duration maxPurgeInterval, Duration gracePeriod )
    {
        if ( maxWorkQueueSize < 1 )
        {
            throw new IllegalArgumentException( "Argument 'maxWorkQueueSize' must be a positive integer." );
        }

        if ( gracePeriod.compareTo( maxPurgeInterval ) > 0 )
        {
            throw new IllegalArgumentException( "Value for argument 'gracePeriod' cannot be larger than 'maxPurgeInterval'." );
        }

        this.id = id;
        this.maxWorkQueueSize = maxWorkQueueSize;
        this.maxPurgeInterval = maxPurgeInterval;
        this.gracePeriod = gracePeriod;
    }

    public void archive( final E data )
    {
        queue.add( new ArchiveCandidate<>( data ) );
    }

    public String getId()
    {
        return id;
    }

    public void run()
    {
        Log.debug( "Running with max work queue size {}, max purge interval {}, grace period {}.", maxWorkQueueSize, maxPurgeInterval, gracePeriod);

        // This loop is designed to write data to be stored in the database without much delay, while at the same
        // time allowing for batching of work that's produced at roughly the same time (which improves performance).
        while ( running )
        {
            // The batch of work for this iteration.
            new ArrayList<>();

            try
            {
                // Blocks until work is produced.
                ArchiveCandidate<E> work;

                // Continue filling up this batch as long as new archive candidates can be retrieved pretty much
                // instantaneously, but don't take longer than the maximum allowed purge interval (this is intended
                // to make sure that the database content is updated regularly)
                final Instant lastLoopStart = Instant.now();
                final Duration maxLoopDuration = maxPurgeInterval.minus( gracePeriod );
                Duration runtime = Duration.ZERO;
                while ( ( workQueue.size() < maxWorkQueueSize ) // Don't allow the batch to grow to big.
                    && ( runtime.compareTo( maxLoopDuration ) < 0 ) // Don't take to long between commits.
                    && ( ( work = queue.poll( gracePeriod.toMillis(), TimeUnit.MILLISECONDS ) ) != null ) )
                {
                    workQueue.add( work );
                    runtime = Duration.between( lastLoopStart, Instant.now() );
                }
            }
            catch ( InterruptedException e )
            {
                // Causes the thread to stop.
                running = false;
            }

            if ( !workQueue.isEmpty() )
            {
                final List<E> batch = workQueue.stream()
                    .map( ArchiveCandidate::getElement )
                    .collect( Collectors.toList() );
                store( batch );
                lastProcessed = workQueue.get( workQueue.size() -1 ).createdAt();
                Log.trace( "Stored all produced work in the database. Work size: {}", workQueue.size() );
                workQueue.clear();
            }
        }
    }

    public void stop()
    {
        running = false;
    }

    /**
     * Returns an estimation on how long it takes for all data that arrived before a certain instant will have become
     * available in the data store. When data is immediately available, 'zero', is returned;
     *
     * Beware: implementations are free to apply a low-effort mechanism to determine a non-zero estimate. Notably,
     * an implementation can choose to not obtain ETAs from individual cluster nodes, when the local cluster node
     * is reporting a non-zero ETA. However, a return value of 'zero' must be true for the entire cluster (and is,
     * in effect, not an 'estimate', but a matter of fact.
     *
     * This method is intended to be used to determine if it's safe to construct an answer (based on database
     * content) to a request for archived data. Such response should only be generated after all data that was
     * queued before the request arrived has been written to the database.
     *
     * This method performs a cluster-wide check, unlike {@link #availabilityETAOnLocalNode(Instant)}.
     *
     * @param instant The timestamp of the data that should be available (cannot be null).
     * @return A period of time, zero when the requested data is already available.
     */
    public Duration availabilityETA( Instant instant )
    {
        final Duration localNode = availabilityETAOnLocalNode( instant );
        if ( !localNode.isZero() )
        {
            return localNode;
        }

        // Check all other cluster nodes.
        final Collection<Duration> objects = CacheFactory.doSynchronousClusterTask( new GetArchiveWriteETATask( instant, id ), false );
        final Duration maxDuration = objects.stream().max( Comparator.naturalOrder() ).orElse( Duration.ZERO );
        return maxDuration;
    }

    /**
     * Returns an estimation on how long it takes for all data that arrived before a certain instant will have become
     * available in the data store. When data is immediately available, 'zero', is returned;
     *
     * This method is intended to be used to determine if it's safe to construct an answer (based on database
     * content) to a request for archived data. Such response should only be generated after all data that was
     * queued before the request arrived has been written to the database.
     *
     * This method performs a check on the local cluster-node only, unlike {@link #availabilityETA(Instant)}.
     *
     * @param instant The timestamp of the data that should be available (cannot be null).
     * @return A period of time, zero when the requested data is already available.
     */
    public Duration availabilityETAOnLocalNode( final Instant instant )
    {
        if ( instant == null )
        {
            throw new IllegalArgumentException( "Argument 'instant' cannot be null." );
        }

        final Instant now = Instant.now();

        // If the date of interest is in the future, data might still arrive.
        if ( instant.isAfter( now ) )
        {
            final Duration result = Duration.between( now, instant ).plus( gracePeriod );
            Log.debug( "The timestamp that's requested ({}) is in the future. It's unpredictable if more data will become available. Data writes cannot have finished until the requested timestamp plus the grace period, which is in {}", instant, result );
            return result;
        }

        // If the last message that's processed is newer than the instant that we're after, all of the
        // data that is of interest must have been written.
        if ( lastProcessed != null && lastProcessed.isAfter( instant ) )
        {
            Log.debug( "Creation date of last logged data ({}) is younger than the timestamp that's requested ({}). Therefor, all data must have already been written.", lastProcessed, instant );
            return Duration.ZERO;
        }

        // If the date of interest is not in the future, the queue is empty, and
        // no data is currently being worked on, then all data has been written.
        if ( queue.isEmpty() && workQueue.isEmpty() )
        {
            Log.debug( "The timestamp that's requested ({}) is not in the future. All data must have already been received. There's no data queued or being worked on. Therefor, all data must have already been written.", instant );
            return Duration.ZERO;
        }

        if ( queue.isEmpty() ) {
            Log.trace( "Cannot determine with certainty if all data that arrived before {} has been written. The queue of pending writes is empty. Unless new data becomes available, the next write should occur within {}", instant, gracePeriod );
            return gracePeriod;
        } else {
            Log.trace( "Cannot determine with certainty if all data that arrived before {} has been written. The queue of pending writes contains data, which can be an indication of high load. A write should have occurred within {}", instant, maxPurgeInterval );
            return maxPurgeInterval;
        }
    }

    public int getMaxWorkQueueSize()
    {
        return maxWorkQueueSize;
    }

    public void setMaxWorkQueueSize( final int maxWorkQueueSize )
    {
        this.maxWorkQueueSize = maxWorkQueueSize;
    }

    public Duration getMaxPurgeInterval()
    {
        return maxPurgeInterval;
    }

    public void setMaxPurgeInterval( final Duration maxPurgeInterval )
    {
        this.maxPurgeInterval = maxPurgeInterval;
    }

    public Duration getGracePeriod()
    {
        return gracePeriod;
    }

    public void setGracePeriod( final Duration gracePeriod )
    {
        this.gracePeriod = gracePeriod;
    }

    protected abstract void store( List<E> batch );
}
