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

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests that verify the implementation of {@link Archiver}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ArchiverTest
{
    /**
     * Verifies that data that is being archived ends up in the archive.
     *
     * This test verifies that the data is written to the storage.
     */
    @Test
    public void testArchiver() throws Exception
    {
        // Setup fixture.
        final int maxWorkQueueSize = 100;
        final Duration maxPurgeInterval = Duration.ofMillis( 5000 );
        final Duration gracePeriod = Duration.ofMillis( 50 );
        final DummyArchiver archiver = new DummyArchiver( "test", maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        final Thread thread = new Thread( archiver );

        try
        {
            // Execute system under test.
            thread.start();
            archiver.archive( 1 );

            // Verify result.
            waitUntilArchivingIsDone( archiver, 1 );

            assertFalse( archiver.store.isEmpty() );
            final Duration timeUntilLogged = Duration.between( archiver.getStarted(), archiver.store.get( 1 ) );
            assertTrue( timeUntilLogged.compareTo( gracePeriod ) >= 0 );
            assertTrue( timeUntilLogged.compareTo( maxPurgeInterval ) < 0 ); // this needs not be entirely true (due to garbage collection, etc), but is a fair assumption.
        }
        finally
        {
            // Teardown fixture.
            archiver.stop();
        }
    }

    /**
     * The archiver is expected to wait for a certain amount of time, before
     * writing data to the store (this is intended to allow the archiver to batch
     * work). This test verifies that the data is written to the storage only
     * after the 'grace period' has ended.
     */
    @Test
    public void testArchiverWaitForGrace() throws Exception
    {
        // Setup fixture.
        final int maxWorkQueueSize = 100;
        final Duration maxPurgeInterval = Duration.ofMillis( 5000 );
        final Duration gracePeriod = Duration.ofMillis( 250 );
        final DummyArchiver archiver = new DummyArchiver( "test", maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        final Thread thread = new Thread( archiver );

        try
        {
            // Execute system under test.
            thread.start();
            archiver.archive( 1 );

            // Verify result.
            waitUntilArchivingIsDone( archiver, 1 );

            assertFalse( archiver.store.isEmpty() );
            final Duration timeUntilLogged = Duration.between( archiver.getStarted(), archiver.store.get( 1 ) );
            assertTrue( timeUntilLogged.compareTo( gracePeriod ) >= 0 );
        }
        finally
        {
            // Teardown fixture.
            archiver.stop();
        }
    }

    /**
     * The archiver is expected to wait for a certain amount of time, before
     * writing data to the store (this is intended to allow the archiver to batch
     * work). This test verifies that two sets of data that are written to the
     * archiver within the 'grace period' are written in the same batch.
     */
    @Test
    public void testArchiverInSameBatchWhenWithinGrace() throws Exception
    {
        // Setup fixture.
        final int maxWorkQueueSize = 100;
        final Duration maxPurgeInterval = Duration.ofMillis( 5000 );
        final Duration gracePeriod = Duration.ofMillis( 250 );
        final DummyArchiver archiver = new DummyArchiver( "test", maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        final Thread thread = new Thread( archiver );

        try
        {
            // Execute system under test.
            thread.start();
            final Instant start = Instant.now();
            archiver.archive( 1 );
            archiver.archive( 2 );
            if ( Duration.between( start, Instant.now() ).compareTo( gracePeriod ) >= 0 )
            {
                // This is very unlikely (maybe caused by a stop-the-world garbage collection), but would render the test result useless. Better to check for it explicitly.
                throw new IllegalStateException( "The test data could not be written fast enough for the test to be meaningful." );
            }

            // Verify result.
            waitUntilArchivingIsDone( archiver, 2 );

            assertFalse( archiver.store.isEmpty() );
            assertEquals( 1, archiver.getBatches().size() );
            final List<Integer> firstBatch = archiver.getBatches().get(0).data;
            assertEquals( 2, firstBatch.size() );
            assertEquals( 1, (long) firstBatch.get( 0 ) );
            assertEquals( 2, (long) firstBatch.get( 1 ) );
        }
        finally
        {
            // Teardown fixture.
            archiver.stop();
        }
    }

    /**
     * The archiver is expected to wait for a certain amount of time, before
     * writing data to the store (this is intended to allow the archiver to batch
     * work). This test verifies that two sets of data that are written to the
     * archiver within the 'grace period' are written in different batches.
     */
    @Test
    public void testArchiverInDistinctBatchesWhenOutsideGrace() throws Exception
    {
        // Setup fixture.
        final int maxWorkQueueSize = 100;
        final Duration maxPurgeInterval = Duration.ofMillis( 5000 );
        final Duration gracePeriod = Duration.ofMillis( 50 );
        final DummyArchiver archiver = new DummyArchiver( "test", maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        final Thread thread = new Thread( archiver );

        try
        {
            // Execute system under test.
            thread.start();
            archiver.archive( 1 );
            Thread.sleep( gracePeriod.toMillis() * 2 ); // Timing is affected by thread scheduling, etc. Waiting for _exactly_ 'grace period' often is not long enough for this test.
            archiver.archive( 2 );

            // Verify result.
            waitUntilArchivingIsDone( archiver, 2 );

            assertFalse( archiver.store.isEmpty() );
            assertEquals( 2, archiver.getBatches().size() );
            final List<Integer> firstBatch = archiver.getBatches().get(0).data;
            final List<Integer> secondBatch = archiver.getBatches().get(1).data;
            assertEquals( 1, firstBatch.size() );
            assertEquals( 1, (long) firstBatch.get( 0 ) );
            assertEquals( 1, secondBatch.size() );
            assertEquals( 2, (long) secondBatch.get( 0 ) );
        }
        finally
        {
            // Teardown fixture.
            archiver.stop();
        }
    }

    /**
     * The amount of work that is batched by the archiver is limited. This test
     * asserts that a batch of work will not exceed that limit, even if more
     * work is available within the 'grace period'.
     */
    @Test
    public void testArchiverNoMoreThanQueue() throws Exception
    {
        // Setup fixture.
        final int maxWorkQueueSize = 1;
        final Duration maxPurgeInterval = Duration.ofMillis( 5000 );
        final Duration gracePeriod = Duration.ofMillis( 250 );
        final DummyArchiver archiver = new DummyArchiver( "test", maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        final Thread thread = new Thread( archiver );

        try
        {
            // Execute system under test.
            thread.start();
            final Instant start = Instant.now();
            archiver.archive( 1 );
            archiver.archive( 2 );
            if ( Duration.between( start, Instant.now() ).compareTo( gracePeriod ) >= 0 )
            {
                // This is very unlikely (maybe caused by a stop-the-world garbage collection), but would render the test result useless. Better to check for it explicitly.
                throw new IllegalStateException( "The test data could not be written fast enough for the test to be meaningful." );
            }

            // Verify result.
            waitUntilArchivingIsDone( archiver, 2 );

            assertFalse( archiver.store.isEmpty() );
            assertEquals( 2, archiver.getBatches().size() );
            final List<Integer> firstBatch = archiver.getBatches().get(0).data;
            final List<Integer> secondBatch = archiver.getBatches().get(1).data;
            assertEquals( 1, firstBatch.size() );
            assertEquals( 1, (long) firstBatch.get( 0 ) );
            assertEquals( 1, secondBatch.size() );
            assertEquals( 2, (long) secondBatch.get( 0 ) );
        }
        finally
        {
            // Teardown fixture.
            archiver.stop();
        }
    }

    /**
     * The period in which work is batched by the archiver is limited. This test
     * asserts that batching not exceed that time limit, even if more work is
     * available within the 'grace period'.
     */
    @Test
    public void testArchiverNoLongerThanMaxPurgeInterval() throws Exception
    {
        // Setup fixture.
        final int maxWorkQueueSize = Integer.MAX_VALUE;
        final Duration maxPurgeInterval = Duration.ofMillis( 200 );
        final Duration gracePeriod = Duration.ofMillis( 100 );
        final DummyArchiver archiver = new DummyArchiver( "test", maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        final Thread thread = new Thread( archiver );
        int i = 0;
        final Instant start = Instant.now();
        try
        {
            // Execute system under test.
            thread.start();
            while ( Duration.between( start, Instant.now() ).compareTo( maxPurgeInterval.multipliedBy( 2 ) ) < 0 ) {
                archiver.archive( ++i );
                Thread.sleep( 1 );
            }

            // Verify result.
            waitUntilArchivingIsDone( archiver, i );

            assertFalse( archiver.store.isEmpty() );
            assertTrue(  archiver.getBatches().size() > 1 );
            final List<Integer> firstBatch = archiver.getBatches().get(0).data;
            final List<Integer> secondBatch = archiver.getBatches().get(1).data;
            assertFalse( firstBatch.isEmpty() );
            assertFalse( secondBatch.isEmpty() );
        }
        finally
        {
            // Teardown fixture.
            archiver.stop();
        }
    }

    /**
     * A utility method that blocks until the archiver should reasonably have finished storing data.
     *
     * @param archiver The archiver to wait for.
     * @param expectedStoreSize The amount of data elements that are expected to have been written to store.
     */
    protected static void waitUntilArchivingIsDone( DummyArchiver archiver, int expectedStoreSize ) throws Exception
    {
        final Instant start = Instant.now();

        while ( archiver.store.size() < expectedStoreSize ) {
            if ( Duration.between( start, Instant.now() ).compareTo( archiver.getMaxPurgeInterval().multipliedBy( 10 ) ) > 0 ) {
                throw new IllegalStateException( "Failsafe triggered: test is taking to long." );
            }
            Thread.sleep( 10 );
        }
    }

    /**
     * An Archiver that stores data in memory, while recording timestamps. Intended to be used by unit tests.
     */
    static class DummyArchiver extends Archiver<Integer>
    {
        private final Map<Integer, Instant> store = new HashMap<>();
        private final List<Batch> batches = new ArrayList<>( );
        private Instant started;

        protected DummyArchiver( final String id, final int maxWorkQueueSize, final Duration maxPurgeInterval, final Duration gracePeriod )
        {
            super( id, maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        }

        @Override
        public void run()
        {
            synchronized ( this ) {
                started = Instant.now();
            }
            super.run();
        }

        public synchronized Instant getStarted() {
            return started;
        }

        public synchronized List<Batch> getBatches()
        {
            return batches;
        }

        @Override
        protected synchronized void store( final List<Integer> batch )
        {
            final Instant now = Instant.now();
            batch.forEach( integer -> store.put( integer, now ) );
            batches.add( new Batch( batch ) );
        }

        static class Batch
        {
            final List<Integer> data;
            public Batch( List<Integer> data ) {
                this.data = data;
            }
        }
    }
}
