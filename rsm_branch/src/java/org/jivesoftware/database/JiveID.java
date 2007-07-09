package org.jivesoftware.database;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Used to specify what jive id an object should have
 *
 * @author Andrew Wright
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JiveID {

    /**
     * should return the int type for this object
     */
    int value();
}
