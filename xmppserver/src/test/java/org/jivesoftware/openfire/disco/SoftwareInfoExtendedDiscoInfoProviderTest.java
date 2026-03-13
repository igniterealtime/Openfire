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
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SoftwareInfoExtendedDiscoInfoProvider}.
 *
 * <p>This test class verifies the behavior of the SoftwareInfoExtendedDiscoInfoProvider,
 * which provides software and operating system information in service discovery responses
 * according to XEP-0232: Software Information.</p>
 *
 * <p>The tests cover various scenarios including:
 * <ul>
 *   <li>Inclusion of software/OS information when enabled</li>
 *   <li>Exclusion when disabled via system property</li>
 *   <li>Exclusion when admin exposure is disabled</li>
 *   <li>Proper filtering based on domain, node, and name parameters</li>
 *   <li>Dynamic enable/disable behavior</li>
 *   <li>Correct form structure with all required fields (os, os_version, software, software_version)</li>
 *   <li>SystemProperty configuration validation</li>
 * </ul>
 * </p>
 *
 * @author Dan Caseley, dan@caseley.me.uk
 * @see SoftwareInfoExtendedDiscoInfoProvider
 * @see <a href="https://xmpp.org/extensions/xep-0232.html">XEP-0232: Software Information</a>
 */
class SoftwareInfoExtendedDiscoInfoProviderTest {

    private SoftwareInfoExtendedDiscoInfoProvider provider;
    private XMPPServer xmppServer;

    @BeforeEach
    public void setUp() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();

        // Create provider
        provider = new SoftwareInfoExtendedDiscoInfoProvider();

        // Mock XMPPServer
        xmppServer = Fixtures.mockXMPPServer();

        // Mock XMPPServerInfo with version (required by AdminConsole.getVersionString())
        XMPPServerInfo serverInfo = xmppServer.getServerInfo();
        Version version = mock(Version.class);
        when(version.getVersionString()).thenReturn("5.1.0-TEST");
        when(serverInfo.getVersion()).thenReturn(version);

        XMPPServer.setInstance(xmppServer);
    }

    @AfterEach
    public void tearDown() {
        XMPPServer.setInstance(null);
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that the provider returns a properly formatted data form containing software and
     * operating system information when enabled (the default state).
     *
     * <p>The form should include:
     * <ul>
     *   <li>A hidden FORM_TYPE field with value "urn:xmpp:dataforms:softwareinfo"</li>
     *   <li>An os field containing the operating system name</li>
     *   <li>An os_version field containing OS version, architecture, and Java version</li>
     *   <li>A software field containing the software name</li>
     *   <li>A software_version field containing the software version</li>
     * </ul>
     * </p>
     */
    @Test
    public void testReturnsSoftwareInfoWhenEnabled() {
        // Setup - ENABLED defaults to true
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
        assertEquals("urn:xmpp:dataforms:softwareinfo", formType.getFirstValue());

        // Check os field
        FormField os = form.getField("os");
        assertNotNull(os);
        assertEquals(FormField.Type.text_single, os.getType());
        assertNotNull(os.getFirstValue());
        assertEquals(System.getProperty("os.name"), os.getFirstValue());

        // Check os_version field
        FormField osVersion = form.getField("os_version");
        assertNotNull(osVersion);
        assertEquals(FormField.Type.text_single, osVersion.getType());
        assertNotNull(osVersion.getFirstValue());
        String expectedOsVersion = System.getProperty("os.version") + " " +
            System.getProperty("os.arch") + " - Java " + System.getProperty("java.version");
        assertEquals(expectedOsVersion, osVersion.getFirstValue());

        // Check software field
        FormField software = form.getField("software");
        assertNotNull(software);
        assertEquals(FormField.Type.text_single, software.getType());
        assertNotNull(software.getFirstValue());

        // Check software_version field
        FormField softwareVersion = form.getField("software_version");
        assertNotNull(softwareVersion);
        assertEquals(FormField.Type.text_single, softwareVersion.getType());
        assertNotNull(softwareVersion.getFirstValue());
    }

    /**
     * Verifies that the provider returns an empty set when disabled via the ENABLED
     * system property, allowing administrators to suppress software information disclosure.
     */
    @Test
    public void testReturnsEmptyWhenDisabled() {
        // Setup
        SoftwareInfoExtendedDiscoInfoProvider.ENABLED.setValue(false);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when admin exposure is disabled
     * via the "admin.disable-exposure" system property, respecting the broader privacy setting.
     */
    @Test
    public void testReturnsEmptyWhenAdminExposureDisabled() {
        // Setup
        IQDiscoInfoHandler.DISABLE_EXPOSURE.setValue(true);

        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when querying a specific node,
     * as software information is only provided for the server itself, not for nodes.
     */
    @Test
    public void testReturnsEmptyWhenNodeIsNotNull() {
        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, "somenode", new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when querying a domain that
     * doesn't match the server's domain, as software information is only provided for
     * the local server.
     */
    @Test
    public void testReturnsEmptyWhenDomainDoesNotMatchServerDomain() {
        // Execute
        Set<DataForm> forms = provider.getExtendedInfos("other.domain", null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider returns an empty set when querying a specific user,
     * as software information is only provided for server-level queries, not user-level queries.
     */
    @Test
    public void testReturnsEmptyWhenNameIsNotNull() {
        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, "someuser", null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    /**
     * Verifies that the provider correctly returns software information when querying
     * the server domain itself with no node or name specified.
     */
    @Test
    public void testWorksForServerDomain() {
        // Execute with domain matching server domain
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify
        assertNotNull(forms);
        assertEquals(1, forms.size());
    }

    /**
     * Verifies that the provider dynamically responds to changes in the ENABLED system property.
     *
     * <p>This test confirms that the SystemProperty is configured as dynamic, allowing
     * administrators to enable/disable software information exposure without restarting the server.</p>
     */
    @Test
    public void testRespectsEnabledPropertyChanges() {
        // Initial state - enabled
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));
        assertFalse(forms.isEmpty());

        // Disable
        SoftwareInfoExtendedDiscoInfoProvider.ENABLED.setValue(false);
        forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));
        assertTrue(forms.isEmpty());

        // Re-enable
        SoftwareInfoExtendedDiscoInfoProvider.ENABLED.setValue(true);
        forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));
        assertFalse(forms.isEmpty());
    }

    /**
     * Verifies that the ENABLED SystemProperty is configured with the correct key
     * for consistent configuration management.
     */
    @Test
    public void testEnabledPropertyHasCorrectKey() {
        // Verify the property key is correct
        assertEquals("xmpp.iqdiscoinfo.xformsoftwareversion", SoftwareInfoExtendedDiscoInfoProvider.ENABLED.getKey());
    }

    /**
     * Verifies that the ENABLED SystemProperty defaults to true, enabling software
     * information disclosure by default while allowing administrators to opt out.
     */
    @Test
    public void testEnabledPropertyDefaultsToTrue() {
        // Verify the default value is true
        assertTrue(SoftwareInfoExtendedDiscoInfoProvider.ENABLED.getDefaultValue());
    }

    /**
     * Verifies that the ENABLED SystemProperty is configured as dynamic, allowing
     * runtime changes without server restart.
     */
    @Test
    public void testEnabledPropertyIsDynamic() {
        // Verify the property is dynamic
        assertTrue(SoftwareInfoExtendedDiscoInfoProvider.ENABLED.isDynamic());
    }

    /**
     * Verifies that the returned data form contains all required fields per XEP-0232:
     * FORM_TYPE, os, os_version, software, and software_version.
     */
    @Test
    public void testAllFieldsArePresent() {
        // Execute
        Set<DataForm> forms = provider.getExtendedInfos(Fixtures.XMPP_DOMAIN, null, null, new JID("user@" + Fixtures.XMPP_DOMAIN));

        // Verify all expected fields are present
        assertNotNull(forms);
        assertEquals(1, forms.size());

        DataForm form = forms.iterator().next();
        assertEquals(5, form.getFields().size()); // FORM_TYPE + 4 info fields

        assertNotNull(form.getField("FORM_TYPE"));
        assertNotNull(form.getField("os"));
        assertNotNull(form.getField("os_version"));
        assertNotNull(form.getField("software"));
        assertNotNull(form.getField("software_version"));
    }
}
