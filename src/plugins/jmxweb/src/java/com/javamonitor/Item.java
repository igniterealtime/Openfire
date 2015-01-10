package com.javamonitor;

/**
 * A monitored item. Basically a pointer into JMX using an object name and an
 * attribute.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
final class Item {
    private final String id;

    private final String objectName;

    private final String attribute;

    private final boolean periodic;

    /**
     * Create a new item.
     * 
     * @param id
     *            The identifier of this item.
     * @param objectName
     *            The object name for the new item.
     * @param attribute
     *            The attribute for the new item.
     * @param periodic
     *            A flag that says if this is a periodic item.
     */
    Item(final String id, final String objectName, final String attribute,
            final boolean periodic) {
        this.id = id;
        this.objectName = objectName;
        this.attribute = attribute;
        this.periodic = periodic;
    }

    /**
     * Get the ID of the item.
     * 
     * @return The ID of the item.
     */
    String getId() {
        return id;
    }

    /**
     * Find the object name of this item
     * 
     * @return The JMX object name of the item.
     */
    String getObjectName() {
        return objectName;
    }

    /**
     * Find the monitored attribute.
     * 
     * @return The monitored attribute.
     */
    String getAttribute() {
        return attribute;
    }

    /**
     * Tell if this is a periodic item.
     * 
     * @return <code>true</code> if this is a periodic item, or
     *         <code>false</code> if it lives as long as a JVM.
     */
    boolean isPeriodic() {
        return periodic;
    }
}
