/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;

import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.roster.AbstractForeignContact;
import org.jivesoftware.wildfire.gateway.roster.Status;

import ymsg.network.StatusConstants;
import ymsg.network.YahooUser;

/**
 * @author Noah Campbell
 */
public class YahooForeignContact extends AbstractForeignContact {

    /** The yahoo user. */
    final private YahooUser user;

    /**
     * Construct a new <code>YahooForeignContact</code>.
     * @param user A YahooUser
     * @param gateway
     * @see YahooUser
     */
    public YahooForeignContact(YahooUser user, Gateway gateway) {
        super(user.getId(), new Status(), gateway);
        this.user = user;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.roster.ForeignContact#getStatus()
     */
    public Status getStatus() {
        getStatusMessage(user, this.status);
        return super.status;
        
    }

    /**
     * @param user2
     * @return
     */
    private Status getStatusMessage(YahooUser user2, Status status) {
        long id = user2.getStatus();
        status.setOnline(true);
        if (StatusConstants.STATUS_AVAILABLE == id) {
            status.updateValue(resource.getString("STATUS_AVAILABLE"));
        }
        else if (StatusConstants.STATUS_BRB == id) {
            status.updateValue(resource.getString("STATUS_BRB"));
        }
        else if (StatusConstants.STATUS_BUSY == id) {
            status.updateValue(resource.getString("STATUS_BUSY"));
        }
        else if (StatusConstants.STATUS_NOTATHOME == id) {
            status.updateValue(resource.getString("STATUS_NOTATHOME"));
        }
        else if (StatusConstants.STATUS_NOTATDESK == id) {
            status.updateValue(resource.getString("STATUS_NOTATDESK"));
        }
        else if (StatusConstants.STATUS_NOTINOFFICE == id) {
            status.updateValue(resource.getString("STATUS_NOTINOFFICE"));
        }
        else if (StatusConstants.STATUS_ONPHONE == id) {
            status.updateValue(resource.getString("STATUS_ONPHONE"));
        }
        else if (StatusConstants.STATUS_ONVACATION == id) {
            status.updateValue(resource.getString("STATUS_ONVACATION"));
        }
        else if (StatusConstants.STATUS_OUTTOLUNCH == id) {
            status.updateValue(resource.getString("STATUS_OUTTOLUNCH"));
        }
        else if (StatusConstants.STATUS_STEPPEDOUT == id) {
            status.updateValue(resource.getString("STATUS_STEPPEDOUT"));
        }
        else if (StatusConstants.STATUS_INVISIBLE == id) {
            status.updateValue(resource.getString("STATUS_INVISIBLE"));
        }
        else if (StatusConstants.STATUS_BAD == id) {
            status.updateValue(resource.getString("STATUS_BAD")); // Bad login?
        }
        else if (StatusConstants.STATUS_LOCKED == id) {
            status.updateValue(resource.getString("STATUS_LOCKED")); // You've been naughty
        }
        else if (StatusConstants.STATUS_CUSTOM == id) {
            status.updateValue(user.getCustomStatusMessage());
        }
        else if (StatusConstants.STATUS_IDLE == id) {
            status.updateValue(resource.getString("STATUS_IDLE"));
        }
        else if (StatusConstants.STATUS_OFFLINE == id) {
            status.setOnline(false);
        }
        else if (StatusConstants.STATUS_TYPING == id) {
            status.updateValue(resource.getString("STATUS_TYPING"));
        }
        else {
            Log.warn(LocaleUtils.getLocalizedString("yahooforeigncontact.unabletolocatestatus", "gateway"));
            status.updateValue("????");
        }

        return status;
    }

    /** The resource. */
    private static ResourceBundle resource = PropertyResourceBundle.getBundle("yahoostatus");

    /**
     * @see org.jivesoftware.wildfire.gateway.roster.ForeignContact#getName()
     */
    public String getName() {
        return user.getId();
    }

}
