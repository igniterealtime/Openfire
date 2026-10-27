/*
 * Copyright (C) 2022-2026 Ignite Realtime Foundation. All rights reserved.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration of Openfire's 'gateway' or 'trunking' functionality, which allows Openfire to act as a gateway to
 * transfer data between other XMPP domains.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://download.igniterealtime.org/openfire/docs/latest/documentation/trunking-guide.html">Openfire documentation: Trunking Guide</a>
 */
public class Trunking
{
    private static final Logger Log = LoggerFactory.getLogger(Trunking.class);

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
        if (!ENABLED.getValue()) {
            return false;
        }
        return normalisedDomains().contains(JID.domainprep(domain));
    }

    /**
     * The configured trunking domains, and their normalised form. Recomputed only when the configuration changes.
     */
    private record NormalisedDomains(List<String> configured, Set<String> normalised) {}

    private static volatile NormalisedDomains normalisedDomains = new NormalisedDomains(List.of(), Set.of());

    /**
     * Returns the normalised form of the domains that are configured for trunking. A configured value that is not a
     * valid domain name is ignored, so that it does not prevent the other configured domains from being recognised.
     * The configuration is normalised again only when it changes, so that an invalid value is reported once per change
     * rather than on every check.
     *
     * @return the normalised domains.
     */
    private static Set<String> normalisedDomains()
    {
        final List<String> configured = ALLOWABLE_DOMAINS.getValue();
        NormalisedDomains current = normalisedDomains;
        if (!current.configured().equals(configured)) {
            final Set<String> normalised = new HashSet<>();
            for (final String value : configured) {
                try {
                    normalised.add(JID.domainprep(value));
                } catch (final IllegalArgumentException e) {
                    Log.warn("Ignoring a value of {} that is not a valid domain name: '{}'", ALLOWABLE_DOMAINS.getKey(), value);
                }
            }
            current = new NormalisedDomains(List.copyOf(configured), Set.copyOf(normalised));
            normalisedDomains = current;
        }
        return current.normalised();
    }
}

