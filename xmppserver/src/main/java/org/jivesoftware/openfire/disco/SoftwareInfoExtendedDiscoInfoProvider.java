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

import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides XEP-0232 Software Information via service discovery.
 * <p>
 * This provider returns a data form containing information about the server's operating system,
 * software name, and version information. This allows clients to discover what software is running
 * on the server.
 * </p>
 * <p>
 * The software information is only returned when:
 * <ul>
 *   <li>The {@code xmpp.iqdiscoinfo.xformsoftwareversion} property is enabled (default: true)</li>
 *   <li>The {@code admin.disable-exposure} property is not set to true</li>
 *   <li>The request is for the server domain (name is null or matches the server domain)</li>
 *   <li>No specific node is requested (node is null)</li>
 * </ul>
 * </p>
 *
 * @see <a href="https://xmpp.org/extensions/xep-0232.html">XEP-0232: Software Information</a>
 */
public class SoftwareInfoExtendedDiscoInfoProvider implements ExtendedDiscoInfoProvider {

    /**
     * Controls whether XEP-0232 software information is included in disco#info responses.
     */
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.iqdiscoinfo.xformsoftwareversion")
        .setDefaultValue(Boolean.TRUE)
        .setDynamic(Boolean.TRUE)
        .build();

    @Override
    public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
        // Check if feature is enabled
        if (!ENABLED.getValue()) {
            return Collections.emptySet();
        }

        // Return empty set if admin exposure is disabled
        if (JiveGlobals.getBooleanProperty("admin.disable-exposure")) {
            return Collections.emptySet();
        }

        // Only respond for server-level requests (no node)
        if (node != null) {
            return Collections.emptySet();
        }

        // Only respond for server domain or null name
        if (name != null && !name.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
            return Collections.emptySet();
        }

        // Build XEP-0232 form
        final DataForm dataFormSoftwareVersion = new DataForm(DataForm.Type.result);

        final FormField fieldTypeSoftwareVersion = dataFormSoftwareVersion.addField();
        fieldTypeSoftwareVersion.setVariable("FORM_TYPE");
        fieldTypeSoftwareVersion.setType(FormField.Type.hidden);
        fieldTypeSoftwareVersion.addValue("urn:xmpp:dataforms:softwareinfo");

        final FormField fieldOs = dataFormSoftwareVersion.addField();
        fieldOs.setType(FormField.Type.text_single);
        fieldOs.setVariable("os");
        fieldOs.addValue(System.getProperty("os.name"));

        final FormField fieldOsVersion = dataFormSoftwareVersion.addField();
        fieldOsVersion.setType(FormField.Type.text_single);
        fieldOsVersion.setVariable("os_version");
        fieldOsVersion.addValue(System.getProperty("os.version") + " " + System.getProperty("os.arch") + " - Java " + System.getProperty("java.version"));

        final FormField fieldSoftware = dataFormSoftwareVersion.addField();
        fieldSoftware.setType(FormField.Type.text_single);
        fieldSoftware.setVariable("software");
        fieldSoftware.addValue(AdminConsole.getAppName());

        final FormField fieldSoftwareVersion = dataFormSoftwareVersion.addField();
        fieldSoftwareVersion.setType(FormField.Type.text_single);
        fieldSoftwareVersion.setVariable("software_version");
        fieldSoftwareVersion.addValue(AdminConsole.getVersionString());

        final Set<DataForm> dataForms = new HashSet<>();
        dataForms.add(dataFormSoftwareVersion);
        return dataForms;
    }
}
