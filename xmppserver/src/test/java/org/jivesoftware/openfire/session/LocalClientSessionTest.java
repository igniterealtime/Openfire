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
package org.jivesoftware.openfire.session;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LocalClientSession}.
 */
public class LocalClientSessionTest
{
    /**
     * Verifies SASL1 channel-binding advertisement.
     */
    @Test
    public void shouldAppendChannelBindingCapabilityForSasl1()
    {
        // Setup test fixture.
        final Element mechanisms = DocumentHelper.createElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final List<Element> features = new ArrayList<>(List.of(mechanisms));
        final Element capability = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);

            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(mechanisms)).thenReturn(Optional.of(capability));

            // Execute system under test.
            LocalClientSession.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            assertTrue(features.contains(capability), "A SASL1 mechanisms feature with channel-binding support should result in a sasl-channel-binding feature being added.");
        }
    }

    /**
     * Verifies SASL2 channel-binding advertisement.
     */
    @Test
    public void shouldAppendChannelBindingCapabilityForSasl2()
    {
        // Setup test fixture.
        final Element authentication = DocumentHelper.createElement(QName.get("authentication", "urn:xmpp:sasl:2"));
        final List<Element> features = new ArrayList<>(List.of(authentication));
        final Element capability = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);

            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(authentication)).thenReturn(Optional.of(capability));

            // Execute system under test.
            LocalClientSession.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            assertTrue(features.contains(capability), "A SASL2 authentication feature with channel-binding support should result in a sasl-channel-binding feature being added.");
        }
    }

    /**
     * Verifies no advertisement without a SASL feature.
     */
    @Test
    public void shouldNotAppendChannelBindingCapabilityWhenNoSaslFeatureExists()
    {
        // Setup test fixture.
        final Element compression = DocumentHelper.createElement(QName.get("compression", "http://jabber.org/features/compress"));
        final List<Element> features = new ArrayList<>(List.of(compression));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            // Execute system under test.
            LocalClientSession.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            mocked.verifyNoInteractions();

            assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())),"No sasl-channel-binding feature should be added when no SASL feature is present.");
        }
    }

    /**
     * Verifies no advertisement when unavailable.
     */
    @Test
    public void shouldNotAppendChannelBindingCapabilityWhenProviderReturnsEmpty()
    {
        // Setup test fixture.
        final Element mechanisms = DocumentHelper.createElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final List<Element> features = new ArrayList<>(List.of(mechanisms));

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);

            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(mechanisms)).thenReturn(Optional.empty());

            // Execute system under test.
            LocalClientSession.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            assertFalse(features.stream().anyMatch(e -> "sasl-channel-binding".equals(e.getName())), "No sasl-channel-binding feature should be added when no channel-binding types are available.");
        }
    }

    /**
     * Verifies SASL1 and SASL2 channel-binding advertisement (simultaneously).
     */
    @Test
    public void shouldAppendChannelBindingCapabilityForBothSasl1AndSasl2()
    {
        // Setup test fixture.
        final Element mechanisms = DocumentHelper.createElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final Element authentication = DocumentHelper.createElement(QName.get("authentication", "urn:xmpp:sasl:2"));
        final List<Element> features = new ArrayList<>(List.of(mechanisms, authentication));

        // Distinct, identifiable return values so the assertions can attribute each to its source feature.
        final Element cbForSasl1 = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));
        cbForSasl1.addAttribute("test-source", "sasl1");
        final Element cbForSasl2 = DocumentHelper.createElement(QName.get("sasl-channel-binding", "urn:xmpp:sasl-cb:0"));
        cbForSasl2.addAttribute("test-source", "sasl2");

        try (final MockedStatic<ChannelBindingProviderManager> mocked = mockStatic(ChannelBindingProviderManager.class))
        {
            final ChannelBindingProviderManager manager = mock(ChannelBindingProviderManager.class);
            mocked.when(ChannelBindingProviderManager::getInstance).thenReturn(manager);
            when(manager.getSASLChannelBindingTypeCapabilityElement(mechanisms)).thenReturn(Optional.of(cbForSasl1));
            when(manager.getSASLChannelBindingTypeCapabilityElement(authentication)).thenReturn(Optional.of(cbForSasl2));

            // Execute system under test
            LocalClientSession.appendChannelBindingCapabilityIfNeeded(features);

            // Verify result.
            verify(manager).getSASLChannelBindingTypeCapabilityElement(authentication);
            verify(manager).getSASLChannelBindingTypeCapabilityElement(mechanisms);
            assertTrue(features.contains(cbForSasl2), "SASL2 must receive channel-binding caps even when SASL1 is also advertised.");
            assertTrue(features.contains(cbForSasl1), "SASL1 caps should also be present in the dual-stack case.");
        }
    }
}
