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
public @interface Rrd {
    /**
     * RRD gets fed samples at arbitrary times. From these it builds Primary 
     * Data Points (PDPs) at exact times every ``step'' interval. The PDPs are 
     * then accumulated into RRAs.
     * 
     * <b>Note:</b> Changing this value from its default of 300 will impact the
     * storage of the default archives.
     * 
     * @return steps Number of seconds between expected reads. 
     */
    long step() default 300;
    
    /**
     * The class that this Rrd overrides.
     * @return cls
     */
    Class override() default None.class;
    
    /**
     * Archives to be associated with this rrd, beyond the Ds defined.
     * @return archives The standard type of archives.
     * 
     * This will be explained later on. The time in-between samples is 300 
     * seconds, a good starting point, which is the same as five minutes.
     *
     * 1 sample "averaged" stays 1 period of 5 minutes
     * 6 samples averaged become one average on 30 minutes
     * 24 samples averaged become one average on 2 hours
     * 288 samples averaged become one average on 1 day
     * 
     * <b>Note:</b> Changing the default step will have an impact on the archives that are 
     * created.
     */
    Arc[] archives() default {@Arc(consolidationFunction=ConsolidationFunction.AVERAGE, xff=0.5, steps=1, rows=600),
        @Arc(consolidationFunction=ConsolidationFunction.AVERAGE, xff=0.5, steps=5, rows=700),
        @Arc(consolidationFunction=ConsolidationFunction.AVERAGE, xff=0.5, steps=24, rows=775),
        @Arc(consolidationFunction=ConsolidationFunction.AVERAGE, xff=0.5, steps=288, rows=797),
        @Arc(consolidationFunction=ConsolidationFunction.MAX, xff=0.5, steps=1, rows=600),
        @Arc(consolidationFunction=ConsolidationFunction.MAX, xff=0.5, steps=5, rows=700),
        @Arc(consolidationFunction=ConsolidationFunction.MAX, xff=0.5, steps=24, rows=775),
        @Arc(consolidationFunction=ConsolidationFunction.MAX, xff=0.5, steps=288, rows=797),
        @Arc(consolidationFunction=ConsolidationFunction.MIN, xff=0.5, steps=1, rows=600),
        @Arc(consolidationFunction=ConsolidationFunction.MIN, xff=0.5, steps=5, rows=700),
        @Arc(consolidationFunction=ConsolidationFunction.MIN, xff=0.5, steps=24, rows=775),
        @Arc(consolidationFunction=ConsolidationFunction.MIN, xff=0.5, steps=288, rows=797)};
}
