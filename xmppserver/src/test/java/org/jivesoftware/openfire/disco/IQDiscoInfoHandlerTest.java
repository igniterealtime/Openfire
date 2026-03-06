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
package org.jivesoftware.openfire.disco;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.util.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IQDiscoInfoHandler}.
 *
 * <p>This test class verifies the form merging and management logic within IQDiscoInfoHandler,
 * specifically testing how the handler processes and combines data forms from multiple
 * {@link ExtendedDiscoInfoProvider} instances according to XEP-0128: Service Discovery Extensions.</p>
 *
 * <p>The tests focus on:
 * <ul>
 *   <li>Merging of forms with the same FORM_TYPE</li>
 *   <li>Addition of forms with different FORM_TYPEs</li>
 *   <li>Handling of duplicate field names (entire form rejection)</li>
 *   <li>Preservation of field types and values during merging</li>
 *   <li>Dynamic provider addition and removal</li>
 *   <li>Edge cases like empty sets, immutable collections, and fields without types</li>
 * </ul>
 * </p>
 *
 * @author Dan Caseley, dan@caseley.me.uk
 * @see IQDiscoInfoHandler
 * @see ExtendedDiscoInfoProvider
 * @see <a href="https://xmpp.org/extensions/xep-0128.html">XEP-0128: Service Discovery Extensions</a>
 */
class IQDiscoInfoHandlerTest {

    @BeforeEach
    public void setUp() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();

        // Mock XMPPServer
        XMPPServer xmppServer = Fixtures.mockXMPPServer();
        // Provide at least one admin to avoid null return from getExtendedInfos
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Mock XMPPServerInfo with version
        XMPPServerInfo serverInfo = xmppServer.getServerInfo();
        Version version = mock(Version.class);
        when(version.getVersionString()).thenReturn("5.1.0-TEST");
        when(serverInfo.getVersion()).thenReturn(version);

        XMPPServer.setInstance(xmppServer);
    }

    @AfterEach
    public void tearDown() {
        XMPPServer.setInstance(null);
    }

    /**
     * Test implementation of {@link ExtendedDiscoInfoProvider} that returns a predefined set of data forms.
     *
     * <p>This provider mimics the behavior of real providers by only returning forms for
     * server-level queries (no node, no name, matching server domain).</p>
     */
    class TestExtendedDiscoInfoProvider implements ExtendedDiscoInfoProvider {

        private final Set<DataForm> forms;

        TestExtendedDiscoInfoProvider(Set<DataForm> forms) {
            this.forms = forms;
        }

        @Override
        public Set<DataForm> getExtendedInfos(String domain, String name, String node, JID senderJID) {
            // Only return forms for server domain, service-level queries (matching real provider behavior)
            if (domain != null && !domain.equals(Fixtures.XMPP_DOMAIN)) {
                return Collections.emptySet();
            }
            if (name != null) {
                return Collections.emptySet();
            }
            if (node != null) {
                return Collections.emptySet();
            }
            return forms;
        }
    }


    private DataForm createForm(String formType, String field, String value) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField type = form.addField();
        type.setVariable("FORM_TYPE");
        type.setType(FormField.Type.hidden);
        type.addValue(formType);

        FormField f = form.addField();
        f.setVariable(field);
        f.setType(FormField.Type.text_single);
        f.addValue(value);

        return form;
    }

    private DataForm testForm(String field, String value) {
        return createForm("urn:xmpp:dataforms:openfire-unittest", field, value);
    }

    private String getFormType(DataForm form) {
        return form.getFields().stream()
            .filter(f -> "FORM_TYPE".equals(f.getVariable()))
            .map(FormField::getFirstValue)
            .findFirst()
            .orElse(null);
    }

    private DiscoInfoProvider invokeGetServerInfoProvider(IQDiscoInfoHandler handler) {
        try {
            Method m = IQDiscoInfoHandler.class
                .getDeclaredMethod("getServerInfoProvider");
            m.setAccessible(true);
            return (DiscoInfoProvider) m.invoke(handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to get extended disco info forms from a handler by simulating a disco#info request.
     *
     * <p>This method tests through the full handleIQ() flow where ExtendedDiscoInfoProviders are applied.
     * It creates a disco#info IQ request, processes it through the handler, and extracts the resulting
     * data forms from the response.</p>
     *
     * @param handler the IQDiscoInfoHandler instance to test
     * @param from the JID of the requesting entity
     * @param to the JID of the target entity (null for server domain)
     * @return the set of data forms included in the disco#info response
     */
    private Set<DataForm> getExtendedInfosViaHandleIQ(IQDiscoInfoHandler handler, JID from, JID to) {
        try {
            // Initialize the handler (this registers the server disco info provider)
            handler.initialize(XMPPServer.getInstance());
            handler.start();

            // Create disco#info request
            org.xmpp.packet.IQ request = new org.xmpp.packet.IQ(org.xmpp.packet.IQ.Type.get);
            request.setFrom(from);
            if (to != null) {
                request.setTo(to);
            } else {
                // If no 'to' specified, it goes to the server domain
                request.setTo(new JID(Fixtures.XMPP_DOMAIN));
            }
            request.setChildElement("query", "http://jabber.org/protocol/disco#info");

            // Handle the request
            org.xmpp.packet.IQ response = handler.handleIQ(request);

            // Parse the response to extract DataForms
            Set<DataForm> forms = new HashSet<>();
            if (response != null && response.getChildElement() != null) {
                for (Object element : response.getChildElement().elements("x")) {
                    if (element instanceof org.dom4j.Element) {
                        org.dom4j.Element xElement = (org.dom4j.Element) element;
                        if ("jabber:x:data".equals(xElement.getNamespaceURI())) {
                            forms.add(new DataForm(xElement));
                        }
                    }
                }
            }
            return forms;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get extended infos via handleIQ", e);
        }
    }

    /**
     * Verifies that when multiple providers return forms with the same FORM_TYPE,
     * the handler merges them into a single form containing all fields.
     *
     * <p>This is the core functionality for XEP-0128 form aggregation.</p>
     */
    @Test
    void testSameFormTypeIsMerged() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        DataForm f1 = testForm("field1", "value1");
        DataForm f2 = testForm("field2", "value2");

        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(f1))
        );
        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(f2))
        );

        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        DataForm merged = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .findFirst()
            .orElseThrow();

        Set<String> fieldNames = merged.getFields().stream()
            .map(FormField::getVariable)
            .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("FORM_TYPE"));
        assertTrue(fieldNames.contains("field1"));
        assertTrue(fieldNames.contains("field2"));
        assertEquals(3, fieldNames.size()); // FORM_TYPE + field1 + field2
    }

    /**
     * Verifies that when multiple providers return forms with different FORM_TYPEs,
     * all forms are included in the response without merging.
     */
    @Test
    void testDifferentFormTypesAreAdded() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        DataForm form1 = testForm("field1", "value1");
        DataForm form2 = createForm("urn:xmpp:dataforms:openfire-unittest:other", "field2", "value2");

        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form1))
        );
        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form2))
        );

        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        long testFormCount = result.stream()
            .filter(f -> {
                String formType = getFormType(f);
                return formType != null && formType.startsWith("urn:xmpp:dataforms:openfire-unittest");
            })
            .count();

        assertEquals(2, testFormCount);
    }

    /**
     * Verifies that the handler gracefully handles providers that return empty sets
     * without errors or including empty forms in the response.
     */
    @Test
    void testEmptySetIsHandled() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Collections.emptySet())
        );

        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        assertNotNull(result);
        // Result should not contain our test forms since provider returned empty set
        long testFormCount = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .count();
        assertEquals(0, testFormCount);
    }

    /**
     * Verifies that when merging forms with the same FORM_TYPE, fields with different types
     * (text_single, list_multi, boolean_type) are correctly preserved and merged together.
     */
    @Test
    void testAdditionalFieldsToExistingForm() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        // Form 1 with mixed field types
        DataForm form1 = new DataForm(DataForm.Type.result);
        FormField typeField1 = form1.addField();
        typeField1.setVariable("FORM_TYPE");
        typeField1.setType(FormField.Type.hidden);
        typeField1.addValue("urn:xmpp:dataforms:openfire-unittest");

        FormField field1 = form1.addField();
        field1.setVariable("field1");
        field1.setType(FormField.Type.text_single); // Single-value
        field1.addValue("value1");

        // Form 2 with multiple fields (mixed types)
        DataForm form2 = new DataForm(DataForm.Type.result);
        FormField typeField2 = form2.addField();
        typeField2.setVariable("FORM_TYPE");
        typeField2.setType(FormField.Type.hidden);
        typeField2.addValue("urn:xmpp:dataforms:openfire-unittest");

        FormField field2 = form2.addField();
        field2.setVariable("field2");
        field2.setType(FormField.Type.list_multi); // Multi-value
        field2.addValue("value2");

        FormField field3 = form2.addField();
        field3.setVariable("field3");
        field3.setType(FormField.Type.boolean_type); // Single-value
        field3.addValue("true");

        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form1))
        );
        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form2))
        );

        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        DataForm merged = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .findFirst()
            .orElseThrow();

        Set<String> fieldNames = merged.getFields().stream()
            .map(FormField::getVariable)
            .collect(Collectors.toSet());

        assertEquals(4, fieldNames.size()); // FORM_TYPE + field1 + field2 + field3
        assertTrue(fieldNames.contains("field1"));
        assertTrue(fieldNames.contains("field2"));
        assertTrue(fieldNames.contains("field3"));

        // Verify field types are preserved
        assertEquals(FormField.Type.text_single, merged.getField("field1").getType());
        assertEquals(FormField.Type.list_multi, merged.getField("field2").getType());
        assertEquals(FormField.Type.boolean_type, merged.getField("field3").getType());
    }

    /**
     * Verifies that the handler can process immutable sets (created via Set.of())
     * returned by providers without throwing UnsupportedOperationException.
     */
    @Test
    void testImmutableSetIsHandled() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        // Use Set.of() to create an immutable set
        DataForm form = testForm("field1", "value1");
        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form))
        );

        // This should not throw an exception even though provider returns immutable set
        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        assertNotNull(result);
        assertTrue(result.stream()
            .anyMatch(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f))));
    }

    /**
     * Verifies that when merging fields without explicit types, the merged result
     * also has no type set (preserving the absence of type information).
     */
    @Test
    void testTypeNotAddedIfNeitherHadType() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        // Both providers create field without type
        DataForm form1 = new DataForm(DataForm.Type.result);
        FormField typeField1 = form1.addField();
        typeField1.setVariable("FORM_TYPE");
        typeField1.setType(FormField.Type.hidden);
        typeField1.addValue("urn:xmpp:dataforms:openfire-unittest");

        FormField field1 = form1.addField();
        field1.setVariable("field1");
        // No type set
        field1.addValue("value1");

        DataForm form2 = new DataForm(DataForm.Type.result);
        FormField typeField2 = form2.addField();
        typeField2.setVariable("FORM_TYPE");
        typeField2.setType(FormField.Type.hidden);
        typeField2.addValue("urn:xmpp:dataforms:openfire-unittest");

        FormField field2 = form2.addField();
        field2.setVariable("field2");
        // No type set
        field2.addValue("value2");

        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form1))
        );
        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form2))
        );

        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        DataForm merged = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .findFirst()
            .orElseThrow();

        FormField mergedField1 = merged.getField("field1");
        assertNotNull(mergedField1);
        // Field should still have no type
        assertNull(mergedField1.getType());

        FormField mergedField2 = merged.getField("field2");
        assertNotNull(mergedField2);
        // Field should still have no type
        assertNull(mergedField2.getType());
    }

    /**
     * Verifies that when a provider's form contains a field that duplicates an existing field name,
     * the entire form from that provider is rejected to maintain data integrity.
     *
     * <p>This ensures that conflicting field definitions don't corrupt the merged form.
     * If provider A adds field1, and provider B's form contains both field1 (duplicate) and field2 (new),
     * neither field from provider B should be added.</p>
     */
    @Test
    void testDuplicateFieldRejectsEntireForm() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        // First provider: field1 only
        DataForm form1 = new DataForm(DataForm.Type.result);
        FormField typeField1 = form1.addField();
        typeField1.setVariable("FORM_TYPE");
        typeField1.setType(FormField.Type.hidden);
        typeField1.addValue("urn:xmpp:dataforms:openfire-unittest");

        FormField field1 = form1.addField();
        field1.setVariable("field1");
        field1.addValue("value1");

        // Second provider: field1 (duplicate) + field2 (non-duplicate)
        // Expected: Neither field should be added because the form contains a duplicate
        DataForm form2 = new DataForm(DataForm.Type.result);
        FormField typeField2 = form2.addField();
        typeField2.setVariable("FORM_TYPE");
        typeField2.setType(FormField.Type.hidden);
        typeField2.addValue("urn:xmpp:dataforms:openfire-unittest");

        FormField field2new = form2.addField();
        field2new.setVariable("field2");  // Non-duplicate field
        field2new.addValue("value2");

        FormField field2duplicate = form2.addField();
        field2duplicate.setVariable("field1");  // Duplicate field name
        field2duplicate.addValue("value1-from-second-provider");

        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form1))
        );
        handler.addExtendedDiscoInfoProvider(
            new TestExtendedDiscoInfoProvider(Set.of(form2))
        );

        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);

        DataForm merged = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .findFirst()
            .orElseThrow();

        // Should have field1 from first provider only
        assertNotNull(merged.getField("field1"));
        assertEquals("value1", merged.getField("field1").getFirstValue());

        // field2 should NOT exist - entire second provider's form was rejected due to duplicate
        assertNull(merged.getField("field2"));

        // Should only have 2 fields total: FORM_TYPE and field1
        assertEquals(2, merged.getFields().size());
    }

    /**
     * Verifies that removing a provider dynamically removes its contributed forms
     * from subsequent disco#info responses.
     */
    @Test
    void testRemoveProviderRemovesContributions() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        // Create a provider with a test form
        DataForm form = testForm("field1", "value1");
        TestExtendedDiscoInfoProvider provider = new TestExtendedDiscoInfoProvider(Set.of(form));

        // Add the provider
        handler.addExtendedDiscoInfoProvider(provider);

        // Verify the form appears in the response
        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);
        long formCountBefore = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .count();
        assertEquals(1, formCountBefore, "Form should be present after adding provider");

        // Remove the provider - need to create a new handler instance to test removal
        handler.removeExtendedDiscoInfoProvider(provider);

        // Verify the form no longer appears in the response
        result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);
        long formCountAfter = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .count();
        assertEquals(0, formCountAfter, "Form should be removed after removing provider");
    }

    /**
     * Verifies that a provider can be removed and then re-added, with its forms
     * correctly appearing, disappearing, and reappearing in the disco#info responses.
     */
    @Test
    void testRemoveAndReAddProvider() {
        IQDiscoInfoHandler handler = new IQDiscoInfoHandler();

        // Create a provider with a test form
        DataForm form = testForm("field1", "value1");
        TestExtendedDiscoInfoProvider provider = new TestExtendedDiscoInfoProvider(Set.of(form));

        // Add the provider and verify form appears
        handler.addExtendedDiscoInfoProvider(provider);
        Set<DataForm> result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);
        long formCountAfterAdd = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .count();
        assertEquals(1, formCountAfterAdd, "Form should be present after adding provider");

        // Remove the provider and verify form disappears
        handler.removeExtendedDiscoInfoProvider(provider);
        result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);
        long formCountAfterRemove = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .count();
        assertEquals(0, formCountAfterRemove, "Form should be removed after removing provider");

        // Re-add the same provider and verify form appears again
        handler.addExtendedDiscoInfoProvider(provider);
        result = getExtendedInfosViaHandleIQ(handler,
            new JID("tester@example.org"), null);
        long formCountAfterReAdd = result.stream()
            .filter(f -> "urn:xmpp:dataforms:openfire-unittest".equals(getFormType(f)))
            .count();
        assertEquals(1, formCountAfterReAdd, "Form should be present after re-adding provider");
    }


}

