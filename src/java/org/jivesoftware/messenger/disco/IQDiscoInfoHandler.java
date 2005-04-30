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

import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.JID;

/**
 * IQDiscoInfoHandler is responsible for handling disco#info requests. This class holds a map with
 * the main entities and the associated DiscoInfoProvider. We are considering the host of the
 * recipient JIDs as main entities. It's the DiscoInfoProvider responsibility to provide information
 * about the JID's name together with any possible requested node.<p>
 * <p/>
 * For example, let's have in the entities map the following entries: "localhost" and
 * "conference.localhost". Associated with each entry we have different DiscoInfoProviders. Now we
 * receive a disco#info request for the following JID: "room@conference.localhost" which is a disco
 * request for a MUC room. So IQDiscoInfoHandler will look for the DiscoInfoProvider associated
 * with the JID's host which in this case is "conference.localhost". Once we have located the
 * provider we will delegate to the provider the responsibility to provide the info specific to
 * the JID's name which in this case is "room". Among the information that a room could provide we
 * could find its identity and the features it supports (e.g. 'muc_passwordprotected',
 * 'muc_unmoderated', etc.). Finally, after we have collected all the information provided by the
 * provider we will add it to the reply. On the other hand, if no provider was found or the provider
 * has no information for the requested name/node then a not-found error will be returned.
 *
 * @author Gaston Dombiak
 */
public class IQDiscoInfoHandler extends IQHandler {

    private HashMap entities = new HashMap();
    private List serverFeatures = new ArrayList();
    private IQHandlerInfo info;

    private List userIdentities = new ArrayList();
    private List userFeatures = new ArrayList();

    public IQDiscoInfoHandler() {
        super("XMPP Disco Info Handler");
        info = new IQHandlerInfo("query", "http://jabber.org/protocol/disco#info");
        serverFeatures.add("http://jabber.org/protocol/disco#info");
        // Initialize the user identity and features collections (optimization to avoid creating
        // the same objects for each response)
        Element userIdentity = DocumentHelper.createElement("identity");
        userIdentity.addAttribute("category", "account");
        userIdentity.addAttribute("type", "registered");
        userIdentities.add(userIdentity);
        userFeatures.add("http://jabber.org/protocol/disco#info");
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // TODO Let configure an authorization policy (ACL?). Currently anyone can discover info.
        
        // Create a copy of the sent pack that will be used as the reply
        // we only need to add the requested info to the reply if any otherwise add 
        // a not found error
        IQ reply = IQ.createResultIQ(packet);

        // Look for a DiscoInfoProvider associated with the requested entity.
        // We consider the host of the recipient JID of the packet as the entity. It's the 
        // DiscoInfoProvider responsibility to provide information about the JID's name together 
        // with any possible requested node.  
        DiscoInfoProvider infoProvider = getProvider(packet.getTo() == null ?
                XMPPServer.getInstance().getServerInfo().getName() : packet.getTo().getDomain());
        if (infoProvider != null) {
            // Get the JID's name
            String name = packet.getTo() == null ? null : packet.getTo().getNode();
            if (name == null || name.trim().length() == 0) {
                name = null;
            }
            // Get the requested node
            Element iq = packet.getChildElement();
            String node = iq.attributeValue("node");
            //String node = metaData.getProperty("query:node");
            
            // Check if we have information about the requested name and node
            if (infoProvider.hasInfo(name, node, packet.getFrom())) {
                reply.setChildElement(iq.createCopy());
                Element queryElement = reply.getChildElement();

                // Add to the reply all the identities provided by the DiscoInfoProvider
                Element identity;
                Iterator identities = infoProvider.getIdentities(name, node, packet.getFrom());
                while (identities.hasNext()) {
                    identity = (Element)identities.next();
                    identity.setQName(new QName(identity.getName(), queryElement.getNamespace()));
                    queryElement.add((Element)identity.clone());
                }
                
                // Add to the reply all the features provided by the DiscoInfoProvider
                Iterator features = infoProvider.getFeatures(name, node, packet.getFrom());
                while (features.hasNext()) {
                    queryElement.addElement("feature").addAttribute("var", (String)features.next());
                }

                // Add to the reply the extended info (XDataForm) provided by the DiscoInfoProvider
                XDataFormImpl dataForm = infoProvider.getExtendedInfo(name, node, packet.getFrom());
                if (dataForm != null) {
                    queryElement.add(dataForm.asXMLElement());
                }
            }
            else {
                // If the DiscoInfoProvider has no information for the requested name and node 
                // then answer a not found error
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.item_not_found);
            }
        }
        else {
            // If we didn't find a DiscoInfoProvider then answer a not found error
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    /**
     * Returns the DiscoInfoProvider responsible for providing information about a given entity or
     * null if none was found.
     *
     * @param name the name of the identity.
     * @return the DiscoInfoProvider responsible for providing information about a given entity or
     *         null if none was found.
     */
    private DiscoInfoProvider getProvider(String name) {
        return (DiscoInfoProvider)entities.get(name);
    }

    /**
     * Sets that a given DiscoInfoProvider will provide information about a given entity. This
     * message must be used when new modules (e.g. MUC) are implemented and need to provide
     * information about them.
     *
     * @param name     the name of the entity.
     * @param provider the DiscoInfoProvider that will provide the entity's information.
     */
    protected void setProvider(String name, DiscoInfoProvider provider) {
        entities.put(name, provider);
    }

    /**
     * Removes the DiscoInfoProvider related to a given entity.
     *
     * @param name the name of the entity.
     */
    protected void removeProvider(String name) {
        entities.remove(name);
    }

    /**
     * Adds the features provided by the new service that implements the ServerFeaturesProvider
     * interface. This information will be used whenever a disco for information is made against
     * the server (i.e. the packet's target is the server).
     * Example of features are: jabber:iq:agents, jabber:iq:time, etc.
     *
     * @param provider the ServerFeaturesProvider that provides new server features.
     */
    private void addServerFeaturesProvider(ServerFeaturesProvider provider) {
        for (Iterator it = provider.getFeatures(); it.hasNext();) {
            serverFeatures.add(it.next());
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        // Track the implementors of ServerFeaturesProvider so that we can collect the features
        // provided by the server
        for (ServerFeaturesProvider provider : server.getServerFeaturesProviders()) {
            addServerFeaturesProvider(provider);
        }
        setProvider(server.getServerInfo().getName(), getServerInfoProvider());
    }

    /**
     * Returns the DiscoInfoProvider responsible for providing information at the server level. This
     * means that this DiscoInfoProvider will provide information whenever a disco request whose
     * recipient JID is the server (e.g. localhost) is made.
     *
     * @return the DiscoInfoProvider responsible for providing information at the server level.
     */
    private DiscoInfoProvider getServerInfoProvider() {
        DiscoInfoProvider discoInfoProvider = new DiscoInfoProvider() {
            ArrayList identities = new ArrayList();
            ArrayList features = new ArrayList();

            public Iterator getIdentities(String name, String node, JID senderJID) {
                if (name == null) {
                    // Answer identity of the server
                    synchronized (identities) {
                        if (identities.isEmpty()) {
                            Element identity = DocumentHelper.createElement("identity");
                            identity.addAttribute("category", "services");
                            identity.addAttribute("name", "Messenger Server");
                            identity.addAttribute("type", "jabber");

                            identities.add(identity);
                        }
                    }
                    return identities.iterator();
                }
                else {
                    // Answer identity of a registered user.
                    // Note: We know that this user exists because #hasInfo returned true
                    return userIdentities.iterator();
                }
            }

            public Iterator getFeatures(String name, String node, JID senderJID) {
                if (name == null) {
                    // Answer features of the server
                    return serverFeatures.iterator();
                }
                else {
                    // Answer features of the user
                    return userFeatures.iterator();
                }
            }

            public boolean hasInfo(String name, String node, JID senderJID) {
                try {
                    // True if it is an info request of the server or of a registered user. We
                    // now support disco of user's bare JIDs
                    return node == null &&
                            (name == null || UserManager.getInstance().getUser(name) != null);
                }
                catch (UserNotFoundException e) {
                    return false;
                }
            }

            public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
                return null;
            }
        };
        return discoInfoProvider;
    }
}