package org.jivesoftware.util;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AutoCloseableReentrantLock implements QuietAutoCloseable {

    private static final Map<String, LockHolder> LOCK_MAP = Collections.synchronizedMap(new WeakHashMap<>());
    private String keyReference;

    public AutoCloseableReentrantLock(final Class clazz, final String part) {
        final String keyString = clazz.getName() + '#' + part;
        final LockHolder lockHolder = LOCK_MAP.computeIfAbsent(keyString, LockHolder::new);
        this.keyReference = lockHolder.key.get();
        lockHolder.lock.lock();
    }

    @Override
    public synchronized void close() {
        // The contract does not state the close() should be idempotent, but it's probably useful
        if (keyReference != null) {
            final ReentrantLock lock = LOCK_MAP.get(keyReference).lock;
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
                // Finally, clear the reference to the key so the GC can remove the entry from the WeakHashMap
                keyReference = null;
            }
        }
    }

    private static class LockHolder {
        private final WeakReference<String> key;
        private final ReentrantLock lock;

        private LockHolder(final String key) {
            this.key = new WeakReference<>(key);
            this.lock = new ReentrantLock();
        }
    }
}
