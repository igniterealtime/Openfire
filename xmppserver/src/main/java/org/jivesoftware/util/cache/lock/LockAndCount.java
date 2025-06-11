package org.jivesoftware.util.cache.lock;

import java.util.concurrent.locks.ReentrantLock;

class LockAndCount
{
    final ReentrantLock lock;
    int count;

    LockAndCount(ReentrantLock lock)
    {
        this.lock = lock;
    }
}
