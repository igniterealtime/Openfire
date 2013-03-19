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
 * An enumeration for supported features.
 * 
 * This represents features that the client supports that we have detected and
 * can make use of.
 * 
 * @author Daniel Henninger
 */
public enum SupportedFeature {

    /**
     * Chat States (XEP-0085)
     */
    chatstates("http://jabber.org/protocol/chatstates"),

    /**
     * Attention (XEP-0224)
     */
    attention("urn:xmpp:attention:0"),
    
    /**
     * VCard-temp (XEP-0054)
     */
    vcardtemp("vcard-temp");
    
    /**
     * disco#info feature identifier.
     */
    private final String var;

    /**
     * Instantiates a new SupportedFeature enum item, including the feature
     * identifier as used by service discovery information queries (XEP-0030).
     * 
     * @param var
     *            disco#info feature identifier.
     */
    private SupportedFeature(String var) {
        this.var = var;
    }

    /**
     * Returns the feature identifier as used by service discovery information
     * queries (XEP-0030).
     * 
     * @return disco#info feature identifier.
     */
    public String getVar() {
        return var;
    }
}
