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
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ContactAddressesExtendedDiscoInfoProvider}.
 *
 * <p>This test class verifies the behavior of the ContactAddressesExtendedDiscoInfoProvider,
 * which provides contact address information (admin addresses) in service discovery responses
 * according to XEP-0157: Contact Addresses for XMPP Services.</p>
 *
 * <p>The tests cover various scenarios including:
 * <ul>
 *   <li>Inclusion of admin contact addresses when admins exist</li>
 *   <li>Exclusion of information when admin exposure is disabled</li>
 *   <li>Proper filtering based on domain, node, and name parameters</li>
 *   <li>Handling of multiple administrators and external admin JIDs</li>
 *   <li>Correct form structure and field types</li>
 * </ul>
 * </p>
 *
 * @author Dan Caseley, dan@caseley.me.uk
 * @see ContactAddressesExtendedDiscoInfoProvider
 * @see <a href="https://xmpp.org/extensions/xep-0157.html">XEP-0157: Contact Addresses for XMPP Services</a>
 */
class ContactAddressesExtendedDiscoInfoProviderTest {

    private ContactAddressesExtendedDiscoInfoProvider provider;
    private XMPPServer xmppServer;

    @BeforeEach
    public void setUp() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();

        // Create provider
        provider = new ContactAddressesExtendedDiscoInfoProvider();

        // Mock XMPPServer
        xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);
    }

    @AfterEach
    public void tearDown() {
        XMPPServer.setInstance(null);
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that the provider returns a properly formatted data form containing admin contact addresses
     * when administrators are configured on the server.
     *
     * <p>The form should include:
     * <ul>
     *   <li>A hidden FORM_TYPE field with value "http://jabber.org/network/serverinfo"</li>
     *   <li>A list_multi field called admin-addresses containing XMPP addresses</li>
     * </ul>
     * </p>
     */
    @Test
    public void testReturnsContactAddressesWhenAdminsExist() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertEquals(1, forms.size());

        DataForm form = forms.iterator().next();
        assertEquals(DataForm.Type.result, form.getType());

        // Check FORM_TYPE field
        FormField formType = form.getField("FORM_TYPE");
        assertNotNull(formType);
        assertEquals(FormField.Type.hidden, formType.getType());
        assertEquals("http://jabber.org/network/serverinfo", formType.getFirstValue());

        // Check admin-addresses field
        FormField adminAddresses = form.getField("admin-addresses");
        assertNotNull(adminAddresses);
        assertEquals(FormField.Type.list_multi, adminAddresses.getType());
        List<String> values = adminAddresses.getValues();
        assertTrue(values.contains("xmpp:admin@" + Fixtures.XMPP_DOMAIN));
        // Email addresses might be included if UserManager can resolve the user
    }

    /**
     * Verifies that the provider returns an empty set when admin exposure is disabled
     * via the "admin.disable-exposure" system property
     */
    @Test
    public void testReturnsEmptyWhenAdminExposureDisabled() {
        // Setup
        IQDiscoInfoHandler.DISABLE_EXPOSURE.setValue(true);
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when querying a specific node,
     * as contact addresses are only provided for the server itself, not for nodes.
     */
    @Test
    public void testReturnsEmptyWhenNodeIsNotNull() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, "somenode", new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when querying a domain that
     * doesn't match the server's domain, as contact addresses are only provided for
     * the local server.
     */
    @Test
    public void testReturnsEmptyWhenDomainDoesNotMatchServerDomain() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos("other.domain", null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when querying a specific user,
     * as contact addresses are only provided for server-level queries, not user-level queries.
     */
    @Test
    public void testReturnsEmptyWhenNameIsNotNull() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, "someuser", null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when no administrators are configured,
     * as there are no contact addresses to expose.
     */
    @Test
    public void testReturnsEmptyWhenNoAdmins() {
        // Setup
        when(xmppServer.getAdmins()).thenReturn(Collections.emptySet());

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider handles null admin sets gracefully by returning an empty set,
     * preventing null pointer exceptions.
     */
    @Test
    public void testReturnsEmptyWhenAdminsIsNull() {
        // Setup
        when(xmppServer.getAdmins()).thenReturn(null);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider correctly returns contact addresses when querying
     * the server domain itself with no node or name specified.
     */
    @Test
    public void testWorksForServerDomain() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute with domain matching server domain
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertEquals(1, forms.size());
    }

    /**
     * Verifies that the provider correctly includes all administrators, including those
     * with JIDs from external domains, in the admin-addresses field.
     */
    @Test
    public void testHandlesMultipleAdmins() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin1@" + Fixtures.XMPP_DOMAIN));
        admins.add(new JID("admin2@" + Fixtures.XMPP_DOMAIN));
        admins.add(new JID("admin3@other.domain")); // External admin
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertEquals(1, forms.size());

        DataForm form = forms.iterator().next();
        FormField adminAddresses = form.getField("admin-addresses");
        assertNotNull(adminAddresses);
        List<String> values = adminAddresses.getValues();

        // Check all XMPP addresses are included
        assertTrue(values.contains("xmpp:admin1@" + Fixtures.XMPP_DOMAIN));
        assertTrue(values.contains("xmpp:admin2@" + Fixtures.XMPP_DOMAIN));
        assertTrue(values.contains("xmpp:admin3@other.domain"));
    }

    /**
     * Verifies that the returned data form has the correct structure with exactly
     * two fields: FORM_TYPE and admin-addresses.
     */
    @Test
    public void testFormStructure() {
        // Setup
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify form has correct structure
        assertNotNull(forms);
        assertEquals(1, forms.size());

        DataForm form = forms.iterator().next();
        assertNotNull(form.getField("FORM_TYPE"));
        assertNotNull(form.getField("admin-addresses"));
        assertEquals(2, form.getFields().size()); // FORM_TYPE + admin-addresses
    }
}
