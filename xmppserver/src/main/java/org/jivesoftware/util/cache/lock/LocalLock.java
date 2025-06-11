package org.jivesoftware.util.cache.lock;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.jivesoftware.util.cache.Cache;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LocalLock implements Lock
{
    /**
     * Keep track of the locks that are currently being used.
     */
    private static final Map<CacheKey, LockAndCount> locks = new ConcurrentHashMap<>();

    private static final Interner<CacheKey> interner = Interners.newWeakInterner();

    private final CacheKey key;

    public static LocalLock getLock(Object key, Cache cache) {
        return new LocalLock(new CacheKey(cache, key));
    }

    LocalLock(CacheKey key)
    {
        this.key = key;
    }

    @Override
    public void lock()
    {
        acquireLock(key);
    }

    @Override
    public void unlock()
    {
        releaseLock(key);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException
    {
        ReentrantLock lock = lookupLockForAcquire(key);
        lock.lockInterruptibly();
    }

    @Nonnull
    @Override
    public Condition newCondition()
    {
        ReentrantLock lock = lookupLockForAcquire(key);
        return lock.newCondition();
    }

    @Override
    public boolean tryLock()
    {
        ReentrantLock lock = lookupLockForAcquire(key);
        return lock.tryLock();
    }

    @Override
    public boolean tryLock(long time, @Nonnull TimeUnit unit) throws InterruptedException
    {
        ReentrantLock lock = lookupLockForAcquire(key);
        return lock.tryLock(time, unit);
    }

    @SuppressWarnings( "LockAcquiredButNotSafelyReleased" )
    private void acquireLock(CacheKey key) {
        ReentrantLock lock = lookupLockForAcquire(key);
        lock.lock();
    }

    private void releaseLock(CacheKey key) {
        ReentrantLock lock = lookupLockForRelease(key);
        lock.unlock();
    }

    private ReentrantLock lookupLockForAcquire(CacheKey cacheKey) {
        CacheKey mutex = interner.intern(cacheKey); // Ensure that the mutex used in the next line is the same for objects that are equal.
        synchronized(mutex) {
            LockAndCount lac = locks.get(mutex);
            if (lac == null) {
                lac = new LockAndCount(new ReentrantLock());
                lac.count = 1;
                locks.put(mutex, lac);
            }
            else {
                lac.count++;
            }

            return lac.lock;
        }
    }

    private ReentrantLock lookupLockForRelease(CacheKey cacheKey) {
        CacheKey mutex = interner.intern(cacheKey); // Ensure that the mutex used in the next line is the same for objects that are equal.
        synchronized(mutex) {
            LockAndCount lac = locks.get(mutex);
            if (lac == null) {
                throw new IllegalStateException("No lock found for object " + mutex);
            }

            if (lac.count <= 1) {
                locks.remove(mutex);
            }
            else {
                lac.count--;
            }

            return lac.lock;
        }
    }
}
