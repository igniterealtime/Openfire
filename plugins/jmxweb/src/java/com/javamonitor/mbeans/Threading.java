package com.javamonitor.mbeans;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.javamonitor.JmxHelper;

/**
 * A threading bean.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class Threading implements ThreadingMBean {
    /**
     * The object name for the threading helper mbean.
     */
    public static final String objectName = JmxHelper.objectNameBase
            + "Threading";

    private static final ThreadMXBean threadMXBean = ManagementFactory
            .getThreadMXBean();

    private static Method findDeadlockMethod = null;
    static {
        try {
            findDeadlockMethod = ThreadMXBean.class
                    .getMethod("findDeadlockedThreads");
        } catch (Exception ignored) {
            // woops, well I guess this is a 1.5 JVM then

            try {
                findDeadlockMethod = ThreadMXBean.class
                        .getMethod("findMonitorDeadlockedThreads");
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    private ThreadInfo[] findDeadlock() throws IllegalAccessException,
            InvocationTargetException {
        final long[] threadIds = (long[]) findDeadlockMethod.invoke(
                threadMXBean, (Object[]) null);

        if (threadIds == null || threadIds.length < 1) {
            // no deadlock, we're done
            return null;
        }

        final ThreadInfo[] threads = threadMXBean.getThreadInfo(threadIds,
                Integer.MAX_VALUE);
        return threads;
    }

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getDeadlockStacktraces()
     */
    public String getDeadlockStacktraces() {
        try {
            final ThreadInfo[] threads = findDeadlock();
            if (threads == null) {
                // no deadlock, we're done
                return null;
            }

            return stacktraces(threads, 0);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static final int MAX_STACK = 10;

    private String stacktraces(final ThreadInfo[] threads, final int i) {
        if (i >= threads.length) {
            return "";
        }
        final ThreadInfo thread = threads[i];

        final StringBuilder trace = new StringBuilder();
        for (int stack_i = 0; stack_i < Math.min(thread.getStackTrace().length,
                MAX_STACK); stack_i++) {
            if (stack_i == (MAX_STACK - 1)) {
                trace.append("    ...");
            } else {
                trace.append("    at ").append(thread.getStackTrace()[stack_i])
                        .append("\n");
            }
        }

        return "\"" + thread.getThreadName() + "\", id " + thread.getThreadId()
                + " is " + thread.getThreadState() + " on "
                + thread.getLockName() + ", owned by "
                + thread.getLockOwnerName() + ", id " + thread.getLockOwnerId()
                + "\n" + trace + "\n\n" + stacktraces(threads, i + 1);
    }

    /**
     * We keep track of the last time we sampled the thread states. It is a
     * crude optimisation to avoid having to query for the threads states versy
     * often.
     */
    private long lastSampled = 0L;

    private final Map<Thread.State, Integer> states = new HashMap<Thread.State, Integer>();

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getThreadsBlocked()
     */
    public int getThreadsBlocked() {
        sampleThreads();

        return states.get(Thread.State.BLOCKED);
    }

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getThreadsNew()
     */
    public int getThreadsNew() {
        sampleThreads();

        return states.get(Thread.State.NEW);
    }

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getThreadsTerminated()
     */
    public int getThreadsTerminated() {
        sampleThreads();

        return states.get(Thread.State.TERMINATED);
    }

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getThreadsTimedWaiting()
     */
    public int getThreadsTimedWaiting() {
        sampleThreads();

        return states.get(Thread.State.TIMED_WAITING);
    }

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getThreadsWaiting()
     */
    public int getThreadsWaiting() {
        sampleThreads();

        return states.get(Thread.State.WAITING);
    }

    /**
     * @see com.javamonitor.mbeans.ThreadingMBean#getThreadsRunnable()
     */
    public int getThreadsRunnable() {
        sampleThreads();

        return states.get(Thread.State.RUNNABLE);
    }

    private synchronized void sampleThreads() {
        if ((lastSampled + 50L) < System.currentTimeMillis()) {
            lastSampled = System.currentTimeMillis();
            for (final Thread.State state : Thread.State.values()) {
                states.put(state, 0);
            }

            for (final ThreadInfo thread : threadMXBean
                    .getThreadInfo(threadMXBean.getAllThreadIds())) {
                if (thread != null) {
                    final Thread.State state = thread.getThreadState();
                    states.put(state, states.get(state) + 1);
                } else {
                    states.put(Thread.State.TERMINATED, states
                            .get(Thread.State.TERMINATED) + 1);
                }
            }
        }
    }
}
