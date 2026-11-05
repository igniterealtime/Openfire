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
package org.jivesoftware.admin.servlet;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.s2stest.CheckResult;
import org.jivesoftware.openfire.server.s2stest.CheckStatus;
import org.jivesoftware.openfire.server.s2stest.ChecklistSummary;
import org.jivesoftware.openfire.server.s2stest.DomainCheck;
import org.jivesoftware.openfire.server.s2stest.DomainValidation;
import org.jivesoftware.openfire.server.s2stest.DomainValidator;
import org.jivesoftware.openfire.server.s2stest.S2SAdvice;
import org.jivesoftware.openfire.server.s2stest.S2SCheck;
import org.jivesoftware.openfire.server.s2stest.S2SChecklist;
import org.jivesoftware.openfire.server.s2stest.S2SChecklistBuilder;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.S2STestService;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The admin console's server-to-server connection test: validates the domain, runs the test, and builds the checklist
 * that the page renders.
 *
 * Every text the page shows is localised here, so the page only has to render (and escape) plain strings.
 *
 * @author Matthew Vivian
 */
@WebServlet(value = "/server-connectiontest.jsp")
public class S2SConnectionTestServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger(S2SConnectionTestServlet.class);

    /**
     * The page that renders what this servlet prepares.
     */
    static final String VIEW = "server-connectiontest-view.jsp";

    private static final String CSRF = "csrf";
    private static final String DOMAIN_PARAMETER = "server2server-testing-domain";
    private static final String KEY_PREFIX = "server2server.settings.testing.";

    /**
     * Runs a test against a domain. Replaceable so that the servlet can be tested without a running server.
     */
    @FunctionalInterface
    interface TestRunner
    {
        S2STestService.Result run(JID domain) throws Exception;
    }

    private final transient DomainValidator validator;
    private final transient TestRunner runner;

    public S2SConnectionTestServlet()
    {
        this(new DomainValidator(
                jid -> XMPPServer.getInstance().isLocal(jid),
                jid -> XMPPServer.getInstance().matchesComponent(jid),
                RemoteServerManager::canAccess),
            domain -> new S2STestService(domain).run());
    }

    S2SConnectionTestServlet(final DomainValidator validator, final TestRunner runner)
    {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    /**
     * Shows the form. A domain in the address (as the failed-session details page passes for "Retry now") fills the
     * form; the test itself runs only on a submitted form, which carries the CSRF token.
     */
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        render(request, response, ParamUtils.getParameter(request, DOMAIN_PARAMETER));
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final String entered = ParamUtils.getParameter(request, DOMAIN_PARAMETER);
        final Cookie csrfCookie = CookieUtils.getCookie(request, CSRF);
        if (csrfCookie == null || !csrfCookie.getValue().equals(request.getParameter(CSRF))) {
            request.setAttribute("errorMessage", LocaleUtils.getLocalizedString("global.csrf.failed"));
            render(request, response, entered);
            return;
        }
        if (entered == null || entered.isBlank()) {
            render(request, response, "");
            return;
        }

        final DomainValidation validation = validator.validate(entered);
        final S2SChecklistBuilder builder = new S2SChecklistBuilder(validation.domain(), validation.check(), LocaleUtils::getLocalizedString);
        if (validation.check() == DomainCheck.VALID) {
            final S2STestService.Result result;
            try {
                result = runner.run(new JID(validation.domain()));
            } catch (final Exception e) {
                if (e instanceof InterruptedException) {
                    // Passed on, so that whatever interrupted the request thread (a shutdown) still sees it.
                    Thread.currentThread().interrupt();
                }
                Log.warn("Unable to run the server-to-server test for '{}'.", validation.domain(), e);
                request.setAttribute("errorMessage", LocaleUtils.getLocalizedString(KEY_PREFIX + "error"));
                render(request, response, entered);
                return;
            }
            builder.milestones(result.milestones())
                .sessionRegistered(result.sessionRegistered())
                .ping(result.ping())
                .log(result.log());
            request.setAttribute("stanzas", result.stanzas());
            request.setAttribute("certs", result.certificates());
            request.setAttribute("logs", result.logs());
        }

        final S2SChecklist checklist = builder.build();
        setChecklist(request, validation.domain(), checklist);
        render(request, response, entered);
    }

    private void render(final HttpServletRequest request, final HttpServletResponse response, final String entered) throws ServletException, IOException
    {
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, CSRF, csrf, -1);
        request.setAttribute(CSRF, csrf);
        request.setAttribute("s2sDomain", entered == null ? "" : entered);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    private static void setChecklist(final HttpServletRequest request, final String domain, final S2SChecklist checklist)
    {
        final ChecklistSummary summary = ChecklistSummary.of(checklist);
        final List<Object> summaryArguments = new ArrayList<>();
        summaryArguments.add(domain);
        final String summaryType;
        switch (summary.kind()) {
            case FAILED -> {
                summaryArguments.add(summary.failedCheck().ordinal() + 1);
                summaryType = "error";
            }
            case WARNINGS -> {
                summaryArguments.add(summary.warnings());
                summaryType = "warning";
            }
            case NOT_OBSERVED -> summaryType = "info";
            default -> summaryType = "success";
        }

        final S2SCheck firstFailure = checklist.getFirstFailure().map(CheckResult::check).orElse(null);
        final List<Row> rows = new ArrayList<>();
        for (final CheckResult result : checklist.getResults()) {
            rows.add(new Row(
                result.check().ordinal() + 1,
                LocaleUtils.getLocalizedString(result.check().titleKey()),
                result.status(),
                LocaleUtils.getLocalizedString(result.status().labelKey()),
                LocaleUtils.getLocalizedString(result.messageKey(), result.parameters()),
                result.check() == firstFailure));
        }

        request.setAttribute("tested", true);
        request.setAttribute("summary", LocaleUtils.getLocalizedString(summary.kind().messageKey(), summaryArguments));
        request.setAttribute("summaryType", summaryType);
        request.setAttribute("resultsHeading", LocaleUtils.getLocalizedString(KEY_PREFIX + "results", List.of(domain)));
        request.setAttribute("rows", rows);
        request.setAttribute("advice", advice(checklist));
    }

    private static List<AdviceItem> advice(final S2SChecklist checklist)
    {
        final List<AdviceItem> items = new ArrayList<>();
        for (final S2SAdvice.Suggestion suggestion : S2SAdvice.forChecklist(checklist)) {
            final S2SAdvice.Advice advice = suggestion.advice();
            // Advice for one exact result message can fill in that result's values, such as the names in a certificate.
            final List<String> values = suggestion.result().parameters();
            final List<S2SAdvice.AdminPage> pages = new ArrayList<>(advice.pages());
            // A DNS override that was used decided where this server connected, so it is named, with its page.
            final String dnsOverride = suggestion.dnsOverride()
                .map(dns -> LocaleUtils.getLocalizedString(KEY_PREFIX + "advice.dns_override", dns.parameters()))
                .orElse(null);
            if (dnsOverride != null && !pages.contains(S2SAdvice.AdminPage.DNS_OVERRIDES)) {
                pages.add(0, S2SAdvice.AdminPage.DNS_OVERRIDES);
            }
            final List<Link> links = new ArrayList<>();
            for (final S2SAdvice.AdminPage page : pages) {
                links.add(new Link(page.url(), LocaleUtils.getLocalizedString(page.labelKey())));
            }
            final S2SCheck check = suggestion.result().check();
            items.add(new AdviceItem(
                LocaleUtils.getLocalizedString(KEY_PREFIX + "advice.check", List.of(check.ordinal() + 1, LocaleUtils.getLocalizedString(check.titleKey()))),
                dnsOverride,
                advice.localKey().map(key -> LocaleUtils.getLocalizedString(key, values)).orElse(null),
                links,
                advice.remoteKey().map(key -> LocaleUtils.getLocalizedString(key, values)).orElse(null),
                advice.noteKey().map(LocaleUtils::getLocalizedString).orElse(null)));
        }
        return items;
    }

    /**
     * What the administrator can do next about one check, with every text already localised. Plain text only: the page
     * escapes it.
     */
    public static final class AdviceItem
    {
        private final String heading;
        private final String dnsOverride;
        private final String local;
        private final List<Link> links;
        private final String remote;
        private final String note;

        AdviceItem(final String heading, final String dnsOverride, final String local, final List<Link> links, final String remote, final String note)
        {
            this.heading = heading;
            this.dnsOverride = dnsOverride;
            this.local = local;
            this.links = List.copyOf(links);
            this.remote = remote;
            this.note = note;
        }

        public String getHeading()
        {
            return heading;
        }

        /**
         * @return where a DNS override on this server sent the connection, when one was used and a later step failed;
         *         otherwise null.
         */
        public String getDnsOverride()
        {
            return dnsOverride;
        }

        /**
         * @return what to look at on this server, or null when nothing on this server can change the outcome.
         */
        public String getLocal()
        {
            return local;
        }

        /**
         * @return a note shared with other advice, such as the server-wide certificate settings, or null.
         */
        public String getNote()
        {
            return note;
        }

        public List<Link> getLinks()
        {
            return links;
        }

        /**
         * @return what could be wrong on the remote server, or null when the advice does not say.
         */
        public String getRemote()
        {
            return remote;
        }
    }

    /**
     * A link to an admin console page. The address is one of the fixed pages in {@link S2SAdvice.AdminPage}.
     */
    public static final class Link
    {
        private final String url;
        private final String label;

        Link(final String url, final String label)
        {
            this.url = url;
            this.label = label;
        }

        public String getUrl()
        {
            return url;
        }

        public String getLabel()
        {
            return label;
        }
    }

    /**
     * One row of the checklist, with every text already localised. Plain text only: the page escapes it.
     */
    public static final class Row
    {
        private final int number;
        private final String title;
        private final CheckStatus status;
        private final String statusLabel;
        private final String detail;
        private final boolean firstFailure;

        Row(final int number, final String title, final CheckStatus status, final String statusLabel, final String detail, final boolean firstFailure)
        {
            this.number = number;
            this.title = title;
            this.status = status;
            this.statusLabel = statusLabel;
            this.detail = detail;
            this.firstFailure = firstFailure;
        }

        public int getNumber()
        {
            return number;
        }

        public String getTitle()
        {
            return title;
        }

        public CheckStatus getStatus()
        {
            return status;
        }

        /**
         * @return the name of the admin console's status icon for this row, or empty for a step that did not take
         *         place (no icon exists for those, and the label says it).
         */
        public String getIcon()
        {
            return switch (status) {
                case PASS -> "success-16x16.gif";
                case WARNING -> "warning-16x16.gif";
                case FAIL -> "error-16x16.gif";
                default -> "";
            };
        }

        public String getStatusLabel()
        {
            return statusLabel;
        }

        public String getDetail()
        {
            return detail;
        }

        public boolean isFirstFailure()
        {
            return firstFailure;
        }

        @Override
        public String toString()
        {
            return number + " " + status + ": " + detail;
        }
    }
}
