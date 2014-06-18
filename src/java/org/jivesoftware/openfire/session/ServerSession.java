package org.jivesoftware.openfire.session;

public interface ServerSession extends Session {
    /**
     * Returns true if this outgoing session was established using server dialback.
     *
     * @return true if this outgoing session was established using server dialback.
     */
    boolean isUsingServerDialback();    
}
