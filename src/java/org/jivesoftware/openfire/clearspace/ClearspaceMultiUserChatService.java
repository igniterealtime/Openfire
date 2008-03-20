/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.dom4j.Element;
import org.xmpp.packet.JID;

import java.util.Iterator;
import java.util.ArrayList;

/**
 * This ia an extension of a standard MultiUserChatService implementation that accounts for
 * Clearspace interactions, restrictions, and special rules.
 *
 * @author Daniel Henninger
 */
public class ClearspaceMultiUserChatService extends MultiUserChatServiceImpl implements MultiUserChatService {

    /**
     * Create a new clearspace group chat service.
     *
     * @param subdomain   Subdomain portion of the conference services (for example, conference for conference.example.org)
     * @param description Short description of service for disco and such.
     */
    public ClearspaceMultiUserChatService(String subdomain, String description) {
        super(subdomain, description);
        // TODO: enable when CS MUC works (should create a protected setter method)
        //mucEventDelegate = new ClearspaceMUCEventDelegate();
    }

    @Override
    public void enableService(boolean enabled, boolean persistent) {
        // Ignore
    }

    @Override
    public boolean isServiceEnabled() {
        return true;
    }

    @Override
    public boolean isServicePrivate() {
        return true;
    }

    @Override
    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        Iterator identitiesIterator = super.getIdentities(name, node, senderJID);
        // TODO: Add custom identities
        return identitiesIterator;
    }

}
