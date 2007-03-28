/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

/**
 * Transport Buddy.
 *
 * This is simply a means for providing a collection of information about
 * a legacy service (transport) buddy to the underlying system.  It collects
 * all of the necessary pieces into one object.
 *
 * @author Daniel Henninger
 */
public class TransportBuddy {

    /**
     * Creates a TransportBuddy instance.
     *
     * @param contactname The legacy contact name.
     * @param nickname The legacy nickname (can be null).
     * @param group The group the legacy contact is in (can be null).
     */
    public TransportBuddy(String contactname, String nickname, String group) {
        this.contactname = contactname.toLowerCase();
        this.nickname = nickname;
        this.group = group;
    }

    /**
     * ID, Screenname, name, whatever the contact name is on the legacy system
     */
    public String contactname = null;

    /**
     * A nickname associated with this contact, if it exists.
     */
    public String nickname = null;

    /**
     * A group associated with this contact, if it exists.
     */
    public String group = null;

    /**
     * Retrieves the name of the contact.
     *
     * @return Name of contact.
     */
    public String getName() {
       return contactname;
    }

    /**
     * Retrieves the nickname of the contact.
     *
     * @return Nickname of contact.
     */
    public String getNickname() {
       return nickname;
    }

    /**
     * Retrieves the group of the contact.
     *
     * @return Group contact is in.
     */
    public String getGroup() {
       return group;
    }

}
