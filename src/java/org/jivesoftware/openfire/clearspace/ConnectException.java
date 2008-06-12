package org.jivesoftware.openfire.clearspace;

/**
 * Thrown when an exception occurs connecting to CS.
 */
public class ConnectException extends Exception {

    public ConnectException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ConnectException(Throwable throwable) {
        super(throwable);
    }
}
