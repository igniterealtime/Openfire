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
 * An enumeration for different presence type.
 *
 * This is combined into a single variable, which is differnet than the
 * typical situation of having to determine the status by looking at both
 * the presence stanza's type and show fields.
 *
 * @author Daniel Henninger
 */
public enum PresenceType {

    /**
     * Available (aka online)
     */
    available,

    /**
     * Away
     */
    away,

    /**
     * XA (extended away)
     */
    xa,

    /**
     * DND (do not disturb)
     */
    dnd,

    /**
     * Chat (free to chat)
     */
    chat,

    /**
     * Unavailable (offline)
     */
    unavailable,

    /**
     * Unknown
     */
    unknown

}
