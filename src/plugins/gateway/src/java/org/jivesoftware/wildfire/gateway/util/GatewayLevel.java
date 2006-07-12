/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.util;

import java.util.logging.Level;

/**
 * Custom log levels.
 * 
 * @author Noah Campbell
 * @see java.util.logging.Level
 */
public class GatewayLevel extends Level {

    /** SECURITY log level. */
    public static final Level SECURITY = new GatewayLevel("SECURITY", 501);

    /**
     * Construct a new <code>GatewayLevel</code>.
     *
     * @param name
     * @param value
     */
    protected GatewayLevel(String name, int value) {
        super(name, value, "gateway_i18n");
    }

}
