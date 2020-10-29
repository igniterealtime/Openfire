/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.ldap;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests that verify the implementation of {@link LdapVCardProvider.VCard}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class VCardTest
{
    /**
     * Verifies that, using a simplified template, a placeholder in a template gets correctly replaced.
     */
    @Test
    public void testReplacePlaceholder() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>{placeholder}</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholder", "value");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>value</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, a placeholder embedded in a element value in a template gets
     * correctly replaced.
     */
    @Test
    public void testReplaceEmbeddedPlaceholder() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>foo{placeholder}bar</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholder", "value");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>foovaluebar</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, a placeholder embedded in a element value in a template gets
     * correctly replaced.
     */
    @Test
    public void testReplaceEmbeddedPlaceholderVariant2() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>foo, {placeholder} bar</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholder", "value");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>foo, value bar</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template (defined in the same element) get
     * correctly replaced.
     */
    @Test
    public void testReplacePlaceholders() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>{placeholderA}, {placeholderB}</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "valueA");
        attributes.put("placeholderB", "valueB");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>valueA, valueB</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template defined in the format that is intended
     * to be replaced with the first non-empty matching attribute value, get correctly replaced.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testReplacePrioritizedPlaceholders() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>(|({placeholderA})({placeholderB})({placeholderC}))</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "");
        attributes.put("placeholderB", "valueB");
        attributes.put("placeholderC", "valueC");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>valueB</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template defined in the format that is intended
     * to be replaced with the first non-empty matching attribute value, get correctly replaced.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testReplacePrioritizedPlaceholdersVariantA() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>(|({placeholderA})({placeholderB})({placeholderC}))</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "valueA");
        attributes.put("placeholderB", "valueB");
        attributes.put("placeholderC", "valueC");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>valueA</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template defined in the format that is intended
     * to be replaced with the first non-empty matching attribute value, get correctly replaced.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testReplacePrioritizedPlaceholdersVariantB() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>(|({placeholderA})({placeholderB})({placeholderC}))</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "valueA");
        attributes.put("placeholderB", "");
        attributes.put("placeholderC", "");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>valueA</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template defined in the format that is intended
     * to be replaced with the first non-empty matching attribute value, get correctly replaced.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testReplacePrioritizedPlaceholdersVariantC() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>(|({placeholderA})({placeholderB})({placeholderC}))</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "");
        attributes.put("placeholderB", "");
        attributes.put("placeholderC", "valueC");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>valueC</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template defined in the format that is intended
     * to be replaced with the first non-empty matching attribute value, get correctly replaced.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testReplacePrioritizedPlaceholdersVariantD() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>(|({placeholderA})({placeholderB})({placeholderC}))</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "");
        attributes.put("placeholderB", "");
        attributes.put("placeholderC", "");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el></el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, all placeholder in a template (defined in the same element) get
     * correctly replaced, when part of a larger element value.
     */
    @Test
    public void testReplaceEmbeddedPlaceholders() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>foo {placeholderA}, {placeholderB} bar</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholderA", "valueA");
        attributes.put("placeholderB", "valueB");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>foo valueA, valueB bar</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, element values that are not a placeholder do not get replaced.
     */
    @Test
    public void testDontReplaceNonPlaceholder() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>placeholder</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("placeholder", "value");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>placeholder</el></vcard>", result.asXML() );
    }

    /**
     * Verifies that, using a simplified template, element values that are not a placeholder do not get replaced, even
     * if the elemnent value contains a separator character used in the implementation to distinguish individual
     * placeholders.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1947">OF-1947</a>
     */
    @Test
    public void testIdentifyNonPlaceholderWithSeparatorChar() throws Exception
    {
        // Setup fixture.
        final Document doc = DocumentHelper.parseText("<vcard><el>place/holder</el></vcard>");
        final LdapVCardProvider.VCardTemplate template = new LdapVCardProvider.VCardTemplate(doc);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("place", "value");
        attributes.put("holder", "value");

        // Execute system under test.
        final LdapVCardProvider.VCard vCard = new LdapVCardProvider.VCard(template);
        final Element result = vCard.getVCard(attributes);

        // Verify result.
        assertNotNull( result );
        assertEquals( "<vcard><el>place/holder</el></vcard>", result.asXML() );
    }
}
