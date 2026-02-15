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

    @Test
    public void testReturnsEmptyWhenAdminExposureDisabled() {
        // Setup
        JiveGlobals.setProperty("admin.disable-exposure", "true");
        Set<JID> admins = new HashSet<>();
        admins.add(new JID("admin@" + Fixtures.XMPP_DOMAIN));
        when(xmppServer.getAdmins()).thenReturn(admins);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

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
