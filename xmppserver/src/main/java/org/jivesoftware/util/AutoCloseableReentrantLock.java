package org.jivesoftware.util;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link ReentrantLock} lock that can be unlocked using an {@link AutoCloseable}. This allows for easy locking of
 * resources, using a specific class as a namespace. Typical usage:
 * <pre>
 *     try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(Clazz.class, user.getUsername()).lock()) {
 *         user.performNonThreadSafeTask();
 *     }
 * </pre>
 * <p>
 * This essentially has the same effect as:
 * <pre>
 *     synchronised ((Clazz.class.getName() + user.getUsername()).intern()) {
 *         user.performNonThreadSafeTask();
 *     }
 * </pre>
 * <p>
 * but has advantages in that the current status of the lock can interrogated, the lock can be acquired interruptibly, etc.
 */
public class AutoCloseableReentrantLock {

    // This is a WeakHashMap - when there are no references to the key, the entry will be removed
    private static final Map<String, ReentrantLock> LOCK_MAP = Collections.synchronizedMap(new WeakHashMap<>());
    private final ReentrantLock lock;
    private final AutoCloseableLock autoCloseable;
    private String key;

    /**
     * Create a class and resource specific lock. If another thread has not closed another AutoCloseableReentrantLock
     * with the same class and resource then this will block until it is closed.
     *
     * @param clazz    The class for which the lock should be created.
     * @param resource The resource for which the lock should be created.
     */
    public AutoCloseableReentrantLock(final Class clazz, final String resource) {
        key = (clazz.getName() + '#' + resource).intern();
        lock = LOCK_MAP.computeIfAbsent(key, missingKey -> new ReentrantLock());
        autoCloseable = new AutoCloseableLock(this);
    }

    private synchronized void close() throws IllegalMonitorStateException {
        lock.unlock();
        // Clear the reference to the key so the GC can remove the entry from the WeakHashMap if no-one else has it
        if (!lock.isHeldByCurrentThread()) {
            key = null;
        }
    }

    private synchronized void checkNotReleased() throws IllegalStateException {
        if (key == null) {
            throw new IllegalStateException("Lock has already been released");
        }
    }

    /**
     * Acquires the lock, blocking indefinitely.
     *
     * @return An AutoCloseableLock
     * @throws IllegalStateException if this lock has already been released by the last thread to hold it
     */
    @SuppressWarnings( "LockAcquiredButNotSafelyReleased" )
    public AutoCloseableLock lock() throws IllegalStateException {
        checkNotReleased();
        lock.lock();
        return autoCloseable;
    }

    /**
     * Tries to acquire the lock, returning immediately.
     *
     * @return An AutoCloseableLock if the lock was required, otherwise empty.
     * @throws IllegalStateException if this lock has already been released by the last thread to hold it
     */
    public Optional<AutoCloseableLock> tryLock() {
        checkNotReleased();
        if (lock.tryLock()) {
            return Optional.of(autoCloseable);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Acquires the lock, blocking until the lock is acquired or the thread is interrupted.
     *
     * @return An AutoCloseableLock
     * @throws InterruptedException  if the thread was interrupted before the lock could be acquired
     * @throws IllegalStateException if this lock has already been released by the last thread to hold it
     */
    @SuppressWarnings( "LockAcquiredButNotSafelyReleased" )
    public AutoCloseableLock lockInterruptibly() throws InterruptedException, IllegalStateException {
        checkNotReleased();
        lock.lockInterruptibly();
        return autoCloseable;
    }

    /**
     * Queries if this lock is held by the current thread.
     *
     * @return {@code true} if current thread holds this lock and {@code false} otherwise
     * @see ReentrantLock#isHeldByCurrentThread()
     */
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Queries if this lock is held by any thread. This method is
     * designed for use in monitoring of the system state,
     * not for synchronization control.
     *
     * @return {@code true} if any thread holds this lock and {@code false} otherwise
     * @see ReentrantLock#isLocked()
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    public static final class AutoCloseableLock implements AutoCloseable {

        private final AutoCloseableReentrantLock lock;

        private AutoCloseableLock(final AutoCloseableReentrantLock lock) {
            this.lock = lock;
        }

        /**
         * Releases the lock.
         *
         * @throws IllegalMonitorStateException if the current thread does not hold the lock.
         */
        @Override
        public void close() throws IllegalMonitorStateException {
            lock.close();
        }
    }

}
