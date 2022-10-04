/*
 * Copyright (C) 2022 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.util.SystemProperty;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Configuration of Openfire's 'gateway' or 'trunking' functionality, which allows Openfire to act as a gateway to
 * transfer data between other XMPP domains.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://download.igniterealtime.org/openfire/docs/latest/documentation/trunking-guide.html">Openfire documentation: Trunking Guide</a>
 */
public class Trunking
{
    /**
     * Enables or disables the trunking functionality that allows Openfire to act as a gateway to transfer data between
     * other XMPP domains.
     */
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.gateway.enabled")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * A collection of XMPP domain names for which Openfire will provide trunking functionality. For each domain in this
     * collection, Openfire will accept data from other domains, and forward that data to that domain.
     */
    public static final SystemProperty<List<String>> ALLOWABLE_DOMAINS = SystemProperty.Builder.ofType(List.class)
        .setKey("xmpp.gateway.domains")
        .setDynamic(true)
        .setDefaultValue(Collections.emptyList())
        .buildList(String.class);

    /**
     * Verifies if trunking functionality is enabled, and if Openfire is configured to accept data to-be-trunked to a
     * particular domain.
     *
     * @param domain The domain for which Openfire is to accept data
     * @return true if Openfire is configured to trunk data for the provided domain.
     */
    public static boolean isTrunkingEnabledFor(@Nonnull final String domain)
    {
        return ENABLED.getValue() && ALLOWABLE_DOMAINS.getValue().stream().anyMatch(v -> JID.domainprep(v).equals(JID.domainprep(domain)));
    }
}

