package org.jivesoftware.util.property;

import org.jivesoftware.util.JiveGlobals;

public final class StringProperty extends Property<String> {
    StringProperty(String propertyKey, String defaultValue) {
        super(propertyKey, defaultValue);
    }

    @Override
    public String get() {
        return JiveGlobals.getProperty(key, defaultValue);
    }
}
