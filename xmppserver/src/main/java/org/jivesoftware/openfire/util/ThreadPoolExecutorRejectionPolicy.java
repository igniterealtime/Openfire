package org.jivesoftware.openfire.http;

/**
 * Enumerates frequently used RejectedExecutionHandler implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public enum ThreadPoolExecutorRejectionPolicy
{
    /**
     * Indicates desired usage of {@link java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy}.
     */
    DiscardOldestPolicy,

    /**
     * Indicates desired usage of {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy}.
     */
    AbortPolicy,

    /**
     * Indicates desired usage of {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}.
     */
    CallerRunsPolicy,

    /**
     * Indicates desired usage of {@link java.util.concurrent.ThreadPoolExecutor.DiscardPolicy}.
     */
    DiscardPolicy
}
