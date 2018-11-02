package com.javamonitor.mbeans;

/**
 * A thread deadlock reporter. This mbean makes the thread deadlocks simpler to
 * read.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public interface ThreadingMBean {
    /**
     * Get a more detailed report on the deadlocked threads.
     * 
     * @return A list of threads that were deadlocked, with their stack traces,
     *         in developer readable format, or <code>null</code> if there are
     *         no deadlocks.
     */
    public String getDeadlockStacktraces();

    /**
     * Count the number of new threads.
     * 
     * @return The number of new threads.
     */
    public int getThreadsNew();

    /**
     * Count the number of runnable threads.
     * 
     * @return The number of runnable threads.
     */
    public int getThreadsRunnable();

    /**
     * Count the number of blocked threads.
     * 
     * @return The number of blocked threads.
     */
    public int getThreadsBlocked();

    /**
     * Count the number of waiting threads.
     * 
     * @return The number of waiting threads.
     */
    public int getThreadsWaiting();

    /**
     * Count the number of sleeping and waiting threads.
     * 
     * @return The number of sleeping and waiting threads.
     */
    public int getThreadsTimedWaiting();

    /**
     * Count the number of terminated threads.
     * 
     * @return The number of terminated threads.
     */
    public int getThreadsTerminated();
}
