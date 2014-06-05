package org.jivesoftware.util.property;

import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnumProperty<E extends Enum<E>> extends Property<E> {

    private static final Logger Log = LoggerFactory.getLogger(EnumProperty.class);

    private Class<E> enumType;

    EnumProperty(String propertyKey, E defaultValue) {
        super(propertyKey, defaultValue);
        this.enumType = defaultValue.getDeclaringClass();
    }

    @Override
    public E get() {
        try {
            return Enum.valueOf(enumType, JiveGlobals.getProperty(key, String.valueOf(defaultValue)));
        } catch (IllegalArgumentException e) {
            Log.error("Error parsing {}: " + e.getMessage(), key, e);
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return key + " = " + enumType.getSimpleName() + " : " + get();
    }
}
