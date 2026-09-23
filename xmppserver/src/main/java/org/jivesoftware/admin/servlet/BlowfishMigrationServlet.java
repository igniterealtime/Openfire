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
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.EncryptedPasswordMigration;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.Blowfish;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.JiveGlobals;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final String PARAM_INCLUDE_WITHOUT_SCRAM = "includeWithoutScram";
    private static final String ACTION_MIGRATE = "migrate";
    private static final String ACTION_REPAIR_PASSWORDS = "repair-passwords";

    /** Prevents starting an operation while another (which can take minutes) is still running. */
    private static final AtomicBoolean operationInProgress = new AtomicBoolean(false);

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

        // User passwords are re-encrypted by the migration, or repaired after a migration by an older version (OF-3374).
        if (needsMigration || alreadyMigrated) {
            request.setAttribute("encryptedPasswordCount", getEncryptedPasswordCount());
            request.setAttribute("passwordReencryptionNeeded", EncryptedPasswordMigration.isRepairNeeded());
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

        final boolean isOperation = ACTION_MIGRATE.equals(action) || ACTION_REPAIR_PASSWORDS.equals(action);
        if (isOperation && !operationInProgress.compareAndSet(false, true)) {
            request.getSession().setAttribute("errorMessage",
                    "security.blowfish.migration.error.in-progress");
            response.sendRedirect("security-blowfish-migration.jsp");
            return;
        }

        try {

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

                if (blockIfClusteringUnsafe(request, response, "security.blowfish.migration.error.")) {
                    return;
                }

                // Block if security.xml cannot be persisted. The migration must store a new PBKDF2 salt
                // and the kdf=pbkdf2 flag in conf/security.xml; if those writes are silently discarded
                // (security.xml missing, empty, corrupt or not writable), the re-encrypted data becomes
                // unrecoverable after a restart. Fail fast here, before any database changes. (OF-3305)
                if (!JiveGlobals.isSecurityPropertiesPersistable()) {
                    request.getSession().setAttribute("errorMessage",
                            "security.blowfish.migration.error.security-xml-not-writable");
                    response.sendRedirect("security-blowfish-migration.jsp");
                    return;
                }

                try {
                    // Perform migration
                    MigrationResult result = migrateBlowfishToPBKDF2();

                    final WebManager webManager = new WebManager();
                    webManager.init(request, response, request.getSession(), request.getServletContext());
                    webManager.logEvent("Migrated encrypted properties to more secure encryption standard", "Successfully migrated " + result.databaseCount() + " database properties, " + result.xmlCount() + " XML properties and " + result.passwords().reencrypted() + " user passwords from SHA1 to PBKDF2.");

                    request.getSession().setAttribute("successMessage",
                            "security.blowfish.migration.success");
                    request.getSession().setAttribute("successParamDb", result.databaseCount());
                    request.getSession().setAttribute("successParamXml", result.xmlCount());
                    storePasswordOutcome(request, result.passwords());

                } catch (Exception e) {
                    request.getSession().setAttribute("errorMessage",
                            "security.blowfish.migration.error.detail");
                    request.getSession().setAttribute("errorParam", e.getMessage());
                    Log.error("Blowfish migration failed", e);
                }
            } else if (ACTION_REPAIR_PASSWORDS.equals(action)) {
                if (!"true".equals(request.getParameter(PARAM_DB_BACKUP))) {
                    request.getSession().setAttribute("errorMessage",
                            "security.blowfish.migration.error.backups-required");
                    response.sendRedirect("security-blowfish-migration.jsp");
                    return;
                }

                if (blockIfClusteringUnsafe(request, response, "security.blowfish.migration.passwords.error.")) {
                    return;
                }

                // Before the migration, re-encrypting with PBKDF2 would make passwords unreadable.
                if (!ENCRYPTION_ALGORITHM_BLOWFISH.equalsIgnoreCase(JiveGlobals.getEncryptionAlgorithm())
                        || !JiveGlobals.BLOWFISH_KDF_PBKDF2.equalsIgnoreCase(JiveGlobals.getBlowfishKdf())) {
                    request.getSession().setAttribute("errorMessage",
                            "security.blowfish.migration.error.passwords-require-pbkdf2");
                    response.sendRedirect("security-blowfish-migration.jsp");
                    return;
                }

                final boolean includeWithoutScram = "true".equals(request.getParameter(PARAM_INCLUDE_WITHOUT_SCRAM));
                try {
                    final EncryptedPasswordMigration.Result result = repairEncryptedPasswords(includeWithoutScram);

                    // While passwords of users without SCRAM credentials remain unverified, they may still use SHA1: the
                    // repair then stays on offer, and logins do not derive credentials from those passwords.
                    JiveGlobals.setPasswordsReencrypted(result.unverifiable().isEmpty());

                    final WebManager webManager = new WebManager();
                    webManager.init(request, response, request.getSession(), request.getServletContext());
                    webManager.logEvent("Re-encrypted user passwords to more secure encryption standard", "Re-encrypted " + result.reencrypted() + " user passwords from SHA1 to PBKDF2"
                            + (includeWithoutScram ? ", including those of users without SCRAM credentials, without verification. " : ". ")
                            + result.unverifiable().size() + " could not be verified.");

                    request.getSession().setAttribute("successMessage", result.reencrypted() > 0
                            ? "security.blowfish.migration.passwords.repair-success"
                            : "security.blowfish.migration.passwords.repair-success.none");
                    storePasswordOutcome(request, result);

                } catch (Exception e) {
                    request.getSession().setAttribute("errorMessage",
                            "security.blowfish.migration.error.detail");
                    request.getSession().setAttribute("errorParam", e.getMessage());
                    Log.error("Re-encryption of user passwords failed", e);
                }
            }

        } finally {
            if (isOperation) {
                operationInProgress.set(false);
            }
        }

        // Redirect to GET to prevent form resubmission
        response.sendRedirect("security-blowfish-migration.jsp");
    }

    /**
     * Blocks an operation, redirecting back with an error, unless this is the only running cluster node.
     *
     * @param request        the request that triggered the operation
     * @param response       the response to redirect on, if blocked
     * @param errorKeyPrefix the prefix of the keys of the error messages for the operation
     * @return {@code true} if the operation was blocked (and the caller must not proceed), {@code false} otherwise
     * @throws IOException if the redirect fails
     */
    private boolean blockIfClusteringUnsafe(HttpServletRequest request, HttpServletResponse response, String errorKeyPrefix) throws IOException {
        // See ADR-004 for detailed explanation of clustering detection approach.
        boolean clusteringEnabled = ClusterManager.isClusteringEnabled();
        boolean clusteringStarted = ClusterManager.isClusteringStarted();
        int clusterNodeCount = clusteringStarted ? ClusterManager.getNodesInfo().size() : 0;

        // Block if clustering is enabled but not yet started (race condition risk).
        // Other nodes might be starting simultaneously.
        if (clusteringEnabled && !clusteringStarted) {
            request.getSession().setAttribute("errorMessage",
                    errorKeyPrefix + "cluster-enabled-not-started");
            response.sendRedirect("security-blowfish-migration.jsp");
            return true;
        }

        // Block if multiple cluster nodes are active.
        if (clusterNodeCount > 1) {
            request.getSession().setAttribute("errorMessage",
                    errorKeyPrefix + "multi-node-active");
            request.getSession().setAttribute("errorParam", String.valueOf(clusterNodeCount));
            response.sendRedirect("security-blowfish-migration.jsp");
            return true;
        }

        return false;
    }

    /**
     * Re-encrypts user passwords that still use SHA1 after a migration to PBKDF2 by an older version (OF-3374).
     *
     * @param includeWithoutScram whether to also re-encrypt, without verification, the passwords of users without SCRAM credentials
     * @return the outcome of the operation
     * @throws SQLException if the stored passwords could not be read or updated (in which case nothing is changed)
     */
    private EncryptedPasswordMigration.Result repairEncryptedPasswords(final boolean includeWithoutScram) throws SQLException {
        // Unlike the migration, this does not replace the password cipher: it already uses PBKDF2.
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            return EncryptedPasswordMigration.reencryptVerified(con, JiveGlobals.BLOWFISH_KDF_SHA1, JiveGlobals.BLOWFISH_KDF_PBKDF2, includeWithoutScram);
        } catch (SQLException | RuntimeException e) {
            abortTransaction = true;
            throw e;
        } finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Stores the outcome of re-encrypting user passwords in the session, for the page to render after the redirect.
     * Only counts are stored; the usernames are in the server log. Unverified passwords (typically already using
     * PBKDF2) are not reported.
     *
     * @param request the request that caused the passwords to be re-encrypted
     * @param result the outcome of re-encrypting user passwords
     */
    private static void storePasswordOutcome(HttpServletRequest request, EncryptedPasswordMigration.Result result) {
        request.getSession().setAttribute("passwordsReencrypted", result.reencrypted());
        if (!result.unverifiable().isEmpty()) {
            request.getSession().setAttribute("passwordsUnverifiableCount", result.unverifiable().size());
        }
        if (!result.undecryptable().isEmpty()) {
            request.getSession().setAttribute("passwordsUndecryptableCount", result.undecryptable().size());
        }
        if (!result.modifiedConcurrently().isEmpty()) {
            request.getSession().setAttribute("passwordsModifiedCount", result.modifiedConcurrently().size());
        }
    }

    /**
     * Counts the user passwords that are stored encrypted in the database.
     *
     * @return Number of users with a non-null ofUser.encryptedPassword
     */
    private int getEncryptedPasswordCount() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(
                    "SELECT COUNT(*) FROM ofUser WHERE encryptedPassword IS NOT NULL");
            rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            Log.error("Error counting encrypted user passwords", e);
            return 0;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
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

        // Defence in depth: refuse to proceed if security.xml cannot be persisted, so the PBKDF2
        // salt and KDF flag this migration generates cannot be silently lost. doPost() and
        // migrateXMLPropertiesFromSHA1ToPBKDF2() also check this; the check here keeps the method
        // self-protecting regardless of how it is reached. (OF-3305)
        if (!JiveGlobals.isSecurityPropertiesPersistable()) {
            throw new IllegalStateException("Cannot migrate: conf/security.xml is not loaded or not writable, "
                    + "so the new PBKDF2 salt and KDF setting cannot be persisted. "
                    + "Repair conf/security.xml before migrating.");
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
        boolean abortTransaction = false;
        int migrated = 0;
        int failed = 0;
        List<String> failedProperties = new ArrayList<>();

        try {
            con = DbConnectionManager.getTransactionConnection();

            // Time-based progress logging - log at most every 5 seconds
            Instant lastLogTime = Instant.now();
            Duration logInterval = Duration.ofSeconds(5);

            for (EncryptedProperty prop : dbProperties) {
                try {
                    // Decrypt with SHA1-derived key
                    String decrypted = sha1Blowfish.decryptString(prop.value);

                    // Re-encrypt with PBKDF2-derived key
                    String reencrypted = pbkdf2Blowfish.encryptString(decrypted);

                    // Update property in database
                    updateEncryptedProperty(con, prop.name, reencrypted);

                    migrated++;

                    // Log progress at most every 5 seconds to avoid log spam
                    Instant now = Instant.now();
                    if (Duration.between(lastLogTime, now).compareTo(logInterval) >= 0) {
                        Log.info("Migration progress: {}/{} properties", migrated, dbProperties.size());
                        lastLogTime = now;
                    }

                } catch (Exception e) {
                    Log.error("Failed to migrate property: {}", prop.name, e);
                    failed++;
                    failedProperties.add(prop.name);
                }
            }

            // 6. Check migration results
            if (failed > 0) {
                abortTransaction = true;
                Log.error("Migration completed with {} failures out of {} properties",
                        failed, dbProperties.size());
                Log.error("Failed properties: {}", String.join(", ", failedProperties));
                throw new RuntimeException("Migration failed for " + failed + " properties. " +
                        "Database has been rolled back. Check logs for details.");
            }

            // 7. Re-encrypt user passwords in the same transaction: they use the same KDF setting, so would otherwise
            // become unreadable (OF-3374). They all still use SHA1, so all can be re-encrypted.
            //
            // Steps 7 to 11 run while no password can be encrypted, decrypted or stored, so that none is stored with the
            // cached SHA1 cipher in the meantime. User logins wait for this to complete.
            final Connection transaction = con;
            final EncryptedPasswordMigration.Result passwords = AuthFactory.replacePasswordCipher(() -> {
                final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencryptAll(
                        transaction, JiveGlobals.BLOWFISH_KDF_SHA1, JiveGlobals.BLOWFISH_KDF_PBKDF2);

                // 8. Explicitly commit the database transaction before updating security.xml.
                // This provides clarity of intent - the transaction boundary is explicit, and
                // it's clear that what follows is post-commit work.
                //
                // Note: True atomicity across database and XML file operations isn't possible
                // since they're separate systems. The openfire XML properties were already
                // migrated in step 2 before this transaction started, so we're already
                // committed to PBKDF2 at that point. This commit finalises the database
                // portion of the migration.
                //
                // The closeTransactionConnection in the finally block will call commit() again,
                // but this is harmless - committing an already-committed transaction is a no-op.
                transaction.commit();
                Log.info("Database transaction committed successfully");

                // 9. Update security.xml to switch KDF to PBKDF2
                // This only updates the local node's security.xml
                // In clustered deployments, admin must manually sync to other nodes
                //
                // Known limitation (OF-3305): persistability was verified before the migration, but this
                // KDF write happens after the database commit. If security.xml became unwritable in that
                // narrow window (e.g. conf/ remounted read-only, or the disk filled), this save fails and
                // is only logged. The salt was already persisted earlier (during setKey), so this state is
                // recoverable by setting encrypt.blowfish.kdf=pbkdf2 in security.xml by hand. Surfacing this
                // failure properly is tracked as a follow-up (make security-critical saves report failure).
                JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
                Log.info("Updated security.xml: encrypt.blowfish.kdf=pbkdf2");

                // 10. Every stored password was re-encrypted, so no repair is needed (OF-3374). Set this before the lock
                // is released, so that waiting logins never see the new KDF without it.
                JiveGlobals.setPasswordsReencrypted(true);

                // 11. Returning discards the cached password cipher.
                return result;
            });

            // 12. Log success
            Log.info("Successfully migrated {} database properties, {} XML properties and {} user passwords from SHA1 to PBKDF2",
                    migrated, xmlMigrated, passwords.reencrypted());
            Log.info("Blowfish KDF is now set to PBKDF2-HMAC-SHA512 in security.xml");

            return new MigrationResult(migrated, xmlMigrated, passwords);

        } catch (Exception e) {
            abortTransaction = true;
            Log.error("Transaction rolled back due to error", e);
            throw e;

        } finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
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
     * Result of migration operation containing counts for both database and XML properties, and the outcome for user
     * passwords.
     */
    private record MigrationResult(int databaseCount, int xmlCount, EncryptedPasswordMigration.Result passwords) {}
}
