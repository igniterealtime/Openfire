/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.util.Map;

/**
 * @author Noah Campbell
 * @version 1.0
 */
interface AccumulatorHelper {

    
    /**
     * <code>getResults</code> returns a map of datasource name and the corresponding
     * value for the paticular Accumulator.  This is a help method to make the
     * extract of an arbitrary accumulator more easily assasible.
     * 
     * @return map A map of the results.
     */
    Map<String, Number> getResults();
}
