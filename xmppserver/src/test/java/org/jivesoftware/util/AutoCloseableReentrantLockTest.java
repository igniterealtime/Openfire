package org.jivesoftware.util;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jivesoftware.openfire.XMPPServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class AutoCloseableReentrantLockTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoCloseableReentrantLockTest.class);
    @Rule public ExpectedException expectedException = ExpectedException.none();

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

    @Test(timeout = 1000)
    public void willLockAResource() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AtomicInteger callCount = new AtomicInteger(0);

        // Create a thread that acquires a lock
        thread1 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1").lock()) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread1.start();

        // Create a thread that attempts (and fails) to acquire the same lock
        thread2 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1").lock()) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread2.start();

        // Wait until we can be sure both threads have started
        await().untilTrue(lockAcquired);

        assertThat(callCount.get(), is(1));
    }

    @Test(timeout = 1000)
    public void willUseDifferentLocksForDifferentPartsOfTheSameClass() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AtomicInteger callCount = new AtomicInteger(0);

        thread1 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1").lock()) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread1.start();

        // Create a lock for a different user on the same class - should be acquired
        thread2 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user2").lock()) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
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
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1").lock()) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread1.start();

        // Create a lock for the same user but for a different class - should be acquired
        thread2 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(XMPPServer.class, "user1").lock()) {
                lockAcquired.set(true);
                callCount.incrementAndGet();
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
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

        try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1").lock()) {
            callCount.incrementAndGet();
        }

        try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1").lock()) {
            callCount.incrementAndGet();
        }

        assertThat(callCount.get(), is(2));
    }

    @Test(timeout = 1000)
    public void willNotReuseAReleasedLock() {

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("already been released");

        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");
        final AutoCloseableReentrantLock.AutoCloseableLock autoCloseable = lock.lock();
        autoCloseable.close();

        lock.lock();
    }

    @Test(timeout = 1000)
    public void willReuseAnUnreleasedLock() {

        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");
        final AutoCloseableReentrantLock.AutoCloseableLock autoCloseable1 = lock.lock();
        final AutoCloseableReentrantLock.AutoCloseableLock autoCloseable2 = lock.lock();
        autoCloseable1.close();
        autoCloseable2.close();
    }

    @Test(timeout = 1000)
    public void willIndicateTheLockIsHeldByTheCurrentThread() {

        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");

        try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = lock.lock()) {
            assertThat(lock.isHeldByCurrentThread(), is(true));
            assertThat(lock.isLocked(), is(true));
        }
        assertThat(lock.isLocked(), is(false));

    }

    @Test(timeout = 1000)
    public void willIndicateTheLockIsHeldByAnotherThread() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");
        thread1 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = lock.lock()) {
                lockAcquired.set(true);
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread1.start();

        await().untilTrue(lockAcquired);

        assertThat(lock.isHeldByCurrentThread(), is(false));
        assertThat(lock.isLocked(), is(true));
    }

    @Test(timeout = 1000)
    public void willReturnEmptyIfTheLockIsHeldByAnotherThread() {

        final AtomicBoolean lockAcquired = new AtomicBoolean(false);
        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");
        thread1 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = lock.lock()) {
                lockAcquired.set(true);
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread1.start();

        await().untilTrue(lockAcquired);

        assertThat(lock.tryLock(), is(Optional.empty()));
    }

    @Test(timeout = 1000)
    public void willReturnTheCloseableIfTheLockIsNotHeldByAnotherThread() {

        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");
        try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = lock.lock()) {
            final Optional<AutoCloseableReentrantLock.AutoCloseableLock> optionalCloseable = lock.tryLock();
            assertThat(optionalCloseable.isPresent(), is(true));
            optionalCloseable.get().close();
        }
    }

    @Test(timeout = 1000)
    public void willInterruptWhilstWaitingForALock() throws InterruptedException {

        final AtomicBoolean lock1Acquired = new AtomicBoolean(false);
        final AtomicBoolean thread2Started = new AtomicBoolean(false);
        final AtomicBoolean lock2Acquired = new AtomicBoolean(false);
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final AutoCloseableReentrantLock lock = new AutoCloseableReentrantLock(AutoCloseableReentrantLockTest.class, "user1");
        thread1 = new Thread(() -> {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = lock.lock()) {
                lock1Acquired.set(true);
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted whilst sleeping", e);
            }
        });
        thread1.start();

        thread2 = new Thread(() -> {
            await().untilTrue(lock1Acquired);
            thread2Started.set(true);
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = lock.lockInterruptibly()) {
                lock2Acquired.set(true);
            } catch (final InterruptedException e) {
                interrupted.set(true);
            }
        });
        thread2.start();

        await().untilTrue(thread2Started);
        thread2.interrupt();
        thread2.join();

        assertThat(interrupted.get(), is(true));
        assertThat(lock2Acquired.get(), is(false));
    }
}
