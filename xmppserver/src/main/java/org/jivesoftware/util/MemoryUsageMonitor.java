/*
 * Copyright (C) 2022 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import com.sun.management.GarbageCollectionNotificationInfo;
import net.jcip.annotations.Immutable;

import javax.annotation.Nonnull;
import javax.management.NotificationEmitter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Records meory usage (based on the 'heap') of Openfire.
 *
 * This monitor, which is intended to be used as a singleton, records the heap-based memory usage directly after each
 * garbage collection event. It is intended to be used to inform end-users about the current memory consumption of
 * Openfire, excluding any memory that is taken up by instances that are eligble for garbage collection.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2403">Improve Admin Console's memory usage reporting</a>
 */
public class MemoryUsageMonitor {

    private static MemoryUsageMonitor INSTANCE = null;

    @Nonnull
    public static synchronized MemoryUsageMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MemoryUsageMonitor();
        }
        return INSTANCE;
    }

    private MemoryUsage memoryUsage = null;

    private MemoryUsageMonitor()
    {
        // Record an event listener that, upon each grabage collection event, records the current memory usage.
        for (final GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans())
        {
            final NotificationEmitter emitter = (NotificationEmitter) gcBean;
            emitter.addNotificationListener(
                (notification, handback) -> memoryUsage = MemoryUsage.createSnapshot(),
                notification -> notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION),
                null);
        }
    }

    /**
     * Returns the memory usage of Openfire as it was recorded directly after the last garbage collection event. When
     * garbage collection has not yet occurred (eg: shortly after startup), the current memory usage will be returned.
     *
     * @return Memory usage statistics.
     */
    @Nonnull
    public MemoryUsage getMemoryUsageAfterLastGC() {
        return memoryUsage == null ? MemoryUsage.createSnapshot() : memoryUsage;
    }

    /**
     * Memory usage statistics.
     */
    @Immutable
    public static class MemoryUsage {
        final int percent;
        final double usedMemory;
        final double totalMemory;
        final double percentUsed;

        /**
         * Creates a snapshot of the current memory usage.
         *
         * @return memory usage statistics
         */
        @Nonnull
        public static MemoryUsage createSnapshot()
        {
            final Runtime runtime = Runtime.getRuntime();

            final double freeMemory = (double)runtime.freeMemory()/(1024*1024);
            final double maxMemory = (double)runtime.maxMemory()/(1024*1024);
            final double totalMemory = (double)runtime.totalMemory()/(1024*1024);
            final double usedMemory = totalMemory - freeMemory;
            final double percentFree = ((maxMemory - usedMemory)/maxMemory)*100.0;
            final double percentUsed = 100 - percentFree;
            final int percent = 100-(int)Math.round(percentFree);

            return new MemoryUsage(percent, usedMemory, totalMemory, percentUsed);
        }

        protected MemoryUsage(final int percent, final double usedMemory, final double totalMemory, final double percentUsed)
        {
            this.percent = percent;
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.percentUsed = percentUsed;
        }

        /**
         * Returns the amount of memory currently used by Openfire (memory populated with objects), in megabytes.
         *
         * @return total amount of memory used by Openfire, in megabytes.
         */
        public double getUsedMemory() {
            return usedMemory;
        }

        /**
         * Returns the total amount of memory available to Openfire, including used and free memory, in megabytes.
         *
         * This value is equivalent to the amount of committed memory in the Java process.
         *
         * @return total amount of memory available to Openfire, in megabytes.
         */
        public double getTotalMemory() {
            return totalMemory;
        }

        /**
         * Returns the percentage of total memory that is currently used by Openfire.
         *
         * @return a percentage (between 0.0 and 100.0).
         */
        public double getPercentUsed() {
            return Math.max(Math.min(percentUsed, 100.0), 0.0);
        }

        /**
         * Returns the percentage of total memory that is currently used by Openfire, rounded to the nearest integer
         * value.
         *
         * @return a percentage (between 0 and 100).
         */
        public int getPercent() {
            return Math.max(Math.min(percent, 100), 0);
        }
    }
}
