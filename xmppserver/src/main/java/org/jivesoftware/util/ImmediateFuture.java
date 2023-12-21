/*
 * Copyright (C) 2017-2018 Ignite Realtime Foundation. All rights reserved.
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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A Future that returns immediately.
 *
 * @param <T> The type of return value
 */
public class ImmediateFuture<T> implements Future<T> {

    private final T value;

    /**
     * Creates a Future that returns null immediately
     */
    public ImmediateFuture() {
        this(null);
    }

    /**
     * Creates a Future that returns the supplied value immediately
     *
     * @param value the value to return
     */
    public ImmediateFuture(final T value) {
        this.value = value;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public T get(final long timeout, final TimeUnit unit) {
        return value;
    }
}
