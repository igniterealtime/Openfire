package org.jivesoftware.util;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jivesoftware.openfire.XMPPServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutoCloseableReentrantLockTest {

    private Thread thread1;
    private Thread thread2;

    @Before
    public void setUp() {
        thread1 = null;
        thread2 = null;
    }

    @After
    public void tearDown() throws Exception {
        // Ensure all the threads have had time to complete, releasing the locks
        for (final Thread thread : new Thread[]{thread1, thread2}) {
            if (thread != null) {
                thread.interrupt();
                thread.join();
            }
        }
    }

    @Test(timeout = 250)
    public void willLockAResource() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AtomicInteger callCount = new AtomicInteger(0);

        // Create a thread that acquires a lock
        thread1 = new Thread(() -> {
            try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1")) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread1.start();

        // Create a thread that attempts (and fails) to acquire the same lock
        thread2 = new Thread(() -> {
            try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1")) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        // Wait until we can be sure both threads have started
        await().untilTrue(lockAcquired);

        assertThat(callCount.get(), is(1));
    }

    @Test(timeout = 250)
    public void willUseDifferentLocksForDifferentPartsOfTheSameClass() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AtomicInteger callCount = new AtomicInteger(0);

        thread1 = new Thread(() -> {
            try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1")) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread1.start();

        // Create a lock for a different user on the same class - should be acquired
        thread2 = new Thread(() -> {
            try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user2")) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        // Wait until we can be sure both threads have started
        await().untilTrue(lockAcquired);

        assertThat(callCount.get(), is(2));

    }

    @Test(timeout = 1000)
    public void willUseDifferentLocksForDifferentClassesWithTheSamePart() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AtomicInteger callCount = new AtomicInteger(0);

        thread1 = new Thread(() -> {
            try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1")) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread1.start();

        // Create a lock for the same user but for a different class - should be acquired
        thread2 = new Thread(() -> {
            try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(XMPPServer.class, "user1")) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        // Wait until we can be sure both threads have started
        await().untilTrue(lockAcquired);

        assertThat(callCount.get(), is(2));

    }

    @Test(timeout = 1000)
    public void locksWillBeAutoClosed() {

        final AtomicInteger callCount = new AtomicInteger(0);

        try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1")) {
            callCount.incrementAndGet();
        }

        try (final QuietAutoCloseable ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1")) {
            callCount.incrementAndGet();
        }

        assertThat(callCount.get(), is(2));
    }

}
