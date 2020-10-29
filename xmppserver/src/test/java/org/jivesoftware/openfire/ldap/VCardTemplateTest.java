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
import org.dom4j.io.SAXReader;
import org.jivesoftware.util.CertificateManager;
import org.junit.Test;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Unit tests that verify the implementation of {@link org.jivesoftware.openfire.ldap.LdapVCardProvider.VCardTemplate}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class VCardTemplateTest
{
    /**
     * Verifies that, using a simplified template, a placeholder gets correctly identified in a VCardTemplate.
     */
    @Test
    public void testIdentifyPlaceholder() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>{placeholder}</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertNotNull( result.getAttributes() );
        assertEquals( 1, result.getAttributes().length );
        assertEquals( "placeholder", result.getAttributes()[0] );
    }

    /**
     * Verifies that, using a simplified template, a placeholder gets correctly identified in a VCardTemplate when the
     * element value is made up of more than just the placeholder.
     */
    @Test
    public void testIdentifyEmbeddedPlaceholder() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>foo{placeholder}bar</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertNotNull( result.getAttributes() );
        assertEquals( 1, result.getAttributes().length );
        assertEquals( "placeholder", result.getAttributes()[0] );
    }

    /**
     * Verifies that, using a simplified template, a placeholder gets correctly identified in a VCardTemplate when the
     * element value is made up of more than just the placeholder.
     */
    @Test
    public void testIdentifyEmbeddedPlaceholderVariant2() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>foo, {placeholder} bar</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertNotNull( result.getAttributes() );
        assertEquals( 1, result.getAttributes().length );
        assertEquals( "placeholder", result.getAttributes()[0] );
    }

    /**
     * Verifies that, using a simplified template, all placeholders get correctly identified in a VCardTemplate, when
     * they're defined in the value of the same element.
     */
    @Test
    public void testIdentifyPlaceholders() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>{placeholderA}, {placeholderB}</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertNotNull( result.getAttributes() );
        assertEquals( 2, result.getAttributes().length );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderA") );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderB") );
    }

    /**
     * Verifies that, using a simplified template, all placeholders get correctly identified in a VCardTemplate, when
     * they're defined in the value of the same element that also holds additional, hardcoded values.
     */
    @Test
    public void testIdentifyEmbeddedPlaceholders() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>foo {placeholderA}, {placeholderB} bar</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertNotNull( result.getAttributes() );
        assertEquals( 2, result.getAttributes().length );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderA") );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderB") );
    }

    /**
     * Verifies that, using a simplified template, all placeholders get correctly identified in a VCardTemplate, when
     * they're defined in the format that uses the first non-empty placeholder that's available.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testIdentifyPrioritizedPlaceholders() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>(|({placeholderA})({placeholderB})({placeholderC}))</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertNotNull( result.getAttributes() );
        assertEquals( 3, result.getAttributes().length );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderA") );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderB") );
        assertTrue( Arrays.asList( result.getAttributes()).contains("placeholderC") );
    }

    /**
     * Verifies that, using a simplified template, attribute values do not get wrongly identified as a placeholder.
     */
    @Test
    public void testIdentifyNonPlaceholder() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>placeholder</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertTrue ( result.getAttributes() == null || result.getAttributes().length == 0 );
    }

    /**
     * Verifies that, using a simplified template, attribute values do not get wrongly identified as a placeholder, even
     * when the value contains a separator character used in the implementation to distinguish individual placeholders.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1947">OF-1947</a>
     */
    @Test
    public void testIdentifyNonPlaceholderWithSeparatorChar() throws Exception
    {
        // Setup fixture.
        final Document input = DocumentHelper.parseText("<vcard><el>placeholder/me</el></vcard>");

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertTrue ( result.getAttributes() == null || result.getAttributes().length == 0 );
    }

    /**
     * Verifies that all placeholder gets correctly identified in the full, default VCardTemplate.
     */
    @Test
    public void testDefaultTemplate() throws Exception
    {
        // Setup fixture.
        final Document input;
        try ( final InputStream stream = getClass().getResourceAsStream("/org/jivesoftware/openfire/ldap/vcardmapping-default.xml" ) )
        {
            SAXReader reader = new SAXReader();
            input = reader.read(stream);
        }

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "jpegPhoto" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "st" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "telephoneNumber" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "mail" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "displayName" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "postalCode" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "homePhone" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "mobile" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "cn" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "l" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "title" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "homePostalAddress" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "uid" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "postalAddress" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "pager" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "departmentNumber" ) );
        assertEquals( 16, result.getAttributes().length );
    }

    /**
     * Verifies that all placeholder gets correctly identified in the full, default VCardTemplate that has been
     * modified to have one element that uses a combination of more than one placeholder.
     */
    @Test
    public void testCombinedTemplate() throws Exception
    {
        // Setup fixture.
        final Document input;
        try ( final InputStream stream = getClass().getResourceAsStream("/org/jivesoftware/openfire/ldap/vcardmapping-combined.xml" ) )
        {
            SAXReader reader = new SAXReader();
            input = reader.read(stream);
        }

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "jpegPhoto" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "st" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "telephoneNumber" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "mail" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "lastName" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "givenName" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "postalCode" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "homePhone" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "mobile" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "cn" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "l" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "title" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "homePostalAddress" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "uid" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "postalAddress" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "pager" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "departmentNumber" ) );
        assertEquals( 17, result.getAttributes().length );
    }

    /**
     * Verifies that all placeholder gets correctly identified in the full, default VCardTemplate that has been
     * modified to have one element that uses a combination of more than one placeholder in the format that uses the
     * first non-empty placeholder that's available.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1106">OF-1106</a>
     */
    @Test
    public void testPrioritizedTemplate() throws Exception
    {
        // Setup fixture.
        final Document input;
        try ( final InputStream stream = getClass().getResourceAsStream("/org/jivesoftware/openfire/ldap/vcardmapping-prioritized.xml" ) )
        {
            SAXReader reader = new SAXReader();
            input = reader.read(stream);
        }

        // Execute system under test.
        final LdapVCardProvider.VCardTemplate result = new LdapVCardProvider.VCardTemplate(input);

        // Verify result.
        assertNotNull( result );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "jpegPhoto" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "st" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "telephoneNumber" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "mail" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "fullName" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "givenName" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "postalCode" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "homePhone" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "mobile" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "cn" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "l" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "title" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "homePostalAddress" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "uid" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "postalAddress" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "pager" ) );
        assertTrue( Arrays.asList( result.getAttributes() ).contains( "departmentNumber" ) );
        assertEquals( 17, result.getAttributes().length );
    }
}
