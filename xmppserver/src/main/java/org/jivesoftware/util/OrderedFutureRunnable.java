package org.jivesoftware.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 
 * A <code>Future</code> type used in <code>OrderedExecutor</code>, as the
 * result of a <code>OrderedExecutor.submit()</code> call.
 * 
 * @author <a href="mailto:renjithalexander@gmail.com">Renjith Alexander</a>
 * 
 */
class OrderedFutureRunnable implements Future<Void>, OrderedRunnable {

    /**
     * The actual runnable.
     */
    private final OrderedRunnable runnable;

    /**
     * Lock to guard the states.
     */
    private final Object lock = new Object();

    /**
     * Actual Future object, got as a result of submitting the runnable to an
     * ExecutorService.
     */
    private Future<?> future;

    /**
     * Flag denoting whether the task has been cancelled.
     */
    private boolean cancelled;
    /**
     * Flag denoting whether the task has been finished.
     */
    private boolean done;

    /**
     * Constructor.
     * 
     * @param runnable the <code>OrderedRunnable</code>.
     */
    OrderedFutureRunnable(OrderedRunnable runnable) {
        this.runnable = runnable;
    }

    /**
     * Sets the actual <code>Future</code> object, got as a result of submitting the
     * runnable to an ExecutorService.<br>
     * Notifies the threads which are waiting on the lock in the <code>get</code>
     * methods, so that now they can use the actual future object for getting the
     * result.
     * 
     * @param f the <code>Future</code> object.
     */
    void setFuture(Future<?> f) {
        synchronized (lock) {
            if (this.future != null) {
                throw new IllegalArgumentException("Future can be set only once");
            }
            if (!cancelled) {
                this.future = f;
                lock.notifyAll();
            }
        }

    }

    /**
     * <code>OrderedExecutor</code> makes call to this method when execution of the
     * <code>OrderedRunnable</code> is finished.
     */
    void done() {
        synchronized (lock) {
            this.done = true;
        }
    }

    /**
     * Tries to cancel the task corresponding to this Future.
     * <p>
     * However, it just prevents the actual <code>OrderedRunnable.run()</code> call
     * from being made, only if it is not actually submitted to an ExecutorService.
     * As the <code>OrderedExecutor</code> relies on completion of a task to submit
     * the queued items, this runnable is required to be submitted to an actual
     * ExecutorService, and be done. Thus, cancelled tasks, which were queued while
     * getting cancelled, too will be submitted to the ExecutorService. However, the
     * actual run method of the task will be skipped.
     * <p>
     * Tasks that are actually submitted to the ExecutorService cannot be cancelled.
     * Neither can they be interrupted, as the <code>OrderedExecutor</code> would
     * need the thread to return, for submitting queued tasks. Thus the parameter
     * <code>mayInterruptIfRunning</code> is unused.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (lock) {
            if (cancelled) {
                return false;
            }
            return cancelled = true;
        }

    }

    /**
     * Returns true if the task got cancelled.
     */
    @Override
    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    /**
     * Returns true if the task is done.
     */
    @Override
    public boolean isDone() {
        synchronized (lock) {
            if (future != null) {
                return future.isDone();
            }
            return cancelled || done;
        }

    }

    /**
     * Actual run of the <code>OrderedRunnable</code>. The
     * <code>OrderedRunnable.run()</code> method will not be called if the task is
     * already cancelled.
     */
    @Override
    public void run() {
        synchronized (lock) {
            if (!cancelled) {
                runnable.run();
            }
        }

    }

    /**
     * Returns the ordering key for the <code>OrderedRunnable</code>.
     */
    @Override
    public Object getOrderingKey() {
        return runnable.getOrderingKey();
    }

    /**
     * Waits for the execution of the task, and returns null.
     * <p>
     * If the task is yet to be submitted to an executor service, this method waits
     * on the internal lock, until it is submitted, and a <code>Future</code> is
     * set. Once a future is available, it will wait on <code>get()</code> method if
     * that future.
     */
    @Override
    public Void get() throws InterruptedException, ExecutionException {
        synchronized (lock) {
            // Task hasn't been submitted for execution yet. Go wait.
            // Once it is submitted, a Future instance is set into this
            // object, which will call notify on the lock.
            if (future == null) {
                lock.wait();
            }
        }
        future.get();
        return null;
    }

    /**
     * Waits for the execution of the task, and returns null.
     * <p>
     * If the task is yet to be submitted to an executor service, this method waits
     * on the internal lock, until it is submitted, and a <code>Future</code> is
     * set. Once a future is available, it will wait on <code>get()</code> method if
     * that future.<br>
     * Timeout behavior remains same as defined in the interface documentation.
     */
    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long actualWaitInMillis = unit.convert(timeout, TimeUnit.MILLISECONDS);
        long submitTime = now();
        synchronized (lock) {
            // Task hasn't been submitted for execution yet. Go wait.
            // Once it is submitted, a Future instance is set into this
            // object, which will call notify on the lock.
            if (future == null) {
                long timeToWait = Math.max(actualWaitInMillis - timeElapsedSince(submitTime), 0);
                lock.wait(timeToWait);
                if (timeElapsedSince(submitTime) >= actualWaitInMillis) {
                    throw new TimeoutException("Wait timed out");
                }
            }
        }
        if (future != null) {
            long timeToWait = Math.max(actualWaitInMillis - timeElapsedSince(submitTime), 0);
            future.get(timeToWait, TimeUnit.MILLISECONDS);
        }
        return null;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long timeElapsedSince(long since) {
        return now() - since;
    }

}
