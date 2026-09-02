/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.fast.FastSessionState;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.sasl.MechanismName;
import org.jivesoftware.openfire.sasl.SaslMechanismCatalog;
import org.jivesoftware.openfire.sasl.SaslMechanismEligibility;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link SaslStreamFeatures}, which renders the SASL-related stream features that a session is offered.
 *
 * Three things are covered: the shape of the mechanism feature elements themselves, including when an empty one is
 * suppressed and when the XEP-0484 inline feature accompanies them; the XEP-0440 channel-binding capability element
 * that goes alongside; and the correspondence between what is rendered and what is recorded on the session, which the
 * SCRAM downgrade-protection hash depends on.
 *
 * Which mechanisms are eligible to be rendered in the first place is decided elsewhere, and covered in
 * {@code SaslMechanismEligibilityTest}.
 */
public class SaslStreamFeaturesTest
{
    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setup()
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        FastTokenManager.ENABLE_FAST.setValue(FastTokenManager.ENABLE_FAST.getDefaultValue());
        SaslMechanismCatalog.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL"));
    }

    // -------------------------------------------------------------------------
    // Mechanism feature elements: client sessions
    // -------------------------------------------------------------------------

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession returns a non-null (but empty) element
     * when there are no available mechanisms, SASL1 is used, and sasl.client.suppressEmpty is false.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl1_suppressEmptyFalse_noMechanisms_returnsEmptyElement()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, false);

        // Verify result.
        assertTrue(result != null && result.elements().isEmpty(),
            "Expected a non-null empty <mechanisms> element when suppressEmpty is false and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession returns null
     * when there are no available mechanisms, SASL1 is used, and sasl.client.suppressEmpty is true.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl1_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, false);

        // Verify result.
        assertNull(result, "Expected null when suppressEmpty is true and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.client.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl2_suppressEmptyFalse_noMechanisms_returnsNull()
    {
        FastTokenManager.ENABLE_FAST.setValue(false);
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is false.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a ClientSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.client.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl2_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        FastTokenManager.ENABLE_FAST.setValue(false);
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption, PLAIN is removed).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is true.");
    }

    /**
     * A SASL1 mechanisms element that would carry nothing must be suppressed when configured to be, even when FAST
     * mechanisms are eligible for the session.
     *
     * FAST mechanisms are rendered in the XEP-0484 inline feature, which only the SASL2 element carries, so they can
     * never populate a SASL1 element. Counting them when deciding whether that element is empty leaves an empty
     * <mechanisms/> on the wire despite sasl.client.suppressEmpty being set.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl1_suppressEmptyTrue_onlyFastMechanisms_returnsNull()
    {
        try (final MockedStatic<ChannelBindingProviderManager> managers = mockStatic(ChannelBindingProviderManager.class))
        {
            // Setup test fixture: no standard mechanism is eligible (EXTERNAL requires encryption), but the FAST
            // mechanisms that need no channel binding are.
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);
            managers.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSupportedChannelBindingTypes()).thenReturn(Set.of());

            FastTokenManager.ENABLE_FAST.setValue(true);
            SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
            JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(false);
            when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of());

            final StreamID streamID = new BasicStreamIDFactory().createStreamID();
            final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

            final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
            assertFalse(advertisableSASLMechanisms.isEmpty(),
                "Test setup issue: expected the FAST mechanisms that need no channel binding to be eligible.");
            assertTrue(advertisableSASLMechanisms.stream().allMatch(MechanismName::isFast),
                "Test setup issue: expected no standard mechanism to be eligible, but found " + advertisableSASLMechanisms);

            // Execute system under test.
            final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, false);

            // Verify result.
            assertNull(result, "A SASL1 element that can carry no mechanism must be suppressed, as FAST mechanisms are " +
                "rendered in the SASL2 inline feature rather than here.");
        }
    }

    /**
     * No channel-binding types are advertised on the strength of FAST mechanisms that are not themselves being
     * offered.
     *
     * The XEP-0484 inline feature is carried only by the SASL2 element, so a session that is not offered SASL2 is not
     * offered any FAST mechanism either. Deriving the XEP-0440 capability from those mechanisms announces a
     * channel-binding type that nothing offered can use, and records it as advertised, which the XEP-0474
     * downgrade-protection hash is computed over.
     */
    @Test
    public void appendSASLFeatures_recordsNoChannelBindingTypes_whenOnlyFastMechanismsNeedThem()
    {
        try (final MockedStatic<ChannelBindingProviderManager> managers = mockStatic(ChannelBindingProviderManager.class))
        {
            // Setup test fixture: an encrypted session that can supply tls-exporter, offered PLAIN and (were SASL2
            // available) the FAST variants that bind to it. SASL2 is disabled, so none of the latter can be offered.
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);
            managers.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-exporter"));
            when(manager.supportsChannelBinding("tls-exporter")).thenReturn(true);

            FastTokenManager.ENABLE_FAST.setValue(true);
            SASLAuthentication.ENABLE_SASL2.setValue(false);
            SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("PLAIN"));

            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(true);
            when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-exporter"));

            final StreamID streamID = new BasicStreamIDFactory().createStreamID();
            final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

            assertTrue(SaslMechanismEligibility.getAdvertisableSASLMechanisms(session).stream()
                    .anyMatch(mechanism -> MechanismName.isFast(mechanism) && mechanism.endsWith("-EXPR")),
                "Test setup issue: expected a channel-binding FAST variant to be eligible, so that suppressing it is " +
                    "what this test observes.");

            // Execute system under test.
            final List<Element> features = new ArrayList<>();
            SaslStreamFeatures.appendSASLFeatures(session, features);

            // Verify result.
            assertEquals(Set.of("PLAIN"), advertisedMechanismsIn(features),
                "Only the standard mechanism can be offered when SASL2, and with it the FAST inline feature, is unavailable.");
            assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())),
                "No channel-binding capability may be announced when no mechanism that could use one was offered.");
            assertEquals(Set.of(), SASLAuthentication.getAdvertisedChannelBindingTypes(session).orElseThrow(),
                "No channel-binding types may be recorded as advertised when none were, or the XEP-0474 hash the " +
                    "server computes will not match the one the peer computes.");
            assertEquals(Set.of(), FastSessionState.getAdvertisedMechanisms(session).orElseThrow(),
                "No FAST mechanisms may be recorded as advertised when the inline feature carrying them was not.");
        }
    }

    /**
     * A SASL2 element is still offered when only FAST mechanisms are eligible, since the XEP-0484 inline feature it
     * carries is how those are advertised.
     *
     * The element has no <mechanism> children in that case, which is only useful to a client that already holds a
     * token — but such a client can complete the negotiation, so suppressing the element would deny it a mechanism it
     * can actually use. Contrast the SASL1 element, which cannot carry FAST mechanisms at all.
     */
    @Test
    public void getSASLMechanismsElement_client_sasl2_onlyFastMechanisms_returnsElementWithInlineFeature()
    {
        // Setup test fixture: no standard mechanism is eligible, but FAST is enabled.
        FastTokenManager.ENABLE_FAST.setValue(true);
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.client.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        assertTrue(advertisableSASLMechanisms.stream().allMatch(MechanismName::isFast),
            "Test setup issue: expected only FAST mechanisms to be eligible, but found " + advertisableSASLMechanisms);

        // Execute system under test.
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNotNull(result, "A SASL2 element must still be offered when the inline feature can carry a usable mechanism.");
        assertTrue(result.elements("mechanism").isEmpty(), "No FAST mechanism may be rendered as a <mechanism/> child.");
        assertNotNull(result.element("inline").element(new QName("fast", Namespace.get("", FastTokenManager.NAMESPACE))),
            "The FAST mechanisms must be advertised in the inline feature instead.");
    }

    // -------------------------------------------------------------------------
    // Mechanism feature elements: inbound server sessions
    // -------------------------------------------------------------------------

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession returns a non-null (but empty) element
     * when there are no available mechanisms, SASL1 is used, and sasl.server.suppressEmpty is false.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl1_suppressEmptyFalse_noMechanisms_returnsEmptyElement()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, false);

        // Verify result.
        assertTrue(result != null && result.elements().isEmpty(),
            "Expected a non-null empty <mechanisms> element when suppressEmpty is false and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession returns null
     * when there are no available mechanisms, SASL1 is used, and sasl.server.suppressEmpty is true.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl1_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, false);

        // Verify result.
        assertNull(result, "Expected null when suppressEmpty is true and no mechanisms are available for SASL1.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.server.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl2_suppressEmptyFalse_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "false");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is false.");
    }

    /**
     * Verifies that getSASLMechanismsElement for a LocalIncomingServerSession always returns null
     * when there are no available mechanisms and SASL2 is used, regardless of sasl.server.suppressEmpty.
     */
    @Test
    public void getSASLMechanismsElement_server_sasl2_suppressEmptyTrue_noMechanisms_returnsNull()
    {
        // Setup test fixture: no mechanisms available (EXTERNAL requires encryption and a trusted cert).
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("EXTERNAL"));
        JiveGlobals.setProperty("sasl.server.suppressEmpty", "true");

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForServerSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNull(result, "Expected null for SASL2 when no mechanisms are available, even when suppressEmpty is true.");
    }

    // -------------------------------------------------------------------------
    // The XEP-0440 channel-binding capability element
    // -------------------------------------------------------------------------

    /**
     * Verifies that exactly one XEP-0440 capability element is advertised, even when both the SASL1 and SASL2
     * feature elements are offered.
     *
     * The previous implementation derived the capability element per SASL feature element, producing a duplicate
     * announcement whenever both profiles were advertised. XEP-0440 defines a single stream feature.
     */
    @Test
    public void asSASLMechanisms_advertisesChannelBindingTypesOnce_whenBothSasl1AndSasl2AreOffered()
    {
        // Setup test fixture.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final List<Element> features = SaslStreamFeatures.asSASLMechanisms(session, Set.of("PLAIN"), Set.of("tls-server-end-point", "tls-exporter"));
        assertEquals(1, features.stream().filter(e -> "mechanisms".equals(e.getName())).count(), "Test setup issue: expected a SASL1 mechanisms feature to be advertised.");
        assertEquals(1, features.stream().filter(e -> "authentication".equals(e.getName())).count(), "Test setup issue: expected a SASL2 authentication feature to be advertised.");

        // Verify result.
        final List<Element> capabilities = features.stream()
            .filter(e -> "sasl-channel-binding".equals(e.getName()))
            .toList();
        assertEquals(1, capabilities.size(), "Expected exactly one sasl-channel-binding feature, regardless of how many SASL profiles are advertised.");
        assertEquals(SASLAuthentication.SASL_CHANNEL_BINDING_NAMESPACE, capabilities.get(0).getNamespaceURI(), "Expected the capability element to be in the XEP-0440 namespace.");
        assertEquals(2, capabilities.get(0).elements("channel-binding").size(), "Expected one channel-binding child per advertised type.");
    }

    /**
     * Verifies that no channel-binding capability element is emitted when no types are advertised, rather than an
     * empty one. A peer following XEP-0474 treats the presence of the element as significant when computing the
     * downgrade protection hash, so an empty element is not equivalent to an absent one.
     */
    @Test
    public void asSASLMechanisms_omitsChannelBindingElement_whenNoTypesAdvertised()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final List<Element> features = SaslStreamFeatures.asSASLMechanisms(session, Set.of("PLAIN"), Set.of());

        // Verify result.
        assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())), "Expected no sasl-channel-binding feature when no channel-binding types are advertised.");
    }

    /**
     * Verifies that an unrecognised session type receives no SASL features at all, including no XEP-0440 capability
     * element, even when channel-binding types would otherwise be advertised.
     */
    @Test
    public void asSASLMechanisms_unknownSessionType_returnsNoFeatures()
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);

        // Execute system under test.
        final List<Element> features = SaslStreamFeatures.asSASLMechanisms(session, Set.of("SCRAM-SHA-1-PLUS"), Set.of("tls-exporter"));

        // Verify result.
        assertTrue(features.isEmpty(), "Expected no features for an unrecognised session type, including no channel-binding capability element.");
    }

    // -------------------------------------------------------------------------
    // Correspondence between what is rendered and what is recorded
    // -------------------------------------------------------------------------

    /**
     * Verifies that the SASL mechanisms and channel-binding types recorded on the session are exactly those
     * rendered into the stream features.
     *
     * This is the property the whole arrangement depends on: the XEP-0474 downgrade protection hash is computed
     * from the recorded sets, while a peer computes its own from what it received. If the two ever diverge, every
     * XEP-0474-aware authentication fails, for every user, with no indication of why. Testing the eligibility
     * lookup and asSASLMechanisms separately cannot catch that divergence; only driving appendSASLFeatures can.
     */
    @Test
    public void appendSASLFeatures_recordsExactlyWhatIsAdvertised_withChannelBinding()
    {
        try (final MockedStatic<AuthFactory> authFactory = mockStatic(AuthFactory.class);
             final MockedStatic<ChannelBindingProviderManager> managers = mockStatic(ChannelBindingProviderManager.class))
        {
            // Setup test fixture: an encrypted session that is offered SCRAM-SHA-1 and its -PLUS variant.
            SaslMechanismCatalog.setEnabledMechanisms(List.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
            authFactory.when(AuthFactory::supportsScram).thenReturn(true);
            authFactory.when(() -> AuthFactory.getScramMechanisms(any())).thenReturn(Set.of("SCRAM-SHA-1"));
            authFactory.when(AuthFactory::getFallbackScramMechanisms).thenReturn(Set.of("SCRAM-SHA-1"));

            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);
            managers.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-server-end-point", "tls-exporter"));

            final Connection connection = mock(Connection.class);
            when(connection.isEncrypted()).thenReturn(true);
            when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-server-end-point", "tls-exporter"));

            final StreamID streamID = new BasicStreamIDFactory().createStreamID();
            final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

            // Execute system under test.
            final List<Element> features = new ArrayList<>();
            SaslStreamFeatures.appendSASLFeatures(session, features);

            // Verify result.
            assertEquals(Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"), advertisedMechanismsIn(features),
                "Test setup issue: expected both SCRAM mechanisms to be offered to this session.");
            assertEquals(Set.of("tls-server-end-point", "tls-exporter"), advertisedChannelBindingTypesIn(features),
                "Test setup issue: expected both channel-binding types to be advertised to this session.");
            assertEquals(advertisedMechanismsIn(features), SASLAuthentication.getAdvertisedSASLMechanisms(session).orElseThrow(),
                "The recorded SASL mechanisms must be exactly those rendered into the stream features.");
            assertEquals(advertisedChannelBindingTypesIn(features), SASLAuthentication.getAdvertisedChannelBindingTypes(session).orElseThrow(),
                "The recorded channel-binding types must be exactly those rendered into the stream features.");
        }
    }

    /**
     * Verifies the same correspondence for a session that is offered no channel-binding-capable mechanism: no
     * capability element is rendered, and an empty set is recorded.
     *
     * Recording the empty set matters as much as recording a populated one. A peer that received no XEP-0440
     * announcement omits the channel-binding section from its hash input entirely, so the server must do the
     * same; an absent record would instead mean 'we do not know what was advertised'.
     */
    @Test
    public void appendSASLFeatures_recordsExactlyWhatIsAdvertised_withoutChannelBinding()
    {
        // Setup test fixture: an unencrypted session, offered only PLAIN.
        SaslMechanismCatalog.setEnabledMechanisms(List.of("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final List<Element> features = new ArrayList<>();
        SaslStreamFeatures.appendSASLFeatures(session, features);

        // Verify result.
        assertEquals(Set.of("PLAIN"), advertisedMechanismsIn(features),
            "Test setup issue: expected PLAIN to be the only mechanism offered to this session.");
        assertEquals(advertisedMechanismsIn(features), SASLAuthentication.getAdvertisedSASLMechanisms(session).orElseThrow(),
            "The recorded SASL mechanisms must be exactly those rendered into the stream features.");
        assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())),
            "Test setup issue: expected no channel-binding capability element to be rendered.");
        assertEquals(Set.of(), SASLAuthentication.getAdvertisedChannelBindingTypes(session).orElseThrow(),
            "An empty set must be recorded when no channel-binding types were advertised, distinct from nothing being recorded at all.");
    }

    // -------------------------------------------------------------------------
    // FAST / XEP-0484 inline feature
    // -------------------------------------------------------------------------

    /**
     * Verifies that the SASL2 inline feature element includes a FAST feature element when FAST is enabled.
     *
     * The SASL2 feature generated for a client session with at least one mechanism available
     * should include the FAST feature inside the &lt;inline/&gt; child.
     */
    @Test
    public void shouldIncludeFastFeatureInSasl2InlineWhenFastEnabled()
    {
        // Setup test fixture: SASL2 enabled, FAST enabled, PLAIN mechanism available.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        JiveGlobals.setProperty("xmpp.fast.enabled", "true");
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result.
        assertNotNull(result, "Expected a non-null SASL2 element.");
        final Element inlineEl = result.element("inline");
        assertNotNull(inlineEl, "Expected an <inline/> element inside the SASL2 authentication element.");
        final Element fastEl = inlineEl.element(new QName("fast", Namespace.get("", FastTokenManager.NAMESPACE)));
        assertNotNull(fastEl, "Expected a <fast/> element inside <inline/> when FAST is enabled.");
        assertEquals(FastTokenManager.NAMESPACE, fastEl.getNamespaceURI(),
            "Expected the <fast/> element to be in the FAST namespace.");
    }

    /**
     * Verifies that the SASL2 inline feature element does NOT include a FAST feature element when FAST is disabled.
     */
    @Test
    public void shouldNotIncludeFastFeatureInSasl2InlineWhenFastDisabled()
    {
        // Setup test fixture: SASL2 enabled, FAST disabled, PLAIN mechanism available.
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        JiveGlobals.setProperty("xmpp.fast.enabled", "false");
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("PLAIN"));

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> advertisableSASLMechanisms = SaslMechanismEligibility.getAdvertisableSASLMechanisms(session);
        final Element result = SaslStreamFeatures.asSASLMechanismsElementForClientSessions(advertisableSASLMechanisms, true);

        // Verify result: either no <inline/> or <inline/> without a <fast/> child.
        if (result != null) {
            final Element inlineEl = result.element("inline");
            if (inlineEl != null) {
                final Element fastEl = inlineEl.element(new QName("fast", Namespace.get("", FastTokenManager.NAMESPACE)));
                assertNull(fastEl, "Expected no <fast/> element inside <inline/> when FAST is disabled.");
            }
        }
        // If result is null, no mechanisms advertised, which is also acceptable (FAST not included).
    }

    /**
     * Verifies that no FAST mechanisms are recorded as advertised for a session that is not offered SASL2 at all,
     * since the XEP-0484 inline feature can only be carried inside the SASL2 feature element.
     */
    @Test
    public void appendFeaturesDoesNotRecordFastMechanismsWhenSasl2IsNotOffered() {
        FastTokenManager.ENABLE_FAST.setValue(true);
        SASLAuthentication.ENABLE_SASL2.setValue(false);
        SaslMechanismCatalog.setEnabledMechanisms(List.of("PLAIN"));
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);

        SaslStreamFeatures.appendSASLFeatures(session, new ArrayList<>());

        assertEquals(Set.of(), FastSessionState.getAdvertisedMechanisms(session).orElseThrow());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the SASL mechanism names rendered into the given stream features, across every SASL profile present.
     */
    private static Set<String> advertisedMechanismsIn(final List<Element> features)
    {
        return features.stream()
            .filter(e -> "mechanisms".equals(e.getName()) || "authentication".equals(e.getName()))
            .flatMap(e -> e.elements("mechanism").stream())
            .map(Element::getTextTrim)
            .collect(Collectors.toSet());
    }

    /**
     * Returns the channel-binding type names rendered into the given stream features.
     */
    private static Set<String> advertisedChannelBindingTypesIn(final List<Element> features)
    {
        return features.stream()
            .filter(e -> "sasl-channel-binding".equals(e.getName()))
            .flatMap(e -> e.elements("channel-binding").stream())
            .map(e -> e.attributeValue("type"))
            .collect(Collectors.toSet());
    }
}
