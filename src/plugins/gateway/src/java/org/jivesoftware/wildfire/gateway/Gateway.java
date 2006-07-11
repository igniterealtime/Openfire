/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.xmpp.packet.JID;

/**
 * A gateway to an external or legacy messaging system.
 * 
 * @author ncampbell
 * @version 1.0
 */
public interface Gateway extends Endpoint {

    /**
     * Return the name, or node, of the gateway.  This should comply with JID
     * Node naming coventions
     * @return Name The gateway name (JID Node)
     */
	public String getName();

    /**
     * Sets the name, or node, of the gateway.  This should comply with JID
     * Node naming coventions
     */
	public void setName(String newname);

    /**
     * A textual description of the gateway
     * @return description
     */
	public String getDescription();

    /**
     * The domain name
     * @return domain
     */
	public String getDomain();
	
    /**
     * Lookup a contact name for the JID
     * @param jid The jabber id
     * @return contact The legacy name
     */
	public String whois(JID jid);
	
    /**
     * Lookup a JID for a legacy contact name
     * @param contact The name of legacy contact
     * @return JID
     */
	public JID whois(String contact);
	
    /**
     * The JID of the gateway
     * @return JID
     */
    public JID getJID();
    
    /**
     * Return the session gateway for this gateway
     * @return SessionFactory
     */
    public SessionFactory getSessionFactory();
}
