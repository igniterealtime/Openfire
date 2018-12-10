package org.jivesoftware.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AutoCloseableReentrantLock implements QuietAutoCloseable {

    private static final Map<String, ReentrantLock> LOCK_MAP = Collections.synchronizedMap(new WeakHashMap<>());
    private String key;

    public AutoCloseableReentrantLock(final Class clazz, final String part) {
        this.key = clazz.getName() + '#' + part;
        LOCK_MAP.computeIfAbsent(key, missingKey -> new ReentrantLock()).lock();
    }

    @Override
    public synchronized void close() {
        // The contract does not state the close() should be idempotent, but it's probably useful
        if (key != null) {
            final ReentrantLock lock = LOCK_MAP.get(key);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
                // Finally, clear the key so the GC can remove the entry from the WeakHashMap
                key = null;
            }
        }

    }
}
