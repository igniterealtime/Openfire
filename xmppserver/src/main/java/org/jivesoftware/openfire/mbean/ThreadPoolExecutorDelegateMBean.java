/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.mbean;

/**
 * MBean definition for a Threadpool-based executor (@link {@link java.util.concurrent.ThreadPoolExecutor}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public interface ThreadPoolExecutorDelegateMBean
{
    String BASE_OBJECT_NAME = "org.igniterealtime.openfire:type=ThreadPoolExecutor,name=";

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     */
    int getCorePoolSize();

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    int getPoolSize();

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    int getLargestPoolSize();

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     */
    int getMaximumPoolSize();

    /**
     * Returns the approximate number of threads that are actively executing tasks.
     *
     * @return the number of threads
     */
    int getActiveCount();

    /**
     * Returns the number of tasks that are currently waiting to be executed by the delegate executor.
     *
     * @return the task queue size
     */
    int getQueuedTaskCount();

    /**
     * Returns the number of additional elements that task queue used by the delegate executor
     * can ideally (in the absence of memory or resource constraints) accept without blocking,
     * or {@code Integer.MAX_VALUE} if there is no intrinsic limit.
     *
     * @return the remaining capacity
     */
    int getQueueRemainingCapacity();

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    long getTaskCount();

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    long getCompletedTaskCount();
}
