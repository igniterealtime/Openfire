/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.type;

/**
 * An enumeration for the valid transport type, which encompasses proprietary IM networks
 * as well as other IM protocols.  For a list of official name/types for legacy networks,
 * see: http://www.xmpp.org/registrar/disco-categories.html#gateway
 *
 * Each transport maps to the official disco identity of each transport.
 *
 * Listing here does not imply that an actual transport exists for an particular service.
 *
 * @author Matt Tucker
 * @author Daniel Henninger
 */
public enum TransportType {

    /**
     * The AOL instant messaging service.
     */
    aim ("aim"),

    /**
     * The Facebook instant messaging service.
     */
    facebook ("facebook"),

    /**
     * The Gadu-Gadu instant messaging service.
     */
    gadugadu ("gadu-gadu"),

    /**
     * Google Talk (a 'special' XMPP server)
     */
    gtalk ("xmpp"),

    /**
     * HTTP web service.
     */
    httpws ("http-ws"),

    /**
     * The ICQ instant messaging service.
     */
    icq ("icq"),

    /**
     * IRC services. (not in the official spec)
     */
    irc ("irc"),

    /**
     * Microsoft Live Communications service.
     */
    lcs ("lcs"),

    /**
     * Live Journal (a 'special' XMPP server)
     */
    livejournal ("xmpp"),

    /**
     * The MSN instant messaging service.
     */
    msn ("msn"),
    
    /**
     * MySpace IM service.
     */
    myspaceim ("myspaceim"),

    /**
     * Microsoft Office Communications service.
     */
    ocs ("ocs"),

    /**
     * The QQ instant messaging service.
     */
    qq ("qq"),

    /**
     * RenRen (a 'special' XMPP server)
     */
    renren ("xmpp"),

    /**
     * IBM Lotus sametime service.
     */
    sametime ("sametime"),

    /**
     * SIP/SIMPLE servers.
     */
    simple ("simple"),

    /**
     * Short message service.
     */
    sms ("sms"),

    /**
     * SMTP (email) service.
     */
    smtp ("smtp"),

    /**
     * Tlen IM service.
     */
    tlen ("tlen"),

    /**
     * XFire gaming and IM service.
     */
    xfire ("xfire"),

    /**
     * A gateway to other XMPP servers.
     */
    xmpp ("xmpp"),

    /**
     * The Yahoo instant messaging service.
     */
    yahoo ("yahoo"),

    /**
     * A gateway to a service not covered by the other options..
     */
    other ("unknown");

    private final String discoIdentity;
    TransportType(String discoIdentity) {
        this.discoIdentity = discoIdentity;
    }
    public String discoIdentity() { return discoIdentity; }

}
