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
package org.jivesoftware.openfire.server.s2stest;

import org.xmpp.packet.JID;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Judges the value an administrator entered as the domain to test, before any connection is attempted.
 *
 * Common copy-and-paste mistakes (a URL scheme, surrounding spaces, a trailing slash) are forgiven, and an address with
 * a user part tests its domain, as the page always has. The server's own state is supplied as predicates, so the rules
 * can be tested without a running server.
 *
 * @author Matthew Vivian
 */
public final class DomainValidator
{
    private static final List<String> URL_SCHEMES = List.of("http://", "https://");

    private final Predicate<JID> isLocal;
    private final Predicate<JID> isComponent;
    private final Predicate<String> isAllowedByPolicy;

    /**
     * @param isLocal whether a domain is served by this server.
     * @param isComponent whether a domain is a component of this server.
     * @param isAllowedByPolicy whether the server-to-server policy allows connections to a domain.
     */
    public DomainValidator(final Predicate<JID> isLocal, final Predicate<JID> isComponent, final Predicate<String> isAllowedByPolicy)
    {
        this.isLocal = Objects.requireNonNull(isLocal, "isLocal");
        this.isComponent = Objects.requireNonNull(isComponent, "isComponent");
        this.isAllowedByPolicy = Objects.requireNonNull(isAllowedByPolicy, "isAllowedByPolicy");
    }

    /**
     * @param input the value as entered; may be null.
     * @return the verdict, with the domain to test or the value to display.
     */
    public DomainValidation validate(final String input)
    {
        final String entered = input == null ? "" : input.trim();
        final JID jid = parse(entered);
        if (jid == null) {
            return new DomainValidation(DomainCheck.INVALID, entered);
        }
        final JID domain = new JID(jid.getDomain());
        if (isLocal.test(domain)) {
            return new DomainValidation(DomainCheck.LOCAL, domain.getDomain());
        }
        if (isComponent.test(domain)) {
            return new DomainValidation(DomainCheck.COMPONENT, domain.getDomain());
        }
        if (!isAllowedByPolicy.test(domain.getDomain())) {
            return new DomainValidation(DomainCheck.BLOCKED_BY_POLICY, domain.getDomain());
        }
        return new DomainValidation(DomainCheck.VALID, domain.getDomain());
    }

    private static JID parse(final String entered)
    {
        String value = entered;
        for (final String scheme : URL_SCHEMES) {
            if (value.regionMatches(true, 0, scheme, 0, scheme.length())) {
                value = value.substring(scheme.length());
            }
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new JID(value);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
