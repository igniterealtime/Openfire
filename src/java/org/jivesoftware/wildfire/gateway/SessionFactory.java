package org.jivesoftware.wildfire.gateway;

/**
 * {@code SessionFactory} is used to generate a new {@code GatewaySession}.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public interface SessionFactory {
    
    /**
     * Return a new instance of a {@code GatewaySession}.
     * 
     * @param info The subscription information for the session.
     * @return gatewaySession The gateway session.
     */
    public GatewaySession newInstance(SubscriptionInfo info);
}
