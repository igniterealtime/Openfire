/**
 * $RCSfile$
 * $Revision: 1651 $
 * $Date: 2005-07-20 00:20:39 -0300 (Wed, 20 Jul 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.event.UserEventAdapter;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.user.User;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages VCard information for users.
 *
 * @author Matt Tucker
 */
public class VCardManager extends BasicModule implements ServerFeaturesProvider {

    private VCardProvider provider;
    private static VCardManager instance;

    private Cache<String, Element> vcardCache;
    private final EventHandler eventHandler = new EventHandler();
    /**
     * List of listeners that will be notified when vCards are created, updated or deleted.
     */
    private List<VCardListener> listeners = new CopyOnWriteArrayList<VCardListener>();

    public static VCardManager getInstance() {
        return instance;
    }

    /**
     * Returns the currently-installed VCardProvider. <b>Warning:</b> in virtually all
     * cases the vcard provider should not be used directly. Instead, the appropriate
     * methods in VCardManager should be called. Direct access to the vcard provider is
     * only provided for special-case logic.
     *
     * @return the current VCardProvider.
     */
    public static VCardProvider getProvider() {
        return instance.provider;
    }

    public VCardManager() {
        super("VCard Manager");
        String cacheName = "VCard";
        vcardCache = CacheFactory.createCache(cacheName);
    }

    /**
     * Returns the user's vCard information for a given vcard property name. If the property
     * has no defined text then an empty string will be returned. However, if the property
     * does not exist then a <tt>null</tt> value will be answered. Advanced user systems can
     * use vCard information to link to user directory information or store other relevant
     * user information.</p>
     * Note that many elements in the vCard may have the same path so the returned value in that
     * case will be the first found element. For instance, "ADR:STREET" may be present in
     * many addresses of the user. Use {@link #getVCard(String)} to get the whole vCard of
     * the user.
     *
     * @param username The username of the user to return his vCard property.
     * @param name     The name of the vcard property to retrieve encoded with ':' to denote
     *                 the path.
     * @return The vCard value found
     */
    public String getVCardProperty(String username, String name) {
        String answer = null;
        Element vCardElement = getOrLoadVCard(username);
        if (vCardElement != null) {
            // A vCard was found for this user so now look for the correct element
            Element subElement = null;
            StringTokenizer tokenizer = new StringTokenizer(name, ":");
            while (tokenizer.hasMoreTokens()) {
                String tok = tokenizer.nextToken();
                if (subElement == null) {
                    subElement = vCardElement.element(tok);
                }
                else {
                    subElement = subElement.element(tok);
                }
                if (subElement == null) {
                    break;
                }
            }
            if (subElement != null) {
                answer = subElement.getTextTrim();
            }
        }
        return answer;
    }

    /**
     * Sets the user's vCard information. The new vCard information will be persistent. Advanced
     * user systems can use vCard information to link to user directory information or store
     * other relevant user information.
     *
     * @param username     The username of the user to set his new vCard.
     * @param vCardElement The DOM element sent by the user as his new vcard.
     */
    public void setVCard(String username, Element vCardElement) {
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("VCard provider is read-only.");
        }
        synchronized (getLock(username)) {
            provider.setVCard(username, vCardElement);
            vcardCache.put(username, vCardElement);
        }
        dispatchVCardSet(username);
    }

    /**
     * Deletes the user's vCard from the user account.
     *
     * @param username The username of the user to delete his vCard.
     * @throws UnsupportedOperationException If the provider is read-only and the data
     *         cannot be deleted, this exception is thrown
     */
    public void deleteVCard(String username) {
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("VCard provider is read-only.");
        }
        boolean deleted = false;
        synchronized (getLock(username)) {
            Element oldVCard = getOrLoadVCard(username);
            if (oldVCard != null) {
                // Delete the property from the DB if it was present in memory
                provider.deleteVCard(username);
                vcardCache.remove(username);
                deleted = true;
            }
        }
        if (deleted) {
            // Alert listeners that a vCard has been deleted
            dispatchVCardDeleted(username);
        }
    }

    /**
     * Returns the vCard of a given user or null if none was defined before. Changes to the
     * returned vCard will not be stored in the database. Use the returned vCard as a
     * read-only vCard.
     *
     * @return the vCard of a given user.
     * @param username the username of the user whose vCard is being retrieved.
     */
    public Element getVCard(String username) {
        Element vCardElement = getOrLoadVCard(username);
        return vCardElement == null ? null : vCardElement.createCopy();
    }

    private Element getOrLoadVCard(String username) {
        synchronized (getLock(username)) {
            Element vCardElement = vcardCache.get(username);
            if (vCardElement == null) {
                vCardElement = provider.loadVCard(username);
                if (vCardElement != null) {
                    vcardCache.put(username, vCardElement);
                }
            }
            return vCardElement;
        }
    }

    private Object getLock(String username) {
        return (username + VCardManager.class.toString()).intern();
    }

    /**
     * Registers a listener to receive events when a vCard is created, updated or deleted.
     *
     * @param listener the listener.
     */
    public void addListener(VCardListener listener) {
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
    public void removeListener(VCardListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns true if vCards are read-only. When vCards are read-only and #setVCard is called an
     * unsupported operation exception is thrown.
     * 
     * @return true if vCards are read-only.
     */
    public boolean isReadOnly() {
        return provider.isReadOnly();
    }

    /**
     * Dispatches that a vCard was updated to all listeners.
     *
     * @param user the user for which the vCard was set.
     */
    private void dispatchVCardSet(String user) {
        for (VCardListener listener : listeners) {
            listener.vCardSet(user);
        }
    }

    /**
     * Dispatches that a vCard was deleted to all listeners.
     *
     * @param user the user for which the vCard was deleted.
     */
    private void dispatchVCardDeleted(String user) {
        for (VCardListener listener : listeners) {
            listener.vCardDeleted(user);
        }
    }

    public void initialize(XMPPServer server) {
        instance = this;

        // Load a VCard provider.
        String className = JiveGlobals.getXMLProperty("provider.vcard.className",
                DefaultVCardProvider.class.getName());
        try {
            Class c = ClassUtils.forName(className);
            provider = (VCardProvider) c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading vcard provider: " + className, e);
            provider = new DefaultVCardProvider();
        }
    }

    public void start() {
        // Add this module as a user event listener so we can delete
        // all user properties when a user is deleted
        if (!provider.isReadOnly()) {
            UserEventDispatcher.addListener(eventHandler);
        }
    }

    public void stop() {
        // Remove this module as a user event listener
        UserEventDispatcher.removeListener(eventHandler);
    }

    /**
     * Resets the manager state. The cache where loaded vCards are stored will be flushed.
     */
    public void reset() {
        vcardCache.clear();
    }

    public Iterator<String> getFeatures() {
        ArrayList<String> features = new ArrayList<String>();
        features.add("vcard-temp");
        return features.iterator();
    }

    private class EventHandler extends UserEventAdapter {
        public void userDeleting(User user, Map params) {
            try {
                deleteVCard(user.getUsername());
            } catch (UnsupportedOperationException ue) { /* Do Nothing */ }
        }
    }
}