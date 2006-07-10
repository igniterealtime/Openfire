package org.jivesoftware.wildfire.gateway;


/**
 * AbstractGatewaySession provides an abstract implementation of 
 * <code>GatewaySession</code> that implements the some of the core responsibilties
 * of a GatewaySession.  This includes: handling registration and endpoint 
 * management. 
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public abstract class AbstractGatewaySession implements GatewaySession, Endpoint {

    /**
     * Construct an a gateway session.
     * 
     * @param info the <code>SubscriptionInfo</code> for this session.
     * @param gateway the <code>Gateway</code> that constructed this session.
     */
    protected AbstractGatewaySession(SubscriptionInfo info, Gateway gateway) {
        this.gateway = gateway;
        this.subscription = info;
    }
    
    /**
     * The gateway.
     */
    protected transient Gateway gateway;
    
    /**
     * Has the client registered with the gateway?
     */
    public boolean clientRegistered;
    
    /**
     * Has the server attempted to register with the client?
     */
    public boolean serverRegistered;
    
    /**
     * The subscriptionInfo.
     * @see org.jivesoftware.wildfire.gateway.SubscriptionInfo
     */
    private final SubscriptionInfo subscription;
    
    /**
     * The jabber endpoint.
     * @see org.jivesoftware.wildfire.gateway.Endpoint
     */
    private Endpoint jabberEndpoint;
    
    /**
     * Set the Jabber <code>Endpoint</code>.
     * 
     * @see org.jivesoftware.wildfire.gateway.GatewaySession#setJabberEndpoint(org.jivesoftware.wildfire.gateway.Endpoint)
     */
    public void setJabberEndpoint(Endpoint jabberEndpoint) {
        this.jabberEndpoint = jabberEndpoint;
    }
    
    /**
     * Return the jabber <code>Endpoint</code>.
     * 
     * @return endpoint The jabber endpoint.
     * @see org.jivesoftware.wildfire.gateway.Endpoint
     */
    public Endpoint getJabberEndpoint() {
        return jabberEndpoint;
    }
    
    /**
     * Return the legacy <code>Endpoint</code>.
     * 
     * @return endpoint The legacy endpoint.
     * @see org.jivesoftware.wildfire.gateway.Endpoint
     */
    public Endpoint getLegacyEndpoint() {
        return this;
    }    
    
    /**
     * Return the <code>SubscriptionInfo</code>
     * 
     * @return subscriptionInfo the <code>SubscriptionInfo</code> associated 
     * this session.
     * @see org.jivesoftware.wildfire.gateway.SubscriptionInfo
     */
    public SubscriptionInfo getSubscriptionInfo() {
        return this.subscription;
    }
    
    /**
     * Return the gateway associated with this session.
     * 
     * @return gateway The gateway.
     * @see org.jivesoftware.wildfire.gateway.Gateway
     */
    public Gateway getGateway() {
        return this.gateway;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#getValve()
     */
    public EndpointValve getValve() {
       return this.jabberEndpoint.getValve();
    }
}
