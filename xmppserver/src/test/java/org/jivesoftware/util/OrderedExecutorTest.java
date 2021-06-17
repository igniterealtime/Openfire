package org.jivesoftware.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for OrderedExecutor.
 * 
 * @author <a href="mailto:renjithalexander@gmail.com">Renjith Alexander</a>
 *
 */

public class OrderedExecutorTest {

    private class Stats {

        private long startTime = System.currentTimeMillis(), endTime;

        private AtomicInteger submittedCount = new AtomicInteger();

        private AtomicInteger executedCount = new AtomicInteger();

        private AtomicInteger rejectedCount = new AtomicInteger();

        private boolean orderedExecutorTest = true;

        private OrderedExecutor executor = new OrderedExecutor("Test");

        private ThreadPoolExecutor regularExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());

        private List<DelayedOrderedRunnable> results = Collections.synchronizedList(new ArrayList<>());

        private List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());

        private List<Thread> threads = Collections.synchronizedList(new ArrayList<>());

        void start() {
            startTime = System.currentTimeMillis();
        }

        void exe(final DelayedOrderedRunnable[] dors) {
            Thread t = new Thread(() -> {
                for (DelayedOrderedRunnable dor : dors) {
                    try {
                        futures.add(orderedExecutorTest ? executor.submit(dor) : regularExecutor.submit(dor));
                    } catch (RejectedExecutionException ree) {
                        rejectedCount.incrementAndGet();
                    }
                    submittedCount.incrementAndGet();
                }
            });
            t.start();
            threads.add(t);

        }

        void waitForAllDone() {
            waitForSubmission();
            waitForExecutionCompletion();
        }

        void waitForSubmission() {
            try {
                for (Thread t : threads) {
                    t.join();
                }
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }

        void waitForExecutionCompletion() {
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RejectedExecutionException) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException ie) {

                }
            }
        }

        void verifyCount(boolean noRejection) {
            if (noRejection && rejectedCount.get() > 0) {
                Assert.fail("There are rejections which are not expected");
            }
            Assert.assertEquals(submittedCount.get(), executedCount.get() + rejectedCount.get());
        }

        void print(String testName) {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(testName).append(']').append('{')
                    .append(" Total time taken = " + (endTime - startTime)).append(", ")
                    .append(" Number of tasks submitted = " + submittedCount.get()).append(", ")
                    .append(" Number of tasks executed = " + executedCount.get()).append(", ")
                    .append(" Number of tasks rejected = " + rejectedCount.get()).append(", ")
                    .append(" Number of Threads = "
                            + (orderedExecutorTest ? executor.getPoolSize() : regularExecutor.getPoolSize()))
                    .append("}");
            System.out.println(Thread.currentThread() + " - " + sb.toString());
        }

    }

    private class DelayedOrderedRunnable implements OrderedRunnable {

        private long delay;
        private String orderingKey;
        private String name;
        private Stats stats;

        DelayedOrderedRunnable(Stats stats, String name, long delay, String orderingKey) {
            this.name = name;
            this.delay = delay;
            this.orderingKey = orderingKey;
            this.stats = stats;
        }

        @Override
        public void run() {
            // print("Going to execute " + name);
            sleep(delay);
            // print("Finished executing " + name);
            stats.executedCount.incrementAndGet();
            stats.endTime = System.currentTimeMillis();
            stats.results.add(this);

        }

        @Override
        public Object getOrderingKey() {
            return orderingKey;
        }
    };

    /**
     * Test the ordering functionality.
     */
    @Test
    public void testOrdering() {
        Stats stats = new Stats();
        try {
            stats.orderedExecutorTest = true;
            DelayedOrderedRunnable tests[] = { new DelayedOrderedRunnable(stats, "1", 0, "a"),
                    new DelayedOrderedRunnable(stats, "2", 0, "a"), new DelayedOrderedRunnable(stats, "3", 0, "b"),
                    new DelayedOrderedRunnable(stats, "4", 0, "c"), new DelayedOrderedRunnable(stats, "5", 0, "b"),
                    new DelayedOrderedRunnable(stats, "6", 0, "b"), new DelayedOrderedRunnable(stats, "7", 0, "c"),
                    new DelayedOrderedRunnable(stats, "8", 0, "c"), new DelayedOrderedRunnable(stats, "9", 0, "d") };

            String[][] expectedOrderSequences = { { "1", "2" }, // ordering key "a"
                    { "3", "5", "6" }, // ordering key "b"
                    { "4", "7", "8" } }; // ordering key "c"

            stats.start();
            stats.exe(tests);
            stats.waitForAllDone();

            for (String[] expectedOrder : expectedOrderSequences) {
                verifyOrder(stats, expectedOrder);
            }
        } finally {

            OrderedExecutor.shutdownInstances();
            stats.regularExecutor.shutdown();
            stats.verifyCount(true);
        }

    }

    private void verifyOrder(Stats stats, String[] expectedOrder) {
        List<String> expectedOrderList = Arrays.asList(expectedOrder);
        List<String> actualOrderList = stats.results.stream().map((d) -> d.name)
                .filter((name) -> expectedOrderList.contains(name)).collect(Collectors.toList());
        Assert.assertEquals(expectedOrderList, actualOrderList);

    }

    /**
     * 100 threads submitting 100 tasks, in an ordered executor. Tasks submitted by
     * a single thread has the same key, which forces them to run sequentially.<br>
     * Executor is shut down after all tasks are submitted. Verifies that the
     * submitted count = executed count + rejected count.<br>
     * Also verifies that there are no lock up on the Future.get() of queued tasks,
     * that were not yet submitted to the original executer.
     */
    @Test
    public void testShutdown() {
        Stats stats = new Stats();
        stats.orderedExecutorTest = true;
        try {
            stats.start();
            for (int i = 0; i < 100; ++i) {
                runInSingleThread(stats, 100,
                        new StringBuilder().append((char) (65 + i)).append('-').append(i).toString(), 0, true);
            }
            stats.waitForSubmission();
            stats.executor.shutDown();
            stats.waitForExecutionCompletion();
        } finally {
            stats.print("testShutdown");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
            stats.verifyCount(false);
        }
    }

    /**
     * 100 threads submitting 100 tasks, in an ordered executor. Tasks submitted by
     * a single thread has the same key, which forces them to run sequentially.<br>
     * Test is just to profile the time taken and count of threads created.
     */
    @Test
    public void testOrderedExecutorCommonKeysProfile() {
        Stats stats = new Stats();
        stats.orderedExecutorTest = true;
        try {
            stats.start();
            for (int i = 0; i < 100; ++i) {
                runInSingleThread(stats, 100,
                        new StringBuilder().append((char) (65 + i)).append('-').append(i).toString(), 0, true);
            }
        } finally {
            stats.waitForAllDone();
            stats.print("testOrderedExecutorCommonKeysProfile");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
            stats.verifyCount(true);
        }
    }

    /**
     * 100 threads submitting 100 tasks each with different keys for each task, in
     * an ordered executor. Total parallel execution.<br>
     * Test is just to profile the time taken and count of threads created.
     */
    @Test
    public void testOrderedExecutorUniqueKeysProfile() {
        Stats stats = new Stats();
        stats.orderedExecutorTest = true;
        try {
            stats.start();
            for (int i = 0; i < 100; ++i) {
                runInSingleThread(stats, 100,
                        new StringBuilder().append((char) (65 + i)).append('-').append(i).toString(), 0, false);
            }
        } finally {
            stats.waitForAllDone();
            stats.print("testOrderedExecutorUniqueKeysProfile");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
            stats.verifyCount(true);
        }
    }

    /**
     * 100 threads submitting 100 tasks each with different keys for each task, in a
     * regular thread pool executor. Total parallel execution.<br>
     * Test is just to profile the time taken and count of threads created.
     */
    @Test
    public void testRegularExecutorProfile() {
        Stats stats = new Stats();
        stats.orderedExecutorTest = false;
        try {
            stats.start();
            for (int i = 0; i < 100; ++i) {
                runInSingleThread(stats, 100,
                        new StringBuilder().append((char) (65 + i)).append('-').append(i).toString(), 0, true);
            }
        } finally {
            stats.waitForAllDone();
            stats.print("testRegularExecutorProfile");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
            stats.verifyCount(true);
        }
    }

    private void runInSingleThread(final Stats stats, int count, String name, long delay, boolean sameKey) {
        DelayedOrderedRunnable[] orderedRunnables = new DelayedOrderedRunnable[count];
        for (int i = 0; i < count; ++i) {
            orderedRunnables[i] = new DelayedOrderedRunnable(stats, name + "--" + i, delay,
                    sameKey ? name : name + "--" + i);
        }
        stats.exe(orderedRunnables);

    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
