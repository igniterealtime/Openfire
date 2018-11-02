package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * The Class UserProperty.
 */
public class UserProperty {

    /** The key. */
    private String key;

    /** The value. */
    private String value;

    /**
     * Instantiates a new user property.
     */
    public UserProperty() {

    }

    /**
     * Instantiates a new user property.
     *
     * @param key
     *            the key
     * @param value
     *            the value
     */
    public UserProperty(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Gets the key.
     *
     * @return the key
     */
    @XmlAttribute
    public String getKey() {
        return key;
    }

    /**
     * Sets the key.
     *
     * @param key
     *            the new key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the value.
     *
     * @return the value
     */
    @XmlAttribute
    public String getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @param value
     *            the new value
     */
    public void setValue(String value) {
        this.value = value;
    }

}
