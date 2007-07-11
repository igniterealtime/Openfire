package org.jivesoftware.openfire.component;

import org.jivesoftware.openfire.lifecycle.JiveLifecycle;

/**
 * Manages a components lifecycle. A component can be started and stopped, either by explicitly
 * calling the start and stop methods or if a jive property is set on the LifeCycle object to be
 * either true or false. True causing the the component to be started, if it is not already started,
 * and false causing it to be stopped. Note that either the property needs to be expliticity set or
 * the component needs to be explicitly stopped in order for the component to enter the stopped
 * state.
 *
 * @author Alexander Wenckus
 */
public interface ComponentLifecycle extends JiveLifecycle {

    /**
     * Starts the component, setting the JiveProperty to true if it exists.
     */
    void start();

    /**
     * Stops the component, setting the JiveProperty to false if it exists.
     */
    void stop();
}
