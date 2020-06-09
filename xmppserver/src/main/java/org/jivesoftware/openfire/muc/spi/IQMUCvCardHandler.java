/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.util.Iterator;
import java.util.Locale;

/**
 * Implements the TYPE_IQ vcard-temp protocol, to be used for MUC rooms.
 * <p>
 * This implementation borrows heavily from IQvCardHandler.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see org.jivesoftware.openfire.handler.IQvCardHandler
 */
public class IQMUCvCardHandler extends IQHandler
{

    private static final Logger Log = LoggerFactory.getLogger(IQMUCvCardHandler.class);

    public static SystemProperty<Boolean> PROPERTY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.muc.vcard.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    public static final String REQUEST_ELEMENT_NAME = "vCard";
    public static final String RESPONSE_ELEMENT_NAME = "vCard";
    public static final String NAMESPACE = "vcard-temp";

    private IQHandlerInfo info;
    private MultiUserChatService mucService;

    public IQMUCvCardHandler( MultiUserChatService mucService )
    {
        super("XMPP vCard Handler for MUC");
        this.mucService = mucService;
        info = new IQHandlerInfo(REQUEST_ELEMENT_NAME, RESPONSE_ELEMENT_NAME);
    }

    @Override
    public IQ handleIQ( IQ packet ) throws PacketException
    {
        IQ result = IQ.createResultIQ(packet);
        IQ.Type type = packet.getType();
        if ( type.equals(IQ.Type.set) )
        {
            Log.debug("vCard update request received from: '{}', for: '{}'", packet.getFrom(), packet.getTo());
            try
            {
                String roomName = packet.getTo().getNode();
                // If no TO was specified then return an error.
                if ( roomName == null )
                {
                    Log.debug("vCard update request from: '{}', for: '{}' is invalid: it does not refer to a specific room.", packet.getFrom(), packet.getTo());
                    result.setChildElement(packet.getChildElement().createCopy());
                    result.setError(PacketError.Condition.not_acceptable);
                    result.getError().setText("Request 'to' attribute has no node-part. The request should be addressed to a room of a MUC service.");
                }
                else
                {
                    final MUCRoom room = mucService.getChatRoom(roomName);
                    Log.debug("vCard update request from: '{}', for: '{}' relates to room: {}", packet.getFrom(), packet.getTo(), room);
                    if ( room == null || !room.getOwners().contains(packet.getFrom().asBareJID()) )
                    {
                        Log.debug("vCard update request from: '{}', for: '{}' is invalid: room does not exist, or sender is not allowed to discover the room.", packet.getFrom(), packet.getTo());
                        result.setChildElement(packet.getChildElement().createCopy());
                        result.setError(PacketError.Condition.forbidden);
                        result.getError().setText("You are not an owner of this room.");
                    }
                    else
                    {
                        Element vcard = packet.getChildElement();
                        if ( vcard != null )
                        {
                            try
                            {
                                VCardManager.getInstance().setVCard(room.getJID().toString(), vcard);

                                // This is what EJabberd does. Mimic it, for compatibility.
                                sendConfigChangeNotification(room);

                                // Mimic a client that broadcasts a vCard update. Converse seems to need this.
                                final String hash = calculatePhotoHash(vcard);
                                sendVCardUpdateNotification(room, hash);
                                Log.debug("vCard update request from: '{}', for: '{}' processed successfully.", packet.getFrom(), packet.getTo());
                            }
                            catch ( UnsupportedOperationException e )
                            {
                                Log.debug("Entity '{}' tried to set VCard, but the configured VCard provider is read-only. An IQ error will be returned to sender.", packet.getFrom());
                                // VCards can include binary data. Let's not echo that back in the error.
                                // result.setChildElement( packet.getChildElement().createCopy() );

                                result.setError(PacketError.Condition.not_allowed);

                                Locale locale = JiveGlobals.getLocale(); // default to server locale.
                                final Session session = SessionManager.getInstance().getSession(result.getTo());
                                if ( session != null && session.getLanguage() != null )
                                {
                                    locale = session.getLanguage(); // use client locale if one is available.
                                }
                                result.getError().setText(LocaleUtils.getLocalizedString("vcard.read_only", locale), locale.getLanguage());
                            }
                        }
                    }
                }
            }
            catch ( UserNotFoundException e )
            {
                // VCards can include binary data. Let's not echo that back in the error.
                // result.setChildElement( packet.getChildElement().createCopy() );

                result.setError(PacketError.Condition.item_not_found);
            }
            catch ( Exception e )
            {
                Log.error(e.getMessage(), e);
                result.setError(PacketError.Condition.internal_server_error);
            }
        }
        else if ( type.equals(IQ.Type.get) )
        {
            Log.debug("vCard retrieve request received from: '{}', for: '{}'", packet.getFrom(), packet.getTo());
            String roomName = packet.getTo().getNode();
            // If no TO was specified then return an error.
            if ( roomName == null )
            {
                Log.debug("vCard retrieve request from: '{}', for: '{}' is invalid: it does not refer to a specific room.", packet.getFrom(), packet.getTo());
                result.setChildElement(packet.getChildElement().createCopy());
                result.setError(PacketError.Condition.not_acceptable);
                result.getError().setText("Request 'to' attribute has no node-part. The request should be addressed to a room of a MUC service.");
            }
            else
            {
                // By default return an empty vCard
                result.setChildElement(RESPONSE_ELEMENT_NAME, NAMESPACE);
                // Only try to get the vCard values of rooms that can be discovered
                // Answer the room occupants as items if that info is publicly available
                final MUCRoom room = mucService.getChatRoom(roomName);
                Log.debug("vCard retrieve request from: '{}', for: '{}' relates to room: {}", packet.getFrom(), packet.getTo(), room);

                if ( room != null && mucService.canDiscoverRoom(room, packet.getFrom()) )
                {
                    VCardManager vManager = VCardManager.getInstance();
                    Element userVCard = vManager.getVCard(room.getJID().toString());
                    if ( userVCard != null )
                    {
                        // Check if the requester wants to ignore some vCard's fields
                        Element filter = packet.getChildElement().element(QName.get("filter", "vcard-temp-filter"));
                        if ( filter != null )
                        {
                            // Create a copy so we don't modify the original vCard
                            userVCard = userVCard.createCopy();
                            // Ignore fields requested by the user
                            for ( Iterator<Element> toFilter = filter.elementIterator(); toFilter.hasNext(); )
                            {
                                Element field = toFilter.next();
                                Element fieldToRemove = userVCard.element(field.getName());
                                if ( fieldToRemove != null )
                                {
                                    fieldToRemove.detach();
                                }
                            }
                        }
                        result.setChildElement(userVCard);
                        Log.debug("vCard retrieve request from: '{}', for: '{}' processed successfully.", packet.getFrom(), packet.getTo());
                    }
                }
                else
                {
                    Log.debug("vCard retrieve request from: '{}', for: '{}' is invalid: room does not exist, or sender is not allowed to discover the room.", packet.getFrom(), packet.getTo());
                    result = IQ.createResultIQ(packet);
                    result.setChildElement(packet.getChildElement().createCopy());
                    result.setError(PacketError.Condition.item_not_found);
                    result.getError().setText("Request 'to' references a room that cannot be found (or is not discoverable by you).");
                }
            }
        }
        else
        {
            // Ignore non-request IQs
            return null;
        }
        return result;
    }

    private void sendVCardUpdateNotification( final MUCRoom room, String hash )
    {
        Log.debug("Sending vcard-temp update notification to all occupants of room {}, using hash {}", room.getName(), hash);
        final Presence notification = new Presence();
        notification.setFrom(room.getJID());
        final Element x = notification.addChildElement("x", "vcard-temp:x:update");
        final Element photo = x.addElement("photo");
        photo.setText(hash);

        for ( final MUCRole occupant : room.getOccupants() )
        {
            occupant.send(notification);
        }
    }

    private void sendConfigChangeNotification( final MUCRoom room )
    {
        Log.debug("Sending configuration change notification to all occupants of room {}", room.getName());
        final Message notification = new Message();
        notification.setType(Message.Type.groupchat);
        notification.setFrom(room.getJID());
        final Element x = notification.addChildElement("x", "http://jabber.org/protocol/muc#user");
        final Element status = x.addElement("status");
        status.addAttribute("code", "104");

        for ( final MUCRole occupant : room.getOccupants() )
        {
            occupant.send(notification);
        }
    }

    public static String calculatePhotoHash( Element vcard )
    {
        if ( vcard.element("PHOTO") == null )
        {
            return "";
        }
        final Element element = vcard.element("PHOTO").element("BINVAL");
        if ( element == null )
        {
            return "";
        }

        final byte[] photo = Base64.decode(element.getTextTrim());
        return StringUtils.hash(photo, "SHA-1");
    }

    @Override
    public IQHandlerInfo getInfo()
    {
        return info;
    }
}
