/**
 * 
 */
package org.jivesoftware.wildfire.gateway.util;

import java.util.logging.Level;

/**
 * Custom log levels.
 * 
 * @author Noah Campbell
 * @version 1.0
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

    /** The serialVersionUID. */
    private static final long serialVersionUID = 1L;


}
