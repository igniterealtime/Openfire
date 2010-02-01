/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.reporting.util;

import org.picocontainer.Disposable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performs tasks using worker threads. It also allows tasks to be scheduled to be
 * run at future dates. This class mimics relevant methods in both
 * {@link ExecutorService} and {@link Timer}. Any {@link TimerTask} that's
 * scheduled to be run in the future will automatically be run using the thread
 * executor's thread pool. This means that the standard restriction that TimerTasks
 * should run quickly does not apply.
 *
 * @author Matt Tucker
 */
public class TaskEngine implements Disposable {

    private static TaskEngine instance = new TaskEngine();

    /**
     * Returns a task engine instance (singleton).
     *
     * @return a task engine.
     */
    public static TaskEngine getInstance() {
        return instance;
    }

    private Timer timer;
    private ExecutorService executor;
    private final Map<TimerTask, TimerTaskWrapper> wrappedTasks = new HashMap<TimerTask, TimerTaskWrapper>();

    /**
     * Constructs a new task engine.
     */
    private TaskEngine() {
        timer = new Timer("timer-monitoring", true);
        executor = Executors.newCachedThreadPool(new ThreadFactory() {

            final AtomicInteger threadNumber = new AtomicInteger(1);

            public Thread newThread(Runnable runnable) {
                // Use our own naming scheme for the threads.
                Thread thread = new Thread(Thread.currentThread().getThreadGroup(), runnable,
                                      "pool-monitoring" + threadNumber.getAndIncrement(), 0);
                // Make workers daemon threads.
                thread.setDaemon(true);
                if (thread.getPriority() != Thread.NORM_PRIORITY) {
                    thread.setPriority(Thread.NORM_PRIORITY);
                }
                return thread;
            }
        });
    }

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task.
     *
     * @param task the task to submit.
     * @return a Future representing pending completion of the task,
     *      and whose <tt>get()</tt> method will return <tt>null</tt>
     *      upon completion.
     * @throws java.util.concurrent.RejectedExecutionException if task cannot be scheduled
     *      for execution.
     * @throws NullPointerException if task null.
     */
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    /**
     * Schedules the specified task for execution after the specified delay.
     *
     * @param task  task to be scheduled.
     * @param delay delay in milliseconds before task is to be executed.
     * @throws IllegalArgumentException if <tt>delay</tt> is negative, or
     *         <tt>delay + System.currentTimeMillis()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or
     *         cancelled, or timer was cancelled.
     */
    public void schedule(TimerTask task, long delay) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        synchronized (wrappedTasks) {
            wrappedTasks.put(task, taskWrapper);
        }
        timer.schedule(taskWrapper, delay);
    }

    /**
     * Schedules the specified task for execution at the specified time.  If
     * the time is in the past, the task is scheduled for immediate execution.
     *
     * @param task task to be scheduled.
     * @param time time at which task is to be executed.
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or
     *         cancelled, timer was cancelled, or timer thread terminated.
     */
    public void schedule(TimerTask task, Date time) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        synchronized (wrappedTasks) {
            wrappedTasks.put(task, taskWrapper);
        }
        timer.schedule(taskWrapper, time);
    }

    /**
     * Schedules the specified task for repeated <i>fixed-delay execution</i>,
     * beginning after the specified delay.  Subsequent executions take place
     * at approximately regular intervals separated by the specified period.
     *
     * <p>In fixed-delay execution, each execution is scheduled relative to
     * the actual execution time of the previous execution.  If an execution
     * is delayed for any reason (such as garbage collection or other
     * background activity), subsequent executions will be delayed as well.
     * In the long run, the frequency of execution will generally be slightly
     * lower than the reciprocal of the specified period (assuming the system
     * clock underlying <tt>Object.wait(long)</tt> is accurate).
     *
     * <p>Fixed-delay execution is appropriate for recurring activities
     * that require "smoothness."  In other words, it is appropriate for
     * activities where it is more important to keep the frequency accurate
     * in the short run than in the long run.  This includes most animation
     * tasks, such as blinking a cursor at regular intervals.  It also includes
     * tasks wherein regular activity is performed in response to human
     * input, such as automatically repeating a character as long as a key
     * is held down.
     *
     * @param task   task to be scheduled.
     * @param delay  delay in milliseconds before task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>delay</tt> is negative, or
     *         <tt>delay + System.currentTimeMillis()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or
     *         cancelled, timer was cancelled, or timer thread terminated.
     */
    public void schedule(TimerTask task, long delay, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        synchronized (wrappedTasks) {
            wrappedTasks.put(task, taskWrapper);
        }
        timer.schedule(taskWrapper, delay, period);
    }

    /**
     * Schedules the specified task for repeated <i>fixed-delay execution</i>,
     * beginning at the specified time. Subsequent executions take place at
     * approximately regular intervals, separated by the specified period.
     *
     * <p>In fixed-delay execution, each execution is scheduled relative to
     * the actual execution time of the previous execution.  If an execution
     * is delayed for any reason (such as garbage collection or other
     * background activity), subsequent executions will be delayed as well.
     * In the long run, the frequency of execution will generally be slightly
     * lower than the reciprocal of the specified period (assuming the system
     * clock underlying <tt>Object.wait(long)</tt> is accurate).
     *
     * <p>Fixed-delay execution is appropriate for recurring activities
     * that require "smoothness."  In other words, it is appropriate for
     * activities where it is more important to keep the frequency accurate
     * in the short run than in the long run.  This includes most animation
     * tasks, such as blinking a cursor at regular intervals.  It also includes
     * tasks wherein regular activity is performed in response to human
     * input, such as automatically repeating a character as long as a key
     * is held down.
     *
     * @param task   task to be scheduled.
     * @param firstTime First time at which task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or
     *         cancelled, timer was cancelled, or timer thread terminated.
     */
    public void schedule(TimerTask task, Date firstTime, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        synchronized (wrappedTasks) {
            wrappedTasks.put(task, taskWrapper);
        }
        timer.schedule(taskWrapper, firstTime, period);
    }

    /**
     * Schedules the specified task for repeated <i>fixed-rate execution</i>,
     * beginning after the specified delay.  Subsequent executions take place
     * at approximately regular intervals, separated by the specified period.
     *
     * <p>In fixed-rate execution, each execution is scheduled relative to the
     * scheduled execution time of the initial execution.  If an execution is
     * delayed for any reason (such as garbage collection or other background
     * activity), two or more executions will occur in rapid succession to
     * "catch up."  In the long run, the frequency of execution will be
     * exactly the reciprocal of the specified period (assuming the system
     * clock underlying <tt>Object.wait(long)</tt> is accurate).
     *
     * <p>Fixed-rate execution is appropriate for recurring activities that
     * are sensitive to <i>absolute</i> time, such as ringing a chime every
     * hour on the hour, or running scheduled maintenance every day at a
     * particular time.  It is also appropriate for recurring activities
     * where the total time to perform a fixed number of executions is
     * important, such as a countdown timer that ticks once every second for
     * ten seconds.  Finally, fixed-rate execution is appropriate for
     * scheduling multiple repeating timer tasks that must remain synchronized
     * with respect to one another.
     *
     * @param task   task to be scheduled.
     * @param delay  delay in milliseconds before task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>delay</tt> is negative, or
     *         <tt>delay + System.currentTimeMillis()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or
     *         cancelled, timer was cancelled, or timer thread terminated.
     */
    public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        synchronized (wrappedTasks) {
            wrappedTasks.put(task, taskWrapper);
        }
        timer.scheduleAtFixedRate(taskWrapper, delay, period);
    }

    /**
     * Schedules the specified task for repeated <i>fixed-rate execution</i>,
     * beginning at the specified time. Subsequent executions take place at
     * approximately regular intervals, separated by the specified period.
     *
     * <p>In fixed-rate execution, each execution is scheduled relative to the
     * scheduled execution time of the initial execution.  If an execution is
     * delayed for any reason (such as garbage collection or other background
     * activity), two or more executions will occur in rapid succession to
     * "catch up."  In the long run, the frequency of execution will be
     * exactly the reciprocal of the specified period (assuming the system
     * clock underlying <tt>Object.wait(long)</tt> is accurate).
     *
     * <p>Fixed-rate execution is appropriate for recurring activities that
     * are sensitive to <i>absolute</i> time, such as ringing a chime every
     * hour on the hour, or running scheduled maintenance every day at a
     * particular time.  It is also appropriate for recurring activities
     * where the total time to perform a fixed number of executions is
     * important, such as a countdown timer that ticks once every second for
     * ten seconds.  Finally, fixed-rate execution is appropriate for
     * scheduling multiple repeating timer tasks that must remain synchronized
     * with respect to one another.
     *
     * @param task   task to be scheduled.
     * @param firstTime First time at which task is to be executed.
     * @param period time in milliseconds between successive task executions.
     * @throws IllegalArgumentException if <tt>time.getTime()</tt> is negative.
     * @throws IllegalStateException if task was already scheduled or
     *         cancelled, timer was cancelled, or timer thread terminated.
     */
    public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
        TimerTaskWrapper taskWrapper = new TimerTaskWrapper(task);
        synchronized (wrappedTasks) {
            wrappedTasks.put(task, taskWrapper);
        }
        timer.scheduleAtFixedRate(taskWrapper, firstTime, period);
    }

    /**
     * Cancels the execution of a scheduled task. {@link java.util.TimerTask#cancel()}
     *
     * @param task the scheduled task to cancel.
     */
    public void cancelScheduledTask(TimerTask task) {
        TaskEngine.TimerTaskWrapper taskWrapper;
        synchronized (wrappedTasks) {
            taskWrapper = wrappedTasks.remove(task);
        }
        if (taskWrapper != null) {
            taskWrapper.cancel();
            task.cancel();
        }
    }

    public void dispose() {
        executor.shutdownNow();
        executor = null;

        timer.cancel();
        timer = null;

        instance = null;
        wrappedTasks.clear();
    }

    /**
     * Wrapper class for a standard TimerTask. It simply executes the TimerTask
     * using the executor's thread pool.
     */
    private class TimerTaskWrapper extends TimerTask {

        private TimerTask task;

        public TimerTaskWrapper(TimerTask task) {
            this.task = task;
        }

        public void run() {
            executor.submit(task);
        }
    }
}
