/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.spamreporting;

import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;

/**
 * A data access object definition for entities that manage storage of spam rapports
 *
 * @author Guus der Kinderen, guus.der.kinderen@mgail.com
 */
public interface SpamReportProvider
{
    /**
     * Persists a spam report.
     *
     * @param spamReport the spam report to persist.
     */
    void store(@Nonnull final SpamReport spamReport);

    /**
     * Retrieves all persisted spam reports, ordered by timestamp.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * @return A collection of persisted spam reports.
     */
    default @Nonnull List<SpamReport> getSpamReports() {
        return getSpamReportsSince(Instant.EPOCH);
    }

    /**
     * Counts the number of persisted spam reports.
     *
     * @return a count of spam reports.
     */
    default int getSpamReportCount() {
        return getSpamReports().size();
    }

    /**
     * Retrieves a sub-collection of persisted spam reports, ordered by timestamp.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * The returned collection will contain less than the requested amount of results only if no more reports were
     * persisted.
     *
     * @param startIndex low endpoint (inclusive) of the subList.
     * @param numResults the maximum amount of reports to return.
     * @return A collection of persisted spam reports.
     */
    default @Nonnull List<SpamReport> getSpamReports(final int startIndex, final int numResults) {
        final List<SpamReport> spamReports = getSpamReports();
        return spamReports.subList(startIndex, Math.min(startIndex + numResults, spamReports.size()));
    }

    /**
     * Retrieves all persisted spam reports, ordered by timestamp, reported at or after the provided timestamp.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * @param timestamp earliest point in time (inclusive) for which to return reports.
     * @return A collection of persisted spam reports.
     */
    @Nonnull List<SpamReport> getSpamReportsSince(@Nonnull final Instant timestamp);

    /**
     * Retrieves a sub-collection of persisted spam reports, ordered by timestamp, reported at or after the provided timestamp.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * The returned collection will contain less than the requested amount of results only if no more reports were
     * persisted.
     *
     * @param timestamp earliest point in time (inclusive) for which to return reports.
     * @param startIndex low endpoint (inclusive) of the subList.
     * @param numResults the maximum amount of reports to return.
     * @return A collection of persisted spam reports.
     */
    default @Nonnull List<SpamReport> getSpamReportsSince(final @Nonnull Instant timestamp, final int startIndex, final int numResults) {
        final List<SpamReport> spamReports = getSpamReportsSince(timestamp);
        return spamReports.subList(startIndex, Math.min(startIndex + numResults, spamReports.size()));
    }

    /**
     * Counts the number of persisted spam reports, reported at or after the provided timestamp.
     *
     * @param timestamp earliest point in time (inclusive) for which to count reports.
     * @return a count of spam reports.
     */
    default int getSpamReportCountSince(final @Nonnull Instant timestamp) {
        return getSpamReportsSince(timestamp).size();
    }

    /**
     * Retrieves all persisted spam reports, ordered by timestamp, reported by the provided entity.
     *
     * The reporting entity is different from the entity that is being reported. For reports on the <em>reported</em>
     * entity, see {#getSpamReportsByReported}.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * @param reporter entity that created/submitted the report.
     * @return A collection of persisted spam reports.
     * @see #getSpamReportsByReported(JID)
     */
    @Nonnull List<SpamReport> getSpamReportsByReporter(@Nonnull final JID reporter);

    /**
     * Retrieves a sub-collection of persisted spam reports, ordered by timestamp, reported by the provided entity.
     *
     * The reporting entity is different from the entity that is being reported. For reports on the <em>reported</em>
     * entity, see {#getSpamReportsByReported}.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * The returned collection will contain less than the requested amount of results only if no more reports were
     * persisted.
     *
     * @param reporter entity that created/submitted the report.
     * @param startIndex low endpoint (inclusive) of the subList.
     * @param numResults the maximum amount of reports to return.
     * @return A collection of persisted spam reports.
     * @see #getSpamReportsByReported(JID)
     */
    default @Nonnull List<SpamReport> getSpamReportsByReporter(@Nonnull final JID reporter, final int startIndex, final int numResults) {
        final List<SpamReport> spamReports = getSpamReportsByReporter(reporter);
        return spamReports.subList(startIndex, Math.min(startIndex + numResults, spamReports.size()));
    }

    /**
     * Counts the number of persisted spam reports, reported by the provided entity.
     *
     * @param reporter entity that created/submitted the report.
     * @return a count of spam reports.
     */
    default int countSpamReportsByReporter(@Nonnull final JID reporter) {
        return getSpamReportsByReporter(reporter).size();
    }

    /**
     * Retrieves all persisted spam reports, ordered by timestamp, that reference the provided entity as the reported
     * entity (the 'offender').
     *
     * The reported entity (the 'offender') is different from the entity that created the report. For reports by the
     * <em>reporting</em> entity, see {#getSpamReportsByReporter}.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * @param reported entity for which the report was created (the reported 'offender').
     * @return A collection of persisted spam reports.
     * @see #getSpamReportsByReporter(JID)
     */
    @Nonnull List<SpamReport> getSpamReportsByReported(@Nonnull final JID reported);

    /**
     * Retrieves a sub-collection of persisted spam reports, ordered by timestamp, that reference the provided entity as
     * the reported entity (the 'offender').
     *
     * The reported entity (the 'offender') is different from the entity that created the report. For reports by the
     * <em>reporting</em> entity, see {#getSpamReportsByReporter}.
     *
     * When more than one report has the same timestamp, the order of those reports is undefined, but consistent across
     * invocations.
     *
     * The returned collection will contain less than the requested amount of results only if no more reports were
     * persisted.
     *
     * @param reported entity for which the report was created (the reported 'offender').
     * @param startIndex low endpoint (inclusive) of the subList.
     * @param numResults the maximum amount of reports to return.
     * @return A collection of persisted spam reports.
     * @see #getSpamReportsByReporter(JID)
     */
    default @Nonnull List<SpamReport> getSpamReportsByReported(@Nonnull final JID reported, final int startIndex, final int numResults) {
        final List<SpamReport> spamReports = getSpamReportsByReported(reported);
        return spamReports.subList(startIndex, Math.min(startIndex + numResults, spamReports.size()));
    }

    /**
     * Counts the number of persisted spam reports, that reference the provided entity as the reported entity (the
     * 'offender').
     *
     * @param reported entity for which the report was created (the reported 'offender').
     * @return a count of spam reports.
     */
    default int countSpamReportsByReported(@Nonnull final JID reported) {
        return getSpamReportsByReported(reported).size();
    }
}
