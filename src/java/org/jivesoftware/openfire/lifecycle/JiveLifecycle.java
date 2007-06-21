package org.jivesoftware.openfire.lifecycle;

/**
 * Provide a JiveProperty which manages the lifecycle of your object. If the provided property
 * is true or does not exist, the Object should be considered to be in a running state. Otherwise, if
 * the property is explicitly set to false the Lifecycle object is considered to not be running.
 */
public interface JiveLifecycle {
    /**
     * The JiveProperty which either when it doesn't exist or is set to true. If no property has been
     * set the Lifecycle should be considered to be in a running state. If the JivePropety
     * is not explicitly set to the value &quot;false&quot; then the Lifecycle object should be considered
     * to be not in a running state.
     *
     * @param jiveProperty the JiveProperty which defines the 
     */
    void setJiveProperty(String jiveProperty);

    /**
     * The only way this method will return False is if a JiveProperty has been specified and it has been
     * explicitly set to &quot;false&quot;.
     *
     * @return true if this Lifecycle object is running and false if it is not.
     */
    boolean isRunning();
}
