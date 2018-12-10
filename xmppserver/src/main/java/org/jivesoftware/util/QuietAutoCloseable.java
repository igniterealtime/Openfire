package org.jivesoftware.util;

/**
 * This class overrides {@link AutoCloseable#close()} so that it can be used without having to catch the {@link Exception}
 * that it throws.
 */
public interface QuietAutoCloseable extends AutoCloseable {

    @Override
    void close();
}
