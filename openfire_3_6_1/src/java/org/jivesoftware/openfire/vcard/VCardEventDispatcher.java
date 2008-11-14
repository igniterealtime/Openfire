/**
 * $RCSfile$
 * $Revision$
 * $Date: 2005-07-26 19:10:33 +0200 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches vCard events. The following events are supported:
 * <ul>
 * <li><b>VCardCreated</b> --> A VCard has been created.</li>
 * <li><b>VCardDeleted</b> --> A VCard has been deleted.</li>
 * <li><b>VCardUpdate</b> --> A VCard has been updated.</li>
 * </ul>
 * Use {@link #addListener(org.jivesoftware.openfire.vcard.VCardListener)}
 * to add or remove {@link org.jivesoftware.openfire.vcard.VCardListener}.
 *
 * @author Gabriel Guardincerri
 */
public class VCardEventDispatcher {
    /**
     * List of listeners that will be notified when vCards are created, updated or deleted.
     */
    private static List<VCardListener> listeners = new CopyOnWriteArrayList<VCardListener>();

    /**
     * Registers a listener to receive events when a vCard is created, updated or deleted.
     *
     * @param listener the listener.
     */
    public static void addListener(VCardListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(VCardListener listener) {
        listeners.remove(listener);
    }

    /**
     * Dispatches that a vCard was updated to all listeners.
     *
     * @param user the user for which the vCard was set.
     * @param vCard the vcard updated.
     */
    public static void dispatchVCardUpdated(String user, Element vCard) {
        for (VCardListener listener : listeners) {
            listener.vCardUpdated(user, vCard);
        }
    }

    /**
     * Dispatches that a vCard was created to all listeners.
     *
     * @param user the user for which the vCard was created.
     * @param vCard the vcard created.
     */
    public static void dispatchVCardCreated(String user, Element vCard) {
        for (VCardListener listener : listeners) {
            listener.vCardCreated(user, vCard);
        }
    }

    /**
     * Dispatches that a vCard was deleted to all listeners.
     *
     * @param user the user for which the vCard was deleted.
     * @param vCard the vcard deleted.
     */
    public static void dispatchVCardDeleted(String user, Element vCard) {
        for (VCardListener listener : listeners) {
            listener.vCardDeleted(user, vCard);
        }
    }

}
