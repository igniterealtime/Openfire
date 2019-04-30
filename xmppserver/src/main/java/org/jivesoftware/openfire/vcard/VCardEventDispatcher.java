/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches vCard events. The following events are supported:
 * <ul>
 * <li><b>VCardCreated</b> --&gt; A VCard has been created.</li>
 * <li><b>VCardDeleted</b> --&gt; A VCard has been deleted.</li>
 * <li><b>VCardUpdate</b> --&gt; A VCard has been updated.</li>
 * </ul>
 * Use {@link #addListener(org.jivesoftware.openfire.vcard.VCardListener)}
 * to add or remove {@link org.jivesoftware.openfire.vcard.VCardListener}.
 *
 * @author Gabriel Guardincerri
 */
public class VCardEventDispatcher {
    private static final Logger Log = LoggerFactory.getLogger(VCardEventDispatcher.class);
    /**
     * List of listeners that will be notified when vCards are created, updated or deleted.
     */
    private static List<VCardListener> listeners = new CopyOnWriteArrayList<>();

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
            try {
                listener.vCardUpdated(user, vCard); 
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'vCardUpdated' event!", e);
            }
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
            try {
                listener.vCardCreated(user, vCard);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'vCardCreated' event!", e);
            }
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
            try {
                listener.vCardDeleted(user, vCard);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'vCardDeleted' event!", e);
            }
        }
    }

}
