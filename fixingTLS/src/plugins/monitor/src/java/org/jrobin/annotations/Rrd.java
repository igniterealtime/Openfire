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
    long step() default 300;
    Class override() default None.class;
}
