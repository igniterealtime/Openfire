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
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.pubsub.PubSubModule;
import org.jivesoftware.openfire.pubsub.PubSubSubscriptionMaintenance;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.WebManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servlet for cleaning up redundant rows in the {@code ofPubsubSubscription} table.
 *
 * Some installations have accumulated very large numbers of redundant subscription rows (rows that share the same node,
 * subscription JID, owner and subscription type, differing only by their generated subscription ID). On services that
 * do not permit multiple subscriptions for the same subscription JID - most notably PEP services - such rows carry no
 * functional value and, in extreme cases, exhaust the Java heap when loaded into memory (OF-3306).
 *
 * This servlet provides an admin-console UI to analyze the extent of the redundancy and to launch a cleanup. The
 * cleanup runs on a background thread (it may delete millions of rows over several minutes); the page polls a small
 * JSON progress endpoint to render a live progress bar.
 *
 * The structure deliberately follows {@link BlowfishMigrationServlet}: a servlet bound to one URL forwards to a
 * separate view JSP (a different filename, to avoid a forward loop), CSRF is carried in a cookie and validated on POST,
 * a destructive action is gated behind explicit backup confirmation, the operation is blocked in unsafe clustering
 * states, and POST-Redirect-GET is used with messages parked in the session.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3306">OF-3306</a>
 */
@WebServlet(value = "/pubsub-subscription-maintenance.jsp")
public class PubSubSubscriptionMaintenanceServlet extends HttpServlet {

    /**
     * Logger for this class.
     */
    private static final Logger Log = LoggerFactory.getLogger(PubSubSubscriptionMaintenanceServlet.class);

    /**
     * Request parameter naming the action to perform.
     */
    private static final String PARAM_ACTION = "action";

    /**
     * Request parameter for the database-backup confirmation checkbox.
     */
    private static final String PARAM_DB_BACKUP = "dbBackup";

    /**
     * Action value: start a cleanup.
     */
    private static final String ACTION_CLEANUP = "cleanup";

    /**
     * Action value: return the current progress as JSON (polled by the page).
     */
    private static final String ACTION_PROGRESS = "progress";

    /**
     * The maintenance instance, held statically so that a running cleanup and its progress survive page reloads for the
     * lifetime of the JVM. A one-shot maintenance job needs no longer lifetime than this.
     *
     * Guarded by {@link #maintenanceLock} for creation; the instance's own progress state is thread-safe.
     */
    private static volatile PubSubSubscriptionMaintenance maintenance;

    /**
     * Lock guarding lazy creation of {@link #maintenance}.
     */
    private static final Object maintenanceLock = new Object();

    /**
     * Cached answer to "is a cleanup worth recommending?", read by the index page. Updated only by a background
     * refresh (never on the calling thread), so reading it is always O(1) and never touches the database.
     */
    private static final AtomicBoolean cleanupAdvisableCache = new AtomicBoolean(false);

    /**
     * Epoch-millis timestamp of the last completed background refresh of {@link #cleanupAdvisableCache}, or 0 if it has
     * never run. Used to decide when the cache is stale.
     */
    private static final AtomicLong cleanupAdvisableCheckedAt = new AtomicLong(0L);

    /**
     * Guards against launching more than one background refresh at a time.
     */
    private static final AtomicBoolean cleanupAdvisableRefreshing = new AtomicBoolean(false);

    /**
     * How long a cached advisability result is considered fresh. After this, the next read triggers a background
     * refresh (but still returns the cached value immediately).
     */
    private static final long CLEANUP_ADVISABLE_TTL_MILLIS = Duration.ofHours(1).toMillis();

    /**
     * Returns whether a cleanup is worth recommending, for display as an advisory on the admin index page.
     *
     * This is deliberately non-blocking and never queries the database on the calling thread: it returns the most
     * recently cached result immediately. If that result is missing or stale, it schedules a one-off background refresh
     * (a full {@link PubSubSubscriptionMaintenance#analyze()} can take many seconds on a very large table, which must
     * never delay rendering of the index page). Consequently the first index view after startup returns {@code false}
     * (no alert) and the alert may only appear on a later view, once the background check has completed - the same
     * best-effort, page-load-friendly approach the index page uses for its DNS warning.
     *
     * @return the cached advisability flag; {@code false} until the first background check has completed.
     */
    public static boolean isCleanupAdvisable() {
        final PubSubSubscriptionMaintenance m = maintenance;
        // While a cleanup is actively running, the row count is changing under us. Do not analyze (it would compete
        // with the delete and could report a misleading interim value); just return the current cached value. Once the
        // run finishes, the cache is marked stale below so the next read re-evaluates promptly.
        if (m != null && m.getProgress().getPhase() == PubSubSubscriptionMaintenance.Phase.RUNNING) {
            cleanupAdvisableCheckedAt.set(0L); // ensure a refresh happens right after the run completes
            return cleanupAdvisableCache.get();
        }

        final long checkedAt = cleanupAdvisableCheckedAt.get();
        final boolean stale = checkedAt == 0L || (System.currentTimeMillis() - checkedAt) > CLEANUP_ADVISABLE_TTL_MILLIS;
        if (stale && cleanupAdvisableRefreshing.compareAndSet(false, true)) {
            final Thread refresh = new Thread(() -> {
                try {
                    final PubSubSubscriptionMaintenance.Analysis analysis = getMaintenance().analyze();
                    cleanupAdvisableCache.set(analysis.isCleanupRecommended());
                } catch (Exception e) {
                    // On failure, leave the previous cached value in place and log; the next read will retry.
                    Log.debug("Background check for redundant pubsub subscription rows failed; will retry later.", e);
                } finally {
                    cleanupAdvisableCheckedAt.set(System.currentTimeMillis());
                    cleanupAdvisableRefreshing.set(false);
                }
            }, "pubsub-subscription-cleanup-advisability-check");
            refresh.setDaemon(true);
            refresh.start();
        }
        return cleanupAdvisableCache.get();
    }

    /**
     * Returns the shared maintenance instance, creating it on first use with the current set of multiple-subscription
     * services excluded.
     *
     * The exclusion set is computed here, in trusted server-side code that can inspect the live services, rather than
     * in the maintenance class (which only sees the database). See {@link #collectMultipleSubscriptionServiceIds()}.
     *
     * @return the shared maintenance instance.
     */
    private static PubSubSubscriptionMaintenance getMaintenance() {
        PubSubSubscriptionMaintenance result = maintenance;
        if (result == null) {
            synchronized (maintenanceLock) {
                result = maintenance;
                if (result == null) {
                    result = new PubSubSubscriptionMaintenance(collectMultipleSubscriptionServiceIds());
                    maintenance = result;
                }
            }
        }
        return result;
    }

    /**
     * Determines the IDs of pubsub services that permit multiple subscriptions for the same subscription JID, and whose
     * rows must therefore never be treated as redundant by the cleanup.
     *
     * Whether multiple subscriptions are permitted is a service-wide setting. PEP services always return false, so they
     * are never excluded (and are the dominant source of the redundancy this cleanup targets). The main pubsub service
     * is governed by the {@code xmpp.pubsub.multiple-subscriptions} property; when it permits multiple subscriptions,
     * its service ID is excluded.
     *
     * Note: this intentionally errs toward excluding (protecting) a service when in doubt. If additional pubsub
     * services that permit multiple subscriptions are introduced, add their IDs here so their data is protected.
     *
     * @return the set of service IDs to exclude from cleanup; never null, possibly empty.
     */
    private static Set<String> collectMultipleSubscriptionServiceIds() {
        final Set<String> excluded = new LinkedHashSet<>();
        try {
            final PubSubModule pubSubModule = XMPPServer.getInstance().getPubSubModule();
            if (pubSubModule != null && pubSubModule.isMultipleSubscriptionsEnabled()) {
                excluded.add(pubSubModule.getServiceID());
            }
        } catch (Exception e) {
            // The main pubsub service is a singleton that is normally available whenever the admin console is running,
            // so this is not expected. If it cannot be inspected, its ID is not added, which means its rows would be
            // treated as eligible for cleanup; log loudly so an operator can verify before proceeding.
            Log.warn("Unable to determine whether the main pubsub service permits multiple subscriptions; its rows " +
                "will be treated as eligible for cleanup. Verify this is correct for this deployment before proceeding.", e);
        }
        return excluded;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // The progress endpoint returns JSON for the page's polling; it is not a normal page view.
        if (ACTION_PROGRESS.equals(request.getParameter(PARAM_ACTION))) {
            writeProgressJson(response);
            return;
        }

        final PubSubSubscriptionMaintenance m = getMaintenance();

        // Read-only analysis for the status table. This can take a few seconds on a very large table, but does not
        // modify data. Failures are surfaced to the page rather than thrown.
        try {
            final PubSubSubscriptionMaintenance.Analysis analysis = m.analyze();
            request.setAttribute("totalRows", analysis.getTotalRows());
            request.setAttribute("distinctSubscriptions", analysis.getDistinctSubscriptions());
            request.setAttribute("removableRows", analysis.getRemovableRows());
            request.setAttribute("cleanupRecommended", analysis.isCleanupRecommended());
            request.setAttribute("redundancyPercent", Math.round(analysis.getRedundancyRatio() * 100));
            request.setAttribute("analysisAvailable", true);
        } catch (Exception e) {
            Log.error("Unable to analyze redundant pubsub subscription rows.", e);
            request.setAttribute("analysisAvailable", false);
            request.setAttribute("analysisError", e.getMessage());
        }

        // Current run state, so the page can show a progress bar if a cleanup is already running (e.g. after a reload).
        final PubSubSubscriptionMaintenance.Progress progress = m.getProgress();
        request.setAttribute("progressPhase", progress.getPhase().name());
        request.setAttribute("progressPercent", progress.getPercentComplete());
        request.setAttribute("progressRemoved", progress.getRemoved());
        request.setAttribute("excludedServiceCount", m.getExcludedServiceIds().size());

        // A just-started flag survives the POST-Redirect-GET via the session; consume it here so the page begins
        // polling even during the brief window before the background job reports RUNNING.
        final Object justStarted = request.getSession().getAttribute("justStarted");
        if (justStarted != null) {
            request.getSession().removeAttribute("justStarted");
        }
        request.setAttribute("justStarted", Boolean.TRUE.equals(justStarted));

        // Clustering status (mirrors the Blowfish servlet's approach).
        final boolean clusteringAvailable = ClusterManager.isClusteringAvailable();
        final boolean clusteringEnabled = ClusterManager.isClusteringEnabled();
        final boolean clusteringStarted = ClusterManager.isClusteringStarted();
        final int clusterNodeCount = clusteringStarted ? ClusterManager.getNodesInfo().size() : 0;
        request.setAttribute("clusteringAvailable", clusteringAvailable);
        request.setAttribute("clusteringEnabled", clusteringEnabled);
        request.setAttribute("clusteringStarted", clusteringStarted);
        request.setAttribute("clusterNodeCount", clusterNodeCount);

        // CSRF token.
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, "csrf", csrf, -1);
        request.setAttribute("csrf", csrf);

        request.getRequestDispatcher("pubsub-subscription-maintenance-page.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Validate CSRF token.
        final String submittedCsrf = request.getParameter("csrf");
        final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        final String cookieCsrf = csrfCookie != null ? csrfCookie.getValue() : null;
        if (submittedCsrf == null || cookieCsrf == null || !submittedCsrf.equals(cookieCsrf)) {
            request.getSession().setAttribute("errorMessage", "pubsub.subscription.maintenance.error.csrf");
            response.sendRedirect("pubsub-subscription-maintenance.jsp");
            return;
        }

        if (ACTION_CLEANUP.equals(request.getParameter(PARAM_ACTION))) {
            // Require explicit backup confirmation, as the cleanup deletes rows irreversibly.
            final boolean dbBackup = "true".equals(request.getParameter(PARAM_DB_BACKUP));
            if (!dbBackup) {
                request.getSession().setAttribute("errorMessage", "pubsub.subscription.maintenance.error.backup-required");
                response.sendRedirect("pubsub-subscription-maintenance.jsp");
                return;
            }

            // Block in unsafe clustering states: a bulk delete racing across nodes risks deleting rows another node is
            // concurrently relying on or recreating.
            final boolean clusteringEnabled = ClusterManager.isClusteringEnabled();
            final boolean clusteringStarted = ClusterManager.isClusteringStarted();
            final int clusterNodeCount = clusteringStarted ? ClusterManager.getNodesInfo().size() : 0;
            if (clusteringEnabled && !clusteringStarted) {
                request.getSession().setAttribute("errorMessage", "pubsub.subscription.maintenance.error.cluster-enabled-not-started");
                response.sendRedirect("pubsub-subscription-maintenance.jsp");
                return;
            }
            if (clusterNodeCount > 1) {
                request.getSession().setAttribute("errorMessage", "pubsub.subscription.maintenance.error.multi-node-active");
                request.getSession().setAttribute("errorParam", String.valueOf(clusterNodeCount));
                response.sendRedirect("pubsub-subscription-maintenance.jsp");
                return;
            }

            // Launch the background cleanup. startCleanup() returns immediately; the page polls for progress.
            final boolean started = getMaintenance().startCleanup();
            if (started) {
                request.getSession().setAttribute("successMessage", "pubsub.subscription.maintenance.started");
                request.getSession().setAttribute("justStarted", Boolean.TRUE);
                final WebManager webManager = new WebManager();
                webManager.init(request, response, request.getSession(), request.getServletContext());
                webManager.logEvent("Started pub/sub subscription cleanup", null);
            } else {
                // A cleanup was already running; not an error, just informational.
                request.getSession().setAttribute("successMessage", "pubsub.subscription.maintenance.already-running");
                request.getSession().setAttribute("justStarted", Boolean.TRUE);
            }
        }

        response.sendRedirect("pubsub-subscription-maintenance.jsp");
    }

    /**
     * Writes the current cleanup progress as a small JSON object for the page's AJAX polling.
     *
     * The JSON shape is {@code {"phase":"RUNNING","percent":42,"removed":1234567,"error":null}}. The values come from
     * the thread-safe {@link PubSubSubscriptionMaintenance.Progress} snapshot, so no locking is needed here.
     *
     * @param response the response to write JSON to.
     * @throws IOException if writing fails.
     */
    private void writeProgressJson(HttpServletResponse response) throws IOException {
        final PubSubSubscriptionMaintenance.Progress progress = getMaintenance().getProgress();
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        final String error = progress.getError();
        final StringBuilder json = new StringBuilder();
        json.append('{')
            .append("\"phase\":\"").append(progress.getPhase().name()).append("\",")
            .append("\"percent\":").append(progress.getPercentComplete()).append(',')
            .append("\"removed\":").append(progress.getRemoved()).append(',')
            .append("\"total\":").append(progress.getTotalToRemove()).append(',')
            .append("\"error\":").append(error == null ? "null" : ('"' + jsonEscape(error) + '"'))
            .append('}');
        response.getWriter().write(json.toString());
    }

    /**
     * Minimal JSON string escaping for the error message: escapes backslash, double-quote and control characters so the
     * progress JSON remains well-formed regardless of the message content.
     *
     * @param value the raw string.
     * @return the escaped string (without surrounding quotes).
     */
    private static String jsonEscape(String value) {
        final StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
