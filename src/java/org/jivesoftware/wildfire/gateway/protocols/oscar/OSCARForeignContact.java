/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import org.jivesoftware.util.Log;

import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.roster.AbstractForeignContact;
import org.jivesoftware.wildfire.gateway.roster.Status;

import net.kano.joscar.snaccmd.ssi.*;
import net.kano.joscar.ssiitem.*;

/**
 * @author Daniel Henninger
 */
public class OSCARForeignContact extends AbstractForeignContact {

    private BuddyItem ssiItem;
    private String oscarStatus = "offline";
    
    public OSCARForeignContact(BuddyItem ssiItem, Gateway gateway) {
        super(ssiItem.getScreenname(), new Status(), gateway);
        this.ssiItem = ssiItem;
    }

    public Status getStatus() {
        getStatusMessage(ssiItem, this.status);
        return super.status;
    }

    public void setStatus(String oscarStatus) {
        this.oscarStatus = oscarStatus;
    }

    private Status getStatusMessage(BuddyItem issiItem, Status status) {
        status.setOnline(true);
        // We need to check other statuses here, keep track of them somehow.
        return status;
    }
    
    public String getName() {
        return ssiItem.getScreenname();
    }

    public SsiItem getSSIItem() {
        return ssiItem.toSsiItem();
    }

}
