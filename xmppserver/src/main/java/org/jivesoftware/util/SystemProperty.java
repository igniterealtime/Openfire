package org.jivesoftware.util;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a system property - also accessible via {@link JiveGlobals}. The only way to create a SystemProperty object
 * is to use a {@link Builder}.
 *
 * @param <T> The type of system property. Can be a {@link String}, {@link Integer}, {@link Long}, {@link Boolean} or {@link Duration}.
 */
public final class SystemProperty<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemProperty.class);
    private static final Map<String, SystemProperty> PROPERTIES = new ConcurrentHashMap<>();
    private static final Map<ChronoUnit, Function<Duration, Long>> DURATION_TO_LONG;
    private static final Map<ChronoUnit, Function<Long, Duration>> LONG_TO_DURATION;
    private static final Map<Class, BiFunction<SystemProperty, Object, String>> SAVE_FORMATTERS;
    private static final Map<Class, BiFunction<SystemProperty, Object, String>> DISPLAY_FORMATTERS;
    private static final Map<Class, Function> GETTERS;
    private static final Set<Class> NULLABLE_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(String.class, Class.class, Duration.class, Instant.class)));

    static {
        // Populate the map that turns a Duration to a Long based on the ChronoUnit a property should be saved in
        final Map<ChronoUnit, Function<Duration, Long>> durationToLong = new HashMap<>();
        durationToLong.put(ChronoUnit.MILLIS, Duration::toMillis);
        durationToLong.put(ChronoUnit.SECONDS, Duration::getSeconds);
        durationToLong.put(ChronoUnit.MINUTES, Duration::toMinutes);
        durationToLong.put(ChronoUnit.HOURS, Duration::toHours);
        durationToLong.put(ChronoUnit.DAYS, Duration::toDays);
        DURATION_TO_LONG = Collections.unmodifiableMap(durationToLong);
    }

    static {
        // Populate the map that turns a Long to a Duration based on the ChronoUnit a property should be saved in
        final Map<ChronoUnit, Function<Long, Duration>> longToDuration = new HashMap<>();
        longToDuration.put(ChronoUnit.MILLIS, Duration::ofMillis);
        longToDuration.put(ChronoUnit.SECONDS, Duration::ofSeconds);
        longToDuration.put(ChronoUnit.MINUTES, Duration::ofMinutes);
        longToDuration.put(ChronoUnit.HOURS, Duration::ofHours);
        longToDuration.put(ChronoUnit.DAYS, Duration::ofDays);
        LONG_TO_DURATION = Collections.unmodifiableMap(longToDuration);
    }

    static {
        // Populate the map that converts a property to the String value that it's saved as
        final Map<Class, BiFunction<SystemProperty, Object, String>> formatters = new HashMap<>();
        formatters.put(String.class, (systemProperty, value) -> (String) value);
        formatters.put(Integer.class, (systemProperty, value) -> value.toString());
        formatters.put(Long.class, (systemProperty, value) -> value.toString());
        formatters.put(Boolean.class, (systemProperty, value) -> value.toString());
        formatters.put(Duration.class, (systemProperty, value) -> value == null ? null : String.valueOf(DURATION_TO_LONG.get(systemProperty.chronoUnit).apply((Duration) value)));
        formatters.put(Instant.class, (systemProperty, value) -> value == null ? null : String.valueOf(((Instant)value).toEpochMilli()));
        formatters.put(Class.class, (systemProperty, value) -> value == null ? null : ((Class) value).getName());
        SAVE_FORMATTERS = Collections.unmodifiableMap(formatters);
    }

    static {
        // Populate the map that retrieves the property value of a given type from JiveGlobals
        final Map<Class, Function> getters = new HashMap<>();
        getters.put(String.class, (Function<SystemProperty<String>, String>) property -> JiveGlobals.getProperty(property.key, property.defaultValue));
        getters.put(Integer.class, (Function<SystemProperty<Integer>, Integer>) property -> JiveGlobals.getIntProperty(property.key, property.defaultValue));
        getters.put(Long.class, (Function<SystemProperty<Long>, Long>) property -> JiveGlobals.getLongProperty(property.key, property.defaultValue));
        getters.put(Boolean.class, (Function<SystemProperty<Boolean>, Boolean>) property -> JiveGlobals.getBooleanProperty(property.key, property.defaultValue));
        getters.put(Duration.class, (Function<SystemProperty<Duration>, Duration>) property -> longValueExists(property.key) ? LONG_TO_DURATION.get(property.chronoUnit).apply(JiveGlobals.getLongProperty(property.key, 0)) : property.defaultValue);
        getters.put(Instant.class, (Function<SystemProperty<Instant>, Instant>) property -> longValueExists(property.key) ? Instant.ofEpochMilli(JiveGlobals.getLongProperty(property.key, 0)) : property.defaultValue);
        getters.put(Class.class, (Function<SystemProperty<Class>, Class>) property -> {
            final String className = JiveGlobals.getProperty(property.key, property.defaultValue == null ? null : property.defaultValue.getName());
            if (StringUtils.isBlank(className)) {
                // No configured value and no default either
                return null;
            }
            try {
                final Class<?> clazz = Class.forName(className);
                if (property.baseClass.isAssignableFrom(clazz)) {
                    return clazz;
                } else {
                    LOGGER.warn("Configured property {} is not an instance of {}, using default value {} instead", className, property.baseClass.getName(), SAVE_FORMATTERS.get(Class.class).apply(property, property.defaultValue));
                    return property.defaultValue;
                }
            } catch (final ClassNotFoundException e) {
                LOGGER.warn("Class {} was not found, using default value {} instead", className, SAVE_FORMATTERS.get(Class.class).apply(property, property.defaultValue), e);
                return property.defaultValue;
            }
        });
        GETTERS = Collections.unmodifiableMap(getters);
    }

    static {
        // Populate the map that converts a property to the String value that it's saved as
        final Map<Class, BiFunction<SystemProperty, Object, String>> formatters = new HashMap<>();
        formatters.put(String.class, (systemProperty, value) -> (String) value);
        formatters.put(Integer.class, (systemProperty, value) -> value.toString());
        formatters.put(Long.class, (systemProperty, value) -> value.toString());
        formatters.put(Boolean.class, (systemProperty, value) -> value.toString());
        formatters.put(Duration.class, (systemProperty, value) -> value == null ? null : org.jivesoftware.util.StringUtils.getFullElapsedTime((Duration)value));
        formatters.put(Instant.class, (systemProperty, value) -> value == null ? null : Date.from((Instant) value).toString());
        formatters.put(Class.class, (systemProperty, value) -> value == null ? null : ((Class)value).getName());
        DISPLAY_FORMATTERS = Collections.unmodifiableMap(formatters);
    }

    static {
        // Add a (single) listener that will call the listeners of each given property
        PropertyEventDispatcher.addListener(new PropertyEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void propertySet(final String property, final Map<String, Object> params) {
                final SystemProperty systemProperty = PROPERTIES.get(property);
                if (systemProperty != null) {
                    final Object newValue = systemProperty.getValue();
                    systemProperty.listeners.forEach(consumer -> ((Consumer) consumer).accept(newValue));
                }
            }

            @Override
            public void propertyDeleted(final String property, final Map<String, Object> params) {
                propertySet(property, params);
            }

            @Override
            public void xmlPropertySet(final String property, final Map<String, Object> params) {
                // Ignored - we're only covering database properties
            }

            @Override
            public void xmlPropertyDeleted(final String property, final Map<String, Object> params) {
                // Ignored - we're only covering database properties
            }
        });
    }

    private final Class<T> clazz;
    private final String key;
    private final String description;
    private final String plugin;
    private final T defaultValue;
    private final Class<?> baseClass;
    private final T minValue;
    private final T maxValue;
    private final boolean dynamic;
    private final ChronoUnit chronoUnit;
    private final Set<Consumer<T>> listeners = ConcurrentHashMap.newKeySet();
    private final T initialValue;
    private final boolean encrypted;

    private SystemProperty(final Builder<T> builder) {
        this.clazz = builder.clazz;
        this.key = builder.key;
        this.description = LocaleUtils.getLocalizedString("system_property." + key);
        this.plugin = builder.plugin;
        this.defaultValue = builder.defaultValue;
        this.baseClass = builder.baseClass;
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        this.dynamic = builder.dynamic;
        this.encrypted = builder.encrypted;
        this.chronoUnit = builder.chronoUnit;
        this.listeners.addAll(builder.listeners);
        this.initialValue = getValue();
        if (encrypted) {
            // Ensure a pre-existing JiveGlobal is encrypted - a null-operation if it doesn't exist/is already encrypted
            JiveGlobals.setPropertyEncrypted(key, true);
        }
    }

    private static boolean longValueExists(final String key) {
        final String stringValue = JiveGlobals.getProperty(key);
        if (stringValue != null) {
            try {
                Long.parseLong(stringValue);
                return true;
            } catch (final NumberFormatException ignored) {
                // Do nothing - will return false
            }
        }
        return false;
    }

    /**
     * @return an unmodifiable collection of all the current SystemProperties
     */
    public static Collection<SystemProperty> getProperties() {
        return Collections.unmodifiableCollection(PROPERTIES.values());
    }

    /**
     * Removes all the properties for a specific plugin. This should be called by a plugin when it is unloaded to
     * allow it to be added again without a server restart
     */
    @SuppressWarnings("WeakerAccess")
    public static void removePropertiesForPlugin(final String plugin) {
        getProperties().stream()
            .filter(systemProperty -> systemProperty.plugin.equals(plugin))
            .map(systemProperty -> systemProperty.key)
            .forEach(PROPERTIES::remove);
    }

    /**
     * Returns the SystemProperty for the specified key
     *
     * @param key the key for the property to fetch
     * @return The SystemProperty for that key, if any
     */
    public static Optional<SystemProperty> getProperty(final String key) {
        return Optional.ofNullable(PROPERTIES.get(key));
    }

    /**
     * @return the current value of the SystemProperty, or the default value if it is not currently set to within the
     * configured constraints. {@code null} if the property has not been set and there is no default value.
     */
    @SuppressWarnings("unchecked")
    public T getValue() {
        final T value = (T) GETTERS.get(clazz).apply(this);
        if (minValue != null && value != null && ((Comparable) minValue).compareTo(value) > 0) {
            LOGGER.warn("Configured value of {} is less than the minimum value of {} for the SystemProperty {} - will use default value of {} instead",
                value, minValue, key, defaultValue);
            return defaultValue;
        }
        if (maxValue != null && value != null && ((Comparable) maxValue).compareTo(value) < 0) {
            LOGGER.warn("Configured value of {} is more than the maximum value of {} for the SystemProperty {} - will use default value of {} instead",
                value, maxValue, key, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * @return the value of this property as saved in the ofProperty table. {@code null} if there is no current value and the default is not set.
     */
    public String getValueAsSaved() {
        return SAVE_FORMATTERS.get(clazz).apply(this, getValue());
    }

    /**
     * @return the value a human readable value of this property. {@code null} if there is no current value and the default is not set.
     */
    public String getDisplayValue() {
        return DISPLAY_FORMATTERS.get(clazz).apply(this, getValue());
    }

    /**
     * @return {@code false} if the property has been changed from it's default value, otherwise {@code true}
     */
    public boolean hasValueChanged() {
        return !Objects.equals(getValue(), defaultValue);
    }

    /**
     * @return the value a human readable value of this property. {@code null} if the default value is not configured.
     */
    public String getDefaultDisplayValue() {
        return DISPLAY_FORMATTERS.get(clazz).apply(this, defaultValue);
    }

    /**
     * Sets the value of the SystemProperty. Note that the new value can be outside any minimum/maximum for the property,
     * and will be saved to the database as such, however subsequent attempts to retrieve it's value will return the default.
     *
     * @param value the new value for the SystemProperty
     */
    public void setValue(final T value) {
        JiveGlobals.setProperty(key, SAVE_FORMATTERS.get(clazz).apply(this, value), isEncrypted());
    }

    /**
     * @return the plugin that created this property - or simply {@code "Openfire"}
     */
    public String getPlugin() {
        return plugin;
    }

    /**
     * @return {@code false} if Openfire or the plugin needs to be restarted for changes to this property to take effect, otherwise {@code true}
     */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * @return {@code true} if the property was initially setup to be encrypted, or was encrypted subsequently, otherwise {@code false}
     */
    public boolean isEncrypted() {
        return encrypted || JiveGlobals.isPropertyEncrypted(key);
    }

    /**
     * @return {@code true} if this property has changed and an Openfire or plugin restart is required, otherwise {@code false}
     */
    public boolean isRestartRequired() {
        return !(dynamic || Objects.equals(getValue(), initialValue));
    }

    /***
     * @param listener a listener to add to the property, that will be called whenever the property value changes
     */
    public void addListener(final Consumer<T> listener) {
        this.listeners.add(listener);
    }

    /***
     * @param listener the listener that is no longer required
     */
    public void removeListener(final Consumer<T> listener) {
        this.listeners.remove(listener);
    }

    /**
     * @return the {@link JiveGlobals} key for this property.
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the description of this property. This is set in the resource bundle for the current locale, using the
     * key {@code system_property.}<b><i>property-key</i></b>.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the default value of this property. {@code null} if there is no default value configured.
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Used to build a {@link SystemProperty}
     *
     * @param <T> the type of system property to build
     */
    public static final class Builder<T> {
        private final Class<T> clazz;
        private final Set<Consumer<T>> listeners = new HashSet<>();
        private String key;
        private String plugin = "Openfire";
        private T defaultValue;
        private T minValue;
        private T maxValue;
        private ChronoUnit chronoUnit;
        private Boolean dynamic;
        private boolean encrypted = false;
        private Class<?> baseClass;

        private Builder(final Class<T> clazz) {
            this.clazz = clazz;
        }

        /**
         * Start a new SystemProperty builder. The following types of SystemProperty are supported:
         * <ul>
         * <li>{@link String}</li>
         * <li>{@link Integer} - for which a default value must be supplied using {@link #setDefaultValue(Object)}</li>
         * <li>{@link Long} - for which a default value must be supplied</li>
         * <li>{@link Boolean} - for which a default value must be supplied</li>
         * <li>{@link Duration} - for which a {@link ChronoUnit} must be specified, to indicate how the value will be saved, using {@link #setChronoUnit(ChronoUnit)}</li>
         * <li>{@link Instant}</li>
         * <li>{@link Class} - for which a base class must be specified from which values must be assignable to, using {@link #setBaseClass(Class)}</li>
         * </ul>
         *
         * @param <T>   the type of SystemProperty
         * @param clazz The class of property being built
         * @return A SystemProperty builder
         */
        public static <T> Builder<T> ofType(final Class<T> clazz) {
            if (!GETTERS.containsKey(clazz) || !SAVE_FORMATTERS.containsKey(clazz) || !DISPLAY_FORMATTERS.containsKey(clazz)) {
                throw new IllegalArgumentException("Cannot create a SystemProperty of type " + clazz.getName());
            }
            return new Builder<>(clazz);
        }

        /**
         * Sets the key for the SystemProperty. Must be unique
         *
         * @param key the property key
         * @return The current SystemProperty builder
         */
        public Builder<T> setKey(final String key) {
            this.key = key;
            return this;
        }

        /**
         * Sets the default value for the SystemProperty. This value will be used if the property is not set, or
         * falls outside the minimum/maximum values configured.
         *
         * @param defaultValue the default value for the property
         * @return The current SystemProperty builder
         * @see #setMinValue(Object)
         * @see #setMaxValue(Object)
         */
        public Builder<T> setDefaultValue(final T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * This indicates which class configured values must inherit from. It must be set (and can only be set) if the default
         * value is a class.
         *
         * @param baseClass - the base class from which all configured values must inherit
         * @return The current SystemProperty builder
         */
        public Builder<T> setBaseClass(final Class<?> baseClass) {
            if(clazz != Class.class) {
                throw new IllegalArgumentException("Only properties of type Class can have a base class set");

            }
            this.baseClass = baseClass;
            return this;
        }

        /**
         * Sets the minimum value for the SystemProperty. If the configured value is less than minimum value, the
         * default value will be used instead.
         * <p><strong>Important:</strong> If a minimum value is configured, the type of property being built must
         * implement {@link Comparable}.</p>
         *
         * @param minValue the minimum value for the property
         * @return The current SystemProperty builder
         */
        public Builder<T> setMinValue(final T minValue) {
            if (!Comparable.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("A minimum value can only be applied to properties that implement Comparable");
            }
            this.minValue = minValue;
            return this;
        }

        /**
         * Sets the maximum value for the SystemProperty. If the configured value is more than maximum value, the
         * default value will be used instead.
         * <p><strong>Important:</strong> If a maximum value is configured, the type of property being built must
         * implement {@link Comparable}.</p>
         *
         * @param maxValue the maximum value for the property
         * @return The current SystemProperty builder
         */
        public Builder<T> setMaxValue(final T maxValue) {
            if (!Comparable.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("A maximum value can only be applied to properties that implement Comparable");
            }
            this.maxValue = maxValue;
            return this;
        }

        /**
         * If the type of the property is a {@link Duration} this is used to indicate how the value is saved in the
         * database. For an example a Duration of one hour will be saved as "60" if the ChronoUnit is {@link ChronoUnit#MINUTES}, or
         * saved as "3600" if the ChronoUnit is {@link ChronoUnit#SECONDS}.
         * <p><strong>Important:</strong> The ChronoUnit is required, and must be set, if the type of property is a Duration.
         *
         * @param chronoUnit the unit of time the Duration is saved to the database in
         * @return The current SystemProperty builder
         */
        public Builder<T> setChronoUnit(final ChronoUnit chronoUnit) {
            if(clazz != Duration.class) {
                throw new IllegalArgumentException("Only properties of type Duration can have a ChronoUnit set");
            }
            this.chronoUnit = chronoUnit;
            return this;
        }

        /**
         * @param listener the listener that will be called when the value of the property changes
         * @return The current SystemProperty builder
         */
        public Builder<T> addListener(final Consumer<T> listener) {
            this.listeners.add(listener);
            return this;
        }

        /**
         * @param dynamic {@code true} if changes to this property take effect immediately, {@code false} if a restart
         *                is required.
         * @return The current SystemProperty builder
         */
        public Builder<T> setDynamic(final boolean dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        /**
         * @param encrypted {@code true} if this property should be encrypted, {@code false} if can be stored in
         *                  plain text. Defaults to plain text if not otherwise specified.
         * @return The current SystemProperty builder
         */
        public Builder<T> setEncrypted(final boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        /**
         * Sets the name of the plugin that is associated with this property. This is used on the Openfire System
         * Properties admin interface to provide filtering capabilities. This will default to Openfire if not set.
         *
         * @param plugin the name of the plugin creating this property.
         * @return The current SystemProperty builder
         */
        public Builder<T> setPlugin(final String plugin) {
            this.plugin = plugin;
            return this;
        }

        /**
         * Validates the details of the SystemProperty, and generates one if it's valid.
         *
         * @return A SystemProperty object
         * @throws IllegalArgumentException if incorrect arguments have been supplied to the builder
         */
        public SystemProperty<T> build() throws IllegalArgumentException {
            checkNotNull(key, "The property key has not been set");
            if (PROPERTIES.containsKey(key)) {
                throw new IllegalArgumentException("A SystemProperty already exists with a key of " + key);
            }
            if (!NULLABLE_TYPES.contains(clazz)) {
                checkNotNull(defaultValue, "The properties default value has not been set");
            }
            checkNotNull(plugin, "The property plugin has not been set");
            checkNotNull(dynamic, "The property dynamism has not been set");
            if (clazz == Duration.class) {
                checkNotNull(chronoUnit, "The ChronoUnit for the Duration property has not been set");
                if (!DURATION_TO_LONG.containsKey(chronoUnit) || !LONG_TO_DURATION.containsKey(chronoUnit)) {
                    throw new IllegalArgumentException("A Duration property cannot be saved with a ChronoUnit of " + chronoUnit);
                }
            }
            //noinspection unchecked
            if (minValue != null && defaultValue != null && ((Comparable<T>) minValue).compareTo(defaultValue) > 0) {
                throw new IllegalArgumentException("The minimum value cannot be more than the default value");
            }
            //noinspection unchecked
            if (maxValue != null && defaultValue != null && ((Comparable<T>) maxValue).compareTo(defaultValue) < 0) {
                throw new IllegalArgumentException("The maximum value cannot be less than the default value");
            }
            if (clazz == Class.class) {
                checkNotNull(baseClass, "The base class must be set for properties of type class");
            }
            final SystemProperty<T> property = new SystemProperty<>(this);
            PROPERTIES.put(key, property);
            return property;
        }

        private void checkNotNull(final Object value, final String s) {
            if (value == null) {
                throw new IllegalArgumentException(s);
            }
        }
    }
}
