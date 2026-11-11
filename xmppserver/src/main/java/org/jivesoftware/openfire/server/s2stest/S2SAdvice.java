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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the administrator of this server can do next about a failed or warning check: what to look at on this server,
 * with the admin console pages that hold the settings, and, where it helps, what could be wrong on the remote server.
 *
 * The test sees only this server's side of the connection, so the remote causes are possibilities for the administrator
 * to weigh, never a verdict on the remote server.
 *
 * Advice is chosen by the message key of a check's result: the advice for that exact message when there is one,
 * otherwise the advice for the check and status (for example {@code certificate.fail}). Advice for one exact message can
 * fill in the values that its result carries, such as the names in a certificate.
 *
 * @author Matthew Vivian
 */
public final class S2SAdvice
{
    static final String KEY_PREFIX = "server2server.settings.testing.advice.";

    /**
     * The note on every certificate failure but revocation: settings can let such a connection go ahead, using Server
     * Dialback, but they apply to every remote server. A revoked certificate gets no such note, as its issuer withdrew it.
     */
    private static final String CERTIFICATE_NOTE = "certificate.note";
    private static final String REVOKED = "certificate.fail.revoked";

    /**
     * An admin console page that holds settings the advice refers to. The addresses are fixed and relative to the admin
     * console, so a page can link to them without escaping concerns.
     */
    public enum AdminPage
    {
        S2S_SETTINGS("connection-settings-socket-s2s.jsp", "s2s_settings"),
        S2S_TRUST_STORE("security-truststore.jsp?connectionType=SOCKET_S2S", "s2s_trust_store"),
        S2S_IDENTITY_STORE("security-keystore.jsp?connectionType=SOCKET_S2S", "s2s_identity_store"),
        DNS_OVERRIDES("dns-overrides.jsp", "dns_overrides"),
        SYSTEM_PROPERTIES("server-properties.jsp", "system_properties"),
        LOGS("logviewer.jsp", "logs");

        private final String url;
        private final String keySegment;

        AdminPage(final String url, final String keySegment)
        {
            this.url = url;
            this.keySegment = keySegment;
        }

        /**
         * @return the page's address, relative to the admin console.
         */
        public String url()
        {
            return url;
        }

        /**
         * @return the i18n key of the page's name, as the advice shows it.
         */
        public String labelKey()
        {
            return KEY_PREFIX + "page." + keySegment;
        }
    }

    /**
     * Which parts the advice has.
     */
    private enum Parts
    {
        /** Only what to look at on this server. */
        LOCAL,
        /** Only what could be wrong on the remote server, as nothing on this server can change the outcome. */
        REMOTE,
        /** Both. */
        LOCAL_AND_REMOTE
    }

    /**
     * The advice for one result.
     *
     * @param id the part of the result's message key after the checklist prefix that this advice answers, for example
     *           {@code certificate.fail.expired} or {@code certificate.fail}.
     * @param check the check the advice is about.
     * @param pages the admin console pages that hold the settings the advice names, in the order it names them.
     * @param local whether the advice says what to look at on this server.
     * @param remoteCauses whether the advice says what could be wrong on the remote server.
     * @param note the id of a note shared by several pieces of advice, or null.
     */
    public record Advice(String id, S2SCheck check, List<AdminPage> pages, boolean local, boolean remoteCauses, String note)
    {
        public Advice
        {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(check, "check");
            pages = List.copyOf(pages);
        }

        /**
         * @return the i18n key of what the administrator can look at on this server, when the advice says.
         */
        public Optional<String> localKey()
        {
            return local ? Optional.of(KEY_PREFIX + id + ".local") : Optional.empty();
        }

        /**
         * @return the i18n key of what could be wrong on the remote server, when the advice says.
         */
        public Optional<String> remoteKey()
        {
            return remoteCauses ? Optional.of(KEY_PREFIX + id + ".remote") : Optional.empty();
        }

        /**
         * @return the i18n key of the note shared with other advice, when there is one.
         */
        public Optional<String> noteKey()
        {
            return Optional.ofNullable(note).map(id -> KEY_PREFIX + id);
        }
    }

    /**
     * The advice chosen for one check of a test, with the result it answers, whose values the advice can fill in.
     *
     * @param advice the advice.
     * @param result the result of the check that the advice is about.
     * @param dnsOverride the DNS result, when a DNS override on this server decided where this server connected and a
     *                    later step of the connection failed; otherwise empty.
     */
    public record Suggestion(Advice advice, CheckResult result, Optional<CheckResult> dnsOverride)
    {
        public Suggestion
        {
            Objects.requireNonNull(advice, "advice");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(dnsOverride, "dnsOverride");
        }
    }

    /**
     * The checks whose failure a DNS override can explain, as it decides which host and port this server connects to.
     */
    private static final Set<S2SCheck> AFTER_DNS = EnumSet.of(S2SCheck.TCP, S2SCheck.STREAM, S2SCheck.TLS);

    private static final Map<String, Advice> ADVICE = new LinkedHashMap<>();

    static {
        add(S2SCheck.DOMAIN, "fail.invalid", Parts.LOCAL);
        add(S2SCheck.DOMAIN, "fail.local", Parts.LOCAL);
        add(S2SCheck.DOMAIN, "fail.component", Parts.LOCAL);
        add(S2SCheck.DOMAIN, "fail.blocked", Parts.LOCAL, AdminPage.S2S_SETTINGS);

        // A DNS override is a legitimate way round a domain that publishes no records. For later steps, the advice
        // mentions an override only when one was used (see Suggestion.dnsOverride).
        add(S2SCheck.DNS, "fail", Parts.LOCAL_AND_REMOTE, AdminPage.DNS_OVERRIDES);

        add(S2SCheck.TCP, "warning", Parts.LOCAL_AND_REMOTE, AdminPage.DNS_OVERRIDES);
        add(S2SCheck.TCP, "fail", Parts.LOCAL_AND_REMOTE);
        add(S2SCheck.TCP, "fail.refused", Parts.LOCAL_AND_REMOTE);
        add(S2SCheck.TCP, "fail.timed_out", Parts.LOCAL_AND_REMOTE, AdminPage.SYSTEM_PROPERTIES);
        add(S2SCheck.TCP, "fail.none_attempted", Parts.LOCAL, AdminPage.S2S_SETTINGS);

        add(S2SCheck.STREAM, "fail", Parts.REMOTE);
        add(S2SCheck.STREAM, "fail.timed_out", Parts.LOCAL_AND_REMOTE, AdminPage.SYSTEM_PROPERTIES);

        add(S2SCheck.TLS, "warning.unencrypted", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);
        // A certificate rejected during the handshake gets the advice of the certificate check, which says why.
        add(S2SCheck.TLS, "fail", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_IDENTITY_STORE, AdminPage.S2S_SETTINGS);
        add(S2SCheck.TLS, "fail.refused", Parts.REMOTE);
        add(S2SCheck.TLS, "fail.timed_out", Parts.LOCAL_AND_REMOTE, AdminPage.SYSTEM_PROPERTIES);
        add(S2SCheck.TLS, "fail.stalled", Parts.LOCAL_AND_REMOTE, AdminPage.SYSTEM_PROPERTIES);
        add(S2SCheck.TLS, "fail.not_offered", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);
        add(S2SCheck.TLS, "fail.disabled", Parts.LOCAL, AdminPage.S2S_SETTINGS, AdminPage.S2S_IDENTITY_STORE);
        add(S2SCheck.TLS, "fail.not_tls", Parts.REMOTE);

        // Every certificate failure but revocation carries the note on the settings that let such a connection go ahead
        // anyway. Its issuer revoked the certificate for a reason, so that advice offers no way around it, and no page.
        add(S2SCheck.CERTIFICATE, "fail", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_TRUST_STORE, AdminPage.S2S_SETTINGS);
        add(S2SCheck.CERTIFICATE, "fail.revoked", Parts.REMOTE);
        add(S2SCheck.CERTIFICATE, "fail.expired", Parts.REMOTE, AdminPage.S2S_SETTINGS);
        add(S2SCheck.CERTIFICATE, "fail.not_yet_valid", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);
        add(S2SCheck.CERTIFICATE, "fail.name_mismatch", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);

        add(S2SCheck.AUTHENTICATION, "warning", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_IDENTITY_STORE);
        add(S2SCheck.AUTHENTICATION, "fail", Parts.LOCAL_AND_REMOTE, AdminPage.SYSTEM_PROPERTIES, AdminPage.S2S_IDENTITY_STORE);
        add(S2SCheck.AUTHENTICATION, "fail.external_refused_dialback_disabled", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_IDENTITY_STORE, AdminPage.SYSTEM_PROPERTIES);
        add(S2SCheck.AUTHENTICATION, "fail.dialback_refused", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);
        // No advice for fail.dialback_fallback: it follows a failed TLS handshake, so check 5 is always the first failure.
        add(S2SCheck.AUTHENTICATION, "fail.timed_out", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);
        // The remote accepted the certificate of this server for these, so the default advice (on certificates and
        // Server Dialback) does not apply.
        add(S2SCheck.AUTHENTICATION, "fail.closed_after_external", Parts.LOCAL_AND_REMOTE, AdminPage.LOGS);
        add(S2SCheck.AUTHENTICATION, "fail.reused_stream_id", Parts.LOCAL_AND_REMOTE, AdminPage.SYSTEM_PROPERTIES);
        add(S2SCheck.AUTHENTICATION, "fail.restarted_stream_no_id", Parts.REMOTE);

        add(S2SCheck.SESSION, "fail", Parts.LOCAL, AdminPage.LOGS);

        add(S2SCheck.PING, "fail", Parts.LOCAL_AND_REMOTE, AdminPage.S2S_SETTINGS);
        add(S2SCheck.PING, "fail.local_error", Parts.LOCAL, AdminPage.LOGS);

        add(S2SCheck.RESPONSE, "warning", Parts.LOCAL_AND_REMOTE);

        add(S2SCheck.LOG, "warning", Parts.LOCAL, AdminPage.LOGS);
        add(S2SCheck.LOG, "fail", Parts.LOCAL, AdminPage.LOGS);
    }

    private static void add(final S2SCheck check, final String suffix, final Parts parts, final AdminPage... pages)
    {
        final String id = check.messageKey(suffix).substring(S2SCheck.KEY_PREFIX.length());
        final boolean local = parts != Parts.REMOTE;
        final boolean remote = parts != Parts.LOCAL;
        final String note = check == S2SCheck.CERTIFICATE && !id.equals(REVOKED) ? CERTIFICATE_NOTE : null;
        ADVICE.put(id, new Advice(id, check, List.of(pages), local, remote, note));
    }

    private S2SAdvice()
    {
    }

    /**
     * @return every piece of advice, so that its texts can be checked.
     */
    static Collection<Advice> all()
    {
        return ADVICE.values();
    }

    /**
     * @param result the result of a check.
     * @return the advice for the result's message, or for its check and status; empty for a result that needs none.
     */
    public static Optional<Advice> forResult(final CheckResult result)
    {
        if (result.status() != CheckStatus.FAIL && result.status() != CheckStatus.WARNING) {
            return Optional.empty();
        }
        String id = result.messageKey().startsWith(S2SCheck.KEY_PREFIX) ? result.messageKey().substring(S2SCheck.KEY_PREFIX.length()) : "";
        // Stop before the check's own segment: advice is always for a status, at least.
        while (id.indexOf('.') > 0) {
            final Advice advice = ADVICE.get(id);
            if (advice != null) {
                return Optional.of(advice);
            }
            id = id.substring(0, id.lastIndexOf('.'));
        }
        return Optional.empty();
    }

    /**
     * Chooses the advice to show for a test: for the first failed check, where the administrator should start, or, when
     * no check failed, for each check that warned. When the TLS handshake failed because this server rejected the remote
     * certificate, the advice is that of the certificate check, which says why.
     *
     * @param checklist the result of the test.
     * @return the suggestions, in the order of the checks; empty when every check passed.
     */
    public static List<Suggestion> forChecklist(final S2SChecklist checklist)
    {
        final Optional<CheckResult> firstFailure = checklist.getFirstFailure();
        if (firstFailure.isPresent()) {
            CheckResult answered = firstFailure.get();
            final CheckResult certificate = checklist.get(S2SCheck.CERTIFICATE);
            if (answered.messageKey().equals(S2SCheck.TLS.messageKey("fail.certificate")) && certificate.status() == CheckStatus.FAIL) {
                answered = certificate;
            }
            final CheckResult result = answered;
            final Optional<CheckResult> dnsOverride = AFTER_DNS.contains(firstFailure.get().check()) ? dnsOverrideUsed(checklist) : Optional.empty();
            return forResult(result).map(advice -> List.of(new Suggestion(advice, result, dnsOverride))).orElse(List.of());
        }
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final CheckResult result : checklist.getResults()) {
            if (result.status() == CheckStatus.WARNING) {
                forResult(result).ifPresent(advice -> suggestions.add(new Suggestion(advice, result, Optional.empty())));
            }
        }
        return suggestions;
    }

    /**
     * @return the DNS result when it says that a DNS override on this server was used; otherwise empty.
     */
    private static Optional<CheckResult> dnsOverrideUsed(final S2SChecklist checklist)
    {
        final CheckResult dns = checklist.get(S2SCheck.DNS);
        return dns.messageKey().equals(S2SCheck.DNS.messageKey("pass.override")) ? Optional.of(dns) : Optional.empty();
    }
}
