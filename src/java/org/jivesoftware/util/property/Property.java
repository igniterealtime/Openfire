package org.jivesoftware.util.property;

import org.jivesoftware.util.JiveGlobals;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public abstract class Property<T> {

    protected final String key;

    protected final T defaultValue;

    protected final PropertyChangeSupport propChange = new PropertyChangeSupport( this );

    public Property(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public abstract T get();

    public void set(T value) {
        final T oldValue = get();
        JiveGlobals.setProperty(key, String.valueOf(value));
        propChange.firePropertyChange(key, oldValue, value);
    }

    public void delete() {
        JiveGlobals.deleteProperty(key);
    }

    public void reset() {
        set(defaultValue);
    }

    public String getKey() {
        return key;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        propChange.addPropertyChangeListener(key, listener);
    }

    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        propChange.removePropertyChangeListener(key, listener);
    }

    public String toString() {
        return key + " = " + String.valueOf(get());
    }

    /*
     * Builder
     */

    public static BooleanProperty of(String propertyKey, boolean defaultValue) {
        return new BooleanProperty(propertyKey, defaultValue);
    }

    public static StringProperty of(String propertyKey, String defaultValue) {
        return new StringProperty(propertyKey, defaultValue);
    }

    public static IntegerProperty of(String propertyKey, int defaultValue) {
        return new IntegerProperty(propertyKey, defaultValue);
    }

    public static <E extends Enum<E>> EnumProperty<E> of(String propertyKey, E defaultValue) {
        return new EnumProperty<>(propertyKey, defaultValue);
    }
}

