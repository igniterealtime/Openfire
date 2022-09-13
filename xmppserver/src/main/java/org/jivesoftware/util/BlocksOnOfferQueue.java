/*
 * Copyright (C) 2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.util;

import javax.annotation.Nonnull;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A LinkedBlockingQueue of which the {@link #offer(Object)} method blocks, instead of immediately returning.
 *
 * This class is designed to be used as a queue for ThreadPoolExecutors that wish to slow down the producing threads
 * when the queue is reaching capacity, and where {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}
 * cannot be used (for example, because it would cause rejected tasks to be executed _before_ already queued tasks).
 *
 * Note that the lock used to guard access in {@link LinkedBlockingQueue#offer(Object, long, TimeUnit)}, which is used
 * by this implementation of {@link #offer(Object)}, uses an unfair lock. No strict ordering of execution of produced
 * tasks can be guaranteed.
 *
 * @param <E>
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class BlocksOnOfferQueue<E> extends LinkedBlockingQueue<E>
{
    public BlocksOnOfferQueue(int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(@Nonnull E e) {
        try {
            return super.offer(e, 999, TimeUnit.DAYS); // 'indefinitely'.
        } catch (InterruptedException ex) {
            return false;
        }
    }
}
