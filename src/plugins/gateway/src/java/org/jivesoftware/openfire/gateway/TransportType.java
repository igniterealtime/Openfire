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
 * An enumeration for the valid transport types, which encompasses proprietary IM networks
 * as well as other IM protocols.
 *
 * @author Matt Tucker
 */
public enum TransportType {

    /**
     * The AOL instant messaging service.
     */
    aim,

    /**
     * The ICQ instant messaging service.
     */
    icq,

    /**
     * The Yahoo instant messaging service.
     */
    yahoo,

    /**
     * The MSN instant messaging service.
     */
    msn,

    /**
     * A gateway to a SIP/SIMPLE servers.
     */
    sip,

    /**
     * A gateway to other XMPP servers.
     */
    xmpp,

    /**
     * A gateway to Google Talk ('special' XMPP server)
     */
    gtalk,

    /**
     * A gateway to IRC servers.
     */
    irc,

    /**
     * A gateway to a service not covered by the other options..
     */
    other

}
