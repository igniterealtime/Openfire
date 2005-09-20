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
@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.METHOD})
public @interface Ds {
    /**
     * Name is the name you will use to reference this particular data source 
     * from an RRD. A ds-name must be 1 to 19 characters long in the 
     * characters [a-zA-Z0-9_].
     * @return name
     */
    String name();
    
    /**
     * <em>type</em> defines the Data Source Type. See the section on
     * ``How to Measure'' below for further insight. The Datasource Type must
     * be one of the following:</p>
     * <dl>
     * 
     * @return sourceType
     */
    SourceType type() default SourceType.COUNTER;
    
    /**
     * heartbeat defines the maximum number of seconds that may pass between two 
     * updates of this data source before the value of the data source is 
     * assumed to be *UNKNOWN*.
     */
    long heartbeat() default 600;
    
    /**
     * min and max are optional entries defining the expected range of the data 
     * supplied by this data source. If min and/or max are defined, any value 
     * outside the defined range will be regarded as *UNKNOWN*. If you do not 
     * know or care about min and max, set them to U for unknown. 
     * Note that min and max always refer to the processed values of the DS. 
     * For a traffic-COUNTER type DS this would be the max and min data-rate 
     * expected from the device.
     */    
    double minValue() default Double.MIN_VALUE;
    /**
     * @see #minValue()
     * @return maxValue
     */
    double maxValue() default Double.MAX_VALUE;
    
    /**
     * A XPath expression that defines how the data is collected from the object
     * model.  This is bean notion with a few exceptions.  The attributes from
     * the mbean server are upper case for the first character (as opposed to 
     * first letter lower case.  Composite data are the exact string value as
     * they are stored.
     * 
     * For example: MBeanAttribute/composite/foo/bar/baz would lookup the attribute
     * named MBeanAttribute (invoking the getAttribute on the server).  Composite
     * would call CompositeData.getField(<i>literal</i>).  If the field is an object
     * than the object graph is traversed with the rules defined in JXPath.
     * 
     * The result should always be a number (short, long, double, float and the
     * appropriate Object types).  If it's not a number, 0 is returned.
     * 
     * If no expression is given, then it's assumed that the name of the data source
     * is the attribute on the ObjectName that it references.
     * 
     * @see "JXPath"
     * @return expression
     */
    String expr() default "";
}
