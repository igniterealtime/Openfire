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
package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.*;
import org.jivesoftware.messenger.container.EventListener;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>Simple classs to track and manage service
 * registrations and respond to service lookups.</p>
 *
 * @author Iain Shigeoka
 */
public class ServiceLookupImpl implements ServiceLookup {

    private ServiceID id;
    private Map idTable = new HashMap();

    /**
     * Simple constructor
     */
    public ServiceLookupImpl() {
        id = new ServiceID();
    }

    public ServiceID getServiceID() {
        return id;
    }

    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) {
        throw new UnsupportedOperationException();
    }

    public Object lookup(Class type) {
        ServiceTemplate tmpl = new ServiceTemplate();
        tmpl.types = new Class[]{type};
        return lookup(tmpl);
    }

    public Object lookup(ServiceTemplate tmpl) {
        Object found = null;
        try {
            matchLock.readLock().lock();

            Iterator itemItr;
            // Add all matching service IDs to the items list
            if (tmpl.serviceID == null) {
                itemItr = idTable.values().iterator();
            }
            else {
                LinkedList items = new LinkedList();
                items.add(idTable.get(tmpl.serviceID));
                itemItr = items.iterator();
            }

            // Now check each item for a match against attributes
            while (itemItr.hasNext()) {
                ServiceItem item = (ServiceItem)itemItr.next();
                if (isMatch(tmpl, item)) {
                    found = item.service;
                    break;
                }
            }
        }
        finally {
            matchLock.readLock().unlock();
        }
        return found;
    }

    private boolean isMatch(ServiceTemplate tmpl, ServiceItem item) {
        boolean isMatch = true;
        if (tmpl.attributes != null) {
            for (int i = 0; i < tmpl.attributes.length; i++) {
                boolean hasAttribute = false;
                for (int j = 0; j < item.attributes.length; j++) {
                    if (item.attributes[j].equals(tmpl.attributes[i])) {
                        hasAttribute = true;
                    }
                }
                if (!hasAttribute) {
                    isMatch = false;
                    item = null;
                }
            }
        }
        if (item != null && tmpl.types != null) {
            for (int i = 0; i < tmpl.types.length; i++) {
                if (!tmpl.types[i].isInstance(item.service)) {
                    isMatch = false;
                    item = null;
                    break;
                }
            }
        }
        return isMatch;
    }

    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) {
        throw new UnsupportedOperationException();
    }

    //private long eventID = 101;
    private static final long EVENT_ID = 101;
    private long sequenceID = 0;
    private ReadWriteLock matchLock = new ReentrantReadWriteLock();
    private HashMap matchMatchListeners = new HashMap();
    private HashMap matchNoMatchListeners = new HashMap();
    private HashMap noMatchMatchListeners = new HashMap();
    private HashMap listeners = new HashMap();


    public EventRegistration notifyRegister(ServiceTemplate tmpl,
                                            int transitions,
                                            EventListener listener) {
        try {
            matchLock.writeLock().lock();
            if ((transitions & TRANSITION_MATCH_MATCH) != 0) {
                matchMatchListeners.put(tmpl, listener);
            }
            if ((transitions & TRANSITION_MATCH_NOMATCH) != 0) {
                matchNoMatchListeners.put(tmpl, listener);
            }
            if ((transitions & TRANSITION_NOMATCH_MATCH) != 0) {
                noMatchMatchListeners.put(tmpl, listener);
            }
            List templates = (List)listeners.get(listener);
            if (templates == null) {
                templates = new LinkedList();
                listeners.put(listener, templates);
            }
            templates.add(tmpl);
            return new ServiceEventRegistrationImpl(this, listener, EVENT_ID, sequenceID);
        }
        finally {
            matchLock.writeLock().unlock();
        }
    }

    public ServiceRegistration register(ServiceItem item) {
        // Generate a service id if needed
        if (item.serviceID == null) {
            item.serviceID = new ServiceID();
        }

        // Register the service
        Object result = null;
        try {
            matchLock.writeLock().lock();
            result = idTable.put(item.serviceID, item);
        }
        finally {
            matchLock.writeLock().unlock();
        }

        // Handle transition notifications
        // This can trigger a nomatch-match or match-match event
        if (result == null) { // nomatch-match
            notifyTransitions(noMatchMatchListeners, TRANSITION_NOMATCH_MATCH, item);
        }
        else { // match-match
            notifyTransitions(matchMatchListeners, TRANSITION_MATCH_MATCH, item);
        }
        return new ServiceRegistrationImpl(this, item.serviceID);
    }

    private void notifyTransitions(Map listenerMap, int transition, ServiceItem item) {
        LinkedList notifyListenerList = new LinkedList();
        try {
            matchLock.readLock().lock();
            Iterator matches = listenerMap.keySet().iterator();
            while (matches.hasNext()) {
                ServiceTemplate tmpl = (ServiceTemplate)matches.next();
                EventListener listener = (EventListener)listenerMap.get(tmpl);
                if (isMatch(tmpl, item)) {
                    notifyListenerList.add(listener);
                }
            }
        }
        finally {
            matchLock.readLock().unlock();
        }
        Iterator notifyListeners = notifyListenerList.iterator();
        while (notifyListeners.hasNext()) {
            EventListener listener = (EventListener)notifyListeners.next();
            try {
                listener.notifyEvent(new ServiceEvent(this,
                        EVENT_ID,
                        sequenceID++,
                        null,
                        item,
                        id,
                        transition));
            }
            catch (UnknownEventException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Remove a service (only should be called by a ServiceRegistrationImpl.cancel()
     *
     * @param removeID The service id of the servce to remove
     */
    final void remove(ServiceID removeID) {
        ServiceItem item;
        // Remove registration
        try {
            matchLock.writeLock().lock();
            item = (ServiceItem)idTable.remove(removeID);
        }
        finally {
            matchLock.writeLock().unlock();
        }
        // Notify listeners
        if (item != null) {
            notifyTransitions(matchNoMatchListeners, TRANSITION_MATCH_NOMATCH, item);
        }
    }

    /**
     * <p>Remove a service event listener
     * (only should be called by ServiceEventRegistrationImpl.cancel()).</p>
     *
     * @param listener The event listener to remove
     */
    final void remove(EventListener listener) {
        try {
            matchLock.writeLock().lock();
            List templates = (List)listeners.remove(listener);
            if (templates != null) {
                Iterator templateItr = templates.iterator();
                while (templateItr.hasNext()) {
                    Object template = templateItr.next();
                    matchMatchListeners.remove(template);
                    matchNoMatchListeners.remove(template);
                    noMatchMatchListeners.remove(template);
                }
            }
        }
        finally {
            matchLock.writeLock().unlock();
        }
    }

    /**
     * Sets the attributes for an entry.
     * Only should be called by a ServiceRegistrationImpl.setAttributes()
     *
     * @param serviceID  The id of the service to modify
     * @param attributes The new attributes for that service
     */
    final void setAttributes(ServiceID serviceID, Entry[] attributes) {
        try {
            matchLock.writeLock().lock();
            ServiceItem item = (ServiceItem)idTable.get(serviceID);
            item.attributes = attributes;
        }
        finally {
            matchLock.writeLock().unlock();
        }
    }
}
