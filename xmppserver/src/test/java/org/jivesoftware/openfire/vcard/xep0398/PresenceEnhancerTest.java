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
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.pep.IQPEPHandler;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PresenceEnhancer}, verifying XEP-0398 Section 4 behaviour:
 * augmenting presence stanzas with the user's current avatar hash.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0398.html#impl-server-presence">XEP-0398 Section 4</a>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceEnhancerTest {

    private static final String AVATAR_HASH = "sha1-hash-abc123def456";
    private static final String AVATAR_NODE = "urn:xmpp:avatar:metadata";

    @Mock
    private LocalClientSession localClientSession;

    @Mock
    private IQPEPHandler pepHandler;

    @Mock
    private PEPServiceManager pepServiceManager;

    @Mock
    private PEPService pepService;

    @Mock
    private Node metadataNode;

    @Mock
    private PublishedItem publishedItem;

    private PresenceEnhancer enhancer;

    @BeforeAll
    static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    void setUp() {
        final XMPPServer mockServer = Fixtures.mockXMPPServer();
        doReturn(pepHandler).when(mockServer).getIQPEPHandler();
        XMPPServer.setInstance(mockServer);

        when(localClientSession.getAddress()).thenReturn(new org.xmpp.packet.JID("user", Fixtures.XMPP_DOMAIN, "res"));

        when(pepHandler.getServiceManager()).thenReturn(pepServiceManager);
        when(pepServiceManager.getPEPService(any(org.xmpp.packet.JID.class), eq(false))).thenReturn(pepService);
        when(pepService.getNode(AVATAR_NODE)).thenReturn(metadataNode);
        when(metadataNode.getLastPublishedItem()).thenReturn(publishedItem);
        when(publishedItem.getID()).thenReturn(AVATAR_HASH);

        enhancer = new PresenceEnhancer();
    }

    @AfterEach
    void tearDown() {
        Fixtures.clearExistingProperties();
    }

    // -------------------------------------------------------------------------
    // Happy-path tests (XEP-0398 Section 4: server SHOULD augment presence)
    // -------------------------------------------------------------------------

    /**
     * XEP-0398 Section 4: When a client sends an available presence without
     * a {@code <x xmlns='vcard-temp:x:update'>} element, the server adds that
     * element together with the current avatar hash in {@code <photo>}.
     */
    @Test
    void testAugments_availablePresence_withoutXElement() throws PacketRejectedException {
        final Presence presence = new Presence();

        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        assertNotNull(x, "Expected <x xmlns='vcard-temp:x:update'> to be added");
        assertEquals(AVATAR_HASH, x.elementTextTrim("photo"),
            "Expected the avatar hash to be placed in the <photo> element");
    }

    /**
     * XEP-0398 Section 4: When a client sends an available presence that
     * already includes {@code <x xmlns='vcard-temp:x:update'>} but no
     * {@code <photo>} element, the server fills in the current avatar hash.
     */
    @Test
    void testAugments_availablePresence_withXButNoPhotoElement() throws PacketRejectedException {
        final Presence presence = new Presence();
        presence.addChildElement("x", "vcard-temp:x:update");

        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        assertNotNull(x, "Expected <x xmlns='vcard-temp:x:update'> to be present");
        assertEquals(AVATAR_HASH, x.elementTextTrim("photo"),
            "Expected the avatar hash to be placed in the <photo> element");
    }

    /**
     * XEP-0398 Section 4 (last sentence): When a client sends presence with a non-empty
     * {@code <photo>} hash that differs from the server's hash, the server MAY overwrite it.
     * Openfire exercises this option and replaces the client-supplied hash with its own.
     */
    @Test
    void testOverwrites_presenceWithExistingPhotoHash() throws PacketRejectedException {
        final String clientSuppliedHash = "client-supplied-hash";
        final Presence presence = new Presence();
        final Element x = presence.addChildElement("x", "vcard-temp:x:update");
        x.addElement("photo").setText(clientSuppliedHash);

        enhancer.interceptPacket(presence, localClientSession, true, false);

        assertEquals(AVATAR_HASH, x.elementTextTrim("photo"),
            "Server MAY overwrite a photo hash already present in the stanza (XEP-0398 Section 4)");
    }

    // -------------------------------------------------------------------------
    // Section 4: client signals "no photo" via an empty <photo/> element
    // -------------------------------------------------------------------------

    /**
     * XEP-0398 Section 4: A client MAY signal that it has no avatar by
     * sending an empty {@code <photo/>} element. The server MUST NOT modify
     * this stanza.
     */
    @Test
    void testDoesNotAugment_presenceWithEmptyPhotoElement() throws PacketRejectedException {
        final Presence presence = new Presence();
        final Element x = presence.addChildElement("x", "vcard-temp:x:update");
        x.addElement("photo"); // empty, no text

        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element photo = x.element("photo");
        assertNotNull(photo, "Empty <photo/> element should still be present");
        assertTrue(photo.getTextTrim().isEmpty(),
            "Server must not add a hash when the client explicitly sent an empty <photo/>");
    }

    // -------------------------------------------------------------------------
    // Guard conditions: packets the enhancer must ignore
    // -------------------------------------------------------------------------

    /**
     * The feature can be disabled via system property; when disabled the
     * presence stanza must not be touched.
     */
    @Test
    void testDoesNotAugment_whenFeatureDisabled() throws PacketRejectedException {
        UserAvatarToVCardConvertor.DISABLED.setValue(true);

        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, false);

        assertNull(presence.getChildElement("x", "vcard-temp:x:update"),
            "No <x> element should be added when the feature is disabled");
    }

    /**
     * The interceptor only acts on incoming packets; outgoing packets must
     * not be modified.
     */
    @Test
    void testDoesNotAugment_outgoingPresence() throws PacketRejectedException {
        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, false, false);

        assertNull(presence.getChildElement("x", "vcard-temp:x:update"),
            "Outgoing presence must not be augmented");
    }

    /**
     * Packets that have already been processed by the interceptor chain must
     * not be touched again.
     */
    @Test
    void testDoesNotAugment_alreadyProcessedPacket() throws PacketRejectedException {
        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, true);

        assertNull(presence.getChildElement("x", "vcard-temp:x:update"),
            "Already-processed packets must not be augmented");
    }

    /**
     * Only sessions from local clients are in scope; remote sessions must be
     * ignored.
     */
    @Test
    void testDoesNotAugment_nonLocalClientSession() throws PacketRejectedException {
        final Session remoteSession = mock(Session.class, withSettings().lenient());
        final Presence presence = new Presence();

        enhancer.interceptPacket(presence, remoteSession, true, false);

        assertNull(presence.getChildElement("x", "vcard-temp:x:update"),
            "Non-local-client sessions must not be augmented");
    }

    /**
     * The interceptor must silently ignore packets that are not Presence stanzas.
     */
    @Test
    void testDoesNotAugment_nonPresencePacket() throws PacketRejectedException {
        final Message message = new Message();
        // Should not throw and should not modify anything
        enhancer.interceptPacket(message, localClientSession, true, false);
        // No assertion needed beyond "no exception thrown"; Message has no x element to check
    }

    /**
     * The interceptor must silently ignore IQ packets.
     */
    @Test
    void testDoesNotAugment_iqPacket() throws PacketRejectedException {
        final IQ iq = new IQ();
        enhancer.interceptPacket(iq, localClientSession, true, false);
    }

    /**
     * Non-available presence types (e.g. {@code unavailable}, {@code subscribe})
     * must not be augmented.
     */
    @Test
    void testDoesNotAugment_unavailablePresence() throws PacketRejectedException {
        final Presence presence = new Presence(Presence.Type.unavailable);

        enhancer.interceptPacket(presence, localClientSession, true, false);

        assertNull(presence.getChildElement("x", "vcard-temp:x:update"),
            "Unavailable presence must not be augmented");
    }

    /**
     * A {@code subscribe} presence type must not be augmented.
     */
    @Test
    void testDoesNotAugment_subscribePresence() throws PacketRejectedException {
        final Presence presence = new Presence(Presence.Type.subscribe);

        enhancer.interceptPacket(presence, localClientSession, true, false);

        assertNull(presence.getChildElement("x", "vcard-temp:x:update"),
            "Subscribe presence must not be augmented");
    }

    // -------------------------------------------------------------------------
    // PEP / avatar-data guard conditions
    // -------------------------------------------------------------------------

    /**
     * If the user has no PEP service, the server cannot look up an avatar
     * hash and must leave the presence unmodified.
     */
    @Test
    void testDoesNotAugment_whenNoPepService() throws PacketRejectedException {
        when(pepServiceManager.getPEPService(any(org.xmpp.packet.JID.class), eq(false))).thenReturn(null);

        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        // x element will have been added before the PEP lookup, but photo must remain empty
        if (x != null) {
            final Element photo = x.element("photo");
            assertTrue(photo == null || photo.getTextTrim().isEmpty(),
                "No hash should be written when there is no PEP service");
        }
    }

    /**
     * If the PEP service exists but has no avatar metadata node, the server
     * must not inject a hash.
     */
    @Test
    void testDoesNotAugment_whenNoAvatarMetadataNode() throws PacketRejectedException {
        when(pepService.getNode(AVATAR_NODE)).thenReturn(null);

        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        if (x != null) {
            final Element photo = x.element("photo");
            assertTrue(photo == null || photo.getTextTrim().isEmpty(),
                "No hash should be written when there is no avatar metadata node");
        }
    }

    /**
     * If the avatar metadata node exists but has never had an item published,
     * the server must not inject a hash.
     */
    @Test
    void testDoesNotAugment_whenNoLastPublishedItem() throws PacketRejectedException {
        when(metadataNode.getLastPublishedItem()).thenReturn(null);

        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        if (x != null) {
            final Element photo = x.element("photo");
            assertTrue(photo == null || photo.getTextTrim().isEmpty(),
                "No hash should be written when there is no published avatar metadata item");
        }
    }

    /**
     * If the last published item exists but its ID is null, the server must
     * not inject a hash.
     */
    @Test
    void testDoesNotAugment_whenPublishedItemIdIsNull() throws PacketRejectedException {
        when(publishedItem.getID()).thenReturn(null);

        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        if (x != null) {
            final Element photo = x.element("photo");
            assertTrue(photo == null || photo.getTextTrim().isEmpty(),
                "No hash should be written when the published item ID is null");
        }
    }

    /**
     * If the last published item has an empty string ID, the server must
     * not inject a hash.
     */
    @Test
    void testDoesNotAugment_whenPublishedItemIdIsEmpty() throws PacketRejectedException {
        when(publishedItem.getID()).thenReturn("");

        final Presence presence = new Presence();
        enhancer.interceptPacket(presence, localClientSession, true, false);

        final Element x = presence.getChildElement("x", "vcard-temp:x:update");
        if (x != null) {
            final Element photo = x.element("photo");
            assertTrue(photo == null || photo.getTextTrim().isEmpty(),
                "No hash should be written when the published item ID is empty");
        }
    }
}
