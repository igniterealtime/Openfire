/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.pep;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.disco.ServerIdentitiesProvider;
import org.jivesoftware.openfire.disco.UserIdentitiesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.pubsub.LeafNode;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * An IQHandler used to implement XEP-0163: "Personal Eventing via Pubsub."
 * </p>
 * 
 * <p>
 * For each user on the server there is an associated PEPService interacting
 * with a PubSubEngine for managing the user's PEP nodes.
 * </p>
 * 
 * <p>
 * An IQHandler can only handle one namespace in its IQHandlerInfo. However, PEP
 * related packets are seen having a variety of different namespaces. Thus,
 * classes like IQPEPOwnerHandler are used to forward packets having these other
 * namespaces to IQPEPHandler.handleIQ().
 * <p>
 * 
 * <p>
 * This handler is used for the following namespaces:
 * <ul>
 * <li><i>http://jabber.org/protocol/pubsub</i></li>
 * <li><i>http://jabber.org/protocol/pubsub#owner</i></li>
 * </ul>
 * </p>
 * 
 * @author Armando Jagucki
 * 
 */
public class IQPEPHandler extends IQHandler implements ServerIdentitiesProvider,
        ServerFeaturesProvider, UserIdentitiesProvider {

    // Map of PEP services. Table, Key: bare JID (String); Value: PEPService
    private Map<String, PEPService> pepServices;

    private IQHandlerInfo info;

    private PubSubEngine pubSubEngine = null;
    
    private UserManager userManager = null;

    public IQPEPHandler() {
        super("Personal Eventing Handler");
        pepServices = new ConcurrentHashMap<String, PEPService>();
        info = new IQHandlerInfo("pubsub", "http://jabber.org/protocol/pubsub");
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        
        pubSubEngine = new PubSubEngine(server.getPacketRouter());
        userManager = server.getUserManager();

    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // TODO: Much to be done here...

        if (packet.getTo() == null) {
            String jidFrom = packet.getFrom().toBareJID();
            
            PEPService pepService = pepServices.get(jidFrom);

            // If no service exists yet for jidFrom, create one.
            if (pepService == null) {
                // Return an error if the packet is from an anonymous or otherwise
                // unregistered user.
                if (!userManager.isRegisteredUser(packet.getFrom())) {
                    IQ reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(PacketError.Condition.not_allowed);
                    return reply;
                }
                
                pepService = new PEPService(XMPPServer.getInstance(), jidFrom);
                pepServices.put(jidFrom, pepService);
                
                // Probe presences
                pubSubEngine.start(pepService);
                if (Log.isDebugEnabled()) {
                    Log.debug("PEP: " + jidFrom + " had a PEPService created");
                }
            }

            // If publishing a node, and the node doesn't exist, create it.
            if (packet.getType() == IQ.Type.set) {
                Element childElement = packet.getChildElement();
                Element publishElement = childElement.element("publish");
                if (publishElement != null) {
                    String nodeID = publishElement.attributeValue("node");
                    
                    if (pepService.getNode(nodeID) == null) {
                        // Create the node
                        JID creator = new JID(jidFrom);
                        LeafNode newNode = new LeafNode(pepService, null, nodeID, creator);
                        newNode.addOwner(creator);
                        newNode.saveToDB();
                        if (Log.isDebugEnabled()) {
                            Log.debug("PEP: Created node ('" + nodeID + "') for " + jidFrom);
                        }
                    }
                }                        
            }
            
            // Process with PubSub as usual.
            if (pubSubEngine.process(pepService, packet)) {
                if (Log.isDebugEnabled()) {
                    Log.debug("PEP: The pubSubEngine processed a packet for " + jidFrom + "'s pepService.");
                }
            }
            else {
                if (Log.isDebugEnabled()) {
                    Log.debug("PEP: The pubSubEngine did not process a packet for " + jidFrom + "'s pepService.");
                }
            }

        }
        else {
            // TODO: Ensure the packet gets handled elsewhere.
        }

        return null; // Error flows are handled in pubSubEngine.process(...)
    }

    public void start() {
        super.start();
        for (PEPService service : pepServices.values()) {
            pubSubEngine.start(service);
        }
    }

    public void stop() {
        super.stop();
        for (PEPService service : pepServices.values()) {
            pubSubEngine.shutdown(service);
        }
    }

    /**
     * Implements ServerIdentitiesProvider and UserIdentitiesProvider, adding
     * the PEP identity to the respective disco#info results.
     */
    public Iterator<Element> getIdentities() {
        ArrayList<Element> identities = new ArrayList<Element>();
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "pubsub");
        identity.addAttribute("type", "pep");
        identities.add(identity);
        return identities.iterator();
    }

    /**
     * Implements ServerFeaturesProvider to include all supported XEP-0060 features
     * in the server's disco#info result (as per section 4 of XEP-0163).
     */
    public Iterator<String> getFeatures() {
        return XMPPServer.getInstance().getPubSubModule().getFeatures(null, null, null);
    }

}
