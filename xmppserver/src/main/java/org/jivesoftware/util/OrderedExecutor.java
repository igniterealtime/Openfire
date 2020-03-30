package org.jivesoftware.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 
 * An executor, which can execute <code>OrderedRunnable</code> instances. The
 * executions are ordered using the key returned by
 * <code>OrderedRunnable.getOrderingKey()</code>. This means that, when two
 * <code>OrderedRunnable</code> instances with same ordering key are submitted,
 * they are executed sequentially in the same order they were submitted. For
 * <code>OrderedRunnable</code> instances with different keys, this rule doesn't
 * apply and they will be executed in parallel.
 * 
 * 
 * @author <a href="mailto:renjithalexander@gmail.com">Renjith Alexander</a>
 */
public class OrderedExecutor {

    /**
     * Lock to guard the ordering logic.
     */
    private final Object lock = new Object();

    /**
     * The set of keys that are corresponding to the tasks being executed at any
     * point of time.
     */
    private final Set<Object> executingItemKeys = new HashSet<>();

    /**
     * The Map of keys to the count of queued items.
     */
    private final Map<Object, Integer> queuedItemKeys = new HashMap<>();

    /**
     * The queue which holds the items that are yet to be submitted for execution.
     */
    private final Queue<OrderedFutureRunnable> localQueue = new LinkedList<>();

    /**
     * The actual ThreadPoolExecutor.
     */
    private final ThreadPoolExecutor executor;

    /**
     * Constructor.
     */
    public OrderedExecutor() {
        final ThreadFactory threadFactory = new NamedThreadFactory("OrderedExecutor-pool-", true, Thread.NORM_PRIORITY,
                Thread.currentThread().getThreadGroup(), 0L);
        executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                threadFactory);
    }

    /**
     * Submits an <code>OrderedRunnable</code> to be executed.
     * <p>
     * If a task with the same ordering key, as the passed ordered runnable, is
     * currently being executed by any thread in the pool, this task will be queued
     * in a 'deferred' queue. If no tasks with the same ordering key is being
     * executed, this task will immediately be taken up for execution.<br>
     * The queued tasks will be taken for execution in the same order as they are
     * queued, after the tasks with same ordering key, which are running finishes.
     * 
     * @param item the task to be executed.
     * @return a Future that can be used for knowing when the task is gets
     *         completed.
     */
    public Future<?> submit(OrderedRunnable item) {
        OrderedFutureRunnable future = new OrderedFutureRunnable(item);
        synchronized (lock) {
            Object orderingKey = item.getOrderingKey();
            // If another task with the same key is being executed,
            // queue this item and return.
            if (executingItemKeys.contains(orderingKey)) {
                localQueue.add(future);
                incrementQueuedCount(orderingKey);
                return future;
            }
            executingItemKeys.add(item.getOrderingKey());
        }
        future.setFuture(executor.submit(new ExecutorRunnable(future)));
        return future;
    }

    /**
     * Removes the ordering key from the current set of executing task keys, and
     * examines the queue for the next candidate to be taken for execution. If one
     * found, that task is submitted to the ExecutorService.
     * 
     * @param finishedItemOrderingKey
     */
    private void dispatchNext(Object finishedItemOrderingKey) {
        OrderedFutureRunnable nextItemToSubmit = null;
        synchronized (lock) {
            executingItemKeys.remove(finishedItemOrderingKey);
            // Only if this task caused another task to get queued, try
            // fetching the next task to execute. Otherwise, all threads will be
            // trying to identify and submit the next task, wasting CPU cycles.
            if (!queuedItemKeys.containsKey(finishedItemOrderingKey)) {
                return;
            }
            Iterator<OrderedFutureRunnable> ite = localQueue.iterator();
            while (ite.hasNext()) {
                OrderedFutureRunnable nextRunnable = ite.next();
                Object nextItemOrderingKey = nextRunnable.getOrderingKey();
                if (!executingItemKeys.contains(nextItemOrderingKey)) {
                    ite.remove();
                    executingItemKeys.add(nextItemOrderingKey);
                    nextItemToSubmit = nextRunnable;
                    // Now that new task is taken up for execution,
                    // reduced the queued count of its key.
                    decrementQueuedCount(nextItemOrderingKey);
                    break;
                }
            }
        }
        if (nextItemToSubmit != null) {
            nextItemToSubmit.setFuture(executor.submit(new ExecutorRunnable(nextItemToSubmit)));
        }
    }

    private void incrementQueuedCount(Object orderingKey) {
        queuedItemKeys.compute(orderingKey, (k, v) -> (v == null) ? 1 : v + 1);
    }

    private void decrementQueuedCount(Object orderingKey) {
        Integer newValue = queuedItemKeys.computeIfPresent(orderingKey, (k, v) -> v - 1);
        if (newValue != null && newValue <= 0) {
            queuedItemKeys.remove(orderingKey);
        }
    }

    /**
     * Returns the pool size.
     * 
     * @return
     */
    public int getPoolSize() {
        return executor.getPoolSize();
    }

    /**
     * Shuts down the executor.
     */
    public void shutDown() {
        executor.shutdown();
    }

    /**
     * The actual runnable, which is composed of the
     * <code>OrderedFutureRunnable</code> submitted to the ExecutorService. This
     * keeps track of end of execution of a task and submits the next task in the
     * queue(if any) to the ExecutorService.
     * 
     * @author <a href="mailto:renjithalexander@gmail.com">Renjith Alexander</a>
     *
     */
    private class ExecutorRunnable implements Runnable {

        private final OrderedFutureRunnable or;

        ExecutorRunnable(OrderedFutureRunnable or) {
            this.or = or;
        }

        @Override
        public void run() {
            try {
                or.run();
            } finally {
                or.done();
                dispatchNext(or.getOrderingKey());
            }

        }

    }

}
