package org.jivesoftware.util.property;

import org.jivesoftware.util.JiveGlobals;

public class IntegerProperty extends Property<Integer> {

    IntegerProperty(String propertyKey, Integer defaultValue) {
        super(propertyKey, defaultValue);
    }

    @Override
    public Integer get() {
        return JiveGlobals.getIntProperty(key, defaultValue);
    }
}
