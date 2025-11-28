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

package org.jivesoftware.admin.servlet;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.Blowfish;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet for migrating Blowfish-encrypted properties from SHA1 to PBKDF2 key derivation.
 *
 * Provides admin console UI for safely re-encrypting all Blowfish-encrypted properties
 * with PBKDF2-HMAC-SHA512 derived keys, replacing legacy SHA1 key derivation.
 *
 * @author Matthew Vivian
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3075">OF-3075</a>
 */
@WebServlet(value = "/security-blowfish-migration.jsp")
public class BlowfishMigrationServlet extends HttpServlet {

    private static final Logger Log = LoggerFactory.getLogger(BlowfishMigrationServlet.class);

    private static final String ENCRYPTION_ALGORITHM_BLOWFISH = "Blowfish";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_DB_BACKUP = "dbBackup";
    private static final String PARAM_SECURITY_BACKUP = "securityBackup";
    private static final String PARAM_OPENFIRE_BACKUP = "openfireBackup";
    private static final String ACTION_MIGRATE = "migrate";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Determine current encryption algorithm
        String encryptionAlg = JiveGlobals.getEncryptionAlgorithm();
        boolean isBlowfish = ENCRYPTION_ALGORITHM_BLOWFISH.equalsIgnoreCase(encryptionAlg);

        // Determine current KDF (only relevant if using Blowfish)
        String currentKdf = isBlowfish ? JiveGlobals.getBlowfishKdf() : null;
        boolean needsMigration = isBlowfish &&
                JiveGlobals.BLOWFISH_KDF_SHA1.equalsIgnoreCase(currentKdf);
        boolean alreadyMigrated = isBlowfish &&
                JiveGlobals.BLOWFISH_KDF_PBKDF2.equalsIgnoreCase(currentKdf);

        // Set page state attributes
        request.setAttribute("isBlowfish", isBlowfish);
        request.setAttribute("needsMigration", needsMigration);
        request.setAttribute("alreadyMigrated", alreadyMigrated);
        request.setAttribute("currentKdf", currentKdf);
        request.setAttribute("encryptionAlgorithm", encryptionAlg);

        // If migration needed, count encrypted properties
        if (needsMigration) {
            int dbCount = getEncryptedPropertyCount();
            int xmlCount = getEncryptedXMLPropertyCount();
            request.setAttribute("encryptedPropertyCountDb", dbCount);
            request.setAttribute("encryptedPropertyCountXml", xmlCount);
        }

        // Detect clustering status using multiple checks to avoid race conditions
        // See ADR-004 for detailed explanation of clustering detection approach
        boolean clusteringAvailable = ClusterManager.isClusteringAvailable();
        boolean clusteringEnabled = ClusterManager.isClusteringEnabled();
        boolean clusteringStarted = ClusterManager.isClusteringStarted();
        int clusterNodeCount = clusteringStarted ? ClusterManager.getNodesInfo().size() : 0;

        request.setAttribute("clusteringAvailable", clusteringAvailable);
        request.setAttribute("clusteringEnabled", clusteringEnabled);
        request.setAttribute("clusteringStarted", clusteringStarted);
        request.setAttribute("clusterNodeCount", clusterNodeCount);

        // Set CSRF token
        String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, "csrf", csrf, -1);
        request.setAttribute("csrf", csrf);

        // Forward to JSP view (note: different filename to avoid infinite loop)
        request.getRequestDispatcher("blowfish-migration.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Validate CSRF token
        String submittedCsrf = request.getParameter("csrf");
        Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        String cookieCsrf = csrfCookie != null ? csrfCookie.getValue() : null;

        if (submittedCsrf == null || cookieCsrf == null || !submittedCsrf.equals(cookieCsrf)) {
            request.getSession().setAttribute("errorMessage",
                    "security.blowfish.migration.error.csrf");
            response.sendRedirect("security-blowfish-migration.jsp");
            return;
        }

        String action = request.getParameter(PARAM_ACTION);

        if (ACTION_MIGRATE.equals(action)) {
            // Verify checkboxes confirmed
            boolean dbBackup = "true".equals(request.getParameter(PARAM_DB_BACKUP));
            boolean securityBackup = "true".equals(request.getParameter(PARAM_SECURITY_BACKUP));
            boolean openfireBackup = "true".equals(request.getParameter(PARAM_OPENFIRE_BACKUP));

            if (!dbBackup || !securityBackup || !openfireBackup) {
                request.getSession().setAttribute("errorMessage",
                        "security.blowfish.migration.error.backups-required");
                response.sendRedirect("security-blowfish-migration.jsp");
                return;
            }

            // Check clustering status - block migration in unsafe scenarios
            // See ADR-004 for detailed explanation of clustering detection approach
            boolean clusteringEnabled = ClusterManager.isClusteringEnabled();
            boolean clusteringStarted = ClusterManager.isClusteringStarted();
            int clusterNodeCount = clusteringStarted ? ClusterManager.getNodesInfo().size() : 0;

            // Block if clustering is enabled but not yet started (race condition risk)
            // Other nodes might be starting simultaneously
            if (clusteringEnabled && !clusteringStarted) {
                request.getSession().setAttribute("errorMessage",
                        "security.blowfish.migration.error.cluster-enabled-not-started");
                response.sendRedirect("security-blowfish-migration.jsp");
                return;
            }

            // Block if multiple cluster nodes are active
            if (clusterNodeCount > 1) {
                request.getSession().setAttribute("errorMessage",
                        "security.blowfish.migration.error.multi-node-active");
                request.getSession().setAttribute("errorParam", String.valueOf(clusterNodeCount));
                response.sendRedirect("security-blowfish-migration.jsp");
                return;
            }

            try {
                // Perform migration
                MigrationResult result = migrateBlowfishToPBKDF2();

                request.getSession().setAttribute("successMessage",
                        "security.blowfish.migration.success");
                request.getSession().setAttribute("successParamDb", result.databaseCount());
                request.getSession().setAttribute("successParamXml", result.xmlCount());

            } catch (Exception e) {
                request.getSession().setAttribute("errorMessage",
                        "security.blowfish.migration.error.detail");
                request.getSession().setAttribute("errorParam", e.getMessage());
                Log.error("Blowfish migration failed", e);
            }
        }

        // Redirect to GET to prevent form resubmission
        response.sendRedirect("security-blowfish-migration.jsp");
    }

    /**
     * Returns the count of encrypted XML properties that have actual values.
     * These are the properties that will be migrated (not just configured names).
     *
     * @return Number of encrypted properties with values in openfire.xml
     */
    private int getEncryptedXMLPropertyCount() {
        return JiveGlobals.getEncryptedXMLPropertyValueCount();
    }

    /**
     * Counts encrypted properties in the database.
     *
     * @return Number of encrypted properties (ofProperty.encrypted = 1)
     */
    private int getEncryptedPropertyCount() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(
                    "SELECT COUNT(*) FROM ofProperty WHERE encrypted = 1");
            rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            Log.error("Error counting encrypted properties", e);
            return 0;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Migrates all Blowfish-encrypted properties from SHA1 to PBKDF2 key derivation.
     *
     * CRITICAL: This operation cannot be reversed without a backup.
     * Properties encrypted with PBKDF2-derived keys cannot be decrypted with SHA1-derived keys.
     *
     * Prerequisites:
     * - Database backup completed
     * - security.xml backed up
     * - All cluster nodes offline (except migration node)
     *
     * @return MigrationResult containing counts for database and XML properties migrated
     * @throws IllegalStateException if already using PBKDF2 or not using Blowfish
     * @throws RuntimeException if migration fails (with rollback)
     */
    private MigrationResult migrateBlowfishToPBKDF2() throws Exception {
        // 1. Verify preconditions
        String currentKdf = JiveGlobals.getBlowfishKdf();
        if (JiveGlobals.BLOWFISH_KDF_PBKDF2.equalsIgnoreCase(currentKdf)) {
            throw new IllegalStateException("Already using PBKDF2 - no migration needed");
        }

        String encryptionAlgorithm = JiveGlobals.getEncryptionAlgorithm();
        if (!ENCRYPTION_ALGORITHM_BLOWFISH.equalsIgnoreCase(encryptionAlgorithm)) {
            throw new IllegalStateException("Encryption algorithm is " + encryptionAlgorithm +
                    ", not Blowfish. Migration only applies to Blowfish.");
        }

        Log.info("Starting Blowfish migration from SHA1 to PBKDF2...");

        // 2. Migrate XML properties FIRST (fail fast before database changes)
        // This migrates encrypted properties in openfire.xml (e.g., database credentials)
        int xmlMigrated = 0;
        try {
            xmlMigrated = JiveGlobals.migrateXMLPropertiesFromSHA1ToPBKDF2();
            Log.info("Migrated {} XML properties from openfire.xml", xmlMigrated);
        } catch (Exception e) {
            // XML migration failed - stop before making any database changes
            throw new RuntimeException("XML property migration failed. " +
                    "No database changes were made. " + e.getMessage(), e);
        }

        // 3. Find all Blowfish-encrypted properties in database
        List<EncryptedProperty> dbProperties = getAllEncryptedPropertiesFromDatabase();
        Log.info("Found {} encrypted properties in database to migrate", dbProperties.size());

        // 4. Get the master encryption key from JiveGlobals
        String masterKey = JiveGlobals.getMasterEncryptionKey();
        if (masterKey == null) {
            Log.info("No custom encryption key configured - using default Blowfish key for migration");
        }

        // 5. Initialise both SHA1 and PBKDF2 encryptors
        // We need two separate instances with different KDFs:
        // - SHA1: for decrypting existing properties
        // - PBKDF2: for re-encrypting with new KDF
        Blowfish sha1Blowfish = new Blowfish();
        sha1Blowfish.setKey(masterKey, JiveGlobals.BLOWFISH_KDF_SHA1);

        Blowfish pbkdf2Blowfish = new Blowfish();
        pbkdf2Blowfish.setKey(masterKey, JiveGlobals.BLOWFISH_KDF_PBKDF2);

        // 6. Migrate each database property within a transaction
        Connection con = null;
        boolean originalAutoCommit = true;
        int migrated = 0;
        int failed = 0;
        List<String> failedProperties = new ArrayList<>();

        try {
            con = DbConnectionManager.getConnection();
            originalAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false); // Start transaction

            for (EncryptedProperty prop : dbProperties) {
                try {
                    // Decrypt with SHA1-derived key
                    String decrypted = sha1Blowfish.decryptString(prop.value);

                    // Re-encrypt with PBKDF2-derived key
                    String reencrypted = pbkdf2Blowfish.encryptString(decrypted);

                    // Update property in database
                    updateEncryptedProperty(con, prop.name, reencrypted);

                    migrated++;

                    if (migrated % 10 == 0) {
                        Log.info("Migration progress: {}/{} properties", migrated, dbProperties.size());
                    }

                } catch (Exception e) {
                    Log.error("Failed to migrate property: {}", prop.name, e);
                    failed++;
                    failedProperties.add(prop.name);
                }
            }

            // 6. Check migration results
            if (failed > 0) {
                // Rollback transaction
                con.rollback();
                Log.error("Migration completed with {} failures out of {} properties",
                        failed, dbProperties.size());
                Log.error("Failed properties: {}", String.join(", ", failedProperties));
                throw new RuntimeException("Migration failed for " + failed + " properties. " +
                        "Database has been rolled back. Check logs for details.");
            }

            // 7. Commit transaction
            con.commit();
            Log.info("Database transaction committed successfully");

            // 8. Update security.xml to switch KDF to PBKDF2
            // This only updates the local node's security.xml
            // In clustered deployments, admin must manually sync to other nodes
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
            Log.info("Updated security.xml: encrypt.blowfish.kdf=pbkdf2");

            // 9. Log success
            Log.info("Successfully migrated {} database properties and {} XML properties from SHA1 to PBKDF2",
                    migrated, xmlMigrated);
            Log.info("Blowfish KDF is now set to PBKDF2-HMAC-SHA512 in security.xml");

            return new MigrationResult(migrated, xmlMigrated);

        } catch (Exception e) {
            // Rollback on any error
            if (con != null) {
                try {
                    con.rollback();
                    Log.error("Transaction rolled back due to error", e);
                } catch (SQLException rollbackEx) {
                    Log.error("Failed to rollback transaction", rollbackEx);
                }
            }
            throw e;

        } finally {
            // Restore autocommit
            if (con != null) {
                try {
                    con.setAutoCommit(originalAutoCommit);
                } catch (SQLException e) {
                    Log.error("Failed to restore autocommit setting", e);
                }
                DbConnectionManager.closeConnection(con);
            }
        }
    }

    /**
     * Retrieves all encrypted properties from the ofProperty database table.
     *
     * @return List of encrypted properties (name and encrypted value)
     */
    private List<EncryptedProperty> getAllEncryptedPropertiesFromDatabase() {
        List<EncryptedProperty> properties = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(
                    "SELECT name, propValue FROM ofProperty WHERE encrypted = 1"
            );
            rs = pstmt.executeQuery();

            while (rs.next()) {
                String name = rs.getString("name");
                String encryptedValue = rs.getString("propValue");
                properties.add(new EncryptedProperty(name, encryptedValue));
            }

        } catch (SQLException e) {
            Log.error("Error loading encrypted properties from database", e);
            throw new RuntimeException("Failed to load encrypted properties", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return properties;
    }

    /**
     * Updates an encrypted property value in the database.
     * Uses the provided connection (for transaction support).
     *
     * @param con Database connection (part of transaction)
     * @param name Property name
     * @param newEncryptedValue New encrypted value (using PBKDF2)
     * @throws SQLException if update fails
     */
    private void updateEncryptedProperty(Connection con, String name, String newEncryptedValue)
            throws SQLException {
        PreparedStatement pstmt = null;

        try {
            pstmt = con.prepareStatement(
                    "UPDATE ofProperty SET propValue = ? WHERE name = ? AND encrypted = 1"
            );
            pstmt.setString(1, newEncryptedValue);
            pstmt.setString(2, name);

            int updated = pstmt.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected to update 1 row, but updated " + updated);
            }

        } finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    /**
     * Simple record for encrypted property.
     */
    private record EncryptedProperty(String name, String value) {}

    /**
     * Result of migration operation containing counts for both database and XML properties.
     */
    private record MigrationResult(int databaseCount, int xmlCount) {}
}
