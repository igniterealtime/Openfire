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

import org.dom4j.*;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.stanzaid.StanzaID;
import org.jivesoftware.util.SAXReaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A data access object stores spam rapports in an Openfire-provided database structure.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DefaultSpamReportProvider implements SpamReportProvider
{
    protected static final DocumentFactory docFactory = DocumentFactory.getInstance();

    private static final Logger Log = LoggerFactory.getLogger(DefaultSpamReportProvider.class);

    private static final String STORE_REPORT = "INSERT INTO ofSpamReport (reportID, reporter, reported, reason, reportOrigin, thirdParty, created, context) VALUES (?,?,?,?,?,?,?,?)";
    private static final String STORE_REPORTED_STANZA = "INSERT INTO ofSpamStanza (reportID, stanzaIDValue, stanzaIDBy, stanza) VALUES (?,?,?,?)";
    private static final String GET_REPORT = "SELECT reportID, reporter, reported, reason, reportOrigin, thirdParty, created, context FROM ofSpamReport WHERE reportID = ?";
    private static final String GET_REPORTED_STANZAS = "SELECT reportID, stanzaIDValue, stanzaIDBy, stanza FROM ofSpamStanza WHERE reportID = ?";
    private static final String GET_REPORTS_SINCE = "SELECT reportID, reporter, reported, reason, reportOrigin, thirdParty, created, context FROM ofSpamReport WHERE created >= ? ORDER BY created ASC, reportID ASC";
    private static final String GET_REPORTS_BY = "SELECT reportID, reporter, reported, reason, reportOrigin, thirdParty, created, context FROM ofSpamReport WHERE reporter = ? ORDER BY created ASC, reportID ASC";
    private static final String GET_REPORTS_ABOUT = "SELECT reportID, reporter, reported, reason, reportOrigin, thirdParty, created, context FROM ofSpamReport WHERE reported = ? ORDER BY created ASC, reportID ASC";

    @Override
    public void store(@Nonnull final SpamReport spamReport)
    {
        Log.trace("Storing spam report: {}", spamReport);

        boolean abort = false;
        Connection con = null;
        PreparedStatement psmttReport = null;
        PreparedStatement pstmtStanza = null;
        try {
            con = DbConnectionManager.getTransactionConnection();

            // Storing the report itself.
            psmttReport = con.prepareStatement(STORE_REPORT);
            psmttReport.setLong(1, spamReport.getId());
            psmttReport.setString(2, spamReport.getReportingAddress().toString());
            psmttReport.setString(3, spamReport.getReportedAddress().toString());
            psmttReport.setString(4, spamReport.getReason());
            psmttReport.setInt(5, spamReport.isAllowedToReportToOriginDomain() ? 1 : 0);
            psmttReport.setInt(6, spamReport.isAllowedToSendToThirdParties() ? 1 : 0);
            psmttReport.setLong(7, spamReport.getTimestamp().toEpochMilli());
            if (!spamReport.getContext().isEmpty()) {
                DbConnectionManager.setLargeTextField(psmttReport, 8, spamReport.getContext().stream().map(t -> t.getValue() + (t.getLang() != null ? " (lang: " + t.getLang() + ")" : "")).collect(Collectors.joining("|+|"))); // TODO properly serialize this.
            } else {
                psmttReport.setNull(8, Types.VARCHAR);
            }
            psmttReport.executeUpdate();

            // Storing associated stanzas.
            final Map<StanzaID, Optional<Packet>> reportedStanzas = spamReport.getReportedStanzas();
            if (!reportedStanzas.isEmpty()) {
                pstmtStanza = con.prepareStatement(STORE_REPORTED_STANZA);
                for (final Map.Entry<StanzaID, Optional<Packet>> reportedStanza : spamReport.getReportedStanzas().entrySet()) {
                    pstmtStanza.setLong(1, spamReport.getId());
                    pstmtStanza.setString(2, reportedStanza.getKey().getId());
                    pstmtStanza.setString(3, reportedStanza.getKey().getId());
                    if (reportedStanza.getValue().isPresent()) {
                        DbConnectionManager.setLargeTextField(pstmtStanza, 4, reportedStanza.getValue().get().toXML());
                    } else {
                        psmttReport.setNull(4, Types.VARCHAR);
                    }
                    pstmtStanza.addBatch();
                }
                pstmtStanza.executeBatch();
            }

        } catch (SQLException e) {
            Log.error("A database error prevented successful storage of a spam report: {}", spamReport, e);
            abort = true;
        } finally {
            DbConnectionManager.closeStatement(pstmtStanza);
            DbConnectionManager.closeStatement(psmttReport);
            DbConnectionManager.closeTransactionConnection(con, abort);
        }
    }

    @Nullable
    @Override
    public SpamReport retrieve(final long reportID)
    {
        Log.trace("Retrieving spam report with ID {}", reportID);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_REPORT);
            pstmt.setLong(1, reportID);
            rs = pstmt.executeQuery();

            return parse(rs).stream().findFirst().orElse(null);
        } catch (SQLException e) {
            Log.error("A database error prevented successful retrieval of a spam report (with ID: {})", reportID, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return null;
    }

    @Nonnull
    public Map<StanzaID, Optional<Packet>> retrieveStanzas(final long reportID)
    {
        Log.trace("Retrieving reported stanzas for report with ID {}", reportID);
        final Map<StanzaID, Optional<Packet>> result = new HashMap<>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_REPORTED_STANZAS);
            pstmt.setLong(1, reportID);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                final String stanzaIDValue = rs.getString("stanzaIDValue");
                final String stanzaIDBy = rs.getString("stanzaIDBy");
                final String stanza = DbConnectionManager.getLargeTextField(rs, 1);
                try {
                    final StanzaID stanzaID = new StanzaID(stanzaIDValue, new JID(stanzaIDBy));
                    final Packet packet;

                    if (stanza != null) {
                        final Element root = SAXReaderUtil.readRootElement(stanza);
                        switch (root.getName()) {
                            case "presence":
                                packet = new Presence(root);
                                break;
                            case "iq":
                                packet = new IQ(root);
                                break;
                            case "message":
                                packet = new Message(root);
                                break;
                            default:
                                Log.warn("Unable to serialize database-stored stanza (for report with ID {}) to an XMPP stanza: {}", reportID, stanza);
                                packet = null;
                        }
                    } else {
                        packet = null;
                    }

                    result.put(stanzaID, Optional.ofNullable(packet));
                } catch (Throwable t) {
                    Log.warn("Unable to serialize database-stored stanza ID or stanza for report with ID {}) to XMPP: {}", reportID, stanza, t);
                }
            }
        } catch (SQLException e) {
            Log.error("A database error prevented successful retrieval of a reported stanzas (for stanza report with id: {})", reportID, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return result;
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
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeTransactionConnection(pstmt, con, false); // Only doing SELECTs, so transaction rollback does not seem important.
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SpamReport> getSpamReportsByReporter(@Nonnull final JID reporter)
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
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeTransactionConnection(pstmt, con, false); // Only doing SELECTs, so transaction rollback does not seem important.
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SpamReport> getSpamReportsByReported(@Nonnull final JID reported)
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
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeTransactionConnection(pstmt, con, false); // Only doing SELECTs, so transaction rollback does not seem important.
        }
        return Collections.emptyList();
    }

    protected List<SpamReport> parse(@Nonnull final ResultSet rs) throws SQLException
    {
        final List<SpamReport> result = new LinkedList<>();

        long progress = 0;
        Instant lastProgressReport = Instant.now();
        while (rs.next())
        {
            final long reportID = rs.getLong("reportID");
            final String reporter = rs.getString("reporter");
            final String reported = rs.getString("reported");
            final String reason = rs.getString("reason");
            final boolean reportOrigin = rs.getInt("reportOrigin") == 1;
            final boolean thirdParty = rs.getInt("thirdParty") == 1;
            final long created = rs.getLong("created");
            final String contextRaw = DbConnectionManager.getLargeTextField(rs, 8);

            final Set<SpamReport.Text> context = new HashSet<>();
            // TODO replace this terrible serialization of texts into one column.
            if (contextRaw != null) {
                final String regex = "\\W\\(lang\\:\\W?(\\w*)\\)$"; // checks for trailing " (lang: XYZ)"
                final Pattern pattern = Pattern.compile(regex);
                for (final String part : contextRaw.split("\\|\\+\\|")) {
                    final Matcher matcher = pattern.matcher(part);
                    final String text;
                    final String language;
                    if (matcher.find()) {
                        final String fullMatch = matcher.group(0);
                        language = matcher.group(1);
                        text = part.substring(0, part.length() - fullMatch.length());
                    } else {
                        text = part;
                        language = null;
                    }
                    context.add(new SpamReport.Text(text, language));
                }
            }

            // TODO Improve on this, as it is not ideal to have another query for each row that is being parsed. On the other hand, a simple JOIN would possibly pull in a lot of duplicated data, which isn't ideal either.
            final Map<StanzaID, Optional<Packet>> reportedStanzas = retrieveStanzas(reportID);

            final SpamReport spamReport = new SpamReport(reportID, Instant.ofEpochMilli(created), new JID(reporter), new JID(reported), reason, reportOrigin, thirdParty, context, reportedStanzas);
            result.add(spamReport);

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
