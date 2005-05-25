package org.jivesoftware.database;

/**
 * Used to specify what jive id an object should have
 *
 * @author Andrew Wright
 */
public @interface JiveID {
    
    /**
     * should return the int type for this object
     */
    int value();
}
