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
