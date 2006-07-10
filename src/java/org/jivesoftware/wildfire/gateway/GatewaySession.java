package org.jivesoftware.wildfire.gateway;

import java.util.List;

import org.jivesoftware.wildfire.gateway.roster.ForeignContact;
import org.jivesoftware.wildfire.gateway.roster.UnknownForeignContactException;
import org.xmpp.packet.JID;

/**
 * GatewaySession provides an interface that legacy gateways need to implement.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public interface GatewaySession {

    /**
     * Logout from the underlying gateway session.
     * @throws Exception
     */
    public void logout() throws Exception;

    /**
     * Login to the underlying gateway session.
     * @throws Exception
     */
    public void login() throws Exception;

    /**
     * Momento of this session, so it can be restablished later without the
     * need for prompting the user for their credentials.
     * 
     * @return SubscriptionInfo the subscription information for this session.
     */
    public SubscriptionInfo getSubscriptionInfo();

    /**
     * Is the session connected?
     * 
     * @return boolean
     */
    public boolean isConnected();
    
    /**
     * Returns all sessions associated with this session/login.
     *  
     * @return contacts A list of <code>String</code>s.
     * @see java.util.List
     */
    public List<ForeignContact> getContacts();
    
    /**
     * Return the endpoint for the legacy system.
     * 
     * @see org.jivesoftware.wildfire.gateway.Endpoint 
     * @return Endpoint legacy endpoint.
     */
    public Endpoint getLegacyEndpoint();
    
    /**
     * Get the Jabber endpoint.
     * 
     * @see org.jivesoftware.wildfire.gateway.Endpoint
     * @return Endpoint the jabber endpoint.
     */
    public Endpoint getJabberEndpoint();

    /**
     * JID associated with this session.
     * 
     * @return jid The jid for this session.
     * @see org.xmpp.packet.JID
     */
    public JID getJID();

    /**
     * Status for a particular contact.
     * 
     * @param id The id of the contact of interest.
     * @return status The status for the particular JID.
     */
    public String getStatus(JID id);
    
    /**
     * Add a contact to this session.  This method will typically update the 
     * roster on the legacy system.
     * 
     * @param jid
     * @throws Exception If add fails.
     * @see org.xmpp.packet.JID
     */
    public void addContact(JID jid) throws Exception;
    
    /**
     * Remove a contact from this session.  This will typically update the 
     * roster on the legacy system.
     * 
     * @param jid
     * @throws Exception If remove fails.
     * @see org.xmpp.packet.JID
     */
    public void removeContact(JID jid) throws Exception;
    
    /**
     * Sets the XMPP Server endpoint.
     * 
     * @param jabberEndpoint
     * @see org.jivesoftware.wildfire.gateway.Endpoint
     */
    public void setJabberEndpoint(Endpoint jabberEndpoint);
    
    /**
     * Get the gateway that is associated with this session.  Every session 
     * has an orinating gateway from which is was created.
     * 
     * @return gateway The underlying gateway for this sessin.
     * @see org.jivesoftware.wildfire.gateway.Gateway
     */
    public Gateway getGateway();

    
    /**
     * The session will return a foreign contact identified by the JID.  If it
     * does not exist then an exception will be thrown.  The Session is responsible
     * for contacting the gateway to perform any name resolution (if it cannot
     * perform it from the JID).
     * 
     * @param to The JID of the contact to locate.
     * @return foreignContact The ForeignContact object that represents this JID.
     * @throws UnknownForeignContactException 
     */
    public ForeignContact getContact(JID to) throws UnknownForeignContactException;
}
