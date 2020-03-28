package org.jivesoftware.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Ignore;
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

        private AtomicInteger count = new AtomicInteger();

        private boolean orderedExecutorTest = true;

        private OrderedExecutor executor = new OrderedExecutor();

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
                    futures.add(orderedExecutorTest ? executor.submit(dor) : regularExecutor.submit(dor));
                }
            });
            t.start();
            threads.add(t);

        }

        void waitForDone() {
            try {
                for (Thread t : threads) {
                    t.join();
                }
                for (Future<?> f : futures) {
                    f.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                Assert.fail();
            }
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
            stats.count.incrementAndGet();
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
            DelayedOrderedRunnable tests[] = {
                    new DelayedOrderedRunnable(stats, "1", 0, "a"),
                    new DelayedOrderedRunnable(stats, "2", 0, "a"),
                    new DelayedOrderedRunnable(stats, "3", 0, "b"),
                    new DelayedOrderedRunnable(stats, "4", 0, "c"),
                    new DelayedOrderedRunnable(stats, "5", 0, "b"),
                    new DelayedOrderedRunnable(stats, "6", 0, "b"),
                    new DelayedOrderedRunnable(stats, "7", 0, "c"),
                    new DelayedOrderedRunnable(stats, "8", 0, "c"),
                    new DelayedOrderedRunnable(stats, "9", 0, "d") };

            String[][] expectedOrderSequences = { 
                    { "1", "2" }, // ordering key "a"
                    { "3", "5", "6" }, // ordering key "b"
                    { "4", "7", "8" } }; // ordering key "c"

            stats.start();
            stats.exe(tests);
            stats.waitForDone();

            for (String[] expectedOrder : expectedOrderSequences) {
                verifyOrder(stats, expectedOrder);
            }
        } finally {

            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
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
     * a single thread has the same key, which forces them to run sequentially.<b>
     * Test is just to profile the time taken and count of threads created.
     */
    @Test
    @Ignore
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
            stats.waitForDone();
            printStats(stats, "testOrderedExecutorCommonKeysProfile");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
        }
    }

    /**
     * 100 threads submitting 100 tasks each with different keys for each task, in
     * an ordered executor. Total parallel execution.<b>
     * Test is just to profile the time taken and count of threads created.
     */
    @Test
    @Ignore
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
            stats.waitForDone();
            printStats(stats, "testOrderedExecutorUniqueKeysProfile");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
        }
    }

    /**
     * 100 threads submitting 100 tasks each with different keys for each task, in a
     * regular thread pool executor. Total parallel execution.<b>
     * Test is just to profile the time taken and count of threads created.
     */
    @Test
    @Ignore
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
            stats.waitForDone();
            printStats(stats, "testRegularExecutorProfile");
            stats.executor.shutDown();
            stats.regularExecutor.shutdown();
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

    private void printStats(Stats stats, String testName) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(testName).append(']').append('{')
                .append(" Total time taken = " + (stats.endTime - stats.startTime)).append(", ")
                .append(" Number of tasks executed = " + stats.count.get()).append(", ")
                .append(" Number of Threads = " + (stats.orderedExecutorTest ? stats.executor.getPoolSize()
                        : stats.regularExecutor.getPoolSize()))
                .append("}");
        print(sb.toString());
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void print(String s) {
        System.out.println(Thread.currentThread() + " - " + s);
    }

}
