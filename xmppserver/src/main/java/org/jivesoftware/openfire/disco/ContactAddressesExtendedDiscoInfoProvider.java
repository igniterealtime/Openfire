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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides XEP-0157 Contact Addresses for XMPP Services via service discovery.
 * <p>
 * This provider returns a data form containing administrative contact addresses for the server,
 * including XMPP addresses for administrators and their email addresses when available.
 * </p>
 * <p>
 * The contact information is only returned when:
 * <ul>
 *   <li>The {@code admin.disable-exposure} property is not set to true</li>
 *   <li>The request is for the server domain (name is null or matches the server domain)</li>
 *   <li>No specific node is requested (node is null)</li>
 *   <li>At least one administrator is configured</li>
 * </ul>
 * </p>
 *
 * @see <a href="https://xmpp.org/extensions/xep-0157.html">XEP-0157: Contact Addresses for XMPP Services</a>
 */
public class ContactAddressesExtendedDiscoInfoProvider implements ExtendedDiscoInfoProvider {

    @Override
    public Set<DataForm> getExtendedInfos(String domain, String name, String node, JID senderJID) {
        // Return empty set if admin exposure is disabled
        if (IQDiscoInfoHandler.DISABLE_EXPOSURE.getValue()) {
            return Collections.emptySet();
        }

        // Only respond for server-level requests (no node)
        if (node != null) {
            return Collections.emptySet();
        }

        // Only respond for server domain (not MUC, PubSub, etc.)
        final String serverDomain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        if (!serverDomain.equals(domain)) {
            return Collections.emptySet();
        }

        // Only respond for service-level queries (name == null)
        if (name != null) {
            return Collections.emptySet();
        }

        // Get admins and return empty if none
        final Collection<JID> admins = XMPPServer.getInstance().getAdmins();
        if (admins == null || admins.isEmpty()) {
            return Collections.emptySet();
        }

        // Build XEP-0157 form
        final DataForm dataForm = new DataForm(DataForm.Type.result);

        final FormField fieldType = dataForm.addField();
        fieldType.setVariable("FORM_TYPE");
        fieldType.setType(FormField.Type.hidden);
        fieldType.addValue("http://jabber.org/network/serverinfo");

        final FormField fieldAdminAddresses = dataForm.addField();
        fieldAdminAddresses.setVariable("admin-addresses");
        fieldAdminAddresses.setType(FormField.Type.list_multi);

        final UserManager userManager = UserManager.getInstance();
        for (final JID admin : admins) {
            fieldAdminAddresses.addValue("xmpp:" + admin.asBareJID());
            if (admin.getDomain().equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                try {
                    final String email = userManager.getUser(admin.getNode()).getEmail();
                    if (email != null && !email.trim().isEmpty()) {
                        fieldAdminAddresses.addValue("mailto:" + email);
                    }
                } catch (Exception e) {
                    // User not found or other error - skip email for this admin
                    continue;
                }
            }
        }

        final Set<DataForm> dataForms = new HashSet<>();
        dataForms.add(dataForm);
        return dataForms;
    }
}
