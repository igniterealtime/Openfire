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
package org.jivesoftware.openfire.pep;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.concurrent.locks.Lock;

/**
 * Manages the creation, persistence and removal of {@link PEPService}
 * instances.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 *
 */
public class PEPServiceManager {

    public static final Logger Log = LoggerFactory
            .getLogger(PEPServiceManager.class);

    /**
     * Cache of PEP services. Table, Key: bare JID (String); Value: PEPService
     */
    private final Cache<String, CacheableOptional<PEPService>> pepServices = CacheFactory
        .createLocalCache("PEPServiceManager");

    private PubSubEngine pubSubEngine = null;

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method will automatically create a PEP service if one does not exist.
     *
     * @param uniqueIdentifier
     *            the unique identifier of the PEP service.
     * @return the requested PEP service.
     */
    public PEPService getPEPService( PubSubService.UniqueIdentifier uniqueIdentifier )
    {
        return getPEPService( uniqueIdentifier, true );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method can automatically create a PEP service if one does not exist.
     *
     * @param uniqueIdentifier
     *            the unique identifier of the PEP service.
     * @param autoCreate
     *            true if a PEP service that does not yet exist needs to be created.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService( PubSubService.UniqueIdentifier uniqueIdentifier, boolean autoCreate )
    {
        // PEP Services use the JID as their service identifier.
        final JID needle;
        try {
            needle = new JID(uniqueIdentifier.getServiceId());
        } catch (IllegalArgumentException ex) {
            Log.warn( "Unable to get PEP service. Provided unique identifier does not contain a valid JID: " + uniqueIdentifier, ex );
            return null;
        }
        return getPEPService( needle, autoCreate );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method will automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the JID of the user that owns the PEP service.
     * @return the requested PEP service.
     */
    public PEPService getPEPService( JID jid ) {
        return getPEPService( jid, true );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method can automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the JID of the user that owns the PEP service.
     * @param autoCreate
     *            true if a PEP service that does not yet exist needs to be created.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService( JID jid, boolean autoCreate ) {
        return getPEPService( jid.toBareJID(), autoCreate );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method will automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the bare JID of the user that owns the PEP service.
     * @return the requested PEP service.
     */
    public PEPService getPEPService( String jid ) {
        return getPEPService( jid, true );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method can automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the bare JID of the user that owns the PEP service.
     * @param autoCreate
     *            true if a PEP service that does not yet exist needs to be created.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService( String jid, boolean autoCreate ) {
        PEPService pepService;

        final Lock lock = CacheFactory.getLock(jid, pepServices);
        try {
            lock.lock();
            if (pepServices.containsKey(jid)) {
                // lookup in cache
                if ( pepServices.get(jid).isAbsent() && autoCreate ) {
                    // needs auto-create despite negative cache.
                    pepService = null;
                } else {
                    return pepServices.get(jid).get();
                }
            } else {
                // lookup in database.
                pepService = PubSubPersistenceProviderManager.getInstance().getProvider().loadPEPServiceFromDB(jid);
            }

            if ( pepService != null ) {
                Log.debug("PEP: Restored service for {} from the database.", jid);
                pubSubEngine.start(pepService);
            } else if (autoCreate) {
                Log.debug("PEP: Auto-created service for {}.", jid);
                pepService = this.create(new JID( jid ));

                // Probe presences
                pubSubEngine.start(pepService);

                // Those who already have presence subscriptions to jidFrom
                // will now automatically be subscribed to this new
                // PEPService.
                XMPPServer.getInstance().getIQPEPHandler().addSubscriptionForRosterItems( pepService );
            }
            pepServices.put(jid, CacheableOptional.of(pepService));
        } finally {
            lock.unlock();
        }

        return pepService;
    }

    public PEPService create(JID owner) {
        // Return an error if the packet is from an anonymous, unregistered user
        // or remote user
        if (!XMPPServer.getInstance().isLocal(owner)
                || !UserManager.getInstance().isRegisteredUser(owner.getNode())) {
            throw new IllegalArgumentException(
                    "Request must be initiated by a local, registered user, but is not: "
                            + owner);
        }

        PEPService pepService = null;
        final String bareJID = owner.toBareJID();
        final Lock lock = CacheFactory.getLock(owner, pepServices);
        try {
            lock.lock();

            if (pepServices.get(bareJID) != null) {
                pepService = pepServices.get(bareJID).get();
            }

            if (pepService == null) {
                pepService = new PEPService(XMPPServer.getInstance(), bareJID);
                pepServices.put(bareJID, CacheableOptional.of(pepService));

                if (Log.isDebugEnabled()) {
                    Log.debug("PEPService created for : " + bareJID);
                }
            }
        } finally {
            lock.unlock();
        }

        return pepService;
    }

    /**
     * Deletes the {@link PEPService} belonging to the specified owner.
     *
     * @param owner
     *            The JID of the owner of the service to be deleted.
     */
    public void remove(JID owner) {
        PEPService service;

        final Lock lock = CacheFactory.getLock(owner, pepServices);
        try {
            lock.lock();
            service = pepServices.remove(owner.toBareJID()).get();
        } finally {
            lock.unlock();
        }

        if (service == null) {
            return;
        }

        // Delete the user's PEP nodes from memory and the database.
        CollectionNode rootNode = service.getRootCollectionNode();
        for (final Node node : service.getNodes()) {
            if (rootNode.isChildNode(node)) {
                node.delete();
            }
        }
        rootNode.delete();
    }

    public void start(PEPService pepService) {
        pubSubEngine.start(pepService);
    }

    public void start() {
        pubSubEngine = new PubSubEngine(XMPPServer.getInstance()
                .getPacketRouter());
    }

    public void stop() {

        for (final CacheableOptional<PEPService> service : pepServices.values()) {
            if (service.isPresent()) {
                pubSubEngine.shutdown(service.get());
            }
        }

        pubSubEngine = null;
    }

    public void process(PEPService service, IQ iq) {
        pubSubEngine.process(service, iq);
    }

    public boolean hasCachedService(JID owner) {
        return pepServices.get(owner.toBareJID()) != null;
    }

    // mimics Shutdown, without killing the timer.
    public void unload(PEPService service) {
        pubSubEngine.shutdown(service);
    }
}
