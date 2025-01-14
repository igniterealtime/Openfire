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

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A data access object stores spam rapports in an Openfire-provided database structure.
 *
 * @author Guus der Kinderen, guus.der.kinderen@mgail.com
 */
public class DefaultSpamReportProvider implements SpamReportProvider
{
    private static final Logger Log = LoggerFactory.getLogger(DefaultSpamReportProvider.class);

    private static final String STORE_REPORT = "INSERT INTO ofSpamReport (reporter, reported, reason, created, raw) VALUES (?,?,?,?,?)";
    private static final String GET_REPORTS_SINCE = "SELECT (reporter, reported, created, raw) FROM ofSpamReport WHERE created >= ? ORDER BY created ASC, reporter, reported, reason";
    private static final String GET_REPORTS_BY = "SELECT (reporter, reported, created, raw) FROM ofSpamReport WHERE reporter = ? ORDER BY created ASC, reported, reason";
    private static final String GET_REPORTS_ABOUT = "SELECT (reporter, reported, created, raw) FROM ofSpamReport WHERE reported = ? ORDER BY created ASC, reporter, reason";

    @Override
    public void store(@Nonnull final SpamReport spamReport)
    {
        Log.trace("Storing spam report: {}", spamReport);

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(STORE_REPORT);
            pstmt.setString(1, spamReport.getReportingAddress().toString());
            pstmt.setString(2, spamReport.getReportedAddress().toString());
            pstmt.setString(3, spamReport.getReason());
            pstmt.setLong(4, spamReport.getTimestamp().toEpochMilli());
            DbConnectionManager.setLargeTextField(pstmt, 5, spamReport.getReportElement().asXML());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.error("A database error prevented successful storage of a spam report: {}", spamReport, e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Nonnull
    @Override
    public List<SpamReport> getSpamReportsSince(@Nonnull final Instant created)
    {
        Log.trace("Retrieving spam reports since {}", created);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // This potentially retrieves many rows from the table, and operates on the entire result set. For large
            // data sets, scalability issues are mitigated by:
            // - depending on auto-commit being disabled (for postgres). Getting a 'transaction' connection will ensure
            //   this (if supported).
            // - MSSQL differentiates between client-cursored and server-cursored result sets. For server-cursored
            //   result sets, the fetch buffer and scroll window are the same size (as opposed to fetch buffer
            //   containing all the rows). To hint that a server-cursored result set is desired, it should be configured
            //   to be 'forward only' as well as 'read only'.
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(GET_REPORTS_SINCE, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

            pstmt.setLong(1, created.toEpochMilli());

            pstmt.setFetchSize(250);
            pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);

            rs = pstmt.executeQuery();

            return parse(rs);
        } catch (SQLException e) {
            Log.error("A database error prevented successful retrieval of a spam reports (since: {})", created, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SpamReport> getSpamReportsByReporter(@Nonnull JID reporter)
    {
        Log.trace("Retrieving spam reports by {}", reporter);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // This potentially retrieves many rows from the table, and operates on the entire result set. For large
            // data sets, scalability issues are mitigated by:
            // - depending on auto-commit being disabled (for postgres). Getting a 'transaction' connection will ensure
            //   this (if supported).
            // - MSSQL differentiates between client-cursored and server-cursored result sets. For server-cursored
            //   result sets, the fetch buffer and scroll window are the same size (as opposed to fetch buffer
            //   containing all the rows). To hint that a server-cursored result set is desired, it should be configured
            //   to be 'forward only' as well as 'read only'.
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(GET_REPORTS_BY, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

            pstmt.setString(1, reporter.toString());

            pstmt.setFetchSize(250);
            pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);

            rs = pstmt.executeQuery();

            return parse(rs);
        } catch (SQLException e) {
            Log.error("A database error prevented successful retrieval of a spam reports (by: {})", reporter, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SpamReport> getSpamReportsByReported(@Nonnull JID reported)
    {
        Log.trace("Retrieving spam reports about {}", reported);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // This potentially retrieves many rows from the table, and operates on the entire result set. For large
            // data sets, scalability issues are mitigated by:
            // - depending on auto-commit being disabled (for postgres). Getting a 'transaction' connection will ensure
            //   this (if supported).
            // - MSSQL differentiates between client-cursored and server-cursored result sets. For server-cursored
            //   result sets, the fetch buffer and scroll window are the same size (as opposed to fetch buffer
            //   containing all the rows). To hint that a server-cursored result set is desired, it should be configured
            //   to be 'forward only' as well as 'read only'.
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(GET_REPORTS_ABOUT, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

            pstmt.setString(1, reported.toString());

            pstmt.setFetchSize(250);
            pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);

            rs = pstmt.executeQuery();

            return parse(rs);
        } catch (SQLException e) {
            Log.error("A database error prevented successful retrieval of a spam reports (about: {})", reported, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return Collections.emptyList();
    }

    protected static List<SpamReport> parse(@Nonnull final ResultSet rs) throws SQLException
    {
        final List<SpamReport> result = new LinkedList<>();

        long progress = 0;
        Instant lastProgressReport = Instant.now();
        while (rs.next())
        {
            final String reporter = rs.getString("reporter");
            final String reported = rs.getString("reported");
            final long reportcreated = rs.getLong("created");
            final String raw = DbConnectionManager.getLargeTextField(rs, 4);

            try {
                if (raw == null) {
                    Log.warn("Unable to parse raw data from the database (record created: {}) as spam report: raw data was missing", reportcreated);
                } else {
                    final Document document = DocumentHelper.parseText(raw);
                    final SpamReport spamReport = new SpamReport(Instant.ofEpochMilli(reportcreated), new JID(reporter), new JID(reported), document.getRootElement());
                    result.add(spamReport);
                }
            } catch (DocumentException e) {
                Log.warn("Unable to parse raw data from the database (record created: {}) as spam report: {} ", reportcreated, raw, e);
            }

            // When there are _many_ rows to be processed, log an occasional progress indicator, to let admins know that things are still churning.
            ++progress;
            if (lastProgressReport.isBefore(Instant.now().minus(10, ChronoUnit.SECONDS)) ) {
                Log.debug( "... processed {} spam reports so far.", progress);
                lastProgressReport = Instant.now();
            }
        }

        return result;
    }
}
