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

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.CollectionNode;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.xep0398.PEPAvatar;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
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

    private final static String GET_PEP_SERVICE = "SELECT DISTINCT serviceID FROM ofPubsubNode WHERE serviceID=?";

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
     * @param jid
     *            the JID of the user that owns the PEP service.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService(JID jid) {
        return getPEPService( jid.toBareJID() );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * @param jid
     *            the bare JID of the user that owns the PEP service.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService(String jid) {
        PEPService pepService;

        final Lock lock = CacheFactory.getLock(jid, pepServices);
        try {
            lock.lock();
            if (pepServices.containsKey(jid)) {
                // lookup in cache
                pepService = pepServices.get(jid).get();
            } else {
                // lookup in database.
                pepService = loadPEPServiceFromDB(jid);

                // always add to the cache, even if it doesn't exist. This will
                // prevent future database lookups.
                pepServices.put(jid, CacheableOptional.of(pepService));
            }
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

        PEPService pepService;
        final String bareJID = owner.toBareJID();
        final Lock lock = CacheFactory.getLock(owner, pepServices);
        try {
            lock.lock();

            pepService = pepServices.get(bareJID).get();
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
     * Loads a PEP service from the database, if it exists.
     *
     * @param jid
     *            the JID of the owner of the PEP service.
     * @return the loaded PEP service, or null if not found.
     */
    private PEPService loadPEPServiceFromDB(String jid) {
        PEPService pepService = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            // Get all PEP services
            pstmt = con.prepareStatement(GET_PEP_SERVICE);
            pstmt.setString(1, jid);
            rs = pstmt.executeQuery();
            // Restore old PEPServices
            while (rs.next()) {
                String serviceID = rs.getString(1);

                // Create a new PEPService
                pepService = new PEPService(XMPPServer.getInstance(), serviceID);
                pepServices.put(serviceID, CacheableOptional.of(pepService));
                pubSubEngine.start(pepService);

                if (Log.isDebugEnabled()) {
                    Log.debug("PEP: Restored service for " + serviceID
                            + " from the database.");
                }
            }
        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
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

    private void deleteVCardAvatar(JID from)
    {
        Element vcard = XMPPServer.getInstance().getVCardManager().getVCard(from.getNode());
        Element vcardphoto = vcard.element("PHOTO");

        if (vcardphoto!=null)
        {
            vcard.remove(vcardphoto);
            try
            {
                XMPPServer.getInstance().getVCardManager().setVCard(from.getNode(), vcard);
            }
            catch (Exception e)
            {
                Log.error("Could not update vcard: "+e.getMessage());
            }
        }
    }

    //Send VCARD Presence
    private void sendVCardPresence(JID from, String id)
    {
        User usr;
        try 
        {
            usr = XMPPServer.getInstance().getUserManager().getUser(from.getNode());
            Presence presenceStanza = XMPPServer.getInstance().getPresenceManager().getPresence(usr);
            presenceStanza.setID(UUID.randomUUID().toString());
            if (presenceStanza.getFrom()==null)
            {
                presenceStanza.setFrom(from);
            }

            Element x = presenceStanza.addChildElement("x", PEPAvatar.NAMESPACE_VCARDUPDATE);
            Element photo = x.addElement("photo");                    
            
            if (id!=null)
            {
                photo.setText(id);
            }

            XMPPServer.getInstance().getPresenceRouter().route(presenceStanza);
        }
        catch (UserNotFoundException e) 
        {
            Log.error("Could not send presence: "+e.getMessage());
        }
    }

    public void process(PEPService service, IQ iq)
    {
        pubSubEngine.process(service, iq);

        if (JiveGlobals.getBooleanProperty(PEPAvatar.PROPERTY_ENABLE_XEP398,false)&&iq!=null)
        {
            Element childElement = iq.getChildElement();
            if (childElement!=null)
            {
                String childns = childElement.attributeValue("xmlns");

                //Check if IQ stanza is a pep avatar metadata node
                if (childns!=null)
                {
                    //metadata node with new item
                    if (childns.equalsIgnoreCase("http://jabber.org/protocol/pubsub")&&
                       (childElement.element("publish")!=null&&
                        childElement.element("publish").attributeValue("xmlns").
                        equalsIgnoreCase(PEPAvatar.NAMESPACE_METADATA)))
                        {
                            Element publish = childElement.element("publish");
                            Element item = publish.element("item");
                            if (item!=null)
                            {
                                Element metadata=item.element("metadata");
                                if (metadata!=null&&metadata.element("info")!=null)
                                {
                                    sendVCardPresence(iq.getFrom(),metadata.element("info").attributeValue("id"));
                                }
                            }
                        }
                        else //metadatanode which should be removed
                            if (childns.equalsIgnoreCase("http://jabber.org/protocol/pubsub")&&
                               ((childElement.element("retract")!=null&&
                                childElement.element("retract").attributeValue("xmlns").
                                equalsIgnoreCase(PEPAvatar.NAMESPACE_METADATA))||
                                (childElement.element("delete")!=null&&
                                 childElement.element("delete").attributeValue("xmlns").
                                 equalsIgnoreCase(PEPAvatar.NAMESPACE_METADATA))))
                                {
                                    deleteVCardAvatar(iq.getFrom());
                                    sendVCardPresence(iq.getFrom(),null);
                                }
                }
            }
        }
    }

    public boolean hasCachedService(JID owner) {
        return pepServices.get(owner.toBareJID()) != null;
    }

    // mimics Shutdown, without killing the timer.
    public void unload(PEPService service) {
        pubSubEngine.shutdown(service);
    }
}
