/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container;

/**
 * <p>Convenience class allowing very simple tracking of services
 * in a container.</p>
 * <p>To use the tracker, simply register a ServiceTrackerListener
 * along with either one, or an array of classes to track. The tracker
 * will use the listener methods to update the listener when services
 * are added or removed from the lookup.</p>
 * <p>All of the features here can be easily implemented using an
 * event listener and the standard ServiceLookup.notifyEvent() method.
 * This class just provides a default implementation of that for
 * the most common case of wanting to know when certain services
 * implementing an interface enter or leave the lookup.</p>
 *
 * @author Iain Shigeoka
 */
public class ServiceTracker {

    private ServiceTrackerListener trackerListener;
    private Class[] trackerClass;
    private ServiceLookup trackerLookup;
    private EventRegistration[] trackerRegs = null;

    /**
     * <p>Create a tracker to watch for the trackClass.</p>
     *
     * @param lookup     The lookup to track services in
     * @param listener   The listener to be updated of trackClass services
     * @param trackClass The class to track
     *                   <p/>
     */
    public ServiceTracker(ServiceLookup lookup,
                          ServiceTrackerListener listener,
                          Class trackClass) {
        this(lookup, listener, new Class[]{trackClass});
    }

    /**
     * <p>Create a tracker to watch for the trackClass.</p>
     *
     * @param lookup       The lookup to track services in
     * @param listener     The listener to be updated of trackClass services
     * @param trackClasses The classes to track
     */
    public ServiceTracker(ServiceLookup lookup,
                          ServiceTrackerListener listener,
                          Class[] trackClasses) {
        this.trackerListener = listener;
        this.trackerClass = trackClasses;
        this.trackerLookup = lookup;
        setupTracking();
    }

    /**
     * <p>Setup the tracking system.</p>
     * <p>We just run through all the track classes and register our
     * private event listener for match-nomatch and nomatch-match transitions.
     * Our private event listener will decode the incoming events and generate
     * the right tracker events.</p>
     * <p/>
     */
    private void setupTracking() {
        TrackerEventListener eventListener = new TrackerEventListener();
        trackerRegs = new EventRegistration[trackerClass.length];
        for (int i = 0; i < trackerClass.length; i++) {
            ServiceTemplate tmpl = new ServiceTemplate(null,
                    null,
                    new Class[]{trackerClass[i]});
            trackerRegs[i] = trackerLookup.notifyRegister(tmpl,
                    ServiceLookup.TRANSITION_MATCH_NOMATCH |
                    ServiceLookup.TRANSITION_NOMATCH_MATCH,
                    eventListener);
        }
        // Now check to see if the service is already in the lookup
        for (int i = 0; i < trackerClass.length; i++) {
            ServiceTemplate tmpl = new ServiceTemplate(null,
                    null,
                    new Class[]{trackerClass[i]});

            Object service = trackerLookup.lookup(tmpl);
            if (service != null) {
                trackerListener.addService(service);
            }
        }
        // We don't need the classes anymore so free them
        trackerClass = null;
    }

    /**
     * Permanently cancels the tracking. Once you call this method, subsequent calls
     * do nothing. The object becomes essentially worthless and you should remove
     * any references to it so the garbage collector can clean it up.
     */
    public void cancel() {
        if (trackerRegs != null) {
            for (int i = 0; i < trackerRegs.length; i++) {
                trackerRegs[i].cancel();
            }
            trackerRegs = null;
            trackerListener = null;
            trackerClass = null;
            trackerLookup = null;
        }
    }

    /**
     * Translates generic ServiceLookup notification events into the
     * simplified tracker events that the ServiceTrackerListener expects.
     * This class is all about convenience for users.
     *
     * @author Iain Shigeoka
     */
    private class TrackerEventListener implements EventListener {

        /**
         * Receive notification of a ServiceLookup notification event.
         *
         * @param e The event that occured
         * @throws UnknownEventException If the event is not what the listener expected
         */
        public void notifyEvent(Event e) throws UnknownEventException {
            if (e instanceof ServiceEvent) {
                ServiceEvent event = (ServiceEvent)e;
                switch (event.getTransition()) {
                    case ServiceLookup.TRANSITION_NOMATCH_MATCH:
                        trackerListener.addService(event.getServiceItem().service);
                        break;
                    case ServiceLookup.TRANSITION_MATCH_NOMATCH:
                        trackerListener.removeService(event.getServiceItem().service);
                        break;
                    default:
                        throw new UnknownEventException("Unexpected service event");
                }
            }
            else {
                throw new UnknownEventException();
            }
        }
    }
}
