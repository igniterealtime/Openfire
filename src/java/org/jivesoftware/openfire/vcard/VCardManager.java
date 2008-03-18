/**
 * $RCSfile$
 * $Revision: 1651 $
 * $Date: 2005-07-20 00:20:39 -0300 (Wed, 20 Jul 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.event.UserEventAdapter;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Manages VCard information for users.
 *
 * @author Matt Tucker
 */
public class VCardManager extends BasicModule implements ServerFeaturesProvider {

    private VCardProvider provider;
    private static VCardManager instance;

    private EventHandler eventHandler;

    private Cache<String, Element> vcardCache;
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
        this.eventHandler = new EventHandler();


        // Keeps the cache updated in case the vCard action was not performed by VCardManager
        VCardEventDispatcher.addListener(new VCardListener() {
            public void vCardCreated(String username, Element vCard) {
                // Since the vCard could be created by the provider, add it to the cache.
                vcardCache.put(username, vCard);
            }

            public void vCardUpdated(String username, Element vCard) {
                // Since the vCard could be updated by the provider, update it to the cache.
                vcardCache.put(username, vCard);
            }

            public void vCardDeleted(String username, Element vCard) {
                // Since the vCard could be delated by the provider, remove it to the cache.
                vcardCache.remove(username);
            }
        });
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
     * @throws Exception if an error occured while storing the new vCard.
     */
    public void setVCard(String username, Element vCardElement) throws Exception {
        boolean created = false;
        boolean updated = false;

        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("VCard provider is read-only.");
        }
        Element oldVCard = getOrLoadVCard(username);
        Element newvCard = null;
        // See if we need to update the vCard or insert a new one.
        if (oldVCard != null) {
            // Only update the vCard in the database if the vCard has changed.
            if (!oldVCard.equals(vCardElement)) {
                try {
                    newvCard = provider.updateVCard(username, vCardElement);
                    vcardCache.put(username, newvCard);
                    updated = true;
                }
                catch (NotFoundException e) {
                    Log.warn("Tried to update a vCard that does not exist", e);
                    newvCard = provider.createVCard(username, vCardElement);
                    vcardCache.put(username, newvCard);
                    created = true;
                }
            }
        }
        else {
            try {
                newvCard = provider.createVCard(username, vCardElement);
                vcardCache.put(username, newvCard);
                created = true;
            }
            catch (AlreadyExistsException e) {
                Log.warn("Tried to create a vCard when one already exist", e);
                newvCard = provider.updateVCard(username, vCardElement);
                vcardCache.put(username, newvCard);
                updated = true;
            }
        }
        // Dispatch vCard events
        if (created) {
            // Alert listeners that a new vCard has been created
            VCardEventDispatcher.dispatchVCardCreated(username, newvCard);
        } else if (updated) {
            // Alert listeners that a vCard has been updated
            VCardEventDispatcher.dispatchVCardUpdated(username, newvCard);
        }
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
        Element oldVCard = getOrLoadVCard(username);
        if (oldVCard != null) {
            vcardCache.remove(username);
            // Delete the property from the DB if it was present in memory
            provider.deleteVCard(username);
            // Alert listeners that a vCard has been deleted
            VCardEventDispatcher.dispatchVCardDeleted(username, oldVCard);
        }
    }

    /**
     * Returns the vCard of a given user or null if none was defined before. Changes to the
     * returned vCard will not be stored in the database. Use the returned vCard as a
     * read-only vCard.
     *
     * @param username Username (not full JID) whose vCard to retrieve.
     * @return the vCard of a given user.
     */
    public Element getVCard(String username) {
        Element vCardElement = getOrLoadVCard(username);
        return vCardElement == null ? null : vCardElement.createCopy();
    }

    private Element getOrLoadVCard(String username) {
        Element vCardElement = vcardCache.get(username);
        if (vCardElement == null) {
            vCardElement = provider.loadVCard(username);
            if (vCardElement != null) {
                vcardCache.put(username, vCardElement);
            }
        }
        return vCardElement;
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