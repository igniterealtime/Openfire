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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Module;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.pep.IQPEPHandler;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.vcard.VCardEventDispatcher;
import org.jivesoftware.openfire.vcard.VCardListener;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods that help convert various User Avatar representations into each-other.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0398.html">XEP-0398: User Avatar to vCard-Based Avatars Conversion</a>
 */
public class UserAvatarToVCardConvertor implements VCardListener, PubSubListener, Module
{
    public static final Logger Log = LoggerFactory.getLogger(UserAvatarToVCardConvertor.class);

    private final PresenceEnhancer presenceEnhancer = new PresenceEnhancer();

    public static final SystemProperty<Boolean> DISABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("avatar.xep0398.disabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    @Override
    public String getName()
    {
        return "UserAvatarToVCardConvertor";
    }

    @Override
    public void initialize(XMPPServer server)
    {
        VCardEventDispatcher.addListener(this);
        PubSubEventDispatcher.addListener(this);
        InterceptorManager.getInstance().addInterceptor(presenceEnhancer);
    }

    @Override
    public void start()
    {
    }

    @Override
    public void stop()
    {
    }

    @Override
    public void destroy()
    {
        InterceptorManager.getInstance().removeInterceptor(presenceEnhancer);
        PubSubEventDispatcher.removeListener(this);
        VCardEventDispatcher.removeListener(this);
    }

    @Override
    public void itemsPublished(@Nonnull final LeafNode.UniqueIdentifier nodeId, @Nonnull final Collection<PublishedItem> items)
    {
        if (DISABLED.getValue()) {
            return;
        }

        if (!nodeId.getNodeId().equals("urn:xmpp:avatar:metadata")) {
            return;
        }

        final IQPEPHandler pepHandler = XMPPServer.getInstance().getIQPEPHandler();
        final PEPServiceManager serviceManager = pepHandler.getServiceManager();
        final PEPService pepService = serviceManager.getPEPService(nodeId.getServiceIdentifier(), false);
        if (pepService == null) {
            return;
        }

        final String owner = pepService.getAddress().getNode();

        // Check if there's a suitable 'info' within an 'item'.
        item: for (final PublishedItem item : items)
        {
            final String id = item.getID();
            final Element metadata = item.getPayload();
            if (metadata != null && metadata.getName().equals("metadata"))
            {
                for (final Element info : metadata.elements("info"))
                {
                    if (info.attributeValue("url") != null) {
                        continue;
                    }
                    final String type = info.attributeValue("type");
                    // Even to the XEP specifically states image/png MUST be used, in practice, it isn't.
                    //if (type == null || !type.equalsIgnoreCase("image/png")) {
                    //    continue;
                    //}
                    final Node dataNode = pepService.getNode("urn:xmpp:avatar:data");
                    if (dataNode == null) {
                        return; // No need to evaluate any other info if there's no published avatar.
                    }
                    final PublishedItem publishedAvatarItem = dataNode.getPublishedItem(id);
                    if (publishedAvatarItem == null) {
                        continue item; // No need to evaluate any of these info's if there's no avatar with an ID that matches this metadata.
                    }
                    final Element avatarPayload = publishedAvatarItem.getPayload();
                    if (avatarPayload == null || !avatarPayload.getName().equals("data") || !Objects.equals(avatarPayload.getNamespaceURI(), "urn:xmpp:avatar:data")) {
                        continue;
                    }
                    final String avatarBase64 = avatarPayload.getTextTrim();
                    if (avatarBase64 == null || avatarBase64.isEmpty()) {
                        continue;
                    }

                    Log.debug("Avatar published via PEP for user '{}'. Cross-posting it to vCard.", owner);
                    Element vCard = VCardManager.getInstance().getVCard(owner);
                    if (vCard == null) {
                        vCard = DocumentHelper.createElement(QName.get("vCard", "vcard-temp"));
                    }
                    if (vCard.element("PHOTO") == null) {
                        vCard.addElement("PHOTO");
                    }
                    Element photoEl = vCard.element("PHOTO");
                    if (photoEl == null) {
                        photoEl = vCard.addElement("PHOTO");
                    }
                    Element binvalEl = photoEl.element("BINVAL");
                    if (binvalEl == null) {
                        binvalEl = photoEl.addElement("BINVAL");
                    }
                    Element typeEl = photoEl.element("TYPE");
                    if (typeEl == null) {
                        typeEl = photoEl.addElement("TYPE");
                    }

                    // TODO Services SHOULD verify that the SHA-1 hash of the image matches the id.

                    try {
                        // Only update if there's a difference.
                        if (avatarBase64.equals(binvalEl.getTextTrim())) {
                            Log.debug("Not converting PEP avatar to vCard photo, as vCard already has this photo for {}", owner);
                        } else {
                            Log.debug("Publishing photo to VCard for user '{}'", owner);
                            binvalEl.setText(avatarBase64);
                            typeEl.setText(type);
                            VCardManager.getInstance().setVCard(owner, vCard);
                        }
                        return;
                    } catch (Exception e) {
                        Log.warn("Unable to store avatar in vCard of user '{}'", owner, e);
                    }
                }
            }
        }
    }

    @Override
    public void itemsDeleted(@Nonnull final LeafNode.UniqueIdentifier nodeId, @Nonnull final Collection<PublishedItem> items)
    {
    }

    @Override
    public void vCardCreated(String username, Element vCard)
    {
        attemptPepUpdate(username, vCard);
    }

    @Override
    public void vCardUpdated(String username, Element vCard)
    {
        attemptPepUpdate(username, vCard);
    }

    @Override
    public void vCardDeleted(String username, Element vCard)
    {
    }

    public void attemptPepUpdate(final String username, final Element vCard) throws RuntimeException
    {
        if (DISABLED.getValue()) {
            return;
        }

        if (username == null || vCard == null) {
            return;
        }
        if (vCard.element("PHOTO") == null) {
            return;
        }
        final Element photoEl = vCard.element("PHOTO");
        if (photoEl == null) {
            return;
        }
        final Element binvalEl = photoEl.element("BINVAL");
        if (binvalEl == null) {
            return;
        }
        final String binVal = binvalEl.getTextTrim();
        if (binVal == null || binVal.isEmpty()) {
            return;
        }
        final Element typeEl = photoEl.element("TYPE");
        if (typeEl == null) {
            return;
        }
        final String type = typeEl.getTextTrim();

        final JID address;
        if (username.contains("@")) { // MUC rooms can set avatars in vCards. For these events the 'username' is a JID (the room address).
            address = new JID(username);
        } else {
            address = XMPPServer.getInstance().createJID(username, null);
        }
        final IQPEPHandler pepHandler = XMPPServer.getInstance().getIQPEPHandler();
        final PEPServiceManager serviceManager = pepHandler.getServiceManager();
        final PEPService pepService = serviceManager.getPEPService(address, false);
        if (pepService == null) {
            return;
        }

        // XEP-0084 requires a _PNG_ image, but in practise not all clients do this. We'll use _any_ image type.
        final byte[] decodedBinVal = decode(binVal);
        final String hash = StringUtils.hash(decodedBinVal, "SHA-1");

        // Check if this is an update.
        final Node metadataNode = pepService.getNode("urn:xmpp:avatar:metadata");
        if (metadataNode != null) {
            if (metadataNode.getPublishedItem(hash) != null) {
                Log.debug("Not converting vCard photo to PEP, as PEP already has this photo for {}", address);
                return;
            }
        }

        final IQ publishDataRequest = generatePublishDataRequest(pepService.getAddress(), binVal, hash);
        final IQ publishMetaDataRequest = generatePublishMetadataRequest(pepService.getAddress(), decodedBinVal, hash, type);

        try {
            Log.debug("Publishing photo to PEP for user '{}'", address);
            // It is important to have the data published _before_ the metadata is published.
            serviceManager.process(pepService, publishDataRequest).get(500, TimeUnit.MILLISECONDS); // TODO replace this with a chained invocation, like the ones provided by CompletableFuture.
            serviceManager.process(pepService, publishMetaDataRequest);
        } catch (Throwable e) {
            Log.warn("An unexpected exception occurred while trying to publish an avatar for '{}' via PEP.", address, e);
        }
    }

    /**
     * Decodes a base64-encoded binary value. This method can decode both Mime-encoded as well as non-Mime-encoded values.
     *
     * @param binVal The value to decode
     * @return the decoded value.
     */
    public static byte[] decode(final String binVal)
    {
        final Base64.Decoder decoder;
        if (binVal.length() > 76 && Character.isWhitespace(binVal.charAt(76))) {
            decoder = Base64.getMimeDecoder();
        } else {
            decoder = Base64.getDecoder();
        }
        return decoder.decode(binVal);
    }

    /**
     * Calculated the SHA-1 hash of Base64-encoded data
     *
     * @param base64EncodedData The data for which to return a hash
     * @return the hash
     */
    public static String hash(@Nonnull final String base64EncodedData)
    {
        final byte[] decodedBinVal = decode(base64EncodedData);
        return StringUtils.hash(decodedBinVal, "SHA-1");
    }

    /**
     * Creates an IQ stanza that is appropriate to publish an item to the avatar data node of the PEP service of
     * a particular user.
     *
     * @param owner The owner of the PEP service on which data is to be published
     * @param binVal The hex-encoded binary representation of the avatar that is published
     * @param hash (optional) The SHA-1 hash of the binary data (will be calculated from decodedBinVal if not provided)
     * @return An IQ stanza
     */
    public static IQ generatePublishDataRequest(@Nonnull final JID owner, @Nonnull final String binVal, @Nullable String hash)
    {
        if (hash == null || hash.isEmpty()) {
            hash = hash(binVal);
        }
        final IQ request = new IQ(IQ.Type.set);
        request.setFrom(owner);
        final Element pubsubEl = request.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publishEl = pubsubEl.addElement("publish");
        publishEl.addAttribute("node", "urn:xmpp:avatar:data");
        final Element itemEl = publishEl.addElement("item");
        itemEl.addAttribute("id", hash);
        final Element dataEl = itemEl.addElement("data", "urn:xmpp:avatar:data");
        dataEl.setText(binVal);

        return request;
    }

    /**
     * Creates an IQ stanza that is appropriate to publish an item to the avatar metadata node of the PEP service of
     * a particular user.
     *
     * @param owner The owner of the PEP service on which data is to be published
     * @param decodedBinVal The binary representation of the avatar for which metadata is published
     * @param hash (optional) The SHA-1 hash of the binary data (will be calculated from decodedBinVal if not provided)
     * @param type The media type of the image
     * @return An IQ stanza
     */
    public static IQ generatePublishMetadataRequest(@Nonnull final JID owner, @Nonnull final byte[] decodedBinVal, @Nullable String hash, @Nonnull final String type)
    {
        if (hash == null || hash.isEmpty()) {
            hash = StringUtils.hash(decodedBinVal, "SHA-1");
        }
        final IQ request = new IQ(IQ.Type.set);
        request.setFrom(owner);
        final Element pubsubEl = request.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publishEl = pubsubEl.addElement("publish");
        publishEl.addAttribute("node", "urn:xmpp:avatar:metadata");
        final Element itemEl = publishEl.addElement("item");
        itemEl.addAttribute("id", hash);
        final Element metadataEl = itemEl.addElement("metadata","urn:xmpp:avatar:metadata");
        final Element infoEl = metadataEl.addElement("info");
        infoEl.addAttribute("bytes", String.valueOf(decodedBinVal.length));
        infoEl.addAttribute("id", hash);
        infoEl.addAttribute("type", type);

        final int[] dimensions = getDimensions(decodedBinVal);
        if (dimensions != null) {
            infoEl.addAttribute("width", String.valueOf(dimensions[0]));
            infoEl.addAttribute("height", String.valueOf(dimensions[1]));
        }
        return request;
    }

    /**
     * Finds the width and height (in pixels) of an image.
     *
     * This method returns an array of size 2. The first element is the width, the second element the height of the
     * image. When the provided data could not be processed, this method returns null.
     *
     * @param bytes the raw bytes of an image.
     * @return an array containing the width and height of the image, or null if the dimensions could not be determined.
     */
    public static int[] getDimensions(byte[] bytes)
    {
        BufferedImage avatar;
        try ( final ByteArrayInputStream stream = new ByteArrayInputStream( bytes ) )
        {
            avatar = ImageIO.read( stream );
            return new int[] { avatar.getWidth(), avatar.getHeight()};
        }
        catch (IOException | RuntimeException ex )
        {
            Log.warn( "Failed to determine dimensions of image. An unexpected exception occurred while reading the image.", ex );
            return null;
        }
    }
}
