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
    String name();
    SourceType type() default SourceType.COUNTER;
    long heartbeat() default 600;
    double minValue() default Double.MIN_VALUE;
    double maxValue() default Double.MAX_VALUE;
    String expr() default "";
}
