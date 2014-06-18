package org.jivesoftware.util.property;

import org.jivesoftware.util.JiveGlobals;

public final class BooleanProperty extends Property<Boolean> {

    BooleanProperty(String propertyKey, Boolean defaultValue) {
        super(propertyKey, defaultValue);
    }

    @Override
    public Boolean get() {
        return JiveGlobals.getBooleanProperty(key, defaultValue);
    }
}
