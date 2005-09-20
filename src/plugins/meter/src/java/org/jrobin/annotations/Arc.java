/**
 * 
 */
package org.jrobin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Noah Campbell
 * @version 1.0
 */
@Target(value=ElementType.TYPE)
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Arc {
    /**
     * The data is also consolidated with the consolidation function (CF) of the 
     * archive. The following consolidation functions are defined: 
     * AVERAGE, MIN, MAX, LAST.
     * @return confun The standard consolidation functions (AVERAGE, MIN, MAX, LAST) for the archive.
     */
    ConsolidationFunction consolidationFunction();
    
    /**
     * xff The xfiles factor defines what part of a consolidation interval may 
     * be made up from *UNKNOWN* data while the consolidated value is still 
     * regarded as known.
     *  
     * @return xff
     */
    double xff();
    
    /**
     * steps defines how many of these primary data points are used to build a 
     * consolidated data point which then goes into the archive.
     * @return steps
     */
    int steps();
    
    /**
     * rows defines how many generations of data values are kept in an RRA.
     * @return rows
     */
    int rows();
}
