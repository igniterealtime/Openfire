/**
 * 
 */
package org.jrobin.annotations;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public enum SourceType {
    /**
     * COUNTER
     */
    COUNTER, 
    
    /**
     * GAUGE
     */
    GAUGE, 
    
    /** 
     * The DERIVE. 
     */
    DERIVE, 
    
    /** 
     * The ABSOLUTE. 
     */
    ABSOLUTE;
}
