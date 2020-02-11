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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Represents a system property - also accessible via {@link JiveGlobals}. The only way to create a SystemProperty object
 * is to use a {@link Builder}.
 *
 * @param <T> The type of system property.
 */
public final class SystemProperty<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemProperty.class);
    private static final Map<String, SystemProperty> PROPERTIES = new ConcurrentHashMap<>();
    private static final Map<ChronoUnit, Function<Duration, Long>> DURATION_TO_LONG = new HashMap<>();
    private static final Map<ChronoUnit, Function<Long, Duration>> LONG_TO_DURATION = new HashMap<>();
    private static final Map<Class, BiFunction<String, SystemProperty, Object>> FROM_STRING = new HashMap<>();
    private static final Map<Class, BiFunction<Object, SystemProperty, String>> TO_STRING = new HashMap<>();
    private static final Map<Class, BiFunction<Object, SystemProperty, String>> TO_DISPLAY_STRING = new HashMap<>();
    private static final Set<Class> NULLABLE_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(String.class, Class.class, Duration.class, Instant.class, JID.class)));

    static {
        // Populate the map that turns a Duration to a Long based on the ChronoUnit a property should be saved in
        DURATION_TO_LONG.put(ChronoUnit.MILLIS, Duration::toMillis);
        DURATION_TO_LONG.put(ChronoUnit.SECONDS, Duration::getSeconds);
        DURATION_TO_LONG.put(ChronoUnit.MINUTES, Duration::toMinutes);
        DURATION_TO_LONG.put(ChronoUnit.HOURS, Duration::toHours);
        DURATION_TO_LONG.put(ChronoUnit.DAYS, Duration::toDays);
    }

    static {
        // Populate the map that turns a Long to a Duration based on the ChronoUnit a property should be saved in
        LONG_TO_DURATION.put(ChronoUnit.MILLIS, Duration::ofMillis);
        LONG_TO_DURATION.put(ChronoUnit.SECONDS, Duration::ofSeconds);
        LONG_TO_DURATION.put(ChronoUnit.MINUTES, Duration::ofMinutes);
        LONG_TO_DURATION.put(ChronoUnit.HOURS, Duration::ofHours);
        LONG_TO_DURATION.put(ChronoUnit.DAYS, Duration::ofDays);
    }

    static {
        // Given a String value and a system property, converts the String to the object of appropriate type or null
        FROM_STRING.put(String.class, (value, systemProperty) -> value);
        FROM_STRING.put(Integer.class, (value, systemProperty) -> org.jivesoftware.util.StringUtils.parseInteger(value).orElse(null));
        FROM_STRING.put(Long.class, (value, systemProperty) -> org.jivesoftware.util.StringUtils.parseLong(value).orElse(null));
        FROM_STRING.put(Double.class, (value, systemProperty) -> org.jivesoftware.util.StringUtils.parseDouble(value).orElse(null));
        FROM_STRING.put(Boolean.class, (value, systemProperty) -> value == null ? null : Boolean.valueOf(value));
        FROM_STRING.put(Duration.class, (value, systemProperty) -> org.jivesoftware.util.StringUtils.parseLong(value).map(longValue -> LONG_TO_DURATION.get(systemProperty.chronoUnit).apply(longValue)).orElse(null));
        FROM_STRING.put(Instant.class, (value, systemProperty) -> org.jivesoftware.util.StringUtils.parseLong(value).map(Instant::ofEpochMilli).orElse(null));
        FROM_STRING.put(JID.class, (value, systemProperty) -> {
            if(value == null) {
                return null;
            }
            try {
                return new JID(value);
            } catch(final Exception e) {
                LOGGER.warn("Configured property {} is not a valid JID", value);
                return null;
            }
        });
        FROM_STRING.put(Enum.class, (value, systemProperty) -> {
            if (StringUtils.isBlank(value)) {
                return null;
            }
            for (final Object constant : systemProperty.defaultValue.getClass().getEnumConstants()) {
                if (((Enum) constant).name().equals(value)) {
                    return constant;
                }
            }
            return null;
        });
        FROM_STRING.put(Class.class, (value, systemProperty) -> {
            if(StringUtils.isBlank(value)) {
                return null;
            }
            try {
                final Class clazz = ClassUtils.forName(value);
                //noinspection unchecked
                if (systemProperty.baseClass.isAssignableFrom(clazz)) {
                    return clazz;
                } else {
                    LOGGER.warn("Configured property {} is not an instance of {}", value, systemProperty.baseClass.getName());
                    return null;
                }
            } catch (final ClassNotFoundException e) {
                LOGGER.warn("Class {} was not found", value, e);
                return null;
            }
        });
        FROM_STRING.put(List.class, (value, systemProperty) -> getObjectStream(value, systemProperty).collect(Collectors.toList()));
        FROM_STRING.put(Set.class, (value, systemProperty) -> getObjectStream(value, systemProperty).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static Stream<Object> getObjectStream(final String value, final SystemProperty systemProperty) {
        if (StringUtils.isEmpty(value)) {
            return Stream.empty();
        }
        final List<String> strings = Arrays.asList(value.split(","));
        Stream<Object> stream = strings.stream()
            .map(singleValue -> FROM_STRING.get(systemProperty.collectionType).apply(singleValue, systemProperty))
            .filter(Objects::nonNull);
        if (systemProperty.sorted) {
            stream = stream.sorted();
        }
        return stream;
    }

    static {
        // Given a value and a system property, converts the value to a String with the appropriate value or null
        TO_STRING.put(String.class, (value, systemProperty) -> (String)value);
        TO_STRING.put(Integer.class, (value, systemProperty) -> value.toString());
        TO_STRING.put(Long.class, (value, systemProperty) -> value.toString());
        TO_STRING.put(Double.class, (value, systemProperty) -> value.toString());
        TO_STRING.put(Boolean.class, (value, systemProperty) -> value.toString());
        TO_STRING.put(Duration.class, (value, systemProperty) -> value == null ? null : DURATION_TO_LONG.get(systemProperty.chronoUnit).apply((Duration) value).toString());
        TO_STRING.put(Instant.class, (value, systemProperty) -> value == null ? null : String.valueOf(((Instant)value).toEpochMilli()));
        TO_STRING.put(JID.class, (value, systemProperty) -> value == null ? null : value.toString());
        TO_STRING.put(Enum.class, (value, systemProperty) -> value == null ? null : ((Enum)value).name());
        TO_STRING.put(Class.class, (value, systemProperty) -> value == null ? null : ((Class)value).getName());
        TO_STRING.put(List.class, (value, systemProperty) -> {
            final Collection collection = (Collection) value;
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            // noinspection unchecked
            Stream<String> stream = collection.stream()
                .map(singleValue -> TO_STRING.get(systemProperty.collectionType).apply(singleValue, systemProperty))
                .filter(Objects::nonNull);
            if(systemProperty.sorted) {
                stream = stream.sorted();
            }
            return stream.collect(Collectors.joining(","));
        });
        TO_STRING.put(Set.class, TO_STRING.get(List.class));
    }

    static {
        // Given a value and a system property, converts the value to a display String of the appropriate value or null
        TO_DISPLAY_STRING.put(String.class, (value, systemProperty) -> (String) value);
        TO_DISPLAY_STRING.put(Integer.class, (value, systemProperty) -> value.toString());
        TO_DISPLAY_STRING.put(Long.class, (value, systemProperty) -> value.toString());
        TO_DISPLAY_STRING.put(Double.class, (value, systemProperty) -> value.toString());
        TO_DISPLAY_STRING.put(Boolean.class, (value, systemProperty) -> value.toString());
        TO_DISPLAY_STRING.put(Duration.class, (value, systemProperty) -> value == null ? null : org.jivesoftware.util.StringUtils.getFullElapsedTime((Duration)value));
        TO_DISPLAY_STRING.put(Instant.class, (value, systemProperty) -> value == null ? null : Date.from((Instant) value).toString());
        TO_DISPLAY_STRING.put(JID.class, (value, systemProperty) -> value == null ? null : value.toString());
        TO_DISPLAY_STRING.put(Enum.class, (value, systemProperty) -> value == null ? null : ((Enum)value).name());
        TO_DISPLAY_STRING.put(Class.class, (value, systemProperty) -> value == null ? null : ((Class)value).getName());
        TO_DISPLAY_STRING.put(List.class, (value, systemProperty) -> {
            final Collection collection = (Collection) value;
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            // noinspection unchecked
            Stream<String> stream = collection.stream()
                .map(singleValue -> TO_DISPLAY_STRING.get(systemProperty.collectionType).apply(singleValue, systemProperty))
                .filter(Objects::nonNull);
            if(systemProperty.sorted) {
                stream = stream.sorted();
            }
            return stream.collect(Collectors.joining(","));
        });
        TO_DISPLAY_STRING.put(Set.class, TO_DISPLAY_STRING.get(List.class));
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
    private final T minValue;
    private final T maxValue;
    private final boolean dynamic;
    private final Set<Consumer<T>> listeners = ConcurrentHashMap.newKeySet();
    private final T initialValue;
    private final boolean encrypted;
    private final ChronoUnit chronoUnit;
    private final Class baseClass;
    private final Class collectionType;
    private final boolean sorted;

    private SystemProperty(final Builder<T> builder) {
        // Before we do anything, convert XML based provider setup to Database based
        JiveGlobals.migrateProperty(builder.key);
        this.clazz = builder.clazz;
        this.key = builder.key;
        this.plugin = builder.plugin;
        this.description = LocaleUtils.getLocalizedPluginString(plugin, "system_property." + key);
        this.defaultValue = builder.defaultValue;
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        this.dynamic = builder.dynamic;
        this.encrypted = builder.encrypted;
        if (encrypted) {
            // Ensure a pre-existing JiveGlobal is encrypted - a null-operation if it doesn't exist/is already encrypted
            JiveGlobals.setPropertyEncrypted(key, true);
        }
        this.chronoUnit = builder.chronoUnit;
        this.baseClass = builder.baseClass;
        this.collectionType = builder.collectionType;
        this.sorted = builder.sorted;
        this.listeners.addAll(builder.listeners);
        this.initialValue = getValue();
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
     * @param plugin The plugin for which properties should be removed
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

    // Enums are a special case
    private Class getConverterClass() {
        if(Enum.class.isAssignableFrom(clazz)) {
            return Enum.class;
        } else {
            return clazz;
        }
    }

    /**
     * @return the current value of the SystemProperty, or the default value if it is not currently set to within the
     * configured constraints. {@code null} if the property has not been set and there is no default value.
     */
    @SuppressWarnings("unchecked")
    public T getValue() {
        final T value = (T) FROM_STRING.get(getConverterClass()).apply(JiveGlobals.getProperty(key), this);
        if (value == null || (Collection.class.isAssignableFrom(value.getClass()) && ((Collection) value).isEmpty())) {
            return defaultValue;
        }
        if (minValue != null && ((Comparable) minValue).compareTo(value) > 0) {
            LOGGER.warn("Configured value of {} is less than the minimum value of {} for the SystemProperty {} - will use default value of {} instead",
                value, minValue, key, defaultValue);
            return defaultValue;
        }
        if (maxValue != null && ((Comparable) maxValue).compareTo(value) < 0) {
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
        return TO_STRING.get(getConverterClass()).apply(getValue(), this);
    }

    /**
     * @return the value a human readable value of this property. {@code null} if there is no current value and the default is not set.
     */
    public String getDisplayValue() {
        return TO_DISPLAY_STRING.get(getConverterClass()).apply(getValue(), this);
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
        return TO_DISPLAY_STRING.get(getConverterClass()).apply(defaultValue, this);
    }

    /**
     * Sets the value of the SystemProperty. Note that the new value can be outside any minimum/maximum for the property,
     * and will be saved to the database as such, however subsequent attempts to retrieve it's value will return the default.
     *
     * @param value the new value for the SystemProperty
     */
    public void setValue(final T value) {
        JiveGlobals.setProperty(key, TO_STRING.get(getConverterClass()).apply(value, this), isEncrypted());
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
        private String plugin = LocaleUtils.OPENFIRE_PLUGIN_NAME;
        private T defaultValue;
        private T minValue;
        private T maxValue;
        private ChronoUnit chronoUnit;
        private Boolean dynamic;
        private boolean encrypted = false;
        private Class<?> baseClass;
        private Class collectionType;
        private boolean sorted;

        private Builder(final Class<T> clazz) {
            this.clazz = clazz;
        }

        /**
         * Start a new SystemProperty builder. The following types of SystemProperty are supported:
         * <ul>
         * <li>{@link String}</li>
         * <li>{@link Integer} - for which a default value must be supplied using {@link #setDefaultValue(Object)}</li>
         * <li>{@link Long} - for which a default value must be supplied</li>
         * <li>{@link Double} - for which a default value must be supplied</li>
         * <li>{@link Boolean} - for which a default value must be supplied</li>
         * <li>{@link Duration} - for which a {@link ChronoUnit} must be specified, to indicate how the value will be saved, using {@link #setChronoUnit(ChronoUnit)}</li>
         * <li>{@link Instant}</li>
         * <li>{@link JID}</li>
         * <li>{@link Class} - for which a base class must be specified from which values must inherit, using {@link #setBaseClass(Class)}</li>
         * <li>any {@link Enum} - for which a default value must be supplied</li>
         * <li>{@link List} - for which a collection type must be specified, using {@link #buildList(Class)}</li>
         * <li>{@link Set} - for which a collection type must be specified, using {@link #buildSet(Class)}</li>
         * </ul>
         *
         * @param <T>   the type of SystemProperty
         * @param clazz The class of property being built
         * @return A SystemProperty builder
         */
        public static <T> Builder<T> ofType(final Class<T> clazz) {
            if (!Enum.class.isAssignableFrom(clazz)
                && (!FROM_STRING.containsKey(clazz) || !TO_STRING.containsKey(clazz) || !TO_DISPLAY_STRING.containsKey(clazz))) {
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
        @SuppressWarnings("unchecked")
        public Builder<T> setDefaultValue(final T defaultValue) {
            if( defaultValue instanceof Instant) {
                this.defaultValue = (T) ((Instant) defaultValue).truncatedTo(ChronoUnit.MILLIS);
            } else {
                this.defaultValue = defaultValue;
            }
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
         * @param sorted {@code true} if this property is a list and should be sorted, {@code false} otherwise.
         * @return The current SystemProperty builder
         */
        public Builder<T> setSorted(final boolean sorted) {
            this.sorted = sorted;
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
            final Class classToCheck;
            if (Collection.class.isAssignableFrom(clazz)) {
                checkNotNull(collectionType, "A collection type must be built using buildList() or buildSet()");
                classToCheck = collectionType;
            } else {
                classToCheck = clazz;
            }

            if (sorted) {
                if (!Collection.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("Only Collection properties can be sorted");
                }
                if (!Comparable.class.isAssignableFrom(classToCheck)) {
                    throw new IllegalArgumentException("Only Collection properties containing Comparable elements can be sorted");
                }
            }

            checkNotNull(plugin, "The property plugin has not been set");
            checkNotNull(dynamic, "The property dynamism has not been set");
            if (classToCheck == Duration.class) {
                checkNotNull(chronoUnit, "The ChronoUnit for the Duration property has not been set");
                if (!DURATION_TO_LONG.containsKey(chronoUnit) || !LONG_TO_DURATION.containsKey(chronoUnit)) {
                    throw new IllegalArgumentException("A Duration property cannot be saved with a ChronoUnit of " + chronoUnit);
                }
            } else if (chronoUnit != null) {
                throw new IllegalArgumentException("Only properties of type Duration can have a ChronoUnit set");
            }
            if (minValue != null) {
                if (!Comparable.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("A minimum value can only be applied to properties that implement Comparable");
                }

                //noinspection unchecked
                if (defaultValue != null && ((Comparable<T>) minValue).compareTo(defaultValue) > 0) {
                    throw new IllegalArgumentException("The minimum value cannot be more than the default value");
                }
            }
            if (maxValue != null) {
                if (!Comparable.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("A maximum value can only be applied to properties that implement Comparable");
                }

                //noinspection unchecked
                if (defaultValue != null && ((Comparable<T>) maxValue).compareTo(defaultValue) < 0) {
                    throw new IllegalArgumentException("The maximum value cannot be less than the default value");
                }
            }
            if (classToCheck == Class.class) {
                checkNotNull(baseClass, "The base class must be set for properties of type class");
            }
            final SystemProperty<T> property = new SystemProperty<>(this);
            PROPERTIES.put(key, property);
            return property;
        }

        @SuppressWarnings("unchecked")
        public <C> SystemProperty<List<C>> buildList(final Class<C> listType) {
            if (clazz != List.class) {
                throw new IllegalArgumentException("Only list types can be built with buildList");
            }
            checkCollectionType(listType);
            this.collectionType = listType;
            return (SystemProperty<List<C>>) build();
        }

        @SuppressWarnings("unchecked")
        public <C> SystemProperty<Set<C>> buildSet(final Class<C> listType) {
            if (clazz != Set.class) {
                throw new IllegalArgumentException("Only set types can be built with buildSet");
            }
            checkCollectionType(listType);
            this.collectionType = listType;
            return (SystemProperty<Set<C>>) build();
        }

        private void checkCollectionType(final Class collectionType) {
            if (!FROM_STRING.containsKey(collectionType) || !TO_STRING.containsKey(collectionType) || !TO_DISPLAY_STRING.containsKey(collectionType)) {
                throw new IllegalArgumentException("Cannot create a SystemProperty containing a collection of type " + collectionType.getName());
            }
            if (Collections.class.isAssignableFrom(collectionType)) {
                throw new IllegalArgumentException("A collection cannot contain a collection");
            }
        }

        private void checkNotNull(final Object value, final String s) {
            if (value == null) {
                throw new IllegalArgumentException(s);
            }
        }
    }
}
