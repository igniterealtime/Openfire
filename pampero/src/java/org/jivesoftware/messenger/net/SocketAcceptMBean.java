/**
 * Copyright Noah Campbell 2005
 */
package org.jivesoftware.messenger.net;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public interface SocketAcceptMBean {

    
    /**
     * Return the total number of times this socket has accepted an incoming 
     * socket since the start of the JVM.
     * 
     * @return acceptCount
     */
    long getAcceptCount();
}
