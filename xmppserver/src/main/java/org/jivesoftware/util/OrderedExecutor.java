package org.jivesoftware.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * 
 * An executor, which can execute <code>OrderedRunnable</code> instances. The
 * executions are ordered using the key returned by
 * <code>OrderedRunnable.getOrderingKey()</code>. This means that, when two
 * <code>OrderedRunnable</code> instances with same ordering key are submitted,
 * they are executed sequentially in the same order they were submitted. For
 * <code>OrderedRunnable</code> instances with different keys, this doesn't
 * apply and will be executed in parallel.
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
    public final Set<Object> executingItemKeys = new HashSet<>();

    /**
     * The queue which holds the items that are yet to be submitted for execution,
     * due to tasks with the same keys being executed.
     */
    public final Queue<OrderedFutureRunnable> localQueue = new LinkedList<>();

    /**
     * The actual ThreadPoolExecutor.
     */
    private final ExecutorService executor;

    /**
     * Constructor.
     */
    public OrderedExecutor() {
        final ThreadFactory threadFactory = new NamedThreadFactory("OrderedExecutor-pool-", true, Thread.NORM_PRIORITY,
                Thread.currentThread().getThreadGroup(), 0L);
        executor = Executors.newCachedThreadPool(threadFactory);
    }

    /**
     * Submits an <code>OrderedRunnable</code> to be executed.
     * <p>
     * If an a task with the same ordering key, as this ordered runnable, is
     * currently being executed by any thread in the pool, this task will be queued
     * in a different queue. If no tasks with the same ordering key is being
     * executed, this task will immediately be taken up for execution.<br>
     * The queued tasks will be taken for execution in the same order as they are
     * queued, after the tasks with same ordering key, which are running finishes.
     * 
     * @param item the task to be executed.
     * @return a Future that can be used for getting knowing if the task is
     *         completed.
     */
    public Future<?> submit(OrderedRunnable item) {
        OrderedFutureRunnable future = new OrderedFutureRunnable(item);
        synchronized (lock) {
            if (executingItemKeys.contains(item.getOrderingKey())) {
                localQueue.add(future);
                return future;
            }
            executingItemKeys.add(item.getOrderingKey());
        }
        future.setFuture(executor.submit(new ExecutorRunnable(future)));
        return future;
    }

    /**
     * Removes the ordering key from the current set of executing task keys, and
     * examines the queue for the next candidate to be taken for execution.
     * 
     * @param finishedItemOrderingKey
     */
    private void dispatchNext(Object finishedItemOrderingKey) {
        OrderedFutureRunnable nextItemToSubmit = null;
        synchronized (lock) {
            executingItemKeys.remove(finishedItemOrderingKey);
            Iterator<OrderedFutureRunnable> ite = localQueue.iterator();
            while (ite.hasNext()) {
                OrderedFutureRunnable nextRunnable = ite.next();
                Object nextItemOrderingKey = nextRunnable.getOrderingKey();
                if (!executingItemKeys.contains(nextItemOrderingKey)) {
                    ite.remove();
                    executingItemKeys.add(nextItemOrderingKey);
                    nextItemToSubmit = nextRunnable;
                }
            }
        }
        if (nextItemToSubmit != null) {
            nextItemToSubmit.setFuture(executor.submit(new ExecutorRunnable(nextItemToSubmit)));
        }
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
