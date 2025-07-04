/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.vcard.xep0398;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.pep.IQPEPHandler;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Packet Interceptor that augments presence stanzas sent by uses, as defined in section 4 of XEP-0398.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0398.html">XEP-0398: User Avatar to vCard-Based Avatars Conversion</a>
 */
public class PresenceEnhancer implements PacketInterceptor
{
    private static final Logger Log = LoggerFactory.getLogger(PresenceEnhancer.class);

    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException
    {
        if (UserAvatarToVCardConvertor.DISABLED.getValue()) {
            return;
        }

        if (!incoming || processed) {
            return;
        }

        if (!(session instanceof LocalClientSession)) {
            return;
        }

        if (!(packet instanceof Presence presence)) {
            return;
        }

        if (presence.getType() != null) {
            return;
        }

        Element x = presence.getChildElement("x", "vcard-temp:x:update");
        if (x == null) {
            x = presence.addChildElement("x", "vcard-temp:x:update");
        }
        Element photo = x.element("photo");
        if (photo != null && photo.getTextTrim().isEmpty()) {
            Log.trace("Not enhancing presence available stanza from '{}'. The original stanza contains an empty 'photo' element.", session.getAddress());
            return;
        }
        if (photo == null) {
            photo = x.addElement("photo");
        }

        final IQPEPHandler pepHandler = XMPPServer.getInstance().getIQPEPHandler();
        final PEPServiceManager serviceManager = pepHandler.getServiceManager();
        final PEPService pepService = serviceManager.getPEPService(session.getAddress(), false);
        if (pepService == null) {
            Log.trace("Not enhancing presence available stanza from '{}'. No PEP service exists for user.", session.getAddress());
            return;
        }

        final Node metadataNode = pepService.getNode("urn:xmpp:avatar:metadata");
        if (metadataNode == null) {
            Log.trace("Not enhancing presence available stanza from '{}'. PEP service does not contain an avatar 'metadata' node.", session.getAddress());
            return;
        }

        final PublishedItem lastPublishedItem = metadataNode.getLastPublishedItem();
        if (lastPublishedItem == null) {
            Log.trace("Not enhancing presence available stanza from '{}'. PEP service does not contain an avatar 'metadata' item", session.getAddress());
            return;
        }

        final String hash = lastPublishedItem.getID();
        if (hash == null || hash.isEmpty()) {
            return;
        }

        Log.trace("Enhancing presence available stanza from '{}' with value '{}'", session.getAddress(), hash);
        photo.addText(hash);
    }
}
