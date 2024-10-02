/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * A CompletionService that allows solvers to be scheduled.
 *
 * This implementation borrows a lot from {@link ExecutorCompletionService}, which sadly is not designed to be extensible.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ScheduledExecutorCompletionService<V> implements CompletionService<V>
{
    private final ScheduledThreadPoolExecutor executor;
    private final BlockingQueue<Future<V>> completionQueue;

    /**
     * FutureTask extension to enqueue upon completion.
     */
    private static class QueueingFuture<V> extends FutureTask<Void> {
        QueueingFuture(RunnableFuture<V> task,
                       BlockingQueue<Future<V>> completionQueue) {
            super(task, null);
            this.task = task;
            this.completionQueue = completionQueue;
        }
        private final Future<V> task;
        private final BlockingQueue<Future<V>> completionQueue;
        protected void done() { completionQueue.add(task); }
    }

    private RunnableFuture<V> newTaskFor(Callable<V> task) {
        return new FutureTask<>(task);
    }

    private RunnableFuture<V> newTaskFor(Runnable task, V result) {
        return new FutureTask<>(task, result);
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and a
     * {@link LinkedBlockingQueue} as a completion queue.
     *
     * @param executor the executor to use
     * @throws NullPointerException if executor is {@code null}
     */
    public ScheduledExecutorCompletionService(ScheduledThreadPoolExecutor executor) {
        if (executor == null)
            throw new NullPointerException();
        this.executor = executor;
        this.completionQueue = new LinkedBlockingQueue<>();
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and the supplied queue as its
     * completion queue.
     *
     * @param executor the executor to use
     * @param completionQueue the queue to use as the completion queue
     *        normally one dedicated for use by this service. This
     *        queue is treated as unbounded -- failed attempted
     *        {@code Queue.add} operations for completed tasks cause
     *        them not to be retrievable.
     * @throws NullPointerException if executor or completionQueue are {@code null}
     */
    public ScheduledExecutorCompletionService(ScheduledThreadPoolExecutor executor,
                                     BlockingQueue<Future<V>> completionQueue) {
        if (executor == null || completionQueue == null)
            throw new NullPointerException();
        this.executor = executor;
        this.completionQueue = completionQueue;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Nonnull
    public Future<V> submit(@Nonnull Callable<V> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task);
        executor.execute(new QueueingFuture<>(f, completionQueue));
        return f;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Nonnull
    public Future<V> submit(@Nonnull Runnable task, V result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task, result);
        executor.execute(new QueueingFuture<>(f, completionQueue));
        return f;
    }

    public Future<V> schedule(Callable<V> task, Duration delay)
    {
        return schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public Future<V> schedule(Callable<V> task, long delay, TimeUnit unit)
    {
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task);
        executor.schedule(new QueueingFuture<>(f, completionQueue), delay, unit);
        return f;
    }

    public Future<V> take() throws InterruptedException {
        return completionQueue.take();
    }

    public Future<V> poll() {
        return completionQueue.poll();
    }

    public Future<V> poll(long timeout, @Nonnull TimeUnit unit)
        throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }
}
