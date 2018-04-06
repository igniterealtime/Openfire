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

package org.jivesoftware.openfire;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.LeafNode;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Collections;

/**
 * Private storage for user accounts (XEP-0049). It is used by some XMPP systems for saving client settings on the server.
 *
 * This implementation uses the Personal Eventing Protocol implementation to store and retrieve data. This ensures that
 * XEP-0049 operates on the same data as XEP-0223.
 *
 * @author Iain Shigeoka
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PrivateStorage extends BasicModule {

    private static final Logger Log = LoggerFactory.getLogger(PrivateStorage.class);

    /**
     * PubSub 7.1.5 specificy Publishing Options that are applicable to private data storage (as described in XEP-0223).
     */
    private static final DataForm PRIVATE_DATA_PUBLISHING_OPTIONS;

    static {
        PRIVATE_DATA_PUBLISHING_OPTIONS = new DataForm( DataForm.Type.submit );
        PRIVATE_DATA_PUBLISHING_OPTIONS.addField( "FORM_TYPE", null, FormField.Type.hidden ).addValue( "http://jabber.org/protocol/pubsub#publish-options" );
        PRIVATE_DATA_PUBLISHING_OPTIONS.addField( "pubsub#persist_items", null, null ).addValue( "true" );
        PRIVATE_DATA_PUBLISHING_OPTIONS.addField( "pubsub#access_model", null, null ).addValue( "whitelist" );
    }

    private boolean enabled = JiveGlobals.getBooleanProperty("xmpp.privateStorageEnabled", true);

    /**
     * Constructs a new PrivateStore instance.
     */
    public PrivateStorage() {
        super("Private user data storage");
    }

    /**
     * Returns true if private storage is enabled.
     *
     * @return true if private storage is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether private storage is enabled.
     *
     * @param enabled true if this private store is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("xmpp.privateStorageEnabled", Boolean.toString(enabled));
    }

    /**
     * Stores private data. If the name and namespace of the element matches another
     * stored private data XML document, then replace it with the new one.
     *
     * @param data the data to store (XML element)
     * @param username the username of the account where private data is being stored
     */
    public void add(String username, Element data) {
        if (!enabled)
        {
            return;
        }

        final JID owner = XMPPServer.getInstance().createJID( username, null );
        final PEPServiceManager serviceMgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
        PEPService pepService = serviceMgr.getPEPService( owner );
        if ( pepService == null )
        {
            pepService = serviceMgr.create( owner );
        }

        Node node = pepService.getNode( data.getNamespaceURI() );
        if ( node == null )
        {
            PubSubEngine.CreateNodeResponse response = PubSubEngine.createNodeHelper( pepService, owner, pepService.getDefaultNodeConfiguration( true ).getConfigurationForm().getElement(), data.getNamespaceURI(), PRIVATE_DATA_PUBLISHING_OPTIONS );
            node = response.newNode;

            if ( node == null )
            {
                Log.error( "Unable to create new PEP node, to be used to store private data. Error condition: {}", response.creationStatus.toXMPP() );
                return;
            }
        }

        if (!(node instanceof LeafNode))
        {
            Log.error( "Unable to store private data into a PEP node. The node that is available is not a leaf node." );
            return;
        }

        data.detach();
        final Element item = DocumentHelper.createElement( "item" );
        item.addAttribute( "id", "current" );
        item.add( data );

        ((LeafNode) node).publishItems( owner, Collections.singletonList( item ) );
    }

    /**
     * Returns the data stored under a key corresponding to the name and namespace
     * of the given element. The Element must be in the form:<p>
     *
     * <code>&lt;name xmlns='namespace'/&gt;</code><p>
     *
     * If no data is currently stored under the given key, an empty element will be
     * returned.
     *
     * @param data an XML document who's element name and namespace is used to
     *      match previously stored private data.
     * @param username the username of the account where private data is being stored.
     * @return the data stored under the given key or the data element.
     */
    public Element get(String username, Element data)
    {
        if (enabled)
        {
            final PEPServiceManager serviceMgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
            final PEPService pepService = serviceMgr.getPEPService( XMPPServer.getInstance().createJID( username, null ) );
            if ( pepService != null )
            {
                final Node node = pepService.getNode( data.getNamespaceURI() );
                if ( node != null )
                {
                    final PublishedItem item = node.getPublishedItem( "current" );
                    if ( item != null )
                    {
                        data.clearContent();
                        data = item.getPayload();
                    }
                }
            }
        }
        return data;
    }
}
