/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.disco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * IQDiscoItemsHandler is responsible for handling disco#items requests. This class holds a map with
 * the main entities and the associated DiscoItemsProvider. We are considering the host of the
 * recipient JIDs as main entities. It's the DiscoItemsProvider responsibility to provide the items
 * associated with the JID's name together with any possible requested node.<p>
 * <p/>
 * For example, let's have in the entities map the following entries: "localhost" and
 * "conference.localhost". Associated with each entry we have different DiscoItemsProvider. Now we
 * receive a disco#items request for the following JID: "room@conference.localhost" which is a disco
 * request for a MUC room. So IQDiscoItemsHandler will look for the DiscoItemsProvider associated
 * with the JID's host which in this case is "conference.localhost". Once we have located the
 * provider we will delegate to the provider the responsibility to provide the items specific to
 * the JID's name which in this case is "room". Depending on the implementation, the items could be
 * the list of existing occupants if that information is publicly available. Finally, after we have
 * collected all the items provided by the provider we will add them to the reply. On the other
 * hand, if no provider was found or the provider has no information for the requested name/node
 * then a not-found error will be returned.<p>
 * <p/>
 * Publishing of client items is still not supported.
 *
 * @author Gaston Dombiak
 */
public class IQDiscoItemsHandler extends IQDiscoHandler implements ServerFeaturesProvider {

    private HashMap entities = new HashMap();
    private List serverItems = new ArrayList();
    private IQHandlerInfo info;
    public IQDiscoInfoHandler infoHandler;

    public IQDiscoItemsHandler() {
        super("XMPP Disco Items Handler");
        info = new IQHandlerInfo("query", "http://jabber.org/protocol/disco#items");
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // TODO Let configure an authorization policy (ACL?). Currently anyone can discover items.
        
        // Create a copy of the sent pack that will be used as the reply
        // we only need to add the requested items to the reply if any otherwise add 
        // a not found error
        IQ reply = IQ.createResultIQ(packet);
        reply.setType(IQ.Type.result);
        reply.setTo(packet.getFrom());
        reply.setFrom(packet.getFrom());
        
        // TODO Implement publishing client items
        if (IQ.Type.set == packet.getType()) {
            reply.setError(PacketError.Condition.feature_not_implemented);
            return reply;
        }

        // Look for a DiscoItemsProvider associated with the requested entity.
        // We consider the host of the recipient JID of the packet as the entity. It's the 
        // DiscoItemsProvider responsibility to provide the items associated with the JID's name  
        // together with any possible requested node.  
        DiscoItemsProvider itemsProvider = getProvider(packet.getTo().getDomain());
        if (itemsProvider != null) {
            // Get the JID's name
            String name = packet.getTo().getNode();
            if (name == null || name.trim().length() == 0) {
                name = null;
            }
            // Get the requested node
            Element iq = packet.getChildElement();
            String node = iq.attributeValue("node");
            //String node = metaData.getProperty("query:node");
            
            // Check if we have items associated with the requested name and node
            Iterator itemsItr = itemsProvider.getItems(name, node, packet.getFrom());
            if (itemsItr != null) {
                Element queryElement = reply.getChildElement();

                // Add to the reply all the items provided by the DiscoItemsProvider
                Element item;
                while (itemsItr.hasNext()) {
                    item = (Element)itemsItr.next();
                    queryElement.add((Element)item.clone());
                }
                ;
            }
            else {
                // If the DiscoItemsProvider has no items for the requested name and node 
                // then answer a not found error
                reply.setError(PacketError.Condition.item_not_found);
            }
        }
        else {
            // If we didn't find a DiscoItemsProvider then answer a not found error
           reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    /**
     * Returns the DiscoItemsProvider responsible for providing the items related to a given entity
     * or null if none was found.
     *
     * @param name the name of the identity.
     * @return the DiscoItemsProvider responsible for providing the items related to a given entity
     *         or null if none was found.
     */
    private DiscoItemsProvider getProvider(String name) {
        return (DiscoItemsProvider)entities.get(name);
    }

    /**
     * Sets that a given DiscoItemsProvider will provide the items related to a given entity. This
     * message must be used when new modules (e.g. MUC) are implemented and need to provide
     * the items related to them.
     *
     * @param name     the name of the entity.
     * @param provider the DiscoItemsProvider that will provide the entity's items.
     */
    protected void setProvider(String name, DiscoItemsProvider provider) {
        entities.put(name, provider);
    }

    /**
     * Removes the DiscoItemsProvider related to a given entity.
     *
     * @param name the name of the entity.
     */
    protected void removeProvider(String name) {
        entities.remove(name);
    }

    /**
     * Adds the items provided by the new service that implements the ServerItemsProvider
     * interface. This information will be used whenever a disco for items is made against
     * the server (i.e. the packet's target is the server).
     * Example of item is: &lt;item jid='conference.localhost' name='Public chatrooms'/&gt;
     *
     * @param provider the ServerItemsProvider that provides new server items.
     */
    public void addServerItemsProvider(ServerItemsProvider provider) {
        DiscoServerItem discoItem;
        for (Iterator it = provider.getItems(); it.hasNext();) {
            discoItem = (DiscoServerItem)it.next();
            // Create a new element based on the provided DiscoItem
            Element element = DocumentHelper.createElement("item");
            element.addAttribute("jid", discoItem.getJID());
            element.addAttribute("node", discoItem.getNode());
            element.addAttribute("name", discoItem.getName());
            // Add the element to the list of items related to the server
            serverItems.add(element);
            
            // Add the new item as a valid entity that could receive info and items disco requests
            String host = StringUtils.parseServer(discoItem.getJID());
            infoHandler.setProvider(host, discoItem.getDiscoInfoProvider());
            setProvider(host, discoItem.getDiscoItemsProvider());
        }
    }

    /**
     * Removes the items provided by the service that implements the ServerItemsProvider
     * interface which is being removed. Example of item is:
     * &lt;item jid='conference.localhost' name='Public chatrooms'/&gt;
     *
     * @param provider the ServerItemsProvider that was providing server items.
     */
    public void removeServerItemsProvider(ServerItemsProvider provider) {
        DiscoItem discoItem;
        for (Iterator it = provider.getItems(); it.hasNext();) {
            discoItem = (DiscoItem)it.next();
            // Locate the element that represents the DiscoItem to remove
            Element element = null;
            boolean found = false;
            for (Iterator itemsItr = serverItems.iterator(); !found && it.hasNext();) {
                element = (Element)itemsItr.next();
                if (discoItem.getJID().equals(element.attributeValue("jid"))) {
                    found = true;
                    break;
                }
            }
            // If the element was found then remove it from the items provided by the server
            if (found) {
                serverItems.remove(element);
            }
            // Remove the item as a valid entity that could receive info and items disco requests
            String host = StringUtils.parseServer(discoItem.getJID());
            infoHandler.removeProvider(host);
            removeProvider(host);
        }
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        // Track the implementors of ServerItemsProvider so that we can collect the items
        // provided by the server
        trackInfo.getTrackerClasses().put(ServerItemsProvider.class, "ServerItemsProvider");
        trackInfo.getTrackerClasses().put(IQDiscoInfoHandler.class, "infoHandler");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (service instanceof XMPPServer) {
            setProvider(((XMPPServer)service).getServerInfo().getName(), getServerItemsProvider());
        }
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("http://jabber.org/protocol/disco#items");
        // TODO Comment out this line when publishing of client items is implemented
        //features.add("http://jabber.org/protocol/disco#publish");
        return features.iterator();
    }

    private DiscoItemsProvider getServerItemsProvider() {
        DiscoItemsProvider discoItemsProvider = new DiscoItemsProvider() {
            public Iterator getItems(String name, String node, JID senderJID)
                    throws UnauthorizedException {
                return serverItems.iterator();
            }
        };
        return discoItemsProvider;
    }

}
